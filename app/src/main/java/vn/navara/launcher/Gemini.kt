package vn.navara.launcher

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ===== Trợ lý Gemini: người dùng tự nhập khoá API (Google AI Studio) trong Cài đặt =====
// Gửi lên Google: câu hỏi hoặc đoạn ghi âm, vị trí hiện tại (khi tìm đường) và số liệu xe hiện có (khi hỏi về xe, mã lỗi, chuyến đi).

// model: tên mô hình thực sự đã dùng khi app phải tự chọn lại mô hình (để lưu vào Cài đặt)
class GeminiResult(val text: String?, val error: String?, val model: String? = null)

object Gemini {
    // Google ngừng mô hình cũ khá thường xuyên (gemini-2.5-flash đã bị ngừng từ tháng 11/2026), nên khi gặp lỗi "không tìm thấy mô hình"
    // app tự hỏi Google danh sách mô hình và chọn mô hình Flash mới nhất.
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    // audio: đoạn ghi âm AAC gửi kèm (Gemini tự nghe và hiểu tiếng Việt); json = true thì ép trả về JSON
    // Lỗi tạm thời được xử lý tự động: mô hình bị ngừng (404) thì chọn Flash mới nhất và lưu lại; Google quá tải (5xx) thì thử lại một lần
    // rồi thử vài mô hình Flash khác, chỉ dùng tạm cho câu hỏi này, không đổi Cài đặt.
    suspend fun ask(
        key: String, model: String, system: String, user: String,
        audio: ByteArray? = null, json: Boolean = false
    ): GeminiResult {
        val m = model.trim().ifEmpty { DEFAULT_MODEL }
        var cur = m
        var r = call(key, cur, system, user, audio, json)
        if (r.notFound) {
            val alt = listFlash(key).firstOrNull()
            if (alt != null && alt != m) {
                val r2 = call(key, alt, system, user, audio, json)
                if (r2.res.text != null) return GeminiResult(r2.res.text, null, alt)
                cur = alt
                r = r2
            }
        }
        if (r.busy) {
            delay(1500)
            r = call(key, cur, system, user, audio, json)
            if (r.busy) {
                for (a in listFlash(key).filter { it != cur }.take(2)) {
                    val r2 = call(key, a, system, user, audio, json)
                    if (r2.res.text != null) return GeminiResult(r2.res.text, null)
                    if (!r2.busy) break
                }
            }
        }
        return r.res
    }

    // busy: Google báo quá tải hoặc lỗi máy chủ tạm thời (500, 502, 503, 504)
    private class Call(val res: GeminiResult, val notFound: Boolean, val busy: Boolean = false)

