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

// Mã dịch vụ giữ cố định để không làm hỏng cài đặt đã lưu: 0 Gemini, 1 Groq, 2 OpenRouter, 5 Khác, 6 Cloudflare, 7 NVIDIA
// (3 Mistral và 4 Cerebras đã bỏ từ bản 0.27).
val AI_PROVIDERS = listOf(
    AiProvider(0, "Gemini", "Gemini (Google)", "", "aistudio.google.com/apikey", Gemini.DEFAULT_MODEL,
        "Gói miễn phí của Google, hiểu giọng nói trực tiếp. Hạn mức thấp và đôi khi quá tải."),
    AiProvider(1, "Groq", "Groq", "https://api.groq.com/openai/v1", "console.groq.com/keys", "openai/gpt-oss-120b",
        "Miễn phí, không cần thẻ, trả lời rất nhanh (khoảng 30 lượt mỗi phút, 1000 lượt mỗi ngày). Có chuyển giọng nói thành chữ, nên dùng được micro."),
    AiProvider(2, "OpenRouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter.ai/keys", "openrouter/free",
        "Mặc định dùng bộ định tuyến openrouter/free: tự chọn một mô hình miễn phí đang chạy. Danh sách mô hình miễn phí (tên có đuôi :free) đổi liên tục; " +
            "nếu bạn nhập tên mô hình đã ngừng, app tự chọn mô hình miễn phí khác. Khoảng 20 lượt mỗi phút, 50 lượt mỗi ngày (1000 nếu nạp 10 USD)."),
    AiProvider(6, "Cloudflare", "Cloudflare Workers AI", "", "dash.cloudflare.com/profile/api-tokens", "@cf/openai/gpt-oss-120b",
        "Cần hai thứ: Account ID (trang Workers AI trong bảng điều khiển Cloudflare) và API token có quyền Workers AI. Miễn phí 10.000 neuron mỗi ngày; một số mô hình mới cần gói trả phí."),
    AiProvider(7, "NVIDIA", "NVIDIA (build.nvidia.com)", "https://integrate.api.nvidia.com/v1", "build.nvidia.com", "meta/llama-3.3-70b-instruct",
        "Gói miễn phí cho nhà phát triển, cần xác minh số điện thoại, khoảng 40 lượt mỗi phút. Khoá bắt đầu bằng nvapi-. Mô hình lớn kiểu suy luận có thể chậm."),
    AiProvider(5, "Khác", "Khác (dịch vụ tương thích OpenAI)", "", "", "",
        "Nhập địa chỉ API (kết thúc bằng /v1), khoá và tên mô hình của bất kỳ dịch vụ nào tương thích OpenAI, kể cả máy chủ riêng của bạn.")
)

fun aiProv(id: Int): AiProvider = AI_PROVIDERS.firstOrNull { it.id == id } ?: AI_PROVIDERS[0]

// Thứ tự thử dịch vụ dự phòng: nhanh và miễn phí rộng rãi trước
val AI_FALLBACK_ORDER = listOf(1, 7, 0, 6, 2, 5)

private fun aiCfg(p: UiPrefs): JSONObject = try { JSONObject(p.aiCfg) } catch (e: Exception) { JSONObject() }
private fun aiField(p: UiPrefs, id: Int, f: String): String = aiCfg(p).optJSONObject(id.toString())?.optString(f).orEmpty()

fun aiKey(p: UiPrefs, id: Int): String = (if (id == 0) p.geminiKey else aiField(p, id, "key")).trim()

// Giá trị đã lưu (có thể rỗng) và giá trị thực dùng (rỗng thì lấy mặc định của dịch vụ)
fun aiModelRaw(p: UiPrefs, id: Int): String = if (id == 0) p.geminiModel else aiField(p, id, "model")
fun aiModel(p: UiPrefs, id: Int): String = aiModelRaw(p, id).trim().ifEmpty { aiProv(id).defModel }
fun aiAcct(p: UiPrefs): String = aiField(p, 6, "acct").trim()   // Account ID của Cloudflare
fun aiUrl(p: UiPrefs, id: Int): String = when (id) {
    5 -> aiField(p, id, "url").trim().trimEnd('/')
    6 -> if (aiAcct(p).isEmpty()) "" else "https://api.cloudflare.com/client/v4/accounts/" + aiAcct(p) + "/ai/v1"
    else -> aiProv(id).base
}

fun UiPrefs.withAi(id: Int, key: String? = null, model: String? = null, url: String? = null, acct: String? = null): UiPrefs {
    if (id == 0) return copy(geminiKey = key ?: geminiKey, geminiModel = model ?: geminiModel)
    val cfg = aiCfg(this)
    val o = cfg.optJSONObject(id.toString()) ?: JSONObject()
    if (key != null) o.put("key", key)
    if (model != null) o.put("model", model)
    if (url != null) o.put("url", url)
    if (acct != null) o.put("acct", acct)
    cfg.put(id.toString(), o)
    return copy(aiCfg = cfg.toString())
}

fun aiReady(p: UiPrefs, id: Int): Boolean = aiKey(p, id).isNotEmpty() && (id != 5 || (aiUrl(p, id).isNotEmpty() && aiModel(p, id).isNotEmpty())) && (id != 6 || aiAcct(p).isNotEmpty())

