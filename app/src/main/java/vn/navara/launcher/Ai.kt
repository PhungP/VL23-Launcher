package vn.navara.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

// ===== Trợ lý AI nhiều dịch vụ: Gemini (Google) và các dịch vụ có gói miễn phí tương thích OpenAI =====
// Người dùng tự nhập khoá API của từng dịch vụ trong Cài đặt. Dịch vụ chính được hỏi trước; nếu hết hạn mức, quá tải hoặc quá chậm
// thì tự chuyển sang dịch vụ khác đã nhập khoá. Gói miễn phí và tên mô hình của các dịch vụ thay đổi theo thời gian, nên tên mô hình sửa được.

class AiProvider(
    val id: Int, val short: String, val title: String, val base: String, val keyUrl: String, val defModel: String, val note: String
)

val AI_PROVIDERS = listOf(
    AiProvider(0, "Gemini", "Gemini (Google)", "", "aistudio.google.com/apikey", Gemini.DEFAULT_MODEL,
        "Gói miễn phí của Google, hiểu giọng nói trực tiếp. Hạn mức thấp và đôi khi quá tải."),
    AiProvider(1, "Groq", "Groq", "https://api.groq.com/openai/v1", "console.groq.com/keys", "openai/gpt-oss-120b",
        "Miễn phí, không cần thẻ, trả lời rất nhanh (khoảng 30 lượt mỗi phút, 1000 lượt mỗi ngày). Có chuyển giọng nói thành chữ, nên dùng được micro."),
    AiProvider(2, "OpenRouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter.ai/keys", "openai/gpt-oss-120b:free",
        "Nhiều mô hình miễn phí (tên có đuôi :free), khoảng 20 lượt mỗi phút và 200 lượt mỗi ngày. Mô hình miễn phí có thể chậm vào giờ đông."),
    AiProvider(3, "Mistral", "Mistral (La Plateforme)", "https://api.mistral.ai/v1", "console.mistral.ai/api-keys", "mistral-small-latest",
        "Gói Experiment miễn phí, cần xác minh số điện thoại và đồng ý cho Mistral dùng dữ liệu để cải thiện mô hình."),
    AiProvider(4, "Cerebras", "Cerebras", "https://api.cerebras.ai/v1", "cloud.cerebras.ai", "gpt-oss-120b",
        "Gói miễn phí, tốc độ rất cao, giới hạn lượt gọi theo ngày."),
    AiProvider(5, "Khác", "Khác (dịch vụ tương thích OpenAI)", "", "", "",
        "Nhập địa chỉ API (kết thúc bằng /v1), khoá và tên mô hình của bất kỳ dịch vụ nào tương thích OpenAI, kể cả máy chủ riêng của bạn.")
)

// Thứ tự thử dịch vụ dự phòng: nhanh và miễn phí rộng rãi trước
val AI_FALLBACK_ORDER = listOf(1, 4, 0, 2, 3, 5)

private fun aiCfg(p: UiPrefs): JSONObject = try { JSONObject(p.aiCfg) } catch (e: Exception) { JSONObject() }
private fun aiField(p: UiPrefs, id: Int, f: String): String = aiCfg(p).optJSONObject(id.toString())?.optString(f).orEmpty()

fun aiKey(p: UiPrefs, id: Int): String = (if (id == 0) p.geminiKey else aiField(p, id, "key")).trim()

// Giá trị đã lưu (có thể rỗng) và giá trị thực dùng (rỗng thì lấy mặc định của dịch vụ)
fun aiModelRaw(p: UiPrefs, id: Int): String = if (id == 0) p.geminiModel else aiField(p, id, "model")
fun aiModel(p: UiPrefs, id: Int): String = aiModelRaw(p, id).trim().ifEmpty { AI_PROVIDERS[id].defModel }
fun aiUrl(p: UiPrefs, id: Int): String = (if (id == 5) aiField(p, id, "url") else AI_PROVIDERS[id].base).trim().trimEnd('/')

fun UiPrefs.withAi(id: Int, key: String? = null, model: String? = null, url: String? = null): UiPrefs {
    if (id == 0) return copy(geminiKey = key ?: geminiKey, geminiModel = model ?: geminiModel)
    val cfg = aiCfg(this)
    val o = cfg.optJSONObject(id.toString()) ?: JSONObject()
    if (key != null) o.put("key", key)
    if (model != null) o.put("model", model)
    if (url != null) o.put("url", url)
    cfg.put(id.toString(), o)
    return copy(aiCfg = cfg.toString())
}

fun aiReady(p: UiPrefs, id: Int): Boolean = aiKey(p, id).isNotEmpty() && (id != 5 || (aiUrl(p, id).isNotEmpty() && aiModel(p, id).isNotEmpty()))
fun aiAny(p: UiPrefs): Boolean = AI_PROVIDERS.any { aiReady(p, it.id) }

// Nói được khi có Gemini (nghe trực tiếp) hoặc Groq (chuyển giọng nói thành chữ)
fun aiVoiceOk(p: UiPrefs): Boolean = aiReady(p, 0) || aiReady(p, 1)

// provider: dịch vụ đã trả lời; geminiModel: tên mô hình Gemini mới nếu app đã tự đổi (để lưu lại); note: ghi chú khi phải dùng dịch vụ dự phòng
class AiResult(val text: String?, val error: String?, val provider: Int = -1, val geminiModel: String? = null, val note: String? = null)

object Ai {
    private class Res(val text: String?, val error: String?)

