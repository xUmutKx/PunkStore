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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
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

/** Satın alma panelindeki fiyat: koyu yeşil indirim kutusu (limon yeşili yazı) + koyu mavi-gri fiyat kutusu. */
@Composable
fun BuyPrice(a: AppItem, modifier: Modifier = Modifier) {
    val free = a.price.isBlank() && a.discount == 0
    val disc = a.discount > 0
    // dönemine göre: 2006 = zeytin yeşili + altın, kabartmalı kenar; 2013 = koyu karbon + limon yeşili; modern = Steam mobil
    val old06 = Steam.pal === PAL_2006; val old13 = Steam.pal === PAL_2013
    val discBg = if (old06) Color(0xFF3E4637) else if (old13) Color(0xFF2B3D0A) else Color(0xFF4C6B22)
    val discFg = if (old06) Color(0xFFC4B550) else Color(0xFFBEEE11)
    val priceBg = if (old06) Color(0xFF5A6A50) else if (old13) Color(0xFF1B1B1B) else Color(0xFF344654)
    val priceFg = if (old06) Color(0xFFE5E2DF) else if (old13) Color(0xFFB8B6B4) else Color(0xFFBFD7EA)
    val frame = if (old06) Modifier.border(1.dp, Steam.edgeHi) else if (old13) Modifier.border(1.dp, Steam.edgeLo) else Modifier
    Row(modifier.then(frame), verticalAlignment = Alignment.CenterVertically) {
        if (free || disc) Box(Modifier.fillMaxHeight().background(discBg).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(if (disc) "-${a.discount}%" else "-100%", color = discFg, fontSize = if (old06) 20.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Column(Modifier.fillMaxHeight().background(priceBg).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.End) {
            if (disc && a.origPrice.isNotBlank()) Text(a.origPrice, color = Color(0xFF9AA7B0), fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, lineHeight = 14.sp, maxLines = 1, softWrap = false)
            Text(if (free) t("ÜCRETSİZ", "FREE") else a.price, color = priceFg, fontSize = 19.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** Steam'in indirim kutusu: yeşil % + (üstü çizili eski fiyat) + güncel fiyat; ücretsiz uygulamada "ÜCRETSİZ". */
@Composable
fun PriceTag(a: AppItem, modifier: Modifier = Modifier) {
    val free = a.price.isBlank() && a.discount == 0
    val disc = a.discount > 0
    Row(modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xE60E141B)), verticalAlignment = Alignment.CenterVertically) {
        if (free || disc) Text(if (disc) "-${a.discount}%" else "-100%", Modifier.background(Color(0xFFA4D007)).padding(horizontal = 6.dp, vertical = 4.dp), color = Color(0xFF254007), fontWeight = FontWeight.Black, fontSize = 13.sp)
        Column(Modifier.padding(start = 8.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)) {
            if (disc && a.origPrice.isNotBlank()) Text(a.origPrice, color = Color(0xFF738895), fontSize = 10.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, lineHeight = 11.sp)
            Text(if (free) t("ÜCRETSİZ", "FREE") else a.price, color = W, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

/** Ayrıntı sayfasında üstte kalan ince çubuk: geri, logo, arama, profil. */
@Composable
fun SteamThinTopBar(s: Store, onBack: () -> Unit, onNav: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().height(42.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack, Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = W) }
        Row(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).clickable { onNav("search") }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PunkLogo(12f, frame = false, color = Steam.text, accent = Steam.dim); Spacer(Modifier.weight(1f)); Icon(Icons.Filled.Search, null, tint = Steam.dim, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clickable { onNav("profile") }) { Avatar(s, 30) }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
fun SteamTopBar2(s: Store, onNav: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Steam.topBrush).gloss().statusBarsPadding().padding(bottom = 4.dp).drawBehind { Steam.topAccent?.let { drawRect(it, androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) } }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).border(1.dp, Steam.edgeLo, RoundedCornerShape(Steam.corner.dp)).clickable { onNav("search") }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PunkLogo(18f, frame = false, color = Steam.text, accent = Steam.dim); Spacer(Modifier.weight(1f))
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
                    listOf(Triple("steam", Icons.Filled.SportsEsports, t("Steam", "Steam")), Triple("settings", Icons.Filled.Settings, t("Ayarlar", "Settings")),
                        Triple("about", Icons.Filled.Info, t("Bilgi", "About")), Triple("refresh", Icons.Filled.Refresh, t("Kataloğu yenile", "Refresh catalog"))).forEach { (k, ic, l) ->
                        DropdownMenuItem({ Text(l) }, { menu = false; onNav(k) }, leadingIcon = { Icon(ic, null) })
                    }
                }
            }
            @Composable fun Nav(label: String, key: String, badge: Int = 0, color: Color = Steam.text) = BadgedBox({ if (badge > 0) Badge { Text("$badge") } }, Modifier.padding(end = 6.dp)) { Text(label, Modifier.clickable { onNav(key) }.padding(horizontal = 8.dp, vertical = 8.dp), color = color, fontSize = 15.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false) }
            Nav(t("İSTEK LİSTESİ", "WISHLIST"), "wishlist", s.wishlist.size)
            Nav(t("İNDİRMELER", "DOWNLOADS"), "downloads", s.busy.size)
        }
    }
}

class QA(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tr: String, val en: String)
val QA_STORE = listOf(QA("daily", Icons.Filled.WbSunny, "Günün uygulaması", "App of the day"), QA("random", Icons.Filled.Casino, "Rastgele", "Random"), QA("open", Icons.Filled.ContentPaste, "Paketle aç", "Open by package"), QA("github", Icons.Filled.Code, "GitHub kaynakları", "GitHub sources"))
val QA_LIBRARY = listOf(QA("collections", Icons.Filled.Folder, "Koleksiyonlar", "Collections"), QA("pinned", Icons.Filled.PushPin, "Favoriler", "Pinned"), QA("recent", Icons.Filled.History, "Son bakılanlar", "Recent"), QA("share", Icons.Filled.Share, "Paylaş", "Share"), QA("backup", Icons.Filled.Backup, "Yedek", "Backup"))
val QA_UPDATES = listOf(QA("storage", Icons.Filled.Storage, "Depolama", "Storage"), QA("cleanup", Icons.Filled.CleaningServices, "Temizlik", "Cleanup"), QA("installed", Icons.Filled.Info, "Kurulu bilgisi", "Installed info"))

/** İkonlu kısayol satırı. */
@Composable
fun QuickRow(items: List<QA>, onNav: (String) -> Unit, extra: @Composable () -> Unit = {}) = Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    extra()
    items.forEach { q -> Row(Modifier.clip(RoundedCornerShape(Steam.corner.dp)).skinBg().clickable { onNav(q.key) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(q.icon, null, tint = Steam.btn, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t(q.tr, q.en), color = W, fontSize = 13.sp, maxLines = 1, softWrap = false) } }
}

@Composable
private fun H(text: String, modifier: Modifier = Modifier) = Text(text, modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = W, fontSize = 21.sp, fontWeight = FontWeight.Bold)

@Composable
private fun SeeAll(onClick: () -> Unit) = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
    Text(t("Tümünü gör", "See All"), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Color(0xFFDCDEDF)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp), color = Color(0xFF1B1D23), fontWeight = FontWeight.SemiBold)
}

