package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val W = Color.White

private class NativePage(val key: String, val icon: ImageVector, val tr: String, val en: String, val introTr: String, val introEn: String)

/** Opened from the profile's Community row: these three show together as tabs. */
private val COMMUNITY_GROUP = listOf("workshop", "community", "market")

private val NATIVE_PAGES = listOf(
    NativePage("points", Icons.Filled.Stars, "Puan dükkanı", "Point Shop",
        "Kozmetik çerçeve önizlemesi. Satın alma ve puan yok.", "Cosmetic frame preview. No purchases or points yet."),
    NativePage("community", Icons.Filled.Groups, "Topluluk", "Community",
        "Steam profilin: üyelik tarihi, yasaklar, hesap durumu.", "Your Steam profile: membership date, bans and account status."),
    NativePage("market", Icons.Filled.Storefront, "Pazar", "Market",
        "Topluluk Pazarı'nda ara, fiyatları gör.", "Search the Community Market and see prices."),
    NativePage("workshop", Icons.Filled.Build, "Atölye", "Workshop",
        "Popüler Atölye öğeleri.", "Popular Workshop items."),
    NativePage("tournaments", Icons.Filled.EmojiEvents, "Play turnuvaları", "Play tournaments",
        "Turnuva sayfası için yer tutucu. Maç verisi yok.", "Placeholder for the tournaments page. No match data yet."),
)

