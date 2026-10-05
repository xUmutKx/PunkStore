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
        running = this
        val n = build(this, null, "Punk Store", t("İndirmeler sürüyor", "Downloads in progress"), -1)
        // startForegroundService() çağrısı her durumda startForeground() ile karşılanmalı; yoksa sistem uygulamayı kapatır
        try { if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(2, n) } catch (e: Throwable) { }
        if (wantStop) halt()
        return START_NOT_STICKY
    }

    override fun onDestroy() { if (running === this) running = null; super.onDestroy() }
    private fun halt() { runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }; stopSelf() }

    companion object {
        @Volatile private var running: DownloadService? = null
        @Volatile private var wantStop = false
        private val icons = mutableMapOf<String, Bitmap>()
        private fun channel(c: Context) { c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads", t("İndirmeler", "Downloads"), NotificationManager.IMPORTANCE_LOW)) }
        private fun build(c: Context, pkg: String?, title: String, text: String, pct: Int): Notification {
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Builder(c, "downloads").setSmallIcon(android.R.drawable.stat_sys_download).setLargeIcon(pkg?.let { icons[it] })
                .setContentTitle(title).setContentText(text).setSubText("Punk Store")
                .setProgress(100, pct.coerceAtLeast(0), pct < 0).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build()
        }
        private fun canNotify(c: Context) = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        fun start(c: Context) { wantStop = false; runCatching { ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) } }
        /** Servis henüz açılmadıysa onStartCommand önce startForeground'u çağırır, sonra kendini kapatır (anında stopService çökmesini önler). */
        fun stop(c: Context) { wantStop = true; running?.let { r -> runCatching { r.halt() } } }

        /** Uygulamanın ikonunu bildirim için bir kez yükler (katalog görseli ya da kuruluysa sistem ikonu). */
        suspend fun loadIcon(c: Context, a: AppItem) {
            runCatching {
                val bmp = if (a.icon != null) (coil.Coil.imageLoader(c).execute(ImageRequest.Builder(c).data(a.icon).size(128).allowHardware(false).build()).drawable?.toBitmap(128, 128))
                          else c.packageManager.getApplicationIcon(a.pkg).toBitmap(128, 128)
                if (bmp != null) icons[a.pkg] = bmp
            }
        }

        private fun action(c: Context, act: String, pkg: String) = PendingIntent.getBroadcast(c, (act + pkg).hashCode(), Intent(c, DlActionReceiver::class.java).setAction(act).putExtra("pkg", pkg), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private val lastPct = mutableMapOf<String, Int>()

        /** Uygulama başına ilerleme bildirimi: yüzde, hız, kalan süre + Duraklat / İptal. */
        fun progress(c: Context, pkg: String, name: String, pct: Int, speed: Long = 0, eta: Long = -1) {
            if (!canNotify(c) || lastPct[pkg] == pct && speed == 0L) return; lastPct[pkg] = pct
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val txt = buildString { append("%$pct"); if (speed > 0) append("  ·  ${sizeText(speed)}/s"); if (eta >= 0) append("  ·  ${etaText(eta)}") }
            c.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), NotificationCompat.Builder(c, "downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download).setLargeIcon(icons[pkg]).setContentTitle(name).setContentText(txt).setSubText("Punk Store")
                .setProgress(100, pct.coerceIn(0, 100), false).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(pi)
                .addAction(0, t("Duraklat", "Pause"), action(c, "punk.PAUSE", pkg)).addAction(0, t("İptal", "Cancel"), action(c, "punk.CANCEL", pkg)).build())
        }
        fun clear(c: Context, pkg: String) { lastPct.remove(pkg); c.getSystemService(NotificationManager::class.java).cancel(pkg.hashCode()) }
        /** Doğrulama / kurulum aşaması: belirsiz ilerleme çubuklu bildirim */
        fun installing(c: Context, pkg: String, name: String) {
            if (!canNotify(c)) return; channel(c); lastPct.remove(pkg)
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), NotificationCompat.Builder(c, "downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download).setLargeIcon(icons[pkg]).setContentTitle(name).setSubText("Punk Store")
                .setContentText(t("Kuruluyor…", "Installing…")).setProgress(0, 0, true).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(pi).build())
        }
        /** Bitti (err == null) ya da hata bildirimi */
        fun done(c: Context, pkg: String, name: String, err: String?, okText: String? = null) {
            lastPct.remove(pkg)
            if (!canNotify(c)) return
            channel(c)
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), NotificationCompat.Builder(c, "downloads")
                .setSmallIcon(if (err == null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error).setLargeIcon(icons[pkg])
                .setContentTitle(name).setSubText("Punk Store")
                .setContentText(if (err == null) okText ?: t("Kuruldu ✓", "Installed ✓") else t("Hata: ", "Error: ") + err).setAutoCancel(true).setContentIntent(pi).build())
            icons.remove(pkg)
        }
    }
}
