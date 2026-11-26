package vn.navara.launcher

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

// ===== Giám sát xe: dựng ảnh chụp dữ liệu đã phân loại mỗi giây, cho trung tâm cảnh báo và bộ chọn chế độ lái =====
// Cấu hình lưu trong SharedPreferences riêng "vl23_monitor" (không đụng UiPrefs nên không ảnh hưởng dữ liệu cũ khi nâng cấp).

object VehicleMonitor {
    val alertConfig = MutableStateFlow(AlertConfig())
    val modeConfig = MutableStateFlow(ModeConfig())
    val override = MutableStateFlow(ModeOverride.AUTO)
    val lockSettingsWhileDriving = MutableStateFlow(true)
    val autoNight = MutableStateFlow(false)
    val nightNow = MutableStateFlow(false)

    private val _active = MutableStateFlow<List<Alert>>(emptyList())
    private val _history = MutableStateFlow<List<Alert>>(emptyList())
    private val _mode = MutableStateFlow(DriveMode.PARKED)
    private val _snapshot = MutableStateFlow<VehicleSnapshot?>(null)
    val active: StateFlow<List<Alert>> = _active
    val history: StateFlow<List<Alert>> = _history
    val mode: StateFlow<DriveMode> = _mode
    val snapshot: StateFlow<VehicleSnapshot?> = _snapshot

    private val engine = AlertEngine()
    private val controller = DriveModeController()
    private var everLive = false
    private var everFix = false
    private var lastProfileId = ""

    fun load(ctx: Context) {
        val s = ctx.getSharedPreferences("vl23_monitor", Context.MODE_PRIVATE)
        val d = AlertConfig()
        alertConfig.value = AlertConfig(
            coolantWarn = s.getFloat("coolantWarn", d.coolantWarn), coolantCrit = s.getFloat("coolantCrit", d.coolantCrit),
            voltLow = s.getFloat("voltLow", d.voltLow), rpmMax = s.getFloat("rpmMax", d.rpmMax), boostMax = s.getFloat("boostMax", d.boostMax),
            tpmsLowBar = s.getFloat("tpmsLow", d.tpmsLowBar), tpmsHighBar = s.getFloat("tpmsHigh", d.tpmsHighBar),
            disabledGroups = (s.getString("offGroups", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet(),
        )
        val m = ModeConfig()
        modeConfig.value = ModeConfig(auto = s.getBoolean("modeAuto", m.auto), driveKmh = s.getInt("driveKmh", m.driveKmh), parkKmh = s.getInt("parkKmh", m.parkKmh))
        lockSettingsWhileDriving.value = s.getBoolean("lockSettings", true)
        autoNight.value = s.getBoolean("autoNight", false)
        // Ghi đè thủ công không lưu: mỗi lần mở app trở về tự động
    }

    fun save(ctx: Context) {
        val c = alertConfig.value; val m = modeConfig.value
        ctx.getSharedPreferences("vl23_monitor", Context.MODE_PRIVATE).edit()
            .putFloat("coolantWarn", c.coolantWarn).putFloat("coolantCrit", c.coolantCrit).putFloat("voltLow", c.voltLow)
            .putFloat("rpmMax", c.rpmMax).putFloat("boostMax", c.boostMax).putFloat("tpmsLow", c.tpmsLowBar).putFloat("tpmsHigh", c.tpmsHighBar)
            .putString("offGroups", c.disabledGroups.joinToString(","))
            .putBoolean("modeAuto", m.auto).putInt("driveKmh", m.driveKmh).putInt("parkKmh", m.parkKmh)
            .putBoolean("lockSettings", lockSettingsWhileDriving.value).putBoolean("autoNight", autoNight.value)
            .apply()
    }

    fun resetAlertConfig(ctx: Context) { alertConfig.value = AlertConfig(); save(ctx) }

    fun acknowledge(key: String) { engine.acknowledge(key); publish() }
    fun acknowledgeAll() { engine.acknowledgeAll(); publish() }
    fun clearHistory() { engine.clearHistory(); publish() }

    private fun publish() {
        _active.value = engine.active
        _history.value = engine.history
    }

    /** Một nhịp đánh giá (gọi mỗi giây trên luồng chính). */
    fun tick(now: Long) {
        val obd = ObdManager.data.value
        val loc = LocationHolder.loc.value
        val gpsOn = LocationHolder.gpsOn.value
        val fix = hasFix(loc)
        val connected = obd.status == ObdManager.OK
        val lastRx = ObdManager.lastRxMs
        if (connected && lastRx > 0 && now - lastRx <= Snapshots.OBD_TTL_MS) everLive = true
        if (fix) everFix = true
        // Giờ định vị của GPS (ms) nếu có; hasFix cho phép tới 15 giây tuổi
        val fixAt = if (fix && loc != null) now - (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L else 0L
        val tp = TpmsManager.wheels.value.mapValues { (_, w) -> w.bar to w.at }
        val raw = RawInputs(
            obdConnected = connected, obdLastRxMs = lastRx, speedKmh = obd.speed, rpm = obd.rpm,
            coolant = obd.coolant, volt = obd.volt, boost = obd.boost, gear = obd.gear, gearEstimated = obd.gearEst,
            gpsOn = gpsOn && everFix, gpsFix = fix, gpsSpeedKmh = gpsKmh(loc), gpsFixAtMs = fixAt,
            tpms = tp, obdExpected = everLive,
        )
        val snap = Snapshots.build(raw, now)
        _snapshot.value = snap

        val prof = Vehicles.current
        // Đổi xe: cảnh báo đang bật của xe cũ không được áp sang xe mới
        if (prof.id != lastProfileId) { if (lastProfileId.isNotEmpty()) engine.reset(); lastProfileId = prof.id }
        // Ngưỡng chung của người dùng, rồi ngưỡng riêng của hồ sơ xe đè lên (nếu có); vòng tua mặc định theo vạch đỏ của xe
        val ac = alertConfig.value.withOverrides(prof.alerts)
        engine.config = if (ac.rpmMax > 0f) ac else ac.copy(rpmMax = prof.rpmRed.toFloat())
        engine.evaluate(snap)
        publish()

        controller.config = modeConfig.value
        controller.override = override.value
        _mode.value = controller.update(now, snap.speed)

        if (autoNight.value) nightNow.value = NightPolicy.isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        else nightNow.value = false
    }

    suspend fun run(ctx: Context) {
        load(ctx)
        while (true) {
            runCatching { tick(System.currentTimeMillis()) }
            delay(1000)
        }
    }
}
