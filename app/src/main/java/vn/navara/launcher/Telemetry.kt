package vn.navara.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Trang theo dõi OBD kiểu xe đua: dải đèn vòng tua, tốc độ / số / vòng tua dạng số điện tử, rồi lưới mọi thông số xe báo hỗ trợ.
// Chỉ hiện những thông số xe thực sự trả về; chạm vào hàng số lớn để xoá giá trị đỉnh (max).

private class Tile(
    val key: String, val label: String, val unit: String, val dec: Int,
    val lo: Float, val hi: Float, val warn: Float = Float.MAX_VALUE
)

private val TILES = listOf(
    Tile("boost", "BOOST", "bar", 2, 0f, 2f),
    Tile("load", "ENGINE LOAD", "%", 0, 0f, 100f),
    Tile("pedal", "PEDAL", "%", 0, 0f, 100f),
    Tile("torque", "TORQUE", "%", 0, 0f, 100f),
    Tile("torqueNm", "TORQUE", "Nm", 0, 0f, 450f),
    Tile("power", "POWER", "kW", 0, 0f, 140f),
    Tile("coolant", "COOLANT", "°C", 0, 40f, 120f, 105f),
    Tile("oil", "OIL", "°C", 0, 40f, 140f, 120f),
    Tile("iat", "INTAKE", "°C", 0, 0f, 80f, 60f),
    Tile("egt", "EGT", "°C", 0, 100f, 800f, 700f),
    Tile("maf", "MAF", "g/s", 1, 0f, 250f),
    Tile("map", "MAP", "kPa", 0, 0f, 300f),
    Tile("rail", "RAIL", "MPa", 0, 0f, 200f),
    Tile("fuelRate", "FUEL", "L/h", 1, 0f, 40f),
    Tile("lper100", "ECONOMY", "L/100", 1, 0f, 30f),
    Tile("volt", "VOLT", "V", 1, 10f, 16f),
    Tile("baro", "BARO", "kPa", 0, 80f, 110f),
    Tile("ambient", "AMBIENT", "°C", 0, 0f, 50f),
    Tile("timing", "TIMING", "°", 1, -10f, 30f),
    Tile("stft", "FUEL TRIM S", "%", 1, -25f, 25f),
    Tile("ltft", "FUEL TRIM L", "%", 1, -25f, 25f),
    Tile("catTemp", "CATALYST", "°C", 0, 200f, 900f, 850f),
    Tile("lambda", "LAMBDA", "", 2, 0.7f, 1.3f),
    Tile("egr", "EGR", "%", 0, 0f, 100f),
    Tile("fuelLevel", "FUEL TANK", "%", 0, 0f, 100f),
    Tile("odo", "ODO", "km", 0, 0f, 0f),
    Tile("distClear", "SINCE CLEAR", "km", 0, 0f, 0f),
    Tile("runtime", "RUN TIME", "", 0, 0f, 0f),
    // Các thông số nâng cao (mặc định ẩn, bật trong "Chọn thông số")
    Tile("load04", "LOAD (PID 04)", "%", 0, 0f, 100f),
    Tile("abs43", "ABS LOAD", "%", 0, 0f, 100f),
    Tile("throttle", "THROTTLE", "%", 0, 0f, 100f),
    Tile("throttleCmd", "THROTTLE CMD", "%", 0, 0f, 100f),
    Tile("relThrottle", "REL THROTTLE", "%", 0, 0f, 100f),
    Tile("pedalE", "PEDAL E", "%", 0, 0f, 100f),
    Tile("torqueDemand", "DEMAND TQ", "%", 0, 0f, 100f),
    Tile("friction", "FRICTION TQ", "%", 0, 0f, 100f),
    Tile("torqueRef", "REF TORQUE", "Nm", 0, 0f, 0f),
    Tile("injTiming", "INJ TIMING", "°", 1, 0f, 0f),
    Tile("fuelPress", "FUEL PRESS", "kPa", 0, 0f, 0f),
    Tile("ctrlVolt", "ECU VOLT", "V", 1, 0f, 0f),
    Tile("warmups", "WARM-UPS", "", 0, 0f, 0f),
    Tile("distMil", "DIST MIL ON", "km", 0, 0f, 0f),
)

// Các thông số hiện mặc định; còn lại bật thêm trong "Chọn thông số"
private val DEFAULT_ON = setOf(
    "boost", "load", "pedal", "torque", "torqueNm", "power", "coolant", "oil", "iat", "egt", "maf", "map", "rail",
    "fuelRate", "lper100", "volt", "baro", "ambient", "timing", "egr", "fuelLevel", "odo", "distClear", "runtime"
)

