package com.punkstore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

fun sizeText(b: Long) = if (b <= 0) "" else if (b > 1_000_000) "%.1f MB".format(b / 1e6) else "%d KB".format(b / 1000)

@Composable
fun AppIcon(a: AppItem, size: Int, radius: Int = 12) {
    val shape = RoundedCornerShape(radius.dp)
    Box(Modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (a.icon != null) AsyncImage(a.icon, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(a.name.take(1).uppercase(), fontSize = (size / 2).sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/** Kapak görseli (ekran görüntüsü) + üstünde uygulama ikonu; görsel yoksa ikonlu renkli degrade. */
@Composable
fun Capsule(a: AppItem, modifier: Modifier = Modifier, iconSize: Int = 40, radius: Int = 3) {
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(Brush.linearGradient(listOf(Steam.panel2, Steam.card)))) {
        a.cover?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .9f) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0E141B)))))
        if (a.cover == null) Text(a.name.take(2).uppercase(), Modifier.align(Alignment.Center), color = Color(0x33FFFFFF), fontSize = 26.sp, fontWeight = FontWeight.Black)
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) { AppIcon(a, iconSize, 6) }
    }
}

/** Steam'in yeşil "Yükle" düğmesi / Material'de dolu düğme. */
@Composable
fun ActionButton(s: Store, a: AppItem, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    val p = s.busy[a.pkg]
    val inst = s.isInstalled(a)
    val upd = s.hasUpdate(a)
    val label = when {
        a.source == "STEAM" -> t("Steam'de aç", "View on Steam")
        p != null && p >= 0 -> "%${(p * 100).toInt()}"
        p != null -> t("Kuruluyor…", "Installing…")
        upd -> t("Güncelle", "Update")
        inst -> t("Aç", "Open")
        else -> if (compact) t("Yükle", "Get") else t("Yükle", "Get") + "  ${sizeText(a.apkSize)}"
    }
    val isSteam = a.source == "STEAM"
    val onClick = { if (isSteam) ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.web)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) else if (inst && !upd) s.open(ctx, a.pkg) else s.getOrUpdate(a, ctx) }
    if (s.design.steam) {
        val shape = RoundedCornerShape(Steam.corner.dp)
        val brush = if (inst && !upd) Brush.horizontalGradient(listOf(Steam.panel2, Color(0xFF3D6E8E)))
                    else Brush.verticalGradient(listOf(Steam.greenA, Steam.greenB))
        Box(
            modifier.clip(shape).background(brush).gloss().border(1.dp, Color(0x66000000), shape).steamBevel().clickable(enabled = p == null, onClick = onClick)
                .padding(horizontal = if (compact) 14.dp else 22.dp, vertical = if (compact) 8.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp, maxLines = 1, softWrap = false)
            if (p != null) DlBar(if (p < 0) 1f else p, Modifier.align(Alignment.BottomStart), Color.White, Color(0x44FFFFFF))
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Button(onClick, enabled = p == null) { Text(label, maxLines = 1, softWrap = false) }; if (p != null) WavyProgress(if (p < 0) 1f else p, Modifier.padding(top = 4.dp)) }
    }
}

