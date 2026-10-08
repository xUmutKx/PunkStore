package com.punkstore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class DlState { QUEUED, DOWNLOADING, PAUSED, VERIFYING, INSTALLING, DONE, FAILED }

/** Tek bir indirme işinin anlık durumu (Compose bu nesneyi izler; her değişimde yeni kopya yazılır). */
data class DlTask(
    val pkg: String,
    val name: String,
    val icon: String? = null,
    val source: String = "FDROID",
    val state: DlState = DlState.QUEUED,
    val bytes: Long = 0,
    val total: Long = 0,
    val speed: Long = 0,
    val error: String? = null,
    val attempt: Int = 0,
    val created: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    val active: Boolean get() = state == DlState.QUEUED || state == DlState.DOWNLOADING || state == DlState.VERIFYING || state == DlState.INSTALLING
    /** Kalan süre (sn); hız bilinmiyorsa -1 */
    val eta: Long get() = if (speed > 0 && total > bytes) (total - bytes) / speed else -1
}

/** İndirilecek tek dosya (F-Droid'de 1, Play'de base + split'ler). */
class DlFile(val url: String, val name: String, val size: Long, val sha256: String)

/**
 * Uzun ömürlü indirme kuyruğu:
 * - aynı anda en fazla 2 uygulama, diğerleri sırada bekler
 * - `.part` dosyası + HTTP Range ile kaldığı yerden devam (duraklat / ağ kopması / uygulama kapanması)
 * - ağ hatasında üstel bekleme ile 4 kez yeniden deneme
 * - boş alan denetimi, boyut + SHA-256 doğrulaması
 * - kurulum sonucu (başarılı / hata) PackageInstaller'dan geri okunur
 * - yarım kalan işler kalıcı; sonraki açılışta "Duraklatıldı" olarak geri gelir
 */
class DownloadQueue(private val s: Store) {
    val tasks = mutableStateMapOf<String, DlTask>()
    private val jobs = mutableMapOf<String, Job>()
    private val cancelled = mutableSetOf<String>()
    private val sem = Semaphore(2)
    private val ctx: Context get() = s.getApplication()
    private val prefs get() = ctx.getSharedPreferences("punk", Context.MODE_PRIVATE)

    init { instance = this }

    operator fun get(pkg: String) = tasks[pkg]
    val activeCount: Int get() = tasks.values.count { it.active }

    private fun put(t: DlTask) {
        tasks[t.pkg] = t
        if (t.active) s.busy[t.pkg] = if (t.state == DlState.INSTALLING || t.state == DlState.VERIFYING) -1f else t.progress else s.busy.remove(t.pkg)
    }
    private fun upd(pkg: String, f: (DlTask) -> DlTask) { if (synchronized(cancelled) { pkg in cancelled }) return; tasks[pkg]?.let { put(f(it)) } }

    /** Kuyruğa al (zaten sürüyorsa bir şey yapmaz; duraklatılmış / hatalıysa devam ettirir). */
    fun enqueue(a: AppItem) {
        val cur = tasks[a.pkg]
        if (cur != null && cur.active) return
        synchronized(cancelled) { cancelled.remove(a.pkg) }
        put(DlTask(a.pkg, a.name, a.icon, a.source, DlState.QUEUED, cur?.bytes ?: 0, cur?.total ?: a.apkSize))
        persist()
        runCatching { DownloadService.start(ctx) }
        s.viewModelScope.launch { DownloadService.loadIcon(ctx, a) }
        jobs[a.pkg] = s.viewModelScope.launch {
            try {
                sem.withPermit { run(a) }
            } catch (e: CancellationException) {
                // duraklatma / iptal: durum pause()/cancel() içinde ayarlandı
            } catch (e: Throwable) {
                if (synchronized(cancelled) { cancelled.remove(a.pkg) }) return@launch
                val msg = friendly(e)
                upd(a.pkg) { it.copy(state = DlState.FAILED, error = msg, speed = 0) }
                s.report("${a.name}: $msg")
                DownloadService.done(ctx, a.pkg, a.name, msg)
            } finally {
                jobs.remove(a.pkg); persist()
                if (activeCount == 0) DownloadService.stop(ctx)
            }
        }
    }

    fun pause(pkg: String) {
        val t = tasks[pkg] ?: return
        if (t.state != DlState.DOWNLOADING && t.state != DlState.QUEUED) return
        put(t.copy(state = DlState.PAUSED, speed = 0)); jobs[pkg]?.cancel(); persist()
        DownloadService.clear(ctx, pkg)
    }

    fun resume(pkg: String) { s.resolve(pkg)?.let { enqueue(it) } ?: tasks.remove(pkg) }