// "" = mặc định, "-" = tắt hết, còn lại là danh sách khoá phân cách bằng dấu phẩy
private fun enabledKeys(s: String): Set<String> = when {
    s.isBlank() -> (DEFAULT_ON - Vehicles.current.obdHide) + Vehicles.current.obdAdd
    s == "-" -> emptySet()
    else -> s.split(",").filter { it.isNotBlank() }.toSet()
}

private fun keysToPref(set: Set<String>): String = if (set.isEmpty()) "-" else TILES.filter { it.key in set }.joinToString(",") { it.key }

private fun valueOf(d: ObdData, k: String): Float? = when (k) {
    "boost" -> if (d.status == ObdManager.OK) d.boost else null
    "load" -> d.load?.toFloat()
    "coolant" -> d.coolant?.toFloat()
    "volt" -> d.volt
    else -> d.ex[k]
}

private fun fmt(v: Float, dec: Int): String = String.format(Locale.US, "%." + dec + "f", v)

private fun fmtTime(sec: Float): String {
    val s = sec.toInt()
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
}

@Composable
fun TelemetryPage(obd: ObdData, nav: Nav, update: (UiPrefs) -> Unit) {
    // Bộ giao diện điện tử (Cài đặt > Bộ giao diện) dùng số 7 đoạn và đèn LED; màu đen/trắng do Theme lo cho mọi trang
    TelemetryBody(obd, nav, update, LocalTheme.current.mono != 0)
}

