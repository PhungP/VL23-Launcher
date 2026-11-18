package vn.navara.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ===== Giao diện dẫn đường: màu truyền từ trang bản đồ (sáng/tối theo bản đồ) =====

// Mũi tên rẽ vẽ bằng nét vector (không phụ thuộc phông chữ của đầu máy)
@Composable
fun TurnIcon(kind: Int, angle: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val w = s * 0.13f
        if (kind == 1) {   // điểm đến
            drawCircle(color, s * 0.30f, c, style = Stroke(w))
            drawCircle(color, s * 0.12f, c)
            return@Canvas
        }
        if (kind == 3) {   // quay đầu: hình chữ U có mũi tên chỉ xuống
            val p = Path().apply {
                moveTo(c.x + s * 0.18f, c.y + s * 0.40f)
                lineTo(c.x + s * 0.18f, c.y - s * 0.18f)
                lineTo(c.x - s * 0.18f, c.y - s * 0.18f)
                lineTo(c.x - s * 0.18f, c.y + s * 0.10f)
            }
            drawPath(p, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val tip = Offset(c.x - s * 0.18f, c.y + s * 0.32f)
            val h = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - s * 0.18f, tip.y - s * 0.17f)
                lineTo(tip.x + s * 0.18f, tip.y - s * 0.17f)
                close()
            }
            drawPath(h, color, style = Fill)
            return@Canvas
        }
        val rad = Math.toRadians(angle.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val m = Offset(c.x, c.y + s * 0.12f)
        val b = Offset(c.x, c.y + s * 0.45f)
        val e = Offset(m.x + dx * s * 0.32f, m.y + dy * s * 0.32f)
        drawLine(color, b, m, w, StrokeCap.Round)
        drawLine(color, m, e, w, StrokeCap.Round)
        val nx = -dy
        val ny = dx
        val tip = Offset(e.x + dx * s * 0.13f, e.y + dy * s * 0.13f)
        val bx = e.x - dx * s * 0.03f
        val by = e.y - dy * s * 0.03f
        val h = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(bx + nx * s * 0.18f, by + ny * s * 0.18f)
            lineTo(bx - nx * s * 0.18f, by - ny * s * 0.18f)
            close()
        }
        drawPath(h, color, style = Fill)
        if (kind == 2) drawCircle(color, s * 0.10f, m, style = Stroke(w * 0.6f))   // vòng xuyến
    }
}