    /** İptal: işi durdurur, yarım dosyaları siler, listeden kaldırır. */
    fun cancel(pkg: String) {
        synchronized(cancelled) { cancelled.add(pkg) }
        runCatching { jobs[pkg]?.cancel(); tasks.remove(pkg); s.busy.remove(pkg); persist() }
        // dosya silme + bildirim temizliği ana iş parçacığını tutmasın, hata verirse uygulamayı düşürmesin
        s.viewModelScope.launch(Dispatchers.IO) {
            delay(300) // indirme iş parçacığı bağlantıyı bıraksın
            runCatching { File(ctx.cacheDir, "apk/$pkg").deleteRecursively() }
            synchronized(cancelled) { cancelled.remove(pkg) }
        }
        runCatching { DownloadService.clear(ctx, pkg) }
        if (activeCount == 0) DownloadService.stop(ctx)
    }

    fun pauseAll() = tasks.keys.toList().forEach { pause(it) }
    fun resumeAll() = tasks.values.filter { it.state == DlState.PAUSED || it.state == DlState.FAILED }.forEach { resume(it.pkg) }
    fun clearFinished() = tasks.values.filter { it.state == DlState.DONE || it.state == DlState.FAILED }.forEach { tasks.remove(it.pkg) }

    // ---------------- iş akışı ----------------
    private suspend fun run(a: AppItem) {
        upd(a.pkg) { it.copy(state = DlState.DOWNLOADING, error = null) }
        DownloadService.progress(ctx, a.pkg, a.name, 0)
        val files = withContext(Dispatchers.IO) { resolveFiles(a) }
        val total = files.sumOf { it.size }.takeIf { it > 0 } ?: a.apkSize
        val dir = File(ctx.cacheDir, "apk/${a.pkg}/${a.versionCode}").apply { mkdirs() }
        // eski sürümlerin yarım dosyaları
        dir.parentFile?.listFiles()?.filter { it != dir }?.forEach { it.deleteRecursively() }
        val have = files.withIndex().sumOf { (i, f) -> fileFor(dir, i, f).let { if (it.exists()) it.length() else File(it.path + ".part").length() } }
        check(dir.usableSpace > (total - have) * 2 + 20_000_000L) { t("Yeterli boş alan yok (${sizeText(total * 2)} gerekiyor)", "Not enough free space (${sizeText(total * 2)} needed)") }
        upd(a.pkg) { it.copy(total = total, bytes = have) }

        // hız ölçümü (üstel ortalama) + bildirimi seyrek güncelle
        var lastT = System.currentTimeMillis(); var lastB = have; var speed = 0.0; var lastUi = 0L
        var doneBefore = 0L
        val out = files.mapIndexed { i, f ->
            val dest = fileFor(dir, i, f)
            fetch(f.url, dest, f.size, onRetry = { n -> upd(a.pkg) { it.copy(attempt = n) } }) { b ->
                val now = System.currentTimeMillis(); val cur = doneBefore + b
                if (now - lastT >= 500) { val inst = (cur - lastB) * 1000.0 / (now - lastT); speed = if (speed == 0.0) inst else speed * .7 + inst * .3; lastT = now; lastB = cur }
                if (now - lastUi >= 120 || cur >= total) {
                    lastUi = now
                    upd(a.pkg) { it.copy(bytes = cur, speed = speed.toLong(), attempt = 0) }
                    DownloadService.progress(ctx, a.pkg, a.name, (cur * 100 / total.coerceAtLeast(1)).toInt(), speed.toLong(), tasks[a.pkg]?.eta ?: -1)
                }
            }
            doneBefore += dest.length()
            dest
        }

        upd(a.pkg) { it.copy(state = DlState.VERIFYING, bytes = total, speed = 0) }
        withContext(Dispatchers.IO) {
            files.zip(out).forEach { (f, file) ->
                if (f.size > 0 && file.length() != f.size) { file.delete(); error(t("Dosya eksik indi, tekrar deneyin", "File is incomplete, try again")) }
                if (!verify(file, f.sha256)) { file.delete(); error(t("Doğrulama başarısız (SHA-256 uyuşmuyor)", "Verification failed (SHA-256 mismatch)")) }
            }
        }

        check(abiOk(out)) { noAbi() }
        // GitHub sürümlerinde paket adı bilinmez: indirilen APK'dan okunup eşlenir (kurulu durumu için)
        if (a.pkg.startsWith("gh:")) runCatching { ctx.packageManager.getPackageArchiveInfo(out.first().path, 0)?.packageName }.getOrNull()?.let { s.setGhReal(a.pkg, it) }
        upd(a.pkg) { it.copy(state = DlState.INSTALLING) }
        DownloadService.installing(ctx, a.pkg, a.name)
        val wait = InstallBus.expect(a.pkg)
        withContext(Dispatchers.IO) { Installer.installMany(ctx, out, a.pkg) }
        val res: String? = when (Cfg.method(ctx)) {
            InstallMethod.SESSION -> withTimeoutOrNull(15 * 60_000L) { wait.await() } ?: ""   // "" = sonuç gelmedi, sorun sayma
            else -> ""
        }
        InstallBus.forget(a.pkg)
        if (res != null && res.isNotEmpty() && res != InstallBus.OK) error(res)
        if (Cfg.deleteApk(ctx)) dir.parentFile?.deleteRecursively()
        tasks[a.pkg]?.let { put(it.copy(state = DlState.DONE, speed = 0)) }
        s.onInstalled(a)
        DownloadService.done(ctx, a.pkg, a.name, null)
        delay(4000)
        if (tasks[a.pkg]?.state == DlState.DONE) tasks.remove(a.pkg)
    }

