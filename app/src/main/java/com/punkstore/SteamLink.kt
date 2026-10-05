package com.punkstore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request

/** Steam herkese açık profil (API anahtarsız): profil, sahip olunan oyunlar, oyun başarımları. */
object SteamLink {
    /**
     * games: tam liste (Steam girişiyle) ya da yalnızca profilde görünen son/çok oynananlar (partial = true).
     * gameCount: profil sayfasındaki toplam oyun sayısı (liste gizli olsa da görünür).
     */
    class Profile(val id: String, val name: String, val avatar: String, val level: Int, val games: List<Game>,
                  val partial: Boolean = false, val gameCount: Int = games.size, val friends: Int = 0, val badges: Int = 0,
                  val headline: String = "", val since: String = "", val online: String = "", val note: String? = null)
    class Game(val appId: Long, val name: String, val minutes: Int) {
        val header get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
        val capsule get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
    }
    class Ach(val api: String, val name: String, val desc: String, val icon: String, val done: Boolean, val time: Long, val percent: Float, val hidden: Boolean)

    /** Steam topluluk oturum çerezleri (Steam ile giriş yapıldıysa); oyun listesi artık yalnızca girişle görünüyor. */
    @Volatile var cookie: String = ""
    val loggedIn get() = cookie.contains("steamLoginSecure")
    /** steamLoginSecure çerezinin başındaki 17 haneli SteamID64 */
    fun idFromCookie(c: String): String? = Regex("steamLoginSecure=(\\d{17})").find(java.net.URLDecoder.decode(c, "UTF-8"))?.groupValues?.get(1)

