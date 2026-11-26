package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class AlertEngineTest {
    private fun rd(v: Float?, unit: String, st: DataStatus = DataStatus.REAL_TIME, src: String = "OBD") =
        Reading(v, unit, st, if (v == null) 0 else 1, src)

    private fun snap(now: Long, coolant: Reading = rd(90f, "°C"), volt: Reading = rd(13.8f, "V"), rpm: Reading = rd(1500f, "rpm"),
                     boost: Reading = rd(0.5f, "bar"), tpms: Map<String, Reading> = emptyMap(),
                     obdLive: Boolean = true, obdExpected: Boolean = true, gpsOn: Boolean = true, gpsFix: Boolean = true) =
        VehicleSnapshot(now, rd(0f, "km/h"), rpm, rd(null, ""), coolant, volt, boost, tpms, obdLive, obdExpected, gpsOn, gpsFix)

    private fun run(e: AlertEngine, from: Long, to: Long, step: Long = 1000, mk: (Long) -> VehicleSnapshot): List<Alert> {
        val out = ArrayList<Alert>()
        var t = from
        while (t <= to) { out += e.evaluate(mk(t)); t += step }
        return out
    }

    @Test fun normalValuesRaiseNothing() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 60_000) { snap(it) }.isEmpty())
        assertTrue(e.active.isEmpty())
    }

    @Test fun debounceDelaysAlert() {
        val e = AlertEngine()
        assertTrue(e.evaluate(snap(0, coolant = rd(108f, "°C"))).isEmpty())
        assertTrue(e.evaluate(snap(2000, coolant = rd(108f, "°C"))).isEmpty())
        val r = e.evaluate(snap(3000, coolant = rd(108f, "°C")))
        assertEquals(AlertKind.ENGINE_TEMP_WARN, r.single().kind)
    }

    @Test fun briefSpikeIsIgnored() {
        val e = AlertEngine()
        e.evaluate(snap(0, coolant = rd(120f, "°C")))
        e.evaluate(snap(1000, coolant = rd(120f, "°C")))
        e.evaluate(snap(2000)) // trở lại bình thường: bộ đếm reset
        assertTrue(run(e, 3000, 4500) { snap(it, coolant = rd(120f, "°C")) }.isEmpty())
    }

    @Test fun criticalReplacesWarningAndSortsFirst() {
        val e = AlertEngine()
        run(e, 0, 5000) { snap(it, coolant = rd(108f, "°C"), tpms = mapOf("FL" to rd(1.5f, "bar", src = "TPMS"))) }
        run(e, 6000, 10_000) { snap(it, coolant = rd(115f, "°C"), tpms = mapOf("FL" to rd(1.5f, "bar", src = "TPMS"))) }
        assertEquals(AlertLevel.CRITICAL, e.active.first().level)
        assertEquals(AlertKind.ENGINE_TEMP_CRIT, e.top?.kind)
    }

    @Test fun staleDataNeverRaisesMeasuredAlert() {
        val e = AlertEngine()
        val r = run(e, 0, 30_000) { snap(it, coolant = rd(130f, "°C", DataStatus.STALE)) }
        assertTrue(r.none { it.kind == AlertKind.ENGINE_TEMP_CRIT || it.kind == AlertKind.ENGINE_TEMP_WARN })
    }

    @Test fun estimatedAndUnavailableDoNotAlert() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 30_000) { snap(it, rpm = rd(9000f, "rpm", DataStatus.ESTIMATED), boost = rd(null, "bar")) }.isEmpty())
    }

    @Test fun lowVoltageOnlyWhenEngineRunning() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 10_000) { snap(it, volt = rd(11f, "V"), rpm = rd(0f, "rpm")) }.isEmpty())
        val r = run(e, 11_000, 15_000) { snap(it, volt = rd(11f, "V")) }
        assertEquals(AlertKind.LOW_VOLTAGE, r.single().kind)
    }

    @Test fun cooldownPreventsRepeat() {
        val e = AlertEngine()
        val hot = { t: Long -> snap(t, coolant = rd(108f, "°C")) }
        assertEquals(1, run(e, 0, 5000, mk = hot).size)
        run(e, 6000, 10_000) { snap(it) }                    // hết điều kiện -> tắt sau clearMs
        assertTrue(e.active.isEmpty())
        assertTrue(run(e, 11_000, 30_000, mk = hot).isEmpty()) // còn trong cooldown 60 giây
        assertEquals(1, run(e, 70_000, 76_000, mk = hot).size)  // hết cooldown mới báo lại
    }

    @Test fun acknowledgeSilencesTopButKeepsActive() {
        val e = AlertEngine()
        run(e, 0, 5000) { snap(it, coolant = rd(108f, "°C")) }
        val k = e.active.single().key
        e.acknowledge(k)
        assertNull(e.top)
        assertEquals(1, e.active.size)
        assertTrue(e.active.single().acknowledged)
        // tiếp tục vượt ngưỡng không tạo cảnh báo mới
        assertTrue(run(e, 6000, 20_000) { snap(it, coolant = rd(109f, "°C")) }.isEmpty())
    }

    @Test fun historyKeepsRaisedAlertsAndIsCapped() {
        val e = AlertEngine(AlertConfig(historyMax = 2, cooldownMs = 0, clearMs = 0))
        repeat(4) { i ->
            val base = i * 20_000L
            run(e, base, base + 4000) { snap(it, coolant = rd(108f, "°C")) }
            run(e, base + 5000, base + 7000) { snap(it) }
        }
        assertEquals(2, e.history.size)
    }

    @Test fun disabledGroupRaisesNothing() {
        val e = AlertEngine(AlertConfig(disabledGroups = setOf("engine")))
        assertTrue(run(e, 0, 10_000) { snap(it, coolant = rd(120f, "°C")) }.isEmpty())
    }

    @Test fun disablingGroupClearsActive() {
        val e = AlertEngine()
        run(e, 0, 5000) { snap(it, coolant = rd(108f, "°C")) }
        assertEquals(1, e.active.size)
        e.config = e.config.copy(disabledGroups = setOf("engine"))
        e.evaluate(snap(6000, coolant = rd(108f, "°C")))
        assertTrue(e.active.isEmpty())
    }

    @Test fun obdLostUsesLongerDebounce() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 9000) { snap(it, obdLive = false) }.none { it.kind == AlertKind.OBD_LOST })
        assertTrue(run(e, 10_000, 11_000) { snap(it, obdLive = false) }.any { it.kind == AlertKind.OBD_LOST })
    }

    @Test fun obdLostNotRaisedWhenNotExpected() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 30_000) { snap(it, obdLive = false, obdExpected = false) }.none { it.kind == AlertKind.OBD_LOST })
    }

    @Test fun gpsLostAndRecovers() {
        val e = AlertEngine()
        run(e, 0, 12_000) { snap(it, gpsFix = false) }
        assertTrue(e.active.any { it.kind == AlertKind.GPS_LOST })
        run(e, 13_000, 17_000) { snap(it, gpsFix = true) }
        assertTrue(e.active.none { it.kind == AlertKind.GPS_LOST })
    }

    @Test fun tpmsLowPerWheelAndMissingSensor() {
        val e = AlertEngine()
        val r = run(e, 0, 5000) { snap(it, tpms = mapOf("FL" to rd(1.5f, "bar", src = "TPMS"), "FR" to rd(2.4f, "bar", src = "TPMS"), "RL" to rd(2.4f, "bar", DataStatus.STALE, "TPMS"))) }
        assertEquals(setOf("TPMS_LOWFL", "TPMS_MISSINGRL"), r.map { it.key }.toSet())
    }

    @Test fun alertCarriesSourceAndTimestamp() {
        val e = AlertEngine()
        val a = run(e, 0, 5000) { snap(it, coolant = rd(108f, "°C")) }.single()
        assertEquals("OBD", a.source); assertEquals(3000L, a.raisedAtMs)
    }
}
