package vn.navara.launcher

// ===== Phân loại nguồn và độ tin cậy của dữ liệu xe =====
// Mỗi thông số đi kèm trạng thái để giao diện, cảnh báo và chế độ lái không bao giờ coi dữ liệu ước tính / cũ như dữ liệu đọc trực tiếp.

enum class DataStatus(val label: String) {
    REAL_TIME("Trực tiếp"),     // đọc trực tiếp từ ECU / cảm biến, còn mới
    GPS("GPS"),                 // lấy từ GPS, còn mới
    ESTIMATED("Ước tính"),      // suy ra từ dữ liệu khác bằng mô hình gần đúng (ví dụ cấp số từ vòng tua / tốc độ)
    DERIVED("Tính toán"),       // tính chính xác từ thông số khác
    STALE("Quá hạn"),           // từng có giá trị nhưng đã quá hạn
    UNAVAILABLE("Không có"),    // chưa có dữ liệu
    ERROR("Lỗi");               // đọc thất bại

    /** Dùng để hiển thị (có thể kèm nhãn nguồn). */
    val usable: Boolean get() = this == REAL_TIME || this == GPS || this == ESTIMATED || this == DERIVED
    /** Đủ tin cậy để kích hoạt cảnh báo hoặc tự đổi chế độ: chỉ dữ liệu đo thật. */
    val trusted: Boolean get() = this == REAL_TIME || this == GPS
}

/** Cách mà một giá trị còn mới được tạo ra. */
enum class DataKind(val fresh: DataStatus) { MEASURED(DataStatus.REAL_TIME), GPS(DataStatus.GPS), ESTIMATED(DataStatus.ESTIMATED), DERIVED(DataStatus.DERIVED) }

class Reading(
    val value: Float?,
    val unit: String,
    val status: DataStatus,
    val atMs: Long,          // thời điểm nhận giá trị (0 nếu chưa từng có)
    val source: String,      // "OBD", "GPS", "TPMS", ...
) {
    val usable: Boolean get() = value != null && status.usable
    val trusted: Boolean get() = value != null && status.trusted

    companion object {
        fun unavailable(unit: String, source: String) = Reading(null, unit, DataStatus.UNAVAILABLE, 0L, source)
        fun error(unit: String, source: String, at: Long) = Reading(null, unit, DataStatus.ERROR, at, source)

        /**
         * Phân loại một giá trị. value == null -> UNAVAILABLE; quá hạn ttlMs -> STALE (vẫn giữ giá trị để tham khảo, không dùng được).
         */
        fun classify(value: Float?, unit: String, source: String, kind: DataKind, atMs: Long, nowMs: Long, ttlMs: Long): Reading {
            if (value == null || atMs <= 0L) return unavailable(unit, source)
            if (nowMs - atMs > ttlMs) return Reading(value, unit, DataStatus.STALE, atMs, source)
            return Reading(value, unit, kind.fresh, atMs, source)
        }
    }
}

/** Ảnh chụp dữ liệu xe tại một thời điểm, đã phân loại. */
class VehicleSnapshot(
    val nowMs: Long,
    val speed: Reading,
    val rpm: Reading,
    val gear: Reading,
    val coolant: Reading,
    val volt: Reading,
    val boost: Reading,
    val tpmsBar: Map<String, Reading>,
    val obdLive: Boolean,       // adapter đang trả dữ liệu mới
    val obdExpected: Boolean,   // người dùng có dùng OBD (đã chọn nguồn) nên mất kết nối là bất thường
    val gpsOn: Boolean,
    val gpsFix: Boolean,
)

/** Dữ liệu đầu vào thô (không phụ thuộc Android) để dựng VehicleSnapshot, thuận tiện kiểm thử. */
class RawInputs(
    val obdConnected: Boolean,         // trạng thái OBD báo đã nối và đọc được
    val obdLastRxMs: Long,             // lần cuối nhận dữ liệu từ ECU
    val speedKmh: Int, val rpm: Int,
    val coolant: Int?, val volt: Float?, val boost: Float?,
    val gear: Int?, val gearEstimated: Boolean,
    val gpsOn: Boolean, val gpsFix: Boolean, val gpsSpeedKmh: Int, val gpsFixAtMs: Long,
    val tpms: Map<String, Pair<Float, Long>>,   // bánh xe -> (bar, thời điểm)
    val obdExpected: Boolean,
)

object Snapshots {
    const val OBD_TTL_MS = 3_000L          // vòng hỏi nhanh vài trăm ms; quá 3 giây là cũ
    const val SLOW_TTL_MS = 15_000L        // thông số hỏi thưa (nhiệt độ nước, điện áp...)
    const val GPS_TTL_MS = 5_000L
    const val TPMS_TTL_MS = 20 * 60_000L   // cảm biến lốp báo thưa khi đỗ

    fun build(r: RawInputs, now: Long): VehicleSnapshot {
        val live = r.obdConnected && r.obdLastRxMs > 0L && now - r.obdLastRxMs <= OBD_TTL_MS
        fun obd(v: Float?, unit: String, ttl: Long): Reading =
            if (!r.obdConnected) Reading.unavailable(unit, "OBD")
            else Reading.classify(v, unit, "OBD", DataKind.MEASURED, r.obdLastRxMs, now, ttl)

        // Tốc độ: ưu tiên OBD khi còn sống; không thì GPS có định vị mới; không có thì không có dữ liệu
        val gpsOk = r.gpsOn && r.gpsFix && r.gpsFixAtMs > 0L && now - r.gpsFixAtMs <= GPS_TTL_MS
        val speed = when {
            live -> Reading(r.speedKmh.toFloat(), "km/h", DataStatus.REAL_TIME, r.obdLastRxMs, "OBD")
            gpsOk -> Reading(r.gpsSpeedKmh.toFloat(), "km/h", DataStatus.GPS, r.gpsFixAtMs, "GPS")
            r.obdConnected && r.obdLastRxMs > 0L -> Reading(r.speedKmh.toFloat(), "km/h", DataStatus.STALE, r.obdLastRxMs, "OBD")
            else -> Reading.unavailable("km/h", "OBD/GPS")
        }
        val gear = when {
            !r.obdConnected || r.gear == null -> Reading.unavailable("", "OBD")
            else -> Reading.classify(r.gear.toFloat(), "", "OBD", if (r.gearEstimated) DataKind.ESTIMATED else DataKind.MEASURED, r.obdLastRxMs, now, OBD_TTL_MS)
        }
        val tp = r.tpms.mapValues { (_, v) -> Reading.classify(v.first, "bar", "TPMS", DataKind.MEASURED, v.second, now, TPMS_TTL_MS) }
        return VehicleSnapshot(
            now, speed, obd(r.rpm.toFloat(), "rpm", OBD_TTL_MS), gear,
            obd(r.coolant?.toFloat(), "°C", SLOW_TTL_MS), obd(r.volt, "V", SLOW_TTL_MS), obd(r.boost, "bar", SLOW_TTL_MS),
            tp, live, r.obdExpected, r.gpsOn, gpsOk,
        )
    }
}
