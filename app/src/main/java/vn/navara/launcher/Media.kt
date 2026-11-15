package vn.navara.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

// Cần được cấp quyền "Truy cập thông báo" để đọc bài hát đang phát
class MediaListener : NotificationListenerService()

// Một bài trong danh sách phát (chỉ có nếu ứng dụng nhạc chia sẻ hàng đợi qua media session)
data class QItem(val id: Long, val title: String, val sub: String)

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: ImageBitmap? = null,
    val playing: Boolean = false,
    val pos: Long = 0,
    val dur: Long = 0,
    val controller: MediaController? = null,
    val hasAccess: Boolean = false,
    val queue: List<QItem> = emptyList(),
    val queueId: Long = -1L,
)

// Tăng giá trị này để buộc giao diện đọc lại bài đang phát ngay (khi quay lại launcher, khi danh sách phiên phát thay đổi)
object MediaRefresh {
    var tick by mutableStateOf(0)
}

private var sessListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

// Đăng ký nhận thông báo khi ứng dụng nhạc tạo / đóng phiên phát, để không bị kẹt ở phiên cũ
fun watchMediaSessions(ctx: Context) {
    if (sessListener != null || !hasListenerAccess(ctx)) return
    try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val l = MediaSessionManager.OnActiveSessionsChangedListener { MediaRefresh.tick++ }
        msm.addOnActiveSessionsChangedListener(l, ComponentName(ctx, MediaListener::class.java))
        sessListener = l
    } catch (e: Exception) { }
}

fun hasListenerAccess(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

fun readMedia(ctx: Context, withQueue: Boolean = false): MediaInfo {
    if (!hasListenerAccess(ctx)) return MediaInfo()
    return try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        // Có thể tồn tại nhiều phiên phát (ví dụ phiên cũ của ứng dụng nhạc chưa đóng): ưu tiên phiên đang phát
        // và cập nhật gần nhất, để không bị kẹt ở bài cũ
        val playingList = list.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val c = (if (playingList.isNotEmpty()) playingList else list)
            .maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            ?: return MediaInfo(hasAccess = true)
        val m = c.metadata
        val ps = c.playbackState
        val bmp = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        val pos = if (ps == null) 0L else if (playing)
            ps.position + ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        else ps.position
        MediaInfo(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            art = bmp?.asImageBitmap(),
            playing = playing,
            pos = pos,
            dur = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            controller = c,
            hasAccess = true,
            queue = if (withQueue) (c.queue ?: emptyList()).take(500).map {
                QItem(it.queueId, it.description.title?.toString() ?: "", it.description.subtitle?.toString() ?: "")
            } else emptyList(),
            queueId = ps?.activeQueueItemId ?: -1L,
        )
    } catch (e: Exception) {
        MediaInfo(hasAccess = true)
    }
}
