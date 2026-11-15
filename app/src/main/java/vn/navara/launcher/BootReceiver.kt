package vn.navara.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

// Tự mở launcher khi Tbox khởi động (Android 10+ cần cấp quyền "Hiện trên ứng dụng khác";
// nếu launcher đã là màn hình chính mặc định thì hệ thống tự mở nó khi khởi động)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!loadPrefs(ctx).autoStart) return
        val ok = Build.VERSION.SDK_INT < 29 || android.provider.Settings.canDrawOverlays(ctx)
        if (ok) {
            try {
                ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { }
        }
    }
}
