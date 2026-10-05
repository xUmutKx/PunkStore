package com.punkstore

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

enum class InstallMethod { SESSION, NATIVE, ROOT }

/** Aurora Store tarzı ayarlar (SharedPreferences "punk"). */
object Cfg {
    private fun sp(c: Context) = c.getSharedPreferences("punk", Context.MODE_PRIVATE)
    fun method(c: Context) = runCatching { InstallMethod.valueOf(sp(c).getString("method", "SESSION")!!) }.getOrDefault(InstallMethod.SESSION)
    fun deleteApk(c: Context) = sp(c).getBoolean("delApk", true)
    fun wifiOnly(c: Context) = sp(c).getBoolean("wifiOnly", false)
    fun wifiOk(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

object Installer {
    // İndirme: Downloads.kt (DownloadQueue — kuyruk, devam ettirme, doğrulama)

    fun canInstall(c: Context) = c.packageManager.canRequestPackageInstalls()

    fun askPermission(c: Context) {
        runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun install(c: Context, apk: File) = installMany(c, listOf(apk))

    /** Kurulum yöntemi: SESSION (PackageInstaller), NATIVE (sistem kurucusu), ROOT (su + pm). */
    fun installMany(c: Context, apks: List<File>, pkg: String? = null) {
        when (Cfg.method(c)) {
            InstallMethod.ROOT -> {
                // Root başarısız olursa kurulum elle (sistem kurucusu) devam eder; APK silinmez
                try { installRoot(c, apks) } catch (e: Throwable) { sessionInstall(c, apks, pkg) }
            }
            InstallMethod.NATIVE -> {
                val apk = apks.firstOrNull() ?: return
                val uri = androidx.core.content.FileProvider.getUriForFile(c, "com.punkstore.app.files", apk)
                c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                return
            }
            else -> sessionInstall(c, apks, pkg)
        }
        // APK'lar indirme kuyruğu tarafından kurulum BAŞARILI olunca silinir (iptal/hata durumunda yeniden indirmeden tekrar denenebilsin)
    }

    private fun sessionInstall(c: Context, apks: List<File>, pkg: String?) {
        val pi = c.packageManager.packageInstaller
        val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        pi.openSession(id).use { s ->
            apks.forEachIndexed { n, apk -> apk.inputStream().use { i -> s.openWrite("$n.apk", 0, apk.length()).use { o -> i.copyTo(o); s.fsync(o) } } }
            val pend = PendingIntent.getBroadcast(c, id, Intent(c, InstallReceiver::class.java).putExtra("pkg", pkg), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            s.commit(pend.intentSender)
        }
    }

    // ---- ROOT ----
    /** su ile komut çalıştırır; başarısızsa fırlatır, çıktıyı döndürür. */
    fun su(vararg cmds: String): String {
        val p = ProcessBuilder("su").redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { w -> cmds.forEach { w.write(it + "\n") }; w.write("exit\n") }
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        check(code == 0) { t("Root komutu başarısız: ", "Root command failed: ") + out.trim().take(200) }
        return out
    }

    fun hasRoot(): Boolean = runCatching { su("id").contains("uid=0") }.getOrDefault(false)

    private fun installRoot(c: Context, apks: List<File>) {
        val total = apks.sumOf { it.length() }
        val out = su("pm install-create -r -d -S $total")
        val id = Regex("\\[(\\d+)]").find(out)?.groupValues?.get(1) ?: error(t("Root oturumu açılamadı: ", "Could not open root session: ") + out.trim())
        apks.forEachIndexed { n, f -> su("pm install-write -S ${f.length()} $id $n.apk '${f.absolutePath}'") }
        val res = su("pm install-commit $id")
        check(res.contains("Success", true)) { res.trim() }
    }

    fun uninstall(c: Context, pkg: String) {
        if (Cfg.method(c) == InstallMethod.ROOT) { su("pm uninstall $pkg"); return }
        c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(c: Context, pkg: String) {
        runCatching { c.packageManager.getLaunchIntentForPackage(pkg)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(c: Context, i: Intent) {
        val pkg = i.getStringExtra("pkg") ?: i.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        when (val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> pkg?.let { InstallBus.result(it, true, null) }
            else -> pkg?.let { InstallBus.result(it, false, when (st) {
                PackageInstaller.STATUS_FAILURE_ABORTED -> t("Kurulum iptal edildi", "Install cancelled")
                PackageInstaller.STATUS_FAILURE_STORAGE -> t("Yetersiz depolama", "Not enough storage")
                PackageInstaller.STATUS_FAILURE_CONFLICT -> t("Kurulu sürümle imza çakışması (önce kaldırın)", "Signature conflict with installed version (uninstall first)")
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> t("Cihazla uyumsuz", "Incompatible with this device")
                else -> i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: t("Kurulum başarısız", "Install failed")
            }) }
        }
    }
}
