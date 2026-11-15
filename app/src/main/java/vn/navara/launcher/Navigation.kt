package vn.navara.launcher

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// ===== Dẫn đường trên bản đồ MapLibre =====
// - Tìm địa điểm: Nominatim (OpenStreetMap), không cần khoá API
// - Tính đường: OSRM (máy chủ demo công cộng, dữ liệu OpenStreetMap), chỉ dẫn từng chặng được dựng thành tiếng Việt ngay trong app
// - Theo dõi xe bám tuyến, tự tính lại đường khi đi lệch, đọc chỉ dẫn bằng giọng nói của máy (nếu máy có tiếng Việt)
// Máy chủ OSRM demo chỉ nên dùng cá nhân với lưu lượng nhỏ; nếu dùng nhiều, hãy đổi OSRM_BASE sang máy chủ định tuyến riêng.

private const val OSRM_BASE = "https://router.project-osrm.org/route/v1/driving/"
private const val NOMINATIM = "https://nominatim.openstreetmap.org/search"

data class Place(val name: String, val sub: String, val lat: Double, val lng: Double)

// kind: 0 mũi tên rẽ, 1 điểm đến, 2 vòng xuyến, 3 quay đầu. angle: 0 = thẳng, 90 = phải, -90 = trái.
// startM: khoảng cách từ đầu tuyến tới nơi thực hiện chỉ dẫn này. major = false là các chặng "đi tiếp" không cần báo.
data class NavStep(val text: String, val angle: Float, val kind: Int, val startM: Double, val major: Boolean)

class NavRoute(
    val pts: List<DoubleArray>,   // [vĩ độ, kinh độ]
    val cum: DoubleArray,         // khoảng cách cộng dồn dọc tuyến (m)
    val steps: List<NavStep>,
    val distM: Double,
    val durS: Double,
    val dest: Place,
)

data class NavState(
    val route: NavRoute? = null,
    val loading: Boolean = false,
    val searching: Boolean = false,
    val message: String? = null,
    val results: List<Place> = emptyList(),
    val nextIdx: Int = 0,          // chỉ dẫn sắp tới
    val toNextM: Double = 0.0,     // còn bao xa tới chỉ dẫn đó
    val remainM: Double = 0.0,
    val remainS: Double = 0.0,
    val offRoute: Boolean = false,
    val arrived: Boolean = false,
    val ver: Int = 0,              // tăng mỗi khi đổi tuyến, để bản đồ vẽ lại
)

fun fmtDist(m: Double): String {
    val vi = Locale.forLanguageTag("vi-VN")
    return when {
        m >= 10000 -> String.format(vi, "%.0f km", m / 1000)
        m >= 1000 -> String.format(vi, "%.1f km", m / 1000)
        else -> ((m / 10).toInt() * 10).toString() + " m"
    }
}

fun fmtDur(s: Double): String {
    val mins = max(1, Math.round(s / 60.0).toInt())
    return if (mins >= 60) (mins / 60).toString() + " giờ " + (mins % 60) + " phút" else "$mins phút"
}