/** Büyük dikey kapsül (Steam ana sayfa kayan afişleri). */
val LocalImpression = androidx.compose.runtime.compositionLocalOf<((String) -> Unit)?> { null }

/** Steam'in fiyat şeridi: limon yeşili indirim kutusu + koyu fiyat kutusu, kapağın altında sağa yaslı. */
@Composable
fun PriceStrip(a: AppItem, modifier: Modifier = Modifier) {
    val disc = a.discount > 0
    val free = a.price.isBlank()
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        if (disc) Box(Modifier.fillMaxHeight().background(Color(0xFFA4D007)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) { Text("-${a.discount}%", color = Color(0xFF254007), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false) }
        Row(Modifier.fillMaxHeight().background(Color(0xFF22262D)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (disc && a.origPrice.isNotBlank()) Text(a.origPrice, color = Color(0xFF8A939B), fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 6.dp))
            Text(if (free) (if (a.rating > 0) "★ %.1f".format(a.rating) else t("Ücretsiz", "Free")) else a.price, color = W, fontSize = 16.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** Steam kartı: kapak görseli + altında fiyat şeridi. Gerçek afişi olmayan uygulamalar (telefon ekran görüntüsü yerine) dikey bir kartta: ikon, ad, açıklama. */
@Composable
fun SteamCard(s: Store, a: AppItem, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val imp = LocalImpression.current
    LaunchedEffect(a.pkg) { if (imp != null) { kotlinx.coroutines.delay(20_000); imp(a.pkg) } }
    if (a.source != "STEAM" && a.banner.isBlank()) {
        Column(modifier.heightIn(min = 190.dp).clip(RoundedCornerShape(3.dp)).background(Steam.card).appPress(s, a, onOpen).padding(10.dp)) {
            AppIcon(a, 64, 12)
            Text(a.name, Modifier.padding(top = 8.dp), color = W, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (a.summary.isNotBlank()) Text(a.summary, Modifier.padding(top = 2.dp), color = Steam.dim, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
            PriceStrip(a, Modifier.align(Alignment.End))
        }
        return
    }
    val showName = a.source != "STEAM" || a.cover == null
    Column(modifier.appPress(s, a, onOpen)) {
        Banner(a, Modifier.fillMaxWidth().aspectRatio(1.78f), icon = 0, fade = false, image = a.banner.ifBlank { null })
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showName) Text(a.name, Modifier.weight(1f).padding(end = 6.dp), color = W, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Spacer(Modifier.weight(1f))
            PriceStrip(a)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SteamStore(s: Store, onOpen: (String) -> Unit, onCategory: (String) -> Unit, onNav: (String) -> Unit = {}) {
    val hvKey = s.hideViewed to s.viewTimes.size
    val featured = remember(s.apps, hvKey) { s.hv(s.recentlyUpdated.filter { it.banner.isNotBlank() }).take(8) }
    val fresh = remember(s.apps, hvKey) { s.hv(s.newest.filter { it.cover != null }).take(16) }
    val recent = remember(s.apps, hvKey) { s.hv(s.recentlyUpdated).take(30) }
    androidx.compose.runtime.CompositionLocalProvider(LocalImpression provides (if (s.hideViewed) { p: String -> s.noteImpression(p) } else null)) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(s.loading || s.checking, { onNav("refresh") }, Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            if (s.loading) Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(if (s.progress < .9f) t("Katalog indiriliyor…", "Downloading catalog…") else t("Katalog işleniyor…", "Processing catalog…"), fontSize = 12.sp, color = Steam.dim)
                LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().padding(top = 4.dp), color = Steam.btn)
            }
            // Mevsim afişi gibi başlık
            // Dokunulabilir afiş: parmağı takip eden ışık, basınca renk kayar, dişli döner; dokununca günün uygulaması
            var touch by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            val down = touch != null
            var hue by remember { mutableIntStateOf(0) }
            val slimeCols = listOf(Color(0xFF3AA6FF), Color(0xFF5BE37A), Color(0xFFFF4FA3), Color(0xFFFFD23A), Color(0xFFA855F7))
            val tintC by androidx.compose.animation.animateColorAsState(slimeCols[hue % slimeCols.size], androidx.compose.animation.core.tween(350), label = "ht")
            val sc by androidx.compose.animation.core.animateFloatAsState(if (down) 1.012f else 1f, androidx.compose.animation.core.spring(.28f, 350f), label = "hs")
            val sy by androidx.compose.animation.core.animateFloatAsState(if (down) .975f else 1f, androidx.compose.animation.core.spring(.28f, 350f), label = "hsy")
            val rot by androidx.compose.animation.core.animateFloatAsState(if (down) 70f else 0f, androidx.compose.animation.core.spring(.35f, 120f), label = "hr")
            val warm by androidx.compose.animation.core.animateFloatAsState(if (down) 1f else 0f, androidx.compose.animation.core.tween(400), label = "hw")
            Box(Modifier.fillMaxWidth().height(150.dp).graphicsLayer { scaleX = sc; scaleY = sy }.background(Brush.linearGradient(Steam.hero))
                .drawBehind { touch?.let { o -> drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), o, 260f), 260f, o) }
                    drawRect(tintC.copy(alpha = .22f * warm)) }
                .pointerInput(Unit) { detectTapGestures(onPress = { o -> touch = o; hue++; tryAwaitRelease(); touch = null }, onTap = {}) }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); if (touch != null) e.changes.firstOrNull()?.let { touch = it.position } } } }) {
                Text("PUNK\nSTORE", Modifier.align(Alignment.CenterStart).padding(start = 20.dp), color = W, fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
                Icon(Icons.Filled.Settings, null, Modifier.align(Alignment.CenterEnd).size(110.dp).padding(end = 10.dp).graphicsLayer { rotationZ = rot }, tint = Color(0x44FFFFFF))
            }
        }
        item { QuickRow(QA_STORE, onNav) {
            Row(Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(if (s.hideViewed) Steam.blue else Steam.wish).clickable { s.changeHideViewed(!s.hideViewed) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s.hideViewed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = if (s.hideViewed) W else Steam.btn, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(t("Görüntülenenleri gizle", "Hide viewed"), color = W, fontSize = 13.sp, maxLines = 1, softWrap = false) } } }
        if (featured.isNotEmpty()) item {
            val pager = rememberPagerState { featured.size }
            Column(Modifier.padding(top = 16.dp)) {
                HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp, pageSize = androidx.compose.foundation.pager.PageSize.Fixed(310.dp)) { i -> SteamCard(s, featured[i], Modifier.width(310.dp), onOpen) }
                Row(Modifier.padding(16.dp, 10.dp)) { repeat(featured.size) { Box(Modifier.padding(end = 4.dp).size(width = if (pager.currentPage == it) 26.dp else 10.dp, height = 4.dp).background(if (pager.currentPage == it) Color(0xFFBBBBBB) else Color(0xFF555B66))) } }
            }
        }
        if (s.wishlist.isNotEmpty()) { item { H(t("İstek listen", "Your wishlist")) }; item { AppCarousel(s, s.wishApps, onOpen) } }
        item { H(t("Kategoriye göz at", "Browse by category")); LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(s.categories.take(24) + UMUTK_CAT) { c -> Text(if (c == UMUTK_CAT) c else I18n.category(c), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).skinBg().clickable { onCategory(c) }.padding(horizontal = 14.dp, vertical = 10.dp), color = W, fontSize = 14.sp) } } }
        if (s.recommended.isNotEmpty()) { item { H(t("Senin için önerilenler", "Recommended for you")) }; item { AppCarousel(s, s.hv(s.recommended), onOpen) } }
        listOf("specials" to t("Steam — indirimdekiler", "Steam — specials"), "top" to t("Steam — çok satanlar", "Steam — top sellers"), "new" to t("Steam — yeni çıkanlar", "Steam — new releases"), "soon" to t("Steam — yakında", "Steam — coming soon")).forEach { (k, title) ->
            s.steamLists[k]?.let { s.hv(it) }?.takeIf { it.isNotEmpty() }?.let { l -> item { Column(Modifier.padding(vertical = 6.dp).then(if (k == "specials") Modifier.padding(horizontal = 8.dp).background(Color(0xFF3B1F16)).padding(vertical = 8.dp) else Modifier)) { H(title); AppCarousel(s, l, onOpen) } } }
        }
        if (s.playTop.isNotEmpty()) { item { H("Google Play — " + t("en çok indirilenler", "Top free")) }; item { AppCarousel(s, s.hv(s.playTop), onOpen) } }
        if (s.playGames.isNotEmpty()) { item { H("Google Play — " + t("oyunlar", "Games")) }; item { AppCarousel(s, s.hv(s.playGames), onOpen) } }
        item { H(t("Yeni çıkanlar", "New releases")) }
        items(fresh.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { SteamCard(s, it, Modifier.weight(1f), onOpen) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(8.dp)); SeeAll { onCategory("*") } }
        item { H(t("Son güncellenenler", "Recently updated")) }
        items(recent, key = { it.pkg }) { AppRow(s, it, onOpen) }
        item { H(UMUTK_CAT); UmutKStrip { onCategory(UMUTK_CAT) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
    }
}

