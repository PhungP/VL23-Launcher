package vn.navara.launcher

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Số điện tử 7 đoạn. Bit: a=1, b=2, c=4, d=8, e=16, f=32, g=64
private val SEG = mapOf(
    '0' to 0x3F, '1' to 0x06, '2' to 0x5B, '3' to 0x4F, '4' to 0x66,
    '5' to 0x6D, '6' to 0x7D, '7' to 0x07, '8' to 0x7F, '9' to 0x6F,
    '-' to 0x40, ' ' to 0x00
)

// Chuỗi số hiển thị kiểu đồng hồ điện tử; đoạn tắt vẫn mờ mờ như màn hình LED thật
@Composable
fun SevenSeg(text: String, height: Dp, on: Color, off: Color, modifier: Modifier = Modifier) {
    val n = text.length
    val dw = height.value * 0.56f
    val gap = height.value * 0.16f
    val w = n * dw + (if (n > 1) (n - 1) * gap else 0f)
    Canvas(modifier.size(Dp(w), height)) {
        val d = size.height
        val dwPx = d * 0.56f
        val gp = d * 0.16f
        val t = d * 0.13f
        text.forEachIndexed { i, ch ->
            val x0 = i * (dwPx + gp)
            val m = SEG[ch] ?: 0
            fun seg(bit: Int, x: Float, y: Float, sw: Float, sh: Float) {
                drawRoundRect(if ((m and bit) != 0) on else off, Offset(x, y), Size(sw, sh), CornerRadius(t * 0.45f))
            }
            val hl = dwPx - t * 1.6f
            val vh = d / 2f - t * 1.3f
            seg(1, x0 + t * 0.8f, 0f, hl, t)                       // a: ngang trên
            seg(2, x0 + dwPx - t, t * 0.8f, t, vh)                 // b: dọc phải trên
            seg(4, x0 + dwPx - t, d / 2f + t * 0.5f, t, vh)        // c: dọc phải dưới
            seg(8, x0 + t * 0.8f, d - t, hl, t)                    // d: ngang dưới
            seg(16, x0, d / 2f + t * 0.5f, t, vh)                  // e: dọc trái dưới
            seg(32, x0, t * 0.8f, t, vh)                           // f: dọc trái trên
            seg(64, x0 + t * 0.8f, d / 2f - t / 2f, hl, t)         // g: ngang giữa
        }
    }
}

// Giá trị số chạy mượt tới đích (dùng cho vòng tua, boost, tải...) thay vì nhảy cóc theo từng lần đọc OBD
@Composable
fun smoothF(v: Float, ms: Int = 260): Float {
    val s by animateFloatAsState(v, tween(ms, easing = LinearOutSlowInEasing), label = "smooth")
    return s
}

// Đồng hồ giờ kiểu điện tử "HH:MM" bằng số 7 đoạn, hai chấm nháy mỗi giây
@Composable
fun SevenSegClock(text: String, height: Dp, on: Color, off: Color, modifier: Modifier = Modifier) {
    var lit by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(500); lit = !lit } }
    val parts = text.split(":")
    val hh = parts.getOrElse(0) { "--" }
    val mm = parts.getOrElse(1) { "--" }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SevenSeg(hh, height, on, off)
        Canvas(Modifier.width(height * 0.32f).height(height)) {
            val d = size.height * 0.11f
            val c = if (lit) on else off
            val x = (size.width - d) / 2f
            drawRoundRect(c, Offset(x, size.height * 0.30f), Size(d, d), CornerRadius(d * 0.3f))
            drawRoundRect(c, Offset(x, size.height * 0.62f), Size(d, d), CornerRadius(d * 0.3f))
        }
        SevenSeg(mm, height, on, off)
    }
}

// Thanh LED chia ô (kiểu đồng hồ điện tử): f = 0..1, các ô tắt vẫn mờ mờ
@Composable
fun LedBar(f: Float, on: Color, off: Color, modifier: Modifier = Modifier, n: Int = 24) {
    val lit = (smoothF(f.coerceIn(0f, 1f), 220) * n + 0.5f).toInt().coerceIn(0, n)
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) drawRect(if (i < lit) on else off, Offset(i * (w + gap), 0f), Size(w, size.height))
    }
}
