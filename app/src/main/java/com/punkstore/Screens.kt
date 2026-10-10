package com.punkstore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
private fun LoadingBanner(s: Store) {
    if (s.loading) Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(if (s.progress < .9f) t("Katalog indiriliyor…", "Downloading catalog…") else t("Katalog işleniyor…", "Processing catalog…"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
    if (!s.loading && s.apps.isEmpty()) Text(t("Katalog yok. Yenile'ye dokun.", "No catalog yet. Tap refresh."), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CategoryChips(s: Store, onCategory: (String) -> Unit) {
    val st = s.design.steam
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.categories.take(24)) { c ->
            if (st) Text(I18n.category(c), Modifier.clip(RoundedCornerShape(3.dp)).background(Steam.panel2).clickable { onCategory(c) }.padding(horizontal = 12.dp, vertical = 8.dp), color = Steam.text, fontSize = 13.sp)
            else AssistChip({ onCategory(c) }, { Text(I18n.category(c)) })
        }
    }
}

// ---------------- STEAM ANA SAYFA ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamHome(s: Store, onOpen: (String) -> Unit, onCategory: (String) -> Unit) {
    val featured = remember(s.apps) { s.recentlyUpdated.filter { it.screenshots.isNotEmpty() }.take(8) }
    val newest = remember(s.apps) { s.newest.take(20) }
    val recent = remember(s.apps) { s.recentlyUpdated.take(30) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { LoadingBanner(s) }
        if (featured.isNotEmpty()) item {
            SectionTitle(t("Öne çıkanlar ve önerilenler", "Featured & recommended"), s)
            val pager = rememberPagerState { featured.size }
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 10.dp) { i ->
                val a = featured[i]
                Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(4.dp)).clickable { onOpen(a.pkg) }) {
                    AsyncImage(a.screenshots.firstOrNull() ?: "", a.name, Modifier.fillMaxSize().background(Steam.card), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0E141B)))))
                    Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a, 44, 4); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(a.summary, color = Steam.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(t("ÜCRETSİZ", "FREE"), Modifier.background(Steam.priceBg).padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFFBEEE11), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(featured.size) { Box(Modifier.padding(3.dp).size(7.dp).clip(CircleShape).background(if (pager.currentPage == it) Steam.blue else Steam.panel2)) }
            }
        }
        item { SectionTitle(t("Kategoriye göz at", "Browse by category"), s); CategoryChips(s, onCategory) }
        if (s.wishlist.isNotEmpty()) { item { SectionTitle(t("İstek listen", "Your wishlist"), s) }; item { AppCarousel(s, s.wishApps, onOpen) } }
        if (s.recommended.isNotEmpty()) { item { SectionTitle(t("Senin için önerilenler", "Recommended for you"), s) }; item { AppCarousel(s, s.recommended, onOpen) } }
        if (s.playTop.isNotEmpty()) { item { SectionTitle("Google Play — " + t("en çok indirilenler", "Top free"), s) }; item { AppCarousel(s, s.playTop, onOpen) } }
        if (s.playGames.isNotEmpty()) { item { SectionTitle("Google Play — " + t("oyunlar", "Games"), s) }; item { AppCarousel(s, s.playGames, onOpen) } }
        if (s.playNew.isNotEmpty()) { item { SectionTitle("Google Play — " + t("yeni ve güncellenenler", "New and updated"), s) }; item { AppCarousel(s, s.playNew, onOpen) } }
        item { SectionTitle(t("Yeni eklenenler", "New releases"), s) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(newest) { a ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(3.dp)).background(Steam.card).clickable { onOpen(a.pkg) }) {
                        Capsule(a, Modifier.fillMaxWidth().height(100.dp), 40, 0)
                        Text(a.name, Modifier.padding(8.dp, 8.dp, 8.dp, 2.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t("ÜCRETSİZ", "FREE"), Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp), color = Color(0xFFBEEE11), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { SectionTitle(t("Son güncellenenler", "Recently updated"), s) }
        items(recent, key = { it.pkg }) { AppRow(s, it, onOpen) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------- MATERIAL ANA SAYFA ----------------
@Composable
fun MaterialHome(s: Store, onOpen: (String) -> Unit, onCategory: (String) -> Unit, onSearch: () -> Unit = {}) {
    val newest = remember(s.apps) { s.newest.take(20) }
    val recent = remember(s.apps) { s.recentlyUpdated.take(30) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { // PUNK on the first line, STORE under it
                    Text("PUNK", fontFamily = LogoFont, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 30.sp)
                    Text("STORE", fontFamily = LogoFont, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 15.sp, letterSpacing = 7.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onSearch) { Icon(Icons.Filled.Search, t("Ara", "Search")) }
                if (s.loading) CircularProgressIndicator(Modifier.size(24.dp)) else IconButton({ s.refresh() }) { Icon(Icons.Filled.Refresh, t("Yenile", "Refresh")) }
            }
        }
        item { LoadingBanner(s) }
        item { CategoryChips(s, onCategory) }
        if (s.playTop.isNotEmpty()) { item { SectionTitle("Google Play — " + t("en çok indirilenler", "Top free"), s) }; item { AppCarousel(s, s.playTop, onOpen) } }
        if (s.playGames.isNotEmpty()) { item { SectionTitle("Google Play — " + t("oyunlar", "Games"), s) }; item { AppCarousel(s, s.playGames, onOpen) } }
        if (s.playNew.isNotEmpty()) { item { SectionTitle("Google Play — " + t("yeni ve güncellenenler", "New and updated"), s) }; item { AppCarousel(s, s.playNew, onOpen) } }
        item { SectionTitle(t("Yeni eklenenler", "New releases"), s) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(newest) { a ->
                    ElevatedCard(Modifier.width(150.dp).clickable { onOpen(a.pkg) }) {
                        Column { Banner(a, Modifier.fillMaxWidth().height(90.dp), icon = 36, fade = false)
                            Text(a.name, Modifier.padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp) }
                    }
                }
            }
        }
        item { SectionTitle(t("Son güncellenenler", "Recently updated"), s) }
        items(recent, key = { it.pkg }) { AppRow(s, it, onOpen) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun AppCarousel(s: Store, list: List<AppItem>, onOpen: (String) -> Unit) {
    val st = s.design.steam
    if (st) { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.pkg }) { a -> SteamCard(s, a, Modifier.width(250.dp), onOpen) } }; return }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.pkg }) { a ->
            Column(Modifier.width(150.dp).clip(RoundedCornerShape(if (st) 3.dp else 16.dp)).background(if (st) Steam.card else MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onOpen(a.pkg) }) {
                Banner(a, Modifier.fillMaxWidth().height(90.dp), icon = 36, fade = false)
                Text(a.name, Modifier.padding(8.dp, 6.dp, 8.dp, 0.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (st) Color.White else MaterialTheme.colorScheme.onSurface)
                if (a.discount > 0 || a.source == "STEAM") PriceTag(a, Modifier.padding(8.dp, 2.dp, 8.dp, 8.dp))
                else Text(if (a.rating > 0) "★ %.1f".format(a.rating) else if (a.price.isBlank()) t("ÜCRETSİZ", "FREE") else a.price, Modifier.padding(8.dp, 2.dp, 8.dp, 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (a.rating > 0) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFBEEE11))
            }
        }
    }
}

