package com.punkstore

import kotlinx.serialization.Serializable

/** Mağaza kaynağı: F-Droid (FOSS) ya da Google Play (Aurora tarzı). */
enum class Source { FDROID, PLAY }

@Serializable
data class AppItem(
    val pkg: String,
    val name: String,
    val summary: String = "",
    val description: String = "",
    val icon: String? = null,
    val categories: List<String> = emptyList(),
    val added: Long = 0,
    val updated: Long = 0,
    val license: String = "",
    val apkUrl: String = "",
    val apkSize: Long = 0,
    val apkSha256: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val screenshots: List<String> = emptyList(),
    val web: String = "",
    val source: String = "FDROID",
    val offerType: Int = 1,
    val rating: Float = 0f,
    val price: String = "",
    val banner: String = "",
    val developer: String = "",
    val installs: String = "",
    val tags: List<String> = emptyList(),
    val ads: Boolean = false,
    val tracking: Boolean = false,
    val nonFree: Boolean = false,
    val discount: Int = 0,
    val origPrice: String = "",
    val review: String = "",
    val extra: Map<String, String> = emptyMap(),
)

/** Kapak: önce yatay afiş (feature graphic / cover), yoksa ilk ekran görüntüsü. */
val AppItem.cover: String? get() = banner.ifBlank { screenshots.firstOrNull().orEmpty() }.ifBlank { null }

// ---- F-Droid index-v2 (yalnızca ihtiyaç duyulan alanlar) ----
@Serializable
data class IndexV2(val packages: Map<String, IndexPkg> = emptyMap())

@Serializable
data class IndexPkg(val metadata: IndexMeta = IndexMeta(), val versions: Map<String, IndexVer> = emptyMap())

@Serializable
data class IndexMeta(
    val name: Map<String, String> = emptyMap(),
    val summary: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val icon: Map<String, IndexFile> = emptyMap(),
    val categories: List<String> = emptyList(),
    val added: Long = 0,
    val lastUpdated: Long = 0,
    val license: String = "",
    val webSite: String = "",
    val authorName: String = "",
    val featureGraphic: Map<String, IndexFile> = emptyMap(),
    val sourceCode: String = "",
    val screenshots: IndexShots = IndexShots(),
)

@Serializable
data class IndexShots(val phone: Map<String, List<IndexFile>> = emptyMap(), val sevenInch: Map<String, List<IndexFile>> = emptyMap(), val tenInch: Map<String, List<IndexFile>> = emptyMap())

@Serializable
data class IndexFile(val name: String = "")

@Serializable
data class IndexVer(val file: IndexVerFile = IndexVerFile(), val manifest: IndexManifest = IndexManifest(), val antiFeatures: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()))

@Serializable
data class IndexVerFile(val name: String = "", val sha256: String = "", val size: Long = 0)

@Serializable
data class IndexManifest(val versionName: String = "", val versionCode: Long = 0, val nativecode: List<String> = emptyList())

/** Kullanıcı yorumu (Steam / Google Play). up: olumlu mu (Steam); stars: Play yıldızı. */
class UserReview(val author: String, val text: String, val up: Boolean?, val stars: Int, val hours: Int, val votes: Int, val time: Long)
