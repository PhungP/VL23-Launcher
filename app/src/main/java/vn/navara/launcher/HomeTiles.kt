package vn.navara.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ===== Ô thông số ở trang 1 kiểu Torque Pro: 6 kiểu hiển thị, đổi cỡ từng ô, chọn thông số và kiểu cho từng ô =====

val TILE_STYLES = listOf("Số", "Kim", "Cung", "Thanh", "Số điện tử", "Đồ thị")

private fun fmtVal(v: Float, dec: Int): String = String.format(Locale.US, "%." + dec + "f", v)

// Cảnh báo: áp suất lốp ngoài ngưỡng thấp / cao do người dùng đặt; thông số khác vượt ngưỡng của chính nó
private fun tileWarn(m: HomeMetric, raw: Float, p: UiPrefs): Boolean =
    if (isTpmsPressure(m.key)) raw * 10f < p.tpmsLow || raw * 10f > p.tpmsHigh
    else m.warn != null && raw >= m.warn

// Màu dùng để vẽ ô: trang 1 theo màu chữ / viền người dùng chọn cho trang 1, trang OBD theo giao diện chung
class TilePal(val ink: Color, val label: Color, val icon: Color)

@Composable
fun HomeTile(
    id: String, key: String, style: Int, obd: ObdData, tp: Map<String, TpmsWheel>, gps: GpsState,
    modifier: Modifier, cutRight: Boolean
) {
    val t = LocalTheme.current
    val m = homeMetric(key)
    val ok = obd.status == ObdManager.OK || isObdFree(key)
    val raw = if (ok) homeMetricValue(obd, key, tp, gps) else null
    val has = raw != null
    val tpmsP = isTpmsPressure(key)
    val shown = if (raw != null && tpmsP) tpmsConv(raw, t.p.tpmsUnit) else raw
    val dec = if (tpmsP) tpmsDec(t.p.tpmsUnit) else m.dec
    val unitText = if (!has) "" else if (tpmsP) tpmsUnitLabel(t.p.tpmsUnit) else m.unit
    val v = smoothF(shown ?: 0f, 220)
    val warn = raw != null && tileWarn(m, raw, t.p)
    val range = metricRange(m, t.p.tpmsUnit)
    val lo = range.first
    val hi = range.second
    val valueText = if (has) fmtVal(v, dec) else "--"
    val f = if (has && hi > lo) ((v - lo) / (hi - lo)).coerceIn(0f, 1f) else 0f
    val editing by HomeEdit.on.collectAsState()
    var sz by remember { mutableStateOf(IntSize.Zero) }
    val pal = TilePal(t.hInk, if (t.mono == 0) t.hLabel else t.muted, t.hIcon)

    Box(modifier.panel(10.dp, left = !cutRight, right = cutRight, line = t.homeLineC, border = tileBorderDp(t.p.tileBorder))) {
        BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { sz = it }) {
            TileBody(style, key, m, valueText, unitText, has, warn, shown, f, lo, hi, pal, true, maxWidth.value, maxHeight.value)
        }
        if (editing) {
            ResizeHandle(id, sz.width.toFloat(), sz.height.toFloat(), Modifier.align(Alignment.BottomEnd))
        }
    }
}

// Độ dày viền ô thông số (dp): không viền, mảnh, vừa (như các bản trước), dày, rất dày
fun tileBorderDp(i: Int): Float = floatArrayOf(0f, 1f, 2f, 3.5f, 5f)[i.coerceIn(0, 4)]

