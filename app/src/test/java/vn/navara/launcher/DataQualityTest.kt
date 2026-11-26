package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class DataQualityTest {
    private fun raw(connected: Boolean = true, lastRx: Long = 1000, gear: Int? = 3, est: Boolean = true, gpsFix: Boolean = true, gpsAt: Long = 1000, expected: Boolean = true) =
        RawInputs(connected, lastRx, 50, 2000, 90, 13.8f, 0.5f, gear, est, true, gpsFix, 48, gpsAt, mapOf("FL" to (2.4f to 1000L)), expected)

    @Test fun nullValueIsUnavailable() =
        assertEquals(DataStatus.UNAVAILABLE, Reading.classify(null, "V", "OBD", DataKind.MEASURED, 100, 200, 1000).status)

    @Test fun freshMeasured() =
        assertEquals(DataStatus.REAL_TIME, Reading.classify(1f, "V", "OBD", DataKind.MEASURED, 100, 200, 1000).status)

    @Test fun expiresToStale() {
        val r = Reading.classify(1f, "V", "OBD", DataKind.MEASURED, 100, 5000, 1000)
        assertEquals(DataStatus.STALE, r.status); assertFalse(r.usable); assertFalse(r.trusted)
    }

    @Test fun estimatedIsNotTrusted() {
        val r = Reading.classify(1f, "", "OBD", DataKind.ESTIMATED, 100, 200, 1000)
        assertTrue(r.usable); assertFalse(r.trusted)
    }

    @Test fun estimatedGearNeverRealTime() =
        assertEquals(DataStatus.ESTIMATED, Snapshots.build(raw(est = true), 1500).gear.status)

    @Test fun hardwareGearIsRealTime() =
        assertEquals(DataStatus.REAL_TIME, Snapshots.build(raw(est = false), 1500).gear.status)

    @Test fun speedPrefersLiveObd() {
        val s = Snapshots.build(raw(), 1500)
        assertEquals(DataStatus.REAL_TIME, s.speed.status); assertEquals(50f, s.speed.value)
    }

    @Test fun speedFallsBackToGps() {
        val s = Snapshots.build(raw(connected = false), 1500)
        assertEquals(DataStatus.GPS, s.speed.status); assertEquals(48f, s.speed.value)
    }

    @Test fun speedUnavailableWithoutAnySource() {
        val s = Snapshots.build(raw(connected = false, gpsFix = false), 1500)
        assertEquals(DataStatus.UNAVAILABLE, s.speed.status)
    }

    @Test fun obdStopsRespondingBecomesStale() {
        val s = Snapshots.build(raw(lastRx = 1000, gpsFix = false), 10_000)
        assertFalse(s.obdLive); assertEquals(DataStatus.STALE, s.speed.status); assertEquals(DataStatus.STALE, s.rpm.status)
    }

    @Test fun staleGpsIsNotUsed() {
        val s = Snapshots.build(raw(connected = false, gpsAt = 1000), 20_000)
        assertEquals(DataStatus.UNAVAILABLE, s.speed.status)
    }

    @Test fun tpmsStaleAfterTtl() {
        val s = Snapshots.build(raw(), 1000 + Snapshots.TPMS_TTL_MS + 1)
        assertEquals(DataStatus.STALE, s.tpmsBar.getValue("FL").status)
    }
}
