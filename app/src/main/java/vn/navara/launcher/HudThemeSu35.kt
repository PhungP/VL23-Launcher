package vn.navara.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== THEME 1: SU-35 COMBAT =====
// Xanh cyan neon #00F0FF trên nền đen. Khung viền mỏng, góc cắt chéo kiểu quân sự.
// Thêm: "SU-35 // HND-ACCORD-2015 HUD SYSTEM", "COMBAT MODE - ACTIVE", vòng ngắm giữa xe "LOCK • TGT LOCKED", dưới cùng "GEAR ▸ 3".
// Chỉ khác theme khác ở MÀU và KHUNG: sửa màu ở đầu file, sửa khung ở drawFrame / Back / Front.
object HudThemeSu35 : HudTheme {
    override val accent = Color(0xFF00F0FF)
    override val ink = Color(0xFF7DFFF0)
    override val label = Color(0xFFD3FEFF)
    override val unit = Color(0xFF7DFFF0)
    override val icon = Color(0xFF00F0FF)
    override val shift = Color(0xFF00F0FF)
    override val rpmStyle = TextStyle(
        color = Color(0xFF7DFFE9), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFF00F0FF), Offset.Zero, 22f)
    )
    override val gearStyle = TextStyle(
        color = Color(0xFF36ECFF), fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFF00F0FF), Offset.Zero, 26f)
    )
    override val rpmScale = 0.135f
    override val gearScale = 0.19f
    override val gearLabel: String? = "GEAR ▸ "
    override val playButtonFill = false
    override val rpmTop = 0.045f
    override val gearTop = 0.735f

    private val panel = Color(0xCC000C12)

    // Khung ô: viền mỏng, hai góc cắt chéo (trên trái, dưới phải), quầng sáng cyan
    override fun drawFrame(d: DrawScope, w: Float, h: Float) {
        val c = minOf(h * 0.24f, w * 0.09f)
        val p = Path().apply {
            moveTo(c, 0f); lineTo(w, 0f); lineTo(w, h - c); lineTo(w - c, h); lineTo(0f, h); lineTo(0f, c); close()
        }
        with(d) {
            drawPath(p, panel)
            drawPath(p, accent.copy(alpha = 0.28f), style = Stroke(width = 4.dp.toPx()))
            drawPath(p, accent, style = Stroke(width = 1.4.dp.toPx()))
        }
    }

    // Nền đen, lưới mờ, bốn góc ngoặc vuông
    @Composable
    override fun Back(W: Dp, H: Dp) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF03070B))
            drawRect(Brush.radialGradient(listOf(Color(0x2400F0FF), Color.Transparent), center = Offset(w * 0.5f, h * 0.58f), radius = w * 0.38f))
            val grid = Color(0x0A00F0FF)
            val step = w * 0.045f
            var x = 0f
            while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), 1f); x += step }
            var y = 0f
            while (y < h) { drawLine(grid, Offset(0f, y), Offset(w, y), 1f); y += step }
            val m = 4.dp.toPx()
            val k = w * 0.03f
            val sw = 1.5.dp.toPx()
            val col = accent
            // trên trái, trên phải, dưới trái, dưới phải
            drawLine(col, Offset(m, m), Offset(m + k, m), sw); drawLine(col, Offset(m, m), Offset(m, m + k), sw)
            drawLine(col, Offset(w - m, m), Offset(w - m - k, m), sw); drawLine(col, Offset(w - m, m), Offset(w - m, m + k), sw)
            drawLine(col, Offset(m, h - m), Offset(m + k, h - m), sw); drawLine(col, Offset(m, h - m), Offset(m, h - m - k), sw)
            drawLine(col, Offset(w - m, h - m), Offset(w - m - k, h - m), sw); drawLine(col, Offset(w - m, h - m), Offset(w - m, h - m - k), sw)
        }
    }

    // Vòng ngắm giữa xe, vòng cung và thang màu RPM, chữ quân sự
    @Composable
    override fun Front(d: HudData, W: Dp, H: Dp) {
        val rpm = d.rpm.toIntOrNull() ?: 0
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // vòng cung quanh số RPM
            val arc = Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFF4DFFB0), Color(0xFF7DFFD0)), startX = w * 0.37f, endX = w * 0.63f)
            drawArc(arc, 180f, 180f, false, Offset(w * 0.37f, h * 0.09f), Size(w * 0.26f, h * 0.30f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            // thang màu RPM 2000..6000 và vạch chỉ giá trị hiện tại
            val bx = w * 0.355f
            val bw = w * 0.29f
            val by = h * 0.272f
            val bh = h * 0.022f
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF00D9FF), Color(0xFF38F0B0), Color(0xFFE8D34A), Color(0xFFFF7A2A)), startX = bx, endX = bx + bw), Offset(bx, by), Size(bw, bh))
            val mx = bx + bw * ((rpm - 2000) / 4000f).coerceIn(0f, 1f)
            drawLine(Color.White, Offset(mx, by - bh * 0.7f), Offset(mx, by + bh * 1.7f), 2.dp.toPx())
            // vòng ngắm
            val cx = w * 0.5f
            val cy = h * 0.555f
            val r = minOf(w * 0.135f, h * 0.30f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.55f), r * 0.92f, Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = dash))
            drawCircle(accent.copy(alpha = 0.6f), r * 0.62f, Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.5f), r * 0.36f, Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = dash))
            val sw = 1.5.dp.toPx()
            drawLine(accent, Offset(cx, cy - r), Offset(cx, cy - r * 0.66f), sw)
            drawLine(accent, Offset(cx, cy + r * 0.66f), Offset(cx, cy + r), sw)
            drawLine(accent, Offset(cx - r, cy), Offset(cx - r * 0.66f, cy), sw)
            drawLine(accent, Offset(cx + r * 0.66f, cy), Offset(cx + r, cy), sw)
            val bx2 = r * 0.78f
            val by2 = r * 0.6f
            val k = r * 0.16f
            for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
                val px = cx + sx * bx2
                val py = cy + sy * by2
                drawLine(accent, Offset(px, py), Offset(px - sx * k, py), 2.dp.toPx())
                drawLine(accent, Offset(px, py), Offset(px, py - sy * k), 2.dp.toPx())
            }
        }
        val fs = H.value
        Text(
            "SU-35 // HND-ACCORD-2015 HUD SYSTEM", color = Color(0xFF6FFCFF), fontSize = (fs * 0.034f).sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.offset(W * 0.22f, 2.dp).width(W * 0.56f)
        )
        Text(
            "⚠ COMBAT MODE - ACTIVE ⚠", color = Color(0xFFFF9D3C), fontSize = (fs * 0.028f).sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(W * 0.715f, 3.dp)
        )
        Text(
            "LOCK • TGT LOCKED", color = Color(0xFFFFB347), fontSize = (fs * 0.032f).sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(W * 0.61f, H * 0.34f)
        )
        Row(Modifier.offset(W * 0.355f, H * 0.30f).width(W * 0.29f), horizontalArrangement = Arrangement.SpaceBetween) {
            for (n in listOf("2000", "3000", "4000", "5000", "6000")) {
                Text(n, color = Color(0xFFBFF9FF), fontSize = (fs * 0.03f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