    // Hỏi Google danh sách mô hình, trả về các mô hình Flash hỗ trợ generateContent theo thứ tự ưu tiên:
    // mới nhất và ổn định trước, bản lite xếp sau cùng (bỏ ảnh, giọng nói, live, nhúng...)
    private suspend fun listFlash(key: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("x-goog-api-key", key.trim())
            if (c.responseCode !in 200..299) { c.disconnect(); return@withContext emptyList<String>() }
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val arr = JSONObject(txt).optJSONArray("models") ?: return@withContext emptyList<String>()
            val bad = listOf("image", "tts", "live", "audio", "embedding", "robotics", "omni", "computer", "thinking", "learnlm", "gemma")
            val normal = ArrayList<Pair<String, Double>>()
            val lite = ArrayList<Pair<String, Double>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("name").removePrefix("models/")
                val methods = o.optJSONArray("supportedGenerationMethods")
                var ok = false
                if (methods != null) for (k in 0 until methods.length()) if (methods.optString(k) == "generateContent") ok = true
                if (!ok || !id.startsWith("gemini-") || !id.contains("flash") || bad.any { id.contains(it) }) continue
                val ver = Regex("""gemini-(\d+(?:\.\d+)?)""").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                // Cùng phiên bản thì ưu tiên bản ổn định hơn bản preview / thử nghiệm
                val score = ver * 10.0 + (if (id.contains("preview") || id.contains("exp")) 0.0 else 1.0)
                if (id.contains("lite")) lite.add(id to score) else normal.add(id to score)
            }
            normal.sortByDescending { it.second }
            lite.sortByDescending { it.second }
            normal.map { it.first } + lite.map { it.first }
        } catch (e: Exception) { emptyList<String>() }
    }

    private suspend fun call(
        key: String, m: String, system: String, user: String, audio: ByteArray?, json: Boolean
    ): Call = withContext(Dispatchers.IO) {
        try {
            val parts = JSONArray()
            if (audio != null) {
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/aac").put("data", Base64.encodeToString(audio, Base64.NO_WRAP))))
            }
            parts.put(JSONObject().put("text", user))
            val gen = JSONObject().put("temperature", 0.4).put("maxOutputTokens", 2048)
            if (json) gen.put("responseMimeType", "application/json")
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", gen)
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 45000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key.trim())
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code !in 200..299) return@withContext Call(GeminiResult(null, errText(code, txt, m)), code == 404, code == 500 || code == 502 || code == 503 || code == 504)
            val cand = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)
            val ps = cand?.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (ps != null) for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
            val out = sb.toString().trim()
            if (out.isEmpty()) Call(GeminiResult(null, "Gemini không trả lời được (" + (cand?.optString("finishReason").orEmpty().ifEmpty { "không rõ lý do" }) + "). Thử hỏi lại."), false)
            else Call(GeminiResult(out, null), false)
        } catch (e: Exception) {
            Call(GeminiResult(null, "Không gọi được Gemini, kiểm tra kết nối mạng"), false)
        }
    }

    private fun errText(code: Int, body: String, model: String): String {
        val msg = try { JSONObject(body).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
        return when {
            code == 400 && msg.contains("API key", ignoreCase = true) -> "Khoá Gemini không hợp lệ. Kiểm tra lại khoá trong Cài đặt."
            code == 401 || code == 403 -> "Gemini từ chối khoá API (sai khoá, hoặc khoá chưa được phép dùng Gemini API)."
            code == 404 -> "Không tìm thấy mô hình \"" + model + "\". Kiểm tra tên mô hình trong Cài đặt (mặc định " + DEFAULT_MODEL + ")."
            code == 429 -> "Đã hết hạn mức Gemini hoặc gọi quá nhanh. Chờ ít phút rồi thử lại."
            code >= 500 -> "Gemini đang quá tải (app đã tự thử lại và thử mô hình khác). Thử lại sau ít phút."
            else -> "Gemini báo lỗi " + code + (if (msg.isNotBlank()) ": " + msg.take(160) else "")
        }
    }
}

// Ghi âm giọng nói từ micro của đầu xe (AAC 16 kHz, một kênh) để gửi cho Gemini
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null

    fun start(): Boolean {
        return try {
            val f = File(ctx.cacheDir, "gemini_voice.aac")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(48000)
            r.setAudioChannels(1)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            true
        } catch (e: Exception) {
            cancel()
            false
        }
    }

    // Dừng ghi và trả về dữ liệu; null nếu lỗi hoặc ghi quá ngắn
    fun stop(): ByteArray? {
        val r = rec ?: return null
        rec = null
        return try {
            r.stop()
            r.release()
            file?.readBytes()
        } catch (e: Exception) {
            try { r.release() } catch (e2: Exception) { }
            null
        }
    }

    fun cancel() {
        val r = rec
        rec = null
        if (r != null) {
            try { r.stop() } catch (e: Exception) { }
            try { r.release() } catch (e: Exception) { }
        }
    }
}

// Thống kê chuyến đi: cộng dồn mỗi giây khi OBD đang nối; lưu lại để còn xem sau khi tắt máy. Tự bắt đầu chuyến mới nếu OBD im quá 20 phút.
object TripStats {
    var startMs = 0L
    var distKm = 0.0
    var driveSec = 0.0
    var idleSec = 0.0
    var maxSpeed = 0f
    var fuelL = 0.0
    var harshAccel = 0
    var harshBrake = 0
    var highRpmSec = 0.0
    val version = MutableStateFlow(0)
    private var lastMs = 0L
    private var lastSpeed = -1f
    private var lastHarshMs = 0L
    private var ticks = 0

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("trip", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val s = prefs(ctx)
        startMs = s.getLong("start", 0L)
        distKm = s.getFloat("dist", 0f).toDouble()
        driveSec = s.getFloat("drive", 0f).toDouble()
        idleSec = s.getFloat("idle", 0f).toDouble()
        maxSpeed = s.getFloat("max", 0f)
        fuelL = s.getFloat("fuel", 0f).toDouble()
        harshAccel = s.getInt("ha", 0)
        harshBrake = s.getInt("hb", 0)
        highRpmSec = s.getFloat("hr", 0f).toDouble()
        lastMs = s.getLong("last", 0L)
        version.value++
    }

