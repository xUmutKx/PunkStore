package com.punkstore

import android.content.Context
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import com.aurora.gplayapi.helpers.TopChartsHelper
import com.aurora.gplayapi.helpers.contracts.TopChartsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.Properties

/** Google Play kaynağı (Aurora Store'un gplayapi kütüphanesi, anonim oturum). */
object PlayRepo {
    var customDispenser = ""
    private val DISPENSERS get() = listOfNotNull(customDispenser.ifBlank { null }, "https://auroraoss.com/api/auth")
    private const val UA = "com.aurora.store-4.6.1-60"
    private val client get() = GpHttp.get()
    private var auth: AuthData? = null
    /** Oturum açılırken oluşan, kullanıcıya gösterilecek not (örn. Google BadAuth) */
    @Volatile var authNote: String? = null

    private fun deviceProps(c: Context): Properties = Properties().also { p ->
        // gplayapi'nin paketlediği cihaz profilleri (Pixel 9a)
        val id = c.resources.getIdentifier("gplayapi_px_9a", "raw", c.packageName)
        c.resources.openRawResource(id).use { p.load(it) }
        p["Platforms"] = android.os.Build.SUPPORTED_ABIS.joinToString(",")
    }

    private fun fetchDispenserToken(): Pair<String, String> {
        var last: Throwable? = null
        repeat(3) {
            for (d in DISPENSERS) runCatching {
                http.newCall(Request.Builder().url(d).header("User-Agent", UA).build()).execute().use { r ->
                    val o = json.parseToJsonElement(r.body!!.string()).jsonObject
                    val e = o["email"]?.jsonPrimitive?.content
                    val a = o["auth"]?.jsonPrimitive?.content
                    if (e != null && a != null) return e to a
                    error(o["error"]?.jsonPrimitive?.content ?: "dispenser")
                }
            }.onFailure { last = it }
            Thread.sleep(600)
        }
        throw IllegalStateException(t("Google Play oturumu alınamadı", "Could not get a Google Play session") + (last?.message?.let { ": $it" } ?: ""))
    }

    suspend fun session(c: Context): AuthData = withContext(Dispatchers.IO) {
        auth?.let { return@withContext it }
        val f = File(c.filesDir, "play-auth.json")
        if (f.exists()) runCatching {
            val a = json.decodeFromString(AuthData.serializer(), f.readText())
            if (AuthHelper.isValid(a)) { auth = a; return@withContext a }
        }
        val g = googleCreds(c)
        fun anon(): AuthData { val (email, token) = fetchDispenserToken(); return AuthHelper.using(client).build(email, token, AuthHelper.Token.AUTH, true, deviceProps(c), Locale.getDefault()) }
        val a = if (g != null) try { AuthHelper.using(client).build(g.first, g.second, AuthHelper.Token.AAS, false, deviceProps(c), Locale.getDefault()) } catch (e: Throwable) {
            // Google belirteci reddedildi (BadAuth): kayıtlı hesabı sil, anonim oturumla devam et
            File(c.filesDir, "play-google.txt").delete()
            authNote = t("Google hesabı reddedildi (${e.message?.take(60) ?: "BadAuth"}); anonim oturumla devam ediliyor. Ayarlar'dan tekrar giriş yapabilirsin.", "Google rejected the account (${e.message?.take(60) ?: "BadAuth"}); continuing anonymously. You can sign in again from Settings.")
            anon()
        } else anon()
        runCatching { f.writeText(json.encodeToString(AuthData.serializer(), a)) }
        auth = a
        a
    }

    /** Google hesabı girişi: (e-posta, AAS belirteci) — yoksa anonim oturum kullanılır. */
    fun googleCreds(c: Context): Pair<String, String>? = runCatching {
        File(c.filesDir, "play-google.txt").readText().split('\n').let { it[0] to it[1] }.takeIf { it.first.isNotBlank() && it.second.isNotBlank() }
    }.getOrNull()
    fun googleEmail(c: Context) = googleCreds(c)?.first

