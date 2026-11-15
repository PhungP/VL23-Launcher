package vn.navara.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import java.text.Normalizer

// ===== Bộ biểu tượng Navara: nét trắng, điểm nhấn cam / xanh, vẽ bằng vector (hệ toạ độ 48x48) =====
// st: 0 nét trắng, 1 nét cam, 2 nét xanh, 3 tô trắng, 4 tô cam, 5 tô xanh
private class El(val d: String, val st: Int, val dash: FloatArray?)

private val GLYPHS: Map<String, List<El>> = mapOf(
    "apk" to listOf(El("M15 21H33A3 3 0 0 1 36 24V35A3 3 0 0 1 33 38H15A3 3 0 0 1 12 35V24A3 3 0 0 1 15 21Z", 0, null), El("M12 27H36", 0, null), El("M24 7V18M19 13l5 5 5-5", 1, null)),  // ApkInstaller
    "autokit" to listOf(El("M19 8H29A4 4 0 0 1 33 12V36A4 4 0 0 1 29 40H19A4 4 0 0 1 15 36V12A4 4 0 0 1 19 8Z", 0, null), El("M22.2 34A1.8 1.8 0 1 0 25.8 34A1.8 1.8 0 1 0 22.2 34Z", 4, null), El("M37 17q4 7 0 14", 2, null)),  // AutoKit
    "bluetooth" to listOf(El("M16 17l15 14-7 7V10l7 7-15 14", 2, null)),  // Bluetooth
    "btmusic" to listOf(El("M13.5 35A4.5 4.5 0 1 0 22.5 35A4.5 4.5 0 1 0 13.5 35Z", 0, null), El("M22.5 35V12", 0, null), El("M22.5 12l12 4", 1, null), El("M32.8 30A2.2 2.2 0 1 0 37.2 30A2.2 2.2 0 1 0 32.8 30Z", 5, null)),  // BT Music
    "car" to listOf(El("M8 31l4-11q1.2-3 5-3h14q3.8 0 5 3l4 11v6H8z", 0, null), El("M11.8 37A3.2 3.2 0 1 0 18.2 37A3.2 3.2 0 1 0 11.8 37Z", 4, null), El("M29.8 37A3.2 3.2 0 1 0 36.2 37A3.2 3.2 0 1 0 29.8 37Z", 4, null), El("M14 25h20", 2, null)),  // Car
    "carbit" to listOf(El("M10.5 13H26.5A2.5 2.5 0 0 1 29 15.5V26.5A2.5 2.5 0 0 1 26.5 29H10.5A2.5 2.5 0 0 1 8 26.5V15.5A2.5 2.5 0 0 1 10.5 13Z", 0, null), El("M23.5 22H37.5A2.5 2.5 0 0 1 40 24.5V34.5A2.5 2.5 0 0 1 37.5 37H23.5A2.5 2.5 0 0 1 21 34.5V24.5A2.5 2.5 0 0 1 23.5 22Z", 2, null)),  // CarbitLink
    "chrome" to listOf(El("M9 24A15 15 0 1 0 39 24A15 15 0 1 0 9 24Z", 0, null), El("M17.5 24A6.5 15 0 1 0 30.5 24A6.5 15 0 1 0 17.5 24Z", 2, null), El("M9 24H39", 1, null)),  // Chrome
    "play" to listOf(El("M14 18H34A3 3 0 0 1 37 21V35A3 3 0 0 1 34 38H14A3 3 0 0 1 11 35V21A3 3 0 0 1 14 18Z", 0, null), El("M18 18v-3q0-5 6-5t6 5v3", 1, null), El("M21 24v9l8-4.5z", 5, null)),  // Cửa hàng Play
    "contacts" to listOf(El("M17.5 17A6.5 6.5 0 1 0 30.5 17A6.5 6.5 0 1 0 17.5 17Z", 0, null), El("M11 39q0-11 13-11t13 11", 2, null)),  // Danh bạ
    "foobar" to listOf(El("M12.5 26H15.5A1.5 1.5 0 0 1 17 27.5V36.5A1.5 1.5 0 0 1 15.5 38H12.5A1.5 1.5 0 0 1 11 36.5V27.5A1.5 1.5 0 0 1 12.5 26Z", 3, null), El("M22.5 12H25.5A1.5 1.5 0 0 1 27 13.5V36.5A1.5 1.5 0 0 1 25.5 38H22.5A1.5 1.5 0 0 1 21 36.5V13.5A1.5 1.5 0 0 1 22.5 12Z", 4, null), El("M32.5 19H35.5A1.5 1.5 0 0 1 37 20.5V36.5A1.5 1.5 0 0 1 35.5 38H32.5A1.5 1.5 0 0 1 31 36.5V20.5A1.5 1.5 0 0 1 32.5 19Z", 5, null)),  // foobar2000
    "fota" to listOf(El("M16 12H32A4 4 0 0 1 36 16V32A4 4 0 0 1 32 36H16A4 4 0 0 1 12 32V16A4 4 0 0 1 16 12Z", 0, null), El("M24 30V17M18 23l6-6 6 6", 1, null)),  // FOTA Update
    "google" to listOf(El("M11 21A10 10 0 1 0 31 21A10 10 0 1 0 11 21Z", 0, null), El("M28.5 28.5L38 38", 1, null)),  // Google
    "gps" to listOf(El("M15 24A9 9 0 1 0 33 24A9 9 0 1 0 15 24Z", 0, null), El("M20.8 24A3.2 3.2 0 1 0 27.2 24A3.2 3.2 0 1 0 20.8 24Z", 4, null), El("M24 6v8M24 34v8M6 24h8M34 24h8", 2, null)),  // GPS Test Plus
    "led" to listOf(El("M17 28q-5-4-5-10a12 12 0 0 1 24 0q0 6-5 10v4H17z", 0, null), El("M20 38h8", 1, null), El("M24 5v3M9 12l2 2M39 12l-2 2", 2, null)),  // LED
    "maps" to listOf(El("M24 41C16 31 12 25.5 12 19.5a12 12 0 0 1 24 0C36 25.5 32 31 24 41z", 0, null), El("M19.4 19.5A4.6 4.6 0 1 0 28.6 19.5A4.6 4.6 0 1 0 19.4 19.5Z", 4, null)),  // Maps
    "microg" to listOf(El("M24 7L38 12V23Q38 34 24 41Q10 34 10 23V12Z", 0, null), El("M17.5 24L22 28.5L30.5 19", 2, null)),  // microG Settings
    "qlog" to listOf(El("M15 8H33A3 3 0 0 1 36 11V37A3 3 0 0 1 33 40H15A3 3 0 0 1 12 37V11A3 3 0 0 1 15 8Z", 0, null), El("M18 18h12", 1, null), El("M18 25h12", 0, null), El("M18 32h7", 2, null)),  // QLog
    "telegram" to listOf(El("M8 22L40 9l-7 29-11-9z", 0, null), El("M22 29l18-20", 1, null)),  // Telegram
    "settings" to listOf(El("M11 24A13 13 0 1 0 37 24A13 13 0 1 0 11 24Z", 0, floatArrayOf(6.4f, 4.2f)), El("M19 24A5 5 0 1 0 29 24A5 5 0 1 0 19 24Z", 1, null)),  // Thiết lập
    "gallery" to listOf(El("M12 12H36A3 3 0 0 1 39 15V33A3 3 0 0 1 36 36H12A3 3 0 0 1 9 33V15A3 3 0 0 1 12 12Z", 0, null), El("M28 20A3 3 0 1 0 34 20A3 3 0 1 0 28 20Z", 4, null), El("M11 34l9-10 7 7 4-4 6 7", 2, null)),  // Thư viện
    "messages" to listOf(El("M10 12h28v18H23l-8 7v-7h-5z", 0, null), El("M16.1 21A1.9 1.9 0 1 0 19.9 21A1.9 1.9 0 1 0 16.1 21Z", 4, null), El("M22.1 21A1.9 1.9 0 1 0 25.9 21A1.9 1.9 0 1 0 22.1 21Z", 4, null), El("M28.1 21A1.9 1.9 0 1 0 31.9 21A1.9 1.9 0 1 0 28.1 21Z", 4, null)),  // Tin nhắn
    "torque" to listOf(El("M10 33A15 15 0 1 1 38 33", 0, null), El("M24 29l8-11", 1, null), El("M21.2 29A2.8 2.8 0 1 0 26.8 29A2.8 2.8 0 1 0 21.2 29Z", 5, null)),  // Torque
    "allapps" to listOf(El("M12.2 15A2.8 2.8 0 1 0 17.8 15A2.8 2.8 0 1 0 12.2 15Z", 3, null), El("M21.2 15A2.8 2.8 0 1 0 26.8 15A2.8 2.8 0 1 0 21.2 15Z", 3, null), El("M30.2 15A2.8 2.8 0 1 0 35.8 15A2.8 2.8 0 1 0 30.2 15Z", 3, null), El("M12.2 24A2.8 2.8 0 1 0 17.8 24A2.8 2.8 0 1 0 12.2 24Z", 3, null), El("M30.2 24A2.8 2.8 0 1 0 35.8 24A2.8 2.8 0 1 0 30.2 24Z", 3, null), El("M12.2 33A2.8 2.8 0 1 0 17.8 33A2.8 2.8 0 1 0 12.2 33Z", 3, null), El("M21.2 33A2.8 2.8 0 1 0 26.8 33A2.8 2.8 0 1 0 21.2 33Z", 3, null), El("M30.2 33A2.8 2.8 0 1 0 35.8 33A2.8 2.8 0 1 0 30.2 33Z", 3, null), El("M20.8 24A3.2 3.2 0 1 0 27.2 24A3.2 3.2 0 1 0 20.8 24Z", 4, null)),  // Tất cả ứng dụng
    "files" to listOf(El("M9 14h11l4 5h15v17H9z", 0, null), El("M9 25h30", 1, null)),  // Tệp
    "ucenter" to listOf(El("M11 17a18 18 0 0 1 26 0", 0, null), El("M16 22a11 11 0 0 1 16 0", 2, null), El("M20.8 29A3.2 3.2 0 1 0 27.2 29A3.2 3.2 0 1 0 20.8 29Z", 4, null), El("M24 32v8", 0, null)),  // u-center
    "viettel" to listOf(El("M14 8h15l7 7v25H14z", 0, null), El("M21 25H29A2 2 0 0 1 31 27V34A2 2 0 0 1 29 36H21A2 2 0 0 1 19 34V27A2 2 0 0 1 21 25Z", 1, null)),  // Viettel Plus
    "youtube" to listOf(El("M16 13H32A7 7 0 0 1 39 20V28A7 7 0 0 1 32 35H16A7 7 0 0 1 9 28V20A7 7 0 0 1 16 13Z", 0, null), El("M20 19v10l9-5z", 4, null)),  // YouTube
    "phone" to listOf(El("M15 9l6 1 2 8-4 3q3 7 9 10l3-4 8 2 1 6q0 4-5 4C21 38 10 27 9 13q0-4 6-4z", 0, null)),  // Điện thoại
)