/** Menüdeki web sayfalarının yerel önizlemesi: tek ekran, sekme başına bir sayfa. Veri yok, hepsi örnek. */
@Composable
fun NativePageScreen(start: String, steamId: String, onBack: () -> Unit) {
    var cur by remember { mutableStateOf(start) }
    val page = NATIVE_PAGES.first { it.key == cur }
    Column(Modifier.fillMaxSize().background(Steam.panel).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Geri", "Back"), tint = W) }
            Text(t(page.tr, page.en), color = W, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (cur in COMMUNITY_GROUP) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMUNITY_GROUP.forEach { k -> val p = NATIVE_PAGES.first { it.key == k }
                FilterChip(p.key == cur, { cur = p.key }, { Text(t(p.tr, p.en), maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(p.icon, null, modifier = Modifier.size(18.dp)) })
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(t("ÖNİZLEME", "PREVIEW"), Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.btn).padding(horizontal = 8.dp, vertical = 3.dp),
                color = W, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(t(page.introTr, page.introEn), Modifier.padding(top = 8.dp), color = Steam.dim, fontSize = 14.sp)
        }
        when (cur) {
            "points" -> FrameGrid()
            "community" -> CommunityPane(steamId)
            "market" -> MarketPane()
            "workshop" -> WorkshopPane()
            else -> NativePlaceholder(page.icon, t("Henüz içerik yok", "Nothing here yet"))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Kozmetik çerçeve önizlemesi: tema renklerinden üretilen sweep gradient halkalar. Tıklama yok. */
@Composable
private fun FrameGrid() {
    val frames = listOf(
        listOf(Steam.btn, Steam.greenA), listOf(Steam.blue, Steam.link), listOf(Steam.greenA, Steam.greenB), listOf(Steam.btn, Steam.blue),
        listOf(Steam.dim, Steam.text), listOf(Steam.link, Steam.btn, Steam.blue), listOf(Steam.greenB, Steam.dim), listOf(Steam.text, Steam.btn),
    )
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        frames.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { colors ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(64.dp).border(4.dp, Brush.sweepGradient(colors), CircleShape).padding(4.dp).clip(CircleShape).background(Steam.panel2), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, null, tint = Steam.dim, modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativePlaceholder(icon: ImageVector, title: String) = Column(
    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(icon, null, tint = Steam.dim, modifier = Modifier.size(56.dp))
    Text(title, Modifier.padding(top = 12.dp), color = W, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun PaneCard(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).padding(14.dp),
    content = content,
)

@Composable
internal fun MarketPane(appId: Int = 730) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SteamMarket.Listing>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load(q: String) {
        scope.launch {
            busy = true; error = null
            runCatching { SteamMarket.search(q, appId) }.onSuccess { items = it }.onFailure { error = it.message }
            busy = false
        }
    }
    LaunchedEffect(appId) { load("") }
    PaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text(t("Eşya ara", "Search items"), color = Steam.dim) })
            IconButton({ load(query) }) { Icon(Icons.Filled.Search, t("Ara", "Search"), tint = W) }
        }
        if (busy) Text(t("Yükleniyor…", "Loading…"), color = Steam.dim, modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(t("Pazar açılamadı: ", "Market failed: ") + it, color = Steam.dim, modifier = Modifier.padding(top = 8.dp)) }
    }
    items.forEach { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            coil.compose.AsyncImage(m.icon.ifEmpty { null }, null, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(m.name, color = W, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(m.game + " · " + m.count + " " + t("ilan", "listings"), color = Steam.dim, fontSize = 12.sp)
            }
            Text(m.price, color = Steam.greenA, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun WorkshopPane(appId: Int = 440) {
    var items by remember { mutableStateOf<List<SteamWorkshop.Item>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(appId) {
        items = null; error = null
        runCatching { SteamWorkshop.browse(appId) }.onSuccess { items = it }.onFailure { error = it.message }
    }
    when {
        error != null -> PaneCard { Text(t("Atölye açılamadı: ", "Workshop failed: ") + error, color = Steam.dim) }
        items == null -> PaneCard { Text(t("Yükleniyor…", "Loading…"), color = Steam.dim) }
        items!!.isEmpty() -> PaneCard { Text(t("Öğe bulunamadı.", "No items found."), color = Steam.dim) }
        else -> items!!.chunked(2).forEach { row ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { it ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.card).padding(8.dp)) {
                        coil.compose.AsyncImage(it.image.ifEmpty { null }, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Steam.corner.dp)))
                        Text(it.title, color = W, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CommunityPane(steamId: String) {
    var profile by remember { mutableStateOf<SteamCommunity.Profile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(steamId) {
        if (steamId.isNotBlank()) runCatching { SteamCommunity.profile(steamId) }.onSuccess { profile = it }.onFailure { error = it.message }
    }
    when {
        steamId.isBlank() -> PaneCard { Text(t("Önce Steam hesabını bağla (Hesap menüsü).", "Link your Steam account first (Account menu)."), color = Steam.dim) }
        error != null -> PaneCard { Text(t("Profil açılamadı: ", "Profile failed: ") + error, color = Steam.dim) }
        profile == null -> PaneCard { Text(t("Yükleniyor…", "Loading…"), color = Steam.dim) }
        else -> profile!!.let { p ->
            PaneCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    coil.compose.AsyncImage(p.avatar.ifEmpty { null }, null, Modifier.size(64.dp).clip(CircleShape))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(p.name, color = W, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(p.online, color = Steam.dim, fontSize = 13.sp)
                    }
                }
            }
            PaneCard {
                InfoLine(t("Üyelik tarihi", "Member since"), p.memberSince)
                InfoLine(t("VAC yasağı", "VAC bans"), p.vacBans.toString())
                InfoLine(t("Ticaret durumu", "Trade status"), p.tradeBan)
                InfoLine(t("Sınırlı hesap", "Limited account"), if (p.limited) t("Evet", "Yes") else t("Hayır", "No"))
                InfoLine(t("Gizlilik", "Privacy"), p.privacy)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(label, color = Steam.dim, modifier = Modifier.weight(1f))
    Text(value, color = W, fontWeight = FontWeight.Bold)
}


/** Workshop and Market of one game, opened from its page (the game's own items, not the whole store). */
@Composable
fun GameWorkshopMarket(appId: Int) {
    var open by remember(appId) { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("workshop" to t("Atölye", "Workshop"), "market" to t("Pazar", "Market")).forEach { (k, label) ->
            FilterChip(open == k, { open = if (open == k) null else k }, { Text(label, maxLines = 1, softWrap = false) })
        }
    }
    when (open) {
        "workshop" -> WorkshopPane(appId)
        "market" -> MarketPane(appId)
    }
}
