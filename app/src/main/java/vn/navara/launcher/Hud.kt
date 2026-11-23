package vn.navara.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

// ===== Màn hình HUD Dashboard: 3 giao diện (1 SU-35 Combat, 2 Orange Sport, 3 Cyberpunk Mechanical) =====
// Bố cục, vị trí các ô, số liệu và ảnh xe dùng chung cho cả 3. Mỗi theme CHỈ khác MÀU và KHUNG, nằm trong:
//   HudThemeSu35.kt, HudThemeOrange.kt, HudThemeCyber.kt  (tương ứng theme-su35.css, theme-orange.css, theme-cyberpunk.css của bản xem trước)
// Vị trí các ô là tỉ lệ so với vùng trang, nên co giãn vừa 800×480 và 1280×720.

// Mọi thứ hiển thị trên màn hình: số liệu thật từ OBD, TPMS, thời tiết, nhạc đang phát; chưa có thì hiện "--".
class HudData(
    val time: String, val temp: String, val weather: String, val place: String, val sunny: Boolean,
    val song: String, val songTime: String, val playing: Boolean,
    val rpm: String, val shiftUp: Boolean, val speed: String, val load: String, val coolant: String, val intake: String,
    val volt: String, val fuel: String, val tpms: List<String>, val tpmsUnit: String, val gear: String
)

// Một giao diện HUD. drawFrame vẽ khung của ô (w, h là kích thước ô tính bằng pixel).
interface HudTheme {
    val accent: Color
    val ink: Color
    val label: Color
    val unit: Color
    val icon: Color
    val shift: Color
    val rpmStyle: TextStyle
    val gearStyle: TextStyle
    val rpmScale: Float          // cỡ chữ RPM so với chiều cao vùng trang
    val gearScale: Float
    val gearLabel: String?       // dòng chữ dưới số gear (theme 1: "GEAR ▸ 3"), null = không có
    val playButtonFill: Boolean  // nút phát nền đặc màu nhấn
    val rpmTop: Float            // vị trí trên của cụm RPM (tỉ lệ chiều cao vùng trang)
    val gearTop: Float           // vị trí trên của số gear
    fun drawFrame(d: DrawScope, w: Float, h: Float)
    @Composable fun Back(W: Dp, H: Dp)                 // nền và trang trí nằm dưới xe
    @Composable fun Front(d: HudData, W: Dp, H: Dp)    // trang trí nằm trên xe (vòng ngắm, cụm cơ khí...)
}

// Bộ giao diện (kind): 5 SU-35 Combat, 6 Orange Sport, 7 Cyberpunk Mechanical
fun hudTheme(kind: Int): HudTheme = when (kind) { 6 -> HudThemeOrange; 7 -> HudThemeCyber; else -> HudThemeSu35 }

private fun fmt(v: Float?, dec: Int): String = if (v == null) "--" else String.format(Locale.US, "%." + dec + "f", v).replace('.', ',')
private fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return String.format(Locale.US, "%02d:%02d", s / 60, s % 60) }

// Số liệu thật; chưa nối OBD thì các ô OBD hiện "--"
@Composable
fun rememberHudData(obd: ObdData, media: MediaInfo, p: UiPrefs): HudData {
    val tp by TpmsManager.wheels.collectAsState()
    val w by WeatherManager.state.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(5000) } }
    val ok = obd.status == ObdManager.OK
    val time = java.text.SimpleDateFormat("HH:mm", Locale.US).format(Date(now))
    val unit = p.tpmsUnit
    val wheels = TPMS_WHEELS.map { k -> tp[k]?.let { fmt(tpmsConv(it.bar, unit), tpmsDec(unit)) } ?: "--" }
    val l100 = obd.ex["lper100"]
    val fuel = if (l100 != null && l100 > 0.1f) fmt(100f / l100, 1) else "--"
    val title = media.title
    return HudData(
        time = time,
        temp = if (w != null) w!!.temp.toString() + "°C" else "--",
        weather = if (w != null) weatherText(w!!.code) else "",
        place = w?.place.orEmpty(),
        sunny = w != null && w!!.code in 0..1,
        song = if (title.isEmpty()) "Chưa có nhạc đang phát" else title,
        songTime = if (title.isEmpty()) "--:-- / --:--" else mmss(media.pos) + " / " + mmss(media.dur),
        playing = media.playing,
        rpm = if (ok) obd.rpm.toString() else "--",
        shiftUp = ok && obd.rpm >= (Vehicles.current.rpmMax * 0.8f),
        speed = if (ok) obd.speed.toString() else "--",
        load = if (ok && obd.load != null) obd.load.toString() else "--",
        coolant = if (ok && obd.coolant != null) obd.coolant.toString() else "--",
        intake = if (ok) (obd.intake?.toString() ?: obd.ex["iat"]?.let { fmt(it, 0) } ?: "--") else "--",
        volt = if (ok) fmt(obd.volt, 1) else "--",
        fuel = if (ok) fuel else "--",
        tpms = wheels,
        tpmsUnit = tpmsUnitLabel(unit).replaceFirstChar { it.uppercase() },
        gear = if (ok) (obd.gear?.toString() ?: "-") else "-"
    )
}

