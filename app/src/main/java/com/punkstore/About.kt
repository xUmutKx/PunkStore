package com.punkstore

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun gearPath(cx: Float, cy: Float, rOut: Float, rIn: Float, teeth: Int, hole: Float): Path {
    val p = Path().apply { fillType = PathFillType.EvenOdd }
    for (i in 0 until teeth) {
        val a = 2 * PI * i / teeth; val w = 2 * PI / teeth
        listOf(0.0 to rIn, 0.08 to rOut, 0.42 to rOut, 0.5 to rIn).forEachIndexed { k, (off, r) ->
            val ang = a + off * w
            val x = (cx + r * cos(ang)).toFloat(); val y = (cy + r * sin(ang)).toFloat()
            if (i == 0 && k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
    }
    p.close()
    p.addOval(androidx.compose.ui.geometry.Rect(cx - hole, cy - hole, cx + hole, cy + hole))
    return p
}

@Composable
fun Gear(size: Int, teeth: Int, dir: Float, colors: List<Color>, modifier: Modifier = Modifier, seconds: Int = 12) {
    val rot by rememberInfiniteTransition(label = "g").animateFloat(0f, 360f * dir, infiniteRepeatable(tween(seconds * 1000, easing = LinearEasing)), label = "r")
    Canvas(modifier.size(size.dp).rotate(rot)) {
        val c = this.size.width / 2
        drawPath(gearPath(c, c, c * .98f, c * .78f, teeth, c * .24f), Brush.linearGradient(colors, Offset(0f, 0f), Offset(this.size.width, this.size.height)))
    }
}

/** Logo işareti: büyük, kalın, 8 dişli beyaz çark; ortası delik (zemin rengi görünür). */
@Composable
fun PunkEmblem(size: Int, modifier: Modifier = Modifier, spin: Boolean = true, seconds: Int = 16, hole: Color = Color(0xFF245FA0)) {
    val rot by rememberInfiniteTransition(label = "e").animateFloat(0f, if (spin) 360f else 0f, infiniteRepeatable(tween(seconds * 1000, easing = LinearEasing)), label = "er")
    Canvas(modifier.size(size.dp)) {
        val k = this.size.width / 108f
        drawRotate(rot, Offset(54f * k, 54f * k)) {
            drawPath(gearPath(54f * k, 54f * k, 36f * k, 29f * k, 8, 11f * k), Brush.verticalGradient(listOf(Color.White, Color(0xFF7CC4FF))))
        }
    }
}

/** Bilgi sayfası: ışın demeti, süzülen kıvılcımlar, dişli zinciri, harf harf giren logo, parıltı, cam kaydırması. */
@Composable
fun AboutScreen(s: Store, onBack: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val tl = rememberInfiniteTransition(label = "t")
    val time by tl.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "time")
    val rays by tl.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "rays")
    val pulse by tl.animateFloat(.85f, 1.1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val bob by tl.animateFloat(-7f, 7f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val glitch by tl.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "gl")
    val sweep by tl.animateFloat(-.5f, 1.5f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "sw")
    val g = glitch > .94f
    val logoIn by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = .4f, stiffness = 120f), label = "li")
    val rows = listOf(
        t("F-Droid + Google Play + Steam, tek mağazada.", "F-Droid + Google Play + Steam in one store."),
        t("${s.apps.size} uygulama · seviye ${s.level} · ${s.achUnlocked.size} başarım", "${s.apps.size} apps · level ${s.level} · ${s.achUnlocked.size} achievements"),
        t("Gün serisi: ${s.streak} 🔥", "Day streak: ${s.streak} 🔥"),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B6E9E), Color(0xFF1D2C5A), Color(0xFF0B1020))))) {
        // dönen ışın demeti
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * .28f)
            for (i in 0 until 14) {
                val a = Math.toRadians((rays + i * (360.0 / 14))).toFloat()
                val p = Path().apply { moveTo(c.x, c.y); lineTo(c.x + cos(a) * size.height, c.y + sin(a) * size.height); lineTo(c.x + cos(a + .09f) * size.height, c.y + sin(a + .09f) * size.height); close() }
                drawPath(p, Color(0x14B7E3FF))
            }
            // süzülen kıvılcımlar
            for (i in 0 until 38) {
                val seed = (i * 7919) % 1000 / 1000f
                val y = size.height * (1f - ((time * (.4f + seed) + seed * 3f) % 1f))
                val x = size.width * seed + sin((time * 6.28f * 2f) + i) * 14f
                drawCircle(Color(0xFFB7E3FF).copy(alpha = (.15f + .5f * seed) * (1f - ((time * (.4f + seed) + seed * 3f) % 1f))), 1.5f + 3f * seed, Offset(x, y))
            }
        }
        IconButton(onBack, Modifier.statusBarsPadding().padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = Color.White) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.size(230.dp).offset(y = bob.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(230.dp).scale(pulse).background(Brush.radialGradient(listOf(Color(0x66B7E3FF), Color.Transparent)), CircleShape))
                Box(Modifier.size(200.dp).scale(logoIn.coerceIn(0f, 1.2f)).background(Brush.verticalGradient(listOf(Color(0xFF2F86C6), Color(0xFF1B3E7A))), CircleShape).border(3.dp, Color.White, CircleShape))
                PunkEmblem(150, Modifier.scale(logoIn.coerceIn(0f, 1.2f)))
                Gear(46, 8, -1f, listOf(Color(0x99FFFFFF), Color(0x55CFE8FA)), Modifier.offset((-78).dp, 66.dp).scale(logoIn.coerceIn(0f, 1.2f)), 7)
                Gear(34, 8, 1f, listOf(Color(0x99FFFFFF), Color(0x55CFE8FA)), Modifier.offset(82.dp, (-62).dp).scale(logoIn.coerceIn(0f, 1.2f)), 6)
            }
            Spacer(Modifier.height(14.dp))
            // harf harf gelen logo + cam kaydırma + glitch
            Box {
                if (g) PunkLogo(46f, Modifier.offset(3.dp, 0.dp).alpha(.7f), Color(0xFFFF3C6E), Color(0xFFFF3C6E), frame = false, align = Alignment.CenterHorizontally)
                if (g) PunkLogo(46f, Modifier.offset((-3).dp, 0.dp).alpha(.7f), Color(0xFF3CF0FF), Color(0xFF3CF0FF), frame = false, align = Alignment.CenterHorizontally)
                PunkLogo(46f, showSteam = true, modifier = Modifier.graphicsLayer { scaleX = .6f + .4f * logoIn.coerceIn(0f, 1f); scaleY = scaleX; alpha = logoIn.coerceIn(0f, 1f) }, align = Alignment.CenterHorizontally)
                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color.Transparent, Color(0x33FFFFFF), Color.Transparent), Offset(sweep * 900f - 200f, 0f), Offset(sweep * 900f + 100f, 200f))))
            }
            Spacer(Modifier.height(26.dp))
            rows.forEachIndexed { i, r ->
                var vis by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(500L + i * 450L); vis = true }
                val a by animateFloatAsState(if (vis) 1f else 0f, tween(700), label = "r$i")
                Text(r, Modifier.alpha(a).offset(y = ((1 - a) * 24).dp).padding(vertical = 5.dp), fontFamily = LogoFont, fontSize = if (i == 0) 17.sp else 14.sp, color = if (i == 0) Color.White else Color(0xFFB7E3FF), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(30.dp))
            Text("Punk Store 0.54", color = Color(0xFF8BA6B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(t("GPL-3.0 · F-Droid ve Aurora Store projelerinden esinlenmiştir.\nSteam tarzı arayüz bir hayranlık çalışmasıdır; Valve ile bağlantısı yoktur.", "GPL-3.0 · Inspired by the F-Droid and Aurora Store projects.\nThe Steam-style UI is a fan tribute and is not affiliated with Valve."), Modifier.padding(top = 6.dp), color = Color(0xFF8BA6B8), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
        }
    }
}
