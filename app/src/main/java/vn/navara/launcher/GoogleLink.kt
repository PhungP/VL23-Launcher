package vn.navara.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

// ===== Dán địa chỉ từ Google Maps vào ô tìm kiếm =====
// Nhận: liên kết Google Maps (đầy đủ hoặc rút gọn maps.app.goo.gl), nội dung "Chia sẻ" (tên + địa chỉ + liên kết),
// toạ độ dạng "20.8449, 106.6881", hoặc địa chỉ chữ thường. Có toạ độ thì dùng thẳng (không tốn lượt tìm địa chỉ).

// place != null: đã có toạ độ; query != null: chỉ có chữ, cần tìm địa chỉ; cả hai null: error giải thích
class PasteResult(val place: Place?, val query: String?, val error: String? = null)

object PasteParser {
    private val URL_RE = Regex("""https?://\S+""")
    private val COORD = Regex("""(-?\d{1,2}\.\d{3,})\s*[,;]?\s*(-?\d{1,3}\.\d{3,})""")
    private val PIN = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val AT = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val PARAM = Regex("""[?&](?:q|query|ll|destination|daddr|center)=(-?\d+\.\d+),\s*(-?\d+\.\d+)""")
    private val CONTINUE = Regex("""[?&]continue=([^&]+)""")

    fun looksLikePaste(s: String): Boolean {
        val t = s.trim()
        if (t.contains("http://") || t.contains("https://")) return true
        return Regex("""^-?\d{1,2}\.\d{3,}\s*[,;]?\s*-?\d{1,3}\.\d{3,}$""").matches(t)
    }

    private fun dec(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    private fun valid(lat: Double, lng: Double) = lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)

    // Toạ độ trong liên kết: ưu tiên ghim địa điểm (!3d!4d), rồi tâm bản đồ (@), rồi tham số q / ll / destination...
    fun coordsFromUrl(url: String): Pair<Double, Double>? {
        val u = dec(url)
        for (re in listOf(PIN, AT, PARAM)) {
            val m = re.find(u) ?: continue
            val a = m.groupValues[1].toDoubleOrNull() ?: continue
            val b = m.groupValues[2].toDoubleOrNull() ?: continue
            if (valid(a, b)) return a to b
        }
        return null
    }

    // Tên địa điểm nằm trong đường dẫn: /maps/place/Ten+Dia+Diem/@...
    private fun nameFromUrl(url: String): String? {
        val m = Regex("""/maps/place/([^/@?]+)""").find(url) ?: return null
        val n = dec(m.groupValues[1].replace('+', ' ')).trim()
        return if (n.isBlank() || COORD.containsMatchIn(n)) null else n
    }

    private fun isShort(url: String): Boolean {
        val host = try { URL(url).host.lowercase() } catch (e: Exception) { return false }
        return host == "maps.app.goo.gl" || host == "goo.gl" || host == "g.co" || url.contains("goo.gl/maps")
    }

    // Đi theo chuyển hướng của liên kết rút gọn bằng tay (không tải nội dung trang); dừng khi đã thấy toạ độ
    private fun resolve(start: String): String? = try {
        var cur = start
        for (i in 0 until 6) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            try {
                val code = c.responseCode
                if (code !in 300..399) return cur
                val loc = c.getHeaderField("Location") ?: return cur
                var next = if (loc.startsWith("http")) loc else URL(URL(cur), loc).toString()
                // Trang đồng ý cookie của Google bọc địa chỉ thật trong tham số continue
                if (next.contains("consent.google.")) CONTINUE.find(next)?.let { next = dec(it.groupValues[1]) }
                cur = next
                if (coordsFromUrl(cur) != null) return cur
            } finally {
                c.disconnect()
            }
        }
        cur
    } catch (e: Exception) { null }

    suspend fun parse(raw: String): PasteResult {
        val text = raw.trim()
        val url = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', ']', ';')
        val rest = if (url != null) text.replace(url, "") else text
        val lines = rest.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = lines.firstOrNull() ?: ""
        val addr = lines.drop(1).joinToString(", ")
        val textQuery = (name + (if (addr.isNotBlank()) ", $addr" else "")).trim()

        if (url != null) {
            val full = if (isShort(url)) withContext(Dispatchers.IO) { resolve(url) } else url
            if (full == null) {
                return if (textQuery.isNotBlank()) PasteResult(null, textQuery)
                else PasteResult(null, null, "Không mở được liên kết Google Maps (kiểm tra mạng). Hãy dán địa chỉ hoặc tên địa điểm.")
            }
            val ll = coordsFromUrl(full)
            val pathName = nameFromUrl(full)
            if (ll != null) {
                val nm = name.ifBlank { pathName ?: "Địa điểm từ Google Maps" }
                val sub = addr.ifBlank { String.format(Locale.US, "%.5f, %.5f", ll.first, ll.second) }
                return PasteResult(Place(nm, sub, ll.first, ll.second), null)
            }
            val q = textQuery.ifBlank { pathName ?: "" }
            return if (q.isNotBlank()) PasteResult(null, q)
            else PasteResult(null, null, "Không đọc được vị trí từ liên kết này. Hãy dán địa chỉ hoặc tên địa điểm.")
        }

        val m = COORD.find(text)
        if (m != null && lines.size <= 1) {
            val a = m.groupValues[1].toDoubleOrNull()
            val b = m.groupValues[2].toDoubleOrNull()
            if (a != null && b != null && valid(a, b)) {
                return PasteResult(Place("Toạ độ " + String.format(Locale.US, "%.5f, %.5f", a, b), "Từ nội dung đã dán", a, b), null)
            }
        }
        return PasteResult(null, text.replace(Regex("""\s*\n\s*"""), ", "))
    }
}
