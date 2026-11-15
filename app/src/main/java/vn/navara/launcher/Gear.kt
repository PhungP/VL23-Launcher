package vn.navara.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sơ đồ số 1 đến 7 kiểu đồng hồ xe: đường xanh dương phía trên, các số màu trắng từ trái sang phải,
// ô vuông viền trắng bao ngoài số đang cài và trượt (nảy nhẹ) giữa các số khi sang số. Chưa biết số thì ô vuông mờ đi.
@Composable
fun GearStrip(gear: Int?, modifier: Modifier = Modifier, height: Dp = 58.dp, rail: Boolean = true) {
    val t = LocalTheme.current
    val target = if (gear != null) (gear - 1).coerceIn(0, 6).toFloat() else 0f
    val pos by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = 380f), label = "gearPos")
    val vis by animateFloatAsState(if (gear != null) 1f else 0f, tween(200), label = "gearVis")
    Column(modifier.height(height)) {
        if (rail) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(t.blueDeep, t.blueLight, t.blueDeep))))
            Spacer(Modifier.height(6.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cw = maxWidth / 7
            val sq = minOf(maxHeight, cw - 4.dp)
            Row(Modifier.fillMaxSize()) {
                for (i in 1..7) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(i.toString(), color = t.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Box(
                Modifier.offset(x = cw * pos + (cw - sq) / 2).size(sq).graphicsLayer { alpha = vis }
                    .border(3.dp, t.ink, RoundedCornerShape(4.dp))
            )
        }
    }
}

// Thanh số của trang thông số OBD: cùng kiểu ô vuông trượt
@Composable
fun GearBar(gear: Int?, modifier: Modifier = Modifier) {
    GearStrip(gear, modifier, 56.dp)
}

// Kiểu đồng hồ điện tử: 7 ô vuông viền mảnh, ô số đang cài đảo màu (nền đen chữ trắng hoặc nền trắng chữ đen)
@Composable
fun GearLed(gear: Int?, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val t = LocalTheme.current
    val shape = RoundedCornerShape(4.dp)
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..7) {
            val f by animateFloatAsState(if (gear == i) 1f else 0f, tween(120), label = "gearLed")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .background(lerp(Color.Transparent, t.ink, f))
                    .border(1.dp, t.ink.copy(alpha = 0.35f), shape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    i.toString(),
                    color = lerp(t.muted, t.bg, f),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