    private class LoginRedirect : Exception()
    private fun getText(url: String): String {
        val rb = Request.Builder().url(url).header("Accept-Language", if (I18n.isTr) "tr" else "en")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128 Mobile Safari/537.36")
        if (cookie.isNotBlank()) rb.header("Cookie", cookie)
        http.newCall(rb.build()).execute().use { r ->
            check(r.isSuccessful) { "Steam: ${r.code}" }
            if (r.request.url.encodedPath.startsWith("/login")) throw LoginRedirect()
            return r.body!!.string()
        }
    }
    /** "1,234.5" / "1.234,5" / "12.3" / "1.234" → sayı (saatler tek ondalıklı olduğundan 3 haneli grup = binlik) */
    fun num(x: String): Double? {
        val v = x.trim().replace(" ", "").replace("\u00A0", "")
        val last = maxOf(v.lastIndexOf('.'), v.lastIndexOf(','))
        if (last < 0) return v.toDoubleOrNull()
        val decimals = v.length - last - 1
        return if (decimals == 3) v.replace(".", "").replace(",", "").toDoubleOrNull()
               else (v.substring(0, last).replace(".", "").replace(",", "") + "." + v.substring(last + 1)).toDoubleOrNull()
    }
    private fun unescape(x: String) = x.replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    private fun tag(x: String, n: String) = Regex("<$n>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</$n>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1)?.trim().orEmpty()
    private fun base(who: String) = who.trim().trimEnd('/').let { w ->
        val last = w.substringAfterLast('/')
        if (last.length == 17 && last.all { it.isDigit() }) "https://steamcommunity.com/profiles/$last" else "https://steamcommunity.com/id/$last"
    }

    /** API anahtarı gerekmez: herkese açık profil + (giriş yapıldıysa) tam oyun listesi. Profil herkese açık olmalı. */
    suspend fun load(who: String, lang: String): Profile = withContext(Dispatchers.IO) {
        val b = if (who.isBlank()) idFromCookie(cookie)?.let { "https://steamcommunity.com/profiles/$it" } ?: error(t("Profil ID'si gir ya da Steam ile giriş yap", "Enter a profile ID or sign in with Steam")) else base(who)
        val xml = getText("$b/?xml=1")
        if (tag(xml, "error").isNotEmpty() || tag(xml, "steamID64").isEmpty()) error(t("Profil bulunamadı (profil ID'si ya da özel URL adı gir)", "Profile not found (enter profile ID or vanity name)"))
        val id = tag(xml, "steamID64")
        val html = runCatching { getText("https://steamcommunity.com/profiles/$id") }.getOrDefault("")
        val level = Regex("friendPlayerLevelNum[^>]*>(\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        fun count(label: String) = Regex("count_link_label\">\\s*$label\\s*</span>.*?profile_count_link_total\">\\s*([\\d.,]+)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        // Not: sayfa dili Accept-Language'e göre değişir; İngilizce + Türkçe etiketler denenir
        val gameCount = maxOf(count("Games"), count("Oyunlar"))
        val friends = maxOf(count("Friends"), count("Arkadaşlar"))
        val badges = maxOf(count("Badges"), count("Rozetler"))

        var note: String? = null
        val full: List<Game> = try { fullList(id) } catch (e: LoginRedirect) {
            note = t("Steam tam oyun listesini yalnızca giriş yapınca gösteriyor. 'Steam ile giriş yap' ile tüm oyunların gelir; şimdilik profilde görünen oyunlar listelendi.",
                     "Steam only shows the full games list when signed in. Use 'Sign in with Steam' to get all games; showing games visible on the profile for now."); emptyList()
        } catch (e: Exception) { note = t("Oyun listesi okunamadı: ", "Could not read games list: ") + (e.message ?: ""); emptyList() }
        val games = full.ifEmpty { profileGames(xml, html) }
        if (full.isEmpty() && games.isEmpty() && note == null) note = t("Oyun listesi boş ya da 'Oyun ayrıntıları' gizli (Steam > Profil > Gizlilik ayarları).", "Games list is empty or 'Game details' is private (Steam > Profile > Privacy settings).")
        Profile(id, tag(xml, "steamID").ifBlank { id }, tag(xml, "avatarFull"), level, games.sortedByDescending { it.minutes },
            partial = full.isEmpty(), gameCount = maxOf(gameCount, games.size), friends = friends, badges = badges,
            headline = tag(xml, "headline"), since = tag(xml, "memberSince"), online = tag(xml, "stateMessage").replace(Regex("<[^>]+>"), " ").trim(), note = note)
    }

    /** Tam oyun listesi: önce XML, olmazsa yeni React sayfasındaki gömülü JSON. Girişsizse LoginRedirect fırlatır. */
    private fun fullList(id: String): List<Game> {
        runCatching { getText("https://steamcommunity.com/profiles/$id/games?tab=all&xml=1") }.onFailure { if (it is LoginRedirect) throw it }.getOrNull()?.let { gx ->
            val l = Regex("<game>(.*?)</game>", RegexOption.DOT_MATCHES_ALL).findAll(gx).mapNotNull { m ->
                val g = m.groupValues[1]
                Game(tag(g, "appID").toLongOrNull() ?: return@mapNotNull null, tag(g, "name").ifBlank { "?" }, ((tag(g, "hoursOnRecord").let(::num) ?: 0.0) * 60).toInt())
            }.toList()
            if (l.isNotEmpty()) return l
        }
        val page = getText("https://steamcommunity.com/profiles/$id/games/?tab=all")
        val raw = Regex("data-profile-gameslist=\"([^\"]*)\"").find(page)?.groupValues?.get(1)?.let(::unescape)
            ?: Regex("var rgGames = (\\[.*?\\]);", RegexOption.DOT_MATCHES_ALL).find(page)?.groupValues?.get(1)?.let { "{\"rgGames\":$it}" }
            ?: return emptyList()
        return json.parseToJsonElement(raw).jsonObject["rgGames"]?.jsonArray.orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val mins = o["playtime_forever"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
                ?: ((o["hours_forever"]?.jsonPrimitive?.content ?: "0").let(::num) ?: 0.0).times(60).toInt()
            Game(o["appid"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null, o["name"]?.jsonPrimitive?.content ?: "?", mins)
        }
    }

    /** Girişsiz yedek: profil XML'indeki "mostPlayedGames" + profil sayfasındaki son etkinlik oyunları. */
    private fun profileGames(xml: String, html: String): List<Game> {
        val l = mutableListOf<Game>()
        Regex("<mostPlayedGame>(.*?)</mostPlayedGame>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val g = m.groupValues[1]
            val id = Regex("/app/(\\d+)").find(tag(g, "gameLink"))?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
            l += Game(id, tag(g, "gameName"), ((tag(g, "hoursOnRecord").let(::num) ?: 0.0) * 60).toInt())
        }
        // Profil sayfası "Son etkinlik": her <div class="recent_game"> bloğunda uygulama bağlantısı, "x hrs on record", oyun adı
        html.split("class=\"recent_game\"").drop(1).forEach { blk ->
            val id = Regex("steamcommunity.com/app/(\\d+)").find(blk)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
            val name = Regex("game_name\">\\s*<a[^>]*>([^<]+)</a>").find(blk)?.groupValues?.get(1)?.let(::unescape)?.trim() ?: return@forEach
            val h = Regex("game_info_details\">\\s*([\\d.,]+)").find(blk)?.groupValues?.get(1)?.let(::num) ?: 0.0
            val i = l.indexOfFirst { it.appId == id }
            if (i < 0) l += Game(id, name, (h * 60).toInt()) else if (l[i].minutes == 0) l[i] = Game(id, l[i].name, (h * 60).toInt())
        }
        return l
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
