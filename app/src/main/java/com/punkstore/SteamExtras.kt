package com.punkstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.text.DateFormat
import java.util.Date

/** Extra Steam store-page data (news, DLC) that the main details call does not carry. */
object SteamExtraApi {
    class News(val title: String, val label: String, val url: String, val text: String, val time: Long)
    class Dlc(val id: String, val name: String, val img: String, val price: String, val discount: Int)

    private val lang get() = if (I18n.isTr) "turkish" else "english"
    private fun get(url: String): JsonObject = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
        check(r.isSuccessful) { "Steam: ${r.code}" }; json.parseToJsonElement(r.body!!.string()).jsonObject
    }

    suspend fun news(id: String): List<News> = withContext(Dispatchers.IO) {
        get("https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=$id&count=6&maxlength=260&l=$lang")["appnews"]?.jsonObject?.get("newsitems")?.jsonArray.orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val txt = o["contents"]?.jsonPrimitive?.contentOrNull.orEmpty().replace(Regex("\\[/?[a-zA-Z0-9*]+[^\\]]*]"), " ").replace(Regex("\\{STEAM_CLAN_IMAGE\\}\\S+"), "").replace(Regex("\\s+"), " ").trim()
            News(o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null, o["feedlabel"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["url"]?.jsonPrimitive?.contentOrNull.orEmpty(), txt, (o["date"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000)
        }
    }

    /** DLC of the app (names, art and prices), looked up one by one because Steam allows a single id with full data. */
    suspend fun dlc(ids: List<String>): List<Dlc> = withContext(Dispatchers.IO) {
        ids.take(8).mapNotNull { id ->
            runCatching {
                val d = get("https://store.steampowered.com/api/appdetails?appids=$id&cc=${if (I18n.isTr) "tr" else "us"}&l=$lang")[id]?.jsonObject?.get("data")?.jsonObject ?: return@runCatching null
                val p = d["price_overview"]?.jsonObject
                Dlc(id, d["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null, d["header_image"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    p?.get("final_formatted")?.jsonPrimitive?.contentOrNull ?: if (d["is_free"]?.jsonPrimitive?.booleanOrNull == true) t("Ücretsiz", "Free") else "",
                    p?.get("discount_percent")?.jsonPrimitive?.intOrNull ?: 0)
            }.getOrNull()
        }
    }

    /** Store feature flags (Steam's own category list) and DLC ids, read from the same appdetails call. */
    suspend fun featuresAndDlc(id: String): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val d = get("https://store.steampowered.com/api/appdetails?appids=$id&cc=us&l=$lang")[id]?.jsonObject?.get("data")?.jsonObject ?: return@withContext emptyList<String>() to emptyList()
        d["categories"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["description"]?.jsonPrimitive?.contentOrNull } to
            d["dlc"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
    }
}

private val W = Color.White
private fun featureIcon(f: String): String = when {
    f.contains("Single", true) -> "👤"; f.contains("Co-op", true) || f.contains("Multi", true) || f.contains("PvP", true) -> "👥"
    f.contains("Achievements", true) -> "🏆"; f.contains("Trading Cards", true) -> "🃏"; f.contains("Cloud", true) -> "☁"
    f.contains("controller", true) -> "🎮"; f.contains("Remote Play", true) -> "📡"; f.contains("Family", true) -> "👪"
    f.contains("Workshop", true) -> "🔧"; f.contains("Purchases", true) -> "🛒"; f.contains("Captions", true) -> "💬"
    f.contains("VR", true) -> "🥽"; f.contains("Stats", true) || f.contains("Leaderboards", true) -> "📊"; else -> "✔"
}

@Composable
private fun Head(text: String) = Text(text, Modifier.padding(top = 18.dp, bottom = 8.dp), color = W, fontSize = 16.sp)

/** Steam store-page sections: features, ratings graph, DLC, news, art & banners, points shop, similar games. */
@Composable
fun SteamExtras(s: Store, a: AppItem, onOpen: (String) -> Unit, web: (String) -> Unit) {
    val id = a.pkg.removePrefix("steam:")
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var feats by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    var dlcIds by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    var dlc by remember(id) { mutableStateOf<List<SteamExtraApi.Dlc>>(emptyList()) }
    var news by remember(id) { mutableStateOf<List<SteamExtraApi.News>>(emptyList()) }
    var similar by remember(id) { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(id) {
        runCatching { SteamExtraApi.featuresAndDlc(id) }.onSuccess { (f, d) -> feats = f; dlcIds = d }
    }
    LaunchedEffect(dlcIds) { if (dlcIds.isNotEmpty()) dlc = runCatching { SteamExtraApi.dlc(dlcIds) }.getOrDefault(emptyList()) }
    LaunchedEffect(id) { news = runCatching { SteamExtraApi.news(id) }.getOrDefault(emptyList()) }
    LaunchedEffect(id) {
        val term = (a.tags + a.categories).firstOrNull { it != "Steam" && it.isNotBlank() } ?: a.developer.ifBlank { return@LaunchedEffect }
        similar = s.searchSteamStore(term).filter { it.pkg != a.pkg }.take(10)
    }

    // ---- FEATURES
    if (feats.isNotEmpty()) {
        Head(t("ÖZELLİKLER", "FEATURES"))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            feats.forEach { f -> Text(featureIcon(f) + "  " + f, Modifier.clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(horizontal = 12.dp, vertical = 8.dp), color = Steam.text, fontSize = 14.sp) }
        }
    }

    // ---- RATINGS GRAPH
    val m = Regex("\\((\\d+)% / ([\\d,]+)\\)").find(a.review)
    val pct = m?.groupValues?.get(1)?.toIntOrNull()
    val total = m?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull()
    if (pct != null && total != null && total > 0) {
        Head(t("PUANLAR", "RATINGS"))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.review.substringBefore(" (").ifBlank { "$pct%" }, Modifier.weight(1f), color = W, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format("%,d", total) + t(" inceleme", " reviews"), color = Steam.dim, fontSize = 13.sp)
            }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp))) {
                Box(Modifier.weight(pct.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Color(0xFF66C0F4)))
                Box(Modifier.weight((100 - pct).coerceAtLeast(1).toFloat()).fillMaxHeight().background(Color(0xFFA34C25)))
            }
            Row(Modifier.padding(top = 6.dp)) {
                Text("👍 $pct%  (" + String.format("%,d", total * pct / 100) + ")", Modifier.weight(1f), color = Color(0xFF66C0F4), fontSize = 12.sp)
                Text("👎 ${100 - pct}%  (" + String.format("%,d", total - total * pct / 100) + ")", color = Color(0xFFD9774B), fontSize = 12.sp)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(a.extra["metacritic"]?.let { "Metacritic" to it }, a.extra["achievements"]?.let { t("Başarım", "Achievements") to it }, a.extra["recs"]?.let { t("Öneri", "Recommended") to it }, a.extra["players"]?.takeIf { it.isNotBlank() }?.let { t("Şu an", "Playing now") to it }).forEach { (k, v) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(3.dp)).background(Steam.wish).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v.toLongOrNull()?.let { String.format("%,d", it) } ?: v, color = W, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(k, color = Steam.dim, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    // ---- DLC
    if (dlc.isNotEmpty()) {
        Head(t("İNDİRİLEBİLİR İÇERİK (DLC)", "DOWNLOADABLE CONTENT"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(dlc, key = { it.id }) { d ->
                Column(Modifier.width(200.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).clickable { web("https://store.steampowered.com/app/${d.id}") }) {
                    AsyncImage(d.img, null, Modifier.fillMaxWidth().height(94.dp).background(Color.Black), contentScale = ContentScale.Crop)
                    Text(d.name, Modifier.padding(8.dp, 6.dp, 8.dp, 0.dp), color = W, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (d.discount > 0) Text("-${d.discount}%", Modifier.background(Color(0xFF4C6B22)).padding(horizontal = 5.dp, vertical = 2.dp), color = Color(0xFFBEEE11), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(d.price, Modifier.padding(start = 6.dp), color = Steam.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // ---- NEWS
    if (news.isNotEmpty()) {
        Head(t("HABERLER VE GÜNCELLEMELER", "NEWS & UPDATES"))
        news.forEach { n ->
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).clickable { web(n.url) }.padding(12.dp)) {
                Text(n.title, color = W, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(n.label, if (n.time > 0) df.format(Date(n.time)) else "").filter { it.isNotBlank() }.joinToString(" · "), color = Steam.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                if (n.text.isNotBlank()) Text(n.text, Modifier.padding(top = 6.dp), color = Steam.text, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(t("Tüm haberler →", "All news →"), Modifier.clickable { web("https://store.steampowered.com/news/app/$id") }.padding(vertical = 4.dp), color = Steam.link, fontSize = 14.sp)
    }

    // ---- ART & BANNERS
    Head(t("GÖRSELLER VE AFİŞLER", "ART & BANNERS"))
    val cdn = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id"
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("header.jpg" to 215, "capsule_616x353.jpg" to 215, "library_hero.jpg" to 300, "library_600x900.jpg" to 110, "logo.png" to 200, "page_bg_generated_v6b.jpg" to 215)) { (f, w) ->
            AsyncImage("$cdn/$f", f.substringBefore('.'), Modifier.height(120.dp).width(w.dp).clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).clickable { web("$cdn/$f") }, contentScale = if (f == "logo.png") ContentScale.Fit else ContentScale.Crop)
        }
    }

    // ---- POINTS SHOP
    Head(t("PUAN MAĞAZASI", "POINTS SHOP"))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Steam.corner.dp)).background(Steam.box).clickable { web("https://store.steampowered.com/points/shop/app/$id") }.padding(14.dp)) {
        Text("🎖  " + t("Profil arka planları, çerçeveler, emojiler ve rozetler", "Profile backgrounds, frames, emoticons and badges"), color = W, fontSize = 14.sp)
        Text(t("Bu oyunun Steam Puan Mağazası öğelerini aç →", "Open this game's Steam Points Shop items →"), Modifier.padding(top = 6.dp), color = Steam.link, fontSize = 14.sp)
    }

    // ---- SIMILAR
    if (similar.isNotEmpty()) {
        Head(t("BENZER OYUNLAR", "MORE LIKE THIS"))
        AppCarousel(s, similar, onOpen)
    }
}
