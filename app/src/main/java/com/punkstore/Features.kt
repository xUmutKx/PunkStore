package com.punkstore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = Color.White) }
        Text(title.uppercase(), color = Color.White, fontSize = 18.sp, letterSpacing = 1.5.sp, fontFamily = LogoFont)
    }
}

private class Feat(val key: String, val icon: ImageVector, val tr: String, val en: String, val dTr: String, val dEn: String)

private val FEATS = listOf(
    Feat("downloads", Icons.Filled.Download, "İndirmeler", "Downloads", "Süren indirmeler ve geçmiş", "Active downloads and history"),
    Feat("collections", Icons.Filled.Folder, "Koleksiyonlar", "Collections", "Uygulamalarını gruplara ayır", "Group your apps"),
    Feat("pinned", Icons.Filled.PushPin, "Favoriler", "Pinned", "Sabitlediğin uygulamalar", "Apps you pinned"),
    Feat("recent", Icons.Filled.History, "Son bakılanlar", "Recently viewed", "Son 40 uygulama", "Last 40 apps"),
    Feat("daily", Icons.Filled.WbSunny, "Günün uygulaması", "App of the day", "Her gün farklı bir öneri", "A new pick every day"),
    Feat("random", Icons.Filled.Casino, "Rastgele uygulama", "Random app", "Şansına bir uygulama aç", "Open a random app"),
    Feat("stats", Icons.Filled.BarChart, "İstatistikler", "Statistics", "Kategori ve kaynak grafikleri", "Category and source charts"),
    Feat("storage", Icons.Filled.Storage, "Depolama", "Storage", "Kurulu uygulamalar boyuta göre", "Installed apps by size"),
    Feat("cleanup", Icons.Filled.CleaningServices, "Temizlik önerileri", "Cleanup tips", "Eski ve hiç açılmayanlar", "Old and never-opened apps"),
    Feat("installed", Icons.Filled.Info, "Kurulu uygulama bilgisi", "Installed app info", "İzinler, hedef SDK, tarihler, APK yedekle", "Permissions, target SDK, dates, APK backup"),
    Feat("backup", Icons.Filled.Backup, "Yedekle / geri yükle", "Backup / restore", "İstek listesi, kütüphane, koleksiyonlar", "Wishlist, library, collections"),
    Feat("share", Icons.Filled.Share, "Kütüphaneyi paylaş", "Share library", "Uygulama listeni metin olarak gönder", "Send your app list as text"),
    Feat("presets", Icons.Filled.Tune, "Filtre hazırları", "Filter presets", "Tek dokunuşla hazır filtreler", "One-tap filter presets"),
    Feat("open", Icons.Filled.ContentPaste, "Paket adıyla aç", "Open by package", "com.örnek.uygulama yaz → aç", "Type com.example.app → open"),
    Feat("steamsearch", Icons.Filled.Search, "Steam'de ara", "Search Steam", "Steam mağazasında oyun ara", "Search the Steam store"),
    Feat("achv", Icons.Filled.EmojiEvents, "Başarımlar", "Achievements", "${ACHIEVEMENTS.size} başarım", "${ACHIEVEMENTS.size} achievements"),
)

