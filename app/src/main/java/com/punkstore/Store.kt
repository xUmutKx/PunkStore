package com.punkstore

import coil.imageLoader
import kotlinx.coroutines.async
import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SortBy(val tr: String, val en: String) { RELEVANCE("Önerilen", "Relevance"), UPDATED("Son güncellenen", "Recently updated"), NEWEST("En yeni", "Newest"), RATING("Puan", "Rating"), NAME("A-Z", "A-Z"), SIZE("Küçük boyut", "Smallest") }

data class Filters(
    val noAds: Boolean = false, val noTracking: Boolean = false, val freeOnly: Boolean = false, val foss: Boolean = false,
    val minRating: Float = 0f, val source: Int = 0, /* 0 hepsi, 1 F-Droid, 2 Play */ val sort: SortBy = SortBy.RELEVANCE,
) {
    val active get() = noAds || noTracking || freeOnly || foss || minRating > 0 || source != 0
    val count get() = listOf(noAds, noTracking, freeOnly, foss, minRating > 0, source != 0).count { it }
}

enum class Design(val steam: Boolean) { STEAM(true), STEAM2013(true), STEAM2006(true), MATERIAL(false) }

class Store(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("punk", Context.MODE_PRIVATE)

    // Ayarlar
    var design by mutableStateOf(runCatching { Design.valueOf(prefs.getString("design", "STEAM")!!) }.getOrDefault(Design.STEAM))
        private set
    var amoled by mutableStateOf(prefs.getBoolean("amoled", false)); private set
    var dark by mutableStateOf(prefs.getBoolean("dark", true)); private set
    fun changeDesign(d: Design) { design = d; Steam.pal = palFor(d); Steam.material = !d.steam; prefs.edit().putString("design", d.name).apply() }
    fun changeAmoled(v: Boolean) { amoled = v; prefs.edit().putBoolean("amoled", v).apply() }
    fun changeDark(v: Boolean) { dark = v; prefs.edit().putBoolean("dark", v).apply() }
    fun changeLang(p: LangPref) { I18n.pref = p; prefs.edit().putString("lang", p.name).apply() }

    // Profil + istek listesi (Steam tarzı)
    var userName by mutableStateOf(prefs.getString("userName", "Punk") ?: "Punk"); private set
    var avatar by mutableStateOf(prefs.getInt("avatar", 0)); private set
    var bio by mutableStateOf(prefs.getString("bio", "") ?: ""); private set
    val joined: Long = prefs.getLong("joined", 0L).let { if (it == 0L) System.currentTimeMillis().also { n -> prefs.edit().putLong("joined", n).apply() } else it }
    fun saveProfile(name: String, av: Int, b: String) {
        userName = name.trim().ifEmpty { "Punk" }; avatar = av; bio = b.trim()
        prefs.edit().putString("userName", userName).putInt("avatar", av).putString("bio", bio).apply()
    }
    val wishlist = mutableStateListOf<String>().apply { addAll((prefs.getString("wish", "") ?: "").split(',').filter { it.isNotBlank() }) }
    // Google hesabı (Aurora gibi; girişsiz anonim oturum yavaş olabilir)
    var googleEmail by mutableStateOf(PlayRepo.googleEmail(app)); private set
    fun googleLogin(email: String, token: String) {
        viewModelScope.launch {
            runCatching { PlayRepo.googleLogin(getApplication(), email, token) }
                .onSuccess { googleEmail = PlayRepo.googleEmail(getApplication()); playLoading = false; loadPlay() }
                .onFailure { error = it.message }
        }
    }
    fun googleLogout() { PlayRepo.logout(getApplication()); googleEmail = null; loadPlay() }

    // Kütüphaneye ekle (Steam gibi: kurulu olmasa da listede, "Yükle" düğmesiyle)
    val libAdded = mutableStateListOf<String>().apply { addAll((prefs.getString("lib", "") ?: "").split(',').filter { it.isNotBlank() }) }
    fun inLibrary(a: AppItem) = a.pkg in libAdded || isInstalled(a)
    fun toggleLibrary(a: AppItem) {
        if (a.pkg in libAdded) libAdded.remove(a.pkg) else { libAdded.add(0, a.pkg); remember(a) }
        prefs.edit().putString("lib", libAdded.joinToString(",")).apply()
    }
    private fun remember(a: AppItem) { if (a.source != "FDROID") prefs.edit().putString("wm_" + a.pkg, listOf(a.name, a.icon ?: "", a.summary, a.banner, a.source, a.price, a.discount.toString(), a.web).joinToString("\u0001")).apply() }
    fun resolve(p: String): AppItem? = byPkg(p) ?: prefs.getString("wm_$p", null)?.split('\u0001')?.let { f ->
        AppItem(p, f[0], f.getOrElse(2) { "" }, icon = f.getOrNull(1)?.ifEmpty { null }, banner = f.getOrElse(3) { "" }, source = f.getOrElse(4) { "PLAY" }, price = f.getOrElse(5) { "" }, discount = f.getOrElse(6) { "0" }.toIntOrNull() ?: 0, web = f.getOrElse(7) { "" })
    }
    /** Kütüphane: elle eklenenler + katalogdaki kurulu uygulamalar + katalog dışı kurulu uygulamalar (Cihaz). */
    val libApps: List<AppItem> get() = (libAdded.mapNotNull { resolve(it) } + apps.filter { isInstalled(it) && it.pkg !in libAdded } + localApps).distinctBy { it.pkg }

    fun isWished(a: AppItem) = a.pkg in wishlist
    fun toggleWish(a: AppItem) {
        if (a.pkg in wishlist) wishlist.remove(a.pkg) else wishlist.add(0, a.pkg)
        prefs.edit().putString("wish", wishlist.joinToString(",")).apply()
        remember(a)
    }
    /** Yüklenmeleri / açılışları say (profil istatistiği) */
    val launches = mutableStateMapOf<String, Int>().apply {
        (prefs.getString("launches", "") ?: "").split(',').filter { it.contains(':') }.forEach { put(it.substringBefore(':'), it.substringAfter(':').toIntOrNull() ?: 0) }
    }
    /** Son açılış zamanı (Steam "son oynanan") */
    val lastPlayed = mutableStateMapOf<String, Long>().apply {
        (prefs.getString("lastPlayed", "") ?: "").split(',').filter { it.contains(':') }.forEach { put(it.substringBefore(':'), it.substringAfter(':').toLongOrNull() ?: 0L) }
    }
    fun noteLaunch(pkg: String) {
        launches[pkg] = (launches[pkg] ?: 0) + 1
        lastPlayed[pkg] = System.currentTimeMillis()
        prefs.edit().putString("lastPlayed", lastPlayed.entries.sortedByDescending { it.value }.take(200).joinToString(",") { "${it.key}:${it.value}" }).apply()
        prefs.edit().putString("launches", launches.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }
    val getCount: Int get() = prefs.getInt("gets", 0)
    fun noteGet() { prefs.edit().putInt("gets", getCount + 1).apply() }
    val wishApps: List<AppItem> get() = wishlist.mapNotNull { resolve(it) }
    /** Steam "seviye": kurulu + istek + açılış sayısına göre */
    val xp: Int get() = installed.size * 10 + wishlist.size * 5 + launches.values.sum() * 2 + getCount * 20
    val level: Int get() = (Math.sqrt(xp / 25.0)).toInt() + 1
    fun levelProgress(): Float { val lo = (level - 1) * (level - 1) * 25; val hi = level * level * 25; return ((xp - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f) }

    // Kurulum / indirme ayarları (Aurora Store tarzı)
    var method by mutableStateOf(Cfg.method(app)); private set
    var deleteApk by mutableStateOf(Cfg.deleteApk(app)); private set
    var wifiOnly by mutableStateOf(Cfg.wifiOnly(app)); private set
    var intervalH by mutableStateOf(prefs.getInt("intervalH", 3)); private set
    val ignored = mutableStateListOf<String>().apply { addAll((prefs.getString("ignored", "") ?: "").split(',').filter { it.isNotBlank() }) }
    var dispenser by mutableStateOf(prefs.getString("dispenser", "") ?: ""); private set
    var rootOk by mutableStateOf<Boolean?>(null); private set
    fun changeMethod(m: InstallMethod) { method = m; prefs.edit().putString("method", m.name).apply(); if (m == InstallMethod.ROOT) testRoot() }
    fun changeDeleteApk(v: Boolean) { deleteApk = v; prefs.edit().putBoolean("delApk", v).apply() }
    fun changeWifiOnly(v: Boolean) { wifiOnly = v; prefs.edit().putBoolean("wifiOnly", v).apply() }
    fun changeInterval(h: Int) { intervalH = h; prefs.edit().putInt("intervalH", h).apply() }
    fun changeDispenser(u: String) { dispenser = u.trim(); prefs.edit().putString("dispenser", dispenser).apply(); PlayRepo.customDispenser = dispenser; PlayRepo.reset(getApplication()) }
    fun testRoot() { viewModelScope.launch { rootOk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Installer.hasRoot() } } }
    fun toggleIgnore(a: AppItem) { if (a.pkg in ignored) ignored.remove(a.pkg) else ignored.add(a.pkg); prefs.edit().putString("ignored", ignored.joinToString(",")).apply() }
    fun clearCache() { getApplication<Application>().cacheDir.resolve("apk").deleteRecursively() }
    fun uninstall(ctx: Context, pkg: String) {
        viewModelScope.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Installer.uninstall(ctx, pkg) } }.onFailure { error = it.message }
            refreshInstalled()
        }
    }

    // Filtreler (kalıcı)
    var filters by mutableStateOf(Filters(
        prefs.getBoolean("f_ads", false), prefs.getBoolean("f_trk", false), prefs.getBoolean("f_free", false), prefs.getBoolean("f_foss", false),
        prefs.getFloat("f_rate", 0f), prefs.getInt("f_src", 0), runCatching { SortBy.valueOf(prefs.getString("f_sort", "RELEVANCE")!!) }.getOrDefault(SortBy.RELEVANCE)))
        private set
    fun applyFilters(f: Filters) {
        filters = f
        prefs.edit().putBoolean("f_ads", f.noAds).putBoolean("f_trk", f.noTracking).putBoolean("f_free", f.freeOnly).putBoolean("f_foss", f.foss)
            .putFloat("f_rate", f.minRating).putInt("f_src", f.source).putString("f_sort", f.sort.name).apply()
    }
    fun List<AppItem>.filtered(f: Filters = filters): List<AppItem> {
        val l = filter { a ->
            (!f.noAds || !a.ads) && (!f.noTracking || !a.tracking) && (!f.freeOnly || a.price.isBlank()) && (!f.foss || (a.source == "FDROID" && !a.nonFree)) &&
                (f.minRating <= 0 || a.rating >= f.minRating) && (f.source == 0 || (f.source == 1) == (a.source == "FDROID"))
        }
        return when (f.sort) {
            SortBy.UPDATED -> l.sortedByDescending { it.updated }
            SortBy.NEWEST -> l.sortedByDescending { it.added }
            SortBy.RATING -> l.sortedByDescending { it.rating }
            SortBy.NAME -> l.sortedBy { it.name.lowercase() }
            SortBy.SIZE -> l.sortedBy { if (it.apkSize <= 0) Long.MAX_VALUE else it.apkSize }
            SortBy.RELEVANCE -> l
        }
    }

    fun filt(l: List<AppItem>, f: Filters = filters) = l.filtered(f)

    // Zevk profili (Keşfet): kategori -> puan. Beğeni +3, geçme -1
    private val taste = mutableMapOf<String, Int>().apply { (prefs.getString("taste", "") ?: "").split('|').filter { it.contains('=') }.forEach { put(it.substringBeforeLast('='), it.substringAfterLast('=').toIntOrNull() ?: 0) } }
    val seen = mutableSetOf<String>().apply { addAll((prefs.getString("seen", "") ?: "").split(',').filter { it.isNotBlank() }) }
    var deckVersion by mutableStateOf(0); private set
    var liked by mutableStateOf(prefs.getInt("liked", 0)); private set
    private fun bump(a: AppItem, d: Int) { (a.categories + a.tags).distinct().forEach { taste[it] = (taste[it] ?: 0) + d }; prefs.edit().putString("taste", taste.entries.joinToString("|") { "${it.key}=${it.value}" }).apply() }
    private fun markSeen(a: AppItem) { seen.add(a.pkg); prefs.edit().putString("seen", seen.toList().takeLast(3000).joinToString(",")).apply() }
    /** Keşfet: sağa kaydır = beğen (istek listesi) */
    fun discoverLike(a: AppItem) { markSeen(a); bump(a, 3); if (a.pkg !in wishlist) toggleWish(a); liked++; prefs.edit().putInt("liked", liked).apply(); deckVersion++ }
    fun discoverSkip(a: AppItem) { markSeen(a); bump(a, -1); deckVersion++ }
    fun resetDiscover() { seen.clear(); prefs.edit().remove("seen").apply(); deckVersion++ }
    private val seed = System.nanoTime()
    /** Zevke göre sıralı aday kartlar: filtreli, kurulu olmayan, görülmemiş. */
    fun deck(mode: String = ""): List<AppItem> {
        deckVersion
        val rnd = java.util.Random(seed + seen.size)
        val pool: List<AppItem> = when (mode) {
            "steam" -> steamMap.values.toList()
            "android" -> apps
            "fdroid" -> apps.filter { it.source == "FDROID" }
            "play" -> apps.filter { it.source == "PLAY" }
            "games" -> apps.filter { a -> a.categories.any { it.contains("Game", true) } } + steamMap.values
            "" -> apps + steamMap.values
            else -> (apps + steamMap.values).filter { mode in it.categories }
        }
        return pool.filtered(filters.copy(sort = SortBy.RELEVANCE)).asSequence()
            .filter { it.pkg !in seen && it.pkg !in wishlist && !isInstalled(it) && (it.cover != null || it.icon != null) }
            .map { a -> a to ((a.categories + a.tags).distinct().sumOf { taste[it] ?: 0 } + (if (a.cover != null) 2 else 0) + a.rating.toInt() + rnd.nextInt(6)) }
            .sortedByDescending { it.second }.take(40).map { it.first }.toList()
    }
    val tasteTop: List<String> get() = taste.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(5).map { it.key }

    // Güncelleme denetimi
    val updateCount: Int get() = apps.count { isInstalled(it) && hasUpdate(it) }
    var selfUpdate by mutableStateOf<AppItem?>(null); private set
    var checking by mutableStateOf(false); private set
    var lastCheck by mutableStateOf(prefs.getLong("lastCheck", 0L)); private set
    var autoCheck by mutableStateOf(prefs.getBoolean("autoCheck", true)); private set
    fun changeAutoCheck(v: Boolean) { autoCheck = v; prefs.edit().putBoolean("autoCheck", v).apply() }

    /** Katalogları yeniler + Punk Store'un kendi sürümünü denetler + güncelleme varsa bildirim gönderir. */
    fun checkUpdates(force: Boolean = false) {
        if (checking) return
        if (!force && System.currentTimeMillis() - lastCheck < intervalH * 3600_000L) return
        checking = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (FdroidRepo.cacheAgeMs(ctx) > 6 * 3600_000L) runCatching { fdroid = FdroidRepo.refresh(ctx) { } }
            runCatching {
                val mine = installed.keys.filter { k -> fdroid.none { it.pkg == k } && !k.startsWith("com.android.") && !k.startsWith("android") }
                mine.chunked(40).forEach { addPlay(PlayRepo.details(ctx, it)) }
            }
            runCatching { selfUpdate = Updater.checkSelf(ctx, installed[ctx.packageName] ?: 0L) }
            lastCheck = System.currentTimeMillis(); prefs.edit().putLong("lastCheck", lastCheck).apply()
            checking = false
            val n = updateCount + (if (selfUpdate != null) 1 else 0)
            if (n > 0 && autoCheck && n != prefs.getInt("notified", 0)) { Updater.notify(ctx, n); prefs.edit().putInt("notified", n).apply() }
            if (n == 0) prefs.edit().putInt("notified", 0).apply()
        }
    }

    // ---- Steam Mağaza (genel API) ----
    val steamMap = mutableStateMapOf<String, AppItem>()
    var steamLists by mutableStateOf<Map<String, List<AppItem>>>(emptyMap()); private set
    var steamErr by mutableStateOf<String?>(null); private set
    fun loadSteamStore() { viewModelScope.launch { runCatching { SteamStoreApi.featured() }.onSuccess { m -> m.values.flatten().forEach { steamMap[it.pkg] = it }; steamLists = m; saveExtra(); steamErr = null }.onFailure { steamErr = it.message } } }
    suspend fun searchSteamStore(q: String): List<AppItem> = runCatching { SteamStoreApi.search(q).onEach { steamMap.putIfAbsent(it.pkg, it) }.also { saveExtra() } }.getOrDefault(emptyList())
    val reviews = mutableStateMapOf<String, List<UserReview>>()
    fun loadReviews(a: AppItem, force: Boolean = false) { if ((a.pkg in reviews && !force) || a.source == "FDROID") return; reviewErr.remove(a.pkg); reviews.remove(a.pkg); viewModelScope.launch { runCatching { if (a.source == "STEAM") SteamStoreApi.reviews(a) else PlayRepo.reviews(getApplication(), a.pkg) }.onSuccess { reviews[a.pkg] = it }.onFailure { reviewErr[a.pkg] = it.message ?: it.javaClass.simpleName; reviews[a.pkg] = emptyList() } } }
    fun enrichSteam(a: AppItem) { if (a.review.isNotBlank() || a.description.isNotBlank()) return; viewModelScope.launch { runCatching { SteamStoreApi.details(a) }.onSuccess { d ->
        steamMap[a.pkg] = d
        val and = runCatching { PlayRepo.search(getApplication(), a.name).firstOrNull { it.name.equals(a.name, true) } }.getOrNull()
        if (and != null) steamMap[a.pkg] = d.copy(extra = d.extra + ("android" to and.pkg))
    } } }
    val reviewErr = mutableStateMapOf<String, String>()

    // ---- Son bakılanlar, favoriler, koleksiyonlar, geçmiş, seri ----
    private fun strList(key: String) = (prefs.getString(key, "") ?: "").split(',').filter { it.isNotBlank() }
    val recent = mutableStateListOf<String>().apply { addAll(strList("recent")) }
    fun noteView(p: String) { recent.remove(p); recent.add(0, p); while (recent.size > 40) recent.removeAt(recent.lastIndex); prefs.edit().putString("recent", recent.joinToString(",")).apply() }
    val pins = mutableStateListOf<String>().apply { addAll(strList("pins")) }
    fun togglePin(a: AppItem) { if (a.pkg in pins) pins.remove(a.pkg) else { pins.add(0, a.pkg); remember(a) }; prefs.edit().putString("pins", pins.joinToString(",")).apply() }
    val collections = mutableStateMapOf<String, List<String>>().apply { (prefs.getString("cols", "") ?: "").split('|').filter { it.contains(':') }.forEach { put(it.substringBefore(':'), it.substringAfter(':').split(',').filter { x -> x.isNotBlank() }) } }
    private fun saveCols() { prefs.edit().putString("cols", collections.entries.joinToString("|") { "${it.key}:${it.value.joinToString(",")}" }).apply() }
    fun addCollection(n: String) { val k = n.trim().replace(Regex("[:|,]"), " "); if (k.isNotEmpty() && k !in collections) { collections[k] = emptyList(); saveCols() } }
    fun deleteCollection(n: String) { collections.remove(n); saveCols() }
    fun toggleInCollection(n: String, a: AppItem) { val l = collections[n].orEmpty(); collections[n] = if (a.pkg in l) l - a.pkg else l + a.pkg; remember(a); saveCols() }
    val history = mutableStateListOf<String>().apply { addAll((prefs.getString("hist", "") ?: "").split('|').filter { it.isNotBlank() }) }
    private fun noteHistory(a: AppItem) { history.add(0, "${a.pkg}~${a.name}~${System.currentTimeMillis()}"); while (history.size > 50) history.removeAt(history.lastIndex); prefs.edit().putString("hist", history.joinToString("|")).apply() }
    fun clearHistory() { history.clear(); prefs.edit().remove("hist").apply() }
    var streak by mutableStateOf(0); private set
    val searches = mutableStateListOf<String>().apply { addAll(strList("searches")) }
    fun noteSearch(q: String) { val k = q.trim().replace(",", " "); if (k.length < 3) return; searches.remove(k); searches.add(0, k); while (searches.size > 12) searches.removeAt(searches.lastIndex); prefs.edit().putString("searches", searches.joinToString(",")).apply() }
    fun clearSearches() { searches.clear(); prefs.edit().remove("searches").apply() }
    var fontScale by mutableStateOf(prefs.getFloat("fscale", 1f)); private set
    fun changeFontScale(v: Float) { fontScale = v; prefs.edit().putFloat("fscale", v).apply() }
    var haptic by mutableStateOf(prefs.getBoolean("haptic", true)); private set
    fun changeHaptic(v: Boolean) { haptic = v; prefs.edit().putBoolean("haptic", v).apply() }
    suspend fun openByPackage(p: String): String? {
        byPkg(p)?.let { return it.pkg }
        val a = runCatching { PlayRepo.detail(getApplication(), p) }.getOrNull() ?: return null
        addPlay(listOf(a)); return a.pkg
    }
    fun exportJson(): String = buildJsonObject {
        put("app", "PunkStore"); put("name", userName); put("wishlist", JsonArray(wishlist.map { JsonPrimitive(it) })); put("library", JsonArray(libAdded.map { JsonPrimitive(it) }))
        put("settings", buildJsonObject { put("design", design.name); put("method", method.name); put("deleteApk", deleteApk); put("wifiOnly", wifiOnly); put("intervalH", intervalH); put("autoCheck", autoCheck); put("fontScale", fontScale); put("haptic", haptic); put("splash", splash); put("lang", I18n.pref.name) })
        put("pins", JsonArray(pins.map { JsonPrimitive(it) })); put("collections", buildJsonObject { collections.forEach { (k, v) -> put(k, JsonArray(v.map { JsonPrimitive(it) })) } })
    }.toString()
    fun importJson(txt: String): Boolean = runCatching {
        val o = Json.parseToJsonElement(txt).jsonObject; check(o["app"]?.jsonPrimitive?.content == "PunkStore")
        fun arr(k: String) = o[k]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        arr("wishlist").forEach { if (it !in wishlist) wishlist.add(it) }; prefs.edit().putString("wish", wishlist.joinToString(",")).apply()
        arr("library").forEach { if (it !in libAdded) libAdded.add(it) }; prefs.edit().putString("lib", libAdded.joinToString(",")).apply()
        arr("pins").forEach { if (it !in pins) pins.add(it) }; prefs.edit().putString("pins", pins.joinToString(",")).apply()
        o["settings"]?.jsonObject?.let { st ->
            st["design"]?.jsonPrimitive?.content?.let { v -> runCatching { changeDesign(Design.valueOf(v)) } }
            st["method"]?.jsonPrimitive?.content?.let { v -> runCatching { changeMethod(InstallMethod.valueOf(v)) } }
            st["deleteApk"]?.jsonPrimitive?.booleanOrNull?.let { changeDeleteApk(it) }; st["wifiOnly"]?.jsonPrimitive?.booleanOrNull?.let { changeWifiOnly(it) }
            st["intervalH"]?.jsonPrimitive?.intOrNull?.let { changeInterval(it) }; st["autoCheck"]?.jsonPrimitive?.booleanOrNull?.let { changeAutoCheck(it) }
            st["fontScale"]?.jsonPrimitive?.floatOrNull?.let { changeFontScale(it) }; st["haptic"]?.jsonPrimitive?.booleanOrNull?.let { changeHaptic(it) }; st["splash"]?.jsonPrimitive?.booleanOrNull?.let { changeSplash(it) }
            st["lang"]?.jsonPrimitive?.content?.let { v -> runCatching { changeLang(LangPref.valueOf(v)) } }
        }
        o["collections"]?.jsonObject?.forEach { (k, v) -> collections[k] = (collections[k].orEmpty() + v.jsonArray.map { it.jsonPrimitive.content }).distinct() }; saveCols(); true
    }.getOrDefault(false)

    // Steam hesabı (Web API)
    var steamKey by mutableStateOf(prefs.getString("steamKey", "") ?: ""); private set
    var steamWho by mutableStateOf(prefs.getString("steamWho", "") ?: ""); private set
    var steamId by mutableStateOf(prefs.getString("steamId", "") ?: ""); private set
    fun saveSteam(key: String, who: String, id: String) { steamKey = key; steamWho = who; steamId = id; prefs.edit().putString("steamKey", key).putString("steamWho", who).putString("steamId", id).apply() }

    // Başarımlar
    val achUnlocked = mutableStateListOf<String>().apply { addAll((prefs.getString("ach", "") ?: "").split(',').filter { it.isNotBlank() }) }
    val achTime = mutableMapOf<String, Long>().apply { (prefs.getString("achT", "") ?: "").split(',').filter { it.contains(':') }.forEach { put(it.substringBefore(':'), it.substringAfter(':').toLongOrNull() ?: 0L) } }
    var achToast by mutableStateOf<Achv?>(null); private set
    fun dismissToast() { achToast = null }
    fun checkAchievements() {
        val new = ACHIEVEMENTS.filter { it.id !in achUnlocked && it.cur(this) >= it.target }
        if (new.isEmpty()) return
        val now = System.currentTimeMillis()
        new.forEach { achUnlocked.add(it.id); achTime[it.id] = now }
        prefs.edit().putString("ach", achUnlocked.joinToString(",")).putString("achT", achTime.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        achToast = new.last()
    }
    var splash by mutableStateOf(prefs.getBoolean("splash", true)); private set
    fun changeSplash(v: Boolean) { splash = v; prefs.edit().putBoolean("splash", v).apply() }

    // Profil özelleştirme
    var cover by mutableStateOf(prefs.getInt("cover", 0)); private set
    var status by mutableStateOf(prefs.getString("status", "") ?: ""); private set
    var photo by mutableStateOf(prefs.getString("photo", null)); private set
    var showcase = mutableStateListOf<String>().apply { addAll((prefs.getString("showcase", "") ?: "").split(',').filter { it.isNotBlank() }) }
    fun saveCustom(cover: Int, status: String, photo: String?, showcase: List<String>) {
        this.cover = cover; this.status = status.take(40); this.photo = photo; this.showcase.clear(); this.showcase.addAll(showcase.take(4))
        prefs.edit().putInt("cover", cover).putString("status", this.status).putString("photo", photo).putString("showcase", this.showcase.joinToString(",")).apply()
    }

    // Veri
    var fdroid by mutableStateOf<List<AppItem>>(emptyList()); private set
    /** Google Play'den gelen (üst listeler, arama sonuçları, kurulu uygulamaların ayrıntıları). */
    private var playMap by mutableStateOf<Map<String, AppItem>>(emptyMap())
    var playTop by mutableStateOf<List<AppItem>>(emptyList()); private set
    var playGames by mutableStateOf<List<AppItem>>(emptyList()); private set
    var playLoading by mutableStateOf(false); private set
    var playError by mutableStateOf<String?>(null); private set
    /** Tüm uygulamalar: F-Droid + Play (aynı paket varsa F-Droid önde). */
    val apps: List<AppItem> by derivedStateOf { fdroid + playMap.values.filter { p -> fdroid.none { it.pkg == p.pkg } } }
    var loading by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set
    fun dismissError() { error = null }

    /** pkg -> indirme ilerlemesi (0..1); -1 = kuruluyor */
    val busy = mutableStateMapOf<String, Float>()
    val installed = mutableStateMapOf<String, Long>()
    val dl = DownloadQueue(this)
    var localApps by mutableStateOf<List<AppItem>>(emptyList()); private set
    private var localJob: kotlinx.coroutines.Job? = null

    init {
        PlayRepo.customDispenser = dispenser
        Steam.pal = palFor(design); Steam.material = !design.steam
        I18n.pref = runCatching { LangPref.valueOf(prefs.getString("lang", "AUTO")!!) }.getOrDefault(LangPref.AUTO)
        FdroidRepo.cached(app)?.let { fdroid = it }
        loadExtra()
        refreshInstalled()
        if (fdroid.isEmpty() || FdroidRepo.cacheAgeMs(app) > 24 * 3600_000L) refresh()
        loadPlay()
        loadSteamStore()
        checkUpdates()
        dl.restore()
        run { val today = System.currentTimeMillis() / 86400000L; val last = prefs.getLong("lastDay", 0L); var st = prefs.getInt("streak", 0)
            if (today != last) { st = if (today == last + 1) st + 1 else 1; prefs.edit().putLong("lastDay", today).putInt("streak", st).apply() }; streak = st }
    }

    fun refresh() {
        if (loading) return
        loading = true; error = null; progress = 0f
        viewModelScope.launch {
            runCatching { FdroidRepo.refresh(getApplication()) { progress = it } }
                .onSuccess { fdroid = it }
                .onFailure { error = it.message ?: t("Bilinmeyen hata", "Unknown error") }
            loading = false
            refreshInstalled()
        }
    }

    /** Açılış animasyonu görselleri: arka planda indirilip önbelleğe alınır; yalnızca gerçekten inenler kaydedilir. */
    val splashUrls: List<String> get() = (prefs.getString("splashUrls", "") ?: "").split("\n").filter { it.startsWith("http") }
    private var splashPrepared = false
    fun prepareSplash() {
        if (splashPrepared || apps.size < 100) return
        splashPrepared = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val pool = (apps.mapNotNull { it.cover ?: it.icon } + steamMap.values.mapNotNull { it.banner.ifBlank { null } }).filter { it.startsWith("http") }.distinct().shuffled().take(120)
            val ok = java.util.Collections.synchronizedList(mutableListOf<String>())
            pool.chunked(12).forEach { ch -> ch.map { u -> async(kotlinx.coroutines.Dispatchers.IO) {
                val r = ctx.imageLoader.execute(coil.request.ImageRequest.Builder(ctx).data(u).size(256).build())
                if (r is coil.request.SuccessResult) ok.add(u) } }.forEach { runCatching { it.await() } } }
            if (ok.size >= 40) prefs.edit().putString("splashUrls", ok.joinToString("\n")).apply()
        }
    }

    private fun addPlay(l: List<AppItem>) { playMap = playMap + l.associateBy { it.pkg }; saveExtra() }

    /** Play + Steam'den gelen sonuçlar yenilemede kaybolmasın: diske yaz, açılışta geri yükle. */
    private val extraFile get() = java.io.File(getApplication<Application>().filesDir, "extra-cache.json")
    private var saveJob: kotlinx.coroutines.Job? = null
    private fun saveExtra() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(1500)
            runCatching { extraFile.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(AppItem.serializer()), (playMap.values + steamMap.values).take(8000).map { it.copy(description = it.description.take(600)) })) }
        }
    }
    private fun loadExtra() = runCatching {
        val l = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AppItem.serializer()), extraFile.readText())
        l.filter { it.source == "STEAM" }.forEach { steamMap[it.pkg] = it }
        playMap = l.filter { it.source != "STEAM" }.associateBy { it.pkg }
    }

    /** Google Play üst listeleri + kurulu uygulamaların güncelleme denetimi. */
    fun loadPlay() {
        if (playLoading) return
        playLoading = true; playError = null
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            runCatching {
                val top = PlayRepo.chart(ctx, false); playTop = top; addPlay(top)
                val g = PlayRepo.chart(ctx, true); playGames = g; addPlay(g)
                // daha fazla uygulama: diğer listeler (çok satan ücretli, trend, en çok kazanan)
                for (games in listOf(false, true)) for (ch in listOf(com.aurora.gplayapi.helpers.contracts.TopChartsContract.Chart.TOP_GROSSING, com.aurora.gplayapi.helpers.contracts.TopChartsContract.Chart.MOVERS_SHAKERS, com.aurora.gplayapi.helpers.contracts.TopChartsContract.Chart.TOP_SELLING_PAID))
                    runCatching { addPlay(PlayRepo.chart(ctx, games, ch)) }
                val mine = installed.keys.filter { k -> fdroid.none { it.pkg == k } && !k.startsWith("com.android.") && !k.startsWith("android") }
                mine.chunked(40).forEach { addPlay(PlayRepo.details(ctx, it)) }
            }.onFailure { playError = "Google Play: " + (it.message ?: t("Bilinmeyen hata", "Unknown error")) }
            playLoading = false
        }
    }

    /** Google Play'de arama (F-Droid araması yerelde yapılır). */
    suspend fun searchPlay(q: String): List<AppItem> {
        val found = runCatching { PlayRepo.search(getApplication(), q) }.getOrElse { playError = "Google Play: " + (it.message ?: ""); emptyList() }
        // Play aramasında adı tutan yoksa: bilinen/olası paket adlarını doğrudan sorgula
        val hit = found.any { Rank.score(it, q) >= 400 }
        val extra: List<AppItem> = if (hit) emptyList() else kotlinx.coroutines.coroutineScope {
            val sc = this
            Rank.guesses(q).map { g -> sc.async { runCatching { PlayRepo.detail(getApplication(), g) }.getOrNull() } }.mapNotNull { it.await() }
        }
        return (extra + found).distinctBy { it.pkg }.also { addPlay(it) }
    }

    fun refreshInstalled() {
        val pm = getApplication<Application>().packageManager
        val now = pm.getInstalledPackages(0).associate { it.packageName to it.longVersionCode }
        installed.keys.retainAll(now.keys); installed.putAll(now)
        loadLocal()
    }

    /** Katalogda olmayan, kullanıcının kurduğu (sistem dışı, açılabilir) uygulamalar: kütüphanede "Cihaz" kaynağıyla. */
    private fun loadLocal() {
        localJob?.cancel()
        localJob = viewModelScope.launch {
            localApps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                runCatching {
                    pm.getInstalledApplications(0).filter { ai -> ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 && ai.packageName != getApplication<Application>().packageName && pm.getLaunchIntentForPackage(ai.packageName) != null }
                        .map { ai -> AppItem(ai.packageName, pm.getApplicationLabel(ai).toString(), source = "LOCAL", versionCode = installed[ai.packageName] ?: 0,
                            updated = runCatching { pm.getPackageInfo(ai.packageName, 0).lastUpdateTime }.getOrDefault(0L), apkSize = runCatching { java.io.File(ai.sourceDir).length() }.getOrDefault(0L)) }
                        .sortedBy { it.name.lowercase() }
                }.getOrDefault(emptyList())
            }
        }
    }

    fun isInstalled(a: AppItem) = installed.containsKey(a.pkg)
    fun hasUpdate(a: AppItem) = a.pkg !in ignored && installed[a.pkg]?.let { it < a.versionCode } == true
    fun byPkg(pkg: String) = apps.firstOrNull { it.pkg == pkg } ?: steamMap[pkg]
    /** Bilinen her kaynaktan (katalog, kayıtlı, cihaz) */
    fun anyPkg(pkg: String) = byPkg(pkg) ?: resolve(pkg) ?: localApps.firstOrNull { it.pkg == pkg }

    /** İndir + kur: kuyruğa alır (devam ettirme, yeniden deneme, doğrulama Downloads.kt'de). */
    fun getOrUpdate(a: AppItem, ctx: Context) {
        dl[a.pkg]?.let { if (it.active) return; if (it.state == DlState.PAUSED || it.state == DlState.FAILED) { dl.resume(a.pkg); return } }
        if (Cfg.wifiOnly(ctx) && !Cfg.wifiOk(ctx)) { error = t("Yalnızca Wi-Fi'de indirme açık", "Wi-Fi only downloads is on"); return }
        if (method != InstallMethod.ROOT && !Installer.canInstall(ctx)) { Installer.askPermission(ctx); return }
        remember(a)
        dl.enqueue(a)
    }
    fun report(msg: String) { error = msg }
    /** Kurulum bitti: kütüphaneye ekle (Steam gibi), geçmiş, istatistik. */
    fun onInstalled(a: AppItem) {
        noteGet(); noteHistory(a)
        if (a.pkg !in libAdded) { libAdded.add(0, a.pkg); prefs.edit().putString("lib", libAdded.joinToString(",")).apply() }
        refreshInstalled()
    }

    // Keşif listeleri
    fun open(ctx: Context, pkg: String) { noteLaunch(pkg); Installer.open(ctx, pkg) }
    /** İstek listesine / kurulu uygulamalara göre kategori önerisi */
    val recommended: List<AppItem> get() {
        val favCats = (wishApps + apps.filter { isInstalled(it) }).flatMap { it.categories }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
        return apps.filter { a -> !isInstalled(a) && a.pkg !in wishlist && a.categories.any { it in favCats } }.sortedByDescending { it.updated }.take(20)
    }
    val newest get() = apps.sortedByDescending { it.added }
    val recentlyUpdated get() = apps.sortedByDescending { it.updated }
    val categories get() = apps.flatMap { it.categories }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    fun search(q: String) = q.trim().lowercase().let { s ->
        if (s.isEmpty()) emptyList() else apps.filter { it.name.lowercase().contains(s) || it.pkg.contains(s) || it.summary.lowercase().contains(s) }
            .sortedByDescending { it.name.lowercase().startsWith(s) }
    }
}
