package vn.navara.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------- Trang Cài đặt kiểu đồng hồ xe: các dòng xanh dương đậm và đen xen kẽ, chữ trắng bên trái,
// công tắc (chỉ màu cam) hoặc lựa chọn bên phải; công tắc xếp 2 cột để hiện được nhiều mục ----------

private sealed class E {
    class Full(val c: @Composable (Brush) -> Unit) : E()
    class Tg(val title: String, val on: Boolean, val ch: (Boolean) -> Unit) : E()
}

private class GB {
    val l = ArrayList<E>()
    val notes = ArrayList<String>()   // chú thích dài đặt dưới nhóm để mọi dòng cao bằng nhau
    fun note(s: String) { notes.add(s) }
    fun full(c: @Composable (Brush) -> Unit) { l.add(E.Full(c)) }
    fun tg(title: String, on: Boolean, ch: (Boolean) -> Unit) { l.add(E.Tg(title, on, ch)) }
    fun line(title: String, desc: String? = null, right: @Composable () -> Unit = {}) {
        if (desc != null) notes.add(desc)
        full { bg -> Line(bg, title, right) }
    }
    fun status(label: String, ok: Boolean) = full { bg -> Line(bg, label) { StatusText(ok) } }
    fun choice(title: String, items: List<String>, sel: Int, desc: String? = null, onSel: (Int) -> Unit) {
        if (desc != null) notes.add(desc)
        full { bg -> Line(bg, title) { Segmented(items, sel, onSel) } }
    }
}

@Composable
private fun rowBrush(i: Int): Brush {
    val t = LocalTheme.current
    return if (t.mono == 0) {
        if (i % 2 == 0) Brush.horizontalGradient(t.rowHi) else SolidColor(t.rowLo)
    } else SolidColor(if (i % 2 == 0) t.panel else t.bg)
}

@Composable
private fun Group(title: String, build: GB.() -> Unit) {
    val t = LocalTheme.current
    val shape = RoundedCornerShape(10.dp)
    val gb = GB().apply(build)
    val l = gb.l
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(), color = t.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        Column(Modifier.fillMaxWidth().clip(shape).border(px(1), if (t.mono == 0) t.line else t.ink.copy(alpha = 0.25f), shape)) {
            var row = 0
            var i = 0
            while (i < l.size) {
                val e = l[i]
                if (e is E.Full) {
                    e.c(rowBrush(row))
                    i++
                } else {
                    val a = e as E.Tg
                    val b = if (i + 1 < l.size) l[i + 1] as? E.Tg else null
                    TwoCols(rowBrush(row), a, b)
                    i += if (b != null) 2 else 1
                }
                row++
            }
        }
        gb.notes.forEach { Text(it, color = t.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp)) }
    }
}

@Composable
private fun TwoCols(bg: Brush, a: E.Tg, b: E.Tg?) {
    val t = LocalTheme.current
    val sep = t.blue.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().background(bg).height(56.dp)) {
        TgCell(a, Modifier.weight(1f).fillMaxHeight())
        TgCell(b, Modifier.weight(1f).fillMaxHeight().drawBehind { drawLine(sep, Offset(0f, 0f), Offset(0f, size.height), 1f) })
    }
}

@Composable
private fun TgCell(e: E.Tg?, modifier: Modifier) {
    val t = LocalTheme.current
    Row(modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (e != null) {
            Text(e.title, color = t.ink, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Toggle(e.on, e.ch)
        }
    }
}

// Một dòng cao cố định 56dp: tiêu đề bên trái, điều khiển bên phải
@Composable
private fun Line(bg: Brush, title: String, right: @Composable () -> Unit = {}) {
    val t = LocalTheme.current
    Row(
        Modifier.fillMaxWidth().background(bg).height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = t.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 21.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        right()
    }
}

// Công tắc: chỉ màu cam khi bật
@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalTheme.current
    val f by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "toggle")
    val track = lerp(Color(0xFF3A3F4D), t.accent, f)
    val thumb = if (t.mono != 0) lerp(t.ink, t.bg, f) else lerp(Color(0xFFAAB3C5), Color.White, f)
    Box(
        Modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(track)
            .clickable { onChange(!on) }.padding(3.dp)
    ) {
        Box(Modifier.offset(x = 24.dp * f).size(26.dp).clip(CircleShape).background(thumb))
    }
}

// Chọn một trong vài phương án: khung viền trắng, phương án đang chọn nền cam
// Độ dày đúng n pixel màn hình (đổi từ dp theo mật độ hiện tại) để nét viền mảnh mà vẫn sắc
@Composable
private fun px(n: Int = 1): androidx.compose.ui.unit.Dp =
    // 1 px: dùng Hairline (đúng 1 pixel vật lý, không bị làm tròn lên 2 px nên nét mảnh và sắc);
    // từ 2 px trở lên trừ một chút để phép làm tròn lên của Compose không đẩy thành n + 1
    if (n <= 1) androidx.compose.ui.unit.Dp.Hairline else ((n - 0.02f) / androidx.compose.ui.platform.LocalDensity.current.density).dp

