package vn.navara.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ===== Bản đồ vẽ trực tiếp bằng Compose (không dùng WebView, không cần key, không cần tài khoản) =====
// - Ảnh nền: tile OpenStreetMap, tự tải và lưu vào bộ nhớ đệm (xem lại offline được những đoạn đã đi qua)
// - Luôn vẽ vị trí xe + đường xe vừa chạy (từ GPS), nên không có mạng vẫn thấy xe di chuyển
// - Nền tối #0A0E1A, đường cao tốc ngả cam, vết đi của xe màu cam #FF7A00

object TileCache {
    private val mem = LruCache<String, ImageBitmap>(160)
    private val busy = ConcurrentHashMap<String, Long>() // 0 = đang tải, khác 0 = lần lỗi gần nhất
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val version = MutableStateFlow(0)
    val okCount = MutableStateFlow(0)
    val failCount = MutableStateFlow(0)

    fun peek(z: Int, x: Int, y: Int): ImageBitmap? = mem.get("$z/$x/$y")

    fun request(ctx: Context, z: Int, x: Int, y: Int) {
        val key = "$z/$x/$y"
        if (mem.get(key) != null) return
        val now = System.currentTimeMillis()
        val prev = busy[key]
        if (prev != null && (prev == 0L || now - prev < 15000L)) return
        busy[key] = 0L
        val dir = File(ctx.applicationContext.cacheDir, "tiles")
        scope.launch {
            try {
                dir.mkdirs()
                val f = File(dir, "$z-$x-$y.png")
                if (!f.exists() || f.length() == 0L) {
                    val c = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
                    c.connectTimeout = 6000
                    c.readTimeout = 8000
                    c.setRequestProperty("User-Agent", APP_UA)
                    if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
                    val tmp = File(dir, "$z-$x-$y.tmp")
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    tmp.renameTo(f)
                }
                val bmp = BitmapFactory.decodeFile(f.path)
                if (bmp == null) { f.delete(); throw java.io.IOException("bad tile") }
                mem.put(key, bmp.asImageBitmap())
                busy.remove(key)
                okCount.value = okCount.value + 1
                version.value = version.value + 1
            } catch (e: Exception) {
                busy[key] = System.currentTimeMillis()
                failCount.value = failCount.value + 1
            }
        }
    }
}

// Vết đường xe vừa đi (giữ tối đa 600 điểm)
object Trail {
    val pts = ArrayList<DoubleArray>()
    val version = MutableStateFlow(0)
    fun add(l: Location?) {
        if (l == null || (l.hasAccuracy() && l.accuracy > 60f)) return
        synchronized(pts) {
            val last = pts.lastOrNull()
            if (last != null) {
                val r = FloatArray(1)
                Location.distanceBetween(last[0], last[1], l.latitude, l.longitude, r)
                if (r[0] < 6f) return
            }
            pts.add(doubleArrayOf(l.latitude, l.longitude))
            if (pts.size > 600) pts.removeAt(0)
        }
        version.value = version.value + 1
    }
}

// Đảo màu tile OSM thành nền tối, đường lớn ngả cam (tương đương CSS invert + hue-rotate(200deg) + saturate(1.7))
private fun darkTileFilter(): ColorFilter {
    val a = Math.toRadians(200.0)
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    val hue = arrayOf(
        floatArrayOf(0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f),
        floatArrayOf(0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f),
        floatArrayOf(0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f)
    )
    val sat = 1.7f
    val sm = arrayOf(
        floatArrayOf(0.213f + 0.787f * sat, 0.715f - 0.715f * sat, 0.072f - 0.072f * sat),
        floatArrayOf(0.213f - 0.213f * sat, 0.715f + 0.285f * sat, 0.072f - 0.072f * sat),
        floatArrayOf(0.213f - 0.213f * sat, 0.715f - 0.715f * sat, 0.072f + 0.928f * sat)
    )
    val m = Array(3) { FloatArray(3) }
    for (i in 0 until 3) for (j in 0 until 3) { var v = 0f; for (k in 0 until 3) v += sm[i][k] * hue[k][j]; m[i][j] = v }
    val br = 0.9f
    val arr = FloatArray(20)
    for (i in 0 until 3) {
        arr[i * 5] = -br * m[i][0]
        arr[i * 5 + 1] = -br * m[i][1]
        arr[i * 5 + 2] = -br * m[i][2]
        arr[i * 5 + 4] = br * 255f * (m[i][0] + m[i][1] + m[i][2])
    }
    arr[18] = 1f
    return ColorFilter.colorMatrix(ColorMatrix(arr))
}