private fun copy(ctx: Context, text: String) { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PunkStore", text)) }

/** Özellikler alt sayfaları. kind: downloads, collections, pinned, recent, stats, storage, cleanup, installed, backup, share, presets, open, steamsearch */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureScreen(s: Store, kind: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val title = FEATS.firstOrNull { it.key == kind }?.let { t(it.tr, it.en) } ?: kind
    Column(Modifier.fillMaxSize()) {
        Header(title, onBack)
        when (kind) {
            "downloads" -> LazyColumn {
                val tasks = s.dl.tasks.values.sortedWith(compareBy<DlTask>({ it.state.ordinal.let { o -> if (o == DlState.DOWNLOADING.ordinal) -1 else o } }, { it.created }))
                item {
                    Row(Modifier.padding(16.dp, 12.dp, 8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t("KUYRUK", "QUEUE") + if (tasks.isNotEmpty()) " (${tasks.size})" else "", Modifier.weight(1f), color = Steam.btn, fontSize = 13.sp, letterSpacing = 1.sp)
                        if (tasks.any { it.state == DlState.DOWNLOADING || it.state == DlState.QUEUED }) TextButton({ s.dl.pauseAll() }) { Text(t("Tümünü duraklat", "Pause all")) }
                        if (tasks.any { it.state == DlState.PAUSED || it.state == DlState.FAILED }) TextButton({ s.dl.resumeAll() }) { Text(t("Tümünü sürdür", "Resume all")) }
                    }
                }
                if (tasks.isEmpty()) item { Text(t("Şu an indirme yok. İndirmeler kaldığı yerden devam eder; ağ koparsa kendiliğinden yeniden dener.", "No downloads. Downloads resume where they left off and retry automatically."), Modifier.padding(16.dp), color = Steam.dim) }
                items(tasks, key = { it.pkg }) { tk ->
                    val a = s.anyPkg(tk.pkg)
                    Row(Modifier.animateItem().fillMaxWidth().padding(12.dp, 4.dp).skinBg().clickable { onOpen(tk.pkg) }.padding(12.dp), verticalAlignment = Alignment.Top) {
                        if (a != null) AppIcon(a, 48, 8)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tk.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                Text(when (tk.source) { "PLAY" -> "Google Play"; "FDROID" -> "F-Droid"; else -> tk.source }, fontSize = 11.sp, color = Steam.dim)
                            }
                            DownloadLine(s, tk.pkg)
                        }
                    }
                }
                if (tasks.any { it.state == DlState.DONE || it.state == DlState.FAILED }) item { TextButton({ s.dl.clearFinished() }, Modifier.padding(horizontal = 8.dp)) { Text(t("Bitenleri / hatalıları temizle", "Clear finished / failed")) } }
                item { Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(t("GEÇMİŞ", "HISTORY"), Modifier.weight(1f), color = Steam.btn, fontSize = 13.sp, letterSpacing = 1.sp); TextButton({ s.clearHistory() }) { Text(t("Temizle", "Clear")) } } }
                items(s.history.toList()) { h ->
                    val p = h.split('~'); val pkg = p.getOrNull(0).orEmpty()
                    Row(Modifier.fillMaxWidth().clickable { onOpen(pkg) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.getOrNull(1).orEmpty(), Modifier.weight(1f), color = Color.White); Text(df.format(Date(p.getOrNull(2)?.toLongOrNull() ?: 0L)), fontSize = 11.sp, color = Steam.dim)
                    }
                }
            }
            "collections" -> {
                var newName by remember { mutableStateOf("") }
                LazyColumn {
                    item { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newName, { newName = it.take(24) }, Modifier.weight(1f), singleLine = true, placeholder = { Text(t("Yeni koleksiyon adı", "New collection name")) })
                        Button({ s.addCollection(newName); newName = "" }, Modifier.padding(start = 8.dp)) { Text(t("Ekle", "Add")) } } }
                    if (s.collections.isEmpty()) item { Text(t("Henüz koleksiyon yok. Bir uygulamanın sayfasında 'Koleksiyon' düğmesiyle ekle.", "No collections yet. Use 'Collection' on an app page."), Modifier.padding(16.dp), color = Steam.dim) }
                    s.collections.keys.sorted().forEach { n ->
                        item(key = "h$n") { Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) { Text("$n (${s.collections[n].orEmpty().size})", Modifier.weight(1f), color = Steam.btn, fontWeight = FontWeight.Bold); TextButton({ s.deleteCollection(n) }) { Text(t("Sil", "Delete")) } } }
                        items(s.collections[n].orEmpty().mapNotNull { s.resolve(it) }, key = { "$n${it.pkg}" }) { AppRow(s, it, onOpen) }
                    }
                }
            }
            "pinned" -> LazyColumn { val l = s.pins.mapNotNull { s.resolve(it) }; if (l.isEmpty()) item { Text(t("Favori yok. Uygulama sayfasındaki ☆ düğmesi.", "No pins yet. Use ☆ on an app page."), Modifier.padding(16.dp), color = Steam.dim) }; items(l, key = { it.pkg }) { AppRow(s, it, onOpen) } }
            "recent" -> LazyColumn { val l = s.recent.mapNotNull { s.resolve(it) }; if (l.isEmpty()) item { Text(t("Henüz bir şey açmadın.", "Nothing viewed yet."), Modifier.padding(16.dp), color = Steam.dim) }; items(l, key = { it.pkg }) { AppRow(s, it, onOpen) } }
            "stats" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                val inst = s.apps.filter { s.isInstalled(it) }
                @Composable fun Bar(label: String, v: Int, max: Int) = Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(110.dp), fontSize = 12.sp, color = Steam.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(14.dp).background(Steam.card)) { Box(Modifier.fillMaxWidth(v.toFloat() / maxOf(max, 1)).fillMaxHeight().background(Steam.btn)) }
                    Text(" $v", fontSize = 12.sp, color = Steam.dim)
                }
                Text(t("Genel", "Overview"), color = Steam.btn, letterSpacing = 1.sp)
                listOf(t("Seviye", "Level") to "${s.level} (${s.xp} XP)", t("Kurulu (katalogdaki)", "Installed (in catalogs)") to "${inst.size}", t("Toplam açılış", "Total launches") to "${s.launches.values.sum()}", t("Yükleme", "Downloads") to "${s.getCount}", t("İstek listesi", "Wishlist") to "${s.wishlist.size}", t("Başarım", "Achievements") to "${s.achUnlocked.size}/${ACHIEVEMENTS.size}", t("Gün serisi", "Streak") to "${s.streak}", t("Keşfet'te beğenilen", "Liked in Discover") to "${s.liked}").forEach { (k, v) -> Row(Modifier.padding(vertical = 2.dp)) { Text(k, Modifier.weight(1f), color = Steam.dim); Text(v, color = Color.White, fontWeight = FontWeight.Bold) } }
                Text(t("Kaynağa göre kurulu", "Installed by source"), Modifier.padding(top = 18.dp, bottom = 6.dp), color = Steam.btn, letterSpacing = 1.sp)
                val bySrc = inst.groupingBy { if (it.source == "PLAY") "Google Play" else "F-Droid" }.eachCount()
                bySrc.forEach { (k, v) -> Bar(k, v, bySrc.values.maxOrNull() ?: 1) }
                Text(t("Kategoriye göre kurulu", "Installed by category"), Modifier.padding(top = 18.dp, bottom = 6.dp), color = Steam.btn, letterSpacing = 1.sp)
                val byCat = inst.flatMap { it.categories }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
                byCat.forEach { Bar(I18n.category(it.key), it.value, byCat.firstOrNull()?.value ?: 1) }
                Text(t("Zevkin (Keşfet)", "Your taste (Discover)"), Modifier.padding(top = 18.dp, bottom = 6.dp), color = Steam.btn, letterSpacing = 1.sp)
                Text(s.tasteTop.joinToString(" · ") { I18n.category(it) }.ifBlank { t("Henüz yok — Keşfet'te kaydır.", "None yet — swipe in Discover.") }, color = Steam.text)
            }
            "storage", "cleanup", "installed" -> InstalledList(s, kind, onOpen)
            "backup" -> {
                var txt by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("İstek listesi, kütüphane, favoriler ve koleksiyonlar tek bir metin olarak dışa aktarılır; başka cihazda yapıştırıp geri yükleyebilirsin.", "Wishlist, library, pins and collections export as one text; paste it on another device to restore."), color = Steam.dim, fontSize = 13.sp)
                    Button({ copy(ctx, s.exportJson()); msg = t("Panoya kopyalandı ✓", "Copied to clipboard ✓") }) { Text(t("Dışa aktar (panoya kopyala)", "Export (copy to clipboard)")) }
                    OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), label = { Text(t("Yedek metnini yapıştır", "Paste backup text")) }, minLines = 3)
                    Button({ msg = if (s.importJson(txt)) t("İçe aktarıldı ✓", "Imported ✓") else t("Geçersiz yedek", "Invalid backup") }) { Text(t("İçe aktar", "Import")) }
                    OutlinedButton({ (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.let { txt = it.toString() } }) { Text(t("Panodan yapıştır", "Paste from clipboard")) }
                    if (msg.isNotBlank()) Text(msg, color = Steam.greenA)
                }
            }
            "share" -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val text = remember(s.libAdded.size) { s.libApps.joinToString("\n") { "• ${it.name} (${it.pkg})" } }
                Text(text.ifBlank { t("Kütüphane boş.", "Library is empty.") }, Modifier.weight(1f, false).verticalScroll(rememberScrollState()), color = Steam.text, fontSize = 13.sp)
                Button({ ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "Punk Store — ${s.userName}\n$text"), null)) }, enabled = text.isNotBlank()) { Text(t("Paylaş", "Share")) }
            }
            "presets" -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                var msg by remember { mutableStateOf("") }
                listOf(
                    Triple(t("Reklamsız + izleyicisiz FOSS", "Ad-free + tracker-free FOSS"), Filters(noAds = true, noTracking = true, foss = true), ""),
                    Triple(t("Yüksek puanlı ücretsiz (Play)", "Top-rated free (Play)"), Filters(minRating = 4f, freeOnly = true, source = 2, sort = SortBy.RATING), ""),
                    Triple(t("Küçük boyutlu", "Small size"), Filters(sort = SortBy.SIZE), ""),
                    Triple(t("En son güncellenenler", "Recently updated"), Filters(sort = SortBy.UPDATED), ""),
                    Triple(t("Filtreleri kapat", "Clear filters"), Filters(), ""),
                ).forEach { (n, f, _) -> OutlinedButton({ s.applyFilters(f); msg = t("Uygulandı: $n", "Applied: $n") }, Modifier.fillMaxWidth()) { Text(n) } }
                if (msg.isNotBlank()) Text(msg, color = Steam.greenA)
            }
            "open" -> {
                var pkg by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(pkg, { pkg = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("com.example.app") })
                    Button({ scope.launch { val r = s.openByPackage(pkg); if (r != null) onOpen(r) else msg = t("Bulunamadı", "Not found") } }, enabled = pkg.contains('.')) { Text(t("Aç", "Open")) }
                    if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error)
                }
            }
            "steamsearch" -> {
                var q by remember { mutableStateOf("") }; var res by remember { mutableStateOf<List<AppItem>>(emptyList()) }
                LaunchedEffect(q) { if (q.length >= 2) { kotlinx.coroutines.delay(400); res = s.searchSteamStore(q) } else res = emptyList() }
                Column { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true, placeholder = { Text(t("Steam oyunu ara", "Search Steam games")) }); LazyColumn { items(res, key = { it.pkg }) { AppRow(s, it, onOpen) } } }
            }
        }
    }
}

