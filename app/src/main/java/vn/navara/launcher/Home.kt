package vn.navara.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ---------- Khung vát góc kiểu đồng hồ xe ----------

// Khung lục giác: hai đầu nhọn trái phải
class HexShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(size.width / 4f)
        val p = Path().apply {
            moveTo(c, 0f); lineTo(size.width - c, 0f); lineTo(size.width, size.height / 2f)
            lineTo(size.width - c, size.height); lineTo(c, size.height); lineTo(0f, size.height / 2f); close()
        }
        return Outline.Generic(p)
    }
}

class Chamfer(private val cut: Dp, private val left: Boolean, private val right: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(if (left) c else 0f, 0f)
            lineTo(if (right) w - c else w, 0f)
            if (right) lineTo(w, c)
            lineTo(w, if (right) h - c else h)
            if (right) lineTo(w - c, h)
            lineTo(if (left) c else 0f, h)
            if (left) lineTo(0f, h - c)
            lineTo(0f, if (left) c else 0f)
            close()
        }
        return Outline.Generic(p)
    }
}

val ClusterLine = Color(0xFF2C4A8A)
private val ClusterFill = listOf(Color(0xFF0B1A3D), Color(0xFF050D24))
private val Label = Color(0xFF7F9AD0)
private val GearBlue = Color(0xFF3D8BFF)

// Khung bo vát hai bên (left/right = có vát góc phía đó), nền xanh đen và viền xanh dương mảnh
@Composable
fun Modifier.panel(cut: Dp = 14.dp, left: Boolean = true, right: Boolean = true, line: Color? = null, border: Float = -1f): Modifier {
    val t = LocalTheme.current
    val shape: Shape = if (t.mono != 0) Chamfer(cut, left, right) else when (t.p.frameShape) {
        1 -> RoundedCornerShape(14.dp)
        2 -> HexShape(18.dp)
        3 -> RectangleShape
        else -> Chamfer(cut * t.cutScale, left, right)
    }
    val c = line ?: (if (t.mono == 0) t.line else t.ink.copy(alpha = 0.45f))
    var m = this.clip(shape)
        .background(if (t.mono == 0) Brush.verticalGradient(t.fillG) else SolidColor(t.panelG))
    // Độ dày viền: border < 0 là mặc định của bộ giao diện; border = 0 là không viền
    val bw = if (border >= 0f) border else if (t.kind == 4) 1.5f else 2f
    if (bw > 0f) {
        if (t.kind == 4) m = m.border((bw + 3.5f).dp, c.copy(alpha = 0.18f), shape)   // SciFi: quầng sáng neon quanh khung
        m = m.border(bw.dp, c, shape)
    }
    if (t.kind == 3) {   // Robot: hai đinh tán ở giữa hai cạnh bên
        m = m.drawBehind {
            val col = Color(0xFF9AA4B2)
            val o = 6.dp.toPx()
            drawCircle(col, 2.2.dp.toPx(), Offset(o, size.height / 2f))
            drawCircle(col, 2.2.dp.toPx(), Offset(size.width - o, size.height / 2f))
        }
    }
    return m
}

// ---------- Trang 1 kiểu đồng hồ xe: thanh trên (giờ, thời tiết, đang phát), ảnh xe ở giữa với 6 ô chỉ số, thanh cấp số ----------