// ---------------- KÜTÜPHANE: dikey kapak ızgarası ----------------
// ---------------- GÜNCELLEMELER (zil sekmesi) ----------------
@Composable
fun SteamUpdates(s: Store, onOpen: (String) -> Unit, onNav: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var instFilter by remember { mutableIntStateOf(0) }   // 0 hepsi, 1 oyunlar, 2 oynanan/kullanılan
    val upd = s.apps.filter { s.isInstalled(it) && s.hasUpdate(it) }
    val ign = s.apps.filter { s.isInstalled(it) && it.pkg in s.ignored && (s.installed[it.pkg] ?: 0) < it.versionCode }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Text(t("İNDİRİLENLER", "DOWNLOADS"), Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().padding(14.dp), color = W, fontSize = 20.sp, letterSpacing = 2.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        item { QuickRow(QA_UPDATES, onNav) }
        val active = s.dl.tasks.values.sortedByDescending { it.created }
        if (active.isNotEmpty()) {
            item { H(t("İndirilenler", "Downloads")) }
            items(active, key = { "d" + it.pkg }) { tk -> Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Text(tk.name, color = W, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); DownloadLine(s, tk.pkg) } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (s.checking) t("Denetleniyor…", "Checking…") else t("Son denetim: ", "Last check: ") + if (s.lastCheck > 0) df.format(Date(s.lastCheck)) else "-", color = Steam.text, fontSize = 13.sp)
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
        val pm = ctx.packageManager
        fun isSys(p: String) = runCatching { val ai = pm.getApplicationInfo(p, 0)
            ai.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                listOf("/system", "/product", "/vendor", "/system_ext", "/apex", "/odm").any { ai.sourceDir.startsWith(it) } }.getOrDefault(false)
        fun isGame(a: AppItem) = a.categories.any { it.contains("Game", true) } || runCatching { pm.getApplicationInfo(a.pkg, 0).category == android.content.pm.ApplicationInfo.CATEGORY_GAME }.getOrDefault(false)
        val inst = s.libApps.filter { s.isInstalled(it) && (s.showSystem || !isSys(it.pkg)) }.filter { a -> when (instFilter) { 1 -> isGame(a); 2 -> (s.launches[a.pkg] ?: 0) > 0 || (s.lastPlayed[a.pkg] ?: 0L) > 0; else -> true } }.sortedByDescending { runCatching { pm.getPackageInfo(it.pkg, 0).lastUpdateTime }.getOrDefault(0L) }
        run {
            item {
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    H(t("Yüklü uygulamalar (${inst.size})", "Installed apps (${inst.size})"), Modifier.weight(1f))
                    @Composable fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, f: () -> Unit) = Row(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Steam.blue else Steam.wish).clickable(onClick = f).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = if (on) W else Steam.link, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(label, color = if (on) W else Steam.link, fontSize = 12.sp, maxLines = 1) }
                    Toggle(Icons.Filled.Android, t("Sistem", "System"), s.showSystem) { s.changeShowSystem(!s.showSystem) }
                    Toggle(Icons.Filled.ViewList, t("Kompakt", "Compact"), s.compactInstalled) { s.changeCompactInstalled(!s.compactInstalled) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(0, Icons.Filled.Apps, t("Hepsi", "All")), Triple(1, Icons.Filled.SportsEsports, t("Oyunlar", "Games")), Triple(2, Icons.Filled.History, t("Oynanan / kullanılan", "Played / used"))).forEach { (k, ic, l) ->
                        Row(Modifier.clip(RoundedCornerShape(2.dp)).background(if (instFilter == k) Steam.blue else Steam.wish).clickable { instFilter = k }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ic, null, tint = if (instFilter == k) W else Steam.link, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(l, color = if (instFilter == k) W else Steam.link, fontSize = 12.sp, maxLines = 1) }
                    }
                }
            }
            items(inst, key = { "in" + it.pkg }) { a ->
                if (s.compactInstalled) Row(Modifier.fillMaxWidth().clickable { if (a.source == "LOCAL") s.open(ctx, a.pkg) else onOpen(a.pkg) }.padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a, 30, 6); Spacer(Modifier.width(10.dp))
                    Text(a.name, Modifier.weight(1f), color = W, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.versionName, color = Steam.dim, fontSize = 11.sp, maxLines = 1)
                } else AppRow(s, a, onOpen)
            }
        }
        val recentLaunched = s.launches.entries.sortedByDescending { it.value }.mapNotNull { s.byPkg(it.key) }.take(6)
        if (recentLaunched.isNotEmpty()) { item { H(t("Sık kullandıkların", "Frequently used")) }; items(recentLaunched, key = { "r" + it.pkg }) { AppRow(s, it, onOpen) } }
    }
}

