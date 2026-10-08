package com.punkstore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

/**
 * The "awesome" lists: community-kept lists of apps (root tools, Shizuku apps, FOSS apps...). Their READMEs link to GitHub repos, so every link
 * becomes an entry (pkg "gh:owner/repo"). Whether a repo has an APK is only looked up when you install it: GitHub allows 60 anonymous requests an hour,
 * far too few to check a thousand repos in advance.
 */
object AwesomeRepo {
    /** [dir]: every .md in that folder of the repo; [files]: exact paths. */
    class Src(val id: String, val repo: String, val label: String, val dir: String? = null, val files: List<String> = emptyList())

    val sources = listOf(
        Src("root", "awesome-android-root/awesome-android-root", "Awesome Root", dir = "docs/apps-and-modules/"),
        Src("shizuku", "timschneeb/awesome-shizuku", "Awesome Shizuku", files = listOf("README.md")),
        Src("foss", "albertomosconi/foss-apps", "Awesome FOSS apps", dir = "categories/"),
        Src("apps", "fiedri/awesome-android-apps", "Awesome Android apps", files = listOf("content/ALL_APPS.md")),
        Src("oss", "pcqpcq/open-source-android-apps", "Open-source Android apps", dir = "categories/"),
    )

    private val LINK = Regex("""\[([^\]]+)]\((https://github\.com/([\w.-]+)/([\w.-]+))(/[^)\s]*)?\)""")
    private val SKIP_OWNER = setOf("sponsors", "topics", "orgs", "settings", "features", "marketplace", "apps", "about", "login", "join", "collections", "readme", "site")
    private val SKIP_TAIL = Regex("^/(issues|pulls|stargazers|discussions|wiki|actions|network|graphs|commits|blob|tree|releases/tag)\\b")
    private val jsonLoose = Json { ignoreUnknownKeys = true }

    private fun cacheFile(c: Context, s: Src) = File(c.filesDir, "awesome-${s.id}.json")

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", "PunkStore").build()).execute().use { r -> if (r.isSuccessful) r.body!!.string() else null }
    }.getOrNull()

    private fun markdownFiles(s: Src): List<String> {
        s.dir ?: return s.files
        val body = runCatching {
            http.newCall(Request.Builder().url("https://api.github.com/repos/${s.repo}/git/trees/HEAD?recursive=1").header("Accept", "application/vnd.github+json").header("User-Agent", "PunkStore").build())
                .execute().use { r -> if (r.isSuccessful) r.body!!.string() else null }
        }.getOrNull() ?: return emptyList()
        return Regex(""""path"\s*:\s*"(${Regex.escape(s.dir)}[^"]+\.md)"""").findAll(body).map { it.groupValues[1] }.toList()
    }

    private fun clean(t: String) = t.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1").replace(Regex("<[^>]+>"), "").replace(Regex("[*_`]+"), "").replace(Regex("""^[\s|:\-–—·•>]+"""), "").replace(Regex("""\s+"""), " ").trim()

    /** Every GitHub repo linked in [md]: (owner/repo, link text, what the line says about it, the heading it sits under). */
    private fun parse(md: String, own: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var heading = ""
        for (line in md.lineSequence()) {
            if (line.startsWith("#")) { heading = clean(line.trimStart('#')).take(40); continue }
            for (m in LINK.findAll(line)) {
                val (text, _, owner, repo, tail) = m.destructured
                val full = "$owner/$repo"
                if (owner.lowercase() in SKIP_OWNER || full.equals(own, true) || tail.isNotEmpty() && SKIP_TAIL.containsMatchIn(tail)) continue
                if (repo.endsWith(".png") || repo.endsWith(".jpg") || repo.endsWith(".svg")) continue
                val after = line.substring(m.range.last + 1)
                val desc = clean(after).take(160)
                out.add(listOf(full, clean(text).ifBlank { repo }, desc, heading))
            }
        }
        return out
    }

    private fun toItem(l: List<String>, src: Src): AppItem {
        val full = l[0]; val owner = full.substringBefore('/')
        val name = GitHubRepo.displayName(full, l[1].takeIf { !it.startsWith("http") && it.length < 50 } ?: full.substringAfter('/'))
        return AppItem(
            pkg = "gh:$full", name = name, summary = l[2], description = l[2],
            icon = "https://github.com/$owner.png?size=128", categories = listOf(src.label), tags = listOf("GitHub", "Awesome") + listOfNotNull(l[3].ifBlank { null }),
            web = "https://github.com/$full", source = "GITHUB", developer = owner,
        )
    }

    /** One list: read from the saved copy while it is fresh (3 days), else downloaded again (the saved copy stays if GitHub cannot be reached). */
    private suspend fun load(c: Context, s: Src): List<AppItem> = withContext(Dispatchers.IO) {
        val f = cacheFile(c, s)
        val saved = runCatching { jsonLoose.decodeFromString(ListSerializer(AppItem.serializer()), f.readText()) }.getOrNull()
        if (saved != null && System.currentTimeMillis() - f.lastModified() < 3 * 86400_000L) return@withContext saved
        val files = markdownFiles(s)
        val seen = HashSet<String>()
        val items = ArrayList<AppItem>()
        for (path in files) {
            val md = get("https://raw.githubusercontent.com/${s.repo}/HEAD/$path") ?: continue
            parse(md, s.repo).forEach { l -> if (seen.add(l[0].lowercase())) items.add(toItem(l, s)) }
        }
        if (items.isEmpty()) return@withContext saved.orEmpty()
        runCatching { f.writeText(jsonLoose.encodeToString(ListSerializer(AppItem.serializer()), items)) }
        items
    }

    /** All the lists, deduplicated (a repo that is in two lists shows once). */
    suspend fun all(c: Context): List<AppItem> = coroutineScope {
        val seen = HashSet<String>()
        sources.map { s -> async { load(c, s) } }.awaitAll().flatten().filter { seen.add(it.pkg.lowercase()) }
    }
}
