package com.punkstore

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
        "libre-tube/LibreTube" to "Open source", "mihonapp/mihon" to "Open source", "JunkFood02/Seal" to "Open source", "zhanghai/MaterialFiles" to "Open source",
        "Fox2Code/FoxMagiskModuleManager" to "Root", "j-hc/zygisk-detach" to "Root", "PerformanC/ReVancedXposed" to "Mods", "inotia00/revanced-manager" to "Mods",
    ).distinctBy { it.first.lowercase() }

    /** Last good copy of every repo, so GitHub's 60 anonymous requests an hour cannot make apps disappear. Set by the Store. */
    var cache: android.content.SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }
    private fun cached(full: String): AppItem? = runCatching { cache?.getString("gh_$full", null)?.let { json.decodeFromString(AppItem.serializer(), it) } }.getOrNull()
    private fun remember(full: String, a: AppItem) { runCatching { cache?.edit()?.putString("gh_$full", json.encodeToString(AppItem.serializer(), a))?.apply() } }
    private fun cachedMine(): List<AppItem> = (cache?.all?.keys.orEmpty()).filter { it.startsWith("gh_") }.mapNotNull { cached(it.removePrefix("gh_")) }.filter { it.categories.firstOrNull() == "by UmutK" }

    /** "ClaudeChat" -> "Claude Chat", "Uno-Groovy" -> "Uno Groovy"; my own apps keep the names I gave them. */
    fun displayName(full: String, repoName: String): String {
        myApps.firstOrNull { it.repo.equals(full, true) }?.let { return it.name }
        return repoName.replace('-', ' ').replace('_', ' ').replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").trim().ifBlank { full.substringAfter('/') }
    }

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

    suspend fun item(full: String, group: String, repoJson: JsonElement? = null): AppItem? = withContext(Dispatchers.IO) {
        runCatching {
            // one request per repo (the repo info comes from the list when we have it); no answer (limit, offline) = the last good copy
            val rel = get("https://api.github.com/repos/$full/releases/latest")?.jsonObject ?: return@runCatching cached(full)
            val apk = pickApk(rel["assets"]?.jsonArray ?: JsonArray(emptyList())) ?: return@runCatching null
            val repo = repoJson ?: get("https://api.github.com/repos/$full")
            val name = displayName(full, repo.s("name").ifBlank { full.substringAfter('/') })
            AppItem(
                pkg = "gh:$full", name = name, summary = repo.s("description"),
                description = repo.s("description") + "\n\n" + rel.s("body").take(1500),
                icon = "https://github.com/${full.substringBefore('/')}.png?size=128", categories = listOf(group), tags = listOf("GitHub", group),
                updated = runCatching { java.time.Instant.parse(rel.s("published_at")).toEpochMilli() }.getOrDefault(0L),
                license = (repo as? JsonObject)?.get("license")?.let { if (it is JsonObject) it.s("spdx_id") else "" }.orEmpty(),
                apkUrl = apk.s("browser_download_url"), apkSize = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                versionName = rel.s("tag_name"), web = "https://github.com/$full", source = "GITHUB", developer = full.substringBefore('/'),
                installs = (apk["download_count"]?.jsonPrimitive?.longOrNull ?: 0L).let { if (it > 0) "$it downloads" else "" },
            ).also { remember(full, it) }
        }.getOrElse { cached(full) }
    }

    /** Every app repo of [OWNER] (not forks, not archived) that has an APK in its latest release; falls back to the saved copies when GitHub does not answer. */
    suspend fun mine(): List<AppItem> = coroutineScope {
        val repos = withContext(Dispatchers.IO) { runCatching { get("https://api.github.com/users/$OWNER/repos?per_page=100&sort=pushed")?.jsonArray }.getOrNull() }
            ?: return@coroutineScope cachedMine()
        val fresh = repos.map { it.jsonObject }
            .filter { it.s("fork") != "true" && it.s("archived") != "true" && it.s("name") != OWNER }
            .map { r -> async { item(r.s("full_name"), UMUTK_CAT, r) } }.awaitAll().filterNotNull()
        // anything that did not come back this time (rate limit) stays from the saved copies
        fresh + cachedMine().filter { c -> fresh.none { it.pkg == c.pkg } }
    }

    suspend fun curatedItems(extra: List<String>): List<AppItem> = coroutineScope {
        (curated + extra.map { it to "Added by you" }).map { (full, g) -> async { item(full, g) } }.awaitAll().filterNotNull()
    }
}

const val UMUTK_CAT = "by UmutK"

