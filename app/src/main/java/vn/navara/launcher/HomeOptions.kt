package vn.navara.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

// ===== Tuỳ chọn trang 1: thông số OBD, hình xe, cách hiện cấp số =====

// ---------- Di chuyển các ô ở trang 1 ----------

// Hàm cập nhật cài đặt do Root cung cấp, để ô kéo xong tự lưu vị trí
val LocalPrefUpdate = staticCompositionLocalOf<(UiPrefs) -> Unit> { {} }

// Chế độ sắp xếp trang 1 (bật từ Cài đặt, tắt bằng nút Xong)
object HomeEdit { val on = MutableStateFlow(false) }

fun parseOffsets(s: String): Map<String, Offset> = s.split(";").mapNotNull { e ->
    val kv = e.split("=")
    if (kv.size != 2) return@mapNotNull null
    val xy = kv[1].split(",")
    val x = xy.getOrNull(0)?.toFloatOrNull()
    val y = xy.getOrNull(1)?.toFloatOrNull()
    if (x == null || y == null) null else kv[0] to Offset(x, y)
}.toMap()

fun offsetsToString(m: Map<String, Offset>): String = m.entries.joinToString(";") { it.key + "=" + it.value.x.toInt() + "," + it.value.y.toInt() }

// Dịch ô theo vị trí đã lưu (dp). Đang ở chế độ sắp xếp thì có viền nổi bật và kéo được; kéo xong lưu ngay.
@Composable
fun Modifier.movable(id: String): Modifier {
    val t = LocalTheme.current
    val cur by rememberUpdatedState(t.p)
    val upd by rememberUpdatedState(LocalPrefUpdate.current)
    val editing by HomeEdit.on.collectAsState()
    val density = LocalDensity.current.density
    val saved = parseOffsets(t.p.homeOffsets)[id] ?: Offset.Zero
    var drag by remember(id, saved) { mutableStateOf(Offset.Zero) }
    var m = this.offset { IntOffset((saved.x * density + drag.x).roundToInt(), (saved.y * density + drag.y).roundToInt()) }
    if (editing) {
        m = m.border(1.5.dp, t.accent).pointerInput(id, saved) {
            detectDragGestures(
                onDragEnd = {
                    val all = parseOffsets(cur.homeOffsets).toMutableMap()
                    all[id] = Offset((saved.x + drag.x / density).coerceIn(-320f, 320f), (saved.y + drag.y / density).coerceIn(-320f, 320f))
                    upd(cur.copy(homeOffsets = offsetsToString(all)))
                },
                onDragCancel = { drag = Offset.Zero }
            ) { change, amount -> change.consume(); drag += amount }
        }
    }
    return m
}

