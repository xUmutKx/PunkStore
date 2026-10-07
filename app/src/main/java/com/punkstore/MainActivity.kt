package com.punkstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.animation.togetherWith

class MainActivity : ComponentActivity() {
    private val store by viewModels<Store>()

    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    override fun onResume() { super.onResume(); store.refreshInstalled(); store.checkUpdates() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoUpdate.apply(this)
        SteamDbWeb.ctx = applicationContext
        if (android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            PunkTheme(store.design, store.dark, store.amoled) {
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * store.fontScale)) { androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontWeight = if (store.boldText) androidx.compose.ui.text.font.FontWeight.SemiBold else null)) { Root(store) } }
            }
        }
    }
}

enum class Tab(private val tr: String, private val en: String, val bottom: Boolean = true) {
    STORE("Mağaza", "Store"), DISCOVER("Keşfet", "Discover"), SEARCH("Ara", "Search", false), LIBRARY("Kütüphane", "Library"), UPDATES("İndirilenler", "Downloads"), PROFILE("Profil", "Profile", false), MENU("Menü", "Menu");
    val label get() = t(tr, en)
}

/** Gezinme anlık görüntüleri: AnimatedContent eski ve yeni ekranı ayrı ayrı çizebilsin diye. */
private data class FullNav(val ach: Boolean, val steamAcc: Boolean, val about: Boolean, val login: Boolean, val pkg: String?) {
    val depth get() = if (pkg != null) 1 else if (ach || steamAcc || about || login) 2 else 0
}
private data class InnerNav(val pkg: String?, val tab: Tab, val materialSearch: Boolean, val overlay: String?, val wishlist: Boolean, val settings: Boolean, val category: String?)