// focusX: vị trí ngang của xe trên màn hình (0.5 = giữa, 0.25 = giữa nửa trái, để nửa phải dành cho phát nhạc)
@Composable
fun MapPane(modifier: Modifier, loc: Location?, dark: Boolean, zoom: Int, focusX: Float = 0.5f, pois: List<Poi> = emptyList(), headingUp: Boolean = false, marker: Int = 0) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    var sz by remember { mutableStateOf(IntSize.Zero) }
    val ver by TileCache.version.collectAsState()
    val tv by Trail.version.collectAsState()
    val ok by TileCache.okCount.collectAsState()
    val fails by TileCache.failCount.collectAsState()
    val filter = remember(dark) { if (dark) darkTileFilter() else null }

    // Hướng xe đã làm mượt (độ, cộng dồn để xoay theo đường ngắn nhất); đứng yên thì giữ hướng cuối cùng
    var cum by remember { mutableStateOf(0f) }
    LaunchedEffect(loc) {
        if (loc != null && loc.hasBearing() && loc.speed > 1.5f) {
            cum += ((loc.bearing - cum) % 360f + 540f) % 360f - 180f
        }
    }
    val ang by animateFloatAsState(cum, tween(700))

    val ts = 256f * dens                       // cạnh 1 ô tile trên màn hình (px)
    val n = 2.0.pow(zoom)
    val lat = loc?.latitude ?: 20.8449         // chưa có GPS: hiển thị tạm khu vực Hải Phòng
    val lng = loc?.longitude ?: 106.6881
    fun wx(lo: Double) = (lo + 180.0) / 360.0 * n * ts
    fun wy(la: Double): Double {
        val s = sin(Math.toRadians(la))
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * n * ts
    }
    val cx = wx(lng)
    val cy = wy(lat)
    val ni = n.toInt()
    // Khi xoay bản đồ theo hướng xe, các góc màn hình lộ ra vùng xa hơn nên cần tải thêm tile
    val reach = Math.hypot(maxOf(sz.width * focusX, sz.width * (1 - focusX)).toDouble(), sz.height / 2.0)
    val rx0 = if (headingUp) reach else sz.width * focusX.toDouble()
    val rx1 = if (headingUp) reach else sz.width * (1 - focusX).toDouble()
    val ry = if (headingUp) reach else sz.height / 2.0
    val tx0 = floor((cx - rx0) / ts).toInt()
    val tx1 = floor((cx + rx1) / ts).toInt()
    val ty0 = floor((cy - ry) / ts).toInt()
    val ty1 = floor((cy + ry) / ts).toInt()

    LaunchedEffect(tx0, tx1, ty0, ty1, zoom) {
        if (sz.width == 0) return@LaunchedEffect
        for (x in tx0..tx1) for (y in ty0..ty1) {
            if (y < 0 || y >= ni) continue
            TileCache.request(ctx, zoom, ((x % ni) + ni) % ni, y)
        }
    }
    // Thử tải lại các tile lỗi mỗi 15 giây (khi mạng vừa có lại)
    LaunchedEffect(zoom, tx0, ty0) {
        while (true) {
            kotlinx.coroutines.delay(15000)
            if (sz.width == 0) continue
            for (x in tx0..tx1) for (y in ty0..ty1) {
                if (y < 0 || y >= ni) continue
                if (TileCache.peek(zoom, ((x % ni) + ni) % ni, y) == null) TileCache.request(ctx, zoom, ((x % ni) + ni) % ni, y)
            }
        }
    }

    val mpp = 40075016.686 * cos(Math.toRadians(lat)) / (n * ts)   // mét trên mỗi px
    val nice = doubleArrayOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)
    val barM = nice.lastOrNull { it / mpp <= 90 * dens } ?: nice[0]
    val barDp = (barM / mpp / dens).toFloat()

    val orange = Color(0xFFFF7A00)
    val gridCol = if (dark) Color(0x14FFFFFF) else Color(0x14000000)
    val inkCol = if (dark) Color.White else Color(0xFF1A1F2E)  // chữ/thước tỉ lệ: trắng trên nền tối, đen trên nền sáng
    Box(modifier.onSizeChanged { sz = it }) {
        Canvas(Modifier.fillMaxSize()) {
            ver.hashCode(); tv.hashCode()
            drawRect(if (dark) Color(0xFF0A0E1A) else Color(0xFFE9E5DC))
            val w = size.width
            val h = size.height
            val left = cx - w * focusX
            val top = cy - h / 2
            val anchor = Offset(w * focusX, h / 2)
            val m = if (headingUp) maxOf(w, h) else 0f   // lề thêm khi xoay bản đồ
            rotate(if (headingUp) -ang else 0f, anchor) {
                // lưới nền mờ (hiện khi chưa có ảnh bản đồ)
                val g = ts / 2
                var gx = -(left % g).toFloat() - ceil(m / g) * g
                while (gx < w + m) { drawLine(gridCol, Offset(gx, -m), Offset(gx, h + m), 1f); gx += g }
                var gy = -(top % g).toFloat() - ceil(m / g) * g
                while (gy < h + m) { drawLine(gridCol, Offset(-m, gy), Offset(w + m, gy), 1f); gy += g }
                // tile bản đồ
                for (x in tx0..tx1) for (y in ty0..ty1) {
                    if (y < 0 || y >= ni) continue
                    val bmp = TileCache.peek(zoom, ((x % ni) + ni) % ni, y) ?: continue
                    drawImage(
                        bmp,
                        dstOffset = IntOffset((x * ts - left).roundToInt(), (y * ts - top).roundToInt()),
                        dstSize = IntSize(ceil(ts).toInt() + 1, ceil(ts).toInt() + 1),
                        colorFilter = filter
                    )
                }
                // vết đường xe đã đi
                val pts = synchronized(Trail.pts) { ArrayList(Trail.pts) }
                if (pts.size > 1) {
                    val p = Path()
                    pts.forEachIndexed { i, q ->
                        val px = (wx(q[1]) - left).toFloat()
                        val py = (wy(q[0]) - top).toFloat()
                        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                    drawPath(p, orange, style = Stroke(5f * dens, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // camera / biển giới hạn / điểm đã lưu
                for (p in pois) {
                    val px = (wx(p.lng) - left).toFloat()
                    val py = (wy(p.lat) - top).toFloat()
                    if (px < -20f - m || px > w + 20f + m || py < -20f - m || py > h + 20f + m) continue
                    val ring = if (p.kind == 3) Color(0xFFE8383D) else Color.White
                    val fill = when (p.kind) { 2 -> Color(0xFFB36BFF); 3 -> Color.White; else -> Color(0xFFE8383D) }
                    drawCircle(ring, 12f * dens, Offset(px, py))
                    drawCircle(fill, 8f * dens, Offset(px, py))
                }
            }
            // xe bán tải Navara nhìn từ trên xuống. Chế độ "theo hướng xe": xe luôn hướng lên, bản đồ xoay.
            if (loc != null) {
                val acc = (loc.accuracy / mpp).toFloat()
                if (acc > 14f * dens && acc < w) drawCircle(orange.copy(alpha = 0.12f), acc, anchor)
                val rot = if (headingUp) 0f else ang
                when (marker) {
                    1 -> rotate(rot, anchor) { drawArrow(anchor, dens, orange) }
                    else -> rotate(rot, anchor) { drawTruck(anchor, dens, orange) }
                }
            }
        }
        if (ok == 0 && fails > 0) {
            Text(
                "Chưa tải được bản đồ nền (kiểm tra mạng) – vẫn hiển thị vị trí và đường xe đi",
                color = Color(0xFF8A94B0), fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(focusX * 2).padding(top = 76.dp, start = 16.dp, end = 16.dp)
            )
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (barM >= 1000) "${(barM / 1000).toInt()} km" else "${barM.toInt()} m",
                color = inkCol, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(barDp.dp).height(3.dp).background(inkCol))
        }
        Text("© OpenStreetMap", color = if (dark) Color(0xFF8A94B0) else Color(0xFF5A6278), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 1.dp))
    }
}

// Xe bán tải cabin kép nhìn từ trên xuống, đầu xe hướng lên. u = px mỗi dp, c = tâm xe.
internal fun DrawScope.drawTruck(c: Offset, u: Float, body: Color) {
    fun rr(x0: Float, y0: Float, x1: Float, y1: Float, col: Color, rad: Float = 0f) =
        drawRoundRect(col, Offset(c.x + x0 * u, c.y + y0 * u), Size((x1 - x0) * u, (y1 - y0) * u), CornerRadius(rad * u, rad * u))
    val glass = Color(0xFF16213D)
    rr(-14f, -31f, 16f, 35f, Color(0x55000000), 7f)                       // bóng đổ
    for (x in floatArrayOf(-16.5f, 10.5f)) for (y in floatArrayOf(-24f, 14f)) rr(x, y, x + 6f, y + 11f, Color(0xFF111111), 2f)  // 4 bánh
    rr(-14f, -32f, 14f, 32f, body, 6f)                                    // thân xe
    drawRoundRect(
        Color.White, Offset(c.x - 14f * u, c.y - 32f * u), Size(28f * u, 64f * u),
        CornerRadius(6f * u, 6f * u), style = Stroke(2.5f * u)
    )
    val ws = Path().apply {                                               // kính trước
        moveTo(c.x - 8f * u, c.y - 15f * u); lineTo(c.x + 8f * u, c.y - 15f * u)
        lineTo(c.x + 10f * u, c.y - 8f * u); lineTo(c.x - 10f * u, c.y - 8f * u); close()
    }
    drawPath(ws, glass)
    rr(-10f, -8f, 10f, 3f, Color(0xFFFFA24A), 2f)                         // nóc cabin
    val rw = Path().apply {                                               // kính sau
        moveTo(c.x - 10f * u, c.y + 3f * u); lineTo(c.x + 10f * u, c.y + 3f * u)
        lineTo(c.x + 8f * u, c.y + 7f * u); lineTo(c.x - 8f * u, c.y + 7f * u); close()
    }
    drawPath(rw, glass)
    rr(-11.5f, 10f, 11.5f, 30.5f, Color(0xFF8A4300), 2f)                  // thùng sau
    rr(-9.5f, 12f, 9.5f, 28.5f, Color(0xFF2A1A0C), 1f)
    rr(-12f, -31.5f, -6f, -29.5f, Color.White, 1f)                        // đèn pha
    rr(6f, -31.5f, 12f, -29.5f, Color.White, 1f)
    rr(-12.5f, 30.5f, -8f, 32f, Color(0xFFE8383D), 0.5f)                  // đèn hậu
    rr(8f, 30.5f, 12.5f, 32f, Color(0xFFE8383D), 0.5f)
}

// Biểu tượng tam giác (mũi tên) chỉ hướng xe
internal fun DrawScope.drawArrow(c: Offset, u: Float, col: Color) {
    val r = 11f * u
    val a = Path().apply {
        moveTo(c.x, c.y - r * 1.7f)
        lineTo(c.x + r * 1.15f, c.y + r)
        lineTo(c.x, c.y + r * 0.4f)
        lineTo(c.x - r * 1.15f, c.y + r)
        close()
    }
    drawPath(a, Color.White, style = Stroke(5f * u, join = StrokeJoin.Round))
    drawPath(a, col, style = Fill)
}
