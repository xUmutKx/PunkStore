@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.punkstore

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val W = Color.White

/** Kütüphane filtresi (Steam'deki "Tümü / Kurulu / …" açılır listesinin karşılığı, çip olarak). */
private class LibFilter(val key: String, val label: String, val test: (AppItem) -> Boolean)

/** Göreli zaman: "3 saat önce" */
private fun ago(ms: Long): String = if (ms <= 0) "" else DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Steam mobil kütüphanesi gibi: son oynanan vitrini, filtre çipleri, gruplu (açılır/kapanır) ızgara ya da liste, uzun basınca menü. */
@Composable
fun SteamLibrary(s: Store, onOpen: (String) -> Unit, onNav: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var q by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateListOf<String>() }
    var colFor by remember { mutableStateOf<AppItem?>(null) }

    val all = s.libApps
    val filters = buildList {
        add(LibFilter("all", t("Tümü", "All")) { true })
        add(LibFilter("installed", t("Kurulu", "Installed")) { s.isInstalled(it) })
        add(LibFilter("notinst", t("Kurulu değil", "Not installed")) { !s.isInstalled(it) && it.source != "STEAM" })
        add(LibFilter("updates", t("Güncellemeler", "Updates")) { s.hasUpdate(it) })
        add(LibFilter("dl", t("İndirilenler", "Downloading")) { s.dl[it.pkg] != null })
        add(LibFilter("pinned", t("Sabitlenen", "Pinned")) { it.pkg in s.pins })
        add(LibFilter("played", t("Oynanan", "Played")) { s.lastPlayed.containsKey(it.pkg) })
        add(LibFilter("games", t("Oyunlar", "Games")) { a -> a.source == "STEAM" || a.categories.any { it.contains("Game", true) } })
        add(LibFilter("fdroid", "F-Droid") { it.source == "FDROID" })
        add(LibFilter("play", "Google Play") { it.source == "PLAY" })
        add(LibFilter("steam", "Steam") { it.source == "STEAM" })
        add(LibFilter("local", t("Cihaz", "Device")) { it.source == "LOCAL" })
        s.collections.forEach { (n, l) -> add(LibFilter("col:$n", "▣ $n") { it.pkg in l }) }
    }
    val f = filters.firstOrNull { it.key == filter } ?: filters[0]
    val sorts = listOf(t("Son oynanan", "Recently played"), t("A-Z", "A-Z"), t("En çok açılan", "Most played"), t("Son güncellenen", "Recently updated"), t("Boyut", "Size"), t("Oynama süresi", "Playtime"))
    val list = remember(all, filter, sort, q, s.installed.toMap(), s.pins.toList(), s.lastPlayed.toMap(), s.dl.tasks.keys.toSet()) {
        all.filter { f.test(it) && (q.isBlank() || it.name.contains(q.trim(), true) || it.pkg.contains(q.trim(), true)) }.let { l ->
            when (sort) {
                1 -> l.sortedBy { it.name.lowercase() }
                2 -> l.sortedByDescending { s.launches[it.pkg] ?: 0 }
                3 -> l.sortedByDescending { it.updated }
                5 -> l.sortedByDescending { it.extra["minutes"]?.toIntOrNull() ?: 0 }
                4 -> l.sortedByDescending { it.apkSize }
                else -> l.sortedWith(compareByDescending<AppItem> { s.lastPlayed[it.pkg] ?: 0L }.thenBy { it.name.lowercase() })
            }
        }.sortedByDescending { it.pkg in s.pins }
    }
    // Steam gibi gruplar (yalnızca "Tümü"de ve aramasızken)
    val sections: List<Pair<String, List<AppItem>>> = if (filter == "all" && q.isBlank()) {
        val dl = list.filter { s.dl[it.pkg] != null }
        val upd = list.filter { s.hasUpdate(it) && it !in dl }
        val inst = list.filter { s.isInstalled(it) && it !in dl && it !in upd }
        val rest = list.filter { it !in dl && it !in upd && it !in inst }
        listOf(t("İNDİRİLENLER", "DOWNLOADS") to dl, t("GÜNCELLEME BEKLEYENLER", "UPDATES PENDING") to upd, t("KURULU", "INSTALLED") to inst, t("KURULU DEĞİL", "NOT INSTALLED") to rest).filter { it.second.isNotEmpty() }
    } else listOf(f.label.uppercase() to list)
    val recent = remember(all, s.lastPlayed.toMap()) { all.filter { s.lastPlayed.containsKey(it.pkg) && s.isInstalled(it) }.sortedByDescending { s.lastPlayed[it.pkg] } .take(12) }

    fun open(a: AppItem) { if (a.source == "LOCAL") s.open(ctx, a.pkg) else onOpen(a.pkg) }

    Column(Modifier.fillMaxSize().background(if (Steam.carbon) Color.Transparent else Steam.bg)) {
        Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.dp))
            Text(t("KÜTÜPHANE", "LIBRARY"), Modifier.weight(1f), color = W, fontSize = 20.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            IconButton({ grid = !grid }) { Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, t("Görünüm", "View"), tint = W) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- son oynanan vitrini
            if (filter == "all" && q.isBlank() && recent.isNotEmpty()) {
                item(key = "hero") { LibHero(s, recent.first()) { open(it) } }
                if (recent.size > 1) item(key = "recent") {
                    Column {
                        Text(t("SON OYNANANLAR", "RECENT GAMES"), Modifier.padding(vertical = 6.dp), color = Steam.dim, fontSize = 13.sp, letterSpacing = 1.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recent.drop(1), key = { it.pkg }) { a ->
                                Column(Modifier.width(96.dp).animateItem().pressScale({ open(a) })) {
                                    Banner(a, Modifier.fillMaxWidth().aspectRatio(.67f).clip(RoundedCornerShape(Steam.corner.dp)), icon = 0)
                                    Text(a.name, color = W, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    Text(ago(s.lastPlayed[a.pkg] ?: 0), color = Steam.dim, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "quick") { QuickRow(QA_LIBRARY, onNav) }
            // ---- arama + sıralama
            item(key = "search") {
                Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.panel).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = Steam.dim, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = W, fontSize = 15.sp), cursorBrush = SolidColor(W),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text(t("Kütüphanede ara (${all.size})", "Search library (${all.size})"), color = Steam.dim, fontSize = 15.sp); inner() } })
                    if (q.isNotEmpty()) Icon(Icons.Filled.Close, null, tint = Steam.dim, modifier = Modifier.clickable { q = "" })
                    Box {
                        Row(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.btn).clickable { sortMenu = true }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sort, null, tint = W, modifier = Modifier.size(16.dp)); Text(" " + sorts[sort], color = W, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }) { sorts.forEachIndexed { i, n -> DropdownMenuItem({ Text(n) }, { sort = i; sortMenu = false }, leadingIcon = { if (i == sort) Icon(Icons.Filled.Check, null) }) } }
                    }
                }
            }
            // ---- filtre çipleri (sayılı)
            item(key = "chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filters.map { it to all.count(it.test) }.filter { (lf, n) -> n > 0 || lf.key == "all" || lf.key == filter }, key = { it.first.key }) { (lf, n) ->
                        val on = lf.key == filter
                        Text("${lf.label}  $n", Modifier.animateItem().clip(RoundedCornerShape(Steam.corner.dp)).background(if (on) Steam.btn else Steam.panel)
                            .border(1.dp, if (on) Color(0x6667C1F5) else Color.Transparent, RoundedCornerShape(Steam.corner.dp)).pressScale({ filter = lf.key }).padding(horizontal = 12.dp, vertical = 7.dp),
                            color = if (on) W else Steam.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            item(key = "stats") {
                val inst = all.count { s.isInstalled(it) }
                val upd = all.count { s.hasUpdate(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("${list.size} öğe · $inst kurulu", "${list.size} items · $inst installed") + (all.filter { s.isInstalled(it) }.sumOf { it.apkSize }.takeIf { it > 0 }?.let { " · ${sizeText(it)}" } ?: ""), Modifier.weight(1f), color = Steam.dim, fontSize = 12.sp)
                    if (upd > 0) Text(t("Tümünü güncelle ($upd)", "Update all ($upd)"), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.greenB).pressScale({ all.filter { s.hasUpdate(it) }.forEach { s.getOrUpdate(it, ctx) } }).padding(horizontal = 10.dp, vertical = 5.dp), color = W, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (list.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.VideoLibrary, null, tint = Steam.dim, modifier = Modifier.size(56.dp))
                    Text(if (q.isNotBlank() || filter != "all") t("Bu filtreye uyan öğe yok.", "Nothing matches this filter.") else t("Kütüphane boş. Bir uygulama yükle ya da sayfasından 'Kütüphaneye ekle' de.", "Your library is empty. Install something or use 'Add to library'."),
                        Modifier.padding(top = 10.dp), color = Steam.dim, textAlign = TextAlign.Center)
                    if (filter != "all") TextButton({ filter = "all"; q = "" }) { Text(t("Filtreyi temizle", "Clear filter")) }
                }
            }
            sections.forEach { (title, apps) ->
                val open = title !in collapsed
                if (sections.size > 1 || filter != "all") item(key = "h:$title") {
                    val rot by animateFloatAsState(if (open) 0f else -90f, label = "rot")
                    Row(Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).clickable { if (open) collapsed.add(title) else collapsed.remove(title) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Steam.dim, modifier = Modifier.rotate(rot))
                        Text("$title (${apps.size})", color = W, fontSize = 14.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.weight(1f).padding(start = 10.dp).height(1.dp).background(Steam.edgeLo))
                    }
                }
                if (open) {
                    if (grid) items(apps.chunked(3), key = { r -> "g:$title:" + r.joinToString { it.pkg } }) { row ->
                        Row(Modifier.animateItem(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { a -> LibCell(s, a, Modifier.weight(1f), { open(a) }) { colFor = it } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    } else items(apps, key = { "l:$title:" + it.pkg }) { a -> LibRow(s, a, Modifier.animateItem(), { open(a) }) { colFor = it } }
                }
            }
        }
    }
    colFor?.let { CollectionDialog(s, it) { colFor = null } }
}

/** Büyük "son oynanan" kartı: kapak, ad, ne zaman / kaç kez açıldığı, OYNA düğmesi. */
@Composable
private fun LibHero(s: Store, a: AppItem, onOpen: (AppItem) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 8f).clip(RoundedCornerShape(Steam.corner.dp)).pressScale({ onOpen(a) })) {
        Banner(a, Modifier.fillMaxSize(), icon = 0)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
        Text(t("SON OYNANAN", "LAST PLAYED"), Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp), color = Steam.link, fontSize = 11.sp, letterSpacing = 1.sp)
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a, 44, 8)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(a.name, color = W, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(ago(s.lastPlayed[a.pkg] ?: 0).ifBlank { null }, s.launches[a.pkg]?.let { t("$it kez açıldı", "launched $it×") }).joinToString(" · "), color = Steam.text, fontSize = 12.sp, maxLines = 1)
            }
            ActionButton(s, a, compact = true)
        }
    }
}

