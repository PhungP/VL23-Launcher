package vn.navara.launcher

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

// ===== Nút Gemini nổi trên mọi trang + bảng trợ lý (tìm đường bằng lời nói, hỏi về xe, giải thích mã lỗi, nhận xét chuyến đi) =====
// Chỉ hiện khi đã nhập khoá API Gemini trong Cài đặt. Kéo nút để đổi chỗ, chạm để mở.

private fun parseGeminiPos(s: String): Offset {
    val a = s.split(",")
    val x = a.getOrNull(0)?.toFloatOrNull()
    val y = a.getOrNull(1)?.toFloatOrNull()
    return if (x != null && y != null) Offset(x, y) else Offset.Zero
}

@Composable
fun GeminiOverlay(obd: ObdData, nav: Nav, update: (UiPrefs) -> Unit) {
    val t = LocalTheme.current
    val p = t.p
    if (p.geminiKey.isBlank()) return
    var open by remember { mutableStateOf(false) }
    val curP by rememberUpdatedState(p)
    val dens = LocalDensity.current.density
    var pos by remember(p.geminiPos) { mutableStateOf(parseGeminiPos(p.geminiPos)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (!open) {
            // Mặc định ở góc dưới trái, trên thanh dưới; kéo tới chỗ nào cũng được và nhớ vị trí
            val maxX = maxWidth.value - 54f
            val maxY = maxHeight.value - 124f
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 78.dp)
                    .offset { IntOffset((pos.x * dens).roundToInt(), (pos.y * dens).roundToInt()) }
                    .size(46.dp).clip(CircleShape).background(t.accent).border(2.dp, t.onAccent.copy(alpha = 0.6f), CircleShape)
                    .pointerInput(Unit) { detectTapGestures(onTap = { open = true }) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { update(curP.copy(geminiPos = String.format(Locale.US, "%.0f,%.0f", pos.x, pos.y))) }
                        ) { change, amount ->
                            change.consume()
                            pos = Offset(
                                (pos.x + amount.x / dens).coerceIn(-8f, maxX),
                                (pos.y + amount.y / dens).coerceIn(-maxY, 70f)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = t.onAccent, modifier = Modifier.size(26.dp))
            }
        } else {
            GeminiPanel(obd, nav) { open = false }
        }
    }
}