@Composable
fun HomeCluster(obd: ObdData, media: MediaInfo, onGrant: () -> Unit, nav: Nav) {
    val t = LocalTheme.current
    val ww by WeatherManager.state.collectAsState()
    val ok = obd.status == ObdManager.OK
    val rpm = smoothF(obd.rpm.toFloat(), 200).roundToInt().coerceIn(0, 9999)
    val boost = smoothF(obd.boost ?: 0f, 240)
    val load = smoothF((obd.load ?: 0).toFloat(), 240).roundToInt()
    val temp = if (ww != null) "${ww!!.temp}°C" else "--"
    val line = weatherLine(ww)
    val heading = rememberHeading()
    val gps = rememberGps()
    val tp by TpmsManager.wheels.collectAsState()
    val cnt = t.p.homeCount.coerceIn(2, 10)
    val slots = parseHomeTiles(t.p.homeTiles, cnt)
    val styles = parseStyles(t.p.homeStyles)
    val leftN = (cnt + 1) / 2
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    t.homeBg,
                    center = Offset(size.width * 0.5f, size.height * 0.52f), radius = size.width * 0.62f
                )
            )
        }
    ) {
        Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // thanh trên
            Box(Modifier.fillMaxWidth().height(66.dp).movable("top").panel(10.dp, line = t.homeLineC)) {
                // giờ | thời tiết | la bàn (hướng xe đang đi) | ô đang phát
                Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 244.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clockText(), color = t.hInk, fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.widthIn(max = 132.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (ww != null) weatherIcon(ww!!.code) else Icons.Filled.Cloud, null, tint = t.hInk, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(temp, color = t.hInk, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(line, color = t.hMuted, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(headingLabel(heading), color = t.hInk, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            if (heading != null) {
                                val d = (((heading % 360f) + 360f) % 360f).toInt()
                                Text(String.format("%03d°", d), color = t.hMuted, fontSize = 12.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                }
                NowPlayingChip(media, onGrant, nav, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(228.dp).fillMaxHeight())
            }
            // khung giữa kéo xuống sát mép dưới: ảnh xe phủ cả khung, 3 ô thông số mỗi bên nằm đè lên, cấp số ở giữa phía dưới
            Box(Modifier.fillMaxWidth().weight(1f).background(t.frameFillG)) {
                CarPhoto(Modifier.fillMaxSize())
                // Hai cột ô thông số: độ rộng theo màn hình, độ dày theo cài đặt (rất mỏng ... dày đầy hàng).
                // Cột trái cắt góc bên phải, cột phải cắt góc bên trái; mỗi ô có kiểu hiển thị và cỡ riêng.
                BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
                    val colW = (maxWidth * 0.26f).coerceIn(150.dp, 230.dp)
                    val gap = 8.dp
                    val factor = floatArrayOf(0.45f, 0.62f, 0.8f, 1f)[t.p.homeThick.coerceIn(0, 3)]
                    val full = factor >= 1f
                    val totalH = maxHeight
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(
                            Modifier.width(colW).fillMaxHeight(),
                            verticalArrangement = if (full) Arrangement.spacedBy(gap) else Arrangement.SpaceEvenly
                        ) {
                            val th = ((totalH - gap * (leftN - 1)) / leftN) * factor
                            for (i in 0 until leftN) {
                                HomeTile("t$i", slots[i], styles["t$i"] ?: t.p.homeStyle, obd, tp, gps, Modifier.height(th).fillMaxWidth().resizable("t$i").movable("t$i"), true)
                            }
                        }
                        Column(
                            Modifier.width(colW).fillMaxHeight(),
                            verticalArrangement = if (full) Arrangement.spacedBy(gap) else Arrangement.SpaceEvenly
                        ) {
                            val rightN = cnt - leftN
                            val th = ((totalH - gap * (rightN - 1)) / rightN) * factor
                            for (i in leftN until cnt) {
                                HomeTile("t$i", slots[i], styles["t$i"] ?: t.p.homeStyle, obd, tp, gps, Modifier.height(th).fillMaxWidth().resizable("t$i").movable("t$i"), false)
                            }
                        }
                    }
                }
                // cấp số: mặc định chỉ một chữ số to nghiêng kiểu thể thao; có thể chọn kiểu khác hoặc ẩn trong Cài đặt
                run {
                    val gv = if (ok) obd.gear else null
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).movable("gear")) {
                        when (t.p.gearStyle) {
                            1 -> GearBig(gv, Modifier.width(220.dp).height(60.dp))
                            else -> GearSport(gv, Modifier.width(150.dp).height(78.dp))
                        }
                    }
                }
                val editing by HomeEdit.on.collectAsState()
                val selTile by HomeEdit.selected.collectAsState()
                if (editing) {
                    val sid = selTile
                    // Chạm một ô thì hiện bảng chọn thông số, kiểu và cỡ của ô đó; đóng bảng thì về thanh Xong / Đặt lại
                    if (sid != null && sid.length >= 2 && sid[0] == 't' && sid.drop(1).all { it.isDigit() }) TileConfigPanel(sid, Modifier.align(Alignment.Center))
                    else HomeEditBar(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

// Hình xe ở giữa theo lựa chọn trong Cài đặt (ảnh Navara, ảnh riêng, khung dây, bóng xe)
@Composable
private fun CarPhoto(modifier: Modifier) = CarImage(modifier, 0.60f, bleed = true)

// Ô đang phát trong thanh trên: không viền; bấm vào để chuyển nhanh sang trang phát nhạc (chưa cấp quyền đọc nhạc thì mở cấp quyền)
@Composable
private fun NowPlayingChip(media: MediaInfo, onGrant: () -> Unit, nav: Nav, modifier: Modifier) {
    val t = LocalTheme.current
    val art = media.art
    Row(
        modifier.clickable {
            if (!media.hasAccess) onGrant()
            else {
                val i = nav.order.indexOf("music")
                if (i >= 0) nav.go(i)
            }
        }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, tint = t.hInk, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            when {
                !media.hasAccess -> Text("Chạm để cấp quyền đọc nhạc", color = t.hInk, fontSize = 14.sp, maxLines = 2)
                media.title.isEmpty() -> Text("Chưa có nhạc đang phát", color = t.hMuted, fontSize = 15.sp, maxLines = 1)
                else -> {
                    Text(media.title, color = t.hInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOf(media.artist, media.album).filter { it.isNotEmpty() }.joinToString(" • ")
                    if (sub.isNotEmpty()) Text(sub, color = t.hMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// Thanh cấp số 7 ô vát hai đầu: số đang cài màu xanh dương có ánh sáng, mỗi ô có thanh nghiêng bên dưới
// (các cấp đã qua chuyển dần từ xám sang xanh, các cấp chưa tới tối)
@Composable
fun GearBarCluster(gear: Int?, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    val sep = Color(0xFF2B3B60)
    Row(modifier.panel(18.dp).padding(horizontal = 10.dp)) {
        for (i in 1..7) {
            val active = gear == i
            val bar = when {
                gear == null -> Color(0xFF2A2F3A)
                active -> t.glow
                i < gear -> androidx.compose.ui.graphics.lerp(Color(0xFF6B7280), t.glow, if (gear <= 2) 0f else (i - 1) / (gear - 2f))
                else -> Color(0xFF2A2F3A)
            }
            val barAnim by animateColorAsState(bar, tween(220), label = "gearBar")
            val numCol by animateColorAsState(if (active) t.glow else if (t.mono == 0) Color(0xFF98A3BA) else t.muted, tween(220), label = "gearNum")
            Box(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    if (i > 1) drawLine(sep, Offset(0f, 6.dp.toPx()), Offset(0f, size.height - 6.dp.toPx()), 1.dp.toPx())
                }
            ) {
                Text(
                    i.toString(), color = numCol, fontSize = if (active) 32.sp else 28.sp, fontWeight = FontWeight.Bold,
                    style = if (active) TextStyle(shadow = Shadow(t.glow, Offset.Zero, 28f)) else TextStyle.Default,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp)
                )
                Canvas(Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 5.dp).fillMaxWidth().height(6.dp)) {
                    val skew = 6.dp.toPx()
                    val p = Path().apply {
                        moveTo(skew, 0f); lineTo(size.width, 0f); lineTo(size.width - skew, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(p, barAnim)
                }
            }
        }
    }
}