@Composable
fun CategoryScreen(s: Store, cat: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    if (cat == UMUTK_CAT) { UmutKScreen(s, onOpen, onBack); return }
    var open by remember { mutableStateOf(false) }
    if (open) FilterSheet(s) { open = false }
    val dev = cat.removePrefix("dev:").takeIf { cat.startsWith("dev:") }
    val list = remember(s.apps, cat, s.filters, s.ghMap.size, s.steamMap.size) {
        if (dev != null) (s.apps + s.ghMap.values + s.steamMap.values).filter { it.developer.ifBlank { it.license }.equals(dev, true) }.distinctBy { it.pkg }.sortedByDescending { it.updated }
        else s.filt(s.apps.filter { cat == "*" || cat in it.categories }.sortedByDescending { it.updated })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back")) }
                Text("${if (dev != null) dev else if (cat == "*") t("Tümü", "All") else I18n.category(cat)} (${list.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                FilterButton(s) { open = true }
            }
        }
        items(list, key = { it.pkg }) { AppRow(s, it, onOpen) }
    }
}

/** Canlı arama: F-Droid yerelden anında, Play + Steam ağdan; boş sorguda (browse) yalnızca filtrelere göre liste. */
class SearchState(val res: List<AppItem>, val busy: Boolean)

@Composable
fun rememberSearch(s: Store, q: String, browse: Boolean): SearchState {
    var playRes by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var steamRes by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        val k = q.trim()
        if (k.length < 2) { playRes = emptyList(); steamRes = emptyList(); busy = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(180)   // yazmayı bitirmesini bekle; F-Droid sonuçları zaten anında yerelden gelir
        busy = true
        kotlinx.coroutines.coroutineScope {
            val sc = this
            sc.launch { playRes = s.searchPlay(k) }
            sc.launch { steamRes = s.searchSteamStore(k) }
        }
        busy = false; s.noteSearch(k)
    }
    val res = remember(q, s.apps, playRes, steamRes, s.filters, browse) {
        if (q.isBlank()) (if (browse || s.filters.active) s.filt(s.apps).take(150) else emptyList())
        else s.filt(Rank.merge(q.trim(), s.search(q).take(200), playRes)) + steamRes
    }
    return SearchState(res, busy)
}

