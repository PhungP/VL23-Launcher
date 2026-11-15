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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ---------- Khung vát góc kiểu đồng hồ xe ----------

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
fun Modifier.panel(cut: Dp = 14.dp, left: Boolean = true, right: Boolean = true, line: Color? = null): Modifier {
    val t = LocalTheme.current
    val shape = Chamfer(cut, left, right)
    val c = line ?: (if (t.mono == 0) ClusterLine else t.ink.copy(alpha = 0.45f))
    return this.clip(shape)
        .background(if (t.mono == 0) Brush.verticalGradient(ClusterFill) else SolidColor(t.panel))
        .border(2.dp, c, shape)
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
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF0E2C5C), Color(0xFF071634), Color(0xFF02050F)),
                    center = Offset(size.width * 0.5f, size.height * 0.52f), radius = size.width * 0.62f
                )
            )
        }
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // thanh trên
            Box(Modifier.fillMaxWidth().height(66.dp).panel(10.dp)) {
                // giờ | thời tiết | la bàn (hướng xe đang đi) | ô đang phát
                Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 244.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clockText(), color = t.ink, fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.widthIn(max = 132.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (ww != null) weatherIcon(ww!!.code) else Icons.Filled.Cloud, null, tint = t.ink, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(temp, color = t.ink, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(line, color = t.muted, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(headingLabel(heading), color = t.ink, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            if (heading != null) {
                                val d = (((heading % 360f) + 360f) % 360f).toInt()
                                Text(String.format("%03d°", d), color = t.muted, fontSize = 12.sp, lineHeight = 13.sp)
                            }
                        }
                    }
                }
                NowPlayingChip(media, onGrant, nav, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(228.dp).fillMaxHeight())
            }
            // khung giữa: 3 ô trái, ảnh xe, 3 ô phải
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0x73050C20)).border(1.5.dp, Color(0xFF1F3560))) {
                Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClusterTile("RPM", if (ok) rpm.toString() else "--", "", Modifier.weight(1f).fillMaxWidth(), true)
                        ClusterTile("ENGINE LOAD", if (ok && obd.load != null) "$load" else "--", if (ok && obd.load != null) "%" else "", Modifier.weight(1f).fillMaxWidth(), true)
                        val cool = obd.coolant
                        ClusterTile("NHIỆT ĐỘ NƯỚC", if (ok && cool != null) "$cool" else "--", if (ok && cool != null) "°C" else "", Modifier.weight(1f).fillMaxWidth(), true, warn = cool != null && cool >= 105)
                    }
                    CarPhoto(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClusterTile("TURBO BOOST", if (ok) "%.2f".format(boost) else "--", if (ok) "BAR" else "", Modifier.weight(1f).fillMaxWidth(), false)
                        val iat = obd.intake
                        ClusterTile("NHIỆT ĐỘ KHÍ NẠP", if (ok && iat != null) "$iat" else "--", if (ok && iat != null) "°C" else "", Modifier.weight(1f).fillMaxWidth(), false)
                        ClusterTile("ĐIỆN ÁP ẮC QUY", if (ok && obd.volt != null) "%.1f".format(obd.volt) else "--", if (ok && obd.volt != null) "V" else "", Modifier.weight(1f).fillMaxWidth(), false)
                    }
                }
            }
            // thanh cấp số
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(170.dp).height(18.dp).clip(Chamfer(6.dp, true, true)).background(Color(0xFF0A1A3C)),
                    contentAlignment = Alignment.Center
                ) { Text("CẤP SỐ / GEAR", color = Label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
                GearBarCluster(if (ok) obd.gear else null, Modifier.fillMaxWidth().height(52.dp))
            }
        }
    }
}

// Ô chỉ số vát góc: nhãn xanh nhạt, giá trị trắng to (cam khi vượt ngưỡng), đơn vị nhỏ
@Composable
private fun ClusterTile(label: String, value: String, unit: String, modifier: Modifier, cutRight: Boolean, warn: Boolean = false) {
    val t = LocalTheme.current
    Column(
        modifier.panel(10.dp, left = !cutRight, right = cutRight),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = if (t.mono == 0) Label else t.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = if (warn) t.accent else t.ink, fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, color = t.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
        }
    }
}

// Ảnh xe ở giữa: hiện trọn chiếc xe, chỉ làm mờ dần phần nền ảnh ở rìa để hoà vào nền xanh đen
@Composable
private fun CarPhoto(modifier: Modifier) {
    Image(
        painterResource(R.drawable.navara_photo), null,
        modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
            drawContent()
            val corner = sqrt(size.width * size.width + size.height * size.height) / 2f
            drawRect(
                Brush.radialGradient(0.0f to Color.Black, 0.60f to Color.Black, 1f to Color.Transparent, center = center, radius = corner),
                blendMode = BlendMode.DstIn
            )
        },
        contentScale = ContentScale.Fit
    )
}

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
            else Icon(Icons.Filled.MusicNote, null, tint = t.ink, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            when {
                !media.hasAccess -> Text("Chạm để cấp quyền đọc nhạc", color = t.ink, fontSize = 14.sp, maxLines = 2)
                media.title.isEmpty() -> Text("Chưa có nhạc đang phát", color = t.muted, fontSize = 15.sp, maxLines = 1)
                else -> {
                    Text(media.title, color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOf(media.artist, media.album).filter { it.isNotEmpty() }.joinToString(" • ")
                    if (sub.isNotEmpty()) Text(sub, color = t.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                active -> GearBlue
                i < gear -> androidx.compose.ui.graphics.lerp(Color(0xFF6B7280), Color(0xFF3D82FF), if (gear <= 2) 0f else (i - 1) / (gear - 2f))
                else -> Color(0xFF2A2F3A)
            }
            val barAnim by animateColorAsState(bar, tween(220), label = "gearBar")
            val numCol by animateColorAsState(if (active) GearBlue else if (t.mono == 0) Color(0xFF98A3BA) else t.muted, tween(220), label = "gearNum")
            Box(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    if (i > 1) drawLine(sep, Offset(0f, 6.dp.toPx()), Offset(0f, size.height - 6.dp.toPx()), 1.dp.toPx())
                }
            ) {
                Text(
                    i.toString(), color = numCol, fontSize = if (active) 32.sp else 28.sp, fontWeight = FontWeight.Bold,
                    style = if (active) TextStyle(shadow = Shadow(Color(0xFF2F7BFF), Offset.Zero, 28f)) else TextStyle.Default,
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
