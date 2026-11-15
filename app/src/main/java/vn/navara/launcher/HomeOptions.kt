package vn.navara.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
)

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
    val custom by produceState<ImageBitmap?>(null, t.p.carVer) { value = withContext(Dispatchers.IO) { loadCar(ctx) } }
    when (mode) {
        2 -> Canvas(modifier) { drawPickup(t.blueLight, t.accent, wire = true) }
        3 -> Canvas(modifier) { drawPickup(t.blue, t.accent, wire = false) }
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
            else Image(painterResource(R.drawable.navara_photo), null, fade, contentScale = ContentScale.Fit)
        }
    }
}

// Xe bán tải nhìn ngang, vẽ trong khung 200 x 80 rồi co giãn vừa ô. wire = true: khung dây có ánh sáng; false: bóng đặc.
private fun DrawScope.drawPickup(body: Color, glow: Color, wire: Boolean) {
    val s = min(size.width / 200f, size.height / 80f) * 0.94f
    val ox = (size.width - 200f * s) / 2f
    val oy = (size.height - 80f * s) / 2f
    val shell = Path().apply {
        moveTo(8f, 54f); lineTo(8f, 35f); lineTo(88f, 35f); lineTo(97f, 17f); lineTo(132f, 12f)
        lineTo(150f, 32f); lineTo(184f, 36f); lineTo(194f, 43f); lineTo(194f, 54f); close()
    }
    val glass = Path().apply {
        moveTo(101f, 20f); lineTo(128f, 16f); lineTo(142f, 31f); lineTo(99f, 31f); close()
    }
    val wheels = listOf(Offset(48f, 56f), Offset(154f, 56f))
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
