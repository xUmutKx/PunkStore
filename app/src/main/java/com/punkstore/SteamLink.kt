package com.punkstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Steam Web API (kullanıcının kendi anahtarı): profil, sahip olunan oyunlar, oyun başarımları. */
object SteamLink {
    class Profile(val id: String, val name: String, val avatar: String, val level: Int, val games: List<Game>)
    class Game(val appId: Long, val name: String, val minutes: Int) {
        val header get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
        val capsule get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
    }
    class Ach(val api: String, val name: String, val desc: String, val icon: String, val done: Boolean, val time: Long, val percent: Float, val hidden: Boolean)

    private fun call(path: String, key: String, vararg q: Pair<String, String>): JsonObject {
        val url = ("https://api.steampowered.com/$path").toHttpUrl().newBuilder().addQueryParameter("key", key).apply { q.forEach { addQueryParameter(it.first, it.second) } }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body!!.string()
            if (r.code == 403 || r.code == 401) error(t("Steam anahtarı geçersiz", "Invalid Steam API key"))
            check(r.isSuccessful) { "Steam: ${r.code}" }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    suspend fun load(key: String, who: String, lang: String): Profile = withContext(Dispatchers.IO) {
        val id = if (who.length == 17 && who.all { it.isDigit() }) who else
            call("ISteamUser/ResolveVanityURL/v1/", key, "vanityurl" to who.substringAfterLast('/').trim())["response"]!!.jsonObject["steamid"]?.jsonPrimitive?.content
                ?: error(t("Kullanıcı bulunamadı (SteamID64 ya da özel URL adı gir)", "User not found (enter SteamID64 or vanity name)"))
        val me = call("ISteamUser/GetPlayerSummaries/v2/", key, "steamids" to id)["response"]!!.jsonObject["players"]!!.jsonArray.first().jsonObject
        val level = runCatching { call("IPlayerService/GetSteamLevel/v1/", key, "steamid" to id)["response"]!!.jsonObject["player_level"]!!.jsonPrimitive.int }.getOrDefault(0)
        val games = runCatching {
            call("IPlayerService/GetOwnedGames/v1/", key, "steamid" to id, "include_appinfo" to "1", "include_played_free_games" to "1")["response"]!!.jsonObject["games"]?.jsonArray.orEmpty()
                .map { it.jsonObject }.map { Game(it["appid"]!!.jsonPrimitive.long, it["name"]?.jsonPrimitive?.content ?: "?", it["playtime_forever"]?.jsonPrimitive?.int ?: 0) }
                .sortedByDescending { it.minutes }
        }.getOrDefault(emptyList())
        Profile(id, me["personaname"]?.jsonPrimitive?.content ?: id, me["avatarfull"]?.jsonPrimitive?.content ?: "", level, games)
    }

    /** Oyunun başarımları: ad/açıklama/simge (şema) + kazanım durumu + küresel yüzde. */
    suspend fun achievements(key: String, id: String, appId: Long, lang: String): List<Ach> = withContext(Dispatchers.IO) {
        val schema = call("ISteamUserStats/GetSchemaForGame/v2/", key, "appid" to "$appId", "l" to lang)["game"]?.jsonObject?.get("availableGameStats")?.jsonObject?.get("achievements")?.jsonArray.orEmpty().map { it.jsonObject }
        val mine = call("ISteamUserStats/GetPlayerAchievements/v1/", key, "steamid" to id, "appid" to "$appId", "l" to lang)["playerstats"]!!.jsonObject
        if (mine["success"]?.jsonPrimitive?.booleanOrNull == false) error(mine["error"]?.jsonPrimitive?.content ?: t("Başarım yok / profil gizli", "No achievements / profile private"))
        val pct = runCatching { call("ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/", key, "gameid" to "$appId")["achievementpercentages"]!!.jsonObject["achievements"]!!.jsonArray.associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["percent"]!!.jsonPrimitive.float } }.getOrDefault(emptyMap())
        val sc = schema.associateBy { it["name"]!!.jsonPrimitive.content }
        mine["achievements"]?.jsonArray.orEmpty().map { it.jsonObject }.map { a ->
            val api = a["apiname"]!!.jsonPrimitive.content; val s = sc[api]; val done = a["achieved"]?.jsonPrimitive?.int == 1
            Ach(api, s?.get("displayName")?.jsonPrimitive?.content ?: a["name"]?.jsonPrimitive?.content ?: api,
                s?.get("description")?.jsonPrimitive?.content?.ifBlank { null } ?: a["description"]?.jsonPrimitive?.content.orEmpty(),
                (if (done) s?.get("icon") else s?.get("icongray"))?.jsonPrimitive?.content.orEmpty(), done, a["unlocktime"]?.jsonPrimitive?.long ?: 0L, pct[api] ?: -1f,
                s?.get("hidden")?.jsonPrimitive?.int == 1)
        }.sortedWith(compareByDescending<Ach> { it.done }.thenByDescending { it.time })
    }
}