private class InstApp(val pkg: String, val name: String, val size: Long, val first: Long, val last: Long, val target: Int, val perms: List<String>, val src: String)

@Composable
private fun InstalledList(s: Store, kind: String, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var sel by remember { mutableStateOf<InstApp?>(null) }
    var msg by remember { mutableStateOf("") }
    val list = remember(s.installed.size) {
        val pm = ctx.packageManager
        s.installed.keys.mapNotNull { p ->
            runCatching {
                val pi = pm.getPackageInfo(p, PackageManager.GET_PERMISSIONS); val ai = pi.applicationInfo ?: return@runCatching null
                if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0) return@runCatching null
                val files = listOf(ai.sourceDir) + (ai.splitSourceDirs?.toList().orEmpty())
                InstApp(p, pm.getApplicationLabel(ai).toString(), files.sumOf { File(it).length() }, pi.firstInstallTime, pi.lastUpdateTime, ai.targetSdkVersion, pi.requestedPermissions?.toList().orEmpty(), ai.sourceDir)
            }.getOrNull()
        }
    }
    val shown = when (kind) {
        "storage" -> list.sortedByDescending { it.size }
        "cleanup" -> list.filter { (s.launches[it.pkg] ?: 0) == 0 && System.currentTimeMillis() - it.last > 90L * 86400000 }.sortedBy { it.last }
        else -> list.sortedBy { it.name.lowercase() }
    }
    LazyColumn {
        if (kind == "cleanup") item { Text(t("Punk Store üzerinden hiç açılmamış ve 90 günden uzun süredir güncellenmemiş uygulamalar. Kaldırmadan önce kendin karar ver.", "Apps never opened via Punk Store and not updated in 90+ days. Decide yourself before removing."), Modifier.padding(16.dp), color = Steam.dim, fontSize = 13.sp) }
        if (msg.isNotBlank()) item { Text(msg, Modifier.padding(16.dp), color = Steam.greenA) }
        items(shown, key = { it.pkg }) { a ->
            Row(Modifier.fillMaxWidth().clickable { sel = a }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(a.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(a.pkg, fontSize = 11.sp, color = Steam.dim, maxLines = 1) }
                Text(sizeText(a.size), color = Steam.link, fontSize = 13.sp)
            }
        }
    }
    sel?.let { a ->
        AlertDialog(onDismissRequest = { sel = null }, title = { Text(a.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(a.pkg, fontSize = 12.sp); Spacer(Modifier.height(6.dp))
                Text(t("Boyut: ", "Size: ") + sizeText(a.size)); Text(t("Hedef SDK: ", "Target SDK: ") + a.target)
                Text(t("İlk kurulum: ", "Installed: ") + df.format(Date(a.first))); Text(t("Son güncelleme: ", "Updated: ") + df.format(Date(a.last)))
                Text(t("İzinler (${a.perms.size}):", "Permissions (${a.perms.size}):"), Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                a.perms.take(40).forEach { Text("• " + it.substringAfterLast('.'), fontSize = 12.sp) }
            } },
            confirmButton = { TextButton({ msg = backupApk(ctx, a.pkg); sel = null }) { Text(t("APK yedekle", "Back up APK")) } },
            dismissButton = { Row { TextButton({ s.uninstall(ctx, a.pkg); sel = null }) { Text(t("Kaldır", "Uninstall")) }; TextButton({ s.byPkg(a.pkg)?.let { onOpen(a.pkg) }; sel = null }) { Text(t("Sayfa", "Page")) } } })
    }
}

/** Kurulu uygulamanın APK'sını İndirilenler/PunkStore klasörüne kopyalar (MediaStore; izin gerektirmez). */
fun backupApk(ctx: Context, pkg: String): String = runCatching {
    val ai = ctx.packageManager.getApplicationInfo(pkg, 0); val ver = ctx.packageManager.getPackageInfo(pkg, 0).versionName ?: "0"
    val v = android.content.ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, "$pkg-$ver.apk"); put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive"); put(MediaStore.Downloads.RELATIVE_PATH, "Download/PunkStore") }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { o -> File(ai.sourceDir).inputStream().use { it.copyTo(o) } }
    t("Yedeklendi: Download/PunkStore/$pkg-$ver.apk", "Saved: Download/PunkStore/$pkg-$ver.apk")
}.getOrElse { t("Yedeklenemedi: ", "Backup failed: ") + it.message }