// ---------- Trang 1 của ba bộ giao diện HUD ----------
// Toàn màn hình: không có thanh dock và thanh điều hướng; vuốt ngang để sang trang khác.
@Composable
fun HudHome(obd: ObdData, media: MediaInfo) {
    val t = LocalTheme.current
    val upd = LocalPrefUpdate.current
    val theme = hudTheme(t.kind)
    val data = rememberHudData(obd, media, t.p)
    Box(Modifier.fillMaxSize().background(t.bg)) {
        HudStage(theme, data, media, t.p)
        // Ba nút chuyển nhanh giữa các bộ HUD (tắt mặc định, bật trong Cài đặt > Hiển thị)
        if (t.p.hudSwitch) {
            Row(Modifier.offset(6.dp, 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (n in 1..3) {
                    val on = t.kind == 4 + n
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (on) theme.accent else Color(0x99000000))
                            .border(1.dp, if (on) theme.accent else Color(0x55FFFFFF), RoundedCornerShape(5.dp))
                            .clickable { upd(t.p.copy(skin = 4 + n)) },
                        contentAlignment = Alignment.Center
                    ) { Text(n.toString(), color = if (on) Color(0xFF031014) else Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// Một ô thông số ở cột trái hoặc phải
private class HudCell(val label: String, val value: String, val unit: String, val icon: ImageVector, val twoLine: Boolean = false)

@Composable
private fun HudStage(theme: HudTheme, d: HudData, media: MediaInfo, p: UiPrefs) {
    val hide = p.hudHide.split(",").toSet()
    fun show(k: String) = k !in hide
    val tpms = p.tpmsOn && show("tpms")   // chưa bật cảm biến lốp thì không hiện bốn ô lốp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = maxWidth
        val H = maxHeight
        theme.Back(W, H)
        // Ảnh xe do người dùng chọn trong Cài đặt (ảnh theo hồ sơ xe, ảnh riêng, khung dây, bóng xe); dùng chung cho cả 3 bộ giao diện
        CarImage(Modifier.offset(W * 0.20f, H * 0.27f).size(W * 0.60f, H * 0.60f), 0.82f)
        theme.Front(d, W, H)

        if (show("time")) TimeCard(theme, d, W, H)
        if (show("music")) MusicCard(theme, d, media, W, H)

        val left = listOfNotNull(
            if (tpms) HudCell("FL", d.tpms[0], d.tpmsUnit, Icons.Filled.Album) else null,
            if (show("load")) HudCell("ENGINE LOAD", d.load, "%", Icons.Filled.Speed) else null,
            if (show("coolant")) HudCell("NHIỆT ĐỘ NƯỚC", d.coolant, "°C", Icons.Filled.Thermostat) else null,
            if (show("volt")) HudCell("ĐIỆN ÁP ẮC QUY", d.volt, "v", Icons.Filled.BatteryChargingFull) else null,
            if (tpms) HudCell("RL", d.tpms[2], d.tpmsUnit, Icons.Filled.Album) else null
        )
        val right = listOfNotNull(
            if (tpms) HudCell("FR", d.tpms[1], d.tpmsUnit, Icons.Filled.Album) else null,
            if (show("speed")) HudCell("TỐC ĐỘ", d.speed, "km/h", Icons.Filled.Speed) else null,
            if (show("intake")) HudCell("NHIỆT ĐỘ KHÍ NẠP", d.intake, "°C", Icons.Filled.AcUnit) else null,
            if (show("fuel")) HudCell("MỨC TIÊU THỤ NHIÊN LIỆU", d.fuel, "km/l", Icons.Filled.LocalGasStation, twoLine = true) else null,
            if (tpms) HudCell("RR", d.tpms[3], d.tpmsUnit, Icons.Filled.Album) else null
        )
        // Hai cột dùng chung chiều cao ô: ít ô thì mỗi ô to hơn (tối đa 17% chiều cao); cột trái có thêm dòng hướng đi ở dưới cùng
        val y0 = 0.25f
        val span = 0.715f
        val gap = 0.014f
        val chip = 0.07f
        var cell = 0.17f
        if (left.isNotEmpty()) cell = minOf(cell, (span - chip - gap * left.size) / left.size)
        if (right.isNotEmpty()) cell = minOf(cell, (span - gap * (right.size - 1)) / right.size)
        val tw = 0.19f
        val leftTotal = left.size * cell + gap * left.size + chip
        var yl = y0 + (span - leftTotal) / 2f
        for (c in left) {
            StatTile(theme, W, H, 0.03f, yl, tw, cell, c.label, c.value, c.unit, c.icon, c.twoLine)
            yl += cell + gap
        }
        HudHeading(theme, W, H, 0.03f, yl, tw, chip)
        val rightTotal = right.size * cell + gap * (right.size - 1).coerceAtLeast(0)
        var yr = y0 + (span - rightTotal) / 2f
        for (c in right) {
            StatTile(theme, W, H, 0.78f, yr, tw, cell, c.label, c.value, c.unit, c.icon, c.twoLine)
            yr += cell + gap
        }

        HudRpm(theme, d, W, H)
        HudGear(theme, d, W, H)
    }
}

// Khung của một ô do theme vẽ; nội dung nằm trong
@Composable
private fun HudBox(theme: HudTheme, W: Dp, H: Dp, x: Float, y: Float, w: Float, h: Float, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    BoxWithConstraints(Modifier.offset(W * x, H * y).size(W * w, H * h)) {
        Canvas(Modifier.fillMaxSize()) { theme.drawFrame(this, size.width, size.height) }
        content()
    }
}

@Composable
private fun StatTile(
    theme: HudTheme, W: Dp, H: Dp, x: Float, y: Float, w: Float, h: Float,
    label: String, value: String, unit: String, icon: ImageVector, twoLine: Boolean = false
) {
    HudBox(theme, W, H, x, y, w, h) {
        val hh = maxHeight.value
        val ww = maxWidth.value
        val iconSize = (hh * 0.5f).coerceAtMost(ww * 0.19f)
        Row(Modifier.fillMaxSize().padding(start = (ww * 0.085f).dp, end = (ww * 0.1f).dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = theme.icon, modifier = Modifier.size(iconSize.dp))
            Spacer(Modifier.width((ww * 0.04f).dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    label, color = theme.label, fontSize = (hh * (if (twoLine) 0.17f else 0.19f)).coerceIn(6.5f, 10f).sp,
                    fontWeight = FontWeight.Bold, maxLines = if (twoLine) 2 else 1, softWrap = twoLine, lineHeight = (hh * 0.19f).coerceIn(7f, 11f).sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value, color = theme.ink, fontSize = (hh * (if (twoLine) 0.34f else 0.42f)).coerceAtMost(ww * 0.19f).sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                    )
                    Text(
                        unit, color = theme.unit, fontSize = (hh * 0.2f).coerceAtLeast(7f).sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeCard(theme: HudTheme, d: HudData, W: Dp, H: Dp) {
    HudBox(theme, W, H, 0.03f, 0.056f, 0.22f, 0.16f) {
        val hh = maxHeight.value
        val ww = maxWidth.value
        Row(Modifier.fillMaxSize().padding(start = (ww * 0.1f).dp, end = (ww * 0.08f).dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (d.sunny) Icons.Filled.WbSunny else Icons.Filled.Cloud, null, tint = Color(0xFFC9D6E2), modifier = Modifier.size((hh * 0.42f).dp))
            Spacer(Modifier.width((ww * 0.05f).dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(d.time, color = theme.ink, fontSize = (hh * 0.36f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text((d.temp + " " + d.weather).trim(), color = theme.label, fontSize = (hh * 0.15f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = theme.icon, modifier = Modifier.size((hh * 0.15f).dp))
                    Text(d.place.ifEmpty { "--" }, color = theme.label, fontSize = (hh * 0.15f).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MusicCard(theme: HudTheme, d: HudData, media: MediaInfo, W: Dp, H: Dp) {
    val c = media.controller
    HudBox(theme, W, H, 0.722f, 0.056f, 0.25f, 0.164f) {
        val hh = maxHeight.value
        val ww = maxWidth.value
        Column(Modifier.fillMaxSize().padding(start = (ww * 0.1f).dp, end = (ww * 0.1f).dp, top = (hh * 0.1f).dp, bottom = (hh * 0.08f).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = theme.icon, modifier = Modifier.size((hh * 0.3f).dp))
                Spacer(Modifier.width((ww * 0.04f).dp))
                Column {
                    Text(
                        d.song, color = theme.ink, fontSize = (hh * 0.2f).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp)
                    )
                    Text(d.songTime, color = theme.label, fontSize = (hh * 0.15f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((ww * 0.1f).dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SkipPrevious, null, tint = theme.icon, modifier = Modifier.size((hh * 0.3f).dp).clickable { c?.transportControls?.skipToPrevious() })
                Box(
                    Modifier.size((hh * 0.34f).dp).clip(CircleShape).then(if (theme.playButtonFill) Modifier.background(theme.accent) else Modifier.border(1.dp, theme.accent, CircleShape))
                        .clickable { if (d.playing) c?.transportControls?.pause() else c?.transportControls?.play() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (d.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = if (theme.playButtonFill) Color(0xFF1A0C04) else theme.icon, modifier = Modifier.size((hh * 0.22f).dp))
                }
                Icon(Icons.Filled.SkipNext, null, tint = theme.icon, modifier = Modifier.size((hh * 0.3f).dp).clickable { c?.transportControls?.skipToNext() })
            }
        }
    }
}

@Composable
private fun HudRpm(theme: HudTheme, d: HudData, W: Dp, H: Dp) {
    Column(Modifier.offset(W * 0.31f, H * theme.rpmTop).size(W * 0.38f, H * 0.23f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.height(H * 0.05f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.shiftUp) {
                Box(Modifier.size(7.dp, 2.dp).background(theme.shift))
                Text("SHIFT UP", color = theme.shift, fontSize = (H.value * 0.045f).sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                Box(Modifier.size(7.dp, 2.dp).background(theme.shift))
            }
        }
        val fs = H.value * theme.rpmScale
        Text(d.rpm, style = theme.rpmStyle.copy(fontSize = fs.sp, lineHeight = (fs * 1.02f).sp), maxLines = 1, softWrap = false)
        Text("vòng/phút", color = theme.ink, fontSize = (H.value * 0.04f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun HudGear(theme: HudTheme, d: HudData, W: Dp, H: Dp) {
    Column(Modifier.offset(W * 0.39f, H * theme.gearTop).size(W * 0.22f, H * 0.25f), horizontalAlignment = Alignment.CenterHorizontally) {
        val fs = H.value * theme.gearScale
        Text(d.gear, style = theme.gearStyle.copy(fontSize = fs.sp, lineHeight = (fs * 1.02f).sp), maxLines = 1, softWrap = false)
        val lbl = theme.gearLabel
        if (lbl != null) Text(lbl + d.gear, color = theme.shift, fontSize = (H.value * 0.042f).sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
    }
}

// Bánh răng n răng, dùng cho trang trí cơ khí của theme 3
fun gearPath(cx: Float, cy: Float, ro: Float, ri: Float, n: Int = 12): androidx.compose.ui.graphics.Path {
    val p = androidx.compose.ui.graphics.Path()
    val w = (2.0 * Math.PI / n).toFloat()
    var first = true
    for (k in 0 until n) {
        val a0 = k * w
        for (pair in listOf(0f to ri, 0.12f to ro, 0.38f to ro, 0.5f to ri)) {
            val ang = a0 + pair.first * 2f * w
            val x = cx + pair.second * kotlin.math.cos(ang)
            val y = cy + pair.second * kotlin.math.sin(ang)
            if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
        }
    }
    p.close()
    return p
}

// Hướng xe đang đi: mũi tên chỉ đường quay theo hướng, kèm tên hướng và số độ; nằm dưới cùng cột trái
@Composable
private fun HudHeading(theme: HudTheme, W: Dp, H: Dp, x: Float, y: Float, w: Float, h: Float) {
    val heading = rememberHeading()
    val hh = H.value * h
    Row(Modifier.offset(W * x, H * y).size(W * w, H * h), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        NavArrow(heading, (hh * 0.95f).dp)
        Spacer(Modifier.width(6.dp))
        Text(headingLabel(heading), color = theme.ink, fontSize = (hh * 0.52f).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (heading != null) {
            val d = (((heading % 360f) + 360f) % 360f).toInt()
            Text("  " + String.format(Locale.US, "%03d°", d), color = theme.label, fontSize = (hh * 0.42f).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}
