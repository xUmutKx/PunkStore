package com.punkstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request

/** Steam herkese açık profil (API anahtarsız): profil, sahip olunan oyunlar, oyun başarımları. */
object SteamLink {
    class Profile(val id: String, val name: String, val avatar: String, val level: Int, val games: List<Game>)
    class Game(val appId: Long, val name: String, val minutes: Int) {
        val header get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
        val capsule get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
    }
    class Ach(val api: String, val name: String, val desc: String, val icon: String, val done: Boolean, val time: Long, val percent: Float, val hidden: Boolean)

    private fun getText(url: String): String {
        http.newCall(Request.Builder().url(url).header("Accept-Language", if (I18n.isTr) "tr" else "en").build()).execute().use { r ->
            check(r.isSuccessful) { "Steam: ${r.code}" }
            return r.body!!.string()
        }
    }
    private fun tag(x: String, n: String) = Regex("<$n>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$n>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1)?.trim().orEmpty()
    private fun base(who: String) = who.trim().trimEnd('/').let { w ->
        val last = w.substringAfterLast('/')
        if (last.length == 17 && last.all { it.isDigit() }) "https://steamcommunity.com/profiles/$last" else "https://steamcommunity.com/id/$last"
    }

    /** API anahtarı gerekmez: herkese açık profilin topluluk sayfalarından okunur. Profil ve "Oyun ayrıntıları" herkese açık olmalı. */
    suspend fun load(who: String, lang: String): Profile = withContext(Dispatchers.IO) {
        val b = base(who)
        val xml = getText("$b/?xml=1")
        if (tag(xml, "error").isNotEmpty() || tag(xml, "steamID64").isEmpty()) error(t("Profil bulunamadı (profil ID'si ya da özel URL adı gir)", "Profile not found (enter profile ID or vanity name)"))
        val id = tag(xml, "steamID64")
        val level = runCatching { Regex("friendPlayerLevelNum[^>]*>(\\d+)").find(getText("https://steamcommunity.com/profiles/$id"))!!.groupValues[1].toInt() }.getOrDefault(0)
        val games = runCatching {
            val gx = getText("https://steamcommunity.com/profiles/$id/games?tab=all&xml=1")
            val fromXml = Regex("<game>(.*?)</game>", RegexOption.DOT_MATCHES_ALL).findAll(gx).map { m ->
                val g = m.groupValues[1]
                Game(tag(g, "appID").toLong(), tag(g, "name").ifBlank { "?" }, (tag(g, "hoursOnRecord").replace(",", "").toDoubleOrNull() ?: 0.0).times(60).toInt())
            }.toList()
            fromXml.ifEmpty {
                val html = getText("https://steamcommunity.com/profiles/$id/games/?tab=all")
                val raw = Regex("data-profile-gameslist=\"([^\"]*)\"").find(html)!!.groupValues[1].replace("&quot;", "\"").replace("&amp;", "&")
                json.parseToJsonElement(raw).jsonObject["rgGames"]?.jsonArray.orEmpty().map { it.jsonObject }
                    .map { Game(it["appid"]!!.jsonPrimitive.long, it["name"]?.jsonPrimitive?.content ?: "?", ((it["hours_forever"]?.jsonPrimitive?.content ?: "0").replace(",", "").toDoubleOrNull() ?: 0.0).times(60).toInt()) }
            }.sortedByDescending { it.minutes }
        }.getOrDefault(emptyList())
        Profile(id, tag(xml, "steamID").ifBlank { id }, tag(xml, "avatarFull"), level, games)
    }

    /** Oyunun başarımları (topluluk istatistik sayfası) + küresel yüzde (anahtarsız herkese açık uç). */
    suspend fun achievements(id: String, appId: Long, lang: String): List<Ach> = withContext(Dispatchers.IO) {
        val xml = getText("https://steamcommunity.com/profiles/$id/stats/$appId/?xml=1&l=$lang")
        tag(xml, "error").takeIf { it.isNotEmpty() }?.let { error(t("Başarım yok / profil gizli", "No achievements / profile private")) }
        val pct = runCatching {
            json.parseToJsonElement(getText("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid=$appId")).jsonObject["achievementpercentages"]!!.jsonObject["achievements"]!!.jsonArray
                .associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["percent"]!!.jsonPrimitive.float }
        }.getOrDefault(emptyMap())
        Regex("<achievement([^>]*)>(.*?)</achievement>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { m ->
            val x = m.groupValues[2]; val done = m.groupValues[1].contains("closed=\"1\""); val api = tag(x, "apiname").ifBlank { tag(x, "name") }
            val d = tag(x, "description")
            Ach(api, tag(x, "name"), d, tag(x, if (done) "iconClosed" else "iconOpen"), done, tag(x, "unlockTimestamp").toLongOrNull() ?: 0L, pct[api] ?: -1f, d.isEmpty() && !done)
        }.toList().sortedWith(compareByDescending<Ach> { it.done }.thenByDescending { it.time })
    }
}
