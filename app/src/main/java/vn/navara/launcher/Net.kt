package vn.navara.launcher

import java.net.HttpURLConnection
import java.net.URL

// Thông tin nhà phát triển và địa chỉ dự án (dùng cho mục Giới thiệu và cập nhật trực tuyến)
const val APP_NAME = "VL23 Launcher"
const val DEV_NAME = "Phan Kim Phụng"
const val REPO_OWNER = "PhungP"
const val REPO_NAME = "VL23-Launcher"
const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"

// User-Agent chung cho mọi yêu cầu mạng (Nominatim và GitHub đều yêu cầu có định danh ứng dụng)
val APP_UA: String get() = "VL23Launcher/" + BuildConfig.VERSION_NAME + " (Android head unit; " + REPO_URL + ")"

// GET một địa chỉ https, trả về nội dung dạng chữ. Gọi trên luồng nền.
fun httpGetText(url: String, accept: String? = null): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", APP_UA)
        if (accept != null) c.setRequestProperty("Accept", accept)
        if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}
