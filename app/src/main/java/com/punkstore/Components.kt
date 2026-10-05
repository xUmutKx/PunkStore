@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.punkstore

import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.shadow
import coil.compose.AsyncImage

/** Kalan süre: "45 sn" / "3 dk 10 sn" / "1 sa 5 dk" */
fun etaText(sec: Long): String = when { sec < 60 -> t("$sec sn kaldı", "${sec}s left"); sec < 3600 -> t("${sec / 60} dk ${sec % 60} sn kaldı", "${sec / 60}m ${sec % 60}s left"); else -> t("${sec / 3600} sa ${sec % 3600 / 60} dk kaldı", "${sec / 3600}h ${sec % 3600 / 60}m left") }
fun sizeText(b: Long) = if (b <= 0) "" else if (b > 1_000_000) "%.1f MB".format(b / 1e6) else "%d KB".format(b / 1000)

/** Cihazda kurulu uygulamanın kendi ikonu (katalogda görseli olmayanlar için). */
@Composable
fun rememberLocalIcon(pkg: String): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    val st = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(LocalIcons.map[pkg], pkg) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }?.also { LocalIcons.map[pkg] = it }
    }
    return st.value
}
object LocalIcons { val map = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>() }

@Composable
fun AppIcon(a: AppItem, size: Int, radius: Int = 12) {
    val shape = RoundedCornerShape(radius.dp)
    Box(Modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val local = if (a.icon == null) rememberLocalIcon(a.pkg) else null
        if (a.icon != null) AsyncImage(a.icon, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (local != null) androidx.compose.foundation.Image(local, a.name, Modifier.fillMaxSize())
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

/** Basınca hafifçe küçülüp yaylanarak geri gelen dokunma efekti. */
fun Modifier.pressScale(onClick: () -> Unit, enabled: Boolean = true, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) .94f else 1f, androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 600f), label = "press")
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .combinedClickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
}

/** Steam'in yeşil "Yükle" düğmesi / Material'de dolu düğme. İndirme sürerken düğmenin içi soldan sağa dolar; dokununca duraklat / devam / tekrar dene. */
@Composable
fun ActionButton(s: Store, a: AppItem, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    val task = s.dl[a.pkg]
    val inst = s.isInstalled(a)
    val upd = s.hasUpdate(a)
    val isSteam = a.source == "STEAM"
    val st = task?.state
    val label = when {
        isSteam -> t("Steam'de aç", "View on Steam")
        st == DlState.QUEUED -> t("Sırada…", "Queued…")
        st == DlState.DOWNLOADING -> "%${(task.progress * 100).toInt()}"
        st == DlState.VERIFYING -> t("Doğrulanıyor…", "Verifying…")
        st == DlState.INSTALLING -> t("Kuruluyor…", "Installing…")
        st == DlState.PAUSED -> t("Devam  ", "Resume  ") + "%${(task.progress * 100).toInt()}"
        st == DlState.FAILED -> t("Tekrar dene", "Retry")
        st == DlState.DONE -> "✓ " + t("Kuruldu", "Installed")
        upd -> t("Güncelle", "Update")
        inst -> if (a.categories.any { it.contains("Game", true) }) t("Oyna", "Play") else t("Aç", "Open")
        else -> if (compact) t("Yükle", "Get") else t("Yükle", "Get") + "  ${sizeText(a.apkSize)}"
    }
    val onClick = {
        when {
            isSteam -> Browser.open(a.web)
            st == DlState.DOWNLOADING || st == DlState.QUEUED -> s.dl.pause(a.pkg)
            st == DlState.PAUSED || st == DlState.FAILED -> s.dl.resume(a.pkg)
            st == DlState.VERIFYING || st == DlState.INSTALLING -> Unit
            inst && !upd -> s.open(ctx, a.pkg)
            else -> s.getOrUpdate(a, ctx)
        }
    }
    val prog by androidx.compose.animation.core.animateFloatAsState(when (st) { null, DlState.DONE -> 0f; DlState.VERIFYING, DlState.INSTALLING -> 1f; else -> task.progress }, androidx.compose.animation.core.tween(250), label = "fill")
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "p").animateFloat(.55f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "pa")
    val failed = st == DlState.FAILED
    if (s.design.steam) {
        val brush = when {
            failed -> Brush.verticalGradient(listOf(Color(0xFFA34C25), Color(0xFF7A3418)))
            task != null && st != DlState.DONE -> Brush.verticalGradient(listOf(Color(0xFF2F3B46), Color(0xFF26313B)))
            inst && !upd -> Brush.horizontalGradient(listOf(Steam.panel2, Color(0xFF3D6E8E)))
            else -> Brush.verticalGradient(listOf(Steam.greenA, Steam.greenB))
        }
        val shape = RoundedCornerShape(2.dp)
        val base = when {
            failed -> Color(0xFFC4501F)
            task != null && st != DlState.DONE -> Color(0xFF3B4A58)
            inst && !upd -> Color(0xFF2F6F9F)
            else -> Color(0xFF5BA02B)
        }
        Box(
            modifier.clip(shape).background(if (base == Color(0xFF5BA02B)) Brush.verticalGradient(listOf(Color(0xFF75B022), Color(0xFF588A1B))) else Brush.verticalGradient(listOf(base, base))).drawBehind {
                if (prog > 0f) drawRect(Color(0xFF6BA524), size = androidx.compose.ui.geometry.Size(size.width * prog, size.height),
                    alpha = if (st == DlState.VERIFYING || st == DlState.INSTALLING) pulse.value else 1f)
            }.pressScale(onClick)
                .padding(horizontal = if (compact) 14.dp else 22.dp, vertical = if (compact) 8.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedContent(label, transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.slideInVertically { it / 2 }) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.slideOutVertically { -it / 2 })
            }, contentKey = { it.firstOrNull()?.let { c -> if (c == '%') "%" else it } ?: it }, label = "lbl") { l ->
                Text(l, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp, maxLines = 1, softWrap = false)
            }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick, colors = if (failed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                androidx.compose.animation.AnimatedContent(label, contentKey = { it.firstOrNull()?.let { c -> if (c == '%') "%" else it } ?: it }, label = "lbl") { l -> Text(l, maxLines = 1, softWrap = false) }
            }
            androidx.compose.animation.AnimatedVisibility(task != null && st != DlState.DONE) { WavyProgress(prog, Modifier.padding(top = 4.dp)) }
        }
    }
}

