package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.ExperimentalLayoutApi

class Achv(val id: String, val icon: ImageVector, val group: String, val tr: String, val en: String, val dTr: String, val dEn: String, val target: Int, val cur: (Store) -> Int)

val ACHIEVEMENTS: List<Achv> = listOf(
    Achv("get1", Icons.Filled.Download, "Yükleme", "İlk Adım", "First Step", "Punk Store'dan ilk uygulamanı yükle.", "Get your first app with Punk Store.", 1) { it.getCount },
    Achv("get5", Icons.Filled.Download, "Yükleme", "Yükleyici", "Downloader", "Toplam 5 uygulama yükle.", "Download 5 apps.", 5) { it.getCount },
    Achv("get25", Icons.Filled.Download, "Yükleme", "Yükleme Makinesi", "Download Machine", "Toplam 25 uygulama yükle.", "Download 25 apps.", 25) { it.getCount },
    Achv("get100", Icons.Filled.Download, "Yükleme", "Yüzbaşı", "Centurion", "Toplam 100 yükleme.", "100 downloads in total.", 100) { it.getCount },
    Achv("lib5", Icons.Filled.Apps, "Kütüphane", "Küçük Koleksiyon", "Small Collection", "Kütüphanende 5 uygulama olsun.", "Have 5 apps in your library.", 5) { it.libApps.size },
    Achv("lib15", Icons.Filled.Apps, "Kütüphane", "Koleksiyoncu", "Collector", "Kütüphanende 15 uygulama olsun.", "Have 15 apps in your library.", 15) { it.libApps.size },
    Achv("lib40", Icons.Filled.Apps, "Kütüphane", "Arşivci", "Archivist", "Kütüphanende 40 uygulama olsun.", "Have 40 apps in your library.", 40) { it.libApps.size },
    Achv("wish1", Icons.Filled.Favorite, "İstek listesi", "İlk Dilek", "First Wish", "İstek listene bir uygulama ekle.", "Add an app to your wishlist.", 1) { it.wishlist.size },
    Achv("wish5", Icons.Filled.Favorite, "İstek listesi", "Hayalperest", "Dreamer", "İstek listene 5 uygulama ekle.", "Wishlist 5 apps.", 5) { it.wishlist.size },
    Achv("wish20", Icons.Filled.Favorite, "İstek listesi", "Sonsuz Liste", "Endless List", "İstek listende 20 uygulama olsun.", "Wishlist 20 apps.", 20) { it.wishlist.size },
    Achv("like1", Icons.Filled.Explore, "Keşfet", "İlk Kaydırma", "First Swipe", "Keşfet'te bir uygulamayı beğen.", "Like an app in Discover.", 1) { it.liked },
    Achv("like10", Icons.Filled.Explore, "Keşfet", "Kaşif", "Explorer", "Keşfet'te 10 uygulamayı beğen.", "Like 10 apps in Discover.", 10) { it.liked },
    Achv("like50", Icons.Filled.Explore, "Keşfet", "Tinder Ustası", "Swipe Master", "Keşfet'te 50 uygulamayı beğen.", "Like 50 apps in Discover.", 50) { it.liked },
    Achv("seen100", Icons.Filled.Visibility, "Keşfet", "Göz Gezdirici", "Window Shopper", "Keşfet'te 100 kart gör.", "See 100 cards in Discover.", 100) { it.seen.size },
    Achv("launch10", Icons.Filled.PlayArrow, "Kullanım", "Isınma", "Warm-up", "Uygulamaları toplam 10 kez aç.", "Launch apps 10 times.", 10) { it.launches.values.sum() },
    Achv("launch50", Icons.Filled.PlayArrow, "Kullanım", "Güç Kullanıcısı", "Power User", "Uygulamaları toplam 50 kez aç.", "Launch apps 50 times.", 50) { it.launches.values.sum() },
    Achv("launch250", Icons.Filled.PlayArrow, "Kullanım", "Bağımlı", "Addicted", "Uygulamaları toplam 250 kez aç.", "Launch apps 250 times.", 250) { it.launches.values.sum() },
    Achv("fresh", Icons.Filled.Update, "Güncellemeler", "Güncel Tut", "Stay Fresh", "3+ kurulu uygulaman var ve bekleyen güncelleme yok.", "3+ apps installed and no pending updates.", 1) { s -> if (s.libApps.count { s.isInstalled(it) } >= 3 && s.updateCount == 0) 1 else 0 },
    Achv("check", Icons.Filled.Notifications, "Güncellemeler", "Nöbetçi", "Watchman", "Güncelleme denetimini en az bir kez çalıştır.", "Run an update check at least once.", 1) { if (it.lastCheck > 0) 1 else 0 },
    Achv("lv5", Icons.Filled.Star, "Seviye", "Seviye 5", "Level 5", "Seviye 5'e ulaş.", "Reach level 5.", 5) { it.level },
    Achv("lv10", Icons.Filled.Star, "Seviye", "Seviye 10", "Level 10", "Seviye 10'a ulaş.", "Reach level 10.", 10) { it.level },
    Achv("lv25", Icons.Filled.Star, "Seviye", "Seviye 25", "Level 25", "Seviye 25'e ulaş.", "Reach level 25.", 25) { it.level },
    Achv("name", Icons.Filled.Person, "Profil", "Kendin Ol", "Be Yourself", "Profil adını değiştir.", "Change your profile name.", 1) { if (it.userName != "Punk") 1 else 0 },
    Achv("photo", Icons.Filled.PhotoCamera, "Profil", "Yüz Yüze", "Face to Face", "Profiline fotoğraf koy.", "Set a profile photo.", 1) { if (it.photo != null) 1 else 0 },
    Achv("show3", Icons.Filled.Dashboard, "Profil", "Vitrin", "Showcase", "Vitrinine 3 uygulama koy.", "Put 3 apps in your showcase.", 3) { it.showcase.size },
    Achv("theme", Icons.Filled.Palette, "Profil", "Nostalji", "Nostalgia", "Steam 2006 ya da 2013 temasını dene.", "Try the Steam 2006 or 2013 theme.", 1) { if (it.design == Design.STEAM2006 || it.design == Design.STEAM2013) 1 else 0 },
    Achv("foss5", Icons.Filled.Code, "Kaynak", "Açık Kaynak Aşığı", "FOSS Fan", "F-Droid'den 5 uygulama kur.", "Install 5 F-Droid apps.", 5) { s -> s.fdroid.count { s.isInstalled(it) } },
    Achv("play1", Icons.Filled.Shop, "Kaynak", "Play Gezgini", "Play Explorer", "Google Play'den bir uygulama kur.", "Install an app from Google Play.", 1) { s -> s.apps.count { it.source == "PLAY" && s.isInstalled(it) } },
    Achv("noads", Icons.Filled.Block, "Kaynak", "Reklamsız Yaşam", "Ad-free Life", "'Reklamsız' filtresini aç.", "Turn on the 'No ads' filter.", 1) { if (it.filters.noAds) 1 else 0 },
    Achv("google", Icons.Filled.AccountCircle, "Hesap", "Giriş Yaptın", "Signed In", "Google hesabınla giriş yap.", "Sign in with your Google account.", 1) { if (it.googleEmail != null) 1 else 0 },
    Achv("root", Icons.Filled.Security, "Hesap", "Süper Kullanıcı", "Superuser", "Root kurulum yöntemini seç.", "Choose the root install method.", 1) { if (it.method == InstallMethod.ROOT) 1 else 0 },
    Achv("streak3", Icons.Filled.LocalFireDepartment, "Seri", "Alışkanlık", "Habit", "3 gün üst üste uygulamayı aç.", "Open the app 3 days in a row.", 3) { it.streak },
    Achv("streak7", Icons.Filled.LocalFireDepartment, "Seri", "Haftalık Seri", "Weekly Streak", "7 gün üst üste uygulamayı aç.", "Open the app 7 days in a row.", 7) { it.streak },
    Achv("col1", Icons.Filled.Folder, "Düzen", "Düzenli", "Organized", "Bir koleksiyon oluştur ve uygulama ekle.", "Create a collection and add an app.", 1) { s -> s.collections.values.count { it.isNotEmpty() } },
    Achv("pin3", Icons.Filled.PushPin, "Düzen", "Favori Üçlü", "Fave Three", "3 uygulamayı favorile.", "Pin 3 apps.", 3) { it.pins.size },
    Achv("steamview", Icons.Filled.Storefront, "Steam", "Pazar Gezgini", "Market Browser", "5 Steam oyununa göz at.", "View 5 Steam games.", 5) { s -> s.recent.count { it.startsWith("steam:") } },
    Achv("steam", Icons.Filled.SportsEsports, "Hesap", "Buhar Bağlantısı", "Steam Link", "Steam hesabını bağla.", "Link your Steam account.", 1) { if (it.steamId.isNotBlank()) 1 else 0 },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsScreen(s: Store, onBack: () -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val done = ACHIEVEMENTS.count { it.id in s.achUnlocked }
    val list = ACHIEVEMENTS.filter { when (filter) { 1 -> it.id in s.achUnlocked; 2 -> it.id !in s.achUnlocked; else -> true } }
        .sortedByDescending { it.id in s.achUnlocked }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back")) }
            Text(t("Başarımlar", "Achievements"), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("$done / ${ACHIEVEMENTS.size}  (${done * 100 / ACHIEVEMENTS.size}%)", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator({ done.toFloat() / ACHIEVEMENTS.size }, Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), color = Steam.btn)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(t("Hepsi", "All"), t("Açık", "Unlocked"), t("Kilitli", "Locked")).forEachIndexed { i, l -> FilterChip(filter == i, { filter = i }, { Text(l) }) } }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { a ->
                val ok = a.id in s.achUnlocked; val cur = a.cur(s).coerceAtMost(a.target)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(if (ok) Steam.row else Steam.card).steamBevel().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(if (ok) Steam.btn else Color(0xFF3A3F47)), contentAlignment = Alignment.Center) { Icon(if (ok) a.icon else Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(a.tr, a.en), fontWeight = FontWeight.Bold, color = if (ok) Color.White else Steam.dim)
                        Text(t(a.dTr, a.dEn), fontSize = 12.sp, color = Steam.dim)
                        if (a.target > 1 && !ok) { LinearProgressIndicator({ cur.toFloat() / a.target }, Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp), color = Steam.btn); Text("$cur / ${a.target}", fontSize = 11.sp, color = Steam.dim) }
                        if (ok) s.achTime[a.id]?.let { Text(t("Açıldı: ", "Unlocked: ") + df.format(Date(it)), fontSize = 11.sp, color = Steam.greenA) }
                    }
                    Text(a.group, fontSize = 10.sp, color = Steam.dim)
                }
            }
        }
    }
}
