package vn.navara.launcher

import android.content.Context
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

// ===== Cảnh báo giao thông =====
// Nguồn dữ liệu: OpenStreetMap (qua Overpass API, cần mạng) + các điểm camera do chính bạn lưu (lưu trong máy).
// - Giới hạn tốc độ: lấy từ thẻ maxspeed của đoạn đường đang đi. Đoạn nào OSM chưa có thì KHÔNG hiện giới hạn (không đoán).
// - Camera / biển giới hạn tốc độ: các nút có trong OSM trong bán kính 1,5 km, chỉ báo khi ở phía trước xe.
// - Điểm của bạn: bấm "Lưu cam" lúc đi qua camera, lần sau sẽ được báo trước.
// Chỉ là hỗ trợ tham khảo, luôn ưu tiên biển báo thực tế trên đường.

// kind: 0 camera tốc độ, 1 camera giám sát khác, 2 điểm do bạn lưu, 3 biển giới hạn tốc độ
data class Poi(val lat: Double, val lng: Double, val kind: Int, val limit: Int? = null, val brg: Float? = null)

data class TrafficState(
    val limit: Int? = null,
    val road: String = "",
    val poi: Poi? = null,
    val poiDist: Int = 0,
    val pois: List<Poi> = emptyList(),
    val online: Boolean = true,
)

// Vượt quá giới hạn từ 4 km/h trở lên thì cảnh báo
fun overLimit(spd: Int, lim: Int?): Boolean = lim != null && spd > lim + 3

private fun parseSpeed(s: String?): Int? {
    if (s.isNullOrEmpty()) return null
    val m = Regex("^\\s*(\\d{2,3})\\s*(mph)?").find(s.lowercase()) ?: return null
    val v = m.groupValues[1].toInt()
    return if (m.groupValues[2] == "mph") (v * 1.609).roundToInt() else v
}

private fun angDiff(a: Float, b: Float): Float {
    var d = abs(a - b) % 360f
    if (d > 180f) d = 360f - d
    return d
}

object TrafficAlert {
    private const val API = "https://overpass-api.de/api/interpreter"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile var enabled = true
    @Volatile private var cams: List<Poi> = emptyList()
    @Volatile private var mine: List<Poi> = emptyList()
    @Volatile private var limit: Int? = null
    @Volatile private var road = ""
    @Volatile private var online = true
    val state = MutableStateFlow(TrafficState())