@Composable
private fun Segmented(items: List<String>, sel: Int, onSel: (Int) -> Unit) {
    val t = LocalTheme.current
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.clip(shape).border(px(1), t.ink, shape)) {
        items.forEachIndexed { i, label ->
            val on = i == sel
            Box(
                Modifier.background(if (on) t.accent else Color.Transparent)
                    .clickable { onSel(i) }.padding(horizontal = 11.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) t.onAccent else t.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Stepper(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val t = LocalTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("−", onClick = onMinus)
        Text(text, color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 76.dp))
        Btn("+", onClick = onPlus)
    }
}

// Danh sách cảm biến TPMS nghe thấy: giá trị đã giải mã, dữ liệu thô và nút gán bánh xe
@Composable
private fun TpmsDevices(bg: Brush, p: UiPrefs, onChange: (UiPrefs) -> Unit) {
    val t = LocalTheme.current
    val seen by TpmsManager.seen.collectAsState()
    val wheels by TpmsManager.wheels.collectAsState()
    val scanning by TpmsManager.scanning.collectAsState()
    val map = parseTpmsMap(p.tpmsMap)
    Column(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (scanning) "Đang nghe cảm biến Bluetooth…" else "Chưa quét được (kiểm tra Bluetooth đã bật và đã cấp quyền Thiết bị lân cận)",
            color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        for (w in TPMS_WHEELS) {
            val v = wheels[w]
            Text(
                tpmsWheelName(w) + ": " + (if (v == null) "--" else String.format(java.util.Locale.US, "%.2f bar  %.0f°C  pin %d%%", v.bar, v.tempC, v.battery)),
                color = t.muted, fontSize = 15.sp
            )
        }
        if (seen.isEmpty()) Text("Chưa nghe thấy cảm biến nào. Cảm biến ngủ khi xe đỗ: lăn bánh vài trăm mét, hoặc xả nhẹ rồi bơm lại lốp để đánh thức.", color = t.muted, fontSize = 15.sp)
        for (s in seen) {
            val assigned = map.entries.firstOrNull { it.value == s.addr }?.key
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    s.name + "  " + s.addr + "  " + s.rssi + " dBm" + (if (assigned != null) "  →  " + tpmsWheelName(assigned) else ""),
                    color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                val r = s.reading
                Text(
                    if (r != null) String.format(java.util.Locale.US, "Đọc được: %.1f kPa (thô), %.1f°C, pin %d%%", r.kpaRaw, r.tempC, r.battery)
                    else "Chưa giải mã được loại cảm biến này. Dữ liệu thô: " + s.hex,
                    color = t.muted, fontSize = 14.sp
                )
                if (r != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (w in TPMS_WHEELS) {
                            Btn(w, modifier = Modifier.widthIn(min = 56.dp)) {
                                val m = map.toMutableMap()
                                m.entries.removeAll { it.value == s.addr || it.key == w }
                                m[w] = s.addr
                                onChange(p.copy(tpmsMap = tpmsMapToString(m)))
                            }
                        }
                        Btn("Bỏ gán") { onChange(p.copy(tpmsMap = tpmsMapToString(map.filterValues { it != s.addr }))) }
                    }
                }
            }
        }
        Text("FL trước trái, FR trước phải, RL sau trái, RR sau phải. Cảm biến chưa gán thì tự theo số trong tên (TPMS1 = FL, 2 = FR, 3 = RL, 4 = RR).", color = t.muted, fontSize = 14.sp)
    }
}

