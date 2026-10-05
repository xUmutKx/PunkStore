package com.punkstore

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Tinder tarzı keşif: sağa = beğen (istek listesine), sola = geç, yukarı butonu = ayrıntı, indir butonu = hemen yükle. */
@Composable
fun DiscoverScreen(s: Store, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var filters by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("") }
    val deck = remember(s.deckVersion, s.apps.size, s.filters, mode, s.steamMap.size) { s.deck(mode) }
    val scope = rememberCoroutineScope()
    val top = deck.firstOrNull()
    val x = remember(top?.pkg) { Animatable(0f) }
    var img by remember(top?.pkg) { mutableIntStateOf(0) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun fling(like: Boolean) {
        val a = top ?: return
        if (s.haptic) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        scope.launch { x.animateTo(if (like) 1600f else -1600f, androidx.compose.animation.core.tween(220)); if (like) s.discoverLike(a) else s.discoverSkip(a) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("KEŞFET", "DISCOVER"), Modifier.weight(1f), fontSize = 20.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            FilterButton(s) { filters = true }
        }
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cats = listOf("" to t("Hepsi", "All"), "android" to "Android", "steam" to "Steam", "games" to t("Oyunlar", "Games"), "fdroid" to "F-Droid", "play" to "Google Play") + s.categories.take(14).map { it to I18n.category(it) }
            items(cats.size) { i -> FilterChip(mode == cats[i].first, { mode = cats[i].first }, { Text(cats[i].second, fontSize = 13.sp) }) }
        }
        if (s.tasteTop.isNotEmpty()) Text(t("Zevkin: ", "Your taste: ") + s.tasteTop.joinToString(" · ") { I18n.category(it) }, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (top == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t("Gösterilecek yeni uygulama kalmadı.", "No more apps to show."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ s.resetDiscover() }) { Text(t("Baştan başla", "Start over")) }
            } else {
                deck.getOrNull(1)?.let { next -> DiscoverCard(s, next, 0, Modifier.fillMaxSize().graphicsLayer { scaleX = .94f; scaleY = .94f; translationY = 24f }) }
                Box(Modifier.fillMaxSize()
                    .graphicsLayer { translationX = x.value; rotationZ = x.value / 40f }
                    .pointerInput(top.pkg) {
                        detectDragGestures(
                            onDragEnd = { when { x.value > 280f -> fling(true); x.value < -280f -> fling(false); else -> scope.launch { x.animateTo(0f) } } },
                            onDragCancel = { scope.launch { x.animateTo(0f) } },
                        ) { change, drag -> change.consume(); scope.launch { x.snapTo(x.value + drag.x) } }
                    }) {
                    DiscoverCard(s, top, img, Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { img++ })
                    if (x.value > 40f) Text(t("BEĞEN", "LIKE"), Modifier.align(Alignment.TopStart).padding(24.dp).graphicsLayer { rotationZ = -12f; alpha = (x.value / 280f).coerceIn(0f, 1f) }.background(Color(0x55000000)).padding(8.dp), color = Color(0xFF8CC63F), fontSize = 32.sp, fontWeight = FontWeight.Black)
                    if (x.value < -40f) Text(t("GEÇ", "NOPE"), Modifier.align(Alignment.TopEnd).padding(24.dp).graphicsLayer { rotationZ = 12f; alpha = (-x.value / 280f).coerceIn(0f, 1f) }.background(Color(0x55000000)).padding(8.dp), color = Color(0xFFE5484D), fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (top != null) Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Filled.Close, Color(0xFFE5484D), 56) { fling(false) }
            RoundBtn(Icons.Filled.Info, Steam.link, 44) { onOpen(top.pkg) }
            RoundBtn(Icons.Filled.Download, Color(0xFF75B022), 44) { s.getOrUpdate(top, ctx) }
            RoundBtn(Icons.Filled.Favorite, Color(0xFFFF5C8A), 56) { fling(true) }
        }
    }
    if (filters) FilterSheet(s) { filters = false }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF222831)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * .5f).dp)) }
}

@Composable
private fun DiscoverCard(s: Store, a: AppItem, imgIndex: Int, modifier: Modifier) {
    val gal = remember(a.pkg) { (a.screenshots + listOf(a.banner)).filter { it.isNotBlank() }.distinct() }
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Steam.card)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Banner(a, Modifier.fillMaxSize(), fade = false, contain = true, image = gal.getOrNull(imgIndex % maxOf(gal.size, 1)))
            if (gal.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                gal.indices.take(10).forEach { i -> Box(Modifier.size(width = if (i == imgIndex % gal.size) 18.dp else 6.dp, height = 4.dp).background(if (i == imgIndex % gal.size) Color.White else Color(0x88FFFFFF), RoundedCornerShape(2.dp))) }
            }
            Text(when (a.source) { "STEAM" -> "STEAM"; "PLAY" -> "GOOGLE PLAY"; else -> "F-DROID" }, Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF14181F)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.source != "STEAM") { AppIcon(a, 48, 10); Spacer(Modifier.width(10.dp)) }
                Text(a.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(a.summary.ifBlank { a.description.replace(Regex("<[^>]+>"), "") }, Modifier.padding(top = 6.dp), color = Color(0xFFD0D6DD), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (a.discount > 0) Chip("-${a.discount}%")
                if (a.rating > 0) Chip(String.format("★ %.1f", a.rating))
                Chip(if (a.price.isBlank()) t("Ücretsiz", "Free") else a.price)
                if (!a.ads && a.source == "FDROID") Chip(t("Reklamsız", "No ads"))
                if (a.apkSize > 0) Chip(sizeText(a.apkSize))
                (a.categories.firstOrNull())?.let { Chip(I18n.category(it)) }
            }
        }
    }
}

@Composable
private fun Chip(text: String) = Text(text, Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp, maxLines = 1)