// Thanh hiện giữa trang khi đang sắp xếp
@Composable
fun HomeEditBar(modifier: Modifier) {
    val t = LocalTheme.current
    val upd = LocalPrefUpdate.current
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xE6101418)).border(2.dp, t.accent, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kéo các ô để đổi vị trí", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A313B)).clickable { upd(t.p.copy(homeOffsets = "")) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Đặt lại", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(t.accent).clickable { HomeEdit.on.value = false }.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("Xong", color = t.onAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- Thông số cho 6 ô ở trang 1 ----------

class HomeMetric(val key: String, val label: String, val unit: String, val dec: Int, val warn: Float? = null)

val HOME_METRICS = listOf(
    HomeMetric("rpm", "RPM", "", 0),
    HomeMetric("speed", "TỐC ĐỘ", "km/h", 0),
    HomeMetric("load", "ENGINE LOAD", "%", 0),
    HomeMetric("coolant", "NHIỆT ĐỘ NƯỚC", "°C", 0, 105f),
    HomeMetric("boost", "TURBO BOOST", "BAR", 2),
    HomeMetric("iat", "NHIỆT ĐỘ KHÍ NẠP", "°C", 0),
    HomeMetric("volt", "ĐIỆN ÁP ẮC QUY", "V", 1),
    HomeMetric("pedal", "CHÂN GA", "%", 0),
    HomeMetric("torqueNm", "MÔ-MEN", "Nm", 0),
    HomeMetric("power", "CÔNG SUẤT", "kW", 0),
    HomeMetric("oil", "NHIỆT ĐỘ DẦU", "°C", 0, 120f),
    HomeMetric("egt", "NHIỆT ĐỘ XẢ EGT", "°C", 0, 700f),
    HomeMetric("map", "ÁP SUẤT HÚT", "kPa", 0),
    HomeMetric("maf", "LƯU LƯỢNG KHÍ", "g/s", 1),
    HomeMetric("fuelRate", "TIÊU THỤ", "L/h", 1),
    HomeMetric("lper100", "TIÊU HAO", "L/100", 1),
    HomeMetric("fuelLevel", "MỨC NHIÊN LIỆU", "%", 0),
    HomeMetric("ambient", "NHIỆT ĐỘ NGOÀI", "°C", 0),
    HomeMetric("throttle", "BƯỚM GA", "%", 0),
    HomeMetric("timing", "ĐÁNH LỬA SỚM", "°", 1),
    HomeMetric("stft", "NHIÊN LIỆU NGẮN HẠN", "%", 1),
    HomeMetric("ltft", "NHIÊN LIỆU DÀI HẠN", "%", 1),
    HomeMetric("catTemp", "NHIỆT ĐỘ XÚC TÁC", "°C", 0, 850f),
)

// Thông số phù hợp với xe đang chọn (ví dụ xe hút khí tự nhiên không có boost, xe dầu không có hiệu chỉnh nhiên liệu xăng)
fun homePool(v: VehicleProfile): List<HomeMetric> = HOME_METRICS.filter { it.key !in v.homeHide }

val HOME_DEFAULT_TILES = listOf("rpm", "load", "coolant", "boost", "iat", "volt")

fun homeMetric(key: String): HomeMetric = HOME_METRICS.firstOrNull { it.key == key } ?: HOME_METRICS[0]

// Chuỗi đã lưu -> đúng 6 khoá hợp lệ (thiếu hoặc lạ thì lấy theo mặc định)
fun parseHomeTiles(s: String): List<String> {
    val got = s.split(",").map { it.trim() }
    return List(6) { i -> got.getOrNull(i)?.takeIf { k -> HOME_METRICS.any { it.key == k } } ?: HOME_DEFAULT_TILES[i] }
}

// Giá trị hiện tại của một thông số; null = xe chưa trả về
fun homeMetricValue(d: ObdData, key: String): Float? = when (key) {
    "rpm" -> d.rpm.toFloat()
    "speed" -> d.speed.toFloat()
    "load" -> d.load?.toFloat()
    "coolant" -> d.coolant?.toFloat()
    "boost" -> d.boost
    "iat" -> d.intake?.toFloat() ?: d.ex["iat"]
    "volt" -> d.volt
    "ambient" -> d.ambient?.toFloat() ?: d.ex["ambient"]
    else -> d.ex[key]
}

// Biểu tượng của từng thông số (hiện trong ô thông số ở trang 1)
fun homeIcon(key: String): ImageVector = when (key) {
    "rpm" -> Icons.Filled.Autorenew
    "speed" -> Icons.Filled.Speed
    "load" -> Icons.Filled.Tune
    "coolant" -> Icons.Filled.Thermostat
    "boost" -> Icons.Filled.Compress
    "iat" -> Icons.Filled.Air
    "volt" -> Icons.Filled.BatteryChargingFull
    "pedal" -> Icons.Filled.TouchApp
    "torqueNm" -> Icons.Filled.RotateRight
    "power" -> Icons.Filled.Bolt
    "oil" -> Icons.Filled.WaterDrop
    "egt" -> Icons.Filled.LocalFireDepartment
    "map" -> Icons.Filled.Cached
    "maf" -> Icons.Filled.Air
    "fuelRate", "lper100", "fuelLevel" -> Icons.Filled.LocalGasStation
    "ambient" -> Icons.Filled.WbSunny
    "timing" -> Icons.Filled.FlashOn
    "catTemp" -> Icons.Filled.Thermostat
    else -> Icons.Filled.Tune
}

// Biểu tượng của ô thông số. Năm thông số chính được vẽ riêng cho đúng hình: đồng hồ vòng tua, động cơ, turbo, dòng khí, ắc quy.
// Các thông số còn lại dùng bộ biểu tượng có sẵn. Hệ toạ độ 24 x 24, nét tròn đầu.
@Composable
fun HomeGlyph(key: String, tint: Color, modifier: Modifier = Modifier) {
    if (key !in setOf("rpm", "load", "boost", "iat", "volt")) {
        androidx.compose.material3.Icon(homeIcon(key), null, tint = tint, modifier = modifier)
        return
    }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val sw = 1.9f * s
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun poly(cl: Boolean, vararg c: Float): Path = Path().apply {
            moveTo(c[0] * s, c[1] * s)
            var i = 2
            while (i < c.size) { lineTo(c[i] * s, c[i + 1] * s); i += 2 }
            if (cl) close()
        }
        when (key) {
            "rpm" -> {   // đồng hồ vòng tua: cung 270 độ, vạch chia, kim và tâm
                drawArc(tint, 135f, 270f, false, topLeft = p(3f, 4f), size = Size(18f * s, 18f * s), style = st)
                for (a in listOf(180.0, 225.0, 270.0, 315.0, 360.0)) {
                    val c = Math.cos(Math.toRadians(a)).toFloat(); val n = Math.sin(Math.toRadians(a)).toFloat()
                    drawLine(tint, p(12f + 6.1f * c, 13f + 6.1f * n), p(12f + 7.6f * c, 13f + 7.6f * n), sw * 0.7f, StrokeCap.Round)
                }
                drawLine(tint, p(12f, 13f), p(16.6f, 8.4f), sw, StrokeCap.Round)
                drawCircle(tint, 1.7f * s, p(12f, 13f))
            }
            "load" -> {   // động cơ: nắp trên, hai đầu trục, thân khối máy
                drawPath(poly(true, 6f, 9f, 9f, 9f, 9f, 6f, 15f, 6f, 15f, 9f, 18f, 9f, 18f, 11f, 21f, 11f, 21f, 16f, 18f, 16f, 18f, 18f, 8f, 18f, 6f, 16f, 3f, 16f, 3f, 11f, 6f, 11f), tint, style = st)
                drawLine(tint, p(10f, 13.5f), p(14f, 13.5f), sw * 0.8f, StrokeCap.Round)
            }
            "boost" -> {   // turbo: vỏ sên tròn, cánh tuabin, cửa ra và mặt bích vào
                drawCircle(tint, 7.2f * s, p(10.5f, 13.5f), style = st)
                drawCircle(tint, 1.5f * s, p(10.5f, 13.5f))
                for (a in listOf(45.0, 135.0, 225.0, 315.0)) {
                    val c = Math.cos(Math.toRadians(a)).toFloat(); val n = Math.sin(Math.toRadians(a)).toFloat()
                    drawLine(tint, p(10.5f + 1.8f * c, 13.5f + 1.8f * n), p(10.5f + 5f * c, 13.5f + 5f * n), sw * 0.8f, StrokeCap.Round)
                }
                drawPath(poly(false, 15.4f, 8.2f, 20.5f, 5f, 20.5f, 10f), tint, style = st)
                drawPath(poly(false, 2.8f, 10.5f, 2.8f, 16.5f), tint, style = st)
            }
            "iat" -> {   // dòng khí: ba làn gió cuộn đầu
                drawPath(Path().apply { moveTo(3f * s, 8f * s); lineTo(14f * s, 8f * s); cubicTo(16.8f * s, 8f * s, 16.8f * s, 4.5f * s, 14f * s, 4.5f * s) }, tint, style = st)
                drawPath(Path().apply { moveTo(3f * s, 12f * s); lineTo(19f * s, 12f * s); cubicTo(22.2f * s, 12f * s, 22.2f * s, 8f * s, 19f * s, 8f * s) }, tint, style = st)
                drawPath(Path().apply { moveTo(3f * s, 16f * s); lineTo(14f * s, 16f * s); cubicTo(16.8f * s, 16f * s, 16.8f * s, 19.5f * s, 14f * s, 19.5f * s) }, tint, style = st)
            }
            else -> {   // ắc quy: thân, hai cực trên, dấu cộng trừ
                drawRoundRect(tint, p(3f, 7.5f), Size(18f * s, 11f * s), CornerRadius(1.8f * s), style = st)
                drawLine(tint, p(6.5f, 5f), p(6.5f, 7.5f), sw * 1.5f, StrokeCap.Round)
                drawLine(tint, p(17.5f, 5f), p(17.5f, 7.5f), sw * 1.5f, StrokeCap.Round)
                drawLine(tint, p(6.5f, 13f), p(10.5f, 13f), sw * 0.9f, StrokeCap.Round)
                drawLine(tint, p(8.5f, 11f), p(8.5f, 15f), sw * 0.9f, StrokeCap.Round)
                drawLine(tint, p(14f, 13f), p(18f, 13f), sw * 0.9f, StrokeCap.Round)
            }
        }
    }
}

// ---------- Ảnh xe riêng ----------

fun loadCar(ctx: Context): ImageBitmap? = try {
    val f = File(ctx.filesDir, "car.png")
    if (!f.exists()) null else BitmapFactory.decodeFile(f.path)?.asImageBitmap()
} catch (e: Exception) { null }

// Lưu ảnh người dùng chọn thành car.png (giữ nền trong suốt nếu có, thu nhỏ còn tối đa khoảng 1400 px). Gọi trên luồng nền.
fun saveCarImage(ctx: Context, uri: Uri) {
    try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > 1400) s *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
        } ?: return
        File(ctx.filesDir, "car.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: Exception) { }
}