private val WHITE = Color(0xFFF2F5FA)
private val ORANGE = Color(0xFFFF8A1F)
private val BLUE = Color(0xFF2F7BFF)

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

// Nhận diện ứng dụng để chọn biểu tượng: ưu tiên tên gói đã biết, sau đó theo tên hiển thị (bỏ dấu, không phân biệt hoa thường).
// Trả về null nếu không nhận ra: khi đó launcher dùng biểu tượng gốc của ứng dụng đó.
fun navaraIconKey(pkg: String, label: String): String? {
    when (pkg) {
        "com.android.chrome" -> return "chrome"
        "com.android.vending" -> return "play"
        "com.google.android.youtube" -> return "youtube"
        "org.telegram.messenger" -> return "telegram"
        "com.foobar2000.foobar2000" -> return "foobar"
        "org.prowl.torque" -> return "torque"
        "com.google.android.apps.maps" -> return "maps"
        "com.google.android.googlequicksearchbox" -> return "google"
        "com.google.android.contacts", "com.android.contacts" -> return "contacts"
        "com.google.android.dialer", "com.android.dialer" -> return "phone"
        "com.google.android.apps.messaging", "com.android.mms" -> return "messages"
        "com.google.android.apps.photos", "com.android.gallery3d" -> return "gallery"
        "com.android.settings" -> return "settings"
        "com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files" -> return "files"
    }
    val n = norm(label)
    return when {
        n.contains("apkinstaller") || n.contains("apk installer") -> "apk"
        n.contains("autokit") -> "autokit"
        n == "bluetooth" -> "bluetooth"
        n.contains("bt music") || n.contains("btmusic") -> "btmusic"
        n == "car" -> "car"
        n.contains("carbitlink") || n.contains("carbit") -> "carbit"
        n.contains("chrome") -> "chrome"
        n.contains("cua hang play") || n.contains("play store") -> "play"
        n == "danh ba" || n == "contacts" -> "contacts"
        n.contains("foobar") -> "foobar"
        n.contains("fota") -> "fota"
        n == "google" -> "google"
        n.contains("gps test") -> "gps"
        n == "led" -> "led"
        n == "maps" || n == "google maps" || n == "ban do" -> "maps"
        n.contains("microg") -> "microg"
        n.contains("qlog") -> "qlog"
        n.contains("telegram") -> "telegram"
        n == "thiet lap" || n == "settings" || n == "cai dat" -> "settings"
        n == "thu vien" || n == "gallery" || n == "photos" || n == "anh" -> "gallery"
        n == "tin nhan" || n == "messages" -> "messages"
        n.contains("torque") -> "torque"
        n.startsWith("tat ca ung dung") || n == "all apps" -> "allapps"
        n == "tep" || n == "files" || n == "file manager" -> "files"
        n.contains("u-center") || n.contains("ucenter") -> "ucenter"
        n.contains("viettel") -> "viettel"
        n.contains("youtube") -> "youtube"
        n == "dien thoai" || n == "phone" -> "phone"
        else -> null
    }
}

@Composable
fun NavaraGlyph(key: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    val els = GLYPHS[key] ?: return
    val th = LocalTheme.current
    val mono = th.mono != 0
    val inkCol = if (selected) th.accent else th.ink   // icon trắng, đang chọn thì cam
    val paths = remember(key) { els.map { PathParser().parsePathString(it.d).toPath() } }
    Canvas(modifier) {
        scale(size.width / 48f, Offset.Zero) {
            els.forEachIndexed { i, e ->
                val col = inkCol
                if (e.st >= 3) {
                    drawPath(paths[i], col, style = Fill)
                } else {
                    val fx = e.dash?.let { PathEffect.dashPathEffect(it) }
                    drawPath(paths[i], col, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = fx))
                }
            }
        }
    }
}
