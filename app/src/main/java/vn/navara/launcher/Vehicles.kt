package vn.navara.launcher

// ===== Hồ sơ xe: mỗi dòng xe (hãng, model, năm) có động cơ, hộp số, giao diện, thông số OBD và PID riêng =====
// Chọn hồ sơ trong Cài đặt > Hồ sơ xe: ứng dụng đổi bộ giao diện, ảnh xe, 6 ô thông số ở trang 1,
// các thông số hiện ở trang OBD, danh sách PID được đọc, bảng tỉ số hộp số để tính cấp số và thang vòng tua.

enum class Fuel { PETROL, DIESEL }

// Ảnh của hồ sơ xe. scene = true: ảnh phong cảnh (xe và nền), phủ kín nền trang 1; false: ảnh xe rời, đặt giữa và mờ dần ra rìa
class CarPhoto(val res: Int, val name: String, val scene: Boolean)

class VehicleProfile(
    val id: String,
    val brand: String,
    val model: String,
    val year: Int,
    val variant: String,                 // đời / phiên bản / thị trường
    val engine: String,                  // mô tả động cơ
    val fuel: Fuel,
    val turbo: Int,                      // 0 hút khí tự nhiên, 1 tăng áp, 2 tăng áp kép
    val gearbox: String,                 // mô tả hộp số
    val gearRatios: DoubleArray,         // tỉ số từng cấp (rỗng = không ước lượng cấp số từ vòng tua/tốc độ)
    val finalDrive: Double,              // tỉ số cầu cuối
    val tireM: Double,                   // chu vi lốp (mét)
    val ratiosVerified: Boolean,         // false = tỉ số hộp số là ước tính, cần kiểm tra
    val sedan: Boolean,                  // true = hình xe vector dạng sedan, false = bán tải
    val photos: List<CarPhoto>,          // các ảnh xe để chọn ở trang 1; rỗng = dùng hình vector
    val skin: Int,                       // bộ giao diện: 0 Đồng hồ (cam + xanh), 5 HUD SU-35, 6 HUD Orange, 7 HUD Cyberpunk
    val homeTiles: String,               // 6 thông số mặc định ở trang 1
    val homeHide: Set<String>,           // thông số không phù hợp, ẩn khỏi danh sách chọn ở trang 1
    val obdHide: Set<String>,            // thông số ẩn ở trang OBD
    val obdAdd: Set<String>,             // thông số bật mặc định thêm ở trang OBD
    val skipPids: Set<String>,           // PID không cần hỏi xe (tiết kiệm thời gian đọc)
    val rpmMax: Int,                     // thang đồng hồ vòng tua
    val rpmRed: Int,                     // bắt đầu vùng đỏ
    val barMax: Int,                     // thang dải đèn vòng tua ở trang OBD
) {
    val title: String get() = "$brand $model $year" + if (variant.isNotBlank()) " $variant" else ""
    val spec: String get() = "$engine · $gearbox"
}

object Vehicles {
    private val NAVARA = VehicleProfile(
        id = "nissan_navara_2023_vl", brand = "Nissan", model = "Navara", year = 2023, variant = "VL",
        engine = "Diesel 2.3L tăng áp kép (YS23DDTT)", fuel = Fuel.DIESEL, turbo = 2,
        gearbox = "Tự động 7AT",
        // Tỉ số theo tài liệu Nissan; cầu 3.357; lốp 255/60R18 chu vi khoảng 2.398 m
        gearRatios = doubleArrayOf(4.886, 3.169, 2.027, 1.411, 1.000, 0.864, 0.774),
        finalDrive = 3.357, tireM = 2.398, ratiosVerified = true, sedan = false,
        photos = listOf(
            CarPhoto(R.drawable.navara_night, "Mưa đêm", true),
            CarPhoto(R.drawable.navara_desert, "Sa mạc", true),
            CarPhoto(R.drawable.navara_photo, "Studio", false),
        ),
        skin = 0, homeTiles = "rpm,load,coolant,boost,iat,volt",
        homeHide = setOf("stft", "ltft", "catTemp"),
        obdHide = setOf("stft", "ltft", "catTemp", "lambda"),
        obdAdd = emptySet(),
        skipPids = setOf("06", "07", "3C", "44"),
        rpmMax = 6000, rpmRed = 4500, barMax = 5000,
    )