/** My own apps, described from their READMEs; screenshots ship in assets/umutk. */
class MyApp(val name: String, val tagline: String, val readme: String, val shots: List<String>, val repo: String, val color: Long)

val myApps: List<MyApp> get() = listOf(
    MyApp("Punk Store", t("F-Droid, Google Play ve Steam tek mağazada", "F-Droid, Google Play and Steam in one store"),
        t("Telefonum için bir şey ararken F-Droid, Play Store ve Steam arasında gidip gelmekten bıktım, üçünü tek mağazada topladım. Görünümü Steam mobil uygulaması gibi.\n\n" +
            "• Üç kaynak, tek arama: F-Droid, Google Play ve Steam; yazdıkça arar, en iyi eşleşme üstte.\n" +
            "• Steam gibi çalışan kütüphane: son kullandığın büyük kartta, filtreler, koleksiyonlar, uzun basınca menü.\n" +
            "• Steam hesabın: oyunların, oynama süren ve başarımların gelir; şifren uygulamaya hiç uğramaz.\n" +
            "• İndirmeler: aynı anda iki indirme, duraklat/devam, SHA-256 doğrulama, root ile sessiz kurulum.\n" +
            "• Uygulama sayfalarında ekran görüntüleri, yorumlar ve gizlilik raporu.\n\nHobi projesi; Valve ile ilgisi yok. GPL-3.0.",
            "I got tired of jumping between F-Droid, the Play Store and Steam every time I wanted to find something for my phone, so I made one store that has all three. It looks like the Steam mobile app.\n\n" +
            "• Three sources, one search: F-Droid, Google Play and Steam; it searches as you type, best match first.\n" +
            "• A library that works like Steam's: last used app on a big card, filters, collections, long-press menu.\n" +
            "• Your Steam account: games, playtime and achievements come over; your password never touches the app.\n" +
            "• Downloads: two at once, pause/resume, SHA-256 checks, silent installs with root.\n" +
            "• App pages with screenshots, reviews and a privacy report.\n\nA fan project I build for fun; nothing to do with Valve. GPL-3.0."),
        listOf("store.png", "app-page.png", "search.png", "profile.png"), "xUmutKx/PunkStore", 0xFF1B6FA8),
    MyApp("Claude Chat", t("Claude Code için Android sohbet uygulaması", "An Android chat app for Claude Code"),
        t("Termux + proot Ubuntu içindeki Claude Code'u, telefonda gerçek bir sohbet arayüzüyle kullan. Uygulama küçük bir köprü (127.0.0.1) üzerinden `claude` ile konuşur.\n\n" +
            "• Aynı anda birden fazla sohbet: yenisini açınca eskisi arkada çalışmaya devam eder.\n" +
            "• Çalışırken dans eden maskot: bildirimde, ada (pill) üzerinde, her zaman açık ekranda ve sohbet başlığında.\n" +
            "• Tek dokunuşla köprüyü başlat, Termux kapansa bile geri getirir.\n" +
            "• Konuya göre otomatik sohbet başlıkları, model/çaba/izin modu seçimi, ek dosyalar.",
            "Use Claude Code from Termux + proot Ubuntu through a real chat interface on your phone. The app talks to `claude` through a tiny bridge on 127.0.0.1.\n\n" +
            "• Several chats at once: open a new one and the old one keeps working in the background.\n" +
            "• A mascot that dances while Claude works: in the notification, the island pill, the always-on screen and the chat header.\n" +
            "• One tap starts the bridge, and brings it back if Termux dies.\n" +
            "• Automatic topic-based chat titles, model/effort/permission pickers, attachments."),
        listOf("claudechat-chat.png", "claudechat-settings.png", "claudechat-black.png"), "xUmutKx/ClaudeChat", 0xFFD97757),
    MyApp("LrcLrc", t("Şarkı sözlerinde ara, satıra atla", "Search your lyrics, jump to the line"),
        t("Müzik klasöründeki .lrc söz dosyalarında anında arama yapar ve eşleşen satırdan Poweramp'te (ya da seçtiğin müzik uygulamasında) çalar.\n\n" +
            "• Düz yazı art arda kelimeleri, virgül tüm sözlerde ayrı ayrı arar.\n• Kitaplıkta kapağa dokununca şarkının sözleri açılır.\n• Varsayılan dil İngilizce, ayarlardan Türkçe seçilir.",
            "Instantly searches the .lrc lyric files in your music folder and plays from the matching line in Poweramp (or the music app you pick).\n\n" +
            "• Plain text searches words in a row, a comma matches each part anywhere in the lyrics.\n• Tap a cover in your library to read the song's lyrics.\n• English by default, Türkçe in Settings."),
        listOf("lrclrc-library.png", "lrclrc-search.png"), "xUmutKx/LrcLrc", 0xFF7C5CFF),
    MyApp("Palette", t("Tek bir renk paletiyle tüm uygulama ikonlarını yeniden temala", "Re-theme every app icon from one colour palette"),
        t("Bir fotoğraf, duvar kağıdın ya da hazır bir palet seç; Palette ana ekrandaki tüm uygulamalar için uyumlu ikonlar üretir. Her şey cihazda çalışır.\n\n" +
            "• Renkler Oklab uzayında eşlenir: logolar çamurlaşmaz, kontrast korunur.\n• Orijinal, stilize ve karo ikon modları; okunmayacak ikonlar otomatik karoya döner.\n• Tasker / Rutinler / adb ile otomasyon, geri alma ve paylaşma.",
            "Pick a photo, your wallpaper or a preset and Palette builds matching icons for every app on your home screen. Everything runs on-device.\n\n" +
            "• Colours are mapped in Oklab, so logos don't turn to mud and contrast holds.\n• Original, styled and tile icon modes; icons that would be illegible fall back to tiles on their own.\n• Automation through Tasker / Routines / adb, undo and sharing."),
        listOf("palette-lavender.jpg", "palette-sakura.jpg"), "xUmutKx/Palette", 0xFF9B7FD6),
    MyApp("Pulse", t("Windows Görev Yöneticisi gibi Android görev yöneticisi", "A task manager for Android that looks like Windows Task Manager"),
        t("Telefonum için Windows Görev Yöneticisi: ısı haritalı süreç listesi, canlı grafikli Performans sekmesi, başlangıç uygulamaları, hizmetler; ayrıca sensörler, kamera, pil sağlığı ve ısı bilgileri.\n\n" +
            "• Windows 11 (Mica), 10, 7, XP ve 95 görünümleri, koyu temalar siyaha yakın.\n• Yüzen izleyici, bildirim, sıcaklık/pil/bellek uyarıları.\n• Hiçbir veri paylaşılmaz.",
            "Windows Task Manager for my phone: a process list with heat-map cells, a Performance tab with live graphs, startup apps and services, plus sensors, camera, battery health and thermals.\n\n" +
            "• Windows 11 (with Mica), 10, 7, XP and 95 looks, dark themes close to AMOLED black.\n• A floating monitor, a notification, alerts for temperature, battery and memory.\n• No data is shared."),
        emptyList(), "xUmutKx/Pulse", 0xFF0078D7),
    MyApp("BlackOut", t("Uygulamaların gri yüzeylerini saf AMOLED siyaha çevir", "Turn the grey surfaces of your apps into pure AMOLED black"),
        t("Android'in koyu modu siyah değil koyu gridir. BlackOut bunu root, erişilebilirlik katmanı ya da LSPosed modülüyle gerçek siyaha çevirir.\n\n" +
            "• Dark pages: beyaz sayfa seçtiğin renge, yazı beyaza (Samsung Notes PDF'leri için ideal).\n• Üst katman: root gerekmez, yazı ve görsellere dokunmaz.\n• LSPosed modülü: renk adlarını gizleyen uygulamalarda bile çalışır.\n• İnternet izni yok, hiçbir veri paylaşılmaz.",
            "Android's dark mode is dark grey, not black. BlackOut turns it into real black with root, an accessibility layer or an LSPosed module.\n\n" +
            "• Dark pages: white pages to the colour you pick, text to white (great for Samsung Notes PDFs).\n• Top layer: no root, leaves text and images alone.\n• LSPosed module: works even in apps that hide their colour names.\n• No internet permission, no data is shared."),
        emptyList(), "xUmutKx/BlackOut", 0xFF3F51B5),
    MyApp("Mega Games", t("Tek uygulamada onlarca mini oyun", "Dozens of mini games in one app"),
        t("Tek bir uygulamada, internet izni olmadan çalışan 3B ve 2B mini oyun koleksiyonu. Oyunlar uygulamanın içinde (WebView + yerel three.js) çalışır; kayıtlar cihazda kalır.\n\n" +
            "• İzin yok, reklam yok: yalnızca titreşim.\n• Geri tuşu oyundan merkeze, merkezden çıkışa götürür.\n• Fruit Ninja, Subway Surfers, Flappy Bird benzeri ve rahatlama oyunları dahil.",
            "A collection of 3D and 2D mini games in one app that needs no internet permission. The games run inside the app (WebView + bundled three.js) and saves stay on your device.\n\n" +
            "• No permissions, no ads: only vibration.\n• Back goes from a game to the hub, from the hub to exit.\n• Includes Fruit Ninja, Subway Surfers and Flappy Bird style games plus relaxing ones."),
        emptyList(), "xUmutKx/MegaGames", 0xFF7B3FE4),
)