    fun save(ctx: Context) {
        prefs(ctx).edit().putLong("start", startMs).putFloat("dist", distKm.toFloat()).putFloat("drive", driveSec.toFloat())
            .putFloat("idle", idleSec.toFloat()).putFloat("max", maxSpeed).putFloat("fuel", fuelL.toFloat())
            .putInt("ha", harshAccel).putInt("hb", harshBrake).putFloat("hr", highRpmSec.toFloat()).putLong("last", lastMs).apply()
    }

    fun reset(ctx: Context) {
        startMs = 0L; distKm = 0.0; driveSec = 0.0; idleSec = 0.0; maxSpeed = 0f; fuelL = 0.0
        harshAccel = 0; harshBrake = 0; highRpmSec = 0.0; lastSpeed = -1f
        save(ctx)
        version.value++
    }

    // Gọi mỗi giây với số liệu OBD mới nhất
    fun update(ctx: Context, d: ObdData) {
        val now = System.currentTimeMillis()
        if (d.status != ObdManager.OK) { lastSpeed = -1f; return }
        if (lastMs != 0L && now - lastMs > 20 * 60_000L) reset(ctx)   // lâu rồi mới có số liệu: coi như chuyến mới
        if (startMs == 0L) startMs = now
        val dt = if (lastMs == 0L) 0.0 else (now - lastMs) / 1000.0
        lastMs = now
        if (dt <= 0.0 || dt > 5.0) { lastSpeed = d.speed.toFloat(); return }
        val v = d.speed.toFloat()
        distKm += v / 3600.0 * dt
        if (v >= 2f) driveSec += dt else if (d.rpm > 400) idleSec += dt
        if (v > maxSpeed) maxSpeed = v
        if (d.rpm > 3500) highRpmSec += dt
        val rate = d.ex["fuelRate"]
        val l100 = d.ex["lper100"]
        if (rate != null) fuelL += rate / 3600.0 * dt
        else if (l100 != null && v > 5f) fuelL += l100 / 100.0 * (v / 3600.0 * dt)
        if (lastSpeed >= 0f) {
            val a = (v - lastSpeed) / 3.6 / dt   // gia tốc (m/s²)
            if (now - lastHarshMs > 3000L) {
                if (a > 2.8) { harshAccel++; lastHarshMs = now } else if (a < -3.2) { harshBrake++; lastHarshMs = now }
            }
        }
        lastSpeed = v
        if (++ticks % 30 == 0) save(ctx)
        version.value++
    }

    fun hasData() = startMs != 0L && distKm > 0.05

    fun summary(): String {
        if (!hasData()) return "Chưa có dữ liệu chuyến đi. Số liệu được ghi khi OBD đang nối và xe chạy."
        val mins = ((System.currentTimeMillis() - startMs) / 60000L).coerceAtLeast(0)
        val moving = driveSec / 3600.0
        val avg = if (moving > 0.001) distKm / moving else 0.0
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "Quãng đường: %.1f km, thời gian từ lúc bắt đầu: %d phút\n", distKm, mins))
        sb.append(String.format(Locale.US, "Tốc độ trung bình khi chạy: %.0f km/h, tối đa: %.0f km/h\n", avg, maxSpeed))
        sb.append(String.format(Locale.US, "Thời gian nổ máy đứng yên: %.0f phút, chạy: %.0f phút\n", idleSec / 60.0, driveSec / 60.0))
        if (fuelL > 0.01) {
            sb.append(String.format(Locale.US, "Nhiên liệu ước tính: %.2f L", fuelL))
            if (distKm > 1.0) sb.append(String.format(Locale.US, " (khoảng %.1f L/100 km)", fuelL / distKm * 100.0))
            sb.append("\n")
        }
        sb.append("Tăng tốc gắt: " + harshAccel + " lần, phanh gắt: " + harshBrake + " lần\n")
        sb.append(String.format(Locale.US, "Thời gian vòng tua trên 3500: %.1f phút", highRpmSec / 60.0))
        return sb.toString()
    }
}