    private val ACCORD = VehicleProfile(
        id = "honda_accord_2015_th", brand = "Honda", model = "Accord", year = 2015, variant = "2.4 (Thái Lan)",
        engine = "Xăng 2.4L i-VTEC hút khí tự nhiên (K24W)", fuel = Fuel.PETROL, turbo = 0,
        gearbox = "Tự động 5AT",
        // Tỉ số hộp số 5AT và cầu cuối là ƯỚC TÍNH (chưa có tài liệu hãng); lốp 215/55R17 chu vi khoảng 2.09 m.
        // Nếu số hiển thị sai, dùng Cài đặt > Hiệu chỉnh cấp số hoặc sửa bảng này cho khớp xe thật.
        gearRatios = doubleArrayOf(2.706, 1.552, 1.000, 0.756, 0.600),
        finalDrive = 4.357, tireM = 2.09, ratiosVerified = false, sedan = true,
        photos = listOf(
            CarPhoto(R.drawable.accord_night, "Đêm khói", true),
            CarPhoto(R.drawable.accord_desert, "Sa mạc", true),
            CarPhoto(R.drawable.accord_mud, "Núi bùn", true),
            CarPhoto(R.drawable.accord_photo, "Studio", false),
            CarPhoto(R.drawable.hud_car, "Drift (HUD)", false),
        ),
        skin = 5, homeTiles = "rpm,load,coolant,map,iat,volt",
        homeHide = setOf("boost", "egt", "rail"),
        obdHide = setOf("boost", "egt", "rail", "torque", "torqueNm", "power", "injTiming", "fuelPress"),
        obdAdd = setOf("stft", "ltft", "catTemp", "lambda", "throttle"),
        skipPids = setOf("78", "23", "5D", "62", "61", "8E", "63"),
        rpmMax = 8000, rpmRed = 6700, barMax = 7000,
    )

    private val GENERIC_PETROL = VehicleProfile(
        id = "generic_petrol", brand = "Xe xăng", model = "(chung)", year = 0, variant = "",
        engine = "Động cơ xăng, đọc theo PID chuẩn", fuel = Fuel.PETROL, turbo = 0,
        gearbox = "Không ước lượng cấp số",
        gearRatios = doubleArrayOf(), finalDrive = 0.0, tireM = 2.0, ratiosVerified = true, sedan = true, photos = emptyList(),
        skin = 0, homeTiles = "rpm,load,coolant,map,iat,volt",
        homeHide = setOf("boost", "egt", "rail"),
        obdHide = setOf("boost", "egt", "rail", "torque", "torqueNm", "power", "injTiming", "fuelPress"),
        obdAdd = setOf("stft", "ltft", "catTemp", "lambda", "throttle"),
        skipPids = setOf("78", "23", "5D", "62", "61", "8E", "63"),
        rpmMax = 8000, rpmRed = 6500, barMax = 7000,
    )

    private val GENERIC_DIESEL = VehicleProfile(
        id = "generic_diesel", brand = "Xe dầu", model = "(chung)", year = 0, variant = "",
        engine = "Động cơ diesel, đọc theo PID chuẩn", fuel = Fuel.DIESEL, turbo = 1,
        gearbox = "Không ước lượng cấp số",
        gearRatios = doubleArrayOf(), finalDrive = 0.0, tireM = 2.3, ratiosVerified = true, sedan = false, photos = emptyList(),
        skin = 0, homeTiles = "rpm,load,coolant,boost,iat,volt",
        homeHide = setOf("stft", "ltft", "catTemp"),
        obdHide = setOf("stft", "ltft", "catTemp", "lambda"),
        obdAdd = emptySet(),
        skipPids = setOf("06", "07", "3C", "44"),
        rpmMax = 6000, rpmRed = 4500, barMax = 5000,
    )

    val all: List<VehicleProfile> = listOf(NAVARA, ACCORD, GENERIC_PETROL, GENERIC_DIESEL)

    // Hồ sơ đang dùng; Root cập nhật mỗi khi đổi trong Cài đặt để mọi trang đọc cùng một giá trị
    @Volatile var current: VehicleProfile = NAVARA

    fun byId(id: String): VehicleProfile = all.firstOrNull { it.id == id } ?: NAVARA
}
