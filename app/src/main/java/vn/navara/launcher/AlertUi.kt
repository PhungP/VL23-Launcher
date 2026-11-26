package vn.navara.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun alertColor(l: AlertLevel): Color = when (l) {
    AlertLevel.CRITICAL -> Color(0xFFE8383D)
    AlertLevel.WARNING -> Color(0xFFFF9F1C)
    AlertLevel.ADVISORY -> Color(0xFFE8C547)
    AlertLevel.INFO -> Color(0xFF4DA3FF)
    AlertLevel.CONNECTIVITY -> Color(0xFF8E9AAF)
}

/** Thanh cảnh báo nổi ở đầu màn hình: chỉ hiện cảnh báo ưu tiên cao nhất chưa xác nhận; chạm để xác nhận. */
@Composable
fun AlertBanner(modifier: Modifier = Modifier) {
    val active by VehicleMonitor.active.collectAsState()
    val mode by VehicleMonitor.mode.collectAsState()
    // Khi đang lái chỉ giữ các cảnh báo quan trọng, bỏ Lưu ý để tránh phân tâm
    val top = active.firstOrNull { !it.acknowledged && (mode == DriveMode.PARKED || it.level != AlertLevel.ADVISORY) } ?: return
    val c = alertColor(top.level)
    val more = active.count { !it.acknowledged } - 1
    Row(
        modifier.padding(top = 8.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6101418)).border(2.dp, c, RoundedCornerShape(12.dp))
            .clickable { VehicleMonitor.acknowledge(top.key) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(14.dp).background(c, RoundedCornerShape(7.dp)))
        Column(Modifier.weight(1f, false)) {
            Text(top.kind.title, color = c, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (mode == DriveMode.PARKED) Text(top.message, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (more > 0) Text("+$more", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Chạm để ẩn", color = Color(0xFF9AA4B2), fontSize = 12.sp)
    }
}

fun alertTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

/** Khi đang lái, mở Cài đặt cần xác nhận (dành cho hành khách); không chặn cứng. */
@Composable
fun DrivingSettingsPrompt(onOpen: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB0000000)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B2028)).clickable(enabled = false) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Xe đang chạy", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Chỉ mở Cài đặt khi an toàn hoặc do hành khách thao tác.", color = Color(0xFFB8C0CC), fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B3340)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                    Text("Quay lại", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF8A1F)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                    Text("Mở Cài đặt", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