// ---------------- DETAY: Steam mağaza sayfası ----------------
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SteamDetail(s: Store, a: AppItem, onBack: () -> Unit, onOpen: (String) -> Unit = {}, onCategory: (String) -> Unit) {
    val ctx = LocalContext.current
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var more by remember(a.pkg) { mutableStateOf(false) }
    LaunchedEffect(a.pkg) { if (a.source == "STEAM") s.enrichSteam(a); s.loadReviews(a) }
    var colDialog by remember { mutableStateOf(false) }
    val gal = remember(a) { (a.screenshots + listOf(a.banner)).filter { it.isNotBlank() }.distinct() }
    val pager = rememberPagerState { gal.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    val desc = a.description.ifBlank { a.summary }.replace(Regex("<[^>]+>"), "").trim()
    fun web(u: String) = Browser.open(u)
    val steamId = a.pkg.removePrefix("steam:")
    Column(Modifier.fillMaxSize().background(Steam.detailBg).verticalScroll(rememberScrollState())) {
        Box {
            Banner(a, Modifier.fillMaxWidth().height(230.dp), icon = 0, fade = false)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Steam.detailBg))))
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                WishButton(s, a)
            }
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) { if (a.source != "STEAM") AppIcon(a, 72, 10) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(a.name, color = W, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
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
            Spacer(Modifier.height(20.dp))
            // "Satın al" paneli = Yükle
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (s.design == Design.STEAM) Color(0xFF3A4556) else Steam.box).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text((if (a.source == "STEAM") t("Satın al: ", "Buy ") else t("Yükle: ", "Get ")) + a.name, Modifier.weight(1f), color = W, fontSize = 26.sp, lineHeight = 30.sp)
                    Row(Modifier.padding(start = 8.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (a.source == "STEAM") {
                            val pl = a.extra["platforms"].orEmpty()
                            if ("Windows" in pl || pl.isEmpty()) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_windows), "Windows", tint = W, modifier = Modifier.size(26.dp))
                            if ("macOS" in pl) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_apple), "macOS", tint = W, modifier = Modifier.size(26.dp))
                            if ("Linux" in pl) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_linux), "Linux", tint = W, modifier = Modifier.size(26.dp))
                            if (a.extra["android"] != null) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_android), "Android", tint = W, modifier = Modifier.size(28.dp))
                        } else Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_android), null, tint = W, modifier = Modifier.size(30.dp))
                    }
                }
                sizeText(a.apkSize).let { if (it.isNotBlank()) Text(t("Boyut: ", "Size: ") + it, color = Steam.link, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp)) }
                // Steam'deki gibi: sağa yaslı, siyah çerçeveli [indirim | fiyat | yeşil düğme]
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.End) {
                    Row(Modifier.background(Color.Black).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        BuyPrice(a, Modifier.height(52.dp))
                        Spacer(Modifier.width(3.dp))
                        ActionButton(s, a, Modifier.heightIn(min = 52.dp))
                    }
                }
            }
            // ---- İSTEK LİSTESİ + eylemler: hepsi tek satırda, ikon üstte kısa etiket altta
            class Act(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val go: () -> Unit)
            val acts = buildList {
                add(Act(if (s.isWished(a)) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, t("İstek", "Wishlist")) { s.toggleWish(a) })
                if (!s.isInstalled(a)) add(Act(Icons.Filled.VideogameAsset, if (a.pkg in s.libAdded) t("Kütüphane ✓", "Library ✓") else t("Kütüphane", "Library")) { s.toggleLibrary(a) })
                if (s.isInstalled(a)) add(Act(Icons.Filled.Delete, t("Kaldır", "Remove")) { s.uninstall(ctx, a.pkg) })
                add(Act(if (a.pkg in s.pins) Icons.Filled.Star else Icons.Filled.StarBorder, if (a.pkg in s.pins) t("Favori ✓", "Pinned") else t("Favori", "Pin")) { s.togglePin(a) })
                add(Act(Icons.Filled.Folder, t("Koleksiyon", "Collect")) { colDialog = true })
                if (a.web.isNotBlank()) add(Act(Icons.Filled.OpenInBrowser, t("Web", "Web")) { web(a.web) })
                add(Act(Icons.Filled.Share, t("Paylaş", "Share")) { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${a.name} — " + (if (a.source == "STEAM") a.web else "https://play.google.com/store/apps/details?id=${a.pkg}")), null)) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                acts.forEach { c ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(2.dp)).background(Steam.wish).clickable(onClick = c.go).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(c.icon, null, tint = Steam.link, modifier = Modifier.size(20.dp)); Spacer(Modifier.height(3.dp)); Text(c.label, color = Steam.link, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Clip)
                    }
                }
            }
            @Composable fun Info(k: String, v: String, link: Boolean = false) = Row(Modifier.padding(vertical = 2.dp)) { Text(k, Modifier.width(110.dp), color = Steam.dim, fontSize = 15.sp); Text(v, color = if (link) Steam.link else Steam.text, fontSize = 15.sp, modifier = Modifier.weight(1f)) }
            if (a.developer.isNotBlank() || a.license.isNotBlank()) Info(t("Geliştirici", "Developer"), a.developer.ifBlank { a.license }, true)
            if (a.source == "STEAM" && a.license.isNotBlank()) Info(t("Yayıncı", "Publisher"), a.license, true)
            Info(t("Kaynak", "Source"), when (a.source) { "PLAY" -> "Google Play"; "STEAM" -> "Steam"; else -> "F-Droid" }, true)
            if (a.updated > 0) Info(t("Güncellendi", "Updated"), df.format(Date(a.updated)))
            Info(if (a.source == "STEAM") t("Çıkış", "Released") else t("Sürüm", "Version"), a.versionName.ifBlank { "-" })
            if (a.installs.isNotBlank()) Info(t("İndirme", "Downloads"), a.installs)
            a.extra["platforms"]?.takeIf { it.isNotBlank() }?.let { Info(t("Platform", "Platforms"), it) }
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
            if (a.source == "STEAM") SteamExtras(s, a, onOpen, ::web)
            // ---- DEĞERLENDİRMELER
            Text(t("DEĞERLENDİRMELER", "REVIEWS"), Modifier.padding(top = 18.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(12.dp)) { ScoresBlock(a) }
            // ---- STEAMDB
            if (a.source == "STEAM") {
                Text("STEAMDB", Modifier.padding(top = 18.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(14.dp)) {
                    a.extra["players"]?.takeIf { it.isNotBlank() }?.let { p -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(Color(0xFF8CC63F), CircleShape)); Spacer(Modifier.width(8.dp)); Text(t("Şu an oynayan: ", "Playing now: ") + String.format("%,d", p.toIntOrNull() ?: 0), color = W, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) } }
                    Text("App ID: $steamId", Modifier.padding(top = 4.dp), color = Steam.dim, fontSize = 13.sp)
                    a.extra["owners"]?.let { Text(t("Tahmini sahip: ", "Est. owners: ") + it, Modifier.padding(top = 4.dp), color = Steam.text, fontSize = 14.sp) }
                    a.extra["revenue"]?.let { Text(t("Tahmini brüt gelir: ", "Est. gross revenue: ") + it + t("  (sahip × fiyat, SteamSpy tahmini)", "  (owners × price, SteamSpy estimate)"), Modifier.padding(top = 4.dp), color = Steam.text, fontSize = 14.sp) }
                    a.extra["avgplay"]?.let { Text(t("Ortalama oynama: ", "Avg. playtime: ") + it + t(" saat", " h"), Modifier.padding(top = 4.dp), color = Steam.text, fontSize = 14.sp) }
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SteamDB" to "https://steamdb.info/app/$steamId/", t("Fiyat geçmişi", "Price history") to "https://steamdb.info/app/$steamId/#pricehistory", t("Oyuncu grafiği", "Player chart") to "https://steamdb.info/app/$steamId/charts/", t("Sürümler", "Patches") to "https://steamdb.info/app/$steamId/patchnotes/", "Steam Charts" to "https://steamcharts.com/app/$steamId", t("Topluluk Pazarı", "Community Market") to "https://steamcommunity.com/market/search?appid=$steamId", t("Fiyat karşılaştır", "Compare prices") to "https://isthereanydeal.com/search/?q=${java.net.URLEncoder.encode(a.name, "UTF-8")}").forEach { (l, u) ->
                            Text(l, Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.wish).clickable { web(u) }.padding(horizontal = 14.dp, vertical = 9.dp), color = Steam.link, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
                a.extra["req"]?.let { rq -> Text(t("SİSTEM GEREKSİNİMLERİ", "SYSTEM REQUIREMENTS"), Modifier.padding(top = 16.dp, bottom = 6.dp), color = W, fontSize = 16.sp); Text(rq, color = Steam.text, fontSize = 13.sp, lineHeight = 19.sp) }
                a.extra["langs"]?.let { lg -> Text(t("DİLLER", "LANGUAGES"), Modifier.padding(top = 16.dp, bottom = 6.dp), color = W, fontSize = 16.sp); Text(lg.take(300), color = Steam.dim, fontSize = 13.sp) }
            }
            if (s.launches[a.pkg] != null) Text(t("Açılış: ${s.launches[a.pkg]} kez", "Launched ${s.launches[a.pkg]} times"), Modifier.padding(top = 8.dp), color = Steam.dim, fontSize = 13.sp)
            // ---- GİZLİLİK RAPORU
            if (a.source != "STEAM") {
                Text(t("GİZLİLİK RAPORU", "PRIVACY REPORT"), Modifier.padding(top = 16.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(14.dp)) {
                    val fd = a.source == "FDROID"
                    Text(if (a.ads) "⚠ " + t("Reklam içeriyor", "Contains ads") else if (fd) "✓ " + t("F-Droid: reklam işaretlenmemiş", "F-Droid: no ads flagged") else "✓ " + t("Geliştirici reklam bildirmemiş (Google Play)", "No ads declared by the developer (Google Play)"), color = Steam.text, fontSize = 14.sp)
                    Text(if (a.tracking) "⚠ " + t("Kullanıcıyı izliyor (F-Droid uyarısı)", "Tracks users (F-Droid anti-feature)") else if (fd) "✓ " + t("F-Droid: izleme uyarısı yok", "F-Droid: no tracking anti-feature") else "? " + t("İzleyiciler bilinmiyor — Exodus raporuna bak", "Trackers unknown — see the Exodus report"), Modifier.padding(top = 4.dp), color = if (!a.tracking && !fd) Steam.dim else Steam.text, fontSize = 14.sp)
                    Text("Exodus Privacy " + t("raporunu aç →", "report →"), Modifier.padding(top = 8.dp).clickable { web("https://reports.exodus-privacy.eu.org/en/reports/search/${a.pkg}/") }, color = Steam.link, fontSize = 14.sp)
                }
            }
            val rv = s.reviews[a.pkg]
            if (a.source != "FDROID" && (rv?.isEmpty() != true || s.reviewErr[a.pkg] != null)) {
                Text(t("KULLANICI İNCELEMELERİ", "USER REVIEWS"), Modifier.padding(top = 16.dp, bottom = 8.dp), color = W, fontSize = 16.sp)
                when {
                    rv == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Steam.btn)
                    rv.isEmpty() -> Column {
                        Text(t("Yorumlar yüklenemedi: ", "Could not load reviews: ") + s.reviewErr[a.pkg].orEmpty().take(120), color = Steam.dim, fontSize = 13.sp)
                        Text(t("Tekrar dene", "Retry"), Modifier.clickable { s.loadReviews(a, true) }.padding(vertical = 8.dp), color = Steam.link, fontSize = 15.sp)
                    }
                    else -> rv.take(10).forEach { r ->
                        var open by remember(r.text) { mutableStateOf(false) }
                        val themed = Steam.pal !== PAL_MODERN
                        val accent = if (r.up == true) Steam.topAccent.takeIf { themed } ?: Steam.link else if (r.up == false) Color(0xFFC4501F) else Color(0xFFF2A33A)
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(if (themed) Brush.verticalGradient(Steam.panelGrad) else Brush.verticalGradient(listOf(Steam.box, Steam.box))).then(if (Steam.bevel) Modifier.border(1.dp, Steam.edgeHi) else Modifier).clickable { open = !open }) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
                        Column(Modifier.weight(1f).padding(12.dp)) {
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
                }
                if (a.source == "STEAM") Text(t("Tüm incelemeleri Steam'de oku →", "Read all reviews on Steam →"), Modifier.clickable { web("https://store.steampowered.com/app/$steamId#app_reviews_hash") }.padding(vertical = 6.dp), color = Steam.link, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
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
