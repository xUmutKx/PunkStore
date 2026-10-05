package com.punkstore

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Arka planda otomatik güncelleme: JobScheduler ile periyodik çalışır, uygulama kapalıyken de güncellemeleri indirip kurar. */
object AutoUpdate {
    private const val JOB = 4201
    private fun prefs(c: Context) = c.getSharedPreferences("punk", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("bgUpdate", false)

    fun apply(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        if (!enabled(c)) { js.cancel(JOB); return }
        val hours = prefs(c).getInt("intervalH", 3).coerceAtLeast(1)
        js.schedule(JobInfo.Builder(JOB, ComponentName(c, UpdateJobService::class.java))
            .setRequiredNetworkType(if (Cfg.wifiOnly(c)) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true).setPersisted(true)
            .setPeriodic(maxOf(hours * 3600_000L, JobInfo.getMinPeriodMillis())).build())
    }

    /** Katalogla karşılaştırıp kurulu ama eskimiş uygulamaları güncelle. */
    suspend fun run(c: Context) {
        val pm = c.packageManager
        val installed = pm.getInstalledPackages(0).associate { it.packageName to it.longVersionCode }
        val ignored = (prefs(c).getString("ignored", "") ?: "").split(',').toSet()
        val fd = FdroidRepo.cached(c).orEmpty()
        val todo = fd.filter { a -> a.pkg !in ignored && (installed[a.pkg] ?: Long.MAX_VALUE) < a.versionCode }.toMutableList()
        // Play'den kurulanlar (F-Droid'de olmayan, sistem dışı)
        runCatching {
            val rest = installed.keys.filter { k -> fd.none { it.pkg == k } && k !in ignored && !k.startsWith("com.android.") && !k.startsWith("android") && pm.getLaunchIntentForPackage(k) != null }
            rest.chunked(40).forEach { chunk -> PlayRepo.details(c, chunk).filter { (installed[it.pkg] ?: Long.MAX_VALUE) < it.versionCode && it.price.isBlank() }.let { todo.addAll(it) } }
        }
        var done = 0
        todo.take(15).forEach { a -> if (runCatching { update(c, a) }.isSuccess) done++ }
        if (done > 0) DownloadService.done(c, "autoupdate", "Punk Store", null, t("$done uygulama arka planda güncellendi", "$done apps updated in the background"))
    }

    private suspend fun update(c: Context, a: AppItem) {
        val dir = File(c.cacheDir, "auto/${a.pkg}").apply { deleteRecursively(); mkdirs() }
        val files: List<DlFile> = if (a.source == "PLAY") {
            val abis = android.os.Build.SUPPORTED_ABIS.map { it.replace('-', '_') }
            val re = Regex("(?<![a-z0-9])(arm64_v8a|armeabi_v7a|x86_64|x86)(?![a-z0-9])", RegexOption.IGNORE_CASE)
            PlayRepo.files(c, a).filter { it.type == com.aurora.gplayapi.data.models.PlayFile.Type.BASE || it.type == com.aurora.gplayapi.data.models.PlayFile.Type.SPLIT }
                .filter { f -> val m = re.find(f.name)?.value?.lowercase(); m == null || m in abis }.map { DlFile(it.url, it.name, it.size, it.sha256) }
        } else listOf(DlFile(a.apkUrl, "base", a.apkSize, a.apkSha256))
        check(files.isNotEmpty())
        val out = files.mapIndexed { i, f ->
            val dest = File(dir, "$i.apk")
            DownloadQueue.fetch(f.url, dest, f.size) { }
            check(DownloadQueue.verify(dest, f.sha256)) { "sha" }
            dest
        }
        check(DownloadQueue.abiOk(out))
        if (Cfg.method(c) == InstallMethod.ROOT) Installer.installMany(c, out, a.pkg)
        else {
            // Android 12+: kurucu biz isek kullanıcı onayı gerekmeden güncellenir; değilse sistem onay ister
            val pi = c.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (android.os.Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                out.forEachIndexed { n, apk -> apk.inputStream().use { i -> s.openWrite("$n.apk", 0, apk.length()).use { o -> i.copyTo(o); s.fsync(o) } } }
                val pend = android.app.PendingIntent.getBroadcast(c, id, android.content.Intent(c, InstallReceiver::class.java).putExtra("pkg", a.pkg), android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                s.commit(pend.intentSender)
            }
        }
        if (Cfg.deleteApk(c)) dir.deleteRecursively()
    }
}

class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    override fun onStartJob(p: JobParameters): Boolean {
        job = scope.launch { runCatching { AutoUpdate.run(applicationContext) }; jobFinished(p, false) }
        return true
    }
    override fun onStopJob(p: JobParameters): Boolean { job?.cancel(); return true }
}

/** Cihaz açılınca periyodik işi yeniden kurar (setPersisted yeterli; yine de uygulama açılışında apply çağrılır). */