    // withText: dựng lại câu hỏi từ văn bản khi dịch vụ không nghe được giọng nói (giọng nói được chuyển thành chữ trước)
    suspend fun ask(
        p: UiPrefs, system: String, user: String,
        audio: ByteArray? = null, json: Boolean = false, withText: ((String) -> String)? = null
    ): AiResult {
        val primary = p.aiProvider.coerceIn(0, AI_PROVIDERS.size - 1)
        val order = ArrayList<Int>()
        if (aiReady(p, primary)) order.add(primary)
        if (p.aiFallback || order.isEmpty()) for (i in AI_FALLBACK_ORDER) if (i != primary && aiReady(p, i)) order.add(i)
        if (order.isEmpty()) return AiResult(null, "Chưa nhập khoá API nào. Vào Cài đặt > Trợ lý AI để nhập.")
        val quick = order.size > 1   // có dịch vụ dự phòng thì không chờ thử lại lâu ở từng dịch vụ
        val errors = ArrayList<String>()
        var transcript: String? = null
        var triedTranscribe = false
        for ((idx, id) in order.withIndex()) {
            val prov = AI_PROVIDERS[id]
            val note = if (idx > 0) "Đã dùng " + prov.short + " vì " + AI_PROVIDERS[order[0]].short + " không trả lời được." else null
            if (id == 0) {
                val r = Gemini.ask(aiKey(p, 0), aiModel(p, 0), system, user, audio, json, quick)
                if (r.text != null) return AiResult(r.text, null, 0, r.model, note)
                errors.add("Gemini: " + (r.error ?: "lỗi không rõ"))
                continue
            }
            var u = user
            if (audio != null) {
                if (!triedTranscribe) { triedTranscribe = true; transcript = transcribe(p, audio) }
                val t = transcript
                if (t == null) { errors.add(prov.short + ": không nghe được giọng nói (cần khoá Gemini hoặc Groq), hãy gõ chữ"); continue }
                u = withText?.invoke(t) ?: (user + "\nNội dung người dùng nói: " + t)
            }
            val r = chat(p, id, system, u)
            if (r.text != null) return AiResult(r.text, null, id, null, note)
            errors.add(r.error ?: (prov.short + ": lỗi không rõ"))
        }
        return AiResult(null, errors.joinToString("\n"))
    }

    // Gọi /chat/completions của dịch vụ tương thích OpenAI
    private suspend fun chat(p: UiPrefs, id: Int, system: String, user: String): Res = withContext(Dispatchers.IO) {
        val prov = AI_PROVIDERS[id]
        val model = aiModel(p, id)
        try {
            val msgs = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user))
            val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.4).put("max_tokens", 2048)
            // Mô hình gpt-oss trên Groq suy luận lâu nếu không giới hạn: đặt mức thấp để trả lời nhanh
            if (id == 1 && model.contains("gpt-oss")) body.put("reasoning_effort", "low")
            val c = URL(aiUrl(p, id) + "/chat/completions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 25000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer " + aiKey(p, id))
            if (id == 2) { c.setRequestProperty("HTTP-Referer", "https://github.com"); c.setRequestProperty("X-Title", "VL23 Launcher") }
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code !in 200..299) return@withContext Res(null, prov.short + ": " + errMsg(code, txt, model))
            val msg = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            var out = if (msg == null || msg.isNull("content")) "" else msg.getString("content")
            out = out.replace(Regex("(?s)<think>.*?</think>"), "").trim()   // một số mô hình suy luận chèn phần nghĩ vào nội dung
            if (out.isEmpty()) Res(null, prov.short + ": mô hình không trả lời được, thử đổi mô hình khác") else Res(out, null)
        } catch (e: java.net.SocketTimeoutException) {
            Res(null, prov.short + ": trả lời quá chậm")
        } catch (e: Exception) {
            Res(null, prov.short + ": không kết nối được, kiểm tra mạng")
        }
    }

    private fun errMsg(code: Int, body: String, model: String): String {
        val msg = try {
            val e = JSONObject(body).opt("error")
            if (e is JSONObject) e.optString("message") else JSONObject(body).optString("message")
        } catch (e: Exception) { "" }
        return when {
            code == 401 || code == 403 -> "khoá bị từ chối, kiểm tra lại khoá"
            code == 402 -> "hết tín dụng"
            code == 404 -> "không tìm thấy mô hình \"" + model + "\", kiểm tra tên mô hình"
            code == 413 || code == 429 -> "hết hạn mức hoặc gọi quá nhanh"
            code >= 500 -> "đang quá tải"
            code == 400 || code == 422 -> "yêu cầu không hợp lệ" + (if (msg.isNotBlank()) " (" + msg.take(120) + ")" else "")
            else -> "lỗi " + code + (if (msg.isNotBlank()) ": " + msg.take(120) else "")
        }
    }

    // Chuyển giọng nói (WAV) thành chữ bằng Whisper của Groq; null nếu chưa có khoá Groq hoặc lỗi
    private suspend fun transcribe(p: UiPrefs, wav: ByteArray): String? = withContext(Dispatchers.IO) {
        if (!aiReady(p, 1)) return@withContext null
        try {
            val boundary = "----vl23" + System.currentTimeMillis()
            val c = URL(aiUrl(p, 1) + "/audio/transcriptions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 25000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer " + aiKey(p, 1))
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
            val out = ByteArrayOutputStream()
            fun field(name: String, value: String) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").toByteArray(Charsets.UTF_8))
            }
            field("model", "whisper-large-v3-turbo")
            field("language", "vi")
            field("response_format", "json")
            field("temperature", "0")
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n").toByteArray(Charsets.UTF_8))
            out.write(wav)
            out.write(("\r\n--" + boundary + "--\r\n").toByteArray(Charsets.UTF_8))
            c.outputStream.use { it.write(out.toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code !in 200..299) return@withContext null
            val t = JSONObject(txt).optString("text").trim()
            if (t.isEmpty()) null else t
        } catch (e: Exception) { null }
    }
}