// Khung chỉ dẫn phía trên: biểu tượng rẽ, khoảng cách tới chỗ rẽ, nội dung chỉ dẫn
@Composable
fun NavBanner(ns: NavState, bg: Color, ink: Color, accent: Color, onAccent: Color, modifier: Modifier = Modifier) {
    val r = ns.route ?: return
    val step = r.steps.getOrNull(ns.nextIdx) ?: return
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.widthIn(max = 340.dp).clip(shape).background(bg).border(2.dp, accent, shape).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(accent), contentAlignment = Alignment.Center) {
            TurnIcon(if (ns.arrived) 1 else step.kind, step.angle, onAccent, Modifier.size(48.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                if (ns.arrived) "Đã đến nơi" else fmtDist(ns.toNextM),
                color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    ns.arrived -> r.dest.name
                    ns.offRoute -> "Đang tính lại đường…"
                    else -> step.text
                },
                color = ink, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Thanh dưới: thời gian, quãng đường còn lại, giờ đến, nút Dừng
@Composable
fun NavSummary(ns: NavState, bg: Color, ink: Color, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val eta = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (ns.remainS * 1000).toLong()))
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(fmtDur(ns.remainS) + "  •  " + fmtDist(ns.remainM), color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Đến nơi lúc $eta", color = ink, fontSize = 14.sp)
        }
        Box(
            Modifier.height(54.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8383D))
                .clickable(onClick = onStop).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) { Text("Dừng", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

// Trạng thái ngắn (đang tìm đường, báo lỗi): chạm để tắt thông báo lỗi
@Composable
fun NavStatusChip(ns: NavState, bg: Color, ink: Color, modifier: Modifier = Modifier) {
    val txt = when {
        ns.loading && ns.route == null -> "Đang tìm đường…"
        ns.message != null && !ns.searching -> ns.message
        else -> null
    } ?: return
    Text(
        txt, color = ink, fontSize = 16.sp,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .clickable { Navigator.dismissMessage() }.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// Màn hình chọn điểm đến: ô nhập, kết quả tìm, các điểm đến gần đây
@Composable
fun NavSearchOverlay(bg: Color, ink: Color, accent: Color, onAccent: Color, loc: Location?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ns by Navigator.state.collectAsState()
    var q by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val recent = remember { Navigator.recent(ctx) }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        Navigator.clearSearch()
        try { focus.requestFocus() } catch (e: Exception) { }
    }
    fun go(p: Place) {
        if (loc == null) { warn = "Chưa có vị trí GPS nên chưa thể dẫn đường"; return }
        Navigator.start(ctx, loc, p)
        onClose()
    }
    val scope = rememberCoroutineScope()
    // Xử lý nội dung dán hoặc gõ vào: liên kết / toạ độ từ Google Maps dùng thẳng, còn lại tìm như địa chỉ thường
    fun handleText(text: String) {
        warn = "Đang đọc nội dung đã dán…"
        scope.launch {
            val r = PasteParser.parse(text)
            warn = null
            when {
                r.place != null -> { q = r.place.name; Navigator.showPlace(r.place) }
                r.query != null -> { q = r.query; Navigator.search(r.query, loc) }
                else -> warn = r.error ?: "Không đọc được nội dung đã dán"
            }
        }
    }
    fun doSearch() {
        warn = null
        if (PasteParser.looksLikePaste(q)) handleText(q) else Navigator.search(q, loc)
    }
    // Nút Dán: lấy nội dung đã sao chép (địa chỉ, tên địa điểm, liên kết Chia sẻ của Google Maps) rồi tìm luôn
    fun doPaste() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val txt = try { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty() } catch (e: Exception) { "" }
        if (txt.isEmpty()) { warn = "Chưa có gì được sao chép. Trong Google Maps hãy sao chép địa chỉ hoặc chọn Chia sẻ > Sao chép liên kết."; return }
        handleText(txt)
    }
    fun doCopy(p: Place) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("address", if (p.sub.isNotBlank() && !p.sub.startsWith(p.name)) p.name + ", " + p.sub else p.name))
        warn = "Đã sao chép địa chỉ"
    }

    val shape = RoundedCornerShape(12.dp)
    val shown = if (ns.results.isNotEmpty()) ns.results else if (q.isEmpty()) recent else emptyList()
    Column(
        Modifier.fillMaxSize().background(bg)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(56.dp).clip(shape).border(2.dp, ink, shape).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 22.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("Nhập địa chỉ hoặc tên địa điểm", color = ink.copy(alpha = 0.5f), fontSize = 22.sp)
                        inner()
                    }
                )
            }
            Box(
                Modifier.height(56.dp).clip(shape).border(2.dp, ink, shape).clickable { doPaste() }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("Dán", color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.height(56.dp).clip(shape).background(accent).clickable { doSearch() }.padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) { Text("Tìm", color = onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Box(Modifier.size(56.dp).clip(shape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = ink, modifier = Modifier.size(32.dp))
            }
        }
        val info = warn ?: if (ns.searching) "Đang tìm…" else ns.message
        if (info != null) Text(info, color = ink, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
        if (ns.results.isEmpty() && q.isEmpty() && recent.isNotEmpty()) {
            Text("Điểm đến gần đây", color = ink.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items(shown) { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { go(p) }.padding(vertical = 9.dp, horizontal = 4.dp)) {
                        Text(p.name, color = ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.sub, color = ink.copy(alpha = 0.65f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Sao chép địa chỉ để dán sang Google Maps hoặc ứng dụng khác
                    Box(Modifier.size(48.dp).clip(shape).clickable { doCopy(p) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ContentCopy, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.15f)))
            }
        }
    }
}
