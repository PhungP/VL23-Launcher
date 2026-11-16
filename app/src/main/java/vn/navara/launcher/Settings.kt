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
                                onChange(p.copy(vehicle = v.id, skin = v.skin, homeTiles = v.homeTiles, carImg = 0, homeOffsets = "", obdTiles = "", gearTrim = 2))
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
                        line("Di chuyển các ô ở trang 1", "Mở trang 1 rồi dùng ngón tay kéo từng ô (6 ô thông số, thanh trên, cấp số) tới vị trí muốn. Bấm Xong để lưu.") {
                            Btn("Bắt đầu") { HomeEdit.on.value = true; homeEvents.value = homeEvents.value + 1; onClose() }
                        }
                        if (p.homeOffsets.isNotEmpty()) line("Đưa các ô về vị trí mặc định") { Btn("Đặt lại") { onChange(p.copy(homeOffsets = "")) } }
                    } else {
                        note("Di chuyển ô chỉ dùng được với bố cục mới (không dùng cho bố cục Thể thao).")
                    }
                }

                Group("Trang 1: xe, thông số, cấp số") {
                    choice(
                        "Hình xe", listOf("Ảnh Navara", "Ảnh riêng", "Khung dây", "Bóng xe"), p.carImg,
                        "Ảnh riêng: chọn ảnh xe của bạn (nên dùng PNG nền trong suốt). Khung dây và Bóng xe là hình vẽ vector, hợp với giao diện Robot và SciFi."
                    ) { onChange(p.copy(carImg = it)) }
                    if (p.carImg == 1) line("Ảnh xe của bạn") { Btn("Chọn ảnh") { pickCar.launch("image/*") } }
                    choice(
                        "Hiển thị cấp số", listOf("Số thể thao", "Ô cấp số"), p.gearStyle,
                        "Số thể thao: một chữ số to, nghiêng, không khung. Ô cấp số: chữ số nằm trong một ô có nhãn GEAR."
                    ) { onChange(p.copy(gearStyle = it)) }
                    val slots = parseHomeTiles(p.homeTiles)
                    val slotNames = listOf("Ô trái trên", "Ô trái giữa", "Ô trái dưới", "Ô phải trên", "Ô phải giữa", "Ô phải dưới")
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
                    choice("Công cụ bản đồ", listOf("MapLibre + MapTiler", "OSM đơn giản"), p.mapEngine) { onChange(p.copy(mapEngine = it)) }
                    note(
                        "Khoá MapTiler miễn phí tạo tại cloud.maptiler.com (gói Free: dùng phi thương mại, khoảng 100 nghìn lượt tải mỗi tháng, bắt buộc hiện logo MapTiler). " +
                            "Để trống thì dùng khoá khai báo lúc build; không có khoá app tự dùng bản đồ OSM đơn giản."
                    )
                    full { bg -> Line(bg, "Khoá API MapTiler") { Box(Modifier.width(300.dp)) { KeyField(p.mapTilerKey) { onChange(p.copy(mapTilerKey = it)) } } } }
                    choice(
                        "Kiểu bản đồ", listOf("Streets", "OpenStreetMap", "Bright"), p.mapTheme,
                        "Streets: nhiều đường và địa điểm. OpenStreetMap: giống bản đồ OSM chuẩn (chỉ có bản sáng, chế độ tối dùng Streets)."
                    ) { onChange(p.copy(mapTheme = it)) }
                    tg("Bản đồ xoay theo hướng xe", p.mapHeadingUp) { onChange(p.copy(mapHeadingUp = it)) }
                    tg("Nghiêng 3D (nút 2D/3D)", p.mapTilt) { onChange(p.copy(mapTilt = it)) }
                    choice("Biểu tượng xe", listOf("Tam giác", "Xe bán tải"), if (p.mapMarker == 1) 0 else 1) { onChange(p.copy(mapMarker = if (it == 0) 1 else 0)) }
                    choice("Màu bản đồ", listOf("Theo giao diện", "Sáng", "Tối"), p.mapStyle) { onChange(p.copy(mapStyle = it)) }
                    tg("Đọc chỉ dẫn dẫn đường bằng giọng nói", p.navVoice) { onChange(p.copy(navVoice = it)) }
                    note(
                        "Dẫn đường: chạm nút mũi tên ở cạnh phải trang Bản đồ, nhập điểm đến rồi chọn. Cần mạng và bản đồ MapLibre + MapTiler. " +
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

@Composable
private fun KeyField(value: String, onChange: (String) -> Unit) {
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
            if (text.isEmpty()) Text("Dán khoá API MapTiler vào đây", color = t.muted, fontSize = 18.sp)
            inner()
        }
    )
}
