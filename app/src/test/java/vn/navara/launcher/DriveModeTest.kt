package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class DriveModeTest {
    private fun sp(v: Float?, st: DataStatus = DataStatus.REAL_TIME) = Reading(v, "km/h", st, 1, "OBD")
    private fun ctl() = DriveModeController(ModeConfig(auto = true))
    private fun feed(c: DriveModeController, from: Long, to: Long, speed: Reading, step: Long = 1000) {
        var t = from; while (t <= to) { c.update(t, speed); t += step }
    }

    @Test fun startsParked() = assertEquals(DriveMode.PARKED, ctl().mode)

    @Test fun switchesToDrivingAfterHold() {
        val c = ctl()
        feed(c, 0, 2000, sp(30f)); assertEquals(DriveMode.PARKED, c.mode)
        feed(c, 3000, 4000, sp(30f)); assertEquals(DriveMode.DRIVING, c.mode)
    }

    @Test fun hysteresisBandKeepsMode() {
        val c = ctl(); feed(c, 0, 5000, sp(40f))
        feed(c, 6000, 60_000, sp(6f))       // giữa 3 và 10 km/h
        assertEquals(DriveMode.DRIVING, c.mode)
    }

    @Test fun returnsToParkedAfterLongerHold() {
        val c = ctl(); feed(c, 0, 5000, sp(40f))
        feed(c, 6000, 14_000, sp(0f)); assertEquals(DriveMode.DRIVING, c.mode)
        feed(c, 15_000, 22_000, sp(0f)); assertEquals(DriveMode.PARKED, c.mode)
    }

    @Test fun trafficStopDoesNotFlapShortStops() {
        val c = ctl(); feed(c, 0, 5000, sp(40f))
        feed(c, 6000, 10_000, sp(0f)); feed(c, 11_000, 14_000, sp(40f))
        assertEquals(DriveMode.DRIVING, c.mode)
    }

    @Test fun staleOrEstimatedSpeedNeverSwitches() {
        val c = ctl()
        feed(c, 0, 20_000, sp(80f, DataStatus.STALE)); assertEquals(DriveMode.PARKED, c.mode)
        feed(c, 21_000, 40_000, sp(80f, DataStatus.ESTIMATED)); assertEquals(DriveMode.PARKED, c.mode)
        feed(c, 41_000, 60_000, sp(null, DataStatus.UNAVAILABLE)); assertEquals(DriveMode.PARKED, c.mode)
    }

    @Test fun lostDataKeepsCurrentModeAndResetsTimer() {
        val c = ctl(); feed(c, 0, 5000, sp(50f))
        feed(c, 6000, 100_000, sp(0f, DataStatus.STALE))
        assertEquals(DriveMode.DRIVING, c.mode)
    }

    @Test fun gpsSpeedIsTrusted() {
        val c = ctl(); feed(c, 0, 4000, Reading(50f, "km/h", DataStatus.GPS, 1, "GPS"))
        assertEquals(DriveMode.DRIVING, c.mode)
    }

    @Test fun manualOverrideWinsAndIsReversible() {
        val c = ctl(); feed(c, 0, 5000, sp(50f))
        c.override = ModeOverride.PARKED; assertEquals(DriveMode.PARKED, c.mode)
        c.override = ModeOverride.AUTO; assertEquals(DriveMode.DRIVING, c.mode)
        c.override = ModeOverride.DRIVING; feed(c, 6000, 60_000, sp(0f)); assertEquals(DriveMode.DRIVING, c.mode)
    }

    @Test fun autoDisabledStaysParked() {
        val c = DriveModeController(ModeConfig(auto = false)); feed(c, 0, 20_000, sp(90f))
        assertEquals(DriveMode.PARKED, c.mode)
    }

    @Test fun nightWindowAcrossMidnight() {
        assertTrue(NightPolicy.isNight(22)); assertTrue(NightPolicy.isNight(3))
        assertFalse(NightPolicy.isNight(12)); assertFalse(NightPolicy.isNight(6)); assertTrue(NightPolicy.isNight(19))
        assertFalse(NightPolicy.isNight(10, 8, 8))
    }
}
