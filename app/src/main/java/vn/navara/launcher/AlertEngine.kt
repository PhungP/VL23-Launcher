package vn.navara.launcher

// ===== Trung tâm cảnh báo xe =====
// Chỉ dữ liệu đo thật (REAL_TIME / GPS) mới tạo cảnh báo; dữ liệu ước tính, cũ hoặc lỗi không bao giờ gây báo động giả.
// Một điều kiện phải đúng liên tục `debounceMs` mới bật cảnh báo, phải hết liên tục `clearMs` mới tắt,
// và sau khi tắt / được xác nhận, cùng loại cảnh báo không bật lại trong `cooldownMs` (chống spam).

enum class AlertLevel(val label: String) {
    CRITICAL("Nghiêm trọng"), WARNING("Cảnh báo"), ADVISORY("Lưu ý"), INFO("Thông tin"), CONNECTIVITY("Kết nối")
}

enum class AlertKind(val level: AlertLevel, val group: String, val title: String) {
    ENGINE_TEMP_CRIT(AlertLevel.CRITICAL, "engine", "Nhiệt độ nước quá cao"),
    ENGINE_TEMP_WARN(AlertLevel.WARNING, "engine", "Nhiệt độ nước cao"),
    LOW_VOLTAGE(AlertLevel.WARNING, "electrical", "Điện áp thấp"),
    HIGH_RPM(AlertLevel.WARNING, "engine", "Vòng tua quá cao"),
    BOOST_HIGH(AlertLevel.WARNING, "engine", "Áp suất tăng áp cao"),
    TPMS_LOW(AlertLevel.WARNING, "tpms", "Áp suất lốp thấp"),
    TPMS_HIGH(AlertLevel.ADVISORY, "tpms", "Áp suất lốp cao"),
    TPMS_MISSING(AlertLevel.ADVISORY, "tpms", "Mất tín hiệu cảm biến lốp"),
    OBD_LOST(AlertLevel.CONNECTIVITY, "connectivity", "Mất kết nối OBD"),
    GPS_LOST(AlertLevel.CONNECTIVITY, "connectivity", "Mất tín hiệu GPS"),
    DATA_STALE(AlertLevel.ADVISORY, "connectivity", "Dữ liệu xe bị cũ"),
}

val ALERT_GROUPS = listOf("engine" to "Động cơ", "electrical" to "Điện", "tpms" to "Áp suất lốp", "connectivity" to "Kết nối")

data class AlertConfig(
    val coolantWarn: Float = 105f,
    val coolantCrit: Float = 112f,
    val voltLow: Float = 11.8f,         // khi động cơ đang chạy
    val rpmMax: Float = 0f,             // 0 = theo vạch đỏ của hồ sơ xe (VehicleMonitor điền vào)
    val boostMax: Float = 2.2f,         // bar
    val tpmsLowBar: Float = 1.9f,
    val tpmsHighBar: Float = 3.4f,
    val debounceMs: Long = 3_000L,
    val clearMs: Long = 3_000L,
    val cooldownMs: Long = 60_000L,
    val connectivityDebounceMs: Long = 10_000L,
    val disabledGroups: Set<String> = emptySet(),
    val historyMax: Int = 100,
)

data class Alert(
    val kind: AlertKind,
    val subject: String,        // ví dụ bánh xe "FL"; rỗng nếu không có
    val message: String,
    val source: String,
    val raisedAtMs: Long,
    val acknowledged: Boolean = false,
) {
    val key: String get() = kind.name + subject
    val level: AlertLevel get() = kind.level
}

class AlertEngine(var config: AlertConfig = AlertConfig()) {
    private class Track(var since: Long = -1L, var clearSince: Long = -1L, var coolUntil: Long = 0L)

    private val tracks = HashMap<String, Track>()
    private val _active = LinkedHashMap<String, Alert>()
    private val _history = ArrayList<Alert>()

    /** Cảnh báo đang bật: Nghiêm trọng trước, rồi theo cấp độ, rồi mới nhất trước. */
    val active: List<Alert> get() = _active.values.sortedWith(compareBy<Alert> { it.level.ordinal }.thenByDescending { it.raisedAtMs })
    val history: List<Alert> get() = _history.toList().asReversed()
    /** Cảnh báo cần hiển thị nổi bật: ưu tiên Critical, bỏ qua cái đã xác nhận. */
    val top: Alert? get() = active.firstOrNull { !it.acknowledged }

    fun acknowledge(key: String) { _active[key]?.let { _active[key] = it.copy(acknowledged = true) } }
    fun acknowledgeAll() { for (k in _active.keys.toList()) acknowledge(k) }
    fun clearHistory() = _history.clear()

    private class Cond(val kind: AlertKind, val subject: String, val on: Boolean, val message: String, val source: String)

