package vn.navara.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ===== THEME 2: ORANGE SPORT =====
// Cam neon #FF8C42 + đen carbon. Khung lục giác kéo dài, góc nhọn. RPM và số gear TO, màu cam gradient, phát sáng.
// Không vòng ngắm, không chữ quân sự: đơn giản, sạch, thể thao.
object HudThemeOrange : HudTheme {
    override val accent = Color(0xFFFF8C42)
    override val ink = Color(0xFFFFFFFF)
    override val label = Color(0xFFF4EFE9)
    override val unit = Color(0xFFFFFFFF)
    override val icon = Color(0xFFFF8C42)
    override val shift = Color(0xFFFF5A3C)
    private val orangeFill = Brush.verticalGradient(listOf(Color(0xFFFFD9BD), Color(0xFFFFA466), Color(0xFFFF7A2F), Color(0xFFE5481A)))
    override val rpmStyle = TextStyle(
        brush = orangeFill, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFFFF7A2F), Offset.Zero, 30f)
    )
    override val gearStyle = TextStyle(
        brush = orangeFill, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFFFF7A2F), Offset.Zero, 34f)
    )
    override val rpmScale = 0.145f
    override val gearScale = 0.22f
    override val gearLabel: String? = null
    override val playButtonFill = true
    override val rpmTop = 0.045f
    override val gearTop = 0.73f

    // Khung lục giác kéo dài: viền cam, nền đen carbon, quầng sáng cam
    override fun drawFrame(d: DrawScope, w: Float, h: Float) {
        val s = w * 0.09f
        val p = Path().apply {
            moveTo(s, 0f); lineTo(w - s, 0f); lineTo(w, h / 2f); lineTo(w - s, h); lineTo(s, h); lineTo(0f, h / 2f); close()
        }
        with(d) {
            drawPath(p, Brush.verticalGradient(listOf(Color(0xE62A1A10), Color(0xE6090A0C))))
            drawPath(p, accent.copy(alpha = 0.28f), style = Stroke(width = 4.dp.toPx()))
            drawPath(p, Brush.horizontalGradient(listOf(Color(0xFFFF8C42), Color(0xFFFFC08F), Color(0xFFFF8C42), Color(0xFFD9632A))), style = Stroke(width = 1.5.dp.toPx()))
        }
    }

    // Nền đen carbon, quầng cam nhẹ, mặt đồng hồ RPM mờ phía sau xe
    @Composable
    override fun Back(W: Dp, H: Dp) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF07080B))
            drawRect(Brush.radialGradient(listOf(Color(0x1CFF7832), Color.Transparent), center = Offset(w * 0.5f, h * 0.6f), radius = w * 0.4f))
            // vân carbon: các đường chéo rất mờ
            val step = 5.dp.toPx()
            var x = -h
            while (x < w) { drawLine(Color(0x08FFFFFF), Offset(x, h), Offset(x + h, 0f), 1f); x += step }
            // mặt đồng hồ mờ
            val dial = Color(0x669FB8C8)
            drawArc(dial, 180f, 180f, false, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, w * 0.6f), style = Stroke(1.dp.toPx()))
            drawArc(Color(0x449FB8C8), 180f, 180f, false, Offset(w * 0.23f, h * 0.2f + w * 0.03f), Size(w * 0.54f, w * 0.54f), style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))))
        }
    }

    @Composable
    override fun Front(d: HudData, W: Dp, H: Dp) { /* Theme cam đơn giản, không có trang trí phía trên xe */ }
}
