package vn.navara.launcher

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val RPM_MAX: Int get() = Vehicles.current.rpmMax      // thang đồng hồ vòng tua theo hồ sơ xe
private val RPM_RED: Int get() = Vehicles.current.rpmRed      // vùng đỏ theo hồ sơ xe (Navara đỏ từ khoảng 4500)
private const val ARC_START = 150f    // cung 240 độ, hở phía dưới: bắt đầu ở góc dưới bên trái
private const val ARC_SWEEP = 240f

// Hướng di chuyển từ GPS (chỉ cập nhật khi xe đang chạy trên 1,5 m/s; dừng thì giữ hướng cuối). Giá trị cộng dồn theo đường quay ngắn nhất
// để mũi tên la bàn không quay vòng ngược khi qua mốc 0/360. Chưa có hướng thì trả về null.
@Composable
fun rememberHeading(): Float? {
    val loc by LocationHolder.loc.collectAsState()
    var cum by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(loc) {
        val l = loc
        if (l != null && l.hasBearing() && l.speed > 1.5f) {
            val c = cum
            cum = if (c == null) l.bearing else c + ((l.bearing - c) % 360f + 540f) % 360f - 180f
        }
    }
    return cum
}

fun headingLabel(deg: Float?): String {
    if (deg == null) return "--"
    val d = ((deg % 360f) + 360f) % 360f
    return arrayOf("Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc")[((d + 22.5f) / 45f).toInt() % 8]
}

// Biểu tượng chỉ đường hai tông xanh (nửa trái xanh nhạt, nửa phải xanh đậm), quay theo hướng xe đang đi; chưa có hướng thì chỉ lên trên.
// Hình vẽ theo khung 512 x 512 của ảnh gốc: mũi nhọn trên cùng, hai cánh bo tròn, khuyết hình chữ V phía dưới.
@Composable
fun NavArrow(heading: Float?, size: Dp, light: Color = Color(0xFF80BBFF), dark: Color = Color(0xFF007FFF)) {
    val ang by animateFloatAsState(heading ?: 0f, tween(300), label = "navarrow")
    Canvas(Modifier.size(size)) {
        val k = this.size.minDimension / 512f
        rotate(ang, center) {
            val left = Path().apply { moveTo(254f * k, 4f * k); lineTo(46f * k, 500f * k); lineTo(254f * k, 385f * k); close() }
            val right = Path().apply { moveTo(258f * k, 4f * k); lineTo(466f * k, 500f * k); lineTo(258f * k, 385f * k); close() }
            drawPath(left, light)
            drawPath(right, dark)
        }
    }
}

// Biểu tượng la bàn nhỏ: vòng tròn có chấm Bắc cố định phía trên, mũi tên quay theo hướng xe đang đi
@Composable
fun CompassIcon(heading: Float?, size: Dp, color: Color, north: Color) {
    val ang by animateFloatAsState(heading ?: 0f, tween(300), label = "compass")
    Canvas(Modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.7f), r * 0.92f, c, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(north, r * 0.12f, Offset(c.x, c.y - r * 0.92f))
        if (heading != null) {
            rotate(ang, c) {
                val p = Path().apply {
                    moveTo(c.x, c.y - r * 0.68f); lineTo(c.x + r * 0.30f, c.y + r * 0.42f); lineTo(c.x, c.y + r * 0.22f); lineTo(c.x - r * 0.30f, c.y + r * 0.42f); close()
                }
                drawPath(p, color)
            }
        }
    }
}

