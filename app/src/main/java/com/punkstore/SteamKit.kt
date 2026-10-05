package com.punkstore

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Steam mobil uygulamasının hesap/ayar sayfalarındaki parçalar (kullanıcının ekran görüntülerinden):
 * - sayfa başlığı: koyu çubuk, sol "<" ve büyük beyaz başlık
 * - bölüm başlığı: "MY CONTENT" gibi gri, aralıklı büyük harf, panelin üstünde
 * - satırlar: #2A2C34 zemin, 1 px ayırıcı, kalın beyaz yazı; alt satırı gri; sağda ">" ok
 * - istatistik kutuları: üç eşit kutu, büyük sayı + gri etiket
 * - ana düğme: tam genişlik parlak mavi (#1A9FFF), ikonlu
 */

private val W = Color.White
val SteamDivider = Color(0xFF1B1D22)

@Composable
fun SteamPageHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Steam.topBrush).statusBarsPadding().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, t("Geri", "Back"), tint = W, modifier = Modifier.clip(RoundedCornerShape(30.dp)).pressScale(onBack).padding(12.dp).size(22.dp))
        Text(title, Modifier.weight(1f), color = W, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** "MY CONTENT" tarzı bölüm başlığı */
@Composable
fun SteamSection(title: String, modifier: Modifier = Modifier) =
    Text(title.uppercase(), modifier.fillMaxWidth().background(Steam.panel).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), color = Steam.dim, fontSize = 15.sp, letterSpacing = 2.sp)

/** Kalın başlıklı liste satırı; isteğe bağlı alt yazı, sol ikon, sağ öğe (yoksa ok) */
@Composable
fun SteamRow(title: String, sub: String? = null, icon: ImageVector? = null, dark: Boolean = false, chevron: Boolean = true, titleColor: Color = W, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(if (dark) Steam.bg else Steam.panel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SteamDivider))
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = if (sub != null) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Steam.text, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, color = Steam.dim, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            when {
                trailing != null -> trailing()
                onClick != null && chevron -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Steam.text, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** Steam tarzı anahtar: mavi iz, beyaz yuvarlak; kaydırarak animasyon */
@Composable
fun SteamSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 22.dp else 2.dp, label = "sw")
    val bg by animateColorAsState(if (on) Steam.blue else Color(0xFF4A4F59), label = "swc")
    Box(Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable { onChange(!on) }) {
        Box(Modifier.offset(x = x, y = 2.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(W))
    }
}

@Composable
fun SteamSwitchRow(title: String, sub: String? = null, on: Boolean, onChange: (Boolean) -> Unit) =
    SteamRow(title, sub, onClick = { onChange(!on) }, trailing = { SteamSwitch(on, onChange) })

/** Seçenek satırı: seçiliyse sağda mavi tik */
@Composable
fun SteamChoiceRow(title: String, sub: String? = null, selected: Boolean, onClick: () -> Unit) =
    SteamRow(title, sub, titleColor = if (selected) W else Steam.text, onClick = onClick, trailing = { if (selected) Icon(Icons.Filled.Check, null, tint = Steam.blue, modifier = Modifier.size(26.dp)) else Spacer(Modifier.size(26.dp)) })

/** Üç eşit istatistik kutusu (Oyun / Arkadaş / Cüzdan gibi) */
@Composable
fun SteamStats(vararg items: Pair<String, String>, onClick: (Int) -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Steam.topBrush).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, (v, l) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Steam.panel).pressScale({ onClick(i) }).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, color = W, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(l, color = Steam.dim, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

/** Tam genişlik parlak mavi düğme ("Add friends" gibi) */
@Composable
fun SteamBigButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, color: Color = Steam.blue, enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(if (enabled) color else color.copy(alpha = .4f)).pressScale(onClick, enabled), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = W, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)) }
        Text(text, color = W, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** Koyu giriş kutusu (Steam arama kutusu #333842), odakta mavi kenar */
@Composable
fun SteamField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, password: Boolean = false, leading: ImageVector? = null) {
    var focus by remember { mutableStateOf(false) }
    BasicTextField(value, onChange, modifier.fillMaxWidth().onFocusChanged { focus = it.isFocused }, singleLine = true, textStyle = TextStyle(color = W, fontSize = 17.sp), cursorBrush = SolidColor(Steam.blue),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF32353C)).border(1.dp, if (focus) Steam.blue else Color.Transparent, RoundedCornerShape(4.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) { Icon(leading, null, tint = Steam.dim, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
                Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = Steam.dim, fontSize = 17.sp, maxLines = 1); inner() }
            }
        })
}


/** Profil başlığı: temalı degrade, kare avatar (renkli çerçeve), ad + "Profili görüntüle" */
@Composable
fun SteamProfileHeader(name: String, avatar: @Composable () -> Unit, sub: String?, button: String?, onBack: (() -> Unit)?, onButton: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF5A1F45), Color(0xFF2B1A2E), Color(0xFF1F2127)))).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 16.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Icon(Icons.AutoMirrored.Filled.ArrowBackIos, t("Geri", "Back"), tint = W, modifier = Modifier.clip(RoundedCornerShape(30.dp)).pressScale(onBack).padding(12.dp).size(22.dp))
            else Spacer(Modifier.width(16.dp))
            Box(Modifier.size(96.dp).border(2.dp, Color(0xFFE58BE8), RoundedCornerShape(2.dp)).padding(2.dp)) { avatar() }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(name, color = W, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) Text(sub, color = Color(0xFFCFC2D0), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (button != null) Text(button, Modifier.padding(top = 8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)).pressScale(onButton).padding(horizontal = 18.dp, vertical = 10.dp), color = W, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