@Composable
private fun TelemetryBody(obd: ObdData, nav: Nav, update: (UiPrefs) -> Unit, digital: Boolean) {
    val t = LocalTheme.current
    val ok = obd.status == ObdManager.OK
    val peaks = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(obd) {
        for (tl in TILES) {
            val v = valueOf(obd, tl.key)
            if (v != null && v > (peaks[tl.key] ?: -1e9f)) peaks[tl.key] = v
        }
        if (obd.rpm.toFloat() > (peaks["rpm"] ?: 0f)) peaks["rpm"] = obd.rpm.toFloat()
        if (obd.speed.toFloat() > (peaks["speed"] ?: 0f)) peaks["speed"] = obd.speed.toFloat()
    }
    val enabled = enabledKeys(t.p.obdTiles)
    var picking by remember { mutableStateOf(false) }
    val shown = TILES.filter { it.key !in Vehicles.current.obdHide }   // thông số không phù hợp với xe đang chọn thì ẩn hẳn
    val tiles = shown.filter { it.key in enabled && valueOf(obd, it.key) != null }
    val off = t.ink.copy(alpha = 0.09f)

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            ShiftBar(smoothF(if (ok) obd.rpm.toFloat() else 0f, 200).toInt())
            Spacer(Modifier.height(6.dp))
            val rpmShow = smoothF(obd.rpm.toFloat(), 200).roundToInt().coerceIn(0, 9999)
            if (digital) {
                val segOff = t.ink.copy(alpha = 0.08f)
                val mono = FontFamily.Monospace
                val spd = if (ok) obd.speed.coerceIn(0, 999) else 0
                Row(Modifier.fillMaxWidth().clickable { peaks.clear() }, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        SevenSeg(if (ok) "%4d".format(rpmShow) else "----", 52.dp, t.ink, segOff)
                        Text("RPM" + (peaks["rpm"]?.let { "  max " + it.toInt() } ?: ""), color = t.muted, fontSize = 14.sp, fontFamily = mono)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        SevenSeg(if (ok) "%3d".format(spd) else "---", 52.dp, t.ink, segOff)
                        Text("km/h" + (peaks["speed"]?.let { "  max " + it.toInt() } ?: ""), color = t.muted, fontSize = 14.sp, fontFamily = mono)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        SevenSeg(if (ok) (obd.gear?.toString() ?: "-") else "-", 52.dp, t.ink, segOff)
                        Text("GEAR", color = t.muted, fontSize = 14.sp, fontFamily = mono)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GEAR POSITION", color = t.muted, fontSize = 14.sp, fontFamily = mono, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 2.dp))
                        GearLed(if (ok) obd.gear else null, Modifier.fillMaxWidth(), 36.dp)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().clickable { peaks.clear() }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(210.dp)) {
                        Text(if (ok) rpmShow.toString() else "--", color = t.ink, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text("RPM" + (peaks["rpm"]?.let { "  max " + it.toInt() } ?: ""), color = t.muted, fontSize = 16.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        // "GEAR POSITION" màu cam, giãn chữ vừa phải, thẳng mép trái với thanh số
                        Text("GEAR POSITION", color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                        GearBar(if (ok) obd.gear else null, Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val mil = obd.ex["mil"]
            val dtc = obd.ex["dtc"]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${obd.pidCount} PID hỗ trợ" + (if (mil != null) " • MIL " + (if (mil > 0f) "BẬT" else "tắt") else "") +
                        (if (dtc != null) " • ${dtc.toInt()} lỗi lưu" else ""),
                    color = if (mil != null && mil > 0f) t.accent else t.muted, fontSize = 15.sp, modifier = Modifier.weight(1f)
                )
                Text("Chọn thông số ›", color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { picking = true }.padding(4.dp))
            }
            if (tiles.isEmpty()) {
                Text(if (!ok) obd.status else if (enabled.isEmpty()) "Chưa chọn thông số nào. Chạm “Chọn thông số”." else "Đang đọc danh sách thông số từ xe…", color = t.muted, fontSize = 18.sp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tiles, key = { it.key }) { tl ->
                        TileView(tl, valueOf(obd, tl.key) ?: 0f, peaks[tl.key], digital)
                    }
                }
            }
        }
        BottomBar(nav, null, false, Modifier.fillMaxWidth())
    }
    if (picking) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f).clip(RoundedCornerShape(14.dp)).background(t.panel).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chọn thông số hiển thị", color = t.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    PickBtn("Hiện tất cả") { update(t.p.copy(obdTiles = keysToPref(shown.map { it.key }.toSet()))) }
                    Spacer(Modifier.width(8.dp))
                    PickBtn("Mặc định") { update(t.p.copy(obdTiles = "")) }
                    Spacer(Modifier.width(8.dp))
                    PickBtn("Xong") { picking = false }
                }
                Text("Chạm để bật hoặc tắt từng thông số. Ô mờ là thông số xe chưa trả về.", color = t.muted, fontSize = 15.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(shown, key = { it.key }) { tl ->
                        val on = tl.key in enabled
                        val has = valueOf(obd, tl.key) != null
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) t.accent else t.panelHi)
                                .clickable {
                                    val ns = if (on) enabled - tl.key else enabled + tl.key
                                    update(t.p.copy(obdTiles = keysToPref(ns)))
                                }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tl.label + if (tl.unit.isNotEmpty()) " (" + tl.unit + ")" else "",
                                color = (if (on) t.onAccent else t.ink).copy(alpha = if (has) 1f else 0.45f),
                                fontSize = 15.sp, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PickBtn(text: String, onClick: () -> Unit) {
    val t = LocalTheme.current
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(t.accent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, color = t.onAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ShiftBar(rpm: Int) {
    val t = LocalTheme.current
    val off = t.ink.copy(alpha = 0.10f)
    val blue = t.accent2
    val orange = t.accent
    val red = Color(0xFFE8383D)
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val n = 36
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val lit = (rpm.toFloat() / Vehicles.current.barMax.toFloat() * n).toInt().coerceIn(0, n)
        for (i in 0 until n) {
            val col = if (i >= lit) off else orange   // cùng màu với chữ số vòng tua
            drawRoundRect(col, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
private fun TileView(tl: Tile, v0: Float, peak: Float?, digital: Boolean) {
    val t = LocalTheme.current
    val sv = smoothF(v0, 240)
    val v = if (tl.key == "runtime") v0 else sv   // số chạy mượt tới giá trị mới (riêng thời gian chạy thì giữ nguyên)
    val ff = if (digital) FontFamily.Monospace else FontFamily.Default
    val hot = v >= tl.warn
    val barCol = if (hot) t.accent else t.accent2
    val hasBar = tl.hi > tl.lo
    Column(Modifier.fillMaxWidth().card(RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tl.label, color = t.muted, fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (peak != null && hasBar) Text("max " + fmt(peak, tl.dec), color = t.muted, fontSize = 14.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (tl.key == "runtime") fmtTime(v) else fmt(v, tl.dec),
                color = if (hot) t.accent else t.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = ff, maxLines = 1
            )
            if (tl.unit.isNotEmpty()) Text(" " + tl.unit, color = t.muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
        }
        if (hasBar) {
            val f = ((v - tl.lo) / (tl.hi - tl.lo)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                drawRoundRect(t.ink.copy(alpha = 0.16f), Offset.Zero, Size(size.width, size.height), CornerRadius(size.height / 2))
                drawRoundRect(barCol, Offset.Zero, Size(size.width * f, size.height), CornerRadius(size.height / 2))
            }
        }
    }
}
