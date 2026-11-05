package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** The code hosts whose projects the store can show and install from: GitHub, GitLab and Codeberg (Forgejo / Gitea). Items use pkg "gh:", "gl:" and "cb:". */
enum class Forge(val host: String, val label: String, val prefix: String, val source: String) {
    GITHUB("github.com", "GitHub", "gh:", "GITHUB"),
    GITLAB("gitlab.com", "GitLab", "gl:", "GITLAB"),
    CODEBERG("codeberg.org", "Codeberg", "cb:", "CODEBERG"),
}

fun isForgePkg(pkg: String) = pkg.startsWith("gh:") || pkg.startsWith("gl:") || pkg.startsWith("cb:")

/** "https://gitlab.com/group/sub/project/-/releases" -> (GitLab, "group/sub/project"); null when the address is not a project of one of the three hosts. */
fun forgeOf(web: String): Pair<Forge, String>? {
    val m = Regex("""^https?://(?:www\.)?(github\.com|gitlab\.com|codeberg\.org)/([^?#]+)""", RegexOption.IGNORE_CASE).find(web.trim()) ?: return null
    val forge = Forge.values().first { it.host.equals(m.groupValues[1], true) }
    val parts = m.groupValues[2].substringBefore("/-/").trimEnd('/').removeSuffix(".git").split('/').filter { it.isNotBlank() }
    val full = if (forge == Forge.GITLAB) parts.joinToString("/") else parts.take(2).joinToString("/")
    return if (full.contains('/')) forge to full else null
}

/** What a person typed to add a source -> the entry the store keeps: "owner/repo" (GitHub), "gl:group/project", "cb:owner/repo"; null when it is none of these. */
fun sourceEntry(text: String): String? {
    val x = text.trim()
    forgeOf(x)?.let { (f, full) -> return if (f == Forge.GITHUB) full else f.prefix + full }
    return x.takeIf { Regex("[\\w.-]+/[\\w.-]+").matches(it) || Regex("(gl:|cb:)[\\w.-]+(/[\\w.-]+)+").matches(it) }
}

fun forgeOfPkg(pkg: String): Pair<Forge, String>? = Forge.values().firstOrNull { pkg.startsWith(it.prefix) }?.let { it to pkg.removePrefix(it.prefix) }

class ReleaseAsset(val name: String, val size: Long, val downloads: Long, val url: String)
class ReleaseInfo(val tag: String, val name: String, val date: String, val body: String, val assets: List<ReleaseAsset>)
class ProjectInfo(
    val forge: Forge, val full: String, val description: String, val stars: Long, val forks: Long, val issues: Long, val license: String,
    val language: String, val topics: List<String>, val updated: String, val home: String, val readme: String, val releases: List<ReleaseInfo>,
)

/** Projects of GitLab and Codeberg (and the details of GitHub ones): found by their public APIs, no account and no key. */
object ForgeRepo {
    private val memory = ConcurrentHashMap<String, ProjectInfo>()