/** Izgara hücresi: dikey kapak, durum etiketi, indirme çubuğu; uzun bas = menü. */
@Composable
private fun LibCell(s: Store, a: AppItem, modifier: Modifier, onOpen: () -> Unit, onCollection: (AppItem) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val task = s.dl[a.pkg]
    Box(modifier.aspectRatio(.67f).clip(RoundedCornerShape(Steam.corner.dp)).pressScale(onOpen, onLongClick = { menu = true })) {
        Banner(a, Modifier.fillMaxSize(), icon = 0)
        Column(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            AppIcon(a, 34, 6)
            Text(a.name, color = W, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        val tag = when {
            task != null -> dlTag(task) to Steam.btn
            s.hasUpdate(a) -> t("GÜNCELLE", "UPDATE") to Steam.greenB
            !s.isInstalled(a) && a.source != "STEAM" -> t("YÜKLE", "INSTALL") to Color(0xCC3D4450)
            else -> null
        }
        tag?.let { Text(it.first, Modifier.align(Alignment.TopEnd).background(it.second).padding(horizontal = 6.dp, vertical = 2.dp), color = W, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        Row(Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Filled.MoreVert, t("Menü", "Menu"), tint = W, modifier = Modifier.padding(4.dp).size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x99000000)).clickable { menu = true }.padding(2.dp))
            if (a.pkg in s.pins) Icon(Icons.Filled.PushPin, null, tint = Steam.link, modifier = Modifier.padding(top = 6.dp).size(16.dp))
        }
        if (task != null) DlBar(if (task.state == DlState.INSTALLING || task.state == DlState.VERIFYING) 1f else task.progress, Modifier.align(Alignment.BottomCenter))
        LibMenu(s, a, menu, { menu = false }, onCollection)
    }
}

private fun dlTag(tk: DlTask) = when (tk.state) {
    DlState.DOWNLOADING -> "%${(tk.progress * 100).toInt()}"; DlState.QUEUED -> t("SIRADA", "QUEUED"); DlState.PAUSED -> t("DURAKLADI", "PAUSED")
    DlState.FAILED -> t("HATA", "ERROR"); DlState.DONE -> "✓"; else -> t("KURULUYOR", "INSTALLING")
}

/** Liste satırı: ikon, ad, durum + son oynama, eylem düğmesi; altında indirme ayrıntısı. */
@Composable
private fun LibRow(s: Store, a: AppItem, modifier: Modifier, onOpen: () -> Unit, onCollection: (AppItem) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Brush.verticalGradient(Steam.panelGrad)).pressScale(onOpen, onLongClick = { menu = true }).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box { AppIcon(a, 46, 8); LibMenu(s, a, menu, { menu = false }, onCollection) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (a.pkg in s.pins) Icon(Icons.Filled.PushPin, null, tint = Steam.link, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                    Text(a.name, color = W, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val status = when { s.hasUpdate(a) -> t("Güncelleme var", "Update available"); s.isInstalled(a) -> t("Kurulu", "Installed"); a.source == "STEAM" -> "Steam"; else -> t("Kurulu değil", "Not installed") }
                Text(listOfNotNull(status, a.extra["minutes"]?.toIntOrNull()?.takeIf { it > 0 }?.let { t("${it / 60} saat oynandı", "${it / 60} h played") }, s.lastPlayed[a.pkg]?.let { t("son: ", "last: ") + ago(it) }, s.launches[a.pkg]?.let { t("$it kez", "$it×") }, sizeText(a.apkSize).ifBlank { null }).joinToString(" · "),
                    color = if (s.hasUpdate(a)) Steam.greenA else Steam.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (a.source == "LOCAL") SmallIconBtn(Icons.Filled.PlayArrow, t("Aç", "Open"), onOpen) else ActionButton(s, a, Modifier.widthIn(min = 84.dp), compact = true)
            SmallIconBtn(Icons.Filled.MoreVert, t("Menü", "Menu")) { menu = true }
        }
        DownloadLine(s, a.pkg)
    }
}

/** Uzun basma menüsü: aç / yükle / güncelle, sabitle, koleksiyon, kütüphaneden çıkar, kaldır, uygulama bilgisi. */
@Composable
private fun LibMenu(s: Store, a: AppItem, open: Boolean, onClose: () -> Unit, onCollection: (AppItem) -> Unit) {
    val ctx = LocalContext.current
    DropdownMenu(open, onClose) {
        @Composable fun I(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, act: () -> Unit) = DropdownMenuItem({ Text(label) }, { onClose(); act() }, leadingIcon = { Icon(icon, null) })
        Text(a.name, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, maxLines = 1)
        if (s.isInstalled(a)) I(Icons.Filled.PlayArrow, t("Aç", "Open")) { s.open(ctx, a.pkg) }
        if (a.source != "LOCAL" && a.source != "STEAM" && (!s.isInstalled(a) || s.hasUpdate(a)) && s.dl[a.pkg]?.active != true) I(Icons.Filled.Download, if (s.hasUpdate(a)) t("Güncelle", "Update") else t("Yükle", "Install")) { s.getOrUpdate(a, ctx) }
        s.dl[a.pkg]?.let { tk -> if (tk.state != DlState.INSTALLING && tk.state != DlState.DONE) I(Icons.Filled.Close, t("İndirmeyi iptal et", "Cancel download")) { s.dl.cancel(a.pkg) } }
        I(Icons.Filled.PushPin, if (a.pkg in s.pins) t("Sabitlemeyi kaldır", "Unpin") else t("Sabitle", "Pin")) { s.togglePin(a) }
        I(Icons.Filled.Folder, t("Koleksiyona ekle…", "Add to collection…")) { onCollection(a) }
        if (a.pkg in s.libAdded) I(Icons.Filled.RemoveCircleOutline, t("Kütüphaneden çıkar", "Remove from library")) { s.toggleLibrary(a) }
        if (s.isInstalled(a)) I(Icons.Filled.Delete, t("Kaldır (sil)", "Uninstall")) { s.uninstall(ctx, a.pkg) }
        if (s.isInstalled(a)) I(Icons.Filled.Info, t("Uygulama bilgisi", "App info")) { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
