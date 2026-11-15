package vn.navara.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import java.util.Locale
import kotlin.math.abs

// Thông số kỹ thuật của file nhạc đang phát
data class AudioSpec(
    val format: String = "",
    val bitrateKbps: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitDepth: Int = 0,
    val sizeBytes: Long = 0L,
)

// Ứng dụng nhạc (Foobar2000...) không báo bitrate / tần số lấy mẫu qua media session, nên launcher tự tìm đúng file
// trong bộ nhớ theo tên bài + thời lượng rồi đọc thông số trực tiếp từ file. Cần quyền đọc file nhạc.
object AudioSpecReader {
    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context): Boolean = ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    fun read(ctx: Context, title: String, artist: String, durMs: Long): AudioSpec? {
        if (title.isEmpty() || !hasPermission(ctx)) return null
        try {
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME
            )
            var bestId = -1L
            var bestName = ""
            var bestSize = 0L
            var bestScore = Long.MAX_VALUE
            fun search(sel: String, arg: String) {
                ctx.contentResolver.query(base, proj, sel, arrayOf(arg), null)?.use { c ->
                    while (c.moveToNext()) {
                        val diff = if (durMs > 0) abs(c.getLong(2) - durMs) else 0L
                        if (diff > 3000L) continue
                        val a = c.getString(1) ?: ""
                        val first = artist.substringBefore(",").trim()
                        val mismatch = artist.isNotEmpty() && first.isNotEmpty() && !a.contains(first, ignoreCase = true)
                        val score = diff * 2 + (if (mismatch) 1L else 0L)
                        if (score < bestScore) {
                            bestScore = score
                            bestId = c.getLong(0)
                            bestSize = c.getLong(3)
                            bestName = c.getString(4) ?: ""
                        }
                    }
                }
            }
            search(MediaStore.Audio.Media.TITLE + " = ?", title)
            if (bestId < 0) search(MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?", "$title.%")
            if (bestId < 0) return null

            val uri = ContentUris.withAppendedId(base, bestId)
            var sr = 0
            var ch = 0
            var br = 0
            var bits = 0
            var mime = ""
            val ex = MediaExtractor()
            try {
                ex.setDataSource(ctx, uri, null)
                if (ex.trackCount > 0) {
                    val f = ex.getTrackFormat(0)
                    mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_BIT_RATE)) br = f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                }
            } catch (e: Exception) {
            } finally {
                ex.release()
            }
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                if (br == 0) br = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000
                if (Build.VERSION.SDK_INT >= 31) {
                    if (sr == 0) sr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                    bits = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
                }
            } catch (e: Exception) {
            } finally {
                r.release()
            }
            if (br == 0 && durMs > 0 && bestSize > 0) br = (bestSize * 8 / durMs).toInt() // bit / ms = kbit/s
            val ext = bestName.substringAfterLast('.', "").uppercase(Locale.ROOT)
            val fmt = ext.ifEmpty {
                when (mime) {
                    "audio/flac" -> "FLAC"
                    "audio/mpeg" -> "MP3"
                    "audio/mp4a-latm" -> "AAC"
                    "audio/vorbis" -> "OGG"
                    "audio/opus" -> "OPUS"
                    "audio/raw" -> "WAV"
                    else -> ""
                }
            }
            return AudioSpec(fmt, br, sr, ch, bits, bestSize)
        } catch (e: Exception) {
            return null
        }
    }
}

fun khz(sr: Int): String =
    if (sr % 1000 == 0) "${sr / 1000} kHz" else String.format(Locale.US, "%.1f kHz", sr / 1000.0).replace('.', ',')
