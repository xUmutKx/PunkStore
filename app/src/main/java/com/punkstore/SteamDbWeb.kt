package com.punkstore

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Reads a user's page on SteamDB's account calculator (https://steamdb.info/calculator/<id>/?cc=tr): account value, level,
 * games played and the most played games with price, hours and achievement percentage. SteamDB refuses plain HTTP
 * requests, so the page is loaded like a normal browser in an off-screen WebView and parsed with assets/steamdb.js.
 */
object SteamDbWeb {
    lateinit var ctx: Context

    const val DEFAULT_ID = "76561199342032499"
    const val DEFAULT_CC = "tr"

    @Serializable class DbGame(val appId: Long, val name: String, val hours: Double, val price: String = "", val pct: String = "") {
        val header get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
    }
    @Serializable class Data(val id: String, val cc: String, val value: String, val level: String, val played: String, val xp: String, val games: List<DbGame>, val at: Long)

    class Failed(msg: String) : Exception(msg)
    /** Message prefix of the failure that means "SteamDB asks for a human check". */
    val CHECK get() = t("SteamDB insan doğrulaması istiyor", "SteamDB wants a human check")

    val script by lazy { ctx.assets.open("steamdb.js").bufferedReader().use { it.readText() } }
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    fun url(steamId: String, cc: String) = "https://steamdb.info/calculator/$steamId/?cc=${cc.ifBlank { DEFAULT_CC }}"

    /** Turns the result of assets/steamdb.js into data; the first value is the page title (a Cloudflare check page has a telling one). */
    fun parse(raw: String?, steamId: String, cc: String): Pair<String, Data?> {
        val txt = raw?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.content }.getOrNull() } ?: return "" to null
        val o = runCatching { json.parseToJsonElement(txt).jsonObject }.getOrNull() ?: return "" to null
        val title = o["title"]?.jsonPrimitive?.content.orEmpty()
        val games = o["games"]?.jsonArray.orEmpty().mapNotNull { e ->
            val g = e.jsonObject
            val id = g["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            DbGame(id, g["name"]?.jsonPrimitive?.content?.ifBlank { null } ?: "App $id", SteamLink.num(g["h"]?.jsonPrimitive?.content.orEmpty()) ?: 0.0,
                g["price"]?.jsonPrimitive?.content.orEmpty(), g["pct"]?.jsonPrimitive?.content.orEmpty())
        }.distinctBy { it.appId }
        val value = o["value"]?.jsonPrimitive?.content.orEmpty()
        if (games.isEmpty() && value.isBlank()) return title to null
        return title to Data(steamId, cc, value, o["level"]?.jsonPrimitive?.content.orEmpty(), o["played"]?.jsonPrimitive?.content.orEmpty(),
            o["xp"]?.jsonPrimitive?.content.orEmpty(), games, System.currentTimeMillis())
    }
    /** A name SteamDB's page gave that is no real title: empty, "App 123", or a number/hours/price line picked up by mistake. */
    fun badName(n: String) = n.isBlank() || n.length < 2 || n.startsWith("App ") || Regex("^[\\d.,\\s]+(h|hrs|hours|%|TL|₺|\\$|€)?$", RegexOption.IGNORE_CASE).matches(n.trim())
    fun isCheckPage(title: String) = title.contains("moment", true) || title.contains("attention", true) || title.contains("cloudflare", true) || title.contains("verif", true) || title.contains("human", true)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(steamId: String, cc: String): Data = withContext(Dispatchers.Main) {
        val wv = WebView(ctx)
        try {
            wv.settings.javaScriptEnabled = true; wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = UA // same UA as the visible check page, so the Cloudflare cookie it earns is accepted here too
            CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            wv.webViewClient = WebViewClient()
            wv.loadUrl(url(steamId, cc))
            var title = ""; var stable = 0; var lastN = -1; var checks = 0
            repeat(40) {
                delay(1500)
                var raw: String? = null
                wv.evaluateJavascript(script) { raw = it }
                delay(300)
                val (ti, d) = parse(raw, steamId, cc)
                if (ti.isNotBlank()) title = ti
                // a human check can't be solved off-screen: stop early so the app can ask the user
                if (isCheckPage(title) && ++checks >= 4) throw Failed(CHECK + " (\"$title\")")
                if (d != null) {
                    // the page fills in while it loads: accept once the number of games stops growing
                    if (d.games.size == lastN) stable++ else stable = 0
                    lastN = d.games.size
                    if (stable >= 1) return@withContext d
                }
            }
            throw Failed(t("SteamDB sayfası okunamadı", "Could not read the SteamDB page") + if (title.isNotBlank()) " (\"$title\")" else "")
        } finally { wv.destroy() }
    }

    /** Games for the library (minutes), used when the Steam profile itself does not list them. */
    suspend fun games(steamId: String): List<SteamLink.Game> =
        load(steamId, DEFAULT_CC).games.map { SteamLink.Game(it.appId, it.name, (it.hours * 60).toInt()) }
}