    /** WebView'den gelen oauth_token'ı AAS belirtecine çevirir (Aurora Store ile aynı yöntem). */
    suspend fun googleLogin(c: Context, email: String, oauthToken: String) = withContext(Dispatchers.IO) {
        val body = okhttp3.FormBody.Builder()
            .add("lang", Locale.getDefault().toString().replace("_", "-")).add("google_play_services_version", "19629032").add("sdk_version", android.os.Build.VERSION.SDK_INT.toString())
            .add("device_country", Locale.getDefault().country.lowercase()).add("Email", email).add("service", "ac2dm")
            .add("get_accountid", "1").add("ACCESSTOKEN", "1").add("callerPkg", "com.google.android.gms").add("add_account", "1")
            .add("Token", oauthToken).add("callerSig", "38918a453d07199354f8b19af05ec6562ced5788").build()
        val txt = http.newCall(Request.Builder().url("https://android.googleapis.com/auth").header("User-Agent", "GoogleAuth/1.4").header("app", "com.google.android.gms").post(body).build()).execute().use { it.body!!.string() }
        val aas = txt.lines().firstOrNull { it.startsWith("Token=") }?.removePrefix("Token=") ?: error("Google: " + txt.lines().firstOrNull { it.startsWith("Error=") }.orEmpty())
        File(c.filesDir, "play-google.txt").writeText("$email\n$aas")
        reset(c); session(c)
    }

    fun logout(c: Context) { File(c.filesDir, "play-google.txt").delete(); reset(c) }

    fun reset(c: Context) { auth = null; File(c.filesDir, "play-auth.json").delete() }

    /** Oturum geçersiz olduysa bir kez yenileyip tekrar dener. */
    private suspend fun <T> withAuth(c: Context, block: (AuthData) -> T): T = withContext(Dispatchers.IO) {
        try { block(session(c)) } catch (e: Exception) { reset(c); block(session(c)) }
    }

    fun toItem(a: App) = AppItem(
        pkg = a.packageName, name = a.displayName.ifBlank { a.packageName },
        summary = a.shortDescription.ifBlank { a.developerName },
        description = a.description,
        icon = a.iconArtwork.url.ifBlank { null },
        categories = listOfNotNull(a.categoryName.ifBlank { null }),
        license = a.developerName,
        apkSize = a.size, versionName = a.versionName, versionCode = a.versionCode,
        screenshots = a.screenshots.map { it.url }.filter { it.isNotBlank() }.take(15),
        web = a.developerWebsite, source = "PLAY", offerType = a.offerType,
        rating = a.rating.average, ads = a.containsAds, nonFree = true, banner = runCatching { a.coverArtwork.url }.getOrDefault("").orEmpty().ifBlank { a.screenshots.firstOrNull { it.width > it.height }?.url.orEmpty() }, developer = a.developerName, installs = a.downloadString, updated = runCatching { java.text.SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(a.updatedOn)?.time ?: 0L }.getOrDefault(0L), tags = a.tags.map { it.name }.take(8), price = if (a.isFree) "" else a.price, discount = playDiscount(a), origPrice = playOrig(a),
    )

    suspend fun search(c: Context, q: String): List<AppItem> = withAuth(c) { au ->
        val b = SearchHelper(au).using(client).searchResults(q, "")
        b.streamClusters.values.flatMap { it.clusterAppList }.distinctBy { it.packageName }.map(::toItem)
    }

    suspend fun chart(c: Context, games: Boolean, chart: TopChartsContract.Chart = TopChartsContract.Chart.TOP_SELLING_FREE): List<AppItem> = withAuth(c) { au ->
        val type = if (games) TopChartsContract.Type.GAME else TopChartsContract.Type.APPLICATION
        TopChartsHelper(au).using(client).getCluster(type.value, chart.value).clusterAppList.map(::toItem)
    }

    /** Tek paket ya da birkaç paketin ayrıntıları (güncelleme denetimi için). */
    suspend fun details(c: Context, pkgs: List<String>): List<AppItem> = withAuth(c) { au ->
        AppDetailsHelper(au).using(client).getAppByPackageName(pkgs).filter { it.packageName.isNotBlank() }.map(::toItem)
    }

