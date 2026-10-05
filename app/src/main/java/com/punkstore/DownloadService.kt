package com.punkstore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest

/** İndirmeler sürerken süreci canlı tutar (uygulama kapansa da devam eder); bildirimde indirilen uygulamanın ikonu ve adı görünür. */
class DownloadService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        channel(this)
        val n = build(this, null, "Punk Store", t("İndirme başlıyor…", "Starting download…"), -1)
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(2, n)
        return START_NOT_STICKY
    }

    companion object {
        private val icons = mutableMapOf<String, Bitmap>()
        private fun channel(c: Context) { c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads", t("İndirmeler", "Downloads"), NotificationManager.IMPORTANCE_LOW)) }
        private fun build(c: Context, pkg: String?, title: String, text: String, pct: Int): Notification {
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Builder(c, "downloads").setSmallIcon(android.R.drawable.stat_sys_download).setLargeIcon(pkg?.let { icons[it] })
                .setContentTitle(title).setContentText(text).setSubText("Punk Store")
                .setProgress(100, pct.coerceAtLeast(0), pct < 0).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build()
        }
        private fun canNotify(c: Context) = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        private var last = -1
        fun start(c: Context) { runCatching { ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) } }
        fun stop(c: Context) { c.stopService(Intent(c, DownloadService::class.java)); last = -1 }

        /** Uygulamanın ikonunu bildirim için bir kez yükler (katalog görseli ya da kuruluysa sistem ikonu). */
        suspend fun loadIcon(c: Context, a: AppItem) {
            runCatching {
                val bmp = if (a.icon != null) (coil.Coil.imageLoader(c).execute(ImageRequest.Builder(c).data(a.icon).size(128).allowHardware(false).build()).drawable?.toBitmap(128, 128))
                          else c.packageManager.getApplicationIcon(a.pkg).toBitmap(128, 128)
                if (bmp != null) icons[a.pkg] = bmp
            }
        }

        fun progress(c: Context, pkg: String, name: String, pct: Int) {
            if (pct == last || !canNotify(c)) return; last = pct
            c.getSystemService(NotificationManager::class.java).notify(2, build(c, pkg, name, t("İndiriliyor  %$pct", "Downloading  $pct%"), pct))
        }
        /** Bitti (err == null) ya da hata bildirimi */
        fun done(c: Context, pkg: String, name: String, err: String?) {
            if (!canNotify(c)) return
            channel(c)
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), NotificationCompat.Builder(c, "downloads")
                .setSmallIcon(if (err == null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error).setLargeIcon(icons[pkg])
                .setContentTitle(name).setSubText("Punk Store")
                .setContentText(if (err == null) t("İndirildi, kuruluyor…", "Downloaded, installing…") else t("Hata: ", "Error: ") + err).setAutoCancel(true).setContentIntent(pi).build())
            icons.remove(pkg)
        }
    }
}
