package vn.navara.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ===== Cập nhật ứng dụng trực tuyến từ GitHub Releases của dự án =====
// Cách phát hành bản mới: trên GitHub tạo Release có tag lớn hơn bản đang cài (ví dụ v0.10) và đính kèm 1 file .apk.
// App so sánh tag với versionName, tải file .apk về bộ nhớ đệm rồi gọi trình cài đặt của Android.
// Lưu ý: bản mới phải ký cùng khoá với bản đang cài thì Android mới cho cập nhật đè.

enum class UpdPhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

data class UpdState(
    val phase: UpdPhase = UpdPhase.IDLE,
    val latest: String = "",        // phiên bản mới nhất trên GitHub
    val notes: String = "",         // ghi chú phát hành
    val apkUrl: String = "",        // liên kết tải file .apk (rỗng nếu release không có file .apk)
    val pageUrl: String = REPO_URL, // trang release để mở bằng trình duyệt
    val progress: Int = 0,          // % tải
    val error: String = "",
)

object Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdState())
    val state: StateFlow<UpdState> = _state
    private var job: Job? = null

    fun isNewer(latest: String, current: String): Boolean = isNewerVersion(latest, current)

    // Kiểm tra file .apk đã tải: đúng package, versionCode mới hơn bản đang cài và cùng chữ ký với bản đang cài.
    // Trả về null nếu hợp lệ, ngược lại trả về lý do (tiếng Việt) để hiển thị cho người dùng.
    @Suppress("DEPRECATION")
    private fun validateApk(ctx: Context, f: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                    else android.content.pm.PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return "File tải về không phải gói APK hợp lệ"
        if (archive.packageName != ctx.packageName) return "File tải về không đúng ứng dụng (${archive.packageName})"
        val cur = pm.getPackageInfo(ctx.packageName, flags)
        val curCode = if (Build.VERSION.SDK_INT >= 28) cur.longVersionCode else cur.versionCode.toLong()
        val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (newCode <= curCode) return "Bản tải về không mới hơn bản đang cài"
        fun sigs(i: android.content.pm.PackageInfo): Set<String> {
            val arr = if (Build.VERSION.SDK_INT >= 28) i.signingInfo?.apkContentsSigners else i.signatures
            return arr?.map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }?.toSet() ?: emptySet()
        }
        val a = sigs(archive)
        if (a.isEmpty() || a != sigs(cur)) return "Chữ ký bản tải về khác bản đang cài, Android sẽ không cho cập nhật đè"
        return null
    }

    fun check() {
        val ph = _state.value.phase
        if (ph == UpdPhase.CHECKING || ph == UpdPhase.DOWNLOADING) return
        _state.update { it.copy(phase = UpdPhase.CHECKING, error = "") }
        job = scope.launch {
            try {
                val j = JSONObject(
                    httpGetText("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest", "application/vnd.github+json")
                )
                val tag = j.optString("tag_name")
                if (tag.isBlank()) throw java.io.IOException("Chưa có bản phát hành nào")
                var apk = ""
                val assets = j.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true)) { apk = a.optString("browser_download_url"); break }
                    }
                }
                val page = j.optString("html_url").ifBlank { REPO_URL }
                val notes = j.optString("body").trim()
                if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                    _state.value = UpdState(UpdPhase.AVAILABLE, tag, notes, apk, page)
                } else {
                    _state.value = UpdState(UpdPhase.UP_TO_DATE, tag, notes, apk, page)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = UpdPhase.ERROR, error = errText(e)) }
            }
        }
    }

    private fun errText(e: Exception): String =
        if (e.message?.startsWith("HTTP 404") == true) "Chưa có bản phát hành nào trên GitHub"
        else if (e.message?.startsWith("HTTP 403") == true) "GitHub đang giới hạn lượt truy cập, thử lại sau"
        else e.message ?: "Không kết nối được"

    fun download(ctx: Context) {
        val s = _state.value
        if (s.phase == UpdPhase.DOWNLOADING) return
        if (s.apkUrl.isBlank()) {
            _state.update { it.copy(phase = UpdPhase.ERROR, error = "Bản phát hành không có file .apk, mở trang GitHub để tải") }
            return
        }
        val dir = File(ctx.applicationContext.cacheDir, "updates")
        _state.update { it.copy(phase = UpdPhase.DOWNLOADING, progress = 0, error = "") }
        job = scope.launch {
            try {
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "VL23-Launcher.apk")
                val tmp = File(dir, "download.tmp")
                var expected: Long
                val c = URL(s.apkUrl).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 10000
                    c.readTimeout = 20000
                    c.instanceFollowRedirects = true
                    c.setRequestProperty("User-Agent", APP_UA)
                    if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
                    val total = c.contentLengthLong
                    expected = total
                    var done = 0L
                    var lastPct = -1
                    c.inputStream.use { i ->
                        tmp.outputStream().use { o ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; _state.update { it.copy(progress = pct) } }
                                }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                if (tmp.length() < 100_000L) throw java.io.IOException("File tải về không hợp lệ")
                if (expected > 0 && tmp.length() != expected) throw java.io.IOException("Tải chưa đủ dung lượng, thử lại")
                validateApk(ctx.applicationContext, tmp)?.let { tmp.delete(); throw java.io.IOException(it) }
                if (!tmp.renameTo(out)) throw java.io.IOException("Không lưu được file cập nhật")
                _state.update { it.copy(phase = UpdPhase.READY, progress = 100) }
                install(ctx.applicationContext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = UpdPhase.ERROR, error = "Tải lỗi: " + errText(e)) }
            }
        }
    }

    // Gọi trình cài đặt của Android. Lần đầu hệ thống có thể yêu cầu cho phép "cài ứng dụng không rõ nguồn" cho VL23 Launcher.
    fun install(ctx: Context) {
        val f = File(File(ctx.applicationContext.cacheDir, "updates"), "VL23-Launcher.apk")
        if (!f.exists()) { download(ctx); return }
        try {
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            _state.update { it.copy(phase = UpdPhase.ERROR, error = "Không mở được trình cài đặt, hãy mở trang GitHub để tải thủ công") }
        }
    }
}

// "v0.10.1" -> [0, 10, 1]; tách riêng khỏi Updater để kiểm thử được
internal fun parseVersion(v: String): List<Int> =
    v.trim().removePrefix("v").removePrefix("V").split('.', '-', '_', ' ')
        .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

internal fun isNewerVersion(latest: String, current: String): Boolean {
    val a = parseVersion(latest)
    val b = parseVersion(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
