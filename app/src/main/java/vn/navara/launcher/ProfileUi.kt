package vn.navara.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

// ===== Quản lý hồ sơ xe: danh sách, chi tiết, tạo / sửa, nhân bản, nhập / xuất, sao lưu / khôi phục =====

// Độ dày đúng n pixel màn hình (giống px trong Settings.kt)
@Composable
private fun pxd(n: Int = 1): androidx.compose.ui.unit.Dp =
    if (n <= 1) androidx.compose.ui.unit.Dp.Hairline else ((n - 0.02f) / androidx.compose.ui.platform.LocalDensity.current.density).dp

private sealed class PScreen {
    object List : PScreen()
    class Detail(val id: String) : PScreen()
    class Edit(val base: ProfileData, val isNew: Boolean) : PScreen()
    class Import(val preview: ImportPreview, val restore: Boolean) : PScreen()
    class Export(val selected: Set<String>) : PScreen()
    object Backup : PScreen()
}

private fun fuelName(f: String) = if (f == "DIESEL") "Dầu" else "Xăng"
private fun typeName(t: String) = when (t) { "SEDAN" -> "Sedan"; "PICKUP" -> "Bán tải"; "SUV" -> "SUV"; "HATCHBACK" -> "Hatchback"; else -> "Khác" }
private fun skinName(s: Int) = when (s) { 5 -> "HUD SU-35"; 6 -> "HUD Orange"; 7 -> "HUD Cyber"; else -> "Đồng hồ" }
private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

private fun summary(d: ProfileData): String = listOf(
    d.engine, if (d.displacementL > 0) "%.1fL".format(Locale.US, d.displacementL) else "", fuelName(d.fuel), d.gearbox,
).filter { it.isNotBlank() }.distinct().joinToString(" · ")

@Composable
private fun PBtn(text: String, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalTheme.current
    val shape = RoundedCornerShape(10.dp)
    val c = if (danger) Color(0xFFE8383D) else if (primary) t.accent else t.ink
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).then(if (primary && enabled) Modifier.background(t.accent) else Modifier.border(pxd(1), c, shape))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (!enabled) t.muted else if (primary) Color.Black else c, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalTheme.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.heightIn(min = 38.dp).clip(shape).background(if (on) t.accent else t.panelHi).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (on) Color.Black else t.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun TField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "", numeric: Boolean = false) {
    val t = LocalTheme.current
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = t.ink, fontSize = 18.sp), cursorBrush = SolidColor(t.accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(t.panelHi).padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = t.muted, fontSize = 17.sp); inner() }
    )
}

@Composable
private fun Dialog(title: String, body: String, confirm: String, danger: Boolean = false, cancel: String = "Huỷ", onConfirm: () -> Unit, onCancel: () -> Unit, content: @Composable () -> Unit = {}) {
    val t = LocalTheme.current
    Box(Modifier.fillMaxSize().zIndex(20f).background(Color(0xB0000000)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onCancel), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(0.92f).clip(RoundedCornerShape(16.dp)).background(t.panel).border(pxd(1), t.line, RoundedCornerShape(16.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = t.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (body.isNotEmpty()) Text(body, color = t.muted, fontSize = 16.sp)
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PBtn(cancel, Modifier.weight(1f), onClick = onCancel)
                PBtn(confirm, Modifier.weight(1f), primary = !danger, danger = danger, onClick = onConfirm)
            }
        }
    }
}

private class Msg(val text: String, val error: Boolean)