// Nội dung một ô theo kiểu hiển thị (0 số, 1 kim, 2 cung, 3 thanh, 4 số điện tử, 5 đồ thị); dùng cho trang 1 và trang OBD.
// shown: giá trị đã đổi đơn vị (null = chưa có), f: vị trí trên thang 0..1, w và h: kích thước ô tính bằng dp.
@Composable
fun TileBody(
    style: Int, key: String, m: HomeMetric, valueText: String, unitText: String, has: Boolean, warn: Boolean,
    shown: Float?, f: Float, lo: Float, hi: Float, pal: TilePal, showIcon: Boolean, w: Float, h: Float
) {
    val t = LocalTheme.current
    val hasScale = hi > lo
    // Kiểu cần thang đo mà thông số không có (ODO, thời gian...) thì hiện dạng số
    val st = if (!hasScale && (style == 1 || style == 2 || style == 3)) 0 else style
    val ink = if (warn) t.accent else pal.ink
    val labelCol = pal.label
    when (st) {
        1 -> DialTile(m, valueText, unitText, f, hasScale, lo, hi, warn, ink, labelCol, pal, w, h, needle = true)
        2 -> DialTile(m, valueText, unitText, f, hasScale, lo, hi, warn, ink, labelCol, pal, w, h, needle = false)
        3 -> BarTile(m, valueText, unitText, f, lo, hi, warn, ink, labelCol, pal, w, h)
        4 -> DigitalTile(m, valueText, unitText, has, warn, ink, labelCol, pal, w, h)
        5 -> GraphTile(key + m.label, m, valueText, unitText, shown, lo, hi, hasScale, warn, ink, labelCol, pal, w, h)
        else -> NumberTile(key, m, valueText, unitText, warn, ink, labelCol, pal, showIcon, w, h)
    }
}

// ---------- Kiểu 0: số (như các bản trước, co giãn theo cỡ ô) ----------
@Composable
private fun NumberTile(key: String, m: HomeMetric, value: String, unit: String, warn: Boolean, ink: Color, labelCol: Color, pal: TilePal, showIcon: Boolean, w: Float, h: Float) {
    val t = LocalTheme.current
    val u = min(w / 168f, h / 92f).coerceIn(0.55f, 2.2f)
    if (h < 56f) {   // ô mỏng: một hàng, nhãn bên trái, số bên phải
        Row(Modifier.fillMaxSize().padding(horizontal = (14f * min(u, 1f)).dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, color = labelCol, fontSize = 10.5f.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, color = ink, fontSize = (h * 0.46f).coerceIn(15f, 30f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = pal.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp))
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        if (showIcon && u >= 0.75f) HomeGlyph(key, if (warn) t.accent else pal.icon, Modifier.align(Alignment.TopStart).padding(start = (16f * u).dp, top = (10f * u).dp).size((24f * u).dp))
        Text(
            m.label, color = labelCol, fontSize = (11f * u).coerceAtLeast(9f).sp, lineHeight = (12f * u).coerceAtLeast(10f).sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = (8f * u).dp, start = if (showIcon && u >= 0.75f) (44f * u).dp else 8.dp, end = (12f * u).dp)
        )
        Row(Modifier.align(Alignment.Center).padding(top = (10f * u).dp), verticalAlignment = Alignment.Bottom) {
            Text(value, color = ink, fontSize = (32f * u).coerceAtMost(72f).sp, lineHeight = (34f * u).coerceAtMost(76f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = pal.ink, fontSize = (13f * u).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp, bottom = (4f * u).dp))
        }
    }
}

// ---------- Kiểu 1 (kim) và 2 (cung): đồng hồ 240 độ ----------
// Hình học theo dp: cung rộng 2r, cao 1,5r, phía dưới chừa một dòng nhãn
private fun dialLayout(w: Float, h: Float): FloatArray {
    val cap = (h * 0.16f).coerceIn(10f, 18f)
    val r = min(w * 0.46f, (h - cap - 2f) / 1.5f).coerceAtLeast(8f)
    return floatArrayOf(w / 2f, 1f + r, r, cap)   // cx, cy, r, chiều cao dòng nhãn
}

