package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

private val avatarColors = listOf(
    Color(0xFF66C0F4) to Steam.panel, Color(0xFF75B022) to Color(0xFF2B4A0C), Color(0xFFE5484D) to Color(0xFF4A1013),
    Color(0xFFF5A524) to Color(0xFF4A3008), Color(0xFFA855F7) to Color(0xFF2E1065), Color(0xFF14B8A6) to Color(0xFF053B35),
)

val coverPresets = listOf(
    listOf(Color(0xFF2E3A4A), Color(0xFF171D25)), listOf(Color(0xFF7B1E5A), Color(0xFF1A0F2E)), listOf(Color(0xFF1B5E3A), Color(0xFF0B1F14)),
    listOf(Color(0xFFB8453A), Color(0xFF2A0E0C)), listOf(Color(0xFFF2A33A), Color(0xFF3B1E5E)), listOf(Color(0xFF1A9FFF), Color(0xFF0B1F3A)),
    listOf(Color(0xFF5A6A50), Color(0xFF2B3324)), listOf(Color(0xFF000000), Color(0xFF222222)),
)

@Composable
fun Avatar(s: Store, size: Int) {
    val ph = s.photo
    if (ph != null) {
        coil.compose.AsyncImage(java.io.File(ph), null, Modifier.size(size.dp).clip(RoundedCornerShape((size / 8).dp)).border(2.dp, Color(0x66FFFFFF), RoundedCornerShape((size / 8).dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        return
    }
    val (a, b) = avatarColors[s.avatar.mod(avatarColors.size)]
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 8).dp)).background(Brush.linearGradient(listOf(a, b))).border(2.dp, Color(0x66FFFFFF), RoundedCornerShape((size / 8).dp)), contentAlignment = Alignment.Center) {
        Text(s.userName.take(1).uppercase(), fontSize = (size / 2).sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

/** Steam'deki seviye rozeti: renk seviyeye göre değişir */
@Composable
fun LevelBadge(level: Int, size: Int = 36) {
    val col = when { level >= 30 -> Color(0xFFE5484D); level >= 20 -> Color(0xFFA855F7); level >= 10 -> Color(0xFF66C0F4); level >= 5 -> Color(0xFF75B022); else -> Color(0xFF8F98A0) }
    Box(Modifier.size(size.dp).clip(CircleShape).border(3.dp, col, CircleShape), contentAlignment = Alignment.Center) {
        Text("$level", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * .42f).sp)
    }
}

@Composable
fun WishButton(s: Store, a: AppItem, modifier: Modifier = Modifier) {
    val w = s.isWished(a)
    IconButton({ s.toggleWish(a) }, modifier) {
        Icon(if (w) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, t("İstek listesi", "Wishlist"), tint = if (w) Color(0xFFE5484D) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WishlistScreen(s: Store, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var sort by remember { mutableIntStateOf(0) }
    val list = s.wishApps.let { l -> when (sort) { 1 -> l.sortedBy { it.name.lowercase() }; 2 -> l.sortedByDescending { it.updated }; else -> l } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back")) }
            Text(t("İstek listesi", "Wishlist") + " (${list.size})", style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(t("Eklenme", "Date added"), t("Ad", "Name"), t("Güncelleme", "Updated")).forEachIndexed { i, l ->
                FilterChip(sort == i, { sort = i }, { Text(l) })
            }
        }
        if (list.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(t("İstek listen boş. Bir uygulamanın sayfasındaki ♥ düğmesine dokun.", "Your wishlist is empty. Tap the ♥ on an app page."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn { items(list, key = { it.pkg }) { a ->
            Box { AppRow(s, a, onOpen); WishButton(s, a, Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) }
        } }
    }
}

@Composable
fun ProfileScreen(s: Store, onOpen: (String) -> Unit, onWishlist: () -> Unit, onSettings: () -> Unit, onAchievements: () -> Unit = {}, onSteam: () -> Unit = {}, onNav: (String) -> Unit = {}) {
    var edit by remember { mutableStateOf(false) }
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val inst = s.apps.count { s.isInstalled(it) }
    val top = s.launches.entries.sortedByDescending { it.value }.take(5).mapNotNull { e -> s.byPkg(e.key)?.let { it to e.value } }
    val st = s.design.steam
    LazyColumn(Modifier.fillMaxSize().then(if (!st) Modifier.statusBarsPadding() else Modifier)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(coverPresets[s.cover.mod(coverPresets.size)] + MaterialTheme.colorScheme.background)).padding(16.dp)) {
                IconButton(onSettings, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Filled.Settings, t("Ayarlar", "Settings"), tint = Color.White) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(s, 84)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.userName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(t("Üyelik: ", "Member since ") + df.format(Date(s.joined)) + "  ·  🔥 ${s.streak}", fontSize = 12.sp, color = Steam.text)
                            if (s.status.isNotBlank()) Text(s.status, fontSize = 13.sp, color = Steam.btn, fontWeight = FontWeight.SemiBold)
                            if (s.bio.isNotBlank()) Text(s.bio, fontSize = 13.sp, color = Steam.text, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(s.level, 44); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("Seviye ${s.level}", "Level ${s.level}") + "  ·  ${s.xp} XP", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            LinearProgressIndicator({ s.levelProgress() }, Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(RoundedCornerShape(Steam.corner.dp)), color = Steam.blue, trackColor = Color(0x44000000))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton({ edit = true }) { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Profili düzenle", "Edit profile")) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(inst.toString(), t("Kurulu", "Installed"), Modifier.weight(1f)) {}
                Stat(s.wishlist.size.toString(), t("İstek listesi", "Wishlist"), Modifier.weight(1f), onWishlist)
                Stat(s.launches.values.sum().toString(), t("Açılış", "Launches"), Modifier.weight(1f)) {}
                Stat(s.getCount.toString(), t("Yükleme", "Downloads"), Modifier.weight(1f)) {}
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(4.dp)).background(Steam.btn).clickable { edit = true }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text(t("Profili düzenle", "Edit profile"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            @Composable fun Rows(items: List<Pair<String, () -> Unit>>) = items.forEach { (n, f) ->
                Row(Modifier.fillMaxWidth().skinBg(0.dp).clickable(onClick = f).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.weight(1f), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold); Icon(Icons.Filled.ChevronRight, null, tint = Steam.dim)
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(t("İÇERİĞİM", "MY CONTENT"), Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp), color = Steam.dim, fontSize = 15.sp, letterSpacing = 1.sp)
            Rows(listOf(t("İstek listesi", "Wishlist") to onWishlist, t("Steam hesabı", "Steam account") to onSteam, t("İstatistikler", "Statistics") to { onNav("stats") }, t("Koleksiyonlar", "Collections") to { onNav("collections") }, t("Ayarlar", "Settings") to onSettings))
            Text(s.googleEmail?.let { t("Google: ", "Google: ") + it } ?: t("Google hesabı bağlı değil (Ayarlar'dan giriş yap)", "No Google account (sign in via Settings)"), Modifier.padding(16.dp), color = Steam.dim, fontSize = 13.sp)
        }
        if (s.showcase.isNotEmpty()) {
            item { SectionTitle(t("Vitrin", "Showcase"), s) }
            item { androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.showcase.mapNotNull { s.resolve(it) }) { a -> Column(Modifier.width(150.dp).clickable { onOpen(a.pkg) }) { Capsule(a, Modifier.fillMaxWidth().height(86.dp), 32, Steam.corner); Text(a.name, Modifier.padding(top = 4.dp), fontSize = 12.sp, maxLines = 1) } }
            } }
        }
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAchievements).padding(16.dp, 14.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("BAŞARIMLAR", "ACHIEVEMENTS") + "  ${s.achUnlocked.size}/${ACHIEVEMENTS.size}", Modifier.weight(1f), color = Steam.btn, fontSize = 15.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                Text(t("Tümü", "All"), color = Steam.link, fontSize = 13.sp); Icon(Icons.Filled.ChevronRight, null, tint = Steam.dim)
            }
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ACHIEVEMENTS.sortedByDescending { it.id in s.achUnlocked }.take(14)) { a ->
                    val ok = a.id in s.achUnlocked
                    Column(Modifier.width(76.dp).clip(RoundedCornerShape(Steam.corner.dp)).skinBg().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (ok) a.icon else Icons.Filled.Lock, null, tint = if (ok) Steam.btn else Color(0xFF55606C), modifier = Modifier.size(30.dp))
                        Text(t(a.tr, a.en), fontSize = 10.sp, maxLines = 2, color = if (ok) Color.White else Steam.dim, lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (top.isNotEmpty()) {
            item { SectionTitle(t("En çok kullandıkların", "Most used"), s) }
            items(top, key = { it.first.pkg }) { (a, n) ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(a.pkg) }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a, 40, 4); Spacer(Modifier.width(10.dp)); Text(a.name, Modifier.weight(1f)); Text("$n ${t("açılış", "launches")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (edit) EditProfile(s) { edit = false }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Steam.blue)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfile(s: Store, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(s.userName) }
    var bio by remember { mutableStateOf(s.bio) }
    var status by remember { mutableStateOf(s.status) }
    var av by remember { mutableIntStateOf(s.avatar) }
    var cover by remember { mutableIntStateOf(s.cover) }
    var photo by remember { mutableStateOf(s.photo) }
    val show = remember { mutableStateListOf<String>().apply { addAll(s.showcase) } }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val f = java.io.File(ctx.filesDir, "avatar-${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            photo?.let { java.io.File(it).delete() }; photo = f.absolutePath
        }
    }
    AlertDialog(onDismissRequest = onDone,
        confirmButton = { TextButton({ s.saveProfile(name, av, bio); s.saveCustom(cover, status, photo, show); onDone() }) { Text(t("Kaydet", "Save")) } },
        dismissButton = { TextButton(onDone) { Text(t("Vazgeç", "Cancel")) } },
        title = { Text(t("Profili düzenle", "Edit profile")) },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text(t("Ad", "Name")) })
            OutlinedTextField(status, { status = it.take(40) }, singleLine = true, label = { Text(t("Durum / unvan", "Status / title")) }, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(bio, { bio = it.take(120) }, label = { Text(t("Hakkında", "About")) }, modifier = Modifier.padding(top = 8.dp))
            Text(t("Avatar", "Avatar"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                avatarColors.forEachIndexed { i, (a, b) ->
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(a, b))).border(if (av == i && photo == null) 3.dp else 0.dp, Color.White, RoundedCornerShape(6.dp)).clickable { av = i; photo?.let { java.io.File(it).delete() }; photo = null })
                }
            }
            Row(Modifier.padding(top = 6.dp)) {
                OutlinedButton({ pick.launch("image/*") }) { Text(t("Galeriden fotoğraf seç", "Pick a photo")) }
                if (photo != null) TextButton({ photo?.let { java.io.File(it).delete() }; photo = null }) { Text(t("Kaldır", "Remove")) }
            }
            Text(t("Kapak teması", "Cover theme"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                coverPresets.forEachIndexed { i, g -> Box(Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(g)).border(if (cover == i) 3.dp else 0.dp, Color.White, RoundedCornerShape(4.dp)).clickable { cover = i }) }
            }
            Text(t("Vitrin (en çok 4 uygulama)", "Showcase (up to 4 apps)"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.libApps.take(40).forEach { a -> FilterChip(a.pkg in show, { if (a.pkg in show) show.remove(a.pkg) else if (show.size < 4) show.add(a.pkg) }, { Text(a.name, maxLines = 1) }) }
            }
        } })
}