    fun start(ctx: Context) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        mine = loadMine(app)
        job = scope.launch {
            launch { refreshLoop() }
            launch { nearestLoop() }
        }
    }

    private fun overpass(q: String): JSONObject? = try {
        val c = URL(API + "?data=" + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", APP_UA)
        if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        Log.d("Traffic", "overpass: " + e.message)
        null
    }

    // Hỏi OSM: giới hạn tốc độ đoạn đường hiện tại (mỗi ~80 m) và camera quanh xe (mỗi ~700 m)
    private suspend fun refreshLoop() {
        var limAt: Location? = null
        var limTime = 0L
        var camAt: Location? = null
        var camTime = 0L
        var camOk = false
        while (true) {
            delay(2000)
            val now = System.currentTimeMillis()
            val l = LocationHolder.loc.value
            if (!enabled) { limit = null; road = ""; cams = emptyList(); camAt = null; continue }
            if (l == null || !hasFix(l) || (l.hasAccuracy() && l.accuracy > 35f)) {
                if (now - limTime > 90_000L) limit = null
                continue
            }
            val moved = limAt?.distanceTo(l) ?: Float.MAX_VALUE
            val running = l.hasSpeed() && l.speed > 1f
            if (limAt == null || (running && moved > 80f && now - limTime > 5000L)) {
                limAt = l
                limTime = now
                val j = overpass("[out:json][timeout:8];way(around:18,${l.latitude},${l.longitude})[\"highway\"][\"maxspeed\"];out tags;")
                if (j != null) {
                    online = true
                    var best: Int? = null
                    var nm = ""
                    val els = j.optJSONArray("elements")
                    if (els != null) {
                        for (i in 0 until els.length()) {
                            val tg = els.getJSONObject(i).optJSONObject("tags") ?: continue
                            val v = parseSpeed(tg.optString("maxspeed", "")) ?: continue
                            if (best == null || v > best) { best = v; nm = tg.optString("name", "") }
                        }
                    }
                    limit = best
                    road = nm
                } else online = false
            }
            val cMoved = camAt?.distanceTo(l) ?: Float.MAX_VALUE
            if (camAt == null || cMoved > 700f || (!camOk && now - camTime > 30_000L)) {
                camAt = l
                camTime = now
                val a = "around:1500,${l.latitude},${l.longitude}"
                val q = "[out:json][timeout:10];(node($a)[\"highway\"=\"speed_camera\"];node($a)[\"enforcement\"];node($a)[\"traffic_sign\"~\"maxspeed\"];);out;"
                val j = overpass(q)
                if (j != null) { camOk = true; online = true; cams = parseCams(j) } else { camOk = false; online = false }
            }
        }
    }

    private fun parseCams(j: JSONObject): List<Poi> {
        val out = ArrayList<Poi>()
        val els = j.optJSONArray("elements") ?: return out
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            if (e.optString("type") != "node") continue
            val tg = e.optJSONObject("tags") ?: continue
            val hw = tg.optString("highway", "")
            val enf = tg.optString("enforcement", "")
            val ts = tg.optString("traffic_sign", "")
            val kind = when {
                hw == "speed_camera" || enf == "maxspeed" -> 0
                enf.isNotEmpty() -> 1
                ts.contains("maxspeed") -> 3
                else -> continue
            }
            out.add(Poi(e.getDouble("lat"), e.getDouble("lon"), kind, parseSpeed(tg.optString("maxspeed", ""))))
        }
        return out
    }

    // Mỗi lần có vị trí mới: tìm điểm cảnh báo gần nhất nằm phía trước xe
    private suspend fun nearestLoop() {
        LocationHolder.loc.collect { l ->
            if (!enabled || l == null) {
                state.value = TrafficState()
                return@collect
            }
            val all = cams + mine
            var best: Poi? = null
            var bd = Float.MAX_VALUE
            val moving = hasFix(l) && l.hasBearing() && l.speed > 2.2f
            if (moving) {
                val range = (l.speed * 14f).coerceIn(250f, 700f)
                val r = FloatArray(3)
                for (p in all) {
                    Location.distanceBetween(l.latitude, l.longitude, p.lat, p.lng, r)
                    val d = r[0]
                    val maxD = if (p.kind == 3) minOf(range, 350f) else range
                    if (d > maxD || d >= bd) continue
                    if (angDiff(r[1], l.bearing) > 45f) continue
                    val pb = p.brg
                    if (pb != null && angDiff(pb, l.bearing) > 60f) continue
                    best = p
                    bd = d
                }
            }
            state.value = TrafficState(limit, road, best, if (best != null) bd.toInt() else 0, all, online)
        }
    }

    // ----- Điểm camera do người dùng lưu -----
    private fun loadMine(ctx: Context): List<Poi> = try {
        val f = File(ctx.filesDir, "alerts.txt")
        if (!f.exists()) emptyList() else f.readLines().mapNotNull { line ->
            val p = line.split(",")
            if (p.size < 3) null else Poi(p[0].toDouble(), p[1].toDouble(), 2, null, p.getOrNull(3)?.toFloatOrNull())
        }
    } catch (e: Exception) { emptyList() }

    private fun saveMine(ctx: Context) {
        runCatching {
            File(ctx.applicationContext.filesDir, "alerts.txt")
                .writeText(mine.joinToString("\n") { "${it.lat},${it.lng},2,${it.brg ?: ""}" })
        }
    }

    fun addMine(ctx: Context, l: Location?): Boolean {
        if (l == null || !hasFix(l)) return false
        val b = if (l.hasBearing() && l.speed > 2f) l.bearing else null
        mine = mine + Poi(l.latitude, l.longitude, 2, null, b)
        saveMine(ctx)
        return true
    }

    fun undoMine(ctx: Context): Boolean {
        if (mine.isEmpty()) return false
        mine = mine.dropLast(1)
        saveMine(ctx)
        return true
    }

    fun beep() {
        scope.launch {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 450)
                delay(800)
                tg.release()
            } catch (e: Exception) { }
        }
    }
}

// Biển giới hạn tốc độ (tròn, viền đỏ); nhấp nháy đỏ khi đang chạy quá tốc độ
@Composable
fun SpeedSign(limit: Int, flash: Boolean, modifier: Modifier = Modifier) {
    val red = Color(0xFFE8383D)
    Box(
        modifier.size(66.dp).clip(CircleShape).background(if (flash) red else Color.White).border(7.dp, red, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(limit.toString(), color = if (flash) Color.White else Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PoiBanner(p: Poi, dist: Int, modifier: Modifier = Modifier) {
    val label = when (p.kind) {
        0 -> "CAMERA TỐC ĐỘ"
        1 -> "CAMERA GIÁM SÁT"
        2 -> "ĐIỂM CẢNH BÁO"
        else -> "BIỂN GIỚI HẠN " + (p.limit?.toString() ?: "")
    }
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8383D)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (p.kind == 3) Icons.Filled.Warning else Icons.Filled.Videocam, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "còn " + (dist / 10 * 10) + " m" + (if (p.kind != 3 && p.limit != null) " • GH " + p.limit else ""),
                color = Color.White, fontSize = 18.sp
            )
        }
    }
}
