package com.punkstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request

/** Steam Mağaza genel API'si (anahtar gerektirmez): öne çıkanlar, arama, ayrıntı, değerlendirmeler. */
object SteamStoreApi {
    private val lang get() = if (I18n.isTr) "turkish" else "english"
    /** Yaş doğrulaması / olgun içerik (VR dahil) için giriş yapmadan da tam veri gelsin. */
    private const val AGE_COOKIE = "birthtime=631152001; lastagecheckage=1-January-1990; wants_mature_content=1; mature_content=1"
    private fun get(url: String): JsonObject = http.newCall(Request.Builder().url(url).header("Cookie", AGE_COOKIE).build()).execute().use { r ->
        check(r.isSuccessful) { "Steam: ${r.code}" }; json.parseToJsonElement(r.body!!.string()).jsonObject
    }

    private fun money(cur: String, cents: Int?): String = if (cents == null) "" else "%.2f %s".format(cents / 100.0, cur)

    private fun fromFeatured(o: JsonObject, upcoming: Boolean): AppItem? {
        val id = o["id"]?.jsonPrimitive?.long ?: return null
        val cur = o["currency"]?.jsonPrimitive?.content ?: "USD"
        val fin = o["final_price"]?.jsonPrimitive?.intOrNull ?: 0
        val orig = o["original_price"]?.jsonPrimitive?.intOrNull
        val disc = o["discount_percent"]?.jsonPrimitive?.intOrNull ?: 0
        return AppItem(
            pkg = "steam:$id", name = o["name"]?.jsonPrimitive?.content ?: return null, source = "STEAM",
            banner = o["large_capsule_image"]?.jsonPrimitive?.content.orEmpty(),
            icon = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/library_600x900.jpg",
            price = if (fin > 0) money(cur, fin) else if (upcoming) t("Yakında", "Coming soon") else "", discount = disc,
            origPrice = if (disc > 0) money(cur, orig) else "", web = "https://store.steampowered.com/app/$id",
            categories = listOf("Steam"),
        )
    }

    suspend fun featured(): Map<String, List<AppItem>> = withContext(Dispatchers.IO) {
        val d = get("https://store.steampowered.com/api/featuredcategories?cc=${if (I18n.isTr) "tr" else "us"}&l=$lang")
        fun list(k: String, up: Boolean = false) = d[k]?.jsonObject?.get("items")?.jsonArray.orEmpty().mapNotNull { fromFeatured(it.jsonObject, up) }.distinctBy { it.pkg }
        mapOf("specials" to list("specials"), "top" to list("top_sellers"), "new" to list("new_releases"), "soon" to list("coming_soon", true))
    }

    suspend fun search(q: String): List<AppItem> = withContext(Dispatchers.IO) {
        val d = get("https://store.steampowered.com/api/storesearch/?term=${java.net.URLEncoder.encode(q, "UTF-8")}&l=$lang&cc=${if (I18n.isTr) "TR" else "US"}")
        d["items"]?.jsonArray.orEmpty().map { it.jsonObject }.mapNotNull { o ->
            val id = o["id"]?.jsonPrimitive?.long ?: return@mapNotNull null
            val pr = o["price"]?.jsonObject
            val fin = pr?.get("final")?.jsonPrimitive?.intOrNull; val ini = pr?.get("initial")?.jsonPrimitive?.intOrNull ?: fin
            val disc = if (fin != null && ini != null && ini > fin) 100 - fin * 100 / ini else 0
            AppItem(pkg = "steam:$id", name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null, source = "STEAM",
                banner = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/header.jpg",
                icon = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/library_600x900.jpg",
                price = if ((fin ?: 0) > 0) money(pr?.get("currency")?.jsonPrimitive?.content ?: "USD", fin) else "", discount = disc,
                origPrice = if (disc > 0) money(pr?.get("currency")?.jsonPrimitive?.content ?: "USD", ini) else "", web = "https://store.steampowered.com/app/$id", categories = listOf("Steam"))
        }
    }