    /** Yorumlar: önce Play web (batchexecute, oturum gerektirmez), olmazsa gplayapi. */
    suspend fun reviews(c: Context, pkg: String): List<UserReview> {
        val web = runCatching { webReviews(pkg) }.getOrDefault(emptyList())
        if (web.isNotEmpty()) return web
        return withAuth(c) { au ->
            com.aurora.gplayapi.helpers.ReviewsHelper(au).using(client).getReviews(pkg, com.aurora.gplayapi.data.models.Review.Filter.ALL, com.aurora.gplayapi.helpers.ReviewsHelper.DEFAULT_SIZE).reviewList
                .map { UserReview(it.userName, it.comment.ifBlank { it.title }, null, it.rating, 0, 0, it.timeStamp * 1000) }.filter { it.text.isNotBlank() }
        }
    }

    private suspend fun webReviews(pkg: String): List<UserReview> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val lang = if (I18n.isTr) "tr" else "en"
        val freq = "[[[\"UsvDTd\",\"[null,null,[2,1,[40,null,null],null,[]],[\\\"$pkg\\\",7]]\",null,\"generic\"]]]"
        val req = okhttp3.Request.Builder().url("https://play.google.com/_/PlayStoreUi/data/batchexecute?rpcids=UsvDTd&source-path=%2Fstore%2Fapps%2Fdetails&hl=$lang&gl=US&rt=c")
            .post(okhttp3.FormBody.Builder().add("f.req", freq).build()).build()
        val body = http.newCall(req).execute().use { check(it.isSuccessful) { "Play: ${it.code}" }; it.body!!.string() }
        val line = body.lineSequence().first { it.startsWith("[[") }
        val inner = json.parseToJsonElement(line).jsonArray[0].jsonArray[2].jsonPrimitive.content
        json.parseToJsonElement(inner).jsonArray[0].jsonArray.mapNotNull { e ->
            runCatching {
                val r = e.jsonArray
                UserReview(r[1].jsonArray[0].jsonPrimitive.content, r[4].jsonPrimitive.content, null, r[2].jsonPrimitive.int, r[6].jsonPrimitive.intOrNull ?: 0, 0, r[5].jsonArray[0].jsonPrimitive.long * 1000)
            }.getOrNull()
        }.filter { it.text.isNotBlank() }
    }

    /** Play web aramasından paket adları (oturumsuz; gplayapi araması bir uygulamayı vermezse yedek). */
    suspend fun webSearchPkgs(q: String): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = "https://play.google.com/store/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&c=apps&hl=en&gl=US"
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36").build()
        val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body!!.string() else "" }
        Regex("/store/apps/details\\?id=([A-Za-z0-9_.]+)").findAll(body).map { it.groupValues[1] }.distinct().take(10).toList()
    }

    suspend fun detail(c: Context, pkg: String): AppItem? = details(c, listOf(pkg)).firstOrNull()

    /** İndirme bağlantılarını alır (base + split'ler + obb). */
    suspend fun files(c: Context, a: AppItem): List<PlayFile> = withAuth(c) { au ->
        PurchaseHelper(au).using(client).purchase(a.pkg, a.versionCode, a.offerType)
            .filter { it.url.isNotBlank() }
    }
}


/** Play'in teklif ayrıntılarından (varsa) indirim yüzdesi / eski fiyat; alan bulunamazsa 0 / boş. */
private fun playDiscount(a: com.aurora.gplayapi.data.models.App): Int = runCatching {
    a.offerDetails.values.firstNotNullOfOrNull { Regex("(\\d{1,2})\\s*%").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
}.getOrDefault(0)
private fun playOrig(a: com.aurora.gplayapi.data.models.App): String = runCatching {
    a.offerDetails.entries.firstOrNull { it.key.contains("original", true) || it.key.contains("list", true) }?.value.orEmpty().takeIf { it != a.price }.orEmpty()
}.getOrDefault("")
