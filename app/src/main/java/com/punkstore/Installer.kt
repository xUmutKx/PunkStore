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
    /** APK'yı indirir (ilerleme 0..1), sha256 doğrular, dosyayı döndürür. */
    suspend fun download(c: Context, app: AppItem, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(c.cacheDir, "apk").apply { mkdirs() }
        val f = File(dir, "${app.pkg}-${app.versionCode}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(app.apkUrl).build()).execute().use { r ->
            check(r.isSuccessful) { t("İndirilemedi: ${r.code}", "Download failed: ${r.code}") }
            val body = r.body!!
            val total = (if (body.contentLength() > 0) body.contentLength() else app.apkSize).coerceAtLeast(1)
            var read = 0L
            f.outputStream().use { o ->
                body.byteStream().use { i ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); read += n
                        onProgress((read.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }
        if (app.apkSha256.isNotBlank()) {
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(app.apkSha256, true)) { f.delete(); error(t("Doğrulama başarısız (sha256 uyuşmuyor)", "Verification failed (sha256 mismatch)")) }
        }
        f
    }

    /** Google Play dosyalarını (base + split) indirir; toplam ilerleme 0..1. */
    suspend fun downloadPlay(c: Context, pkg: String, files: List<com.aurora.gplayapi.data.models.PlayFile>, onProgress: (Float) -> Unit): List<File> = withContext(Dispatchers.IO) {
        val dir = File(c.cacheDir, "apk/$pkg").apply { deleteRecursively(); mkdirs() }
        val abis = android.os.Build.SUPPORTED_ABIS.map { it.replace('-', '_') }
        val abiRe = Regex("(?<![a-z0-9])(arm64_v8a|armeabi_v7a|x86_64|x86)(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val apks = files.filter { it.type == com.aurora.gplayapi.data.models.PlayFile.Type.BASE || it.type == com.aurora.gplayapi.data.models.PlayFile.Type.SPLIT }
            .filter { f -> val m = abiRe.find(f.name)?.value?.lowercase(); m == null || m in abis }
        check(apks.isNotEmpty()) { t("İndirilebilir dosya yok (ücretli ya da bölgeye kapalı olabilir)", "Nothing to download (may be paid or region-locked)") }
        val total = apks.sumOf { it.size }.coerceAtLeast(1)
        var done = 0L
        apks.mapIndexed { i, pf ->
            val f = File(dir, "${i}-${pf.name.ifBlank { "split.apk" }}".replace('/', '_'))
            http.newCall(Request.Builder().url(pf.url).build()).execute().use { r ->
                check(r.isSuccessful) { t("İndirilemedi: ${r.code}", "Download failed: ${r.code}") }
                f.outputStream().use { o -> r.body!!.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress((done.toFloat() / total).coerceAtMost(1f)) }
                } }
            }
            f
        }
    }

    fun canInstall(c: Context) = c.packageManager.canRequestPackageInstalls()

    fun askPermission(c: Context) {
        c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(c: Context, apk: File) = installMany(c, listOf(apk))

    /** Kurulum yöntemi: SESSION (PackageInstaller), NATIVE (sistem kurucusu), ROOT (su + pm). */
    fun installMany(c: Context, apks: List<File>) {
        when (Cfg.method(c)) {
            InstallMethod.ROOT -> installRoot(c, apks)
            InstallMethod.NATIVE -> {
                val apk = apks.first()
                val uri = androidx.core.content.FileProvider.getUriForFile(c, "com.punkstore.app.files", apk)
                c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                return
            }
            else -> {
                val pi = c.packageManager.packageInstaller
                val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
                pi.openSession(id).use { s ->
                    apks.forEachIndexed { n, apk -> apk.inputStream().use { i -> s.openWrite("$n.apk", 0, apk.length()).use { o -> i.copyTo(o); s.fsync(o) } } }
                    val pend = PendingIntent.getBroadcast(c, id, Intent(c, InstallReceiver::class.java), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    s.commit(pend.intentSender)
                }
            }
        }
        if (Cfg.deleteApk(c)) apks.forEach { it.delete() }
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
        if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
