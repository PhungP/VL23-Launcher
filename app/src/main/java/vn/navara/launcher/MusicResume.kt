package vn.navara.launcher

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Phát lại nhạc sau khi khởi động xe: launcher nhớ ứng dụng nhạc đã dùng lần trước (ví dụ Foobar2000).
// Khi bấm Play mà chưa có trình phát nào chạy, launcher đánh thức ứng dụng đó rồi bấm phát; danh sách phát và vị trí bài
// do chính ứng dụng nhạc lưu và khôi phục.
object MusicResume {
    private const val PREF = "music_resume"
    @Volatile private var cached: String? = null

    fun remember(ctx: Context, pkg: String) {
        if (pkg == cached) return
        cached = pkg
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("pkg", pkg).apply()
    }

    private fun lastPkg(ctx: Context): String? =
        cached ?: ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("pkg", null).also { cached = it }

    // Gửi phím Play tới đúng ứng dụng; nếu ứng dụng có bộ nhận phím media thì nó tự dậy và phát
    private fun sendPlayKey(ctx: Context, pkg: String) {
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            try {
                ctx.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
                )
            } catch (e: Exception) { }
        }
    }

    private suspend fun waitSession(ctx: Context, ms: Long): MediaController? {
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) {
            val c = withContext(Dispatchers.IO) { readMedia(ctx).controller }
            if (c != null) return c
            delay(400)
        }
        return null
    }

    suspend fun start(ctx: Context) {
        val app = ctx.applicationContext
        val pkg = lastPkg(app)
        if (pkg == null) {
            // chưa từng phát nhạc: mở ứng dụng nhạc mặc định để người dùng chọn
            try {
                app.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { }
            return
        }
        sendPlayKey(app, pkg)
        var c = waitSession(app, 3500)
        if (c != null) {
            if (c.playbackState?.state != PlaybackState.STATE_PLAYING) c.transportControls.play()
            return
        }
        // Ứng dụng chưa chạy và không tự dậy: mở nó lên, đợi có phiên phát rồi bấm Play, sau đó quay về launcher
        try {
            val li = app.packageManager.getLaunchIntentForPackage(pkg)
            if (li != null) app.startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
        c = waitSession(app, 12000)
        if (c != null) {
            c.transportControls.play()
            delay(800)
            try {
                app.startActivity(
                    Intent(app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (e: Exception) { }
        }
    }
}