// Số liệu xe hiện có, gửi kèm câu hỏi để Gemini trả lời sát tình huống
fun geminiContext(obd: ObdData, tp: Map<String, TpmsWheel>, p: UiPrefs): String {
    val v = Vehicles.byId(p.vehicle)
    val sb = StringBuilder("Xe: " + v.title + " (" + v.spec + ")\n")
    if (obd.status != ObdManager.OK) {
        sb.append("OBD chưa kết nối nên chưa có số liệu động cơ.\n")
    } else {
        for (m in homePool(v)) {
            if (isTpmsKey(m.key)) continue
            val x = homeMetricValue(obd, m.key) ?: continue
            sb.append(m.label + ": " + String.format(Locale.US, "%." + m.dec + "f", x) + (if (m.unit.isNotEmpty()) " " + m.unit else "") + "\n")
        }
        obd.gear?.let { sb.append("Số đang cài: " + it + "\n") }
        val mil = obd.ex["mil"]
        if (mil != null) sb.append("Đèn báo lỗi động cơ (MIL): " + (if (mil > 0f) "đang BẬT" else "tắt") + "\n")
        obd.ex["dtc"]?.let { sb.append("Số mã lỗi đang lưu: " + it.toInt() + "\n") }
    }
    for (w in TPMS_WHEELS) {
        val x = tp[w] ?: continue
        sb.append("Lốp " + tpmsWheelName(w).lowercase() + ": " + String.format(Locale.US, "%.2f bar, %.0f°C", x.bar, x.tempC) + "\n")
    }
    return sb.toString().trim()
}

fun geminiClean(s: String): String = s.replace("**", "").replace("__", "").replace("`", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^\\s*[*]\\s+"), "- ").trim()

const val GEMINI_SYS_NAV = "Bạn là bộ phận hiểu yêu cầu tìm điểm đến của hệ thống dẫn đường trên ô tô tại Việt Nam. Người dùng nói hoặc gõ tiếng Việt. " +
    "Hãy rút điểm đến thành một chuỗi tìm kiếm ngắn gọn để đưa vào dịch vụ tìm địa chỉ: tên địa điểm kèm quận hoặc huyện và thành phố, hoặc số nhà, đường, phường, quận, thành phố. " +
    "Nếu người dùng nói gần đây hoặc gần nhất, hãy thêm tên quận hoặc thành phố suy ra từ toạ độ hiện tại. " +
    "Không bịa địa chỉ cụ thể khi không chắc: nếu chỉ có loại địa điểm (ví dụ quán phở), hãy trả loại địa điểm kèm khu vực. " +
    "Chỉ trả về JSON dạng {\"query\":\"...\",\"say\":\"một câu xác nhận ngắn bằng tiếng Việt\"}. Nếu không phải yêu cầu tìm đường thì trả {\"query\":\"\",\"say\":\"lý do ngắn\"}."

const val GEMINI_SYS_CAR = "Bạn là trợ lý kỹ thuật ô tô nói tiếng Việt, đang trả lời người lái trong lúc lái xe. Trả lời ngắn gọn, tối đa 4 câu, dễ hiểu, không dùng định dạng markdown. " +
    "Dựa vào số liệu xe hiện tại được cung cấp; nếu số liệu không đủ để kết luận thì nói rõ. " +
    "Ưu tiên an toàn: nếu có dấu hiệu nguy hiểm (nhiệt độ nước quá cao, áp suất lốp quá thấp, đèn báo nghiêm trọng) hãy khuyên dừng xe ở nơi an toàn. Đây chỉ là tham khảo, không thay thế thợ kỹ thuật."

const val GEMINI_SYS_DTC = "Bạn là chuyên gia chẩn đoán ô tô. Giải thích từng mã lỗi OBD-II bằng tiếng Việt, đúng với dòng xe được cung cấp: ý nghĩa, tối đa 3 nguyên nhân thường gặp, " +
    "mức độ (có thể đi tiếp, nên sớm kiểm tra, hay nên dừng xe), việc nên làm. Mỗi mã tối đa 3 câu. Nếu mã đặc thù của hãng mà không chắc thì nói rõ là không chắc. " +
    "Không dùng định dạng markdown. Đây chỉ là tham khảo, không thay thế thợ kỹ thuật."

const val GEMINI_SYS_TRIP = "Bạn là huấn luyện viên lái xe tiết kiệm và an toàn. Dựa vào số liệu chuyến đi, nhận xét bằng tiếng Việt ngắn gọn: một câu tổng quan, " +
    "tối đa 3 điểm nên cải thiện cụ thể kèm con số, và một điều đã làm tốt nếu có. Không dùng định dạng markdown. Giọng thân thiện, không phán xét gay gắt."
