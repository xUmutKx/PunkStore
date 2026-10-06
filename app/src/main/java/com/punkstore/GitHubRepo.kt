package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request

/** Apps published as GitHub releases. Items use pkg "gh:owner/repo" and source "GITHUB". */
object GitHubRepo {
    const val OWNER = "xUmutKx"

    /** Curated list: (owner/repo, group). Users can add more from the tab. */
    val curated = listOf(
        "ReVanced/revanced-manager" to "Mods", "MorpheApp/morphe-manager" to "Mods", "revanced-apks/build-apps" to "Mods",
        "tiann/KernelSU" to "Root", "topjohnwu/Magisk" to "Root", "LSPosed/LSPosed" to "Root", "bmax121/APatch" to "Root",
        "Aliucord/Aliucord" to "Mods", "NewPipe/NewPipe" to "Open source", "TeamNewPipe/NewPipe" to "Open source",
        "AntennaPod/AntennaPod" to "Open source", "Kotatsu/Kotatsu" to "Open source", "termux/termux-app" to "Open source",
        "aurora-oss/AuroraStore" to "Open source", "Droid-ify/Droid-ify" to "Open source", "Neo-Store/Neo-Store" to "Open source",
    ).distinctBy { it.first.lowercase() }

    private fun get(url: String): JsonElement? = http.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
        if (!r.isSuccessful) null else Json.parseToJsonElement(r.body!!.string())
    }

    private fun JsonElement?.s(k: String) = (this as? JsonObject)?.get(k)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun pickApk(assets: JsonArray): JsonObject? {
        val apks = assets.map { it.jsonObject }.filter { it.s("name").endsWith(".apk", true) }
        val abis = android.os.Build.SUPPORTED_ABIS.map { it.replace('-', '_').lowercase() }
        val abiTokens = listOf("arm64_v8a", "arm64", "armeabi_v7a", "armeabi", "x86_64", "x86")
        fun norm(n: String) = n.lowercase().replace('-', '_')
        return apks.firstOrNull { a -> abis.any { norm(a.s("name")).contains(it) } }
            ?: apks.firstOrNull { a -> abiTokens.none { norm(a.s("name")).contains(it) } || norm(a.s("name")).contains("universal") }
            ?: apks.firstOrNull()
    }

    suspend fun item(full: String, group: String): AppItem? = withContext(Dispatchers.IO) {
        runCatching {
            val rel = get("https://api.github.com/repos/$full/releases/latest")?.jsonObject ?: return@runCatching null
            val apk = pickApk(rel["assets"]?.jsonArray ?: JsonArray(emptyList())) ?: return@runCatching null
            val repo = get("https://api.github.com/repos/$full")
            val name = repo.s("name").ifBlank { full.substringAfter('/') }
            AppItem(
                pkg = "gh:$full", name = name, summary = repo.s("description"),
                description = repo.s("description") + "\n\n" + rel.s("body").take(1500),
                icon = "https://github.com/${full.substringBefore('/')}.png?size=128", categories = listOf(group), tags = listOf("GitHub", group),
                updated = runCatching { java.time.Instant.parse(rel.s("published_at")).toEpochMilli() }.getOrDefault(0L),
                license = (repo as? JsonObject)?.get("license")?.let { if (it is JsonObject) it.s("spdx_id") else "" }.orEmpty(),
                apkUrl = apk.s("browser_download_url"), apkSize = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                versionName = rel.s("tag_name"), web = "https://github.com/$full", source = "GITHUB", developer = full.substringBefore('/'),
                installs = (apk["download_count"]?.jsonPrimitive?.longOrNull ?: 0L).let { if (it > 0) "$it downloads" else "" },
            )
        }.getOrNull()
    }

    /** Public repos of [OWNER] that have an APK in their latest release. */
    suspend fun mine(): List<AppItem> = coroutineScope {
        val repos = withContext(Dispatchers.IO) { runCatching { get("https://api.github.com/users/$OWNER/repos?per_page=100&sort=pushed")?.jsonArray }.getOrNull() } ?: return@coroutineScope emptyList()
        repos.map { it.jsonObject }.filter { it.s("fork") != "true" }.map { r -> async { item(r.s("full_name"), "by UmutK") } }.awaitAll().filterNotNull()
    }

    suspend fun curatedItems(extra: List<String>): List<AppItem> = coroutineScope {
        (curated + extra.map { it to "Added by you" }).map { (full, g) -> async { item(full, g) } }.awaitAll().filterNotNull()
    }
}

@Composable
fun UmutKScreen(s: Store, onOpen: (String) -> Unit) {
    LaunchedEffect(Unit) { s.loadGithub() }
    val mine = s.ghMap.values.filter { it.categories.firstOrNull() == "by UmutK" }.sortedByDescending { it.updated }
    Column(Modifier.fillMaxSize().background(Steam.bg)) {
        Text("by UmutK", Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(14.dp), color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, letterSpacing = 2.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { SteamSection(t("Uygulamalarım (GitHub)", "My apps (GitHub)")) }
            if (mine.isEmpty()) item { Text(if (s.ghLoading) t("Yükleniyor…", "Loading…") else t("Henüz herkese açık sürüm yok (özel depolar listelenmez).", "No public releases yet (private repos are not listed)."), Modifier.padding(16.dp), color = Steam.dim, fontSize = 14.sp) }
            items(mine, key = { it.pkg }) { GhRow(it, onOpen) }
        }
    }
}

/** GitHub sources: other people's repos whose releases carry an Android APK (ReVanced Manager, Morphe, root tools...). */
@Composable
fun GithubSourcesList(s: Store, onOpen: (String) -> Unit) {
    LaunchedEffect(Unit) { s.loadGithubSources() }
    var adding by remember { mutableStateOf("") }
    val others = s.ghMap.values.filter { it.categories.firstOrNull() != "by UmutK" }.groupBy { it.categories.firstOrNull().orEmpty() }
    LazyColumn(Modifier.fillMaxSize().background(Steam.bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        if (others.isEmpty()) item { Text(if (s.ghLoading) t("Yükleniyor…", "Loading…") else t("Kaynak bulunamadı.", "Nothing found."), Modifier.padding(16.dp), color = Steam.dim) }
        others.forEach { (g, l) ->
            item { SteamSection(when (g) { "Root" -> t("Root araçları", "Root tools"); "Mods" -> t("Modlar (ReVanced, Morphe…)", "Mods (ReVanced, Morphe…)"); "Open source" -> t("Açık kaynak", "Open source"); else -> g }) }
            items(l.sortedBy { it.name.lowercase() }, key = { it.pkg }) { GhRow(it, onOpen) }
        }
        item {
            SteamSection(t("Kaynak ekle", "Add a source"))
            Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(adding, { adding = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("owner/repo") })
                Text(t("Ekle", "Add"), Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(Steam.btn).clickable { if (Regex("[\\w.-]+/[\\w.-]+").matches(adding.trim())) { s.addGithub(adding.trim()); adding = "" } }.padding(horizontal = 16.dp, vertical = 14.dp), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
            if (s.ghExtra.isNotEmpty()) Text(t("Eklediklerin: ", "Yours: ") + s.ghExtra.joinToString(", "), Modifier.padding(16.dp, 0.dp), color = Steam.dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GhRow(a: AppItem, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(a.pkg) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(a.icon, null, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Steam.box), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(a.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.summary.ifBlank { a.developer }, color = Steam.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(a.versionName, Modifier.padding(start = 8.dp), color = Steam.link, fontSize = 12.sp, maxLines = 1)
    }
}