// Chuẩn hoá mã dịch vụ đã lưu: dịch vụ đã bỏ (Mistral 3, Cerebras 4) hoặc lạ thì về Gemini
fun aiNormId(i: Int): Int = if (AI_PROVIDERS.any { it.id == i }) i else 0
fun aiAny(p: UiPrefs): Boolean = AI_PROVIDERS.any { aiReady(p, it.id) }

// Nói được khi có Gemini (nghe trực tiếp) hoặc Groq (chuyển giọng nói thành chữ)
fun aiVoiceOk(p: UiPrefs): Boolean = aiReady(p, 0) || aiReady(p, 1)

// provider: dịch vụ đã trả lời; newModel: tên mô hình mới nếu app đã tự đổi mô hình của dịch vụ đó (để lưu lại); note: ghi chú khi phải dùng dịch vụ dự phòng
class AiResult(val text: String?, val error: String?, val provider: Int = -1, val newModel: String? = null, val note: String? = null)

object Ai {
    // badModel: Google, OpenRouter... báo không có mô hình này (đã bị ngừng); chat() thử lại bằng mô hình khác
    private class Res(val text: String?, val error: String?, val badModel: Boolean = false)

    // withText: dựng lại câu hỏi từ văn bản khi dịch vụ không nghe được giọng nói (giọng nói được chuyển thành chữ trước)
    suspend fun ask(
        p: UiPrefs, system: String, user: String,
        audio: ByteArray? = null, json: Boolean = false, withText: ((String) -> String)? = null
    ): AiResult {
        val primary = aiNormId(p.aiProvider)
        val order = ArrayList<Int>()
        if (aiReady(p, primary)) order.add(primary)
        if (p.aiFallback || order.isEmpty()) for (i in AI_FALLBACK_ORDER) if (i != primary && aiReady(p, i)) order.add(i)
        if (order.isEmpty()) return AiResult(null, "Chưa nhập khoá API nào. Vào Cài đặt > Trợ lý AI để nhập.")
        val quick = order.size > 1   // có dịch vụ dự phòng thì không chờ thử lại lâu ở từng dịch vụ
        val errors = ArrayList<String>()
        var transcript: String? = null
        var triedTranscribe = false
        for ((idx, id) in order.withIndex()) {
            val prov = aiProv(id)
            val note = if (idx > 0) "Đã dùng " + prov.short + " vì " + aiProv(order[0]).short + " không trả lời được." else null
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
            var r = chat(p, id, system, u, null)
            // OpenRouter: danh sách mô hình miễn phí đổi liên tục; mô hình đã chọn bị ngừng thì tự chọn mô hình miễn phí khác và lưu lại
            if (r.text == null && r.badModel && id == 2) {
                val alt = pickOpenRouterFree()
                if (alt != null && alt != aiModel(p, id)) {
                    val r2 = chat(p, id, system, u, alt)
                    if (r2.text != null) return AiResult(r2.text, null, id, alt, note)
                    r = r2
                }
            }
            if (r.text != null) return AiResult(r.text, null, id, null, note)
            errors.add(r.error ?: (prov.short + ": lỗi không rõ"))
        }
        return AiResult(null, errors.joinToString("\n"))
    }

    // Gọi /chat/completions của dịch vụ tương thích OpenAI
    private suspend fun chat(p: UiPrefs, id: Int, system: String, user: String, modelOverride: String?): Res = withContext(Dispatchers.IO) {
        val prov = aiProv(id)
        val model = modelOverride ?: aiModel(p, id)
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
            if (code !in 200..299) {
                val bad = code == 404 || ((code == 400 || code == 422) && Regex("model|endpoint", RegexOption.IGNORE_CASE).containsMatchIn(txt))
                return@withContext Res(null, prov.short + ": " + errMsg(code, txt, model), bad)
            }
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

    // Hỏi OpenRouter danh sách mô hình (không cần khoá) và chọn một mô hình MIỄN PHÍ đang chạy: tên có đuôi :free, giá 0, nhận và trả văn bản,
    // ngữ cảnh từ 32 nghìn token; ưu tiên mô hình lớn, ngữ cảnh dài và không phải loại suy luận chậm.
    private suspend fun pickOpenRouterFree(): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://openrouter.ai/api/v1/models").openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            if (c.responseCode !in 200..299) { c.disconnect(); return@withContext null }
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val arr = JSONObject(txt).optJSONArray("data") ?: return@withContext null
            val bad = listOf("embed", "safety", "guard", "image", "audio", "tts", "speech", "moderation", "classif", "rerank")
            var best: String? = null
            var bestScore = -100.0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (!id.endsWith(":free") || bad.any { id.contains(it, ignoreCase = true) }) continue
                val pr = o.optJSONObject("pricing") ?: continue
                if (pr.optString("prompt") != "0" || pr.optString("completion") != "0") continue
                val mod = o.optJSONObject("architecture")?.optString("modality").orEmpty()
                if (mod.isNotEmpty() && !(mod.startsWith("text") && mod.endsWith("->text"))) continue
                val ctx = o.optDouble("context_length", 0.0)
                if (ctx < 32768.0) continue
                val params = Regex("""(\d+)b""", RegexOption.IGNORE_CASE).find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 20.0
                var score = minOf(ctx, 1_000_000.0) / 1_000_000.0 + minOf(params, 400.0) / 800.0
                if (id.contains("reasoning", ignoreCase = true) || id.contains("thinking", ignoreCase = true)) score -= 0.5
                if (score > bestScore) { bestScore = score; best = id }
            }
            best
        } catch (e: Exception) { null }
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