@Composable
private fun Btn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalTheme.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.clip(shape).border(px(1), t.ink, shape).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun StatusText(ok: Boolean) {
    val t = LocalTheme.current
    val c = if (t.mono != 0) (if (ok) t.ink else t.muted) else if (ok) t.green else Color(0xFFE8383D)
    Text(if (ok) "Có" else "Chưa", color = c, fontSize = 19.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SettingsScreen(
    p: UiPrefs,
    obd: ObdData,
    onChange: (UiPrefs) -> Unit,
    onPickBg: (Uri) -> Unit,
    onPickCar: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    // Phím Back trên màn hình thoát khỏi trang Cài đặt
    BackHandler { onClose() }
    val ctx = LocalContext.current
    val t = LocalTheme.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickBg(uri)
    }
    val pickCar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickCar(uri)
    }
    fun open(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun setDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = ctx.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            } catch (e: Exception) { }
        }
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
    }

    // Làm tươi trạng thái mỗi giây (người dùng quay lại từ màn hình cấp quyền của hệ thống)
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val isHome = remember(tick) { isDefaultHome(ctx) }
    val overlay = remember(tick) { Build.VERSION.SDK_INT < 29 || android.provider.Settings.canDrawOverlays(ctx) }
    val gpsPerm = remember(tick) { ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val gpsOn = remember(tick) { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) }
    // Cập nhật trực tuyến: tự kiểm tra một lần khi mở trang Cài đặt
    val upd by Updater.state.collectAsState()
    LaunchedEffect(Unit) { if (Updater.state.value.phase == UpdPhase.IDLE) Updater.check() }



    Box(
        Modifier.fillMaxSize()
            .background(if (t.mono == 0) Brush.verticalGradient(t.pageBg) else SolidColor(t.bg))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
    ) {
        // Nút đóng luôn nổi phía trên, kể cả khi cuộn xuống cuối trang
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 12.dp).zIndex(10f).size(46.dp)
                .clip(CircleShape).background(t.panelHi).border(px(2), t.accent, CircleShape).clickable { onClose() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Đóng", tint = t.ink, modifier = Modifier.size(28.dp)) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
                Text("Cài đặt", color = t.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().height(px(2)).background(Brush.horizontalGradient(listOf(t.blueDeep, t.blueLight, t.blueDeep))))
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Group("Giao diện") {
                    choice(
                        "Bộ giao diện", listOf("Đồng hồ", "Robot", "SciFi"), when (p.skin) { 3 -> 1; 4 -> 2; else -> 0 },
                        "Đồng hồ: nền đen, dải xanh dương, mục đang chọn màu cam. " +
                            "Robot: kim loại xám, đinh tán, đèn hổ phách. SciFi: nền xanh đen, viền neon xanh lơ và hồng tím."
                    ) { onChange(p.copy(skin = listOf(0, 3, 4)[it])) }
                    choice(
                        "Bố cục trang 1", listOf("Bố cục mới", "Thể thao"), p.homeLayout,
                        "Chỉ áp dụng cho bộ Đồng hồ xe. Bố cục mới: ảnh xe ở giữa và 6 ô chỉ số. Thể thao: đồng hồ vòng tua dạng cung, la bàn và số cấp lớn ở giữa."
                    ) { onChange(p.copy(homeLayout = it)) }
                    choice("Cỡ chữ", listOf("Nhỏ", "Vừa", "Lớn", "Rất lớn"), p.textSize) { onChange(p.copy(textSize = it)) }
                    val dm = ctx.resources.displayMetrics
                    choice(
                        "Tỉ lệ giao diện", listOf("100%", "80%", "90%", "110%", "125%", "140%"), p.uiScale,
                        "Giao diện tự co giãn vừa màn hình (800×480, 1280×720, 1920×1080...). Màn hình hiện tại " + maxOf(dm.widthPixels, dm.heightPixels) + "×" + minOf(dm.widthPixels, dm.heightPixels) +
                            " điểm ảnh. Chọn tỉ lệ nhỏ hơn 100% nếu muốn chữ và nút nhỏ lại để hiện được nhiều hơn, lớn hơn 100% nếu muốn dễ nhìn hơn."
                    ) { onChange(p.copy(uiScale = it)) }
                    choice("Kích thước icon", listOf("Nhỏ", "Vừa", "Lớn", "Rất lớn"), p.iconSize) { onChange(p.copy(iconSize = it)) }
                    tg("Biểu tượng Navara đồng nhất", p.navaraIcons) { onChange(p.copy(navaraIcons = it)) }
                    if (!p.navaraIcons) tg("Biểu tượng gốc một màu trắng", p.whiteIcons) { onChange(p.copy(whiteIcons = it)) }
                    if (p.navaraIcons || p.whiteIcons) tg("Biểu tượng xanh kết hợp trắng (theo tông đồng hồ xe)", p.iconBlue) { onChange(p.copy(iconBlue = it)) }
                    tg("Thanh tiêu đề phía trên", p.titleBar) { onChange(p.copy(titleBar = it)) }
                    tg("Ẩn thanh trạng thái hệ thống", p.hideBar) { onChange(p.copy(hideBar = it)) }
                }

                Group("Hồ sơ xe") {
                    val cur = Vehicles.byId(p.vehicle)
                    for (v in Vehicles.all) {
                        val sel = v.id == cur.id
                        line(v.title, v.spec) {
                            if (sel) Text("Đang dùng", color = t.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            else Btn("Chọn") {
                                // Áp dụng hồ sơ: giao diện, ảnh xe, 6 ô thông số, thông số ở trang OBD và bảng tỉ số hộp số
                                onChange(p.copy(vehicle = v.id, skin = v.skin, homeTiles = v.homeTiles, carImg = 0, carPhoto = 0, homeOffsets = "", homeSizes = "", homeStyles = "", obdTiles = "", gearTrim = 2))
                            }
                        }
                    }
                    note(
                        "Chọn đúng xe của bạn để ứng dụng đổi giao diện, ảnh xe, thông số trang 1 và trang OBD, danh sách PID được đọc và bảng tỉ số hộp số tính cấp số. " +
                            "Sau khi chọn, bạn vẫn chỉnh lại từng phần ở các mục bên dưới."
                    )
                    if (!cur.ratiosVerified) note("Lưu ý: tỉ số hộp số của " + cur.title + " là ước tính. Nếu số hiển thị sai, dùng Hiệu chỉnh cấp số ở mục OBD hoặc báo mình để chỉnh bảng tỉ số cho khớp xe thật.")
                    if (cur.gearRatios.isEmpty()) note("Hồ sơ chung không có bảng tỉ số, nên chỉ hiện cấp số khi xe tự báo qua OBD.")
                }

                Group("Hình nền và khung") {
                    choice(
                        "Hình nền", listOf("Đen trơn", "Địa hình", "Chuyển màu", "Ảnh của bạn"), p.bgStyle,
                        "Hình nền hiện phía sau các trang Ứng dụng, thông số OBD và Nhạc. Trang 1 dùng ảnh xe riêng nên không áp dụng hình nền. Khung trong suốt để nhìn xuyên ra hình nền."
                    ) { onChange(p.copy(bgStyle = it)) }
                    if (p.bgStyle == 3) line("Ảnh nền của bạn") { Btn("Chọn ảnh") { pick.launch("image/*") } }
                    choice("Độ tối ảnh nền", listOf("Nhẹ", "Vừa", "Đậm"), p.dim) { onChange(p.copy(dim = it)) }
                    choice(
                        "Độ trong suốt khung", listOf("Đặc", "Hơi mờ", "Mờ", "Rất trong", "Trong"), p.glass,
                        "Áp dụng cho các ô và khung ở trang 1, Ứng dụng, thông số OBD, nhạc và thanh dưới."
                    ) { onChange(p.copy(glass = it)) }
                    choice(
                        "Hình khung thông số", listOf("Vát góc", "Bo tròn", "Lục giác", "Vuông"), p.frameShape,
                        "Hình bao quanh các ô thông số và thanh thông tin ở trang 1."
                    ) { onChange(p.copy(frameShape = it)) }
                    if (p.homeLayout == 0) {
                        line(
                            "Sắp xếp các ô ở trang 1 (kiểu Torque Pro)",
                            "Mở trang 1 rồi kéo từng ô (ô thông số, thanh trên, cấp số) tới vị trí muốn. Kéo ô vuông ở góc một ô thông số để đổi cỡ. " +
                                "Chạm vào một ô thông số để chọn thông số, kiểu hiển thị (số, kim, cung, thanh, số điện tử, đồ thị) và cỡ riêng cho ô đó. Bấm Xong để lưu."
                        ) {
                            Btn("Bắt đầu") { HomeEdit.on.value = true; homeEvents.value = homeEvents.value + 1; onClose() }
                        }
                        if (p.homeOffsets.isNotEmpty() || p.homeSizes.isNotEmpty() || p.homeStyles.isNotEmpty()) {
                            line("Đưa các ô về vị trí, cỡ và kiểu mặc định") { Btn("Đặt lại") { onChange(p.copy(homeOffsets = "", homeSizes = "", homeStyles = "")) } }
                        }
                    } else {
                        note("Di chuyển ô chỉ dùng được với bố cục mới (không dùng cho bố cục Thể thao).")
                    }
                }

                Group("Trang 1: xe, thông số, cấp số") {
                    choice(
                        "Hình xe", listOf("Ảnh hồ sơ", "Ảnh riêng", "Khung dây", "Bóng xe"), p.carImg,
                        "Ảnh riêng: chọn ảnh xe của bạn (nên dùng PNG nền trong suốt). Khung dây và Bóng xe là hình vẽ vector, hợp với giao diện Robot và SciFi."
                    ) { onChange(p.copy(carImg = it)) }
                    full { bg -> Line(bg, "Màu chữ") { Swatches(p.homeText) { onChange(p.copy(homeText = it)) } } }
                    full { bg -> Line(bg, "Màu viền hộp") { Swatches(p.homeLine) { onChange(p.copy(homeLine = it)) } } }
                    full { bg -> Line(bg, "Màu số cấp") { Swatches(p.homeGear) { onChange(p.copy(homeGear = it)) } } }
                    note("Chọn màu cho chữ, viền các hộp và số cấp ở trang 1. Ô tròn có chữ A là theo màu của giao diện đang dùng.")
                    if (p.carImg == 0) {
                        val photos = Vehicles.byId(p.vehicle).photos
                        if (photos.size > 1) choice(
                            "Ảnh của hồ sơ xe", photos.map { it.name }, p.carPhoto.coerceIn(0, photos.size - 1),
                            "Ảnh phong cảnh phủ kín nền trang 1; ảnh Studio là xe rời đặt giữa."
                        ) { onChange(p.copy(carPhoto = it)) }
                    }
                    if (p.carImg == 1) line("Ảnh xe của bạn") { Btn("Chọn ảnh") { pickCar.launch("image/*") } }
                    choice(
                        "Hiển thị cấp số", listOf("Số thể thao", "Ô cấp số"), p.gearStyle,
                        "Số thể thao: một chữ số to, nghiêng, không khung. Ô cấp số: chữ số nằm trong một ô có nhãn GEAR."
                    ) { onChange(p.copy(gearStyle = it)) }
                    line("Số ô thông số", "Từ 2 đến 10 ô, chia đều cho hai cột trái và phải (nhiều ô thì mỗi ô thấp lại). Áp dụng cho bố cục mới.") {
                        Stepper("${p.homeCount.coerceIn(2, 10)} ô", { onChange(p.copy(homeCount = (p.homeCount - 1).coerceIn(2, 10))) }, { onChange(p.copy(homeCount = (p.homeCount + 1).coerceIn(2, 10))) })
                    }
                    choice(
                        "Độ dày viền ô", listOf("Không", "Mảnh", "Vừa", "Dày", "Rất dày"), p.tileBorder,
                        "Độ dày đường viền của các hộp thông số OBD, áp dụng cho trang 1 và trang OBD. Vừa là độ dày như các bản trước."
                    ) { onChange(p.copy(tileBorder = it)) }
                    choice(
                        "Chiều cao ô", listOf("Rất thấp", "Thấp", "Vừa", "Đầy hàng"), p.homeThick,
                        "Đầy hàng: ô lấp đầy cột như các bản trước. Thấp hơn thì ô thấp lại và lộ ra nhiều ảnh xe hơn; ô quá thấp tự chuyển thành một hàng nhãn và số."
                    ) { onChange(p.copy(homeThick = it)) }
                    choice(
                        "Kiểu hiển thị ô", TILE_STYLES, p.homeStyle,
                        "Kiểu mặc định cho mọi ô. Muốn mỗi ô một kiểu: Bắt đầu sắp xếp ở mục \"Sắp xếp các ô ở trang 1\" (nhóm Hình nền và khung) rồi chạm vào ô."
                    ) { onChange(p.copy(homeStyle = it)) }
                    val slotCount = p.homeCount.coerceIn(2, 10)
                    val slots = parseHomeTiles(p.homeTiles, slotCount)
                    val leftCount = (slotCount + 1) / 2
                    val slotNames = List(slotCount) { i -> if (i < leftCount) "Ô trái " + (i + 1) else "Ô phải " + (i - leftCount + 1) }
                    val pool = homePool(Vehicles.byId(p.vehicle))
                    fun setSlot(i: Int, d: Int) {
                        val cur = pool.indexOfFirst { it.key == slots[i] }.coerceAtLeast(0)
                        val nk = pool[(cur + d + pool.size) % pool.size].key
                        onChange(p.copy(homeTiles = slots.toMutableList().also { it[i] = nk }.joinToString(",")))
                    }
                    slots.forEachIndexed { i, k ->
                        line(slotNames[i]) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(homeMetric(k).label, color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(190.dp))
                                Btn("◀") { setSlot(i, -1) }
                                Btn("▶") { setSlot(i, 1) }
                            }
                        }
                    }
                    line("Khôi phục thông số mặc định", "Thông số chỉ hiện khi xe thực sự trả về giá trị; chưa có thì hiện --. Áp dụng cho bộ giao diện Đồng hồ, Robot, SciFi (bố cục mới).") {
                        Btn("Khôi phục") { onChange(p.copy(homeTiles = UiPrefs().homeTiles)) }
                    }
                }

                Group("Trang OBD: kiểu hiển thị thông số") {
                    val obdNames = listOf("Thẻ", "Số", "Kim", "Cung", "Thanh", "Số điện tử", "Đồ thị")
                    line("Kiểu hiển thị", "Thẻ: số kèm thanh nhỏ như các bản trước. Các kiểu còn lại vẽ giống ô ở trang 1: số, đồng hồ kim, đồng hồ cung, thanh ngang, số điện tử 7 đoạn, đồ thị theo thời gian.") {
                        Stepper(
                            obdNames[p.obdStyle.coerceIn(0, 6)],
                            { onChange(p.copy(obdStyle = (p.obdStyle + 6) % 7)) },
                            { onChange(p.copy(obdStyle = (p.obdStyle + 1) % 7)) }
                        )
                    }
                    choice("Số cột thông số", listOf("3", "4", "5", "6"), p.obdCols.coerceIn(3, 6) - 3, "Ít cột thì ô to, dễ nhìn; nhiều cột thì hiện được nhiều thông số cùng lúc. Độ dày viền chỉnh ở nhóm Trang 1.") { onChange(p.copy(obdCols = it + 3)) }
                }

                Group("Gemini AI (trợ lý giọng nói)") {
                    note(
                        "Dán khoá API Gemini của bạn (tạo miễn phí tại aistudio.google.com/apikey) để hiện nút Gemini nổi trên mọi trang. " +
                            "Nút này giúp: nói để tìm đường, hỏi về tình trạng xe, giải thích mã lỗi đọc từ xe, nhận xét chuyến đi. " +
                            "Khoá chỉ lưu trên máy bạn. Tên mô hình để mặc định là được: nếu Google ngừng mô hình đó, app tự chọn mô hình Flash mới nhất. Khi dùng, câu hỏi hoặc đoạn ghi âm, vị trí xe (khi tìm đường) và số liệu xe hiện có được gửi tới Google. " +
                            "Câu trả lời của AI chỉ để tham khảo, không thay thế thợ kỹ thuật; khi có đèn cảnh báo nghiêm trọng hãy dừng xe ở nơi an toàn."
                    )
                    full { bg -> Line(bg, "Khoá API Gemini") { Box(Modifier.width(300.dp)) { KeyField(p.geminiKey, "Dán khoá API Gemini vào đây") { onChange(p.copy(geminiKey = it)) } } } }
                    full { bg -> Line(bg, "Mô hình Gemini") { Box(Modifier.width(300.dp)) { KeyField(p.geminiModel, "gemini-3.8-flash") { onChange(p.copy(geminiModel = it)) } } } }
                    tg("Đọc to câu trả lời", p.geminiSpeak) { onChange(p.copy(geminiSpeak = it)) }
                    if (p.geminiPos.isNotEmpty()) line("Đưa nút Gemini về vị trí mặc định") { Btn("Đặt lại") { onChange(p.copy(geminiPos = "")) } }
                }

                Group("TPMS: áp suất lốp (cảm biến mua ngoài)") {
                    tg("Đọc cảm biến TPMS qua Bluetooth", p.tpmsOn) { onChange(p.copy(tpmsOn = it)) }
                    if (p.tpmsOn) {
                        choice("Đơn vị áp suất", listOf("bar", "psi", "kPa"), p.tpmsUnit) { onChange(p.copy(tpmsUnit = it)) }
                        line("Ngưỡng áp suất thấp (cảnh báo màu cam)") {
                            Stepper(String.format(java.util.Locale.US, "%.1f bar", p.tpmsLow / 10f), { onChange(p.copy(tpmsLow = (p.tpmsLow - 1).coerceIn(5, 40))) }, { onChange(p.copy(tpmsLow = (p.tpmsLow + 1).coerceIn(5, 40))) })
                        }
                        line("Ngưỡng áp suất cao (cảnh báo màu cam)") {
                            Stepper(String.format(java.util.Locale.US, "%.1f bar", p.tpmsHigh / 10f), { onChange(p.copy(tpmsHigh = (p.tpmsHigh - 1).coerceIn(10, 60))) }, { onChange(p.copy(tpmsHigh = (p.tpmsHigh + 1).coerceIn(10, 60))) })
                        }
                        tg("Cảm biến báo áp suất tuyệt đối (trừ áp khí quyển)", p.tpmsAbs) { onChange(p.copy(tpmsAbs = it)) }
                        line("Hiệu chỉnh áp suất", "So với đồng hồ đo lốp thật: đồng hồ cao hơn thì bấm +, thấp hơn thì bấm −. Mỗi bước 0,05 bar.") {
                            Stepper(String.format(java.util.Locale.US, "%+.2f bar", p.tpmsTrim / 100f), { onChange(p.copy(tpmsTrim = (p.tpmsTrim - 5).coerceIn(-60, 60))) }, { onChange(p.copy(tpmsTrim = (p.tpmsTrim + 5).coerceIn(-60, 60))) })
                        }
                        full { bg -> TpmsDevices(bg, p, onChange) }
                    }
                    note(
                        "Cảm biến nắp van Bluetooth (tên TPMS1_..., TPMS4_...) được giải mã sẵn; các loại khác chỉ hiện dữ liệu thô để đối chiếu. " +
                            "Sau khi bật, lên trang 1, bấm Bắt đầu sắp xếp rồi chạm vào một ô và chọn Lốp trước trái, Lốp trước phải, Lốp sau trái, Lốp sau phải (áp suất) hoặc Nhiệt độ lốp. " +
                            "Cảm biến báo áp suất tuyệt đối nên mặc định trừ áp khí quyển khoảng 1,01 bar; nếu số lệch so với đồng hồ đo lốp thì chỉnh ở mục Hiệu chỉnh. " +
                            "Phần giải mã chưa kiểm tra được với cảm biến thật của bạn, nếu số bất thường hãy gửi dòng dữ liệu thô ở trên để chỉnh lại."
                    )
                }

                Group("Trang và điều hướng") {
                    val order = parsePageOrder(p.pageOrder)
                    order.forEachIndexed { i, id ->
                        line("${i + 1}. " + pageName(id)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("▲") {
                                    if (i > 0) {
                                        val l = order.toMutableList(); l.removeAt(i); l.add(i - 1, id)
                                        onChange(p.copy(pageOrder = l.joinToString(",")))
                                    }
                                }
                                Btn("▼") {
                                    if (i < order.size - 1) {
                                        val l = order.toMutableList(); l.removeAt(i); l.add(i + 1, id)
                                        onChange(p.copy(pageOrder = l.joinToString(",")))
                                    }
                                }
                            }
                        }
                    }
                    line("Khôi phục thứ tự mặc định", "Thanh điều hướng bên dưới đổi theo thứ tự này") {
                        Btn("Khôi phục") { onChange(p.copy(pageOrder = UiPrefs().pageOrder)) }
                    }
                }

                Group("Xe và OBD") {
                    choice("Nguồn dữ liệu OBD", listOf("Kết nối trực tiếp", "Torque Pro"), p.obdSource) { onChange(p.copy(obdSource = it)) }
                    line("Trạng thái kết nối", obd.status)
                    if (p.obdSource == 1) {
                        line(
                            "Hướng dẫn Torque Pro",
                            "Trong Torque Pro (tên menu có thể khác theo phiên bản): Cài đặt > Ghi dữ liệu và tải lên > " +
                                "bật tải lên máy chủ web (Upload to webserver), đặt URL là http://127.0.0.1:8765/ " +
                                "rồi chọn các dữ liệu cần gửi: tốc độ, vòng tua, nhiệt độ nước, tải động cơ, nhiệt độ khí nạp, " +
                                "áp suất cổ hút (MAP), áp suất khí quyển, điện áp. Đặt đơn vị hệ mét (kPa, °C) và để Torque chạy nền." +
                                (if (TorqueServer.lastKeys.isNotEmpty()) "\nĐã nhận các khóa: " + TorqueServer.lastKeys else "")
                        )
                    }
                    choice("Nguồn engine load", listOf("Tự động", "Tính toán", "Chân ga", "Mô-men"), p.loadSource) { onChange(p.copy(loadSource = it)) }
                    choice(
                        "Hiệu chỉnh cấp số", listOf("-6%", "-3%", "0", "+3%", "+6%"), p.gearTrim,
                        "Số hiển thị luôn thấp hơn thực tế thì chọn dấu +, luôn cao hơn thì chọn dấu −."
                    ) { onChange(p.copy(gearTrim = it)) }
                }

                Group("Bản đồ") {
                    // mapEngine giữ số cũ: 0 MapTiler, 1 OSM, 3 Goong (số 2 là Google Maps đã bỏ) nên danh sách chọn có 3 mục
                    choice("Công cụ bản đồ", listOf("MapLibre + MapTiler", "OSM đơn giản", "MapLibre + Goong"), if (p.mapEngine == 3) 2 else p.mapEngine.coerceAtMost(1)) { onChange(p.copy(mapEngine = if (it == 2) 3 else it)) }
                    if (p.mapEngine == 3) {
                        note(
                            "Bản đồ Goong dùng \"Maptiles Key\" (tạo tại account.goong.io/keys). Khoá này khác với \"API Key\" ở mục Goong bên dưới. " +
                                "Khoá chỉ lưu trên máy bạn."
                        )
                        full { bg -> Line(bg, "Khoá Maptiles Goong") { Box(Modifier.width(300.dp)) { KeyField(p.goongMapKey, "Dán Maptiles Key của Goong vào đây") { onChange(p.copy(goongMapKey = it)) } } } }
                        choice(
                            "Kiểu bản đồ Goong", listOf("Đường phố", "Sáng", "Dẫn đường"), p.mapTheme,
                            "Đường phố: đầy đủ địa điểm. Sáng: nền nhạt gọn. Dẫn đường: nền tối giản dành cho lái xe (tự đổi ngày/đêm theo Màu bản đồ)."
                        ) { onChange(p.copy(mapTheme = it)) }
                    } else {
                    note(
                        "Khoá MapTiler miễn phí tạo tại cloud.maptiler.com (gói Free: dùng phi thương mại, khoảng 100 nghìn lượt tải mỗi tháng, bắt buộc hiện logo MapTiler). " +
                            "Để trống thì dùng khoá khai báo lúc build; không có khoá app tự dùng bản đồ OSM đơn giản."
                    )
                    full { bg -> Line(bg, "Khoá API MapTiler") { Box(Modifier.width(300.dp)) { KeyField(p.mapTilerKey) { onChange(p.copy(mapTilerKey = it)) } } } }
                    choice(
                        "Kiểu bản đồ", listOf("Streets", "OpenStreetMap", "Bright"), p.mapTheme,
                        "Streets: nhiều đường và địa điểm. OpenStreetMap: giống bản đồ OSM chuẩn (chỉ có bản sáng, chế độ tối dùng Streets)."
                    ) { onChange(p.copy(mapTheme = it)) }
                    }
                    if (p.mapEngine != 1) {
                        note(
                            "Tìm địa chỉ bằng Goong (dùng được với cả MapTiler và Goong): dán \"API Key\" của Goong, loại khoá dành cho dịch vụ, không phải Maptiles Key. " +
                                "Mỗi lần bấm Tìm tốn một lượt gọi Geocoding, câu đã tìm rồi được nhớ lại nên không tốn thêm. Để trống thì tìm bằng Nominatim."
                        )
                        full { bg -> Line(bg, "Khoá API Goong (tìm địa chỉ)") { Box(Modifier.width(300.dp)) { KeyField(p.goongApiKey, "Dán API Key của Goong vào đây") { onChange(p.copy(goongApiKey = it)) } } } }
                        tg("Chỉ đường bằng Goong (tốn thêm lượt gọi)", p.goongRoute) { onChange(p.copy(goongRoute = it)) }
                    }
                    tg("Bản đồ xoay theo hướng xe", p.mapHeadingUp) { onChange(p.copy(mapHeadingUp = it)) }
                    tg("Nghiêng 3D (nút 2D/3D)", p.mapTilt) { onChange(p.copy(mapTilt = it)) }
                    choice("Biểu tượng xe", listOf("Tam giác", "Xe bán tải"), if (p.mapMarker == 1) 0 else 1) { onChange(p.copy(mapMarker = if (it == 0) 1 else 0)) }
                    choice("Màu bản đồ", listOf("Theo giao diện", "Sáng", "Tối"), p.mapStyle) { onChange(p.copy(mapStyle = it)) }
                    tg("Đọc chỉ dẫn dẫn đường bằng giọng nói", p.navVoice) { onChange(p.copy(navVoice = it)) }
                    note(
                        "Dẫn đường: chạm nút mũi tên ở cạnh phải trang Bản đồ, nhập điểm đến rồi chọn. Cần mạng và bản đồ MapLibre (MapTiler hoặc Goong). " +
                            "Giọng nói dùng bộ đọc tiếng Việt của máy (cần cài sẵn dữ liệu giọng tiếng Việt)."
                    )
                }

                Group("Cảnh báo giao thông") {
                    tg("Cảnh báo giới hạn tốc độ và camera", p.trafficAlert) { onChange(p.copy(trafficAlert = it)) }
                    tg("Âm báo khi cảnh báo", p.alertSound) { onChange(p.copy(alertSound = it)) }
                }

                Group("Hệ thống") {
                    tg("Tự khởi động cùng Tbox", p.autoStart) { onChange(p.copy(autoStart = it)) }
                    status("Là màn hình chính mặc định", isHome)
                    status("Quyền hiện trên ứng dụng khác", overlay)
                    status("Quyền vị trí (GPS)", gpsPerm)
                    status("Dịch vụ Vị trí đang bật", gpsOn)
                    if (!isHome) line("Màn hình chính mặc định") { Btn("Đặt làm mặc định") { setDefaultHome() } }
                    if (!overlay) line("Quyền hiện trên ứng dụng khác") {
                        Btn("Cấp quyền") { open(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }
                    }
                    if (!gpsPerm) line("Quyền vị trí cho GPS") {
                        Btn("Cấp quyền") { open(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
                    }
                    if (!gpsOn) line("Dịch vụ Vị trí (GPS)") {
                        Btn("Bật") { open(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    }
                    if (isHome && overlay && p.autoStart) line("✓ Sẵn sàng", "Tbox sẽ tự mở giao diện này khi khởi động.")
                    line("Cài đặt hệ thống của Tbox") { Btn("Mở") { open(Intent(android.provider.Settings.ACTION_SETTINGS)) } }
                    line("Quyền đọc nhạc đang phát", "Để hiện tên bài hát và ảnh bìa") {
                        Btn("Cấp quyền") { open(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    }
                }
                Group("Cập nhật ứng dụng") {
                    line("Phiên bản đang dùng") { Text(BuildConfig.VERSION_NAME, color = t.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    when (upd.phase) {
                        UpdPhase.IDLE -> line("Kiểm tra bản mới trên GitHub") { Btn("Kiểm tra") { Updater.check() } }
                        UpdPhase.CHECKING -> line("Đang kiểm tra bản mới…")
                        UpdPhase.UP_TO_DATE -> line("Đang dùng bản mới nhất") { Btn("Kiểm tra lại") { Updater.check() } }
                        UpdPhase.AVAILABLE -> {
                            line("Có bản mới ${upd.latest}", upd.notes.take(300).ifBlank { null }) {
                                if (upd.apkUrl.isNotBlank()) Btn("Tải và cài") { Updater.download(ctx) }
                                else Btn("Mở GitHub") { open(Intent(Intent.ACTION_VIEW, Uri.parse(upd.pageUrl))) }
                            }
                        }
                        UpdPhase.DOWNLOADING -> line("Đang tải bản mới… ${upd.progress}%")
                        UpdPhase.READY -> line("Đã tải xong ${upd.latest}") { Btn("Cài đặt") { Updater.install(ctx) } }
                        UpdPhase.ERROR -> line("Không cập nhật được", upd.error) { Btn("Thử lại") { Updater.check() } }
                    }
                    note(
                        "Bản mới lấy từ mục Releases của $REPO_URL. Cần có mạng. Lần đầu cài, Android sẽ yêu cầu cho phép " +
                            "\"cài ứng dụng không rõ nguồn\" cho $APP_NAME; bản mới phải ký cùng khoá với bản đang cài."
                    )
                }

                Group("Giới thiệu") {
                    line("Ứng dụng") { Text(APP_NAME, color = t.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    line("Nhà phát triển") { Text(DEV_NAME, color = t.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    line("Trang ứng dụng trên GitHub", REPO_URL) {
                        Btn("Mở link") { open(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

fun isDefaultHome(ctx: Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
}

// Hàng ô tròn chọn màu: ô đầu (chữ A) = theo giao diện, các ô sau lấy từ HOME_COLORS
@Composable
private fun Swatches(sel: Int, onSel: (Int) -> Unit) {
    val t = LocalTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..HOME_COLORS.size) {
            val c: Color? = if (i == 0) null else HOME_COLORS[i - 1]
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c ?: t.panelHi)
                    .border(if (i == sel) px(3) else px(1), if (i == sel) t.accent else t.ink.copy(alpha = 0.5f), CircleShape)
                    .clickable { onSel(i) },
                contentAlignment = Alignment.Center
            ) { if (i == 0) Text("A", color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun KeyField(value: String, hint: String = "Dán khoá API MapTiler vào đây", onChange: (String) -> Unit) {
    val t = LocalTheme.current
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = { text = it.trim(); onChange(text) },
        singleLine = true,
        textStyle = TextStyle(color = t.ink, fontSize = 18.sp),
        cursorBrush = SolidColor(t.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.panelHi).padding(12.dp),
        decorationBox = { inner ->
            if (text.isEmpty()) Text(hint, color = t.muted, fontSize = 18.sp)
            inner()
        }
    )
}
