package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class ProfileIntegrationTest {
    @Test fun everyBuiltinProfileIsValidSoItsDuplicateIsValid() {
        for (v in Vehicles.builtin) assertTrue(v.id + ": " + ProfileValidation.validate(v.toData()), ProfileValidation.validate(v.toData()).isEmpty())
    }

    @Test fun builtinRoundTripThroughJsonKeepsOperatingParameters() {
        for (v in Vehicles.builtin) {
            val p = ProfileIO.preview(ProfileIO.exportJson(listOf(v.toData())), emptyList(), emptyList())
            val back = p.items.single().data!!.toRuntime()
            assertEquals(v.tireM, back.tireM, 1e-9); assertEquals(v.rpmRed, back.rpmRed); assertEquals(v.rpmMax, back.rpmMax)
            assertArrayEquals(v.gearRatios, back.gearRatios, 1e-9); assertEquals(v.skipPids, back.skipPids); assertEquals(v.fuel, back.fuel)
        }
    }

    @Test fun userProfileBecomesRuntimeProfileWithItsOwnParameters() {
        val d = Vehicles.builtin[1].toData().copy(id = "u_1", nickname = "Accord nhà", tireM = 2.15, redline = 6500, obdProtocol = 6,
            priorityPids = listOf("11"), alerts = AlertOverride(coolantWarn = 100f))
        val r = d.toRuntime()
        assertTrue(r.custom); assertEquals("Accord nhà", r.title); assertEquals(2.15, r.tireM, 1e-9)
        assertEquals(6500, r.rpmRed); assertEquals(6, r.obdProtocol); assertEquals(listOf("11"), r.priorityPids); assertEquals(100f, r.alerts.coolantWarn)
    }

    @Test fun switchingProfileAppliesNewVehicleAndKeepsUnrelatedSettings() {
        val before = UiPrefs(vehicle = "nissan_navara_2023_vl", geminiKey = "SECRET", mapEngine = 3, trafficAlert = false, homeOffsets = "x")
        val after = before.withVehicle(Vehicles.builtin.first { it.id.startsWith("honda") })
        assertTrue(after.vehicle.startsWith("honda")); assertEquals(1, after.screenMode)
        assertEquals("SECRET", after.geminiKey); assertEquals(3, after.mapEngine); assertFalse(after.trafficAlert)
        assertEquals("", after.homeOffsets)   // bố cục ô của xe cũ không áp sang xe mới
    }

    @Test fun customProfileKeepsCurrentScreenMode() {
        val d = Vehicles.builtin[0].toData().copy(id = "u_2", nickname = "X").toRuntime()
        assertEquals(1, UiPrefs(screenMode = 1).withVehicle(d).screenMode)
    }

    @Test fun profileAlertOverridesReplaceGlobalThresholdsOnlyForThatVehicle() {
        val global = AlertConfig(coolantWarn = 105f, coolantCrit = 112f)
        val oldCar = global.withOverrides(AlertOverride(coolantWarn = 95f, coolantCrit = 100f))
        val newCar = global.withOverrides(AlertOverride())
        assertEquals(95f, oldCar.coolantWarn); assertEquals(105f, newCar.coolantWarn)
    }

    @Test fun switchingVehicleClearsActiveAlertsButKeepsHistory() {
        val e = AlertEngine()
        fun rd(v: Float) = Reading(v, "°C", DataStatus.REAL_TIME, 1, "OBD")
        fun snap(t: Long) = VehicleSnapshot(t, Reading(0f, "km/h", DataStatus.REAL_TIME, 1, "OBD"), Reading(1500f, "rpm", DataStatus.REAL_TIME, 1, "OBD"),
            Reading.unavailable("", "OBD"), rd(110f), Reading(13.8f, "V", DataStatus.REAL_TIME, 1, "OBD"), Reading.unavailable("bar", "OBD"), emptyMap(), true, true, true, true)
        for (t in 0L..5000L step 1000) e.evaluate(snap(t))
        assertEquals(1, e.active.size)
        e.reset()
        assertTrue(e.active.isEmpty()); assertEquals(1, e.history.size)
        // sau khi đổi xe, điều kiện phải được đếm lại từ đầu (debounce) chứ không báo ngay
        assertTrue(e.evaluate(snap(6000)).isEmpty())
    }

    @Test fun vehiclesByIdFindsUserProfilesAndFallsBackSafely() {
        val u = Vehicles.builtin[1].toData().copy(id = "u_9", nickname = "Của tôi").toRuntime()
        Vehicles.user = listOf(u)
        try {
            assertEquals("u_9", Vehicles.byId("u_9").id)
            assertEquals(Vehicles.builtin[0].id, Vehicles.byId("does_not_exist").id)   // hồ sơ bị xoá: về hồ sơ mặc định, không crash
            assertEquals(Vehicles.builtin.size + 1, Vehicles.all.size)
        } finally { Vehicles.user = emptyList() }
    }

    @Test fun oldInstallsKeepWorkingBecauseBuiltinIdsAreStable() {
        assertEquals("nissan_navara_2023_vl", Vehicles.byId("nissan_navara_2023_vl").id)
        assertEquals("honda_accord_2015_th", Vehicles.byId("honda_accord_2015_th").id)
        assertEquals("generic_petrol", Vehicles.byId("generic_petrol").id)
        assertEquals("generic_diesel", Vehicles.byId("generic_diesel").id)
    }
}
