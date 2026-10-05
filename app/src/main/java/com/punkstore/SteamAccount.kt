package com.punkstore

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val W = Color.White
private fun hours(min: Int) = if (min < 60) t("$min dk", "$min min") else t("%.1f saat", "%.1f h").format(min / 60.0)

/** Steam hesabı: Steam mobil "hesap" sayfası gibi. Steam ile giriş (tam oyun listesi) ya da profil adı (herkese açık). */
@Composable
fun SteamAccountScreen(s: Store, onBack: () -> Unit, onNav: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var login by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<SteamLink.Game?>(null) }
    LaunchedEffect(Unit) { if (s.steamProfile == null && (s.steamWho.isNotBlank() || s.steamId.isNotBlank() || s.steamLoggedIn)) s.loadSteamProfile(s.steamWho.ifBlank { s.steamId }) }
    BackHandler(login || game != null) { if (login) login = false else game = null }
    val page = when { login -> 1; game != null -> 2; else -> 0 }
    AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "acc") { pg ->
        Column(Modifier.fillMaxSize().background(Steam.bg)) {
            when (pg) {
                1 -> {
                    SteamPageHeader(t("Steam ile giriş", "Sign in with Steam"), { login = false })
                    SteamLoginView { cookie -> s.saveSteamCookie(cookie); login = false; s.loadSteamProfile("") }
                }
                2 -> game?.let { g -> SteamGameAchievements(s, g) { game = null } }
                else -> {
                    val p = s.steamProfile
                    if (p == null) SteamConnect(s, onBack) { login = true }
                    else LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            SteamProfileHeader(p.name, { AsyncImage(p.avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
                                listOf(p.online, p.headline).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }, t("Profili görüntüle", "View Profile"), onBack) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/profiles/${p.id}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            SteamStats("${p.gameCount}" to t("Oyun", "Games"), "${p.level}" to t("Seviye", "Level"), "${p.games.sumOf { it.minutes } / 60}" to t("Saat", "Hours"))
                            if (s.steamLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Steam.blue, trackColor = Steam.panel)
                            Column(Modifier.fillMaxWidth().background(Steam.topBrush).padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                                if (!s.steamLoggedIn) SteamBigButton(t("Steam ile giriş yap", "Sign in with Steam"), Icons.AutoMirrored.Filled.Login) { login = true }
                                else SteamBigButton(t("Kütüphanede gör", "Show in library"), Icons.Filled.VideoLibrary) { onNav("library") }
                                (p.note ?: s.steamAccErr)?.let { Text(it, Modifier.padding(top = 12.dp), color = Color(0xFFE0B25A), fontSize = 13.sp) }
                            }
                        }
                        item { SteamGamesHeader(p) }
                        items(p.games, key = { it.appId }) { g -> SteamGameRow(g, Modifier.animateItem()) { game = g } }
                        item {
                            SteamSection(t("Hesap", "Account"))
                            SteamRow(t("Hesabı değiştir", "Change Account"), s.steamWho.ifBlank { p.id }, onClick = { s.steamLogout() })
                            SteamRow(t("Yenile", "Refresh"), t("Profili ve oyunları yeniden yükle", "Reload profile and games"), dark = true, onClick = { s.loadSteamProfile(s.steamWho.ifBlank { p.id }) }, chevron = false)
                            SteamRow(t("Çıkış yap", "Sign Out"), null, dark = true, onClick = { s.steamLogout() }, chevron = false)
                            Spacer(Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamGamesHeader(p: SteamLink.Profile) {
    SteamSection(t("Oyunlarım", "My games") + " (${p.games.size}${if (p.partial && p.gameCount > p.games.size) " / ${p.gameCount}" else ""})")
}

/** Steam arama sonucu satırı gibi: başlık görseli + ad + oynama süresi */
@Composable
private fun SteamGameRow(g: SteamLink.Game, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().background(Steam.panel).padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF16202D)).pressScale(onClick), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(g.header, null, Modifier.width(140.dp).height(66.dp).background(Color(0xFF0E141B)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(g.name, color = Steam.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (g.minutes > 0) hours(g.minutes) + t(" oynandı", " played") else t("Hiç oynanmadı", "Never played"), color = Steam.dim, fontSize = 13.sp)
        }
        Icon(Icons.Filled.EmojiEvents, t("Başarımlar", "Achievements"), tint = Steam.dim, modifier = Modifier.padding(end = 12.dp).size(22.dp))
    }
}

/** Bağlı değilken: Steam ile giriş (önerilen) ya da profil adı/ID */
@Composable
private fun SteamConnect(s: Store, onBack: () -> Unit, onLogin: () -> Unit) {
    var who by remember { mutableStateOf(s.steamWho) }
    Column(Modifier.fillMaxSize()) {
        SteamPageHeader(t("Steam hesabı", "Steam account"), onBack)
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1B2838), Steam.bg))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.SportsEsports, null, tint = Steam.blue, modifier = Modifier.size(64.dp))
            Text("STEAM", color = W, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, modifier = Modifier.padding(top = 8.dp))
            Text(t("Oyunlarını, oynama sürelerini ve başarımlarını Punk Store kütüphanene getir.", "Bring your games, playtime and achievements into your Punk Store library."), color = Steam.text, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SteamBigButton(t("Steam ile giriş yap", "Sign in with Steam"), Icons.AutoMirrored.Filled.Login, onClick = onLogin)
            Text(t("Önerilen: tam oyun listesi yalnızca girişle görünür. Giriş Steam'in kendi sayfasında yapılır; şifren Punk Store'a gelmez.", "Recommended: the full games list is only visible when signed in. You sign in on Steam's own page; your password never reaches Punk Store."),
                Modifier.padding(top = 8.dp), color = Steam.dim, fontSize = 13.sp)
        }
        SteamSection(t("ya da profil adıyla", "or by profile name"), Modifier.padding(top = 20.dp))
        Column(Modifier.fillMaxWidth().background(Steam.panel).padding(20.dp)) {
            SteamField(who, { who = it }, t("Profil ID, özel URL adı ya da profil bağlantısı", "Profile ID, vanity name or profile link"), leading = Icons.Filled.Person)
            Spacer(Modifier.height(12.dp))
            SteamBigButton(if (s.steamLoading) t("Bağlanıyor…", "Connecting…") else t("Profili bağla", "Link profile"), color = Steam.panel2, enabled = who.isNotBlank() && !s.steamLoading) { s.loadSteamProfile(who) }
            s.steamAccErr?.let { Text(it, Modifier.padding(top = 10.dp), color = Color(0xFFE07B53), fontSize = 13.sp) }
            Text(t("Girişsiz yalnızca profilde görünen (son/çok oynanan) oyunlar gelir. Profil herkese açık olmalı.", "Without signing in only games shown on the profile (recent / most played) are listed. The profile must be public."), Modifier.padding(top = 10.dp), color = Steam.dim, fontSize = 12.sp)
        }
        if (s.steamLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Steam.blue, trackColor = Steam.panel)
    }
}

