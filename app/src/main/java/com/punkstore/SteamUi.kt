package com.punkstore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val W = Color.White

/**
 * Akıllı kapak: yatay görsel varsa tam kaplar; dikey/kare ise bulanık arka plan + ortada net görsel;
 * hiç görsel yoksa ikonun bulanık büyütülmüş hâli + ortada net ikon (düz renk yerine).
 */
@Composable
fun Banner(a: AppItem, modifier: Modifier = Modifier, icon: Int = 0, fade: Boolean = true, contain: Boolean = false, image: String? = null) {
    val hue = (a.pkg.hashCode().mod(360)).toFloat()
    val c1 = Color.hsv(hue, .55f, .42f); val c2 = Color.hsv((hue + 40) % 360, .6f, .16f)
    var portrait by remember(a.pkg) { mutableStateOf(false) }
    val cover = image ?: a.cover
    Box(modifier.background(Brush.linearGradient(listOf(c1, c2)))) {
        if (cover != null) {
            if (portrait || contain) AsyncImage(cover, null, Modifier.fillMaxSize().blur(28.dp), contentScale = ContentScale.Crop, alpha = .7f)
            AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = if (portrait || contain) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { st -> val sz = st.painter.intrinsicSize; if (sz.height > 0 && sz.width / sz.height < 1.25f) portrait = true })
        } else {
            a.icon?.let { AsyncImage(it, null, Modifier.fillMaxSize().blur(36.dp), contentScale = ContentScale.Crop, alpha = .6f) }
            Box(Modifier.align(Alignment.Center)) { if (a.icon != null) AsyncImage(a.icon, null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) else Text(a.name.take(2).uppercase(), color = Color(0x55FFFFFF), fontSize = 44.sp, fontWeight = FontWeight.Black) }
        }
        if (fade) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
        if (icon > 0) Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) { AppIcon(a, icon, 8) }
    }
}

/** Steam'in indirim kutusu: yeşil % + (üstü çizili eski fiyat) + güncel fiyat; ücretsiz uygulamada "ÜCRETSİZ". */
@Composable
fun PriceTag(a: AppItem, modifier: Modifier = Modifier) {
    val free = a.price.isBlank() && a.discount == 0
    val disc = a.discount > 0
    Row(modifier.clip(RoundedCornerShape(2.dp)).background(Color(0xE60E141B)), verticalAlignment = Alignment.CenterVertically) {
        if (free || disc) Text(if (disc) "-${a.discount}%" else "-100%", Modifier.background(Color(0xFFA4D007)).padding(horizontal = 6.dp, vertical = 4.dp), color = Color(0xFF254007), fontWeight = FontWeight.Black, fontSize = 13.sp)
        Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            if (disc && a.origPrice.isNotBlank()) Text(a.origPrice, color = Color(0xFF738895), fontSize = 10.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, lineHeight = 11.sp)
            Text(if (free) t("ÜCRETSİZ", "FREE") else a.price, color = W, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun SteamTopBar2(s: Store, onNav: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Steam.topBrush).gloss().statusBarsPadding().padding(bottom = 4.dp).drawBehind { Steam.topAccent?.let { drawRect(it, androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) } }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).border(1.dp, Steam.edgeLo, RoundedCornerShape(Steam.corner.dp)).clickable { onNav("search") }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PunkLogo(15f, frame = false, color = Steam.text, accent = Steam.dim); Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Search, null, tint = Steam.dim)
            }
            Spacer(Modifier.width(4.dp))
            if (s.loading || s.checking) CircularProgressIndicator(Modifier.size(20.dp), color = Steam.blue, strokeWidth = 2.dp)
            else IconButton({ onNav("refresh") }) { Icon(Icons.Filled.MoreVert, null, tint = W) }
            Box(Modifier.clickable { onNav("profile") }) { Avatar(s, 44) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(t("MENÜ", "MENU"), color = Steam.text, fontSize = 15.sp, letterSpacing = 1.sp); Icon(Icons.Filled.KeyboardArrowDown, null, tint = Steam.text) }
                DropdownMenu(menu, { menu = false }) {
                    listOf(Triple("store", Icons.Filled.Storefront, t("Mağaza", "Store")), Triple("discover", Icons.Filled.Explore, t("Keşfet", "Discover")), Triple("library", Icons.Filled.VideogameAsset, t("Kütüphane", "Library")),
                        Triple("wishlist", Icons.Filled.Favorite, t("İstek listesi", "Wishlist")), Triple("downloads", Icons.Filled.Download, t("İndirmeler", "Downloads")), Triple("updates", Icons.Filled.Notifications, t("Güncellemeler", "Updates")),
                        Triple("achievements", Icons.Filled.EmojiEvents, t("Başarımlar", "Achievements")), Triple("profile", Icons.Filled.Person, t("Profil", "Profile")), Triple("settings", Icons.Filled.Settings, t("Ayarlar", "Settings")),
                        Triple("about", Icons.Filled.Info, t("Bilgi", "About")), Triple("refresh", Icons.Filled.Refresh, t("Kataloğu yenile", "Refresh catalog"))).forEach { (k, ic, l) ->
                        DropdownMenuItem({ Text(l) }, { menu = false; onNav(k) }, leadingIcon = { Icon(ic, null) })
                    }
                }
            }
            @Composable fun Nav(label: String, key: String, badge: Int = 0, color: Color = Steam.text) = BadgedBox({ if (badge > 0) Badge { Text("$badge") } }, Modifier.padding(end = 6.dp)) { Text(label, Modifier.clickable { onNav(key) }.padding(horizontal = 8.dp, vertical = 8.dp), color = color, fontSize = 15.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false) }
            Nav(t("İSTEK LİSTESİ", "WISHLIST"), "wishlist", s.wishlist.size)
            Nav(t("İNDİRMELER", "DOWNLOADS"), "downloads", s.busy.size)
            Nav(t("KEŞFET", "DISCOVER"), "discover")
            Nav(t("BAŞARIMLAR", "ACHIEVEMENTS"), "achievements", 0)
            Nav(t("SEVİYE ", "LEVEL ") + s.level, "profile", 0, Steam.btn)
            Nav("🔥 ${s.streak}", "stats", 0, Steam.link)
        }
    }
}