// Hình xe ở giữa trang 1 theo lựa chọn trong Cài đặt. edge = phần giữa ảnh giữ nguyên, ngoài đó mờ dần hoà vào nền.
@Composable
fun CarImage(modifier: Modifier, edge: Float) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val mode = t.p.carImg
    val prof = Vehicles.byId(t.p.vehicle)   // ảnh và hình vector theo hồ sơ xe đang chọn
    val custom by produceState<ImageBitmap?>(null, t.p.carVer) { value = withContext(Dispatchers.IO) { loadCar(ctx) } }
    when (mode) {
        2 -> Canvas(modifier) { drawPickup(t.blueLight, t.accent, wire = true, sedan = prof.sedan) }
        3 -> Canvas(modifier) { drawPickup(t.blue, t.accent, wire = false, sedan = prof.sedan) }
        else -> {
            val fade = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
                drawContent()
                val corner = sqrt(size.width * size.width + size.height * size.height) / 2f
                drawRect(
                    Brush.radialGradient(0.0f to Color.Black, edge to Color.Black, 1f to Color.Transparent, center = center, radius = corner),
                    blendMode = BlendMode.DstIn
                )
            }
            val c = custom
            if (mode == 1 && c != null) Image(c, null, fade, contentScale = ContentScale.Fit)
            else if (prof.photo != null) Image(painterResource(prof.photo), null, fade, contentScale = ContentScale.Fit)
            else Canvas(modifier) { drawPickup(t.blue, t.accent, wire = false, sedan = prof.sedan) }   // hồ sơ chung chưa có ảnh: hình vector
        }
    }
}