    /** Gọi định kỳ (khoảng 1 lần/giây) với ảnh chụp mới nhất. Trả về các cảnh báo vừa BẬT trong lần gọi này. */
    fun evaluate(s: VehicleSnapshot): List<Alert> {
        val now = s.nowMs
        val c = config
        val conds = ArrayList<Cond>()

        // Mọi điều kiện đo chỉ xét khi dữ liệu tin cậy; dữ liệu không tin cậy -> "chưa biết" (không bật, không tắt)
        fun measured(kind: AlertKind, subject: String, r: Reading, test: (Float) -> Boolean, msg: (Float) -> String) {
            if (r.trusted) conds.add(Cond(kind, subject, test(r.value!!), msg(r.value), r.source))
        }
        val engineOn = s.rpm.trusted && s.rpm.value!! > 400f
        measured(AlertKind.ENGINE_TEMP_CRIT, "", s.coolant, { it >= c.coolantCrit }) { "Nước làm mát ${it.toInt()}°C, hãy dừng xe an toàn và tắt máy" }
        measured(AlertKind.ENGINE_TEMP_WARN, "", s.coolant, { it >= c.coolantWarn && it < c.coolantCrit }) { "Nước làm mát ${it.toInt()}°C" }
        if (engineOn) measured(AlertKind.LOW_VOLTAGE, "", s.volt, { it < c.voltLow }) { "Điện áp %.1f V".format(it) }
        if (c.rpmMax > 0f) measured(AlertKind.HIGH_RPM, "", s.rpm, { it > c.rpmMax }) { "Vòng tua ${it.toInt()}" }
        measured(AlertKind.BOOST_HIGH, "", s.boost, { it > c.boostMax }) { "Tăng áp %.2f bar".format(it) }
        for ((w, r) in s.tpmsBar) {
            measured(AlertKind.TPMS_LOW, w, r, { it < c.tpmsLowBar }) { "${tpmsWheelName(w)}: %.2f bar".format(it) }
            measured(AlertKind.TPMS_HIGH, w, r, { it > c.tpmsHighBar }) { "${tpmsWheelName(w)}: %.2f bar".format(it) }
            // Cảm biến đã từng báo nhưng đã quá hạn
            conds.add(Cond(AlertKind.TPMS_MISSING, w, r.status == DataStatus.STALE, "${tpmsWheelName(w)}: không còn tín hiệu", "TPMS"))
        }
        // Kết nối: dùng debounce dài hơn
        if (s.obdExpected) conds.add(Cond(AlertKind.OBD_LOST, "", !s.obdLive, "Không nhận được dữ liệu từ OBD", "OBD"))
        if (s.gpsOn) conds.add(Cond(AlertKind.GPS_LOST, "", !s.gpsFix, "Chưa có định vị GPS", "GPS"))
        if (s.obdLive) {
            val stale = listOf(s.coolant, s.volt).any { it.status == DataStatus.STALE }
            conds.add(Cond(AlertKind.DATA_STALE, "", stale, "Có thông số xe không còn cập nhật", "OBD"))
        }

        val raised = ArrayList<Alert>()
        for (cd in conds) {
            val key = cd.kind.name + cd.subject
            val t = tracks.getOrPut(key) { Track() }
            val enabled = cd.kind.group !in c.disabledGroups
            val isActive = key in _active
            val debounce = if (cd.kind.level == AlertLevel.CONNECTIVITY) c.connectivityDebounceMs else c.debounceMs
            if (cd.on && enabled) {
                t.clearSince = -1L
                if (t.since < 0L) t.since = now
                if (!isActive && now - t.since >= debounce && now >= t.coolUntil) {
                    val a = Alert(cd.kind, cd.subject, cd.message, cd.source, now)
                    _active[key] = a
                    addHistory(a)
                    raised.add(a)
                } else if (isActive) {
                    // cập nhật nội dung (giá trị mới) nhưng giữ nguyên thời điểm và trạng thái xác nhận
                    _active[key] = _active.getValue(key).copy(message = cd.message)
                }
            } else {
                t.since = -1L
                if (isActive) {
                    if (t.clearSince < 0L) t.clearSince = now
                    if (!enabled || now - t.clearSince >= c.clearMs) { _active.remove(key); t.coolUntil = now + c.cooldownMs; t.clearSince = -1L }
                }
            }
        }
        // Điều kiện không còn được đánh giá được (dữ liệu mất tin cậy): xoá bộ đếm bật, cảnh báo cũ giữ tới khi điều kiện quay lại và hết thật
        val seen = conds.map { it.kind.name + it.subject }.toSet()
        for ((k, t) in tracks) if (k !in seen && k !in _active) t.since = -1L
        return raised
    }

    private fun addHistory(a: Alert) {
        _history.add(a)
        while (_history.size > config.historyMax) _history.removeAt(0)
    }
}