object Navigator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var trackJob: Job? = null
    @Volatile private var lastSeg = 0
    @Volatile private var farCount = 0
    @Volatile private var lastReroute = 0L
    private val said: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    private var tts: TextToSpeech? = null
    @Volatile private var ttsOk = false

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- Tìm địa điểm ----------
    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searching = false, results = emptyList(), message = null) }
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }

    fun search(query: String, near: Location?) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(searching = true, message = null, results = emptyList()) }
        searchJob = scope.launch {
            try {
                var url = NOMINATIM + "?format=jsonv2&limit=8&accept-language=vi&q=" + enc(q)
                if (near != null) {
                    // ưu tiên (không bắt buộc) kết quả quanh vị trí xe
                    url += "&viewbox=" + (near.longitude - 0.6) + "," + (near.latitude + 0.6) + "," + (near.longitude + 0.6) + "," + (near.latitude - 0.6)
                }
                val arr = JSONArray(httpGetText(url))
                val out = ArrayList<Place>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val disp = o.optString("display_name")
                    val nm = o.optString("name").ifBlank { disp.substringBefore(",") }
                    out.add(Place(nm, disp, o.getString("lat").toDouble(), o.getString("lon").toDouble()))
                }
                _state.update { it.copy(searching = false, results = out, message = if (out.isEmpty()) "Không tìm thấy địa điểm này" else null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, message = "Không tìm được, kiểm tra kết nối mạng") }
            }
        }
    }

    // ---------- Điểm đến gần đây ----------
    fun recent(ctx: Context): List<Place> = try {
        val s = ctx.getSharedPreferences("nav", Context.MODE_PRIVATE).getString("recent", "") ?: ""
        s.split("\n").mapNotNull { ln ->
            val p = ln.split("\t")
            if (p.size < 4) null else Place(p[0], p[1], p[2].toDouble(), p[3].toDouble())
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun addRecent(ctx: Context, d: Place) {
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        val list = (listOf(d) + recent(ctx).filter { it.name != d.name || hypot(it.lat - d.lat, it.lng - d.lng) > 1e-4 }).take(6)
        ctx.getSharedPreferences("nav", Context.MODE_PRIVATE).edit()
            .putString("recent", list.joinToString("\n") { clean(it.name) + "\t" + clean(it.sub) + "\t" + it.lat + "\t" + it.lng })
            .apply()
    }

    // ---------- Bắt đầu / dừng dẫn đường ----------
    fun start(ctx: Context, from: Location?, dest: Place) {
        if (from == null) {
            _state.update { it.copy(message = "Chưa có vị trí GPS để dẫn đường") }
            return
        }
        val app = ctx.applicationContext
        addRecent(app, dest)
        requestRoute(app, from, dest, true)
        // Theo dõi vị trí ngay trong Navigator nên vẫn dẫn đường khi người dùng chuyển sang trang khác
        trackJob?.cancel()
        trackJob = scope.launch {
            LocationHolder.loc.collect { l -> onLocation(app, l) }
        }
    }

    fun stop() {
        routeJob?.cancel()
        trackJob?.cancel()
        Handler(Looper.getMainLooper()).post { try { tts?.stop() } catch (e: Exception) { } }
        _state.update { NavState(ver = it.ver + 1) }
    }

    private fun requestRoute(ctx: Context, from: Location, dest: Place, first: Boolean) {
        routeJob?.cancel()
        _state.update { it.copy(loading = true, message = null, results = emptyList()) }
        routeJob = scope.launch {
            try {
                val r = fetchRoute(from.latitude, from.longitude, dest)
                lastSeg = 0
                farCount = 0
                said.clear()
                _state.update {
                    NavState(
                        route = r, nextIdx = 0, toNextM = 0.0, remainM = r.distM, remainS = r.durS, ver = it.ver + 1
                    )
                }
                if (first && loadPrefs(ctx).navVoice) speak(ctx, "Bắt đầu dẫn đường, quãng đường " + fmtDist(r.distM))
                onLocation(ctx, LocationHolder.loc.value)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = "Không tìm được đường đi, kiểm tra kết nối mạng") }
            }
        }
    }

    // ---------- Tính đường (OSRM) ----------
    private fun dist(a: DoubleArray, b: DoubleArray): Double {
        val dy = (b[0] - a[0]) * 110574.0
        val dx = (b[1] - a[1]) * 111320.0 * cos(Math.toRadians((a[0] + b[0]) / 2))
        return hypot(dx, dy)
    }

    private fun fetchRoute(lat: Double, lng: Double, d: Place): NavRoute {
        val url = OSRM_BASE + lng + "," + lat + ";" + d.lng + "," + d.lat + "?overview=full&geometries=geojson&steps=true"
        val j = JSONObject(httpGetText(url))
        if (j.optString("code") != "Ok") throw java.io.IOException("Không có đường đi")
        val r = j.getJSONArray("routes").getJSONObject(0)
        val co = r.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = ArrayList<DoubleArray>(co.length())
        for (i in 0 until co.length()) {
            val c = co.getJSONArray(i)
            pts.add(doubleArrayOf(c.getDouble(1), c.getDouble(0)))
        }
        if (pts.size < 2) throw java.io.IOException("Tuyến quá ngắn")
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + dist(pts[i - 1], pts[i])
        val total = cum[cum.size - 1]
        val osrmDist = r.getDouble("distance")
        val k = if (osrmDist > 1.0) total / osrmDist else 1.0
        val stepsJ = r.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        val steps = ArrayList<NavStep>()
        var acc = 0.0
        for (i in 0 until stepsJ.length()) {
            val s = stepsJ.getJSONObject(i)
            val m = s.getJSONObject("maneuver")
            steps.add(makeStep(m.optString("type"), m.optString("modifier"), s.optString("name"), m.optInt("exit", 0), acc * k))
            acc += s.getDouble("distance")
        }
        if (steps.isEmpty() || steps[steps.size - 1].kind != 1) steps.add(NavStep("Đến nơi", 0f, 1, total, true))
        return NavRoute(pts, cum, steps, total, r.getDouble("duration"), d)
    }

    // Dựng câu chỉ dẫn tiếng Việt từ kiểu và hướng rẽ của OSRM
    private fun makeStep(type: String, mod: String, name: String, exit: Int, startM: Double): NavStep {
        val on = if (name.isNotBlank()) " vào $name" else ""
        val ang = when (mod) {
            "left" -> -90f
            "right" -> 90f
            "slight left" -> -45f
            "slight right" -> 45f
            "sharp left" -> -135f
            "sharp right" -> 135f
            "uturn" -> 180f
            else -> 0f
        }
        val side = when (mod) {
            "left", "slight left", "sharp left" -> "trái"
            "right", "slight right", "sharp right" -> "phải"
            else -> ""
        }
        return when (type) {
            "depart" -> NavStep(if (name.isNotBlank()) "Xuất phát theo $name" else "Xuất phát", 0f, 0, startM, true)
            "arrive" -> NavStep("Đến nơi", 0f, 1, startM, true)
            "roundabout", "rotary", "roundabout turn" ->
                NavStep(if (exit > 0) "Vào vòng xuyến, ra lối thứ $exit$on" else "Vào vòng xuyến$on", ang, 2, startM, true)
            "exit roundabout", "exit rotary" -> NavStep("Ra khỏi vòng xuyến$on", ang, 0, startM, true)
            else -> {
                if (mod == "uturn") {
                    NavStep("Quay đầu xe", 180f, 3, startM, true)
                } else {
                    val quiet = (type == "new name" || type == "continue" || type == "notification") && (mod == "" || mod == "straight")
                    val txt = when (type) {
                        "fork" -> if (side.isNotEmpty()) "Đi theo nhánh bên $side$on" else "Đi theo nhánh giữa$on"
                        "merge" -> "Nhập làn$on"
                        "on ramp" -> "Vào đường dẫn$on"
                        "off ramp" -> "Ra khỏi đường chính$on"
                        "end of road" -> if (side.isNotEmpty()) "Cuối đường, rẽ $side$on" else "Cuối đường$on"
                        "new name", "continue", "notification" -> "Đi tiếp$on"
                        else -> when (mod) {
                            "left", "right" -> "Rẽ $side$on"
                            "slight left", "slight right" -> "Chếch sang $side$on"
                            "sharp left", "sharp right" -> "Rẽ gắt sang $side$on"
                            else -> "Đi thẳng$on"
                        }
                    }
                    NavStep(txt, ang, 0, startM, !quiet)
                }
            }
        }
    }

    // ---------- Theo dõi hành trình ----------
    private fun onLocation(ctx: Context, l: Location?) {
        val st = _state.value
        val r = st.route ?: return
        if (l == null || st.arrived || r.pts.size < 2) return

        // Chiếu vị trí xe lên đường tuyến (chỉ xét đoạn quanh vị trí trước đó; lệch nhiều thì quét lại toàn tuyến)
        val n = r.pts.size
        val lat0 = l.latitude
        val kx = 111320.0 * cos(Math.toRadians(lat0))
        val ky = 110574.0
        val full = farCount >= 2
        val from = if (full) 0 else max(0, lastSeg - 3)
        val to = if (full) n - 2 else min(n - 2, lastSeg + 250)
        var best = Double.MAX_VALUE
        var bestSeg = from
        var bestT = 0.0
        for (i in from..to) {
            val ax = (r.pts[i][1] - l.longitude) * kx
            val ay = (r.pts[i][0] - lat0) * ky
            val bx = (r.pts[i + 1][1] - l.longitude) * kx
            val by = (r.pts[i + 1][0] - lat0) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-6) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best) { best = d; bestSeg = i; bestT = t }
        }

        val limit = max(45.0, if (l.hasAccuracy()) (l.accuracy * 1.5f).toDouble() else 0.0)
        if (best > limit) {
            farCount++
            if (farCount >= 2) _state.update { it.copy(offRoute = true) }
            val now = System.currentTimeMillis()
            if (farCount >= 3 && now - lastReroute > 10000L) {
                lastReroute = now
                requestRoute(ctx, l, r.dest, false)
            }
            return
        }
        farCount = 0
        lastSeg = bestSeg

        val progress = r.cum[bestSeg] + bestT * (r.cum[bestSeg + 1] - r.cum[bestSeg])
        val remain = max(0.0, r.distM - progress)
        var idx = r.steps.indexOfFirst { it.major && it.startM > progress + 5.0 }
        if (idx < 0) idx = r.steps.size - 1
        val toNext = max(0.0, r.steps[idx].startM - progress)
        val arrived = remain < 25.0
        val voice = loadPrefs(ctx).navVoice

        _state.update {
            it.copy(
                nextIdx = idx, toNextM = toNext, remainM = remain,
                remainS = if (r.distM > 1.0) r.durS * remain / r.distM else 0.0,
                offRoute = false, loading = false, arrived = arrived
            )
        }

        if (arrived) {
            if (voice) speak(ctx, "Bạn đã đến nơi")
            scope.launch {
                delay(8000)
                if (_state.value.arrived) stop()
            }
            return
        }
        if (voice) {
            // Báo trước 500 m, 200 m và ngay tại điểm rẽ; mỗi mức chỉ báo một lần cho mỗi chỉ dẫn
            val levels = intArrayOf(40, 200, 500)
            val lv = levels.firstOrNull { toNext <= it }
            if (lv != null && said.add(idx * 1000 + lv)) {
                for (o in levels) if (o > lv) said.add(idx * 1000 + o)
                val step = r.steps[idx]
                val txt = when {
                    lv <= 40 -> step.text
                    step.kind == 1 -> "Sau $lv mét, bạn sẽ đến nơi"
                    else -> "Sau $lv mét, " + step.text.replaceFirstChar { c -> c.lowercase() }
                }
                speak(ctx, txt)
            }
        }
    }

    // ---------- Giọng nói ----------
    private fun speak(ctx: Context, text: String) {
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                val t = tts
                if (t == null) {
                    tts = TextToSpeech(app) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            val res = tts?.setLanguage(Locale.forLanguageTag("vi-VN")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                            ttsOk = res >= TextToSpeech.LANG_AVAILABLE
                            if (ttsOk) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
                        }
                    }
                } else if (ttsOk) {
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
                }
            } catch (e: Exception) { }
        }
    }
}