@Composable
private fun DialTile(
    m: HomeMetric, value: String, unit: String, f: Float, hasScale: Boolean, lo: Float, hi: Float,
    warn: Boolean, ink: Color, labelCol: Color, pal: TilePal, w: Float, h: Float, needle: Boolean
) {
    val t = LocalTheme.current
    val g = dialLayout(w, h)
    val r = g[2]
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / w   // dp -> px
            val sw = r * (if (needle) 0.09f else 0.17f) * k
            val rr = r * k - sw / 2f
            val tl = Offset(g[0] * k - rr, g[1] * k - rr)
            val sq = Size(rr * 2f, rr * 2f)
            val track = pal.ink.copy(alpha = 0.16f)
            drawArc(track, 150f, 240f, false, tl, sq, style = Stroke(sw, cap = StrokeCap.Round))
            if (needle) {
                val wl = m.warn
                if (wl != null && hasScale && wl > lo && wl < hi) {   // vùng đỏ từ ngưỡng cảnh báo tới cuối thang
                    val fw = ((wl - lo) / (hi - lo)).coerceIn(0f, 1f)
                    drawArc(t.accent.copy(alpha = 0.85f), 150f + 240f * fw, 240f * (1f - fw), false, tl, sq, style = Stroke(sw, cap = StrokeCap.Butt))
                }
                for (i in 0..10) {
                    val a = Math.toRadians(150.0 + 24.0 * i)
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    val r1 = r * (if (i % 5 == 0) 0.66f else 0.72f) * k
                    val r2 = r * 0.80f * k
                    drawLine(pal.ink.copy(alpha = 0.55f), Offset(g[0] * k + ca * r1, g[1] * k + sa * r1), Offset(g[0] * k + ca * r2, g[1] * k + sa * r2), (if (i % 5 == 0) 2f else 1f) * k, StrokeCap.Round)
                }
                val a = Math.toRadians(150.0 + 240.0 * f)
                val nl = r * 0.62f * k
                drawLine(t.accent, Offset(g[0] * k, g[1] * k), Offset(g[0] * k + cos(a).toFloat() * nl, g[1] * k + sin(a).toFloat() * nl), max(2.2f * k, r * 0.05f * k), StrokeCap.Round)
                drawCircle(t.accent, r * 0.07f * k, Offset(g[0] * k, g[1] * k))
            } else if (f > 0.004f) {
                drawArc(if (warn) t.accent else pal.icon, 150f, 240f * f, false, tl, sq, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        // số: kiểu kim nằm dưới tâm, kiểu cung nằm giữa vòng
        val vy = if (needle) g[1] + 0.34f * r else g[1] + 0.02f * r
        val vf = if (needle) r * 0.40f else r * 0.56f
        Row(
            Modifier.offset(y = (vy - vf * 0.65f).dp).width(w.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom
        ) {
            Text(value, color = ink, fontSize = vf.sp, lineHeight = (vf * 1.15f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = pal.ink, fontSize = (vf * 0.38f).coerceAtLeast(9f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        Text(
            m.label, color = labelCol, fontSize = (g[3] * 0.62f).coerceAtLeast(8f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp, start = 6.dp, end = 6.dp)
        )
    }
}

// ---------- Kiểu 3: thanh ngang ----------
@Composable
private fun BarTile(
    m: HomeMetric, value: String, unit: String, f: Float, lo: Float, hi: Float,
    warn: Boolean, ink: Color, labelCol: Color, pal: TilePal, w: Float, h: Float
) {
    val t = LocalTheme.current
    val u = min(w / 168f, h / 92f).coerceIn(0.55f, 2f)
    val barH = (h * 0.22f).coerceIn(6f, 22f)
    Column(
        Modifier.fillMaxSize().padding(horizontal = (12f * min(u, 1.3f)).dp, vertical = (7f * min(u, 1.3f)).dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(m.label, color = labelCol, fontSize = (11f * u).coerceAtLeast(9f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(bottom = 2.dp))
            Text(value, color = ink, fontSize = (h * 0.34f).coerceIn(15f, 34f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = pal.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(barH.dp)) {
            val rad = size.height / 2f
            drawRoundRect(pal.ink.copy(alpha = 0.16f), Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(rad, rad))
            val fw = size.width * f
            if (fw > 1f) drawRoundRect(if (warn) t.accent else pal.icon, Offset.Zero, Size(max(fw, size.height), size.height), androidx.compose.ui.geometry.CornerRadius(rad, rad))
            val wl = m.warn
            if (wl != null && hi > lo && wl > lo && wl < hi) {   // vạch ngưỡng cảnh báo
                val x = size.width * ((wl - lo) / (hi - lo))
                drawLine(t.accent, Offset(x, 0f), Offset(x, size.height), 2f)
            }
        }
    }
}

// ---------- Kiểu 4: số điện tử 7 đoạn ----------
@Composable
private fun DigitalTile(m: HomeMetric, value: String, unit: String, has: Boolean, warn: Boolean, ink: Color, labelCol: Color, pal: TilePal, w: Float, h: Float) {
    val t = LocalTheme.current
    val on = if (warn) t.accent else pal.ink
    val off = on.copy(alpha = 0.10f)
    val intPart = if (has) value.substringBefore('.') else "--"
    val frac = if (has && value.contains('.')) value.substringAfter('.') else null
    val digits = intPart.length + (frac?.length ?: 0)
    val unitW = unit.length * 7f
    val hh = min(h * 0.46f, (w - 22f - unitW) / (digits * 0.72f + (if (frac != null) 0.3f else 0f))).coerceAtLeast(10f)
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(m.label, color = labelCol, fontSize = (h * 0.12f).coerceIn(8f, 12f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height((h * 0.05f).dp))
        Row(verticalAlignment = Alignment.Bottom) {
            SevenSeg(intPart, hh.dp, on, off)
            if (frac != null) {
                Canvas(Modifier.width((hh * 0.30f).dp).height(hh.dp)) {
                    val d = size.height * 0.12f
                    drawCircle(on, d / 2f, Offset(size.width / 2f, size.height - d / 2f))
                }
                SevenSeg(frac, hh.dp, on, off)
            }
            if (unit.isNotEmpty()) Text(unit, color = pal.ink, fontSize = (hh * 0.30f).coerceIn(9f, 14f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

// ---------- Kiểu 5: đồ thị theo thời gian ----------
@Composable
private fun GraphTile(
    hkey: String, m: HomeMetric, value: String, unit: String, cur: Float?, lo: Float, hi: Float, hasScale: Boolean,
    warn: Boolean, ink: Color, labelCol: Color, pal: TilePal, w: Float, h: Float
) {
    val t = LocalTheme.current
    val latest by rememberUpdatedState(cur)
    val hist = remember(hkey) { mutableStateListOf<Float>() }
    // Lấy mẫu mỗi 0,5 giây, giữ 90 mẫu (khoảng 45 giây)
    LaunchedEffect(hkey) {
        while (true) {
            val x = latest
            if (x != null) { hist.add(x); while (hist.size > 90) hist.removeAt(0) }
            delay(500)
        }
    }
    val line = if (warn) t.accent else pal.icon
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().padding(top = (h * 0.30f).dp)) {
            val n = hist.size
            if (n < 2) return@Canvas
            var a = if (hasScale) lo else hist.minOrNull() ?: 0f
            var b = if (hasScale) hi else hist.maxOrNull() ?: 1f
            if (!hasScale && b - a < 1e-3f) { a -= 1f; b += 1f }
            if (!hasScale) { val pad = (b - a) * 0.1f; a -= pad; b += pad }
            val path = Path()
            val area = Path()
            for (i in 0 until n) {
                val x = size.width * i / (90f - 1f)
                val y = size.height * (1f - ((hist[i] - a) / (b - a)).coerceIn(0f, 1f))
                if (i == 0) { path.moveTo(x, y); area.moveTo(x, size.height); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
            }
            area.lineTo(size.width * (n - 1) / (90f - 1f), size.height)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0f))), style = Fill)
            drawPath(path, line, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 5.dp), verticalAlignment = Alignment.Top) {
            Text(m.label, color = labelCol, fontSize = (h * 0.12f).coerceIn(8.5f, 12f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(top = 3.dp))
            Text(value, color = ink, fontSize = (h * 0.28f).coerceIn(14f, 30f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = pal.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp, top = 8.dp))
        }
    }
}

// ---------- Đổi cỡ ô bằng cách kéo ô vuông ở góc (chế độ sắp xếp) ----------
@Composable
private fun ResizeHandle(id: String, tileW: Float, tileH: Float, modifier: Modifier) {
    val t = LocalTheme.current
    val cur by rememberUpdatedState(t.p)
    val upd by rememberUpdatedState(LocalPrefUpdate.current)
    val tileSz by rememberUpdatedState(Offset(tileW, tileH))
    Box(
        modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(t.accent).pointerInput(id) {
            detectDragGestures(
                onDragEnd = {
                    val s = HomeEdit.live[id]
                    if (s != null) {
                        val all = parseOffsets(cur.homeSizes).toMutableMap()
                        all[id] = s
                        upd(cur.copy(homeSizes = sizesToString(all)))
                    }
                    HomeEdit.live.remove(id)
                },
                onDragCancel = { HomeEdit.live.remove(id) }
            ) { change, amount ->
                change.consume()
                val base = HomeEdit.live[id] ?: (parseOffsets(cur.homeSizes)[id] ?: Offset(1f, 1f))
                // ô co giãn quanh tâm nên góc chỉ dịch nửa mức đổi cỡ: nhân đôi để góc bám theo ngón tay
                val w0 = tileSz.x / base.x
                val h0 = tileSz.y / base.y
                if (w0 > 1f && h0 > 1f) {
                    HomeEdit.live[id] = Offset((base.x + 2f * amount.x / w0).coerceIn(0.4f, 2.5f), (base.y + 2f * amount.y / h0).coerceIn(0.4f, 2.5f))
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(14.dp)) {
            val s = size.minDimension
            val c = t.onAccent
            drawLine(c, Offset(s, s * 0.25f), Offset(s * 0.25f, s), 2f, StrokeCap.Round)
            drawLine(c, Offset(s, s * 0.62f), Offset(s * 0.62f, s), 2f, StrokeCap.Round)
        }
    }
}

// ---------- Bảng cấu hình ô (chạm một ô khi đang sắp xếp) ----------
@Composable
private fun CfgChip(text: String, sel: Boolean, onClick: () -> Unit) {
    val t = LocalTheme.current
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) t.accent else Color(0xFF2A313B)).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, color = if (sel) t.onAccent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileConfigPanel(id: String, modifier: Modifier) {
    val t = LocalTheme.current
    val upd = LocalPrefUpdate.current
    val p = t.p
    val idx = id.drop(1).toIntOrNull() ?: return
    val n = p.homeCount.coerceIn(2, 10)
    if (idx >= n) return
    val tiles = parseHomeTiles(p.homeTiles, n)
    val curKey = tiles[idx]
    val styles = parseStyles(p.homeStyles)
    val curStyle = styles[id] ?: p.homeStyle
    val sizes = parseOffsets(p.homeSizes)
    val size = sizes[id] ?: Offset(1f, 1f)
    val pool = homePool(Vehicles.byId(p.vehicle))

    fun setTile(k: String) = upd(p.copy(homeTiles = tiles.toMutableList().also { it[idx] = k }.joinToString(",")))
    fun setStyle(i: Int) = upd(p.copy(homeStyles = stylesToString(styles + (id to i))))
    fun setSize(x: Float, y: Float) {
        val nx = x.coerceIn(0.4f, 2.5f)
        val ny = y.coerceIn(0.4f, 2.5f)
        upd(p.copy(homeSizes = sizesToString(if (nx == 1f && ny == 1f) sizes - id else sizes + (id to Offset(nx, ny)))))
    }

    Column(
        modifier.width(310.dp).heightIn(max = 290.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF2101418))
            .border(2.dp, t.accent, RoundedCornerShape(14.dp)).padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ô " + (idx + 1) + ": " + homeMetric(curKey).label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            CfgChip("Đóng", false) { HomeEdit.selected.value = null }
        }
        Text("Thông số", color = Color(0xFFB8C0CC), fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            pool.forEach { m -> CfgChip(m.label, m.key == curKey) { setTile(m.key) } }
        }
        Text("Kiểu hiển thị", color = Color(0xFFB8C0CC), fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TILE_STYLES.forEachIndexed { i, nm -> CfgChip(nm, i == curStyle) { setStyle(i) } }
        }
        Text("Kích thước ô (hoặc kéo ô vuông ở góc ô)", color = Color(0xFFB8C0CC), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Rộng " + Math.round(size.x * 100) + "%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(70.dp))
            CfgChip("−", false) { setSize(size.x - 0.1f, size.y) }
            CfgChip("+", false) { setSize(size.x + 0.1f, size.y) }
            Spacer(Modifier.width(6.dp))
            Text("Cao " + Math.round(size.y * 100) + "%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(62.dp))
            CfgChip("−", false) { setSize(size.x, size.y - 0.1f) }
            CfgChip("+", false) { setSize(size.x, size.y + 0.1f) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CfgChip("Đặt lại cỡ ô", false) { setSize(1f, 1f) }
        }
    }
}