@Composable
fun ProfileManagerScreen(prefs: UiPrefs, onChange: (UiPrefs) -> Unit, onClose: () -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Profiles.init(ctx)
    val ver by Profiles.version.collectAsState()
    val userList by Profiles.repo.profiles.collectAsState()
    var screen by remember { mutableStateOf<PScreen>(PScreen.List) }
    var msg by remember { mutableStateOf<Msg?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProfileData?>(null) }
    var dupFrom by remember { mutableStateOf<ProfileData?>(null) }
    var pendingSave by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Boolean?>(null) }   // chờ chọn tệp: false = nhập, true = khôi phục
    val activeId = prefs.vehicle
    fun note(s: String, err: Boolean = false) { msg = Msg(s, err) }
    LaunchedEffect(msg) { if (msg != null) { delay(5000); msg = null } }

    val allData = remember(ver, userList) { Profiles.builtinData() + userList }
    fun select(v: VehicleProfile) { onChange(prefs.withVehicle(v)); note("Đã chọn: " + v.title) }

    suspend fun readFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { i ->
                val bytes = i.readNBytes(MAX_IMPORT_BYTES + 1)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val restore = pendingRestore ?: false
        pendingRestore = null
        if (uri != null) scope.launch {
            busy = true
            val text = readFile(uri)
            busy = false
            if (text == null) { note("Không đọc được tệp", true); return@launch }
            val p = ProfileIO.preview(text, Profiles.userData(), Profiles.builtinData(), Profiles.repo::newId)
            if (p.fatal != null) note(p.fatal, true) else screen = PScreen.Import(p, restore)
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingSave.toByteArray(Charsets.UTF_8)) } ?: error("") }.isSuccess }
            busy = false
            if (ok) note("Đã lưu tệp") else note("Không ghi được tệp", true)
        }
    }
    fun share(text: String, name: String) {
        try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, name).apply { writeText(text, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Chia sẻ").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { note("Không mở được bảng chia sẻ", true) }
    }

    BackHandler { if (screen is PScreen.List) onClose() else screen = PScreen.List }

    Box(
        Modifier.fillMaxSize().background(if (t.mono == 0) Brush.verticalGradient(t.pageBg) else SolidColor(t.bg))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (screen !is PScreen.List) {
                    PBtn("‹ Hồ sơ xe", onClick = { screen = PScreen.List })
                    Spacer(Modifier.width(12.dp))
                }
                val title = when (val s = screen) {
                    is PScreen.List -> "Hồ sơ xe"
                    is PScreen.Detail -> "Chi tiết hồ sơ"
                    is PScreen.Edit -> if (s.isNew) "Tạo hồ sơ xe" else "Sửa hồ sơ xe"
                    is PScreen.Import -> if (s.restore) "Khôi phục" else "Nhập hồ sơ"
                    is PScreen.Export -> "Xuất hồ sơ"
                    PScreen.Backup -> "Sao lưu và khôi phục"
                }
                Text(title, color = t.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.size(46.dp).clip(CircleShape).background(t.panelHi).border(pxd(2), t.accent, CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Đóng", tint = t.ink, modifier = Modifier.size(28.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Đang dùng:", color = t.muted, fontSize = 15.sp)
                Text(Vehicles.byId(activeId).title, color = t.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = screen) {
                    is PScreen.List -> ListScreen(allData, activeId, ::select,
                        { screen = PScreen.Detail(it) },
                        { screen = PScreen.Edit(newBlank(Profiles.repo.newId()), true) },
                        { pendingRestore = false; openFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        { screen = PScreen.Export(userList.map { it.id }.toSet()) },
                        { screen = PScreen.Backup })
                    is PScreen.Detail -> {
                        val d = allData.firstOrNull { it.id == s.id }
                        if (d == null) { screen = PScreen.List } else DetailScreen(d, activeId,
                            onSelect = { select(Vehicles.byId(d.id)) },
                            onEdit = { screen = PScreen.Edit(d, false) },
                            onDuplicate = { dupFrom = d },
                            onExport = { screen = PScreen.Export(setOf(d.id)) },
                            onDelete = { confirmDelete = d },
                            onResetUi = { onChange(prefs.withVehicle(Vehicles.byId(d.id))); note("Đã đặt lại giao diện theo hồ sơ") })
                    }
                    is PScreen.Edit -> EditScreen(s.base, s.isNew) { saved -> note("Đã lưu hồ sơ: " + saved.displayName); screen = PScreen.Detail(saved.id) }
                    is PScreen.Import -> ImportScreen(s.preview, s.restore, activeId, onDone = { text, newActive ->
                        note(text)
                        if (newActive != null) onChange(prefs.withVehicle(Vehicles.byId(newActive)))
                        else if (Vehicles.all.none { it.id == activeId }) onChange(prefs.withVehicle(Vehicles.builtin.first()))
                        screen = PScreen.List
                    }, onError = { note(it, true) })
                    is PScreen.Export -> ExportScreen(allData, s.selected,
                        onShare = { ids -> share(ProfileIO.exportJson(allData.filter { it.id in ids }, BuildConfig.VERSION_NAME), "vl23-vehicle-profiles.json") },
                        onSave = { ids -> pendingSave = ProfileIO.exportJson(allData.filter { it.id in ids }, BuildConfig.VERSION_NAME); saveFile.launch("vl23-vehicle-profiles.json") })
                    PScreen.Backup -> BackupScreen(userList.size,
                        onShare = { share(ProfileIO.backupJson(userList, activeId, BuildConfig.VERSION_NAME), "vl23-profile-backup.json") },
                        onSave = { pendingSave = ProfileIO.backupJson(userList, activeId, BuildConfig.VERSION_NAME); saveFile.launch("vl23-profile-backup.json") },
                        onRestore = { pendingRestore = true; openFile.launch(arrayOf("application/json", "text/plain", "*/*")) })
                }
                if (busy) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { Text("Đang xử lý…", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            msg?.let {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(if (it.error) Color(0xFF5A1A1C) else Color(0xFF1E4A2B)).padding(12.dp)) {
                    Text(it.text, color = Color.White, fontSize = 16.sp)
                }
            }
        }

        dupFrom?.let { src ->
            var name by remember(src.id) { mutableStateOf(src.displayName + " (bản sao)") }
            Dialog("Nhân bản hồ sơ", "Tạo hồ sơ mới giữ nguyên thông số của \"${src.displayName}\". Hồ sơ gốc không bị thay đổi.", "Nhân bản",
                onConfirm = {
                    try { val n = Profiles.repo.duplicate(src, name); Profiles.publish(); dupFrom = null; note("Đã nhân bản: " + n.displayName); screen = PScreen.Detail(n.id) }
                    catch (e: ProfileException) { note(e.errors.firstOrNull() ?: e.message ?: "Lỗi", true); dupFrom = null }
                    catch (e: Exception) { note("Không lưu được: " + e.message, true); dupFrom = null }
                }, onCancel = { dupFrom = null }) { TField(name, { name = it.take(60) }, hint = "Tên hồ sơ mới") }
        }
        confirmDelete?.let { d ->
            Dialog("Xoá hồ sơ?", "Xoá \"${d.displayName}\" vĩnh viễn. Không thể hoàn tác (hãy xuất hoặc sao lưu trước nếu cần).", "Xoá", danger = true,
                onConfirm = {
                    try { Profiles.repo.delete(d.id, activeId); Profiles.publish(); note("Đã xoá: " + d.displayName); screen = PScreen.List }
                    catch (e: ProfileException) { note(e.message ?: "Không xoá được", true) }
                    catch (e: Exception) { note("Không ghi được: " + e.message, true) }
                    confirmDelete = null
                }, onCancel = { confirmDelete = null })
        }
    }
}

private fun newBlank(id: String) = ProfileData(id = id, brand = "", model = "", fuel = "PETROL", vehicleType = "SEDAN")

// ---------------- Danh sách ----------------

@Composable
private fun ListScreen(
    all: List<ProfileData>, activeId: String, onSelect: (VehicleProfile) -> Unit, onOpen: (String) -> Unit,
    onCreate: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onBackup: () -> Unit,
) {
    val t = LocalTheme.current
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }   // 0 tất cả, 1 dựng sẵn, 2 của tôi, 3 xăng, 4 dầu
    var sort by remember { mutableStateOf(0) }     // 0 tên, 1 năm mới nhất, 2 hãng
    val shown = remember(all, q, filter, sort) {
        val nq = plain(q.trim())
        all.filter {
            (nq.isEmpty() || plain(it.displayName + " " + it.engine + " " + it.brand + " " + it.model).contains(nq)) &&
                when (filter) { 1 -> it.builtin; 2 -> !it.builtin; 3 -> it.fuel == "PETROL"; 4 -> it.fuel == "DIESEL"; else -> true }
        }.sortedWith(when (sort) { 1 -> compareByDescending<ProfileData> { it.year }.thenBy { it.displayName }; 2 -> compareBy<ProfileData> { it.brand.lowercase() }.thenBy { it.displayName }; else -> compareBy { it.displayName.lowercase() } })
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
            PBtn("+ Tạo xe", primary = true, onClick = onCreate); PBtn("Nhập", onClick = onImport); PBtn("Xuất", onClick = onExport); PBtn("Sao lưu", onClick = onBackup)
        }
        TField(q, { q = it }, hint = "Tìm theo tên, hãng, động cơ…")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
            listOf("Tất cả", "Dựng sẵn", "Của tôi", "Xăng", "Dầu").forEachIndexed { i, s -> Chip(s, filter == i) { filter = i } }
            Spacer(Modifier.width(8.dp))
            listOf("Tên", "Năm", "Hãng").forEachIndexed { i, s -> Chip("Xếp: $s", sort == i) { sort = i } }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (all.none { !it.builtin } && filter == 2) "Bạn chưa tạo hồ sơ nào. Bấm \"+ Tạo xe\" hoặc nhân bản một hồ sơ có sẵn." else "Không có hồ sơ phù hợp", color = t.muted, fontSize = 18.sp)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(shown, key = { it.id }) { d ->
                val active = d.id == activeId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.panel).border(pxd(if (active) 2 else 1), if (active) t.accent else t.line, RoundedCornerShape(12.dp))
                        .clickable { onOpen(d.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(d.displayName, color = t.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(summary(d).ifBlank { "—" }, color = t.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (d.builtin) "MẶC ĐỊNH" else "CỦA TÔI", color = if (d.builtin) t.muted else t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(typeName(d.vehicleType).uppercase(), color = t.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (active) Text("ĐANG DÙNG", color = t.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    else PBtn("Chọn") { onSelect(Vehicles.byId(d.id)) }
                }
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier = this.fillMaxWidth().horizontalScroll(rememberScrollState())

// ---------------- Chi tiết ----------------

@Composable
private fun DetailScreen(d: ProfileData, activeId: String, onSelect: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit, onResetUi: () -> Unit) {
    val t = LocalTheme.current
    val active = d.id == activeId
    fun f(v: Double, unit: String, dec: Int = 1) = if (v > 0) "%.${dec}f".format(Locale.US, v) + " " + unit else "—"
    val rows = listOf(
        "Tên" to d.displayName, "Hãng / Model" to listOf(d.brand, d.model).filter { it.isNotBlank() }.joinToString(" "), "Năm" to (if (d.year > 0) "${d.year}" else "—"), "Phiên bản" to d.variant.ifBlank { "—" },
        "Động cơ" to d.engine.ifBlank { "—" }, "Dung tích" to f(d.displacementL, "L"), "Nhiên liệu" to fuelName(d.fuel) + if (d.turbo > 0) " · turbo x${d.turbo}" else "",
        "Hộp số" to d.gearbox.ifBlank { "—" }, "Dẫn động" to d.drivetrain.ifBlank { "—" }, "Loại xe" to typeName(d.vehicleType),
        "Tỉ số hộp số" to (if (d.gearRatios.isEmpty()) "Không dùng (chỉ hiện số khi xe báo)" else d.gearRatios.joinToString(" / ") { "%.3f".format(Locale.US, it) } + if (!d.ratiosVerified) "  (ước tính)" else ""),
        "Cầu cuối" to f(d.finalDrive, "", 3), "Chu vi lốp" to "%.3f m".format(Locale.US, d.tireM), "Cỡ mâm" to d.wheelSize.ifBlank { "—" },
        "Vạch đỏ / Thang vòng tua" to "${d.redline} / ${d.rpmMax}", "Vòng tua không tải" to "${d.idleRpm}", "Tốc độ tối đa" to (if (d.maxSpeedKmh > 0) "${d.maxSpeedKmh} km/h" else "—"), "Bình nhiên liệu" to f(d.fuelTankL, "L", 0),
        "Giao thức OBD" to (if (d.obdProtocol == 0) "Tự động" else "ATSP${d.obdProtocol}"), "PID ưu tiên" to d.priorityPids.joinToString(", ").ifBlank { "—" }, "PID bỏ qua" to d.skipPids.joinToString(", ").ifBlank { "—" },
        "Giao diện" to skinName(d.skin), "Ô thông số trang 1" to d.homeTiles.ifBlank { "—" },
        "Ngưỡng cảnh báo riêng" to (if (d.alerts.isEmpty) "Dùng cài đặt chung" else listOfNotNull(
            d.alerts.coolantWarn?.let { "nước ≥${it.toInt()}°C" }, d.alerts.coolantCrit?.let { "nước nghiêm trọng ≥${it.toInt()}°C" }, d.alerts.voltLow?.let { "điện áp <%.1fV".format(Locale.US, it) },
            d.alerts.rpmMax?.let { "vòng tua >${it.toInt()}" }, d.alerts.boostMax?.let { "tăng áp >%.1f bar".format(Locale.US, it) },
            d.alerts.tpmsLowBar?.let { "lốp thấp <%.1f".format(Locale.US, it) }, d.alerts.tpmsHighBar?.let { "lốp cao >%.1f".format(Locale.US, it) }).joinToString(", ")),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
            if (active) Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) { Text("ĐANG DÙNG", color = t.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            else PBtn("Chọn dùng", primary = true, onClick = onSelect)
            if (!d.builtin) PBtn("Sửa", onClick = onEdit)
            PBtn("Nhân bản", onClick = onDuplicate)
            PBtn("Xuất", onClick = onExport)
            PBtn("Đặt lại giao diện", onClick = onResetUi)
            if (!d.builtin) PBtn("Xoá", danger = true, enabled = !active, onClick = onDelete)
        }
        if (d.builtin) Text("Hồ sơ mặc định của ứng dụng: không sửa hoặc xoá được. Hãy nhân bản để tạo bản của riêng bạn.", color = t.muted, fontSize = 14.sp)
        if (!d.builtin && active) Text("Đang dùng nên chưa xoá được. Chọn hồ sơ khác trước.", color = t.muted, fontSize = 14.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.panel)) {
            rows.forEachIndexed { i, (k, v) ->
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Color.Transparent else t.panelHi.copy(alpha = 0.4f)).padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(k, color = t.muted, fontSize = 16.sp, modifier = Modifier.weight(0.4f))
                    Text(v, color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.6f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------- Tạo / sửa ----------------

private class Form(d: ProfileData) {
    var nickname by mutableStateOf(d.nickname); var brand by mutableStateOf(d.brand); var model by mutableStateOf(d.model)
    var year by mutableStateOf(if (d.year > 0) d.year.toString() else ""); var variant by mutableStateOf(d.variant)
    var engine by mutableStateOf(d.engine); var disp by mutableStateOf(if (d.displacementL > 0) d.displacementL.toString() else "")
    var fuel by mutableStateOf(d.fuel); var turbo by mutableStateOf(d.turbo); var gearbox by mutableStateOf(d.gearbox)
    var drive by mutableStateOf(d.drivetrain); var type by mutableStateOf(d.vehicleType)
    var ratios by mutableStateOf(d.gearRatios.joinToString(", ")); var finalDrive by mutableStateOf(if (d.finalDrive > 0) d.finalDrive.toString() else "")
    var tire by mutableStateOf(d.tireM.toString()); var wheel by mutableStateOf(d.wheelSize); var verified by mutableStateOf(d.ratiosVerified)
    var redline by mutableStateOf(d.redline.toString()); var rpmMax by mutableStateOf(d.rpmMax.toString()); var idle by mutableStateOf(d.idleRpm.toString())
    var barMax by mutableStateOf(d.barMax.toString()); var maxSpeed by mutableStateOf(if (d.maxSpeedKmh > 0) d.maxSpeedKmh.toString() else "")
    var tank by mutableStateOf(if (d.fuelTankL > 0) d.fuelTankL.toString() else "")
    var protocol by mutableStateOf(d.obdProtocol.toString()); var priPids by mutableStateOf(d.priorityPids.joinToString(", ")); var skipPids by mutableStateOf(d.skipPids.joinToString(", "))
    var skin by mutableStateOf(d.skin); var tiles by mutableStateOf(d.homeTiles)
    val a = listOf(d.alerts.coolantWarn, d.alerts.coolantCrit, d.alerts.voltLow, d.alerts.rpmMax, d.alerts.boostMax, d.alerts.tpmsLowBar, d.alerts.tpmsHighBar).map { mutableStateOf(it?.toString() ?: "") }
    val keep = d   // các trường không có trong form (ô ẩn, thông số OBD) giữ nguyên từ hồ sơ gốc

    fun build(errs: MutableList<String>): ProfileData {
        fun d(label: String, s: String, def: Double?): Double? { val x = s.trim().replace(',', '.'); if (x.isEmpty()) { if (def == null) errs.add("Thiếu $label"); return def }; return x.toDoubleOrNull() ?: run { errs.add("$label phải là số"); def } }
        fun i(label: String, s: String, def: Int?): Int? { val v = d(label, s, def?.toDouble()); return v?.let { if (it != Math.floor(it)) { errs.add("$label phải là số nguyên"); def } else it.toInt() } }
        fun list(s: String) = s.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        fun alert(idx: Int, label: String) = a[idx].value.trim().replace(',', '.').let { if (it.isEmpty()) null else it.toFloatOrNull() ?: run { errs.add("$label phải là số"); null } }
        val rs = list(ratios).map { it.replace(',', '.').toDoubleOrNull() ?: run { errs.add("Tỉ số hộp số \"$it\" không phải số"); 0.0 } }
        return keep.copy(
            nickname = nickname.trim(), brand = brand.trim(), model = model.trim(), year = i("Năm", year, 0) ?: 0, variant = variant.trim(),
            engine = engine.trim(), displacementL = d("Dung tích", disp, 0.0) ?: 0.0, fuel = fuel, turbo = turbo, gearbox = gearbox.trim(), drivetrain = drive, vehicleType = type,
            gearRatios = rs, finalDrive = d("Cầu cuối", finalDrive, 0.0) ?: 0.0, tireM = d("Chu vi lốp", tire, null) ?: 2.0, wheelSize = wheel.trim(), ratiosVerified = verified,
            redline = i("Vạch đỏ", redline, null) ?: 0, rpmMax = i("Thang vòng tua", rpmMax, null) ?: 0, idleRpm = i("Vòng tua không tải", idle, null) ?: 0, barMax = i("Thang dải đèn", barMax, null) ?: 0,
            maxSpeedKmh = i("Tốc độ tối đa", maxSpeed, 0) ?: 0, fuelTankL = d("Bình nhiên liệu", tank, 0.0) ?: 0.0,
            obdProtocol = i("Giao thức OBD", protocol, 0) ?: 0, priorityPids = list(priPids).map { it.uppercase() }, skipPids = list(skipPids).map { it.uppercase() },
            skin = skin, homeTiles = tiles.trim(),
            alerts = AlertOverride(alert(0, "Nước cảnh báo"), alert(1, "Nước nghiêm trọng"), alert(2, "Điện áp thấp"), alert(3, "Vòng tua cao"), alert(4, "Tăng áp"), alert(5, "Lốp thấp"), alert(6, "Lốp cao")),
        )
    }
}

@Composable
private fun EditScreen(base: ProfileData, isNew: Boolean, onSaved: (ProfileData) -> Unit) {
    val t = LocalTheme.current
    val form = remember(base.id) { Form(base) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    val scroll = rememberScrollState()

    @Composable fun Sec(s: String) { Text(s.uppercase(), color = t.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp)) }
    @Composable fun Row2(label: String, content: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = t.ink, fontSize = 16.sp, modifier = Modifier.weight(0.38f)); Box(Modifier.weight(0.62f)) { content() }
        }
    }
    @Composable fun In(label: String, v: String, num: Boolean = false, hint: String = "", set: (String) -> Unit) = Row2(label) { TField(v, set, hint = hint, numeric = num) }
    @Composable fun <T> Pick(label: String, items: List<T>, sel: T, name: (T) -> String, set: (T) -> Unit) = Row2(label) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollRow()) { items.forEach { Chip(name(it), it == sel) { set(it) } } }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (errors.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF5A1A1C)).padding(12.dp)) {
                Text("Chưa lưu được, cần sửa:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                errors.take(8).forEach { Text("• $it", color = Color.White, fontSize = 15.sp) }
            }
            Sec("Thông tin cơ bản")
            In("Tên gọi (tuỳ chọn)", form.nickname, hint = "Ví dụ: Xe nhà") { form.nickname = it }
            In("Hãng xe", form.brand) { form.brand = it }
            In("Model", form.model) { form.model = it }
            In("Năm sản xuất", form.year, true, "2015") { form.year = it }
            In("Phiên bản", form.variant) { form.variant = it }
            In("Động cơ", form.engine, hint = "Ví dụ: 2.4L i-VTEC") { form.engine = it }
            In("Dung tích (lít)", form.disp, true, "2.4") { form.disp = it }
            Pick("Nhiên liệu", FUELS, form.fuel, ::fuelName) { form.fuel = it }
            Pick("Turbo", listOf(0, 1, 2), form.turbo, { if (it == 0) "Không" else "x$it" }) { form.turbo = it }
            In("Hộp số", form.gearbox, hint = "Ví dụ: Tự động 5AT") { form.gearbox = it }
            Pick("Dẫn động", DRIVETRAINS, form.drive, { it.ifBlank { "—" } }) { form.drive = it }
            Pick("Loại xe", VEHICLE_TYPES, form.type, ::typeName) { form.type = it }
            Sec("Thông số vận hành")
            In("Vạch đỏ (vòng/phút)", form.redline, true) { form.redline = it }
            In("Thang vòng tua", form.rpmMax, true) { form.rpmMax = it }
            In("Vòng tua không tải", form.idle, true) { form.idle = it }
            In("Thang dải đèn vòng tua", form.barMax, true) { form.barMax = it }
            In("Tốc độ tối đa (km/h)", form.maxSpeed, true) { form.maxSpeed = it }
            In("Bình nhiên liệu (lít)", form.tank, true) { form.tank = it }
            In("Chu vi lốp (mét)", form.tire, true, "2.09") { form.tire = it }
            In("Cỡ mâm", form.wheel, hint = "215/55R17") { form.wheel = it }
            In("Tỉ số hộp số", form.ratios, hint = "2.706, 1.552, 1.000 …") { form.ratios = it }
            In("Tỉ số cầu cuối", form.finalDrive, true) { form.finalDrive = it }
            Pick("Tỉ số đã kiểm chứng", listOf(true, false), form.verified, { if (it) "Chính xác" else "Ước tính" }) { form.verified = it }
            Sec("Cấu hình OBD")
            In("Giao thức (0 = tự động)", form.protocol, true) { form.protocol = it }
            In("PID ưu tiên", form.priPids, hint = "Ví dụ: 11, 05 (tối đa 6)") { form.priPids = it }
            In("PID bỏ qua", form.skipPids, hint = "Ví dụ: 78, 23") { form.skipPids = it }
            Sec("Giao diện / HUD")
            Pick("Bộ giao diện", SKINS, form.skin, ::skinName) { form.skin = it }
            In("6 ô trang 1", form.tiles, hint = "rpm,load,coolant,…") { form.tiles = it }
            Sec("Ngưỡng cảnh báo riêng (để trống = dùng cài đặt chung)")
            listOf("Nước làm mát cảnh báo (°C)", "Nước làm mát nghiêm trọng (°C)", "Điện áp thấp (V)", "Vòng tua quá cao", "Tăng áp cao (bar)", "Lốp thấp (bar)", "Lốp cao (bar)").forEachIndexed { i, l ->
                In(l, form.a[i].value, true) { form.a[i].value = it }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PBtn(if (isNew) "Tạo hồ sơ" else "Lưu thay đổi", Modifier.weight(1f), primary = true) {
                val errs = ArrayList<String>()
                val d = form.build(errs)
                if (errs.isEmpty()) errs.addAll(ProfileValidation.validate(d))
                if (errs.isNotEmpty()) { errors = errs; return@PBtn }
                try {
                    val saved = if (isNew) Profiles.repo.create(d) else Profiles.repo.update(d)
                    Profiles.publish(); onSaved(saved)
                } catch (e: ProfileException) { errors = e.errors.ifEmpty { listOf(e.message ?: "Lỗi") } }
                catch (e: Exception) { errors = listOf("Không ghi được: " + e.message) }
            }
        }
    }
}

// ---------------- Xuất ----------------

@Composable
private fun ExportScreen(all: List<ProfileData>, initial: Set<String>, onShare: (Set<String>) -> Unit, onSave: (Set<String>) -> Unit) {
    val t = LocalTheme.current
    var sel by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
            Chip("Hồ sơ của tôi", false) { sel = all.filter { !it.builtin }.map { it.id }.toSet() }
            Chip("Chọn tất cả", false) { sel = all.map { it.id }.toSet() }
            Chip("Bỏ chọn", false) { sel = emptySet() }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(all, key = { it.id }) { d ->
                val on = d.id in sel
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.panel).clickable { sel = if (on) sel - d.id else sel + d.id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).border(pxd(2), t.accent, RoundedCornerShape(6.dp)).background(if (on) t.accent else Color.Transparent))
                    Column(Modifier.weight(1f)) {
                        Text(d.displayName, color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (d.builtin) "Mặc định" else "Của tôi", color = t.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Text("Tệp JSON (UTF-8) chỉ chứa thông số hồ sơ, không chứa khoá API hay dữ liệu cá nhân.", color = t.muted, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PBtn("Chia sẻ (${sel.size})", Modifier.weight(1f), primary = true, enabled = sel.isNotEmpty()) { onShare(sel) }
            PBtn("Lưu vào tệp", Modifier.weight(1f), enabled = sel.isNotEmpty()) { onSave(sel) }
        }
    }
}

// ---------------- Sao lưu / khôi phục ----------------

@Composable
private fun BackupScreen(userCount: Int, onShare: () -> Unit, onSave: () -> Unit, onRestore: () -> Unit) {
    val t = LocalTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bản sao lưu gồm $userCount hồ sơ của bạn (kèm giao diện, ô thông số và ngưỡng cảnh báo của từng hồ sơ) và hồ sơ đang chọn.", color = t.ink, fontSize = 17.sp)
        Text("Không gồm khoá API, token, bộ nhớ đệm hay trạng thái chạy. Cài đặt chung của ứng dụng (ngoài hồ sơ xe) chưa nằm trong bản sao lưu này.", color = t.muted, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PBtn("Chia sẻ bản sao lưu", Modifier.weight(1f), primary = true, enabled = userCount > 0, onClick = onShare)
            PBtn("Lưu vào tệp", Modifier.weight(1f), enabled = userCount > 0, onClick = onSave)
        }
        if (userCount == 0) Text("Bạn chưa có hồ sơ nào để sao lưu.", color = t.muted, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text("KHÔI PHỤC", color = t.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Chọn tệp sao lưu để xem trước. Bạn quyết định gộp hay thay thế; dữ liệu hiện tại chỉ bị đổi sau khi bạn xác nhận, và được giữ nguyên nếu tệp lỗi hoặc ghi thất bại.", color = t.muted, fontSize = 15.sp)
        PBtn("Chọn tệp để khôi phục…", Modifier.fillMaxWidth(), onClick = onRestore)
    }
}

// ---------------- Nhập / khôi phục ----------------

private fun statusText(s: ImportStatus) = when (s) {
    ImportStatus.NEW -> "Mới"; ImportStatus.DUP_ID_USER -> "Trùng mã với hồ sơ của bạn"; ImportStatus.DUP_ID_BUILTIN -> "Trùng mã với hồ sơ mặc định"; ImportStatus.INVALID -> "Không hợp lệ"
}
private fun actionText(a: ImportAction) = when (a) { ImportAction.IMPORT_NEW -> "Nhập thành hồ sơ mới"; ImportAction.REPLACE -> "Thay thế hồ sơ hiện có"; ImportAction.SKIP -> "Bỏ qua" }

@Composable
private fun ImportScreen(p: ImportPreview, restore: Boolean, activeId: String, onDone: (String, String?) -> Unit, onError: (String) -> Unit) {
    val t = LocalTheme.current
    val actions = remember(p) { mutableStateListOf<ImportAction>().apply { addAll(p.items.map { it.defaultAction }) } }
    var confirm by remember { mutableStateOf<String?>(null) }   // "replace" | "restoreAll"
    val validCount = p.items.count { it.data != null }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tệp có ${p.items.size} hồ sơ, hợp lệ $validCount." + if (p.schema == 0) " (Định dạng cũ, không có schema.)" else "", color = t.ink, fontSize = 17.sp)
        if (restore) Text("Có thể gộp (chỉ thêm hồ sơ mới) hoặc thay toàn bộ hồ sơ của bạn bằng nội dung tệp.", color = t.muted, fontSize = 14.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(p.items.size) { i ->
                val it = p.items[i]
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(it.data?.displayName ?: "Mục ${i + 1}", color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    it.data?.let { d -> Text(summary(d).ifBlank { "—" }, color = t.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(statusText(it.status) + if (it.nameClash) " · trùng tên" else "", color = if (it.status == ImportStatus.INVALID) Color(0xFFE8383D) else if (it.status == ImportStatus.NEW) t.accent else t.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    it.errors.take(3).forEach { e -> Text("• $e", color = Color(0xFFE8383D), fontSize = 13.sp) }
                    if (!restore && it.allowed.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScrollRow()) {
                            it.allowed.forEach { a -> Chip(actionText(a), actions[i] == a) { actions[i] = a } }
                        }
                    }
                }
            }
        }
        if (!restore) {
            val replaceNames = p.items.indices.filter { actions[it] == ImportAction.REPLACE }.mapNotNull { p.items[it].data?.displayName }
            val doIt = {
                val r = ProfileIO.apply(p.items, actions.toList(), Profiles.repo)
                Profiles.publish()
                onDone("Nhập xong: thêm ${r.added}, thay thế ${r.replaced}, bỏ qua ${r.skipped}" + if (r.errors.isNotEmpty()) ". Lỗi: " + r.errors.first() else "", null)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PBtn("Nhập các mục đã chọn", Modifier.weight(1f), primary = true, enabled = actions.any { it != ImportAction.SKIP }) { if (replaceNames.isNotEmpty()) confirm = "replace" else doIt() }
            }
            if (confirm == "replace") Dialog("Thay thế hồ sơ?", "Các hồ sơ sau sẽ bị GHI ĐÈ bằng nội dung trong tệp:\n" + replaceNames.joinToString("\n") { "• $it" } + (if (p.items.any { actions[p.items.indexOf(it)] == ImportAction.REPLACE && it.data?.id == activeId }) "\n\nTrong đó có hồ sơ đang dùng." else ""), "Thay thế", danger = true, onConfirm = { confirm = null; doIt() }, onCancel = { confirm = null })
        } else {
            val mergeIt = {
                val r = ProfileIO.apply(p.items, p.items.map { it.defaultAction }, Profiles.repo)
                Profiles.publish()
                onDone("Đã gộp: thêm ${r.added}, bỏ qua ${r.skipped}", null)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PBtn("Gộp (chỉ thêm mới)", Modifier.weight(1f), primary = true, enabled = validCount > 0, onClick = mergeIt)
                PBtn("Thay toàn bộ…", Modifier.weight(1f), danger = true, enabled = validCount > 0) { confirm = "restoreAll" }
            }
            if (confirm == "restoreAll") Dialog("Thay toàn bộ hồ sơ của bạn?", "Hồ sơ của bạn hiện có sẽ được thay bằng $validCount hồ sơ trong tệp. Hồ sơ không có trong tệp sẽ mất (hãy sao lưu trước nếu cần). Nếu có lỗi khi ghi, dữ liệu cũ được giữ nguyên.", "Thay toàn bộ", danger = true,
                onConfirm = {
                    confirm = null
                    try {
                        val newActive = ProfileIO.restoreAll(p, Profiles.repo, Vehicles.builtin.map { it.id }.toSet())
                        Profiles.publish()
                        onDone("Đã khôi phục $validCount hồ sơ", newActive)
                    } catch (e: ProfileException) { onError(e.message ?: "Không khôi phục được") }
                }, onCancel = { confirm = null })
        }
    }
}