    private fun fileFor(dir: File, i: Int, f: DlFile) = File(dir, "$i-${f.name.ifBlank { "base" }.replace('/', '_').removeSuffix(".apk")}.apk")

    private suspend fun resolveFiles(a: AppItem): List<DlFile> = if (a.source == "PLAY") {
        val abis = android.os.Build.SUPPORTED_ABIS.map { it.replace('-', '_') }
        val abiRe = Regex("(?<![a-z0-9])(arm64_v8a|armeabi_v7a|x86_64|x86)(?![a-z0-9])", RegexOption.IGNORE_CASE)
        val l = PlayRepo.files(ctx, a)
            .filter { it.type == com.aurora.gplayapi.data.models.PlayFile.Type.BASE || it.type == com.aurora.gplayapi.data.models.PlayFile.Type.SPLIT }
            .filter { f -> val m = abiRe.find(f.name)?.value?.lowercase(); m == null || m in abis }
        check(l.isNotEmpty()) { t("İndirilebilir dosya yok (ücretli ya da bölgeye kapalı olabilir)", "Nothing to download (may be paid or region-locked)") }
        l.map { DlFile(it.url, it.name, it.size, it.sha256) }
    } else {
        // an entry from an awesome list only names a GitHub repo: its latest release is looked up now (one request), not for every entry up front
        var item = a
        if (item.apkUrl.isBlank() && item.pkg.startsWith("gh:")) item = GitHubRepo.item(item.pkg.removePrefix("gh:"), item.categories.firstOrNull().orEmpty()) ?: item
        check(item.apkUrl.isNotBlank()) { t("Bu depoda APK içeren bir sürüm yok", "This repository has no release with an APK") }
        listOf(DlFile(item.apkUrl, "base", item.apkSize, item.apkSha256))
    }

    /** Kalıcılık: yarım işlerin paket adları */
    private fun persist() = prefs.edit().putString("dlq", tasks.values.filter { it.state != DlState.DONE }.joinToString(",") { it.pkg }).apply()

    /** Açılışta: önceki oturumdan kalan işler "duraklatıldı" olarak listeye döner (otomatik inmez, kota yemez). */
    fun restore() {
        (prefs.getString("dlq", "") ?: "").split(',').filter { it.isNotBlank() && it !in tasks }.forEach { p ->
            val a = s.resolve(p) ?: return@forEach
            val part = File(ctx.cacheDir, "apk/$p").walk().filter { it.isFile }.sumOf { it.length() }
            if (part > 0) put(DlTask(p, a.name, a.icon, a.source, DlState.PAUSED, part, a.apkSize))
        }
    }