/** İndirme ayrıntı satırı: çubuk + "12,3 / 45,6 MB · 2,1 MB/s · 15 sn kaldı" + duraklat/devam/iptal. */
@Composable
fun DownloadLine(s: Store, pkg: String, modifier: Modifier = Modifier) {
    val task = s.dl[pkg]
    androidx.compose.animation.AnimatedVisibility(task != null, modifier, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
        val tk = task ?: return@AnimatedVisibility
        val prog by androidx.compose.animation.core.animateFloatAsState(if (tk.state == DlState.DONE || tk.state == DlState.INSTALLING || tk.state == DlState.VERIFYING) 1f else tk.progress, androidx.compose.animation.core.tween(250), label = "dlp")
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            DlBar(prog, color = if (tk.state == DlState.FAILED) Color(0xFFA34C25) else if (tk.state == DlState.PAUSED) Steam.dim else Steam.btn)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dlStatus(tk), Modifier.weight(1f), color = if (tk.state == DlState.FAILED) Color(0xFFE07B53) else Steam.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (tk.state) {
                    DlState.DOWNLOADING, DlState.QUEUED -> SmallIconBtn(Icons.Filled.Pause, t("Duraklat", "Pause")) { s.dl.pause(pkg) }
                    DlState.PAUSED, DlState.FAILED -> SmallIconBtn(if (tk.state == DlState.FAILED) Icons.Filled.Refresh else Icons.Filled.PlayArrow, t("Devam", "Resume")) { s.dl.resume(pkg) }
                    else -> {}
                }
                if (tk.state != DlState.INSTALLING && tk.state != DlState.DONE && tk.state != DlState.VERIFYING) SmallIconBtn(Icons.Filled.Close, t("İptal", "Cancel")) { s.dl.cancel(pkg) }
            }
        }
    }
}

@Composable
fun SmallIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) =
    Icon(icon, desc, tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(30.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Color(0x33FFFFFF)).pressScale(onClick).padding(5.dp))

