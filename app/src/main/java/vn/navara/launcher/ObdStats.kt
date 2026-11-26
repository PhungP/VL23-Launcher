package vn.navara.launcher

// ===== Thống kê kết nối OBD cho màn hình chẩn đoán (thuần Kotlin, kiểm thử được) =====

class ObdStats {
    var ok = 0L; private set
    var timeouts = 0L; private set
    var errors = 0L; private set
    var disconnects = 0L; private set
    var lastOkMs = 0L; private set
    var lastLatencyMs = 0L; private set
    var avgLatencyMs = 0.0; private set
    private val recent = ArrayDeque<Long>()     // thời điểm các phản hồi thành công gần đây, để tính tần suất hỏi
    private val WINDOW_MS = 10_000L

    @Synchronized fun success(now: Long, latencyMs: Long) {
        ok++; lastOkMs = now; lastLatencyMs = latencyMs
        avgLatencyMs = if (ok == 1L) latencyMs.toDouble() else avgLatencyMs * 0.9 + latencyMs * 0.1   // trung bình trượt
        recent.addLast(now); trim(now)
    }
    @Synchronized fun timeout() { timeouts++ }
    @Synchronized fun error() { errors++ }
    @Synchronized fun disconnect() { disconnects++ }

    /** Số phản hồi thành công mỗi giây trong 10 giây gần nhất. */
    @Synchronized fun pollRate(now: Long): Double { trim(now); return recent.size * 1000.0 / WINDOW_MS }

    /** Tỉ lệ phản hồi thành công trên tổng lượt hỏi (0..1), null nếu chưa có lượt nào. */
    @Synchronized fun successRatio(): Double? { val n = ok + timeouts + errors; return if (n == 0L) null else ok.toDouble() / n }

    @Synchronized fun reset() { ok = 0; timeouts = 0; errors = 0; disconnects = 0; lastOkMs = 0; lastLatencyMs = 0; avgLatencyMs = 0.0; recent.clear() }

    private fun trim(now: Long) { while (recent.isNotEmpty() && now - recent.first() > WINDOW_MS) recent.removeFirst() }
}
