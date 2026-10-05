package com.punkstore

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * SteamDB'nin hesap hesaplayıcı sayfasından kullanıcının oyunlarını okur (SteamDB düz HTTP isteklerini engeller,
 * bu yüzden ekran dışında çalışan bir WebView ile sayfa normal bir tarayıcı gibi yüklenir).
 */
object SteamDbWeb {
    lateinit var ctx: Context

    private const val JS = """(function(){var o=[];document.querySelectorAll('tr[data-appid]').forEach(function(r){
        var id=r.getAttribute('data-appid'); var a=r.querySelector('a[href*="/app/"]'); var nm=a?a.textContent.trim():'';
        var m=r.textContent.match(/(\d[\d.,]*)\s*(?:hours|hrs|h)\b/i); o.push({id:id,name:nm,h:m?m[1]:'0'});});
        return JSON.stringify({n:o.length,rows:o,title:document.title});})()"""

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun games(steamId: String): List<SteamLink.Game> = withContext(Dispatchers.Main) {
        val wv = WebView(ctx)
        try {
            wv.settings.javaScriptEnabled = true; wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptCookie(true)
            wv.webViewClient = WebViewClient()
            wv.loadUrl("https://steamdb.info/calculator/$steamId/?cc=us")
            var last = 0; var stable = 0
            repeat(24) {
                delay(1500)
                var raw: String? = null
                wv.evaluateJavascript(JS) { raw = it }
                delay(300)
                val txt = raw?.let { runCatching { json.parseToJsonElement(it).jsonPrimitive.content }.getOrNull() } ?: return@repeat
                val o = runCatching { json.parseToJsonElement(txt).jsonObject }.getOrNull() ?: return@repeat
                val n = o["n"]?.jsonPrimitive?.intOrNull ?: 0
                if (n > 0 && n == last) stable++ else stable = 0
                last = n
                if (n > 0 && stable >= 1) return@withContext o["rows"]!!.jsonArray.mapNotNull { e ->
                    val r = e.jsonObject
                    val id = r["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    val h = SteamLink.num(r["h"]?.jsonPrimitive?.content.orEmpty()) ?: 0.0
                    SteamLink.Game(id, r["name"]?.jsonPrimitive?.content?.ifBlank { "App $id" } ?: "App $id", (h * 60).toInt())
                }.distinctBy { it.appId }
            }
            emptyList()
        } finally { wv.destroy() }
    }
}