@Composable
fun Tag(text: String, s: Store) {
    val st = s.design.steam
    Text(
        text, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        color = if (st) Steam.blue else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.clip(RoundedCornerShape(if (st) 2.dp else 8.dp))
            .background(if (st) Color(0x3367C1F5) else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Liste satırı: Steam'de kapsül gibi, Material'de kart gibi. */
@Composable
fun AppRow(s: Store, a: AppItem, onOpen: (String) -> Unit) {
    val st = s.design.steam
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(if (st) 3.dp else 16.dp))
            .background(if (st) Brush.verticalGradient(Steam.panelGrad) else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .border(1.dp, if (st) Steam.edgeLo else Color.Transparent, RoundedCornerShape(if (st) Steam.corner.dp else 16.dp)).steamBevel().clickable { onOpen(a.pkg) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Capsule(a, Modifier.width(118.dp).height(66.dp), 30, if (st) 3 else 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).widthIn(min = 0.dp)) {
            Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (st) Color.White else MaterialTheme.colorScheme.onSurface)
            Text(a.summary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.categories.isNotEmpty()) Row(Modifier.padding(top = 4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { a.categories.take(1).forEach { Tag(I18n.category(it), s) } }
        }
        Spacer(Modifier.width(8.dp))
        ActionButton(s, a, Modifier.widthIn(min = 84.dp), compact = true)
    }
}

@Composable
fun SectionTitle(text: String, s: Store, modifier: Modifier = Modifier) {
    if (s.design.steam)
        Text(text.uppercase(), modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Steam.blue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    else Text(text, modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
fun BottomBar(design: Design, tab: Tab, updates: Int, onTab: (Tab) -> Unit) {
    val icons = mapOf(Tab.DISCOVER to (Icons.Filled.Explore to Icons.Outlined.Explore), Tab.SEARCH to (Icons.Filled.Search to Icons.Outlined.Search), Tab.STORE to (Icons.Filled.Storefront to Icons.Outlined.Storefront), Tab.SEARCH to (Icons.Filled.Search to Icons.Outlined.Search),
        Tab.LIBRARY to (Icons.Filled.VideogameAsset to Icons.Outlined.VideogameAsset), Tab.UPDATES to (Icons.Filled.Notifications to Icons.Outlined.Notifications), Tab.PROFILE to (Icons.Filled.Person to Icons.Outlined.Person))
    if (design.steam) {
        Row(Modifier.fillMaxWidth().background(Steam.topBrush).navigationBarsPadding().height(58.dp)) {
            Tab.values().filter { it.bottom }.forEach { t ->
                val sel = t == tab
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onTab(t) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.width(36.dp).height(2.dp).background(if (sel) Steam.btn else Color.Transparent))
                    Spacer(Modifier.height(6.dp))
                    BadgedBox({ if (t == Tab.UPDATES && updates > 0) Badge { Text("$updates") } }) { Icon(icons[t]!!.first, t.label, tint = if (sel) Steam.btn else Color(0xFFDDDDDD), modifier = Modifier.size(28.dp)) }
                }
            }
        }
    } else {
        NavigationBar {
            Tab.values().filter { it.bottom || it == Tab.SEARCH }.forEach { t ->
                NavigationBarItem(selected = t == tab, onClick = { onTab(t) }, label = { Text(t.label) },
                    icon = { Icon(if (t == tab) icons[t]!!.first else icons[t]!!.second, t.label) })
            }
        }
    }
}

@Composable
fun SteamTopBar(s: Store, onWishlist: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Steam.panel, Steam.bg))).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Settings, null, tint = Steam.blue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text("PUNK STORE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        IconButton(onWishlist) { BadgedBox({ if (s.wishlist.isNotEmpty()) Badge { Text("${s.wishlist.size}") } }) { Icon(Icons.Filled.Favorite, t("İstek listesi", "Wishlist"), tint = Steam.dim) } }
        if (s.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Steam.blue, strokeWidth = 2.dp)
        else IconButton({ s.refresh() }) { Icon(Icons.Filled.Refresh, t("Yenile", "Refresh"), tint = Steam.dim) }
    }
}

/** 2006 Steam: aydınlık üst-sol (136,145,128) / karanlık alt-sağ (40,46,34) kabartma kenar; diğer temalarda etkisiz. */
fun Modifier.steamBevel(): Modifier = if (Steam.bevel) this.border(1.dp, Brush.linearGradient(listOf(Color(0xFF889180), Color(0xFF282E22))), RoundedCornerShape(0.dp)) else this

/** Temaya göre degrade panel + kenar (2013: parlak karbon, 2006: kabartma, modern: yumuşak degrade). */
fun Modifier.skinBg(radius: androidx.compose.ui.unit.Dp = Steam.corner.dp): Modifier {
    val sh = RoundedCornerShape(radius)
    return this.clip(sh).background(Brush.verticalGradient(Steam.panelGrad)).border(1.dp, Brush.verticalGradient(listOf(Steam.edgeHi, Steam.edgeLo)), sh)
}

/** 2013 karbon lif dokusu: ince çapraz çizgiler. */
fun Modifier.carbon(): Modifier = this.drawBehind {
    val step = 7.dp.toPx()
    var x = -size.height
    while (x < size.width) { drawLine(Color(0x0DFFFFFF), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x + size.height, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(Color(0x14000000), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f); y += step / 2 }
}

/** Parlak cam efekti: üst yarıya beyaz→şeffaf. */
fun Modifier.gloss(): Modifier = if (!Steam.glossy) this else this.drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x08FFFFFF)), 0f, size.height * .5f), size = androidx.compose.ui.geometry.Size(size.width, size.height * .5f))
}

/** Dalgalı (Material Expressive tarzı) ilerleme çubuğu: dolu kısım akan bir sinüs dalgası. */
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Steam.btn, track: Color = color.copy(alpha = .25f)) {
    val phase: Float by androidx.compose.animation.core.rememberInfiniteTransition(label = "w").animateFloat(0f, 6.2831855f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "ph")
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val w = size.width; val mid = size.height / 2; val stroke = 3.dp.toPx(); val amp = 3.2.dp.toPx(); val wl = 22.dp.toPx()
        val end = w * progress.coerceIn(0f, 1f)
        drawLine(track, androidx.compose.ui.geometry.Offset(end, mid), androidx.compose.ui.geometry.Offset(w, mid), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        if (end > 1f) {
            val p = androidx.compose.ui.graphics.Path(); var x = 0f
            p.moveTo(0f, mid)
            while (x <= end) { p.lineTo(x, mid + amp * kotlin.math.sin(x / wl * 2 * Math.PI.toFloat() - phase)); x += 2f }
            drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

/** İndirme çubuğu: Material temasında dalgalı, Steam temalarında düz Steam tarzı (mavi dolgu + akan ışık). */
@Composable
fun DlBar(progress: Float, modifier: Modifier = Modifier, color: Color = Steam.btn, track: Color = Color(0x44000000)) {
    if (Steam.material) { WavyProgress(progress, modifier, color, track); return }
    val shine by androidx.compose.animation.core.rememberInfiniteTransition(label = "dl").animateFloat(-0.3f, 1.3f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "sh")
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRect(track)
        val end = size.width * progress.coerceIn(0f, 1f)
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = .85f), color)), size = androidx.compose.ui.geometry.Size(end, size.height))
        val x = end * shine
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent), x - 40f, x + 40f), size = androidx.compose.ui.geometry.Size(end, size.height))
    }
}
