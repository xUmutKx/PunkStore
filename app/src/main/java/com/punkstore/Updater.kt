package com.punkstore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/** Punk Store'un kendi güncellemesi (GitHub sürümleri) + bildirim. */
object Updater {
    const val RELEASES = "https://api.github.com/repos/xUmutKx/PunkStore/releases/latest"

    private fun ver(s: String) = s.trimStart('v', 'V').split('.', '-').mapNotNull { it.toIntOrNull() }
    private fun newer(a: List<Int>, b: List<Int>): Boolean { for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }; return false }

    /** Yeni sürüm varsa indirilecek APK'yı AppItem olarak döndürür. */
    suspend fun checkSelf(c: Context, installedCode: Long): AppItem? = withContext(Dispatchers.IO) {
        val cur = c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty()
        http.newCall(Request.Builder().url(RELEASES).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val o = json.parseToJsonElement(r.body!!.string()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            if (!newer(ver(tag), ver(cur))) return@withContext null
            val apk = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true } ?: return@withContext null
            AppItem(pkg = c.packageName, name = "Punk Store", summary = t("Yeni sürüm: $tag", "New version: $tag"), versionName = tag, versionCode = installedCode + 1,
                apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content, apkSize = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0)
        }
    }

    fun notify(c: Context, n: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("updates", t("Güncellemeler", "Updates"), NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, NotificationCompat.Builder(c, "updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Punk Store").setContentText(t("$n güncelleme bekliyor", "$n updates available")).setContentIntent(pi).setAutoCancel(true).build())
    }
}
