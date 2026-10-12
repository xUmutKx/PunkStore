package com.punkstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request

/**
 * Steam Market, Workshop and Community read straight from Steam's public pages and JSON.
 * No API key and no browser: the data comes into the app and is shown with the app's own screens.
 */
private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
    http.newCall(Request.Builder().url(url).header("User-Agent", BROWSER_UA).header("Accept-Language", if (I18n.isTr) "tr" else "en").build()).execute().use { r ->
        check(r.isSuccessful) { "Steam: ${r.code}" }
        r.body!!.string()
    }
}

private fun unescapeHtml(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

object SteamMarket {
    class Listing(val name: String, val price: String, val icon: String, val game: String, val count: Int)

    /** Search the Community Market of one game (730 = Counter-Strike 2, 440 = TF2, 570 = Dota 2). */
    suspend fun search(query: String, appId: Int = 730): List<Listing> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val body = fetchText("https://steamcommunity.com/market/search/render/?appid=$appId&norender=1&count=25&start=0&query=$q")
        val arr = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val icon = o["asset_description"]?.jsonObject?.get("icon_url")?.jsonPrimitive?.content.orEmpty()
            Listing(
                name = name,
                price = o["sell_price_text"]?.jsonPrimitive?.content.orEmpty(),
                icon = if (icon.isEmpty()) "" else "https://community.cloudflare.steamstatic.com/economy/image/$icon/96fx96f",
                game = o["app_name"]?.jsonPrimitive?.content.orEmpty(),
                count = o["sell_listings"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }
}

object SteamWorkshop {
    class Item(val id: String, val title: String, val image: String)

    /** The most popular items of one game. The browse page lists the ids, each item page gives its title and picture. */
    suspend fun browse(appId: Int = 440): List<Item> {
        val html = fetchText("https://steamcommunity.com/workshop/browse/?appid=$appId&browsesort=trend&numperpage=12&section=readytouseitems")
        val ids = Regex("filedetails/\\?id=(\\d+)").findAll(html).map { it.groupValues[1] }.distinct().take(12).toList()
        return coroutineScope { ids.map { id -> async { runCatching { detail(id) }.getOrNull() } }.awaitAll().filterNotNull() }
    }

    private suspend fun detail(id: String): Item {
        val h = fetchText("https://steamcommunity.com/sharedfiles/filedetails/?id=$id")
        val title = Regex("<meta property=\"og:title\" content=\"(.*?)\"").find(h)?.groupValues?.get(1)?.let(::unescapeHtml) ?: "#$id"
        val image = Regex("<meta property=\"og:image\" content=\"(.*?)\"").find(h)?.groupValues?.get(1).orEmpty()
        return Item(id, title, image)
    }
}

object SteamCommunity {
    class Profile(val name: String, val avatar: String, val online: String, val memberSince: String, val vacBans: Int, val tradeBan: String, val limited: Boolean, val privacy: String)

    /** Public profile card: name, picture, membership date, bans and account limits. */
    suspend fun profile(steamId: String): Profile {
        val x = fetchText("https://steamcommunity.com/profiles/$steamId/?xml=1")
        fun tag(n: String) = Regex("<$n><!\\[CDATA\\[(.*?)]]></$n>|<$n>(.*?)</$n>", RegexOption.DOT_MATCHES_ALL).find(x)
            ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
        return Profile(
            name = tag("steamID"), avatar = tag("avatarFull"), online = tag("stateMessage"), memberSince = tag("memberSince"),
            vacBans = tag("vacBanned").toIntOrNull() ?: 0, tradeBan = tag("tradeBanState"), limited = tag("isLimitedAccount") == "1", privacy = tag("privacyState"),
        )
    }
}