@Composable
private fun GChip(text: String, sel: Boolean, onClick: () -> Unit) {
    val t = LocalTheme.current
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(if (sel) t.accent else t.panelHi).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (sel) t.onAccent else t.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeminiPanel(obd: ObdData, nav: Nav, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTheme.current
    val p = t.p
    val scope = rememberCoroutineScope()
    val tp by TpmsManager.wheels.collectAsState()
    val ns by Navigator.state.collectAsState()
    val codes by ObdManager.dtcCodes.collectAsState()
    val dtcBusy by ObdManager.dtcBusy.collectAsState()
    val tripVer by TripStats.version.collectAsState()
    var mode by remember { mutableStateOf(0) }       // 0 tìm đường, 1 hỏi về xe, 2 mã lỗi, 3 chuyến đi
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    DisposableEffect(Unit) { onDispose { recorder.cancel(); Navigator.stopSpeaking() } }

    fun speak(s: String) { if (p.geminiSpeak && s.isNotBlank()) Navigator.speakText(ctx, s) }

    // Gọi Gemini; kết quả đi qua onText, lỗi hiện bằng chữ đỏ
    fun ask(system: String, user: String, audio: ByteArray? = null, json: Boolean = false, onText: (String) -> Unit) {
        if (busy) return
        busy = true
        answer = ""
        isError = false
        Navigator.stopSpeaking()
        scope.launch {
            val r = Gemini.ask(p.geminiKey, p.geminiModel, system, user, audio, json)
            busy = false
            val txt = r.text
            if (txt == null) { answer = r.error ?: "Lỗi không rõ"; isError = true } else onText(txt)
        }
    }

    fun sendNav(audio: ByteArray?) {
        val l = LocationHolder.loc.value
        val where = if (l != null) String.format(Locale.US, "Vị trí hiện tại của xe: %.5f, %.5f.\n", l.latitude, l.longitude) else "Chưa có vị trí GPS.\n"
        val user = where + (if (audio != null) "Yêu cầu của người dùng nằm trong đoạn ghi âm." else "Yêu cầu của người dùng: " + input)
        ask(GEMINI_SYS_NAV, user, audio, true) { txt ->
            val j = try { JSONObject(txt.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) } catch (e: Exception) { null }
            val q = j?.optString("query").orEmpty().trim()
            val say = j?.optString("say").orEmpty().trim()
            answer = say.ifBlank { if (q.isNotBlank()) "Đang tìm: " + q else "Tôi chưa hiểu bạn muốn đi đâu." }
            if (q.isNotBlank()) {
                Navigator.clearSearch()
                Navigator.search(q, LocationHolder.loc.value)
            }
            speak(answer)
        }
    }

    fun sendCar(audio: ByteArray?) {
        val user = "Số liệu xe hiện tại:\n" + geminiContext(obd, tp, p) + "\n\n" +
            (if (audio != null) "Câu hỏi của người lái nằm trong đoạn ghi âm." else "Câu hỏi của người lái: " + input)
        ask(GEMINI_SYS_CAR, user, audio) { txt -> answer = geminiClean(txt); speak(answer) }
    }

    fun explain(list: List<String>) {
        if (list.isEmpty()) return
        val user = "Mã lỗi: " + list.joinToString(", ") + "\n\nSố liệu xe hiện tại:\n" + geminiContext(obd, tp, p)
        ask(GEMINI_SYS_DTC, user) { txt -> answer = geminiClean(txt); speak(answer) }
    }

    fun sendTrip() {
        if (!TripStats.hasData()) return
        ask(GEMINI_SYS_TRIP, "Xe: " + Vehicles.byId(p.vehicle).title + "\nSố liệu chuyến đi:\n" + TripStats.summary()) { txt -> answer = geminiClean(txt); speak(answer) }
    }

    // Khi đọc xong mã lỗi từ xe thì tự nhờ Gemini giải thích
    LaunchedEffect(codes) {
        val c = codes
        if (mode == 2 && c != null && c.isNotEmpty() && !busy) explain(c)
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { answer = "Cần cấp quyền micro (Ghi âm) cho ứng dụng để nói với Gemini."; isError = true }
    }
    fun finishVoice(b: ByteArray?) {
        recording = false
        if (b == null || b.size < 2500) { answer = "Ghi âm quá ngắn hoặc micro không hoạt động. Thử nói lại hoặc gõ chữ."; isError = true; return }
        if (mode == 0) sendNav(b) else sendCar(b)
    }
    fun toggleMic() {
        if (recording) { finishVoice(recorder.stop()); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        Navigator.stopSpeaking()
        if (recorder.start()) {
            recording = true
            answer = ""
            isError = false
            scope.launch { delay(12000); if (recording) finishVoice(recorder.stop()) }   // tự dừng sau 12 giây
        } else { answer = "Không bật được micro."; isError = true }
    }

    fun send() {
        if (input.isBlank()) return
        if (mode == 0) sendNav(null) else if (mode == 1) sendCar(null)
        else {
            val list = Regex("[PCBUpcbu][0-9A-Fa-f]{4}").findAll(input).map { it.value.uppercase() }.toList()
            if (list.isEmpty()) { answer = "Hãy nhập mã lỗi dạng P0299 hoặc đọc mã từ xe."; isError = true } else explain(list)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f).clip(RoundedCornerShape(14.dp)).background(t.panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = t.accent, modifier = Modifier.size(24.dp))
                Text("Gemini", color = t.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                GChip("Tìm đường", mode == 0) { mode = 0; answer = ""; isError = false }
                GChip("Hỏi về xe", mode == 1) { mode = 1; answer = ""; isError = false }
                GChip("Mã lỗi", mode == 2) { mode = 2; answer = ""; isError = false }
                GChip("Chuyến đi", mode == 3) { mode = 3; answer = ""; isError = false }
                GChip("Đóng", false) { onClose() }
            }

            if (mode == 0 || mode == 1 || mode == 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(t.panelHi).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                when (mode) {
                                    0 -> "Nơi muốn tới, ví dụ: đường tới sân bay Cát Bi"
                                    1 -> "Hỏi về xe, ví dụ: nhiệt độ nước như vậy có sao không?"
                                    else -> "Hoặc gõ mã lỗi, ví dụ: P0299 P0420"
                                },
                                color = t.muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = input, onValueChange = { input = it }, singleLine = true,
                            textStyle = TextStyle(color = t.ink, fontSize = 19.sp), cursorBrush = SolidColor(t.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (mode != 2) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(if (recording) Color(0xFFE8383D) else t.panelHi).clickable { toggleMic() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, null, tint = if (recording) Color.White else t.ink, modifier = Modifier.size(28.dp))
                        }
                    }
                    GChip("Gửi", true) { send() }
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recording) Text("Đang nghe… bấm nút micro lần nữa khi nói xong.", color = t.accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (busy) Text("Đang hỏi Gemini…", color = t.muted, fontSize = 19.sp)
                if (answer.isNotBlank()) Text(answer, color = if (isError) Color(0xFFFF7A7A) else t.ink, fontSize = 19.sp)

                when (mode) {
                    0 -> {
                        if (ns.searching) Text("Đang tìm địa điểm…", color = t.muted, fontSize = 18.sp)
                        if (!ns.searching && ns.results.isEmpty() && ns.message != null) Text(ns.message ?: "", color = t.muted, fontSize = 17.sp)
                        for (pl in ns.results.take(6)) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.panelHi).clickable {
                                    Navigator.start(ctx, LocationHolder.loc.value, pl)
                                    val mi = nav.order.indexOf("map")
                                    if (mi >= 0) nav.go(mi)
                                    onClose()
                                }.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(pl.name, color = t.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pl.sub, color = t.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (answer.isBlank() && !busy && ns.results.isEmpty()) {
                            Text("Bấm micro và nói nơi muốn tới, hoặc gõ chữ. Chọn một kết quả để bắt đầu dẫn đường.", color = t.muted, fontSize = 17.sp)
                        }
                    }
                    1 -> {
                        if (answer.isBlank() && !busy) {
                            Text("Hỏi nhanh:", color = t.muted, fontSize = 16.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (q in listOf("Xe đang ổn không?", "Nhiệt độ nước có bình thường không?", "Áp suất lốp thế nào?", "Làm sao để đỡ tốn nhiên liệu?")) {
                                    GChip(q, false) { input = q; sendCar(null) }
                                }
                            }
                        }
                    }
                    2 -> {
                        val canRead = obd.status == ObdManager.OK && p.obdSource != 1
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GChip(if (dtcBusy) "Đang đọc…" else "Đọc mã lỗi từ xe", true) { if (canRead && !dtcBusy) { answer = ""; isError = false; ObdManager.requestDtc() } }
                            val c = codes
                            if (c != null) Text(if (c.isEmpty()) "Xe không có mã lỗi lưu." else "Mã lỗi: " + c.joinToString(", "), color = t.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!canRead) Text("Cần nối OBD trực tiếp với adapter (không qua Torque) để đọc mã lỗi từ xe. Bạn vẫn có thể gõ mã lỗi ở ô phía trên.", color = t.muted, fontSize = 16.sp)
                        else if (!dtcBusy && codes == null && answer.isBlank()) Text("Bấm Đọc mã lỗi từ xe: Gemini sẽ giải thích ý nghĩa, nguyên nhân và mức độ nghiêm trọng.", color = t.muted, fontSize = 16.sp)
                        Text("Chỉ đọc mã đang lưu. Việc này không xoá mã lỗi và không tắt đèn báo.", color = t.muted, fontSize = 15.sp)
                    }
                    else -> {
                        Text(if (tripVer >= 0) TripStats.summary() else "", color = t.ink, fontSize = 18.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GChip("Nhận xét từ Gemini", true) { sendTrip() }
                            GChip("Bắt đầu chuyến mới", false) { TripStats.reset(ctx); answer = "" }
                        }
                    }
                }
            }
        }
    }
}
