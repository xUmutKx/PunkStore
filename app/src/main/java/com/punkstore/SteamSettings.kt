package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Steam temalarında ayarlar: Steam mobil hesap sayfası gibi büyük satırlar, gri bölüm başlıkları, mavi anahtarlar. */
@Composable
fun SteamSettingsScreen(s: Store, onBack: () -> Unit, onGoogleLogin: () -> Unit, onAbout: () -> Unit, onNav: (String) -> Unit) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(I18n.pref) }
    var disp by remember { mutableStateOf(s.dispenser) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxSize().background(Steam.bg)) {
        SteamPageHeader(t("Ayarlar", "Settings"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SteamSection(t("Hesaplar", "Accounts"))
            SteamRow(t("Steam hesabı", "Steam account"), s.steamProfile?.name ?: if (s.steamId.isNotBlank()) s.steamWho.ifBlank { s.steamId } else t("Bağlı değil — oyunların ve başarımların için bağla", "Not linked — link for your games and achievements"), Icons.Filled.SportsEsports, onClick = { onNav("steam") })
            SteamRow(t("Google hesabı", "Google account"), s.googleEmail ?: t("Anonim oturum (giriş yaparsan Play daha hızlı)", "Anonymous session (sign in for faster Play)"), Icons.Filled.AccountCircle,
                onClick = { if (s.googleEmail == null) onGoogleLogin() }, trailing = if (s.googleEmail != null) { { Text(t("Çıkış", "Sign out"), Modifier.clip(RoundedCornerShape(4.dp)).background(Steam.panel2).pressScale({ s.googleLogout() }).padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold) } } else null)

            SteamSection(t("Alt sekmeler (sıralama)", "Bottom tabs (order)"))
            s.orderedTabs().forEachIndexed { i, tb ->
                SteamRow(tb.label, null, null, chevron = false, trailing = {
                    Row {
                        Text("▲", Modifier.clip(RoundedCornerShape(4.dp)).background(Steam.panel2).pressScale({ s.moveTab(tb, -1) }).padding(horizontal = 14.dp, vertical = 8.dp), color = if (i > 0) Color.White else Steam.dim)
                        Spacer(Modifier.width(6.dp))
                        Text("▼", Modifier.clip(RoundedCornerShape(4.dp)).background(Steam.panel2).pressScale({ s.moveTab(tb, 1) }).padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
                    }
                })
            }

            SteamSection(t("Tasarım", "Design"))
            Design.values().forEach { d ->
                val (title, sub) = when (d) {
                    Design.STEAM -> t("Steam", "Steam") to t("Bugünkü Steam mobil (ana tema)", "Today's Steam mobile (default)")
                    Design.STEAM2013 -> "Steam 2013" to t("Karbon siyahı, camsı yeşil düğmeler", "Carbon black, glassy green buttons")
                    Design.STEAM2006 -> "Steam 2006" to t("Zeytin yeşili, kabartmalı kenarlar", "Olive green, bevelled edges")
                    else -> "Material You" to t("Android'in kendi renkleri", "Android's own colors")
                }
                SteamChoiceRow(title, sub, s.design == d) { s.changeDesign(d) }
            }
            SteamSwitchRow(t("Açılış animasyonu", "Startup animation"), on = s.splash) { s.changeSplash(it) }
            SteamSwitchRow(t("Yüzen dock", "Floating dock"), t("Alt çubuk ekranın üstünde yüzen yuvarlak bir dock olur", "Bottom bar becomes a floating rounded dock"), s.floatDock) { s.changeFloatDock(it) }
            SteamChoiceRow(t("Basılı tutunca: alttan açılan sayfa", "Long-press: bottom sheet"), t("Uygulamalara basılı tutunca alttan yukarı çıkan işlem sayfası", "Action sheet that slides up from the bottom"), s.longPress == LongPressMode.SHEET) { s.changeLongPress(LongPressMode.SHEET) }
            SteamChoiceRow(t("Basılı tutunca: küçük menü", "Long-press: small menu"), selected = s.longPress == LongPressMode.MENU) { s.changeLongPress(LongPressMode.MENU) }
            SteamChoiceRow(t("Basılı tutunca: hiçbir şey", "Long-press: do nothing"), selected = s.longPress == LongPressMode.OFF) { s.changeLongPress(LongPressMode.OFF) }
            SteamSwitchRow(t("Kalın yazı", "Bold text"), t("Tüm uygulamada yazılar daha kalın", "Makes text heavier across the app"), s.boldText) { s.changeBoldText(it) }
            SteamSwitchRow(t("Titreşim", "Haptics"), t("Keşfet kaydırmalarında", "On Discover swipes"), s.haptic) { s.changeHaptic(it) }
            SteamRow(t("Yazı boyutu", "Text size"), "%${(s.fontScale * 100).toInt()}", chevron = false)
            Box(Modifier.fillMaxWidth().background(Steam.panel).padding(horizontal = 20.dp)) {
                Slider(s.fontScale, { s.changeFontScale((it * 20).toInt() / 20f) }, valueRange = .8f..1.4f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Steam.blue, inactiveTrackColor = Color(0xFF4A4F59)))
            }

            SteamSection(t("Dil", "Language"))
            listOf(LangPref.EN to "English", LangPref.TR to "Türkçe", LangPref.AUTO to t("Telefonun dili", "Device language")).forEach { (p, title) ->
                SteamChoiceRow(title, null, lang == p) { s.changeLang(p); lang = p }
            }

            SteamSection(t("Kurulum yöntemi", "Install method"))
            SteamChoiceRow(t("Oturum kurucusu", "Session installer"), t("Önerilen · Android PackageInstaller, her kurulumda onay ister", "Recommended · Android PackageInstaller, asks each time"), s.method == InstallMethod.SESSION) { s.changeMethod(InstallMethod.SESSION) }
            SteamChoiceRow(t("Sistem kurucusu", "Native installer"), t("Sistemin kendi kurulum ekranı", "The system install screen"), s.method == InstallMethod.NATIVE) { s.changeMethod(InstallMethod.NATIVE) }
            SteamChoiceRow("Root", t("su ile sessiz kurulum / kaldırma", "Silent install / uninstall via su"), s.method == InstallMethod.ROOT) { s.changeMethod(InstallMethod.ROOT) }
            if (s.method == InstallMethod.ROOT) SteamRow(when (s.rootOk) { true -> t("✓ Root erişimi var", "✓ Root access granted"); false -> t("✗ Root erişimi yok", "✗ No root access"); null -> t("Root denetleniyor…", "Checking root…") },
                t("Yeniden denemek için dokun", "Tap to retry"), titleColor = if (s.rootOk == true) Steam.greenA else Color(0xFFE07B53), onClick = { s.testRoot() }, chevron = false)

            SteamSection(t("İndirmeler", "Downloads"))
            SteamRow(t("İndirmeler", "Downloads"), t("Kuyruk, duraklat / sürdür, geçmiş", "Queue, pause / resume, history"), Icons.Filled.Download, onClick = { onNav("downloads") })
            SteamSwitchRow(t("Kurulumdan sonra APK'yı sil", "Delete APK after install"), on = s.deleteApk) { s.changeDeleteApk(it) }
            SteamSwitchRow(t("Yalnızca Wi-Fi'de indir", "Download on Wi-Fi only"), on = s.wifiOnly) { s.changeWifiOnly(it) }
            SteamRow(t("İndirme önbelleğini temizle", "Clear download cache"), t("Yarım kalan indirmeler de silinir", "Also removes partial downloads"), Icons.Filled.DeleteSweep, onClick = { s.clearCache() }, chevron = false)

            SteamSection(t("Güncellemeler", "Updates"))
            SteamSwitchRow(t("Otomatik denetle ve bildir", "Auto-check and notify"), on = s.autoCheck) { s.changeAutoCheck(it) }
            SteamSwitchRow(t("Arka planda otomatik güncelle", "Update apps automatically in the background"), t("Uygulama kapalıyken güncellemeleri indirir ve kurar. Root ya da bu uygulamanın kurduğu uygulamalarda onaysız; diğerlerinde sistem onay ister.", "Downloads and installs updates while the app is closed. Silent with root or for apps this store installed; otherwise Android asks to confirm."), s.bgUpdate) { s.changeBgUpdate(it) }
            Row(Modifier.fillMaxWidth().background(Steam.panel).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Sıklık", "Every"), color = Steam.dim, fontSize = 15.sp)
                listOf(1, 3, 6, 12, 24).forEach { h ->
                    val on = s.intervalH == h
                    Text("$h" + t(" sa", "h"), Modifier.clip(RoundedCornerShape(4.dp)).background(if (on) Steam.blue else Steam.panel2).pressScale({ s.changeInterval(h) }).padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }

            SteamSection("Google Play")
            SteamRow(t("Durum", "Status"), if (s.playLoading) t("yükleniyor…", "loading…") else s.playError ?: t("Bağlı", "Connected"), chevron = false)
            Column(Modifier.fillMaxWidth().background(Steam.panel).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(t("Özel anonim dağıtıcı (dispenser)", "Custom anonymous dispenser"), color = Steam.dim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                SteamField(disp, { disp = it }, "https://auroraoss.com/api/auth")
                Text(t("Kaydet ve yeniden bağlan", "Save and reconnect"), Modifier.padding(top = 10.dp).clip(RoundedCornerShape(4.dp)).background(Steam.blue).pressScale({ s.changeDispenser(disp); s.loadPlay() }).padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }

            SteamSection(t("Kaynaklar", "Sources"))
            SteamRow("F-Droid", "${s.fdroid.size} " + t("uygulama", "apps"), chevron = false)
            SteamRow(t("Kataloğu yenile", "Refresh catalog"), null, Icons.Filled.Refresh, onClick = { s.refresh() }, chevron = false)
            SteamRow(t("Yedekle / geri yükle", "Backup / restore"), t("Ayarlar, istek listesi, kütüphane, koleksiyonlar", "Settings, wishlist, library, collections"), Icons.Filled.Backup, onClick = { onNav("backup") })

            SteamSection(t("Hakkında", "About"))
            SteamRow(t("Bilgi", "About"), "Punk Store $version · GPL-3.0", Icons.Filled.Info, onClick = onAbout)
            Text(t("F-Droid ve Aurora Store projelerinden esinlenmiştir. Valve ile bağlantısı yoktur.", "Inspired by F-Droid and Aurora Store. Not affiliated with Valve."), Modifier.fillMaxWidth().background(Steam.bg).padding(20.dp), color = Steam.dim, fontSize = 12.sp)
            Spacer(Modifier.height(40.dp))
        }
    }
}