@Composable
private fun MyIcon(a: MyApp, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(size / 5)).background(androidx.compose.ui.graphics.Color(a.color)), contentAlignment = Alignment.Center) {
        Text(a.name.first().toString(), color = androidx.compose.ui.graphics.Color.White, fontSize = (size * 0.5f).sp, fontWeight = FontWeight.Black)
    }
}

/** Compact strip at the bottom of the store: my apps, tap for the full "by UmutK" category. */
@Composable
fun UmutKStrip(onOpen: () -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(myApps, key = { it.name }) { a ->
            Row(Modifier.width(250.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MyIcon(a, 48)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(a.name, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    Text(a.tagline, color = Steam.dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun UmutKScreen(s: Store, onOpen: (String) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { s.loadGithub() }
    var sel by remember { mutableStateOf<MyApp?>(null) }
    androidx.activity.compose.BackHandler(sel != null) { sel = null }
    val mine = s.ghMap.values.filter { it.categories.firstOrNull() == "by UmutK" && myApps.none { m -> m.repo.equals(it.pkg.removePrefix("gh:"), true) } }.sortedByDescending { it.updated }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Steam.bg)) {
        Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton({ if (sel != null) sel = null else onBack() }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = androidx.compose.ui.graphics.Color.White) }
            Text(sel?.name ?: UMUTK_CAT, Modifier.weight(1f), color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, letterSpacing = 2.sp)
            if (sel == null) androidx.compose.material3.IconButton({ s.reloadGithub() }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Refresh, t("Yenile", "Reload"), tint = androidx.compose.ui.graphics.Color.White) }
        }
        val cur = sel
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            if (cur != null) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        MyIcon(cur, 72)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(cur.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(cur.tagline, color = Steam.dim, fontSize = 13.sp)
                        }
                    }
                }
                if (cur.shots.isNotEmpty()) item {
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(cur.shots) { f -> AsyncImage("file:///android_asset/umutk/$f", null, Modifier.height(380.dp).clip(RoundedCornerShape(6.dp)).background(Steam.box), contentScale = ContentScale.FillHeight) }
                    }
                }
                item { SteamSection("README") }
                item { Text(cur.readme, Modifier.padding(16.dp, 4.dp), color = Steam.text, fontSize = 14.sp, lineHeight = 21.sp) }
                item {
                    Text("GitHub · ${cur.repo}", Modifier.padding(16.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.btn).clickable { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/${cur.repo}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                item { SteamSection(t("Uygulamalarım", "My apps") + if (s.ghLoading) "  ·  " + t("yükleniyor…", "loading…") else "") }
                items(myApps, key = { it.name }) { a ->
                    // an app with a release opens like any other app (install, update, open); without one, its README card
                    val gh = s.ghMap.values.firstOrNull { it.pkg.removePrefix("gh:").equals(a.repo, true) }
                    Row(Modifier.fillMaxWidth().clickable { if (gh != null) onOpen(gh.pkg) else sel = a }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MyIcon(a, 52)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(a.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(a.tagline, color = Steam.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(gh?.versionName ?: t("sürüm yok", "no release"), Modifier.padding(start = 8.dp), color = if (gh != null) Steam.link else Steam.dim, fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (mine.isNotEmpty()) item { SteamSection(t("Diğer uygulamalarım", "More of my apps")) }
                items(mine, key = { it.pkg }) { GhRow(it, onOpen) }
            }
        }
    }
}

/** GitHub sources: other people's repos whose releases carry an Android APK (ReVanced Manager, Morphe, root tools...). */
@Composable
fun GithubSourcesList(s: Store, onOpen: (String) -> Unit) {
    LaunchedEffect(Unit) { s.loadGithubSources(); s.loadAwesome() }
    var adding by remember { mutableStateOf("") }
    val others = s.ghMap.values.filter { it.categories.firstOrNull() != "by UmutK" }.groupBy { it.categories.firstOrNull().orEmpty() }
        .toList().sortedBy { (g, _) -> if (g.startsWith("Awesome") || g.startsWith("Open-source Android")) 1 else 0 }
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
