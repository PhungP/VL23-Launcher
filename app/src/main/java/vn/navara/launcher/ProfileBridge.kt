package vn.navara.launcher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

// ===== Cầu nối giữa hồ sơ dựng sẵn (VehicleProfile), hồ sơ người dùng (ProfileData) và ứng dụng =====

fun VehicleProfile.toData(): ProfileData = ProfileData(
    id = id, builtin = !custom, nickname = nickname, brand = brand, model = model, year = year, variant = variant,
    engine = engine, displacementL = displacementL, fuel = fuel.name, turbo = turbo, gearbox = gearbox, drivetrain = drivetrain,
    vehicleType = vehicleType.ifBlank { if (sedan) "SEDAN" else "PICKUP" },
    gearRatios = gearRatios.toList(), finalDrive = finalDrive, tireM = tireM, wheelSize = wheelSize, ratiosVerified = ratiosVerified,
    redline = rpmRed, rpmMax = rpmMax, idleRpm = idleRpm.takeIf { it > 0 } ?: 800, barMax = barMax, maxSpeedKmh = maxSpeedKmh, fuelTankL = fuelTankL,
    obdProtocol = obdProtocol, priorityPids = priorityPids, skipPids = skipPids.toList(),
    skin = skin, homeTiles = homeTiles, homeHide = homeHide.toList(), obdHide = obdHide.toList(), obdAdd = obdAdd.toList(), alerts = alerts,
)

fun ProfileData.toRuntime(): VehicleProfile = VehicleProfile(
    id = id, brand = brand, model = model, year = year, variant = variant, engine = engine,
    fuel = if (fuel == "DIESEL") Fuel.DIESEL else Fuel.PETROL, turbo = turbo, gearbox = gearbox,
    gearRatios = gearRatios.toDoubleArray(), finalDrive = finalDrive, tireM = tireM, ratiosVerified = ratiosVerified,
    sedan = vehicleType != "PICKUP" && vehicleType != "SUV", photos = emptyList(),
    skin = skin, homeTiles = homeTiles, homeHide = homeHide.toSet(), obdHide = obdHide.toSet(), obdAdd = obdAdd.toSet(),
    skipPids = skipPids.toSet(), rpmMax = rpmMax, rpmRed = redline, barMax = barMax,
    custom = true, nickname = nickname, displacementL = displacementL, drivetrain = drivetrain, vehicleType = vehicleType,
    idleRpm = idleRpm, maxSpeedKmh = maxSpeedKmh, fuelTankL = fuelTankL, wheelSize = wheelSize,
    obdProtocol = obdProtocol, priorityPids = priorityPids, alerts = alerts,
)

/** Áp hồ sơ xe vào cài đặt giao diện (giữ đúng hành vi chọn xe trước đây: bộ giao diện, 6 ô trang 1, thông số OBD, hiệu chỉnh cấp số). */
fun UiPrefs.withVehicle(v: VehicleProfile): UiPrefs = copy(
    vehicle = v.id,
    // Hai màn hình có sẵn của Navara / Accord; hồ sơ do người dùng tạo giữ nguyên chế độ màn hình đang dùng
    screenMode = if (v.id.startsWith("honda")) 1 else if (v.id.startsWith("nissan")) 0 else screenMode,
    skin = v.skin, homeTiles = v.homeTiles, carImg = 0, carPhoto = 0, homeOffsets = "", homeSizes = "", homeStyles = "", obdTiles = "", gearTrim = 2,
)

/** Kho hồ sơ người dùng dùng chung toàn ứng dụng. */
object Profiles {
    private var _repo: ProfileRepository? = null
    val repo: ProfileRepository get() = _repo ?: error("Profiles.init chưa được gọi")
    val version = MutableStateFlow(0)   // tăng mỗi khi danh sách đổi, để giao diện dựng lại

    @Synchronized fun init(ctx: Context) {
        if (_repo != null) return
        val r = ProfileRepository(FileProfileStore(ctx.applicationContext.filesDir), reservedIds = { Vehicles.builtin.map { it.id }.toSet() })
        r.load()
        _repo = r
        Vehicles.user = r.profiles.value.map { it.toRuntime() }
    }

    /** Gọi sau mọi thay đổi trong repo để ứng dụng thấy hồ sơ mới. Nếu hồ sơ đang dùng bị sửa thì Vehicles.current cũng cập nhật. */
    fun publish() {
        Vehicles.user = repo.profiles.value.map { it.toRuntime() }
        version.value++
    }

    fun builtinData(): List<ProfileData> = Vehicles.builtin.map { it.toData() }
    fun userData(): List<ProfileData> = repo.profiles.value
    fun dataOf(id: String): ProfileData? = (builtinData() + userData()).firstOrNull { it.id == id }
}