@Composable
fun SearchScreen(s: Store, onOpen: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var browse by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    val st = rememberSearch(s, q, browse); val res = st.res; val busy = st.busy
    LaunchedEffect(q) { if (q.isNotBlank()) browse = false }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(t("Ara", "Search")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(if (s.design.steam) Steam.corner.dp else 28.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (q.isBlank()) browse = true }))
            FilterButton(s) { filterOpen = true }
        }
        if (s.filters.active || browse) Text(t("${res.size} sonuç" + if (s.filters.active) " · filtre açık" else "", "${res.size} results" + if (s.filters.active) " · filters on" else ""), Modifier.padding(16.dp, 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (q.isBlank()) {
            Text(t("F-Droid · Google Play · Steam", "F-Droid · Google Play · Steam"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.searches.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(t("Son aramalar", "Recent searches"), Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton({ s.clearSearches() }) { Text(t("Temizle", "Clear"), fontSize = 12.sp) } }
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(s.searches.toList()) { h -> AssistChip({ q = h }, { Text(h) }) } }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        LazyColumn { items(res, key = { it.pkg }) { AppRow(s, it, onOpen) } }
    }
    if (filterOpen) FilterSheet(s) { filterOpen = false }
}

@Composable
fun LibraryScreen(s: Store, onOpen: (String) -> Unit) {
    var sort by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val mine = remember(s.apps, s.installed.toMap(), s.libAdded.toList(), sort, q, s.launches.toMap()) {
        s.libApps.filter { it.name.contains(q, true) }.let { l ->
            when (sort) { 1 -> l.sortedByDescending { s.launches[it.pkg] ?: 0 }; 2 -> l.sortedByDescending { it.updated }; else -> l.sortedBy { it.name.lowercase() } }
        }
    }
    val updates = mine.filter { s.hasUpdate(it) }
    LazyColumn(Modifier.fillMaxSize().then(if (s.design == Design.MATERIAL) Modifier.statusBarsPadding() else Modifier)) {
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp, 8.dp), singleLine = true, placeholder = { Text(t("Kütüphanede ara", "Search library")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(if (s.design.steam) 3.dp else 28.dp))
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(t("A-Z", "A-Z"), t("En çok oynanan", "Most played"), t("Son güncellenen", "Recently updated")).forEachIndexed { i, l -> FilterChip(sort == i, { sort = i }, { Text(l, fontSize = 12.sp) }) }
                Spacer(Modifier.weight(1f))
                IconButton({ grid = !grid }) { Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, null) }
            }
        }
        if (updates.isNotEmpty()) {
            item { SectionTitle(t("Güncellemeler (${updates.size})", "Updates (${updates.size})"), s) }
            items(updates, key = { "u" + it.pkg }) { AppRow(s, it, onOpen) }
        }
        item { SectionTitle(t("Kütüphanen (${mine.size})", "Your library (${mine.size})"), s) }
        if (mine.isEmpty()) item { Text(t("Kütüphane boş. Bir uygulamanın sayfasından 'Kütüphaneye ekle' de.", "Your library is empty. Use 'Add to library' on an app page."), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (grid) items(mine.chunked(3)) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onOpen(a.pkg) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Capsule(a, Modifier.fillMaxWidth().height(64.dp), 32, 6); Text(a.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(if (!s.isInstalled(a)) t("Kurulu değil", "Not installed") else if (s.hasUpdate(a)) t("Güncelle", "Update") else (s.launches[a.pkg] ?: 0).let { "$it ${t("açılış", "launches")}" }, fontSize = 10.sp, color = if (s.hasUpdate(a)) Steam.greenA else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        } else items(mine, key = { it.pkg }) { AppRow(s, it, onOpen) }
    }
}

@Composable
fun SettingsScreen(s: Store, onGoogleLogin: () -> Unit = {}, onAbout: () -> Unit = {}, onNav: (String) -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(if (s.design == Design.MATERIAL) Modifier.statusBarsPadding() else Modifier).padding(16.dp)) {
        SectionTitle(t("Tasarım", "Design"), s, Modifier.padding(0.dp))
        Design.values().forEach { d ->
            val title = when (d) { Design.STEAM -> t("Steam (modern, ana)", "Steam (modern, default)"); Design.STEAM2013 -> t("Steam 2013 (mavi gradyan)", "Steam 2013 (blue gradient)"); Design.STEAM2006 -> t("Steam 2006 (zeytin yeşili)", "Steam 2006 (olive)"); else -> "Material You" }
            Row(Modifier.fillMaxWidth().clickable { s.changeDesign(d) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(s.design == d, { s.changeDesign(d) }); Text(title)
            }
        }
        if (s.design == Design.MATERIAL) {
            SectionTitle(t("Material You seçenekleri", "Material You options"), s, Modifier.padding(0.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Koyu tema", "Dark theme"), Modifier.weight(1f)); Switch(s.dark || s.amoled, { s.changeDark(it); if (!it) s.changeAmoled(false) }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t("AMOLED siyah", "AMOLED black"), Modifier.weight(1f)); Switch(s.amoled, { s.changeAmoled(it) }) }
        }
        SectionTitle(t("Dil", "Language"), s, Modifier.padding(0.dp))
        listOf(LangPref.EN to "English", LangPref.TR to "Türkçe", LangPref.AUTO to t("Telefonun dili", "Device language")).forEach { (p, title) ->
            Row(Modifier.fillMaxWidth().clickable { s.changeLang(p) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(I18n.pref == p, { s.changeLang(p) }); Text(title)
            }
        }
        SectionTitle(t("Kurulum yöntemi", "Installation method"), s, Modifier.padding(0.dp))
        listOf(
            Triple(InstallMethod.SESSION, t("Oturum kurucusu (önerilen)", "Session installer (recommended)"), t("Android PackageInstaller; her kurulumda onay ister", "Android PackageInstaller; asks to confirm each install")),
            Triple(InstallMethod.NATIVE, t("Sistem kurucusu", "Native installer"), t("Sistemin kendi kurulum ekranını açar", "Opens the system install screen")),
            Triple(InstallMethod.ROOT, "Root", t("su ile sessiz kurulum/kaldırma (root gerekir)", "Silent install/uninstall via su (root required)")),
        ).forEach { (m, title, hint) ->
            Row(Modifier.fillMaxWidth().clickable { s.changeMethod(m) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(s.method == m, { s.changeMethod(m) }); Column { Text(title); Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (s.method == InstallMethod.ROOT) {
            Text(when (s.rootOk) { true -> t("✓ Root erişimi var", "✓ Root access granted"); false -> t("✗ Root erişimi yok / reddedildi", "✗ No root access / denied"); null -> t("Root denetleniyor…", "Checking root…") }, fontSize = 12.sp, color = if (s.rootOk == true) Steam.greenA else MaterialTheme.colorScheme.error)
            OutlinedButton({ s.testRoot() }) { Text(t("Root'u yeniden dene", "Retry root")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Açılış animasyonu", "Startup animation"), Modifier.weight(1f)); Switch(s.splash, { s.changeSplash(it) }) }
        SectionTitle(t("Görünüm", "Appearance"), s, Modifier.padding(0.dp))
        Text(t("Yazı boyutu: %${(s.fontScale * 100).toInt()}", "Text size: ${(s.fontScale * 100).toInt()}%"))
        Slider(s.fontScale, { s.changeFontScale((it * 20).toInt() / 20f) }, valueRange = .8f..1.4f)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Titreşim (Keşfet kaydırması)", "Haptics (Discover swipes)"), Modifier.weight(1f)); Switch(s.haptic, { s.changeHaptic(it) }) }
        SectionTitle(t("İndirmeler", "Downloads"), s, Modifier.padding(0.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Kurulumdan sonra APK'yı sil", "Delete APK after install"), Modifier.weight(1f)); Switch(s.deleteApk, { s.changeDeleteApk(it) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Yalnızca Wi-Fi'de indir", "Download on Wi-Fi only"), Modifier.weight(1f)); Switch(s.wifiOnly, { s.changeWifiOnly(it) }) }
        OutlinedButton({ s.clearCache() }) { Text(t("İndirme önbelleğini temizle", "Clear download cache")) }
        SectionTitle(t("Güncellemeler", "Updates"), s, Modifier.padding(0.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Otomatik denetle ve bildir", "Auto-check and notify"), Modifier.weight(1f)); Switch(s.autoCheck, { s.changeAutoCheck(it) }) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Sıklık:", "Every:")); listOf(1, 3, 6, 12, 24).forEach { h -> FilterChip(s.intervalH == h, { s.changeInterval(h) }, { Text("${h}${t("sa", "h")}") }) }
        }
        SectionTitle(t("Google Play", "Google Play"), s, Modifier.padding(0.dp))
        var disp by remember { mutableStateOf(s.dispenser) }
        OutlinedTextField(disp, { disp = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Özel anonim dağıtıcı (dispenser) adresi", "Custom anonymous dispenser URL")) }, placeholder = { Text("https://auroraoss.com/api/auth") })
        TextButton({ s.changeDispenser(disp); s.loadPlay() }) { Text(t("Kaydet ve yeniden bağlan", "Save and reconnect")) }
        SectionTitle(t("Kaynaklar", "Sources"), s, Modifier.padding(0.dp))
        Text("F-Droid (FOSS): ${s.fdroid.size} " + t("uygulama", "apps"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Steam oyunlarını göster", "Show Steam games"), Modifier.weight(1f))
            Switch(s.showSteam, { s.changeShowSteam(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SteamDB (" + t("Steam profil verisi", "Steam profile data") + ")", Modifier.weight(1f))
            Switch(s.steamDbOn, { s.setSteamDb(it) })
        }
        if (s.googleEmail != null) {
            Text(t("Google hesabı: ", "Google account: ") + s.googleEmail, color = Steam.greenA)
            OutlinedButton({ s.googleLogout() }) { Text(t("Çıkış yap (anonime dön)", "Sign out (back to anonymous)")) }
        } else {
            Text(t("Google ile giriş yaparsan Play bölümü daha hızlı ve kararlı olur (Aurora Store gibi). Girişsiz anonim oturum yavaş olabilir.", "Signing in with Google makes Play faster and more reliable (like Aurora Store). The anonymous session can be slow."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ onGoogleLogin() }) { Text(t("Google ile giriş yap", "Sign in with Google")) }
        }
        Text("Google Play: " + (if (s.playLoading) t("yükleniyor…", "loading…") else s.playError ?: t("bağlı (anonim oturum, arama + üst listeler)", "connected (anonymous session, search + top charts)")), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton({ s.refresh() }) { Text(t("Kataloğu yenile", "Refresh catalog")) }
        SectionTitle(t("Yedek", "Backup"), s, Modifier.padding(0.dp))
        Text(t("Ayarlar, istek listesi, kütüphane, favoriler ve koleksiyonlar tek bir metin olarak dışa/içe aktarılır.", "Settings, wishlist, library, pins and collections export/import as one text."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton({ onNav("backup") }) { Text(t("Dışa / içe aktar", "Export / import")) }
        Spacer(Modifier.height(16.dp))
        Button(onAbout, Modifier.fillMaxWidth()) { Text(t("Bilgi / Hakkında", "Info / About"), fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive, fontSize = 20.sp) }
        Spacer(Modifier.height(48.dp))
        Text(t("Punk Store 0.54 — GPL-3.0. F-Droid ve Aurora Store projelerinden esinlenmiştir.", "Punk Store 0.53 — GPL-3.0. Inspired by the F-Droid and Aurora Store projects."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- DETAY ----------------
@Composable
fun DetailScreen(s: Store, a: AppItem, onBack: () -> Unit) {
    val st = s.design.steam
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(230.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            a.screenshots.firstOrNull()?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, MaterialTheme.colorScheme.background))))
            IconButton(onBack, Modifier.statusBarsPadding().padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) }
            WishButton(s, a, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp).offset(y = (-30).dp), verticalAlignment = Alignment.Bottom) {
            AppIcon(a, 84, if (st) 4 else 20)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(a.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (st) Color.White else MaterialTheme.colorScheme.onBackground)
                Text(a.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-20).dp)) {
            Text(a.summary, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { a.categories.take(3).forEach { Tag(I18n.category(it), s) }; if (a.license.isNotBlank()) Tag(a.license, s) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (st) 3.dp else 20.dp)).background(if (st) Steam.panel2.copy(alpha = .5f) else MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (a.source == "PLAY") (if (a.price.isBlank()) t("Ücretsiz", "Free") else a.price) + (if (a.rating > 0) "  ★ %.1f".format(a.rating) else "") else if (st) t("ÜCRETSİZ — Açık kaynak", "FREE — Open source") else t("Ücretsiz", "Free"), fontWeight = FontWeight.Bold, color = if (st) Color(0xFFBEEE11) else MaterialTheme.colorScheme.primary)
                    Text(t("Sürüm", "Version") + " ${a.versionName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionButton(s, a)
                DownloadLine(s, a.pkg)
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.isInstalled(a)) OutlinedButton({ s.toggleLibrary(a) }, Modifier) { Text(if (a.pkg in s.libAdded) t("✓ Kütüphanede", "✓ In library") else t("Kütüphaneye ekle", "Add to library")) }
                if (!s.isInstalled(a)) OutlinedButton({ s.toggleWish(a) }, Modifier) { Text(if (s.isWished(a)) t("♥ İstek listesinde", "♥ On wishlist") else t("İstek listesine ekle", "Add to wishlist")) }
                OutlinedButton({ ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${a.name} — https://play.google.com/store/apps/details?id=${a.pkg}"), null)) }, Modifier) { Text(t("Paylaş", "Share")) }
                if (s.isInstalled(a)) OutlinedButton({ s.uninstall(ctx, a.pkg) }, Modifier) { Text(t("Kaldır", "Uninstall")) }
            }
            // the store keeps this app's updates off (background updates and the update list skip it)
            if (s.isInstalled(a)) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Otomatik güncellemeleri kapat", "Disable auto updates"), Modifier.weight(1f))
                Switch(a.pkg in s.ignored, { s.toggleIgnore(a) })
            }
            if (s.launches[a.pkg] != null) Text(t("Açılış: ${s.launches[a.pkg]} kez", "Launched ${s.launches[a.pkg]} times"), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.screenshots.isNotEmpty()) {
                SectionTitle(t("Ekran görüntüleri", "Screenshots"), s, Modifier.padding(0.dp).padding(top = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(a.screenshots) { AsyncImage(it, null, Modifier.height(260.dp).clip(RoundedCornerShape(if (st) 3.dp else 12.dp)), contentScale = ContentScale.FillHeight) }
                }
            }
            SectionTitle(t("Hakkında", "About"), s, Modifier.padding(0.dp).padding(top = 12.dp))
            Text(a.description.ifBlank { a.summary }.replace(Regex("<[^>]+>"), "").trim(), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            SectionTitle(t("Bilgi", "Information"), s, Modifier.padding(0.dp).padding(top = 12.dp))
            listOf(t("Sürüm", "Version") to "${a.versionName} (${a.versionCode})", t("Boyut", "Size") to sizeText(a.apkSize), t("Güncellendi", "Updated") to df.format(Date(a.updated)), t("Eklendi", "Added") to df.format(Date(a.added)), t("Kaynak", "Source") to if (a.source == "PLAY") "Google Play" else "F-Droid").forEach { (k, v) ->
                Row(Modifier.padding(vertical = 3.dp)) { Text(k, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); Text(v, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