@Composable
fun Root(s: Store) {
    var tab by remember { mutableStateOf(Tab.STORE) }
    var prevTab by remember { mutableStateOf(Tab.STORE) }
    var openPkg by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var wishlist by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var achScreen by remember { mutableStateOf(false) }
    var steamAcc by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var splash by remember { mutableStateOf(s.splash) }
    LaunchedEffect(s.xp, s.apps.size, s.liked, s.seen.size, s.lastCheck, s.design, s.method, s.googleEmail, s.photo, s.userName, s.showcase.size, s.filters, s.steamId, s.updateCount) { s.checkAchievements() }
    LaunchedEffect(s.apps.size, s.steamMap.size) { kotlinx.coroutines.delay(6000); s.prepareSplash() }
    LaunchedEffect(s.achToast) { if (s.achToast != null) { kotlinx.coroutines.delay(4500); s.dismissToast() } }
    fun nav(k: String) {
        category = null; wishlist = false; settings = false; overlay = null; openPkg = null
        when (k) {
            "store" -> tab = Tab.STORE; "discover" -> tab = Tab.DISCOVER; "library" -> tab = Tab.LIBRARY; "updates" -> tab = Tab.UPDATES; "profile" -> tab = Tab.PROFILE; "menu" -> tab = Tab.MENU; "downloads" -> tab = Tab.UPDATES; "umutk" -> { tab = Tab.STORE; category = UMUTK_CAT };"search" -> { if (tab != Tab.SEARCH) prevTab = tab; tab = Tab.SEARCH }
            "wishlist" -> wishlist = true; "settings" -> settings = true; "about" -> about = true; "achievements", "achv" -> achScreen = true; "steam" -> steamAcc = true
            "refresh" -> { s.refresh(); s.checkUpdates(true); s.loadSteamStore() }
            "daily" -> s.apps.filter { it.cover != null }.let { l -> if (l.isNotEmpty()) openPkg = l[((System.currentTimeMillis() / 86400000L) % l.size).toInt()].pkg }
            "random" -> s.apps.filter { it.cover != null }.let { l -> if (l.isNotEmpty()) openPkg = l.random().pkg }
            else -> if (k.startsWith("app:")) openPkg = k.removePrefix("app:") else overlay = "f:$k"
        }
    }
    LaunchedEffect(openPkg) { openPkg?.let { s.noteView(it) } }
    val app = openPkg?.let { s.byPkg(it) ?: s.anyPkg(it) }?.takeIf { it.source != "LOCAL" }
    BackHandler(enabled = overlay != null || achScreen || steamAcc || about || login || app != null || category != null || wishlist || settings) {
        when { overlay != null -> overlay = null; achScreen -> achScreen = false; steamAcc -> steamAcc = false; about -> about = false; login -> login = false; app != null -> openPkg = null; wishlist -> wishlist = false; settings -> settings = false; else -> category = null }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize().then(if (s.design.steam && Steam.carbon) Modifier.carbon() else Modifier)) {
        if (splash) SplashOverlayHost(s) { splash = false }
        // Tam ekran sayfalar arası geçiş: ayrıntı sağdan kayarak gelir, diğerleri yumuşak solma + hafif büyüme
        val full = FullNav(achScreen, steamAcc, about, login, if (app != null && !s.design.steam) openPkg else null)
        androidx.compose.animation.AnimatedContent(full, transitionSpec = {
            val fwd = targetState.depth > initialState.depth || (targetState.pkg != null && initialState.pkg != null)
            if (targetState.pkg != null || initialState.pkg != null) {
                (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { if (fwd) it / 3 else -it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(280)) { if (fwd) -it / 4 else it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)))
            } else (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220), initialScale = .96f)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
        }, label = "full") { fn ->
        val fApp = fn.pkg?.let { s.byPkg(it) ?: s.anyPkg(it) }
        if (fn.ach) {
            AchievementsScreen(s) { achScreen = false }
        } else if (fn.steamAcc) {
            SteamAccountScreen(s, { steamAcc = false }) { steamAcc = false; nav(it) }
        } else if (fn.about) {
            AboutScreen(s) { about = false }
        } else if (fn.login) {
            GoogleLoginScreen({ mail, tok -> s.googleLogin(mail, tok); login = false }) { login = false }
        } else if (fApp != null) {
            if (s.design.steam) SteamDetail(s, fApp, { openPkg = null }, onOpen = { openPkg = it }) { openPkg = null; category = it } else DetailScreen(s, fApp) { openPkg = null }
        } else {
            Scaffold(
                containerColor = if (s.design.steam && Steam.carbon) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = { if (s.design.steam && tab == Tab.MENU && app == null && !wishlist && !settings && category == null) SteamMenuTopBar(s) { nav(it) } else if (s.design.steam && app != null) SteamThinTopBar(s, { openPkg = null }) { nav(it) } else if (s.design.steam && !wishlist && !settings && (if (tab == Tab.SEARCH) prevTab else tab) == Tab.STORE && category == null) SteamTopBar2(s) { nav(it) } },
                bottomBar = { Column { DownloadDock(s) { openPkg = null; category = null; wishlist = false; settings = false; tab = Tab.UPDATES }; BottomBar(s.design, tab, s.updateCount, s.orderedTabs(), s.floatDock && s.design.steam) { if (it == Tab.SEARCH && tab != Tab.SEARCH) prevTab = tab; tab = it; openPkg = null; category = null; wishlist = false; settings = false } } },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    val inner = InnerNav(if (app != null && s.design.steam) openPkg else null, if (tab == Tab.SEARCH && s.design.steam) prevTab else tab, tab == Tab.SEARCH && !s.design.steam, overlay, wishlist, settings, category)
                    // Sekmeler arası: yöne göre kayma (sağdaki sekmeye geçince sağdan gelir)
                    androidx.compose.animation.AnimatedContent(inner, transitionSpec = {
                        val dir = targetState.tab.ordinal.compareTo(initialState.tab.ordinal)
                        if (dir != 0 && targetState.overlay == null && initialState.overlay == null)
                            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { dir * it / 5 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200))) togetherWith
                                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(260)) { -dir * it / 5 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
                        else (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(240)) { it / 14 }) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
                    }, label = "tab") { n ->
                    Box(Modifier.fillMaxSize()) {
                    when {
                        n.pkg != null && s.design.steam -> s.byPkg(n.pkg).let { it ?: s.anyPkg(n.pkg) }?.let { SteamDetail(s, it, { openPkg = null }, onOpen = { openPkg = it }) { openPkg = null; category = it } }
                        n.overlay != null && n.overlay.startsWith("f:") -> FeatureScreen(s, n.overlay.removePrefix("f:"), { openPkg = it }) { overlay = null }
                        n.wishlist -> WishlistScreen(s, { openPkg = it }) { wishlist = false }
                        n.settings -> if (s.design.steam) SteamSettingsScreen(s, { settings = false }, { login = true }, { about = true }, { nav(it) }) else Column { SettingsBar { settings = false }; SettingsScreen(s, { login = true }, { about = true }, { nav(it) }) }
                        n.category != null -> CategoryScreen(s, n.category, { openPkg = it }) { category = null }
                        n.tab == Tab.STORE -> if (s.design.steam) SteamStore(s, { openPkg = it }, { category = it }, { nav(it) })
                                            else MaterialHome(s, { openPkg = it }, { category = it }) { nav("search") }
                        n.materialSearch -> SearchScreen(s) { openPkg = it }
                        n.tab == Tab.LIBRARY -> if (s.design.steam) SteamLibrary(s, { openPkg = it }, { nav(it) }) else LibraryScreen(s) { openPkg = it }
                        n.tab == Tab.UPDATES -> SteamUpdates(s, { openPkg = it }, { nav(it) })
                        n.tab == Tab.DISCOVER -> DiscoverScreen(s) { openPkg = it }
                        n.tab == Tab.MENU -> SteamMenu(s) { nav(it) }
                        n.tab == Tab.PROFILE -> ProfileScreen(s, { openPkg = it }, { wishlist = true }, { settings = true }, { achScreen = true }, { steamAcc = true }, { nav(it) })
                    }
                    }
                    }
                }
            }
        }
        }
        if (tab == Tab.SEARCH && s.design.steam && app == null && !achScreen && !steamAcc && !about && !login) SteamSearch(s, { openPkg = it }, { tab = prevTab })
        Browser.url?.let { u -> Box(Modifier.fillMaxSize().zIndex(8f)) { WebScreen(u) { Browser.url = null } } }
        s.achToast?.let { a ->
            Row(Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(Steam.corner.dp)).background(Steam.row).border(1.dp, Steam.btn, androidx.compose.foundation.shape.RoundedCornerShape(Steam.corner.dp)).clickable { s.dismissToast(); achScreen = true }.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(a.icon, null, tint = Steam.btn, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(10.dp))
                Column { Text(t("Başarım açıldı!", "Achievement unlocked!"), color = Steam.dim, style = MaterialTheme.typography.labelSmall); Text(t(a.tr, a.en), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            }
        }
        s.error?.let { e ->
            LaunchedEffect(e) { kotlinx.coroutines.delay(6000); s.dismissError() }
            Snackbar(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp).padding(bottom = 72.dp), containerColor = androidx.compose.ui.graphics.Color(0xFF3A1E1E), contentColor = androidx.compose.ui.graphics.Color.White,
                action = { TextButton({ s.dismissError() }) { Text("OK", color = androidx.compose.ui.graphics.Color(0xFF67C1F5)) } }) { Text(e, color = androidx.compose.ui.graphics.Color.White, maxLines = 6) }
        }
    }
    }
}

@Composable
private fun SettingsBar(onBack: () -> Unit) {
    Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(onBack) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back")) }
        Text(t("Ayarlar", "Settings"), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SplashOverlayHost(s: Store, onDone: () -> Unit) { Box(Modifier.fillMaxSize().zIndex(10f)) { SplashOverlay(s, onDone) } }
