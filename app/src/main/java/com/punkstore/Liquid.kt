package com.punkstore

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/** Basılı tutunca renk kayan, slime gibi sıkışıp esneyen yarı saydam "liquid glass" yüzey. */
fun Modifier.liquidGlass(base: Color, onClick: () -> Unit, enabled: Boolean = true, fill: Float = 0f): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // Basılıyken renk tabana göre kayar (yeşil -> turkuaz -> mavi), bırakınca geri döner
    val shift by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 700 else 450), label = "shift")
    val sx by animateFloatAsState(if (pressed) 1.07f else 1f, spring(.32f, 380f), label = "sx")
    val sy by animateFloatAsState(if (pressed) .88f else 1f, spring(.32f, 380f), label = "sy")
    val drift = rememberInfiniteTransition(label = "drift").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    val tint = lerp(base, Color(0xFF3AA6FF), shift * .75f)
    this.graphicsLayer { scaleX = sx; scaleY = sy }
        .drawBehind {
            val r = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
            // cam gövdesi: hafif saydam renk
            drawRoundRect(Brush.verticalGradient(listOf(tint.copy(alpha = .78f), tint.copy(alpha = .52f))), cornerRadius = r)
            // ilerleme dolgusu (indirme)
            if (fill > 0f) drawRoundRect(tint.copy(alpha = .95f), size = androidx.compose.ui.geometry.Size(size.width * fill, size.height), cornerRadius = r)
            // yumuşak, kayan ışık lekeleri (blur hissi)
            val cx = size.width * (.25f + .5f * drift.value)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .30f + .15f * shift), Color.Transparent), Offset(cx, size.height * .1f), size.height * 1.1f), size.height * 1.1f, Offset(cx, size.height * .1f))
            drawCircle(Brush.radialGradient(listOf(lerp(tint, Color.White, .35f).copy(alpha = .35f), Color.Transparent), Offset(size.width - cx, size.height), size.height), size.height, Offset(size.width - cx, size.height))
            // üst parlama + ince cam kenarı
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .38f), Color.Transparent), endY = size.height * .5f), cornerRadius = r)
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .12f))), cornerRadius = r, style = Stroke(1.2.dp.toPx()))
        }
        .combinedClickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}
