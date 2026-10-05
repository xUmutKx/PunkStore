package com.punkstore

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.GZIPInputStream

val http: OkHttpClient = OkHttpClient.Builder().build()
val json = Json { ignoreUnknownKeys = true; isLenient = true }

const val FDROID = "https://f-droid.org/repo"

/** F-Droid deposu: index-v2'yi indirir, ince bir önbelleğe çevirir. */
object FdroidRepo {
    private fun cacheFile(c: Context) = File(c.filesDir, "fdroid-slim2.json")

    fun cached(c: Context): List<AppItem>? = runCatching {
        val f = cacheFile(c)
        if (!f.exists()) null else json.decodeFromString(ListSerializer(AppItem.serializer()), f.readText())
    }.getOrNull()

    fun cacheAgeMs(c: Context): Long = cacheFile(c).let { if (it.exists()) System.currentTimeMillis() - it.lastModified() else Long.MAX_VALUE }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun refresh(c: Context, onProgress: (Float) -> Unit): List<AppItem> = withContext(Dispatchers.IO) {
        val tmp = File(c.cacheDir, "index-v2.json.gz")
        val req = Request.Builder().url("$FDROID/index-v2.json").header("Accept-Encoding", "identity").build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { t("F-Droid indeksi alınamadı: ${r.code}", "Could not fetch F-Droid index: ${r.code}") }
            val body = r.body!!
            val total = body.contentLength().coerceAtLeast(1)
            var read = 0L
            tmp.outputStream().use { o ->
                body.byteStream().use { i ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); read += n
                        onProgress((read.toFloat() / total).coerceAtMost(0.9f))
                    }
                }
            }
        }
        onProgress(0.92f)
        val isGz = tmp.inputStream().use { it.read() == 0x1f && it.read() == 0x8b }
        val idx: IndexV2 = tmp.inputStream().buffered().let { raw ->
            (if (isGz) GZIPInputStream(raw) else raw).use { json.decodeFromStream(IndexV2.serializer(), it) }
        }
        tmp.delete()
        val list = idx.packages.mapNotNull { (pkg, p) -> toItem(pkg, p) }
        cacheFile(c).writeText(json.encodeToString(ListSerializer(AppItem.serializer()), list))
        onProgress(1f)
        list
    }

    private fun <T> Map<String, T>.loc(): T? =
        this["tr"] ?: this["tr-TR"] ?: this["en-US"] ?: this["en"] ?: values.firstOrNull()

    private fun toItem(pkg: String, p: IndexPkg): AppItem? {
        val m = p.metadata
        val best = p.versions.values.maxByOrNull { it.manifest.versionCode } ?: return null
        val name = m.name.loc() ?: return null
        fun pick(x: Map<String, List<IndexFile>>) = x["tr"] ?: x["en-US"] ?: x["en"] ?: x.values.firstOrNull() ?: emptyList()
        val shots = (pick(m.screenshots.phone) + pick(m.screenshots.sevenInch) + pick(m.screenshots.tenInch)).map { FDROID + it.name }.distinct().take(12)
        return AppItem(
            pkg = pkg, name = name,
            summary = m.summary.loc().orEmpty(),
            description = m.description.loc().orEmpty(),
            icon = m.icon.loc()?.name?.let { FDROID + it },
            categories = m.categories, added = m.added, updated = m.lastUpdated, license = m.license,
            apkUrl = FDROID + best.file.name, apkSize = best.file.size, apkSha256 = best.file.sha256,
            versionName = best.manifest.versionName, versionCode = best.manifest.versionCode,
            ads = "Ads" in best.antiFeatures.keys, tracking = "Tracking" in best.antiFeatures.keys, nonFree = best.antiFeatures.keys.any { it.startsWith("NonFree") }, banner = m.featureGraphic.loc()?.name?.let { FDROID + it }.orEmpty(), developer = m.authorName, tags = m.categories, screenshots = shots, web = m.webSite.ifBlank { m.sourceCode },
        )
    }
}