class QA(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tr: String, val en: String)
val QA_STORE = listOf(QA("daily", Icons.Filled.WbSunny, "Günün uygulaması", "App of the day"), QA("random", Icons.Filled.Casino, "Rastgele", "Random"), QA("steamsearch", Icons.Filled.SportsEsports, "Steam'de ara", "Search Steam"), QA("discover", Icons.Filled.Explore, "Keşfet", "Discover"), QA("presets", Icons.Filled.Tune, "Filtreler", "Filters"), QA("open", Icons.Filled.ContentPaste, "Paketle aç", "Open by package"))
val QA_LIBRARY = listOf(QA("collections", Icons.Filled.Folder, "Koleksiyonlar", "Collections"), QA("pinned", Icons.Filled.PushPin, "Favoriler", "Pinned"), QA("recent", Icons.Filled.History, "Son bakılanlar", "Recent"), QA("share", Icons.Filled.Share, "Paylaş", "Share"), QA("backup", Icons.Filled.Backup, "Yedek", "Backup"))
val QA_UPDATES = listOf(QA("downloads", Icons.Filled.Download, "İndirmeler", "Downloads"), QA("storage", Icons.Filled.Storage, "Depolama", "Storage"), QA("cleanup", Icons.Filled.CleaningServices, "Temizlik", "Cleanup"), QA("installed", Icons.Filled.Info, "Kurulu bilgisi", "Installed info"))

/** İkonlu kısayol satırı. */
@Composable
fun QuickRow(items: List<QA>, onNav: (String) -> Unit) = Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items.forEach { q -> Row(Modifier.clip(RoundedCornerShape(Steam.corner.dp)).skinBg().clickable { onNav(q.key) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(q.icon, null, tint = Steam.btn, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t(q.tr, q.en), color = W, fontSize = 13.sp, maxLines = 1, softWrap = false) } }
}

@Composable
private fun H(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = W, fontSize = 15.sp, letterSpacing = 1.sp)

@Composable
private fun SeeAll(onClick: () -> Unit) = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
    Text(t("Tümünü gör", "See All"), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Color(0xFFDCDEDF)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp), color = Color(0xFF1B1D23), fontWeight = FontWeight.SemiBold)
}