/** Steam'in kendi giriş sayfası (WebView); oturum çerezi gelince döner. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SteamLoginView(onCookie: (String) -> Unit) {
    var done by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.parseColor("#1B2838"))
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                    override fun onPageFinished(view: WebView, url: String?) {
                        loading = false
                        val ck = CookieManager.getInstance().getCookie("https://steamcommunity.com").orEmpty()
                        if (!done && ck.contains("steamLoginSecure")) { done = true; CookieManager.getInstance().flush(); onCookie(ck) }
                    }
                }
                loadUrl("https://steamcommunity.com/login/home/?goto=%2Fmy%2Fgames%2F%3Ftab%3Dall")
            }
        }, modifier = Modifier.fillMaxSize())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Steam.blue, trackColor = Color.Transparent)
    }
}

/** Oyun başarımları: başlık görseli, ilerleme, liste */
@Composable
private fun SteamGameAchievements(s: Store, g: SteamLink.Game, onBack: () -> Unit) {
    val p = s.steamProfile ?: return
    var achs by remember { mutableStateOf<List<SteamLink.Ach>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LaunchedEffect(g.appId) { runCatching { SteamLink.achievements(p.id, g.appId, if (I18n.isTr) "turkish" else "english") }.onSuccess { achs = it }.onFailure { err = it.message; achs = emptyList() } }
    Column(Modifier.fillMaxSize()) {
        SteamPageHeader(g.name, onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
                    AsyncImage(g.header, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Steam.bg))))
                    Text(hours(g.minutes) + t(" oynandı", " played"), Modifier.align(Alignment.BottomStart).padding(16.dp), color = W, fontSize = 15.sp)
                }
                val l = achs
                when {
                    l == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Steam.blue, trackColor = Steam.panel)
                    l.isEmpty() -> Text(err ?: t("Bu oyunda başarım yok.", "This game has no achievements."), Modifier.padding(20.dp), color = Steam.dim)
                    else -> Column(Modifier.padding(20.dp)) {
                        val d = l.count { it.done }
                        Text(t("BAŞARIMLAR", "ACHIEVEMENTS") + "  $d / ${l.size}", color = W, fontSize = 16.sp, letterSpacing = 1.sp)
                        DlBar(d.toFloat() / l.size, Modifier.padding(top = 8.dp))
                    }
                }
            }
            items(achs.orEmpty(), key = { it.api }) { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(2.dp)).background(Steam.panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(a.icon, null, Modifier.size(56.dp).clip(RoundedCornerShape(2.dp)).background(Color.DarkGray))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(a.name, fontWeight = FontWeight.Bold, color = if (a.done) W else Steam.dim)
                        Text(a.desc.ifBlank { if (a.hidden) t("Gizli başarım", "Hidden achievement") else "" }, fontSize = 13.sp, color = Steam.dim)
                        if (a.done && a.time > 0) Text(t("Açıldı: ", "Unlocked: ") + df.format(Date(a.time * 1000)), fontSize = 12.sp, color = Steam.greenA)
                    }
                    if (a.percent >= 0) Text("%.1f%%".format(a.percent), fontSize = 13.sp, color = Steam.link)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