fun dlStatus(tk: DlTask): String = when (tk.state) {
    DlState.QUEUED -> t("Sırada bekliyor", "Waiting in queue")
    DlState.DOWNLOADING -> buildString {
        append(sizeText(tk.bytes).ifBlank { "0 KB" }); if (tk.total > 0) append(" / ${sizeText(tk.total)}")
        if (tk.speed > 0) append("  ·  ${sizeText(tk.speed)}/s"); if (tk.eta >= 0) append("  ·  ${etaText(tk.eta)}")
        if (tk.attempt > 0) append("  ·  " + t("yeniden deneniyor (${tk.attempt}/4)", "retrying (${tk.attempt}/4)"))
    }
    DlState.PAUSED -> t("Duraklatıldı", "Paused") + "  ·  ${sizeText(tk.bytes).ifBlank { "0 KB" }}" + (if (tk.total > 0) " / ${sizeText(tk.total)}" else "")
    DlState.VERIFYING -> t("SHA-256 doğrulanıyor…", "Verifying SHA-256…")
    DlState.INSTALLING -> t("Kuruluyor…", "Installing…")
    DlState.DONE -> "✓ " + t("Kuruldu", "Installed")
    DlState.FAILED -> tk.error ?: t("Hata", "Error")
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
            .border(1.dp, if (st) Steam.edgeLo else Color.Transparent, RoundedCornerShape(if (st) Steam.corner.dp else 16.dp)).steamBevel().appPress(s, a, onOpen).padding(10.dp),
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
fun BottomBar(design: Design, tab: Tab, updates: Int, floating: Boolean = false, onTab: (Tab) -> Unit) {
    val icons = mapOf(Tab.DISCOVER to (Icons.Filled.Explore to Icons.Outlined.Explore), Tab.SEARCH to (Icons.Filled.Search to Icons.Outlined.Search), Tab.STORE to (Icons.Filled.Storefront to Icons.Outlined.Storefront), Tab.SEARCH to (Icons.Filled.Search to Icons.Outlined.Search),
        Tab.LIBRARY to (Icons.Filled.VideogameAsset to Icons.Outlined.VideogameAsset), Tab.UPDATES to (Icons.Filled.Download to Icons.Outlined.Download), Tab.PROFILE to (Icons.Filled.Person to Icons.Outlined.Person))
    if (design.steam) {
        // Steam'deki gibi çubuk biraz yukarıda durur; altta orantılı bir boşluk kalır
        val dockMod = if (floating) Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).shadow(10.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Steam.topBrush).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                     else Modifier.fillMaxWidth().background(Steam.topBrush).navigationBarsPadding()
        Column(dockMod) {
        Row(Modifier.fillMaxWidth().height(54.dp)) {
            Tab.values().filter { it.bottom }.forEach { t ->
                val sel = t == tab
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onTab(t) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.width(34.dp).height(2.dp).background(if (sel) Steam.btn else Color.Transparent))
                    Spacer(Modifier.height(4.dp))
                    BadgedBox({ if (t == Tab.UPDATES && updates > 0) Badge { Text("$updates") } }) { Icon(icons[t]!!.first, t.label, tint = if (sel) Steam.btn else Color(0xFFDDDDDD), modifier = Modifier.size(24.dp)) }
                    Text(t.label, color = if (sel) Steam.btn else Color(0xFFAAAAAA), fontSize = 9.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
        if (!floating) Spacer(Modifier.height(10.dp))
        }
    } else {
        NavigationBar {
            Tab.values().filter { it.bottom }.forEach { t ->
                NavigationBarItem(selected = t == tab, onClick = { onTab(t) }, label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
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

/** Alt çubuğun hemen üstünde, her ekranda görünen genel indirme çubuğu. */
@Composable
fun DownloadDock(s: Store, onOpen: () -> Unit) {
    val tasks = s.dl.tasks.values.filter { it.state != DlState.DONE }
    androidx.compose.animation.AnimatedVisibility(tasks.isNotEmpty(), enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
        val tk = tasks.firstOrNull { it.state == DlState.DOWNLOADING } ?: tasks.firstOrNull() ?: return@AnimatedVisibility
        val prog by androidx.compose.animation.core.animateFloatAsState(if (tk.state == DlState.INSTALLING || tk.state == DlState.VERIFYING) 1f else tk.progress, androidx.compose.animation.core.tween(250), label = "dock")
        val mat = Steam.material
        val bg = if (mat) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xE61B2838)
        val fg = if (mat) MaterialTheme.colorScheme.onSurface else Color.White
        val dim = if (mat) MaterialTheme.colorScheme.onSurfaceVariant else Steam.dim
        Column(Modifier.fillMaxWidth().background(bg).clickable(onClick = onOpen)) {
            DlBar(prog, color = if (tk.state == DlState.FAILED) Color(0xFFA34C25) else if (mat) MaterialTheme.colorScheme.primary else Steam.btn, track = if (mat) MaterialTheme.colorScheme.surfaceVariant else Color(0x33FFFFFF))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tk.name + if (tasks.size > 1) "  +${tasks.size - 1}" else "", Modifier.weight(1f), color = fg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dlStatus(tk), color = dim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