    private fun get(url: String, accept: String? = null): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "PunkStore").apply { if (accept != null) header("Accept", accept) }.build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    private fun obj(s: String?): JsonObject? = runCatching { Json.parseToJsonElement(s!!).jsonObject }.getOrNull()
    private fun arr(s: String?): JsonArray? = runCatching { Json.parseToJsonElement(s!!).jsonArray }.getOrNull()
    private fun JsonObject.s(k: String): String = (get(k) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.l(k: String): Long = (get(k) as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.o(k: String): JsonObject? = get(k) as? JsonObject
    private fun JsonObject.a(k: String): JsonArray = get(k) as? JsonArray ?: JsonArray(emptyList())
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** The picture-less, tag-less text of a README: images, badges and HTML are left out so it reads as plain text. */
    internal fun plainReadme(md: String): String = md
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("(?is)<(script|style).*?</\\1>"), "")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[!\\[[^\\]]*]\\([^)]*\\)]\\([^)]*\\)"), "")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("\n{3,}"), "\n\n").trim()

    suspend fun load(forge: Forge, full: String): ProjectInfo? = withContext(Dispatchers.IO) {
        val key = forge.prefix + full
        memory[key]?.let { return@withContext it }
        runCatching {
            when (forge) {
                Forge.GITHUB -> github(full)
                Forge.GITLAB -> gitlab(full)
                Forge.CODEBERG -> codeberg(full)
            }
        }.getOrNull()?.also { memory[key] = it }
    }

    private fun github(full: String): ProjectInfo? {
        val r = obj(get("https://api.github.com/repos/$full", "application/vnd.github+json")) ?: return null
        val readme = get("https://api.github.com/repos/$full/readme", "application/vnd.github.raw").orEmpty()
        val rel = arr(get("https://api.github.com/repos/$full/releases?per_page=6", "application/vnd.github+json"))?.map { it.jsonObject }.orEmpty().map { x ->
            ReleaseInfo(x.s("tag_name"), x.s("name"), x.s("published_at").take(10), x.s("body"),
                x.a("assets").map { it.jsonObject }.map { ReleaseAsset(it.s("name"), it.l("size"), it.l("download_count"), it.s("browser_download_url")) })
        }
        return ProjectInfo(Forge.GITHUB, full, r.s("description"), r.l("stargazers_count"), r.l("forks_count"), r.l("open_issues_count"), r.o("license")?.s("spdx_id").orEmpty(),
            r.s("language"), r.a("topics").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, r.s("pushed_at").take(10), r.s("homepage"), plainReadme(readme), rel)
    }

    private fun gitlab(full: String): ProjectInfo? {
        val id = enc(full)
        val r = obj(get("https://gitlab.com/api/v4/projects/$id?license=true")) ?: return null
        val branch = r.s("default_branch").ifBlank { "HEAD" }
        val file = r.s("readme_url").substringAfterLast('/').ifBlank { "README.md" }
        val readme = get("https://gitlab.com/api/v4/projects/$id/repository/files/${enc(file)}/raw?ref=${enc(branch)}").orEmpty()
        val lang = obj(get("https://gitlab.com/api/v4/projects/$id/languages"))?.entries?.maxByOrNull { (it.value as? JsonPrimitive)?.doubleOrNull ?: 0.0 }?.key.orEmpty()
        val rel = arr(get("https://gitlab.com/api/v4/projects/$id/releases?per_page=6"))?.map { it.jsonObject }.orEmpty().map { x ->
            val links = x.o("assets")?.a("links")?.map { it.jsonObject }.orEmpty()
            ReleaseInfo(x.s("tag_name"), x.s("name"), x.s("released_at").take(10), x.s("description"),
                links.map { ReleaseAsset(it.s("name"), 0L, 0L, it.s("direct_asset_url").ifBlank { it.s("url") }) })
        }
        return ProjectInfo(Forge.GITLAB, full, r.s("description"), r.l("star_count"), r.l("forks_count"), r.l("open_issues_count"), r.o("license")?.s("name").orEmpty(),
            lang, r.a("topics").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, r.s("last_activity_at").take(10), r.s("web_url"), plainReadme(readme), rel)
    }

    private fun codeberg(full: String): ProjectInfo? {
        val base = "https://codeberg.org/api/v1/repos/$full"
        val r = obj(get(base)) ?: return null
        val branch = r.s("default_branch").ifBlank { "main" }
        val readme = listOf("README.md", "readme.md", "README.rst", "README").firstNotNullOfOrNull { get("$base/raw/${enc(it)}?ref=${enc(branch)}") }.orEmpty()
        val topics = obj(get("$base/topics"))?.a("topics")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val rel = arr(get("$base/releases?limit=6"))?.map { it.jsonObject }.orEmpty().map { x ->
            ReleaseInfo(x.s("tag_name"), x.s("name"), x.s("published_at").take(10), x.s("body"),
                x.a("assets").map { it.jsonObject }.map { ReleaseAsset(it.s("name"), it.l("size"), it.l("download_count"), it.s("browser_download_url")) })
        }
        return ProjectInfo(Forge.CODEBERG, full, r.s("description"), r.l("stars_count"), r.l("forks_count"), r.l("open_issues_count"), "",
            r.s("language"), topics, r.s("updated_at").take(10), r.s("website"), plainReadme(readme), rel)
    }

    /** An installable item for a GitLab or Codeberg project: its newest release with an APK. Null when it has none. */
    suspend fun item(forge: Forge, full: String, group: String): AppItem? = withContext(Dispatchers.IO) {
        val info = load(forge, full) ?: return@withContext null
        val rel = info.releases.firstOrNull { r -> r.assets.any { it.name.endsWith(".apk", true) || it.url.substringBefore('?').endsWith(".apk", true) } } ?: return@withContext null
        val abis = android.os.Build.SUPPORTED_ABIS.map { it.replace('-', '_').lowercase() }
        val tokens = listOf("arm64_v8a", "arm64", "armeabi_v7a", "armeabi", "x86_64", "x86")
        fun norm(n: String) = n.lowercase().replace('-', '_')
        val apks = rel.assets.filter { it.name.endsWith(".apk", true) || it.url.substringBefore('?').endsWith(".apk", true) }
        val apk = apks.firstOrNull { a -> abis.any { norm(a.name).contains(it) } }
            ?: apks.firstOrNull { a -> tokens.none { norm(a.name).contains(it) } || norm(a.name).contains("universal") } ?: apks.first()
        AppItem(
            pkg = forge.prefix + full, name = full.substringAfterLast('/').replace('-', ' ').replace('_', ' ').trim(), summary = info.description,
            description = info.description + "\n\n" + rel.body.take(1500), categories = listOf(group), tags = listOf(forge.label, group),
            license = info.license, apkUrl = apk.url, apkSize = apk.size, versionName = rel.tag, source = forge.source,
            developer = full.substringBefore('/'), installs = if (apk.downloads > 0) "${apk.downloads} downloads" else "",
            web = "https://${forge.host}/$full",
        )
    }
}