/** Büyük dikey kapsül (Steam ana sayfa kayan afişleri). */
@Composable
private fun BigCapsule(a: AppItem, onOpen: (String) -> Unit, w: Int = 300) {
    Box(Modifier.width(w.dp).height(w.dp * 3 / 4).clip(RoundedCornerShape(Steam.corner.dp)).clickable { onOpen(a.pkg) }) {
        Banner(a, Modifier.fillMaxSize(), icon = 0)
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            AppIcon(a, 48, 6)
            Text(a.name, color = W, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).widthIn(max = (w - 20).dp))
        }
        PriceTag(a, Modifier.align(Alignment.BottomEnd))
    }
}

/** İki sütunlu ızgara hücresi: afiş + altında fiyat şeridi. */
@Composable
private fun GridCell(s: Store, a: AppItem, modifier: Modifier, onOpen: (String) -> Unit) {
    Column(modifier.clickable { onOpen(a.pkg) }) {
        Banner(a, Modifier.fillMaxWidth().aspectRatio(1.75f).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)), icon = 30)
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(Steam.panelGrad)).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, Modifier.weight(1f), color = W, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.isInstalled(a)) Icon(Icons.Filled.CheckCircle, null, tint = Steam.greenA, modifier = Modifier.size(16.dp)) else PriceTag(a)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamStore(s: Store, onOpen: (String) -> Unit, onCategory: (String) -> Unit, onNav: (String) -> Unit = {}) {
    val featured = remember(s.apps) { (s.recentlyUpdated.filter { it.cover != null }).take(8) }
    val fresh = remember(s.apps) { s.newest.filter { it.cover != null }.take(16) }
    val recent = remember(s.apps) { s.recentlyUpdated.take(30) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            if (s.loading) Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(if (s.progress < .9f) t("Katalog indiriliyor…", "Downloading catalog…") else t("Katalog işleniyor…", "Processing catalog…"), fontSize = 12.sp, color = Steam.dim)
                LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().padding(top = 4.dp), color = Steam.btn)
            }
            // Mevsim afişi gibi başlık
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.linearGradient(Steam.hero))) {
                Text("PUNK\nSTORE", Modifier.align(Alignment.CenterStart).padding(start = 20.dp), color = W, fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
                Text(t("${s.apps.size} uygulama · F-Droid + Google Play", "${s.apps.size} apps · F-Droid + Google Play"), Modifier.align(Alignment.BottomStart).padding(20.dp), color = Color(0xFFFFE9C8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.Settings, null, Modifier.align(Alignment.CenterEnd).size(110.dp).padding(end = 10.dp), tint = Color(0x44FFFFFF))
            }
        }
        item { QuickRow(QA_STORE + QA("downloads", Icons.Filled.Download, "İndirmeler", "Downloads"), onNav) }
        if (featured.isNotEmpty()) item {
            val pager = rememberPagerState { featured.size }
            Column(Modifier.padding(top = 16.dp)) {
                HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp, pageSize = androidx.compose.foundation.pager.PageSize.Fixed(310.dp)) { i -> BigCapsule(featured[i], onOpen, 310) }
                Row(Modifier.padding(16.dp, 10.dp)) { repeat(featured.size) { Box(Modifier.padding(end = 4.dp).size(width = if (pager.currentPage == it) 26.dp else 10.dp, height = 4.dp).background(if (pager.currentPage == it) Color(0xFFBBBBBB) else Color(0xFF555B66))) } }
            }
        }
        if (s.wishlist.isNotEmpty()) { item { H(t("İstek listen", "Your wishlist")) }; item { AppCarousel(s, s.wishApps, onOpen) } }
        item { H(t("Kategoriye göz at", "Browse by category")); LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(s.categories.take(24)) { c -> Text(I18n.category(c), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).skinBg().clickable { onCategory(c) }.padding(horizontal = 14.dp, vertical = 10.dp), color = W, fontSize = 14.sp) } } }
        if (s.recommended.isNotEmpty()) { item { H(t("Senin için önerilenler", "Recommended for you")) }; item { AppCarousel(s, s.recommended, onOpen) } }
        listOf("specials" to t("Steam — indirimdekiler", "Steam — specials"), "top" to t("Steam — çok satanlar", "Steam — top sellers"), "new" to t("Steam — yeni çıkanlar", "Steam — new releases"), "soon" to t("Steam — yakında", "Steam — coming soon")).forEach { (k, title) ->
            s.steamLists[k]?.takeIf { it.isNotEmpty() }?.let { l -> item { H(title) }; item { AppCarousel(s, l, onOpen) } }
        }
        if (s.playTop.isNotEmpty()) { item { H("Google Play — " + t("en çok indirilenler", "Top free")) }; item { AppCarousel(s, s.playTop, onOpen) } }
        if (s.playGames.isNotEmpty()) { item { H("Google Play — " + t("oyunlar", "Games")) }; item { AppCarousel(s, s.playGames, onOpen) } }
        item { H(t("Yeni çıkanlar", "New releases")) }
        items(fresh.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { GridCell(s, it, Modifier.weight(1f), onOpen) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(8.dp)); SeeAll { onCategory("*") } }
        item { H(t("Son güncellenenler", "Recently updated")) }
        items(recent, key = { it.pkg }) { AppRow(s, it, onOpen) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------- KÜTÜPHANE: dikey kapak ızgarası ----------------
@Composable
fun SteamLibrary(s: Store, onOpen: (String) -> Unit, onNav: (String) -> Unit = {}) {
    var sort by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val names = listOf(t("Son eklenen", "Recent"), t("A-Z", "A-Z"), t("En çok açılan", "Most played"), t("Güncellenen", "Updated"))
    val list = remember(s.apps, s.installed.toMap(), s.libAdded.toList(), s.pins.toList(), sort, q, s.launches.toMap()) {
        s.libApps.filter { it.name.contains(q, true) }.let { l -> when (sort) { 1 -> l.sortedBy { it.name.lowercase() }; 2 -> l.sortedByDescending { s.launches[it.pkg] ?: 0 }; 3 -> l.sortedByDescending { it.updated }; else -> l }.sortedByDescending { it.pkg in s.pins } }
    }
    Column(Modifier.fillMaxSize().background(if (Steam.carbon) Color.Transparent else Steam.bg)) {
        Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("KÜTÜPHANE", "LIBRARY"), Modifier.weight(1f), color = W, fontSize = 20.sp, letterSpacing = 2.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { QuickRow(QA_LIBRARY, onNav) }
            item {
                Row(Modifier.fillMaxWidth().height(50.dp).background(Steam.panel).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = W, fontSize = 16.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(W),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text(t("Kütüphanede ara", "Search library"), color = Steam.dim); inner() } })
                    Icon(Icons.Filled.Search, null, tint = Steam.text)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("SIRALA:", "SORT BY:"), color = Steam.text, fontSize = 16.sp, letterSpacing = 1.sp); Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.btn).clickable { menu = true }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(names[sort], Modifier.weight(1f), color = W, fontWeight = FontWeight.Bold, fontSize = 18.sp); Icon(Icons.Filled.KeyboardArrowDown, null, tint = W)
                        }
                        DropdownMenu(menu, { menu = false }) { names.forEachIndexed { i, n -> DropdownMenuItem({ Text(n) }, { sort = i; menu = false }) } }
                    }
                }
                val upd = list.count { s.hasUpdate(it) }
                if (upd > 0) Text(t("$upd güncelleme bekliyor", "$upd updates pending"), Modifier.padding(top = 10.dp), color = Steam.greenA, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (list.isEmpty()) Text(t("Kütüphane boş. Bir uygulamanın sayfasından 'Kütüphaneye ekle' de.", "Your library is empty. Use 'Add to library' on an app page."), Modifier.padding(top = 20.dp), color = Steam.dim)
            }
            items(list.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { a ->
                        Box(Modifier.weight(1f).aspectRatio(.67f).clip(RoundedCornerShape(Steam.corner.dp)).clickable { onOpen(a.pkg) }) {
                            Banner(a, Modifier.fillMaxSize(), icon = 0)
                            Column(Modifier.align(Alignment.BottomStart).padding(6.dp)) { AppIcon(a, 36, 6); Text(a.name, color = W, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                            val tag = when { !s.isInstalled(a) -> t("YÜKLE", "INSTALL") to Steam.btn; s.hasUpdate(a) -> t("GÜNCELLE", "UPDATE") to Steam.greenA; else -> null }
                            tag?.let { Text(it.first, Modifier.align(Alignment.TopEnd).background(it.second).padding(horizontal = 6.dp, vertical = 2.dp), color = W, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------- GÜNCELLEMELER (zil sekmesi) ----------------
@Composable
fun SteamUpdates(s: Store, onOpen: (String) -> Unit, onNav: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val upd = s.apps.filter { s.isInstalled(it) && s.hasUpdate(it) }
    val ign = s.apps.filter { s.isInstalled(it) && it.pkg in s.ignored && (s.installed[it.pkg] ?: 0) < it.versionCode }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Text(t("GÜNCELLEMELER", "UPDATES"), Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(14.dp), color = W, fontSize = 20.sp, letterSpacing = 2.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        item { QuickRow(QA_UPDATES, onNav) }
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (s.checking) t("Denetleniyor…", "Checking…") else t("Son denetim: ", "Last check: ") + if (s.lastCheck > 0) df.format(Date(s.lastCheck)) else "-", color = Steam.text, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(s.autoCheck, { s.changeAutoCheck(it) }, Modifier.scale(.8f)); Text(t("Otomatik denetle + bildir", "Auto-check + notify"), color = Steam.dim, fontSize = 12.sp) }
                }
                Text(t("DENETLE", "CHECK NOW"), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.btn).clickable(enabled = !s.checking) { s.checkUpdates(true) }.padding(horizontal = 16.dp, vertical = 10.dp), color = W, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (s.checking) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Steam.btn)
        }
        s.selfUpdate?.let { su ->
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Brush.horizontalGradient(listOf(Steam.panel2, Steam.panel))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, null, tint = Steam.btn, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(t("Punk Store güncellemesi", "Punk Store update"), color = W, fontWeight = FontWeight.Bold); Text(su.summary, color = Steam.dim, fontSize = 12.sp) }
                    ActionButton(s, su, compact = true)
                }
            }
        }
        if (upd.isEmpty() && s.selfUpdate == null) item { Text(t("Her şey güncel. ✓", "Everything is up to date. ✓"), Modifier.padding(24.dp), color = Steam.text) }
        if (upd.isNotEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Brush.horizontalGradient(listOf(Steam.greenA, Steam.greenB))).clickable { upd.forEach { s.getOrUpdate(it, ctx) } }.padding(14.dp), contentAlignment = Alignment.Center) { Text(t("Tümünü güncelle (${upd.size})", "Update all (${upd.size})"), color = W, fontWeight = FontWeight.Bold, fontSize = 16.sp) } }
            items(upd, key = { it.pkg }) { a ->
                Column { AppRow(s, a, onOpen); Text(t("Bu uygulamanın güncellemelerini yoksay", "Ignore updates for this app"), Modifier.padding(start = 16.dp, bottom = 4.dp).clickable { s.toggleIgnore(a) }, color = Steam.dim, fontSize = 11.sp) }
            }
        }
        if (ign.isNotEmpty()) { item { H(t("Yoksayılan güncellemeler", "Ignored updates")) }; items(ign, key = { "i" + it.pkg }) { a -> Column { AppRow(s, a, onOpen); Text(t("Yoksaymayı kaldır", "Stop ignoring"), Modifier.padding(start = 16.dp, bottom = 4.dp).clickable { s.toggleIgnore(a) }, color = Steam.link, fontSize = 11.sp) } } }
        val recentLaunched = s.launches.entries.sortedByDescending { it.value }.mapNotNull { s.byPkg(it.key) }.take(6)
        if (recentLaunched.isNotEmpty()) { item { H(t("Sık kullandıkların", "Frequently used")) }; items(recentLaunched, key = { "r" + it.pkg }) { AppRow(s, it, onOpen) } }
    }
}