// Xe bán tải nhìn ngang, vẽ trong khung 200 x 80 rồi co giãn vừa ô. wire = true: khung dây có ánh sáng; false: bóng đặc.
private fun DrawScope.drawPickup(body: Color, glow: Color, wire: Boolean, sedan: Boolean = false) {
    val s = min(size.width / 200f, size.height / 80f) * 0.94f
    val ox = (size.width - 200f * s) / 2f
    val oy = (size.height - 80f * s) / 2f
    val shell = Path().apply {
        if (sedan) {   // sedan: nắp cabin liền, đuôi ngắn
            moveTo(6f, 56f); lineTo(10f, 46f); lineTo(44f, 40f); lineTo(70f, 22f); lineTo(130f, 20f)
            lineTo(158f, 38f); lineTo(190f, 44f); lineTo(196f, 56f); close()
        } else {       // bán tải: thùng sau và cabin
            moveTo(8f, 54f); lineTo(8f, 35f); lineTo(88f, 35f); lineTo(97f, 17f); lineTo(132f, 12f)
            lineTo(150f, 32f); lineTo(184f, 36f); lineTo(194f, 43f); lineTo(194f, 54f); close()
        }
    }
    val glass = Path().apply {
        if (sedan) { moveTo(76f, 26f); lineTo(126f, 24f); lineTo(148f, 38f); lineTo(66f, 39f); close() }
        else { moveTo(101f, 20f); lineTo(128f, 16f); lineTo(142f, 31f); lineTo(99f, 31f); close() }
    }
    val wheels = if (sedan) listOf(Offset(46f, 58f), Offset(152f, 58f)) else listOf(Offset(48f, 56f), Offset(154f, 56f))
    withTransform({ translate(ox, oy); scale(s, s, Offset.Zero) }) {
        if (wire) {
            drawPath(shell, glow.copy(alpha = 0.22f), style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(shell, body, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(glass, body.copy(alpha = 0.85f), style = Stroke(1.6f, join = StrokeJoin.Round))
            drawLine(body.copy(alpha = 0.6f), Offset(88f, 35f), Offset(88f, 54f), 1.4f)
            drawLine(body.copy(alpha = 0.6f), Offset(120f, 31f), Offset(120f, 54f), 1.4f)
            for (w in wheels) {
                drawCircle(glow.copy(alpha = 0.22f), 15f, w, style = Stroke(6f))
                drawCircle(body, 13f, w, style = Stroke(2.4f))
                drawCircle(body.copy(alpha = 0.8f), 6f, w, style = Stroke(1.6f))
                for (i in 0 until 5) {
                    val a = Math.toRadians(i * 72.0 - 90.0)
                    drawLine(body.copy(alpha = 0.7f), w, Offset(w.x + (6f * Math.cos(a)).toFloat(), w.y + (6f * Math.sin(a)).toFloat()), 1.2f)
                }
            }
        } else {
            drawPath(shell, Brush.verticalGradient(listOf(body.copy(alpha = 0.95f), body.copy(alpha = 0.45f)), startY = 12f, endY = 54f), style = Fill)
            drawPath(shell, body, style = Stroke(1.6f, join = StrokeJoin.Round))
            drawPath(glass, Color(0xCC05080F), style = Fill)
            drawLine(Color(0x66000000), Offset(88f, 35f), Offset(88f, 54f), 1.4f)
            for (w in wheels) {
                drawCircle(Color(0xFF0A0C10), 14f, w)
                drawCircle(Color(0xFF9AA4B2), 7.5f, w)
                drawCircle(Color(0xFF2A313B), 3f, w)
            }
            // đèn trước
            drawCircle(glow, 2.6f, Offset(188f, 41f))
        }
    }
}

// ---------- Cấp số kiểu thể thao: chỉ một chữ số to, nghiêng ----------
@Composable
fun GearSport(gear: Int?, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            gear?.toString() ?: "-", color = if (gear != null) t.glow else t.muted,
            fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            style = if (gear != null) TextStyle(shadow = Shadow(t.glow, Offset.Zero, 30f)) else TextStyle.Default
        )
    }
}

// ---------- Cấp số kiểu số lớn ----------
@Composable
fun GearBig(gear: Int?, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    Box(modifier.panel(18.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GEAR", color = t.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            Spacer(Modifier.width(14.dp))
            Text(
                gear?.toString() ?: "-", color = if (gear != null) t.glow else t.muted,
                fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
                style = if (gear != null) TextStyle(shadow = Shadow(t.glow, Offset.Zero, 26f)) else TextStyle.Default
            )
        }
    }
}