    /** Just the game's name (for lists that only know the app id). */
    suspend fun name(id: Long): String? = withContext(Dispatchers.IO) {
        get("https://store.steampowered.com/api/appdetails?appids=$id&filters=basic&l=english")["$id"]?.jsonObject?.get("data")?.jsonObject?.get("name")?.jsonPrimitive?.content
    }

    /** Açıklama, geliştirici, türler, ekran görüntüleri ve gerçek kullanıcı değerlendirme özeti. */
    suspend fun details(a: AppItem): AppItem = withContext(Dispatchers.IO) {
        val id = a.pkg.removePrefix("steam:")
        val d = get("https://store.steampowered.com/api/appdetails?appids=$id&cc=${if (I18n.isTr) "tr" else "us"}&l=$lang")[id]?.jsonObject?.get("data")?.jsonObject ?: return@withContext spyOnly(a, id)
        val genres = d["genres"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["description"]?.jsonPrimitive?.content }
        val cats = d["categories"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["description"]?.jsonPrimitive?.content }.take(6)
        val shots = d["screenshots"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["path_full"]?.jsonPrimitive?.content }.take(10)
        val review = runCatching {
            val q = get("https://store.steampowered.com/appreviews/$id?json=1&language=all&purchase_type=all&num_per_page=0&l=$lang")["query_summary"]!!.jsonObject
            val pos = q["total_positive"]!!.jsonPrimitive.int; val neg = q["total_negative"]!!.jsonPrimitive.int
            if (pos + neg == 0) "" else String.format("%s (%d%% / %,d)", q["review_score_desc"]?.jsonPrimitive?.content.orEmpty(), pos * 100 / (pos + neg), pos + neg)
        }.getOrDefault("")
        val pct = Regex("(\\d+)%").find(review)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        fun strip(h: String?) = h.orEmpty().replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
        val players = runCatching { get("https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=$id")["response"]!!.jsonObject["player_count"]!!.jsonPrimitive.int.toString() }.getOrDefault("")
        val plat = d["platforms"]?.jsonObject?.let { p -> listOf("windows" to "Windows", "mac" to "macOS", "linux" to "Linux").filter { p[it.first]?.jsonPrimitive?.booleanOrNull == true }.joinToString(" · ") { it.second } }.orEmpty()
        val spy = runCatching { get("https://steamspy.com/api.php?request=appdetails&appid=$id") }.getOrNull()
        val extra = buildMap {
            spy?.let { sp ->
                sp["owners"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { put("owners", it) }
                sp["average_forever"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let { put("avgplay", (it / 60).toString()) }
                val usd = (sp["initialprice"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L)
                val rng = Regex("([\\d,]+) \\.\\. ([\\d,]+)").find(sp["owners"]?.jsonPrimitive?.contentOrNull.orEmpty())
                if (rng != null && usd > 0) { val lo = rng.groupValues[1].replace(",", "").toLong(); val hi = rng.groupValues[2].replace(",", "").toLong(); put("revenue", String.format("$%,d – $%,d", lo * usd / 100, hi * usd / 100)) }
            }
            d["metacritic"]?.jsonObject?.get("score")?.jsonPrimitive?.content?.let { put("metacritic", it) }
            d["metacritic"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull?.let { put("metaurl", it) }
            d["achievements"]?.jsonObject?.get("total")?.jsonPrimitive?.content?.let { put("achievements", it) }
            d["recommendations"]?.jsonObject?.get("total")?.jsonPrimitive?.content?.let { put("recs", it) }
            put("platforms", plat); put("players", players)
            strip(d["pc_requirements"]?.jsonObject?.get("minimum")?.jsonPrimitive?.contentOrNull).takeIf { it.isNotBlank() }?.let { put("req", it) }
            strip(d["supported_languages"]?.jsonPrimitive?.contentOrNull).takeIf { it.isNotBlank() }?.let { put("langs", it) }
            d["website"]?.jsonPrimitive?.contentOrNull?.let { put("website", it) }
            d["required_age"]?.jsonPrimitive?.contentOrNull?.let { put("age", it) }
            d["movies"]?.jsonArray?.firstOrNull()?.jsonObject?.let { mv -> (mv["mp4"]?.jsonObject?.get("max") ?: mv["webm"]?.jsonObject?.get("max"))?.jsonPrimitive?.contentOrNull?.let { put("movie", it) } }
        }
        a.copy(
            extra = extra,
            description = d["about_the_game"]?.jsonPrimitive?.content?.ifBlank { null } ?: d["short_description"]?.jsonPrimitive?.content.orEmpty(),
            summary = d["short_description"]?.jsonPrimitive?.content.orEmpty(),
            developer = d["developers"]?.jsonArray.orEmpty().joinToString { it.jsonPrimitive.content },
            license = d["publishers"]?.jsonArray.orEmpty().joinToString { it.jsonPrimitive.content },
            categories = (genres + "Steam").distinct(), tags = (genres + cats).distinct(), screenshots = shots,
            banner = d["header_image"]?.jsonPrimitive?.content ?: a.banner, review = review, rating = pct / 20f,
            versionName = d["release_date"]?.jsonObject?.get("date")?.jsonPrimitive?.content.orEmpty(),
        )
    }

    /** appdetails bir oyun için veri vermezse (bölge/yaş kısıtı) SteamSpy'dan temel bilgileri al. */
    private fun spyOnly(a: AppItem, id: String): AppItem {
        val sp = runCatching { get("https://steamspy.com/api.php?request=appdetails&appid=$id") }.getOrNull() ?: return a
        val tags = sp["tags"]?.let { (it as? JsonObject)?.keys?.take(10) }.orEmpty().toList()
        val pos = sp["positive"]?.jsonPrimitive?.intOrNull ?: 0; val neg = sp["negative"]?.jsonPrimitive?.intOrNull ?: 0
        val extra = buildMap {
            sp["owners"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { put("owners", it) }
            sp["average_forever"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let { put("avgplay", (it / 60).toString()) }
        }
        return a.copy(extra = extra, developer = sp["developer"]?.jsonPrimitive?.contentOrNull.orEmpty(), license = sp["publisher"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            tags = tags, categories = (tags + "Steam").distinct(), rating = if (pos + neg > 0) pos * 5f / (pos + neg) else 0f,
            review = if (pos + neg > 0) String.format("(%d%% / %,d)", pos * 100 / (pos + neg), pos + neg) else "")
    }

    /** Kullanıcı incelemeleri (en faydalılar): Türkçe, yoksa tüm diller. */
    suspend fun reviews(a: AppItem): List<UserReview> = withContext(Dispatchers.IO) {
        val id = a.pkg.removePrefix("steam:")
        fun fetch(lang: String) = get("https://store.steampowered.com/appreviews/$id?json=1&filter=all&language=$lang&num_per_page=12&purchase_type=all")["reviews"]?.jsonArray.orEmpty().map { it.jsonObject }.mapNotNull { o ->
            val txt = o["review"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(); if (txt.isBlank()) return@mapNotNull null
            UserReview(o["author"]?.jsonObject?.get("steamid")?.jsonPrimitive?.contentOrNull?.takeLast(4)?.let { "Steam #$it" } ?: "Steam", txt.take(600), o["voted_up"]?.jsonPrimitive?.booleanOrNull, 0,
                (o["author"]?.jsonObject?.get("playtime_forever")?.jsonPrimitive?.intOrNull ?: 0) / 60, o["votes_up"]?.jsonPrimitive?.intOrNull ?: 0, (o["timestamp_created"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000)
        }
        fetch(if (I18n.isTr) "turkish" else "english").ifEmpty { fetch("all") }
    }
}