// ---------------- DETAY: Steam mağaza sayfası ----------------
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SteamDetail(s: Store, a: AppItem, onBack: () -> Unit, onCategory: (String) -> Unit) {
    val ctx = LocalContext.current
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var more by remember(a.pkg) { mutableStateOf(false) }
    LaunchedEffect(a.pkg) { if (a.source == "STEAM") s.enrichSteam(a); s.loadReviews(a) }
    var colDialog by remember { mutableStateOf(false) }
    val gal = remember(a) { (a.screenshots + listOf(a.banner)).filter { it.isNotBlank() }.distinct() }
    val pager = rememberPagerState { gal.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    val desc = a.description.ifBlank { a.summary }.replace(Regex("<[^>]+>"), "").trim()
    fun web(u: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val steamId = a.pkg.removePrefix("steam:")
    Column(Modifier.fillMaxSize().background(Steam.detailBg).verticalScroll(rememberScrollState())) {
        Box {
            Banner(a, Modifier.fillMaxWidth().height(230.dp), icon = 0, fade = false)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Steam.detailBg))))
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = W) }
                Spacer(Modifier.weight(1f))
                WishButton(s, a)
            }
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) { if (a.source != "STEAM") AppIcon(a, 72, 10) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(a.name, color = W, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            @Composable fun Info(k: String, v: String, link: Boolean = false) = Row(Modifier.padding(vertical = 2.dp)) { Text(k, Modifier.width(110.dp), color = Steam.dim, fontSize = 15.sp); Text(v, color = if (link) Steam.link else Steam.text, fontSize = 15.sp, modifier = Modifier.weight(1f)) }
            if (a.developer.isNotBlank() || a.license.isNotBlank()) Info(t("Geliştirici", "Developer"), a.developer.ifBlank { a.license }, true)
            if (a.source == "STEAM" && a.license.isNotBlank()) Info(t("Yayıncı", "Publisher"), a.license, true)
            Info(t("Kaynak", "Source"), when (a.source) { "PLAY" -> "Google Play"; "STEAM" -> "Steam"; else -> "F-Droid" }, true)
            if (a.updated > 0) Info(t("Güncellendi", "Updated"), df.format(Date(a.updated)))
            Info(if (a.source == "STEAM") t("Çıkış", "Released") else t("Sürüm", "Version"), a.versionName.ifBlank { "-" })
            if (a.installs.isNotBlank()) Info(t("İndirme", "Downloads"), a.installs)
            a.extra["platforms"]?.takeIf { it.isNotBlank() }?.let { Info(t("Platform", "Platforms"), it) }
            a.extra["metacritic"]?.let { Info("Metacritic", it) }
            a.extra["achievements"]?.let { Info(t("Başarım", "Achievements"), it) }
            a.extra["recs"]?.let { Info(t("Öneri", "Recommended"), it) }
            Spacer(Modifier.height(16.dp))
            Text(desc, color = Steam.text, fontSize = 16.sp, lineHeight = 24.sp, maxLines = if (more) Int.MAX_VALUE else 7, overflow = TextOverflow.Ellipsis)
            if (desc.length > 300) Text(if (more) t("Daha az", "Show less") else t("Daha fazla", "Show more"), Modifier.clickable { more = !more }.padding(vertical = 6.dp), color = Steam.link)
            val tags = (a.tags + a.categories).distinct().take(10)
            if (tags.isNotEmpty()) {
                Text(t("ETİKETLER", "TAGS"), Modifier.padding(top = 16.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { c -> Text(I18n.category(c), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).clickable { onCategory(c) }.padding(horizontal = 14.dp, vertical = 9.dp), color = Steam.link, fontSize = 15.sp, maxLines = 1) } }
            }
            // ---- DEĞERLENDİRMELER
            Text(t("DEĞERLENDİRMELER", "REVIEWS"), Modifier.padding(top = 18.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(14.dp)) {
                if (a.review.isNotBlank()) Row { Text("STEAM: ", color = Steam.dim, fontSize = 15.sp); Text(a.review, color = Steam.link, fontSize = 15.sp) }
                else if (a.rating > 0) {
                    val pct = (a.rating / 5f * 100).toInt()
                    val (label, col) = when { pct >= 90 -> t("Çok olumlu", "Very Positive") to Steam.link; pct >= 75 -> t("Olumlu", "Positive") to Steam.link; pct >= 60 -> t("Karışık", "Mixed") to Color(0xFFB9A074); else -> t("Olumsuz", "Negative") to Color(0xFFA34C25) }
                    Row { Text("GOOGLE PLAY: ", color = Steam.dim, fontSize = 15.sp); Text(label, color = col, fontSize = 15.sp); Text(String.format(" (%d%% · ★ %.1f)", pct, a.rating), color = Steam.dim, fontSize = 15.sp) }
                } else Row { Text(if (a.source == "FDROID") t("AÇIK KAYNAK: ", "OPEN SOURCE: ") else t("PUAN: ", "RATING: "), color = Steam.dim, fontSize = 15.sp); Text(a.license.ifBlank { t("henüz yok", "none yet") }, color = Steam.link, fontSize = 15.sp) }
            }
            val rv = s.reviews[a.pkg]
            if (a.source != "FDROID") {
                Text(t("KULLANICI İNCELEMELERİ", "USER REVIEWS"), Modifier.padding(top = 16.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
                when {
                    rv == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Steam.btn)
                    rv.isEmpty() -> Text(t("Henüz yorum yok ya da yüklenemedi.", "No reviews yet or they could not be loaded."), color = Steam.dim, fontSize = 14.sp)
                    else -> rv.take(10).forEach { r ->
                        var open by remember(r.text) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).clickable { open = !open }.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (r.up != null) Icon(if (r.up) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown, null, tint = if (r.up) Steam.link else Color(0xFFA34C25), modifier = Modifier.size(20.dp))
                                else Text("★".repeat(r.stars.coerceIn(0, 5)), color = Color(0xFFF2A33A), fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.author, color = W, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(listOfNotNull(if (r.up != null) (if (r.up) t("Önerir", "Recommended") else t("Önermez", "Not recommended")) else null, if (r.hours > 0) t("${r.hours} saat oynadı", "${r.hours} h played") else null, if (r.time > 0) df.format(Date(r.time)) else null).joinToString(" · "), color = Steam.dim, fontSize = 11.sp)
                                }
                            }
                            Text(r.text, Modifier.padding(top = 8.dp), color = Steam.text, fontSize = 14.sp, lineHeight = 20.sp, maxLines = if (open) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                            if (r.votes > 0) Text(t("${r.votes} kişi faydalı buldu", "${r.votes} found this helpful"), Modifier.padding(top = 6.dp), color = Steam.dim, fontSize = 11.sp)
                        }
                    }
                }
                if (a.source == "STEAM") Text(t("Tüm incelemeleri Steam'de oku →", "Read all reviews on Steam →"), Modifier.clickable { web("https://store.steampowered.com/app/$steamId#app_reviews_hash") }.padding(vertical = 6.dp), color = Steam.link, fontSize = 14.sp)
            }
            // ---- GÖRSELLER (kaydırmalı)
            if (gal.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box {
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(300.dp).background(Color.Black), pageSpacing = 4.dp) { i -> AsyncImage(gal[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    Text("${pager.currentPage + 1}/${gal.size}", Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 2.dp), color = W, fontSize = 12.sp)
                }
                LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(gal.size) { i -> AsyncImage(gal[i], null, Modifier.height(86.dp).width(130.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Color.Black).clickable { scope.launch { pager.animateScrollToPage(i) } }, contentScale = ContentScale.Crop, alpha = if (i == pager.currentPage) 1f else .55f) }
                }
                a.extra["movie"]?.let { mv -> Text("▶ " + t("Fragmanı izle", "Watch trailer"), Modifier.padding(top = 8.dp).clickable { web(mv) }, color = Steam.link, fontSize = 15.sp) }
            }
            // ---- STEAMDB
            if (a.source == "STEAM") {
                Text("STEAMDB", Modifier.padding(top = 18.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(14.dp)) {
                    a.extra["players"]?.takeIf { it.isNotBlank() }?.let { p -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(Color(0xFF8CC63F), CircleShape)); Spacer(Modifier.width(8.dp)); Text(t("Şu an oynayan: ", "Playing now: ") + String.format("%,d", p.toIntOrNull() ?: 0), color = W, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) } }
                    Text("App ID: $steamId", Modifier.padding(top = 4.dp), color = Steam.dim, fontSize = 13.sp)
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SteamDB" to "https://steamdb.info/app/$steamId/", t("Fiyat geçmişi", "Price history") to "https://steamdb.info/app/$steamId/#pricehistory", t("Oyuncu grafiği", "Player chart") to "https://steamdb.info/app/$steamId/charts/", t("Sürümler", "Patches") to "https://steamdb.info/app/$steamId/patchnotes/", "Steam Charts" to "https://steamcharts.com/app/$steamId").forEach { (l, u) ->
                            Text(l, Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.wish).clickable { web(u) }.padding(horizontal = 14.dp, vertical = 9.dp), color = Steam.link, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
                a.extra["req"]?.let { rq -> Text(t("SİSTEM GEREKSİNİMLERİ", "SYSTEM REQUIREMENTS"), Modifier.padding(top = 16.dp, bottom = 6.dp), color = W, fontSize = 16.sp); Text(rq, color = Steam.text, fontSize = 13.sp, lineHeight = 19.sp) }
                a.extra["langs"]?.let { lg -> Text(t("DİLLER", "LANGUAGES"), Modifier.padding(top = 16.dp, bottom = 6.dp), color = W, fontSize = 16.sp); Text(lg.take(300), color = Steam.dim, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(18.dp))
            // ---- İSTEK LİSTESİ (tam genişlik) + eylemler (ikonlu)
            Text(if (s.isWished(a)) t("♥ İstek listende", "♥ On your wishlist") else t("İstek listene ekle", "Add to your wishlist"), Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.wish).clickable { s.toggleWish(a) }.padding(vertical = 14.dp), color = Steam.link, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) = Row(Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.wish).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Steam.link, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = Steam.link, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
                if (!s.isInstalled(a)) Chip(Icons.Filled.VideogameAsset, if (a.pkg in s.libAdded) t("Kütüphanede ✓", "In library ✓") else t("Kütüphaneye ekle", "Add to library")) { s.toggleLibrary(a) }
                if (s.isInstalled(a)) Chip(Icons.Filled.Delete, t("Kaldır", "Uninstall")) { s.uninstall(ctx, a.pkg) }
                Chip(if (a.pkg in s.pins) Icons.Filled.Star else Icons.Filled.StarBorder, if (a.pkg in s.pins) t("Favori", "Pinned") else t("Favorile", "Pin")) { s.togglePin(a) }
                Chip(Icons.Filled.Folder, t("Koleksiyona ekle", "Add to collection")) { colDialog = true }
                if (a.web.isNotBlank()) Chip(Icons.Filled.OpenInBrowser, t("Web'de aç", "Open web")) { web(a.web) }
                Chip(Icons.Filled.Share, t("Paylaş", "Share")) { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${a.name} — " + (if (a.source == "STEAM") a.web else "https://play.google.com/store/apps/details?id=${a.pkg}")), null)) }
            }
            Spacer(Modifier.height(22.dp))
            // "Satın al" paneli = Yükle
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Brush.verticalGradient(Steam.buy)).gloss().padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text((if (a.source == "STEAM") t("Steam'de: ", "On Steam: ") else t("Yükle: ", "Get ")) + a.name, Modifier.weight(1f), color = W, fontSize = 24.sp, lineHeight = 28.sp)
                    Icon(when (a.source) { "STEAM" -> Icons.Filled.DesktopWindows; "PLAY" -> Icons.Filled.Shop; else -> Icons.Filled.Android }, null, tint = W, modifier = Modifier.size(34.dp))
                }
                Text(sizeText(a.apkSize).let { if (it.isBlank()) "" else t("Boyut: ", "Size: ") + it }, color = Steam.link, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    PriceTag(a, Modifier.height(44.dp)); Spacer(Modifier.width(8.dp))
                    ActionButton(s, a, Modifier.weight(1f, fill = false))
                }
            }
            val p = s.busy[a.pkg]
            if (p != null) DlBar(if (p < 0) 1f else p, Modifier.padding(top = 8.dp))
            if (s.launches[a.pkg] != null) Text(t("Açılış: ${s.launches[a.pkg]} kez", "Launched ${s.launches[a.pkg]} times"), Modifier.padding(top = 8.dp), color = Steam.dim, fontSize = 13.sp)
            Spacer(Modifier.height(72.dp).navigationBarsPadding())
        }
    }
    if (colDialog) CollectionDialog(s, a) { colDialog = false }
}

@Composable
fun CollectionDialog(s: Store, a: AppItem, onDone: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDone, confirmButton = { TextButton(onDone) { Text(t("Tamam", "Done")) } },
        title = { Text(t("Koleksiyona ekle", "Add to collection")) },
        text = { Column {
            s.collections.keys.sorted().forEach { n -> Row(Modifier.fillMaxWidth().clickable { s.toggleInCollection(n, a) }, verticalAlignment = Alignment.CenterVertically) { Checkbox(a.pkg in s.collections[n].orEmpty(), { s.toggleInCollection(n, a) }); Text(n) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it.take(24) }, Modifier.weight(1f), singleLine = true, placeholder = { Text(t("Yeni koleksiyon", "New collection")) })
                TextButton({ s.addCollection(newName); newName = "" }) { Text("+") }
            }
        } })
}
