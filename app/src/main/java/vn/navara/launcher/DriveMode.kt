package vn.navara.launcher

// ===== Chế độ lái / đỗ / ban đêm =====

enum class DriveMode { PARKED, DRIVING }
enum class ModeOverride { AUTO, PARKED, DRIVING }

data class ModeConfig(
    val auto: Boolean = false,           // mặc định tắt để không đổi hành vi hiện có; bật trong Cài đặt
    val driveKmh: Int = 10,              // nhanh hơn ngưỡng này đủ lâu -> chế độ lái
    val parkKmh: Int = 3,                // chậm hơn ngưỡng này đủ lâu -> chế độ đỗ
    val driveHoldMs: Long = 3_000L,
    val parkHoldMs: Long = 15_000L,
)

/**
 * Tự đổi chế độ theo tốc độ với trễ (hysteresis) hai ngưỡng và thời gian giữ.
 * Dữ liệu tốc độ không tin cậy (cũ, ước tính, lỗi, không có) KHÔNG bao giờ làm đổi chế độ: giữ nguyên chế độ hiện tại.
 */
class DriveModeController(var config: ModeConfig = ModeConfig()) {
    var auto: DriveMode = DriveMode.PARKED; private set
    var override: ModeOverride = ModeOverride.AUTO

    private var fastSince = -1L
    private var slowSince = -1L

    /** Chế độ đang áp dụng (ghi đè thủ công thắng chế độ tự động). */
    val mode: DriveMode get() = when (override) {
        ModeOverride.PARKED -> DriveMode.PARKED
        ModeOverride.DRIVING -> DriveMode.DRIVING
        ModeOverride.AUTO -> if (config.auto) auto else DriveMode.PARKED
    }

    fun update(nowMs: Long, speed: Reading): DriveMode {
        val v = speed.value
        if (!speed.trusted || v == null) { fastSince = -1L; slowSince = -1L; return mode }
        if (v >= config.driveKmh) {
            slowSince = -1L
            if (fastSince < 0L) fastSince = nowMs
            if (auto != DriveMode.DRIVING && nowMs - fastSince >= config.driveHoldMs) auto = DriveMode.DRIVING
        } else if (v <= config.parkKmh) {
            fastSince = -1L
            if (slowSince < 0L) slowSince = nowMs
            if (auto != DriveMode.PARKED && nowMs - slowSince >= config.parkHoldMs) auto = DriveMode.PARKED
        } else {
            // vùng giữa hai ngưỡng: không đổi, reset bộ đếm
            fastSince = -1L; slowSince = -1L
        }
        return mode
    }
}

object NightPolicy {
    /** Ban đêm từ startHour đến endHour (có thể qua nửa đêm, ví dụ 19 -> 6). */
    fun isNight(hour: Int, startHour: Int = 19, endHour: Int = 6): Boolean =
        if (startHour == endHour) false
        else if (startHour < endHour) hour in startHour until endHour
        else hour >= startHour || hour < endHour
}
