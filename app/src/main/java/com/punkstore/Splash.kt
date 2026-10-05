package com.punkstore

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Hızlı açılış (~1.3 sn): kurulu uygulamaların gerçek ikonlarından çapraz akan kolaj + patlayarak oturan logo. */
@Composable
fun SplashOverlay(s: Store, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var icons by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    LaunchedEffect(Unit) {
        icons = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val def = pm.defaultActivityIcon.constantState
            s.installed.keys.filter { it != ctx.packageName && pm.getLaunchIntentForPackage(it) != null }.shuffled().take(60).mapNotNull { p -> runCatching {
                val ai = pm.getApplicationInfo(p, 0); if (ai.icon == 0) return@runCatching null
                val d = pm.getApplicationIcon(p); if (d.constantState == def) return@runCatching null
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 96, 96); d.draw(ACanvas(bmp)); bmp.asImageBitmap()
            }.getOrNull() }
        }
    }
    val covers = remember(s.apps.size) { s.apps.mapNotNull { it.cover ?: it.icon }.shuffled().take(30) + s.steamMap.values.mapNotNull { it.banner.ifBlank { null } }.shuffled().take(10) }
    val drift by rememberInfiniteTransition(label = "d").animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "dr")
    val logo = remember { Animatable(0f) }
    val boom = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val spin by rememberInfiniteTransition(label = "s").animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sp")
    LaunchedEffect(Unit) {
        logo.animateTo(1.15f, spring(dampingRatio = .45f, stiffness = 500f))
        boom.animateTo(1f, tween(260)); delay(250)
        fade.animateTo(0f, tween(220)); onDone()
    }
    Box(Modifier.fillMaxSize().alpha(fade.value).background(Brush.verticalGradient(listOf(Color(0xFF1B6E9E), Color(0xFF1D2C5A), Color(0xFF0B1020)))).clickable { onDone() }) {
        Row(Modifier.fillMaxSize().graphicsLayer { rotationZ = -18f; scaleX = 1.8f; scaleY = 1.8f }.alpha(.75f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { col ->
                Column(Modifier.weight(1f).offset(y = ((if (col % 2 == 0) -1 else 1) * drift * 140f - 70f).dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(9) { r ->
                        val idx = col * 9 + r
                        val catalog = if (idx % 3 == 2) covers.getOrNull(idx / 3 % maxOf(covers.size, 1)) else null
                        val bm = if (catalog != null) null else icons.getOrNull(idx % maxOf(icons.size, 1))
                        val hue = ((col * 9 + r) * 37 % 360).toFloat()
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color.hsv(hue, .5f, .55f), Color.hsv((hue + 40) % 360, .6f, .25f))))) {
                            bm?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            catalog?.let { coil.compose.AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x00000000), Color(0xB0050914)))))
        Box(Modifier.align(Alignment.Center).size(260.dp).scale(.3f + boom.value * 3f).alpha(1f - boom.value).background(Color(0x66FFFFFF), CircleShape))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).scale(logo.value).alpha(logo.value.coerceIn(0f, 1f)).background(Brush.verticalGradient(listOf(Color(0xFF2F86C6), Color(0xFF1B3E7A))), CircleShape).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                PunkEmblem(124, Modifier, true, 5)
            }
            Spacer(Modifier.height(8.dp))
            PunkLogo(52f, Modifier.scale(logo.value).alpha(logo.value.coerceIn(0f, 1f)), align = Alignment.CenterHorizontally)
        }
    }
}
