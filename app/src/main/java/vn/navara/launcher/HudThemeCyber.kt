package vn.navara.launcher

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== THEME 3: CYBERPUNK MECHANICAL =====
// Xanh cyan #00E5FF + xám kim loại. Khung dày kiểu máy móc: ốc vít, mạch điện, bánh răng, viền kim loại 3D.
// Ô số Gear nằm trong cụm cơ khí, chữ "HỘP SỐ HIỆN HÀNH" ở trên và "MODE: SPORT" ở dưới.
object HudThemeCyber : HudTheme {
    override val accent = Color(0xFF00E5FF)
    override val ink = Color(0xFFF2FDFF)
    override val label = Color(0xFFE6F6FA)
    override val unit = Color(0xFFBFEFFF)
    override val icon = Color(0xFF29E6FF)
    override val shift = Color(0xFF29E6FF)
    override val rpmStyle = TextStyle(
        color = Color(0xFFF2FDFF), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFF00E5FF), Offset.Zero, 26f)
    )
    override val gearStyle = TextStyle(
        color = Color(0xFF48EEFF), fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
        shadow = Shadow(Color(0xFF00E5FF), Offset.Zero, 28f)
    )
    override val rpmScale = 0.125f
    override val gearScale = 0.16f
    override val gearLabel: String? = null
    override val playButtonFill = true
    override val rpmTop = 0.04f
    override val gearTop = 0.775f

    private val metal = listOf(Color(0xFFAAB4C0), Color(0xFF596270), Color(0xFF262C33), Color(0xFF8A95A2), Color(0xFF323941))
    private val dark = Color(0xE6050C12)

    private fun hex(l: Float, t: Float, r: Float, b: Float, depth: Float): Path = Path().apply {
        moveTo(l + depth, t); lineTo(r - depth, t); lineTo(r, (t + b) / 2f); lineTo(r - depth, b); lineTo(l + depth, b); lineTo(l, (t + b) / 2f); close()
    }

    // Khung ô: viền kim loại dày kiểu 3D, đường cyan bên trong, nền tối, hai ốc vít
    override fun drawFrame(d: DrawScope, w: Float, h: Float) {
        val depth = w * 0.075f
        val bez = minOf(h * 0.09f, 4.dp.toPx2(d))
        val outer = hex(0f, 0f, w, h, depth)
        val inner = hex(bez, bez, w - bez, h - bez, depth)
        with(d) {
            drawPath(outer, Color(0x99000000), style = Stroke(width = 3.dp.toPx()))   // bóng đổ
            drawPath(outer, Brush.linearGradient(metal, start = Offset(0f, 0f), end = Offset(w, h)))
            drawPath(inner, dark)
            drawPath(inner, Color(0x4400E5FF), style = Stroke(width = 3.dp.toPx()))
            drawPath(inner, accent, style = Stroke(width = 1.3.dp.toPx()))
            val sr = 1.7.dp.toPx()
            drawCircle(Color(0xFFC5CED8), sr, Offset(depth + 4.dp.toPx(), bez * 0.5f + 1f))
            drawCircle(Color(0xFF1B2026), sr * 0.45f, Offset(depth + 4.dp.toPx(), bez * 0.5f + 1f))
            drawCircle(Color(0xFFC5CED8), sr, Offset(w - depth - 4.dp.toPx(), h - bez * 0.5f - 1f))
            drawCircle(Color(0xFF1B2026), sr * 0.45f, Offset(w - depth - 4.dp.toPx(), h - bez * 0.5f - 1f))
        }
    }

    private fun Dp.toPx2(d: DrawScope): Float = with(d) { this@toPx2.toPx() }

    // Nền kim loại tối, mạch điện cyan, khung RPM cơ khí phía trên
    @Composable
    override fun Back(W: Dp, H: Dp) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1016), Color(0xFF05080C), Color(0xFF080C12))))
            drawRect(Brush.radialGradient(listOf(Color(0x1A00E5FF), Color.Transparent), center = Offset(w * 0.5f, h * 0.62f), radius = w * 0.4f))
            // mạch điện
            val cl = Color(0x8800E5FF)
            val sw = 1.2.dp.toPx()
            val path = Path().apply {
                moveTo(w * 0.206f, h * 0.33f); lineTo(w * 0.23f, h * 0.33f); lineTo(w * 0.245f, h * 0.365f); lineTo(w * 0.245f, h * 0.49f)
                moveTo(w * 0.794f, h * 0.33f); lineTo(w * 0.77f, h * 0.33f); lineTo(w * 0.755f, h * 0.365f); lineTo(w * 0.755f, h * 0.49f)
                moveTo(w * 0.206f, h * 0.575f); lineTo(w * 0.225f, h * 0.575f); lineTo(w * 0.238f, h * 0.61f); lineTo(w * 0.238f, h * 0.7f)
                moveTo(w * 0.794f, h * 0.575f); lineTo(w * 0.775f, h * 0.575f); lineTo(w * 0.762f, h * 0.61f); lineTo(w * 0.762f, h * 0.7f)
            }
            drawPath(path, cl, style = Stroke(width = sw))
            for (pt in listOf(Offset(w * 0.206f, h * 0.33f), Offset(w * 0.794f, h * 0.33f), Offset(w * 0.206f, h * 0.575f), Offset(w * 0.794f, h * 0.575f))) {
                drawCircle(Color(0xCC00E5FF), 2.dp.toPx(), pt)
            }
            // viền kim loại ngoài cùng của màn hình
            val b = 3.dp.toPx()
            drawRect(Brush.linearGradient(listOf(Color(0xFF7D8793), Color(0xFF2A3037), Color(0xFF131820), Color(0xFF6E7885))), Offset.Zero, Size(w, b))
            drawRect(Brush.linearGradient(listOf(Color(0xFF7D8793), Color(0xFF2A3037), Color(0xFF131820), Color(0xFF6E7885))), Offset(0f, h - b), Size(w, b))
            // khung RPM: kim loại, đường cyan, nền tối, có khuyết hai bên dưới
            val fx = w * 0.31f
            val fw = w * 0.38f
            val fh = h * 0.235f
            val rp = Path().apply { moveTo(fx, 0f); lineTo(fx + fw, 0f); lineTo(fx + fw, fh * 0.52f); lineTo(fx + fw * 0.92f, fh); lineTo(fx + fw * 0.08f, fh); lineTo(fx, fh * 0.52f); close() }
            drawPath(rp, Brush.linearGradient(metal, start = Offset(fx, 0f), end = Offset(fx + fw, fh)))
            val ins = 3.dp.toPx()
            val rp2 = Path().apply { moveTo(fx + ins, 0f); lineTo(fx + fw - ins, 0f); lineTo(fx + fw - ins, fh * 0.52f); lineTo(fx + fw * 0.92f - ins * 0.4f, fh - ins); lineTo(fx + fw * 0.08f + ins * 0.4f, fh - ins); lineTo(fx + ins, fh * 0.52f); close() }
            drawPath(rp2, Color(0xFF060D13))
            drawPath(rp2, accent, style = Stroke(width = 1.3.dp.toPx()))
            // hai bánh răng nhỏ hai bên số RPM
            for (gx in listOf(fx + fw * 0.07f, fx + fw * 0.93f)) {
                val gr = h * 0.045f
                drawPath(gearPath(gx, fh * 0.5f, gr, gr * 0.78f), Color(0xFF9AA6B4))
                drawCircle(Color(0xFF060D13), gr * 0.4f, Offset(gx, fh * 0.5f))
            }
        }
    }

    // Cụm cơ khí chứa số gear: hai piston, lõi lục giác, bánh răng; chữ ở trên và ở dưới
    @Composable
    override fun Front(d: HudData, W: Dp, H: Dp) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx0 = w * 0.235f
            val cw = w * 0.53f
            val cy0 = h * 0.72f
            val ch = h * 0.275f
            // piston trái và phải
            val pistonBrush = Brush.verticalGradient(listOf(Color(0xFF8A95A2), Color(0xFF3B434C), Color(0xFF1C2127), Color(0xFF6C7783), Color(0xFF2B323A)), startY = cy0 + ch * 0.26f, endY = cy0 + ch * 0.74f)
            val pl = Path().apply {
                moveTo(cx0, cy0 + ch * 0.30f); lineTo(cx0 + cw * 0.24f, cy0 + ch * 0.26f); lineTo(cx0 + cw * 0.34f, cy0 + ch * 0.32f)
                lineTo(cx0 + cw * 0.34f, cy0 + ch * 0.68f); lineTo(cx0 + cw * 0.24f, cy0 + ch * 0.74f); lineTo(cx0, cy0 + ch * 0.70f); close()
            }
            val pr = Path().apply {
                moveTo(cx0 + cw, cy0 + ch * 0.30f); lineTo(cx0 + cw * 0.76f, cy0 + ch * 0.26f); lineTo(cx0 + cw * 0.66f, cy0 + ch * 0.32f)
                lineTo(cx0 + cw * 0.66f, cy0 + ch * 0.68f); lineTo(cx0 + cw * 0.76f, cy0 + ch * 0.74f); lineTo(cx0 + cw, cy0 + ch * 0.70f); close()
            }
            drawPath(pl, pistonBrush)
            drawPath(pr, pistonBrush)
            for (i in 1..4) {
                val x = cx0 + cw * (0.04f + i * 0.055f)
                drawLine(Color(0xCC00E5FF), Offset(x, cy0 + ch * 0.46f), Offset(x, cy0 + ch * 0.54f), 1.5.dp.toPx())
                val x2 = cx0 + cw * (0.96f - i * 0.055f)
                drawLine(Color(0xCC00E5FF), Offset(x2, cy0 + ch * 0.46f), Offset(x2, cy0 + ch * 0.54f), 1.5.dp.toPx())
            }
            // bánh răng
            val gcol = Color(0xFF7D8793)
            val g1 = Offset(cx0 + cw * 0.70f + ch * 0.12f, cy0 + ch * 0.16f)
            drawPath(gearPath(g1.x, g1.y, ch * 0.2f, ch * 0.155f), gcol)
            drawCircle(Color(0xFF050C12), ch * 0.075f, g1)
            val g2 = Offset(cx0 + cw * 0.285f, cy0 + ch * 0.82f)
            drawPath(gearPath(g2.x, g2.y, ch * 0.11f, ch * 0.085f), gcol)
            drawCircle(Color(0xFF050C12), ch * 0.04f, g2)
            val g3 = Offset(cx0 + cw * 0.30f, cy0 + ch * 0.1f)
            drawPath(gearPath(g3.x, g3.y, ch * 0.075f, ch * 0.058f), gcol)
            // lõi lục giác chứa số gear
            val lx = cx0 + cw * 0.30f
            val lw = cw * 0.40f
            val core = Path().apply {
                moveTo(lx + lw * 0.09f, cy0 + ch * 0.03f); lineTo(lx + lw * 0.91f, cy0 + ch * 0.03f); lineTo(lx + lw, cy0 + ch * 0.5f)
                lineTo(lx + lw * 0.91f, cy0 + ch * 0.97f); lineTo(lx + lw * 0.09f, cy0 + ch * 0.97f); lineTo(lx, cy0 + ch * 0.5f); close()
            }
            drawPath(core, Brush.linearGradient(metal, start = Offset(lx, cy0), end = Offset(lx + lw, cy0 + ch)))
            val k = 3.dp.toPx()
            val core2 = Path().apply {
                moveTo(lx + lw * 0.09f + k, cy0 + ch * 0.03f + k); lineTo(lx + lw * 0.91f - k, cy0 + ch * 0.03f + k); lineTo(lx + lw - k * 1.4f, cy0 + ch * 0.5f)
                lineTo(lx + lw * 0.91f - k, cy0 + ch * 0.97f - k); lineTo(lx + lw * 0.09f + k, cy0 + ch * 0.97f - k); lineTo(lx + k * 1.4f, cy0 + ch * 0.5f); close()
            }
            drawPath(core2, Brush.radialGradient(listOf(Color(0xFF0A2A33), Color(0xFF050C12)), center = Offset(lx + lw / 2f, cy0 + ch * 0.55f), radius = lw * 0.6f))
            drawPath(core2, accent, style = Stroke(width = 1.4.dp.toPx()))
        }
        val fs = H.value
        Text(
            "HỘP SỐ HIỆN HÀNH", color = Color(0xFF4AF0FF), fontSize = (fs * 0.032f).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.offset(W * 0.40f, H * 0.742f).width(W * 0.2f)
        )
        Text(
            "MODE: SPORT", color = Color(0xFFFF9D3C), fontSize = (fs * 0.03f).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.offset(W * 0.40f, H * 0.945f).width(W * 0.2f)
        )
    }
}