    companion object {
        var instance: DownloadQueue? = null

        fun friendly(e: Throwable): String = when (e) {
            is java.net.UnknownHostException -> t("İnternet bağlantısı yok", "No internet connection")
            is java.net.SocketTimeoutException -> t("Bağlantı zaman aşımına uğradı", "Connection timed out")
            is IOException -> t("Ağ hatası: ", "Network error: ") + (e.message ?: e.javaClass.simpleName)
            else -> e.message?.let { m ->
                if (m.contains("NO_MATCHING_ABI", true) || m.contains("-113")) noAbi() else m
            } ?: e.javaClass.simpleName
        }

        fun noAbi(): String = t("Bu uygulamanın cihazının işlemcisi (${android.os.Build.SUPPORTED_ABIS.firstOrNull()}) için derlemesi yok", "This app has no build for your device's CPU (${android.os.Build.SUPPORTED_ABIS.firstOrNull()})")

        /** APK'ların içindeki lib/<abi> klasörlerine bakar; yerel kütüphane var ama cihaz ABI'si yoksa false. */
        fun abiOk(files: List<File>): Boolean {
            val found = mutableSetOf<String>()
            files.forEach { f -> runCatching { java.util.zip.ZipFile(f).use { z -> z.entries().asSequence().forEach { en -> if (en.name.startsWith("lib/") && en.name.count { it == '/' } >= 2) found.add(en.name.split('/')[1]) } } } }
            return found.isEmpty() || found.any { it in android.os.Build.SUPPORTED_ABIS }
        }

        /** sha256 hex ya da base64 (Play) olabilir; tanınmayan biçimde doğrulama atlanır. */
        fun verify(f: File, want: String): Boolean {
            val w = want.trim(); if (w.isEmpty()) return true
            val wantBytes: ByteArray = when {
                w.length == 64 && w.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> ByteArray(32) { w.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                else -> runCatching { Base64.decode(w, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()?.takeIf { it.size == 32 } ?: return true
            }
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i -> val b = ByteArray(256 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().contentEquals(wantBytes)
        }

        /**
         * Tek dosyayı `.part` üzerinden indirir; varsa kaldığı yerden (Range) devam eder.
         * Ağ hatasında 1-2-4-8 sn bekleyerek 4 kez yeniden dener. İptal edilirse bağlantıyı hemen keser.
         */
        suspend fun fetch(url: String, dest: File, expect: Long, onRetry: (Int) -> Unit = {}, onBytes: (Long) -> Unit) = withContext(Dispatchers.IO) {
            if (dest.exists() && (expect <= 0 || dest.length() == expect)) { onBytes(dest.length()); return@withContext }
            dest.delete()
            val part = File(dest.path + ".part")
            var attempt = 0
            while (true) {
                coroutineContext.ensureActive()
                try { once(url, part, expect, onBytes); break }
                catch (e: CancellationException) { throw e }
                catch (e: IOException) {
                    if (++attempt > 4) throw e
                    onRetry(attempt); delay(1000L shl (attempt - 1))
                }
            }
            check(part.renameTo(dest)) { "rename failed" }
        }

        private suspend fun once(url: String, part: File, expect: Long, onBytes: (Long) -> Unit) {
            var have = if (part.exists()) part.length() else 0L
            if (expect > 0 && have > expect) { part.delete(); have = 0 }
            if (expect > 0 && have == expect) { onBytes(have); return }
            val rb = Request.Builder().url(url).header("Accept-Encoding", "identity")
            if (have > 0) rb.header("Range", "bytes=$have-")
            val call = http.newCall(rb.build())
            val job = coroutineContext[Job]
            val h = job?.invokeOnCompletion { if (it != null) call.cancel() }
            try {
                call.execute().use { r ->
                    when {
                        r.code == 416 -> { part.delete(); throw IOException("HTTP 416") }
                        r.code == 206 -> {}
                        r.isSuccessful -> have = 0 // sunucu devam etmeyi desteklemiyor: baştan
                        r.code == 429 || r.code >= 500 -> throw IOException("HTTP ${r.code}")
                        else -> error(t("İndirilemedi: HTTP ${r.code}", "Download failed: HTTP ${r.code}"))
                    }
                    FileOutputStream(part, have > 0).use { o ->
                        r.body!!.byteStream().use { i ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = i.read(buf); if (n < 0) break
                                o.write(buf, 0, n); have += n; onBytes(have)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                coroutineContext.ensureActive(); throw e   // iptalden kaynaklanan IOException'ı iptal olarak yay
            } finally { h?.dispose() }
            if (expect > 0 && have < expect) throw IOException(t("Bağlantı yarıda kesildi", "Connection dropped"))
        }
    }
}

/** PackageInstaller oturumlarının sonucunu bekleyen indirme işine iletir. */
object InstallBus {
    const val OK = "\u0000ok"
    private val waits = mutableMapOf<String, CompletableDeferred<String>>()
    @Synchronized fun expect(pkg: String) = CompletableDeferred<String>().also { waits[pkg] = it }
    @Synchronized fun forget(pkg: String) { waits.remove(pkg) }
    @Synchronized fun result(pkg: String, ok: Boolean, msg: String?) { waits[pkg]?.complete(if (ok) OK else msg?.ifBlank { null } ?: t("Kurulum başarısız", "Install failed")) }
}

/** Bildirimdeki "Duraklat" / "İptal" düğmeleri. */
class DlActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pkg = i.getStringExtra("pkg") ?: return
        val q = DownloadQueue.instance ?: run { DownloadService.clear(c, pkg); return }
        when (i.action) { "punk.PAUSE" -> q.pause(pkg); "punk.CANCEL" -> q.cancel(pkg) }
    }
}