private val DETAIL_W = Color.White

/** The project's own page inside the app page: counts, the latest releases with their files, and the README, in the store's theme. Shown for any app whose address is a GitHub, GitLab or Codeberg project. */
@Composable
fun ProjectDetails(a: AppItem) {
    val ref = forgeOf(a.web) ?: forgeOfPkg(a.pkg) ?: return
    val (forge, full) = ref
    val info by produceState<ProjectInfo?>(null, forge, full) { value = ForgeRepo.load(forge, full) }
    var allReleases by remember(full) { mutableStateOf(false) }
    var readmeAll by remember(full) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(t("PROJE", "PROJECT") + " · " + forge.label, Modifier.padding(bottom = 8.dp), color = DETAIL_W, fontSize = 16.sp)
        val i = info
        if (i == null) { Text(t("Yükleniyor…", "Loading…"), color = Steam.dim, fontSize = 13.sp); return@Column }
        fun big(n: Long) = if (n >= 1000) "%.1fk".format(n / 1000.0) else n.toString()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("★ ${big(i.stars)}", "⑂ ${big(i.forks)}", t("Sorun ", "Issues ") + big(i.issues), i.license, i.language, i.updated).filter { it.isNotBlank() }.forEach { c ->
                Text(c, Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(horizontal = 10.dp, vertical = 5.dp), color = Steam.text, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (i.topics.isNotEmpty()) Text(i.topics.take(8).joinToString("  ·  "), Modifier.padding(top = 8.dp), color = Steam.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (i.releases.isNotEmpty()) {
            Text(t("SÜRÜMLER", "RELEASES"), Modifier.padding(top = 16.dp, bottom = 6.dp), color = DETAIL_W, fontSize = 14.sp)
            (if (allReleases) i.releases else i.releases.take(2)).forEach { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(12.dp)) {
                    Text(r.name.ifBlank { r.tag } + if (r.date.isNotBlank()) "  ·  ${r.date}" else "", color = DETAIL_W, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r.body.isNotBlank()) Text(ForgeRepo.plainReadme(r.body).take(500), Modifier.padding(top = 4.dp), color = Steam.dim, fontSize = 12.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    r.assets.take(6).forEach { f ->
                        Text(f.name + (if (f.size > 0) "  ·  " + sizeText(f.size) else "") + (if (f.downloads > 0) "  ·  ↓${f.downloads}" else ""),
                            Modifier.fillMaxWidth().clickable { Browser.open(f.url) }.padding(top = 6.dp), color = Steam.link, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (i.releases.size > 2) Text(if (allReleases) t("Daha az", "Show less") else t("Tüm sürümler", "All releases"), Modifier.clickable { allReleases = !allReleases }.padding(vertical = 4.dp), color = Steam.link)
        }
        if (i.readme.isNotBlank()) {
            Text("README", Modifier.padding(top = 16.dp, bottom = 6.dp), color = DETAIL_W, fontSize = 14.sp)
            Text(if (readmeAll) i.readme.take(12000) else i.readme.take(900), color = Steam.text, fontSize = 13.sp, lineHeight = 19.sp)
            if (i.readme.length > 900) Text(if (readmeAll) t("Daha az", "Show less") else t("Devamı", "Read more"), Modifier.clickable { readmeAll = !readmeAll }.padding(vertical = 6.dp), color = Steam.link)
        }
        Text(t("${forge.label}'da aç", "Open on ${forge.label}"), Modifier.padding(top = 10.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.btn).clickable { Browser.open(a.web.ifBlank { "https://${forge.host}/$full" }) }.padding(horizontal = 14.dp, vertical = 8.dp),
            color = DETAIL_W, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
