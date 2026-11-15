package vn.navara.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Trang Gọi điện kiểu đồng hồ xe: màn hình số, bàn phím 3x4 vát góc, nút gọi màu xanh lá, xoá (giữ lâu để xoá hết),
// mở Danh bạ / Nhật ký cuộc gọi của hệ thống. Cuộc gọi thật do ứng dụng điện thoại của Android xử lý.
@Composable
fun DialerScreen(onClose: () -> Unit) {
    val t = LocalTheme.current
    val ctx = LocalContext.current
    var num by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    BackHandler { onClose() }
    val hasTel = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) }
    fun open(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { msg = "Không mở được ứng dụng điện thoại" }
    }
    fun dial(call: Boolean) = open(Intent(if (call) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && num.isNotEmpty()) dial(true) else if (!granted) msg = "Chưa cấp quyền gọi điện, mở bàn phím hệ thống để gọi"
    }
    fun call() {
        if (num.isEmpty()) { msg = "Nhập số điện thoại trước"; return }
        msg = ""
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) dial(true)
        else askCall.launch(Manifest.permission.CALL_PHONE)
    }
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to ""
    )
    Box(
        Modifier.fillMaxSize()
            .background(if (t.mono == 0) Brush.verticalGradient(listOf(Color(0xFF06103A), Color(0xFF01030C))) else SolidColor(t.bg))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                Text("Gọi điện", color = t.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Icon(
                    Icons.Filled.Close, null, tint = t.ink,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { onClose() }.padding(horizontal = 18.dp, vertical = 10.dp).size(30.dp)
                )
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(t.blueDeep, t.blueLight, t.blueDeep))))
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // bên trái: màn hình số + nút
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(70.dp).panel(12.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            if (num.isEmpty()) "Nhập số điện thoại" else num,
                            color = if (num.isEmpty()) t.muted else t.ink, fontSize = if (num.length > 12) 26.sp else 34.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().height(64.dp).panel(12.dp, line = if (t.mono == 0) t.green else null)
                            .background(if (t.mono == 0) t.green.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { call() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Call, null, tint = t.green, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("GỌI", color = t.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DialBtn("Danh bạ", Modifier.weight(1f)) { open(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)) }
                        DialBtn("Nhật ký", Modifier.weight(1f)) { open(Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE)) }
                    }
                    DialBtn("Mở bàn phím hệ thống", Modifier.fillMaxWidth()) { dial(false) }
                    if (!hasTel) Text("Thiết bị này có thể không hỗ trợ gọi thoại (không có chức năng điện thoại).", color = t.muted, fontSize = 13.sp)
                    if (msg.isNotEmpty()) Text(msg, color = t.accent, fontSize = 14.sp)
                }
                // bên phải: bàn phím
                Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until 4) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until 3) {
                                val (main, sub) = keys[r * 3 + c]
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().panel(10.dp, left = c == 2, right = c == 0)
                                        .clickable { if (num.length < 24) num += main; msg = "" },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                ) {
                                    Text(main, color = t.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                                    if (sub.isNotEmpty()) Text(sub, color = t.muted, fontSize = 11.sp, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
                // cột xoá
                Box(
                    Modifier.width(64.dp).fillMaxHeight().panel(10.dp, left = true, right = false)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { if (num.isNotEmpty()) num = num.dropLast(1) }, onLongPress = { num = "" })
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Backspace, null, tint = t.ink, modifier = Modifier.size(30.dp)) }
            }
        }
    }
}

@Composable
private fun DialBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTheme.current
    Box(modifier.height(46.dp).panel(8.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(text, color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