// Đồng hồ vòng tua kiểu thể thao: cung vạch chia 50 vòng/phút (vạch dài mỗi 500, rất dài mỗi 1000) sáng dần màu trắng, vùng đỏ,
// kim cam mảnh trỏ đúng giá trị hiện tại; ở giữa là la bàn (hướng xe đi), số cấp rất lớn và vòng tua dạng số.
// Cung hở phía dưới nên chiều cao chỉ cần 1,5 lần bán kính.
@Composable
fun SportGauge(rpm: Float, gear: Int?, ok: Boolean, heading: Float?, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    val red = Color(0xFFE53935)
    BoxWithConstraints(modifier) {
        val hh = 30.dp   // dải phía trên đỉnh đồng hồ, chứa hướng xe (nằm ngay trên số 3)
        val r = minOf(maxWidth / 2f, (maxHeight - hh) / 1.5f)
        val rv = r.value
        Canvas(Modifier.fillMaxSize().padding(top = hh)) {
            val rad = r.toPx()
            val c = Offset(size.width / 2f, rad)
            val step = 50
            val n = RPM_MAX / step
            drawArc(
                t.blue, ARC_START, ARC_SWEEP, false,
                topLeft = Offset(c.x - rad, c.y - rad), size = Size(2f * rad, 2f * rad), style = Stroke(width = 2.dp.toPx())
            )
            for (i in 0..n) {
                val v = i * step
                val a = Math.toRadians((ARC_START + ARC_SWEEP * v / RPM_MAX).toDouble())
                val kind = if (v % 1000 == 0) 2 else if (v % 500 == 0) 1 else 0
                val r1 = rad * 0.96f
                val r0 = rad * (if (kind == 2) 0.80f else if (kind == 1) 0.85f else 0.90f)
                val lit = ok && v <= rpm
                val zone = v >= RPM_RED
                val col = when {
                    lit && zone -> red
                    lit -> t.ink
                    zone -> red.copy(alpha = 0.35f)
                    else -> t.ink.copy(alpha = 0.16f)
                }
                drawLine(
                    col,
                    Offset(c.x + (r0 * cos(a)).toFloat(), c.y + (r0 * sin(a)).toFloat()),
                    Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()),
                    strokeWidth = (if (kind == 2) 3.dp else if (kind == 1) 2.5.dp else 1.5.dp).toPx(), cap = StrokeCap.Butt
                )
            }
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = rad * 0.13f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                color = t.muted.toArgb()
            }
            drawIntoCanvas { cv ->
                for (k in 0..6) {
                    val a = Math.toRadians((ARC_START + ARC_SWEEP * k / 6f).toDouble())
                    val rr = rad * 0.68f
                    cv.nativeCanvas.drawText(k.toString(), c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat() + paint.textSize * 0.35f, paint)
                }
            }
            if (ok) {
                val a = Math.toRadians((ARC_START + ARC_SWEEP * (rpm / RPM_MAX).coerceIn(0f, 1f)).toDouble())
                drawLine(
                    t.accent,
                    Offset(c.x + (rad * 0.78f * cos(a)).toFloat(), c.y + (rad * 0.78f * sin(a)).toFloat()),
                    Offset(c.x + (rad * 0.99f * cos(a)).toFloat(), c.y + (rad * 0.99f * sin(a)).toFloat()),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Row(Modifier.align(Alignment.TopCenter).height(hh), verticalAlignment = Alignment.CenterVertically) {
            NavArrow(heading, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(headingLabel(heading), color = t.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (heading != null) {
                val d = (((heading % 360f) + 360f) % 360f).toInt()
                Text("  " + String.format("%03d°", d), color = t.muted, fontSize = 16.sp, maxLines = 1)
            }
        }
        Column(Modifier.align(Alignment.TopCenter).offset(y = hh + r * 0.44f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (ok) (gear?.toString() ?: "-") else "-", color = t.ink,
                fontSize = (rv * 0.60f).sp, lineHeight = (rv * 0.62f).sp, fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (ok) rpm.roundToInt().coerceIn(0, 9999).toString() else "--", color = t.ink, fontSize = (rv * 0.20f).sp, fontWeight = FontWeight.SemiBold)
                Text(" RPM", color = t.muted, fontSize = (rv * 0.10f).sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// Ô chỉ số nhỏ: nhãn, giá trị trắng (cam khi vượt ngưỡng) và thanh xanh dương mảnh
@Composable
fun StatTile(label: String, value: String, f: Float?, modifier: Modifier = Modifier, warn: Boolean = false) {
    val t = LocalTheme.current
    Column(modifier.card().padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, color = t.muted, fontSize = 12.sp, letterSpacing = 1.sp)
        Text(value, color = if (warn) t.accent else t.ink, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (f != null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.panelHi)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(t.blueDeep, t.blueLight)))
                )
            }
        }
    }
}
