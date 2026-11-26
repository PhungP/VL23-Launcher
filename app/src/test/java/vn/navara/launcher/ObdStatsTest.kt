package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class ObdStatsTest {
    @Test fun emptyHasNoRatio() = assertNull(ObdStats().successRatio())

    @Test fun countsAndRatio() {
        val s = ObdStats()
        s.success(1000, 100); s.success(1100, 200); s.timeout(); s.error()
        assertEquals(2L, s.ok); assertEquals(1L, s.timeouts); assertEquals(1L, s.errors)
        assertEquals(0.5, s.successRatio()!!, 1e-9)
        assertEquals(1100L, s.lastOkMs); assertEquals(200L, s.lastLatencyMs)
    }

    @Test fun averageLatencyFirstSampleThenSmoothed() {
        val s = ObdStats(); s.success(0, 100)
        assertEquals(100.0, s.avgLatencyMs, 1e-9)
        s.success(1, 200)
        assertEquals(110.0, s.avgLatencyMs, 1e-9)
    }

    @Test fun pollRateUsesTenSecondWindow() {
        val s = ObdStats()
        for (i in 0 until 20) s.success(1000L + i * 100, 50)   // 20 phản hồi trong 2 giây
        assertEquals(2.0, s.pollRate(3000), 1e-9)
        assertEquals(0.0, s.pollRate(20_000), 1e-9)            // đã ra khỏi cửa sổ
    }

    @Test fun disconnectAndReset() {
        val s = ObdStats(); s.disconnect(); s.success(1, 10); s.reset()
        assertEquals(0L, s.disconnects); assertEquals(0L, s.ok); assertNull(s.successRatio())
    }
}
