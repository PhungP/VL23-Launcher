package vn.navara.launcher

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

// Các trang của launcher (id) và thứ tự mặc định
val PAGE_IDS = listOf("home", "map", "music", "telemetry", "apps")

// Chuỗi thứ tự đã lưu -> danh sách đủ 5 trang, mỗi trang đúng một lần (thiếu thì bổ sung cuối, lạ thì bỏ)
fun parsePageOrder(s: String): List<String> {
    val got = s.split(",").map { it.trim() }.filter { it in PAGE_IDS }.distinct()
    return got + PAGE_IDS.filter { it !in got }
}

fun pageName(id: String): String = when (id) {
    "home" -> "Trang chủ (OBD)"
    "telemetry" -> "Thông số OBD (xe đua)"
    "apps" -> "Ứng dụng"
    "map" -> "Bản đồ"
    else -> "Nhạc"
}

data class UiPrefs(
    val dark: Boolean = true,
    val accent: Int = 0xFFFF8A1F.toInt(),
    val accent2: Int = 0xFF2F7BFF.toInt(),
    val iconSize: Int = 1,   // 0..3: nhỏ, vừa, lớn, rất lớn
    val textSize: Int = 1,   // 0..3: nhỏ, vừa, lớn, rất lớn
    val bgStyle: Int = 0,    // 0 trơn, 1 đường đồng mức, 2 chuyển màu, 3 ảnh
    val cardStyle: Int = 0,  // 0 đặc, 1 viền mỏng, 2 kính mờ
    val dim: Int = 1,        // độ tối của ảnh nền 0..2
    val obdSource: Int = 0,  // 0 kết nối trực tiếp, 1 Torque Pro
    val hideBar: Boolean = true,
    val whiteIcons: Boolean = true,
    val navaraIcons: Boolean = true,    // biểu tượng ứng dụng đồng nhất kiểu Navara (trắng/cam/xanh)
    val titleBar: Boolean = false,
    val autoStart: Boolean = true,
    val trafficAlert: Boolean = true,   // cảnh báo giới hạn tốc độ / camera
    val alertSound: Boolean = true,     // âm báo khi cảnh báo
    val pageOrder: String = "home,map,music,telemetry,apps",  // thứ tự các trang khi vuốt
    val obdTiles: String = "",         // thông số hiển thị ở trang theo dõi OBD: rỗng = mặc định, "-" = không hiện gì, hoặc danh sách khoá
    val loadSource: Int = 0,            // nguồn thanh engine load: 0 tự động, 1 tính toán, 2 chân ga, 3 mô-men
    val mapEngine: Int = 0,             // công cụ bản đồ: 0 MapLibre + MapTiler (cần khoá), 1 OSM đơn giản, 3 MapLibre + Goong (cần khoá); số 2 (Google Maps) đã bỏ
    val mapTilerKey: String = "",       // khoá API MapTiler nhập trong Cài đặt (nếu trống dùng khoá khai báo lúc build)
    val gearTrim: Int = 2,              // hiệu chỉnh cấp số: 0..4 = -6%, -3%, 0, +3%, +6%
    val mapMarker: Int = 1,             // biểu tượng xe trên bản đồ: 0 xe bán tải, 1 tam giác (mặc định)
    val mapHeadingUp: Boolean = true,   // bản đồ xoay theo hướng xe (xe luôn hướng lên), mặc định bật
    val mapStyle: Int = 0,              // bản đồ trang 3: 0 theo giao diện, 1 luôn sáng, 2 luôn tối
    val skin: Int = 0,                  // bộ giao diện cho mọi trang: 0 đồng hồ xe, 3 robot, 4 SciFi (1, 2 đã bỏ)
    val mapTheme: Int = 0,              // kiểu bản đồ MapTiler: 0 Streets, 1 OpenStreetMap, 2 Bright
    val mapTilt: Boolean = false,       // nghiêng bản đồ 3D khi xoay theo hướng xe (MapLibre)
    val carImg: Int = 0,                // hình xe ở trang 1: 0 ảnh Navara, 1 ảnh riêng, 2 khung dây, 3 bóng xe
    val carVer: Int = 0,                // tăng mỗi khi chọn ảnh xe riêng mới (để nạp lại ảnh)
    val gearStyle: Int = 0,             // cách hiện cấp số ở trang 1: 0 số thể thao, 1 ô cấp số
    val homeTiles: String = "rpm,load,coolant,boost,iat,volt",   // các ô thông số ở trang 1 (nửa đầu cột trái từ trên xuống, nửa sau cột phải); thiếu thì lấy theo mặc định
    val homeText: Int = 0,              // màu chữ ở trang 1: 0 theo giao diện, 1..8 theo bảng HOME_COLORS
    val homeLine: Int = 0,              // màu viền các hộp ở trang 1
    val homeGear: Int = 0,              // màu số cấp ở trang 1
    val carPhoto: Int = 0,              // ảnh đang chọn trong danh sách ảnh của hồ sơ xe
    val goongMapKey: String = "",       // khoá Maptiles của Goong (vẽ bản đồ) do người dùng nhập
    val goongApiKey: String = "",  // khoá API của Goong do người dùng nhập: tìm địa chỉ (Geocoding), và chỉ đường nếu bật goongRoute
    val goongRoute: Boolean = false,    // chỉ đường bằng Goong Direction (tắt = dùng OSRM, không tốn hạn mức Goong)
    val vehicle: String = "nissan_navara_2023_vl",   // hồ sơ xe đang chọn (Vehicles.kt)
    val glass: Int = 3,                 // độ trong suốt của khung/ô: 0 đặc ... 4 trong suốt hoàn toàn
    val frameShape: Int = 0,            // hình khung: 0 vát góc, 1 bo tròn, 2 lục giác, 3 vuông
    val homeOffsets: String = "",       // vị trí đã kéo của các ô ở trang 1: "id=dx,dy;..." (dp)
    val iconBlue: Boolean = false,      // biểu tượng ứng dụng: xanh kết hợp trắng theo tông đồng hồ xe
    val navVoice: Boolean = true,       // đọc chỉ dẫn dẫn đường bằng giọng nói
    val homeLayout: Int = 0,            // bố cục trang 1 (bộ Đồng hồ xe): 0 bố cục mới (ảnh xe + 6 ô), 1 thể thao (đồng hồ vòng tua dạng cung)
    val tileBorder: Int = 2,            // độ dày viền ô thông số (trang 1 và trang OBD): 0 không viền, 1 mảnh, 2 vừa (như các bản trước), 3 dày, 4 rất dày
    val obdStyle: Int = 0,              // kiểu hiển thị thông số ở trang OBD: 0 thẻ (số và thanh nhỏ), 1..6 = số, kim, cung, thanh, số điện tử, đồ thị
    val obdCols: Int = 4,               // số cột thông số ở trang OBD (3..6)
    val homeCount: Int = 6,             // số ô thông số ở trang 1 (2..10), chia hai cột trái và phải
    val homeThick: Int = 3,             // độ dày ô: 0 rất mỏng, 1 mỏng, 2 vừa, 3 dày (đầy hàng, như các bản trước)
    val homeStyle: Int = 0,             // kiểu hiển thị mặc định của ô: 0 số, 1 đồng hồ kim, 2 đồng hồ cung, 3 thanh, 4 số điện tử, 5 đồ thị
    val homeStyles: String = "",        // kiểu riêng từng ô: "t0=1;t3=4"
    val homeSizes: String = "",         // kích thước riêng từng ô (hệ số rộng, cao so với ô gốc): "t0=1.2,0.9"
    val uiScale: Int = 0,               // tỉ lệ giao diện: 0 tự khớp màn hình (100%), 1..5 = 80%, 90%, 110%, 125%, 140%
    val tpmsOn: Boolean = false,        // đọc cảm biến áp suất lốp (TPMS) mua ngoài qua Bluetooth
    val tpmsUnit: Int = 0,              // đơn vị áp suất lốp: 0 bar, 1 psi, 2 kPa
    val tpmsMap: String = "",           // gán cảm biến cho bánh xe: "FL=AA:BB:CC:DD:EE:FF;FR=..." (FL trước trái, FR trước phải, RL sau trái, RR sau phải)
    val tpmsAbs: Boolean = true,        // cảm biến báo áp suất tuyệt đối: trừ áp khí quyển để ra áp suất lốp
    val tpmsTrim: Int = 0,              // hiệu chỉnh áp suất lốp, đơn vị 0,01 bar (âm hoặc dương)
    val tpmsLow: Int = 20,              // ngưỡng áp suất thấp, đơn vị 0,1 bar
    val tpmsHigh: Int = 32,             // ngưỡng áp suất cao, đơn vị 0,1 bar
)

fun loadPrefs(c: Context): UiPrefs {
    val s = c.getSharedPreferences("ui", Context.MODE_PRIVATE)
    if (s.getInt("ver", 0) < 2) {
        // Bản 0.3: biểu tượng xe mặc định là tam giác và bản đồ mặc định xoay theo hướng xe. Áp dụng một lần cho cả máy đã cài bản cũ.
        s.edit().putInt("mapMarker", 1).putBoolean("mapHeadingUp", true).putInt("ver", 2).apply()
    }
    val d = UiPrefs()
    return UiPrefs(
        dark = s.getBoolean("dark", d.dark),
        accent = s.getInt("accent", d.accent),
        accent2 = s.getInt("accent2", d.accent2),
        iconSize = s.getInt("iconSize", d.iconSize),
        textSize = s.getInt("textSize", d.textSize),
        bgStyle = s.getInt("bgStyle", d.bgStyle),
        cardStyle = s.getInt("cardStyle", d.cardStyle),
        dim = s.getInt("dim", d.dim),
        obdSource = s.getInt("obdSource", d.obdSource),
        hideBar = s.getBoolean("hideBar", d.hideBar),
        whiteIcons = s.getBoolean("whiteIcons", d.whiteIcons),
        navaraIcons = s.getBoolean("navaraIcons", d.navaraIcons),
        titleBar = s.getBoolean("titleBar", d.titleBar),
        autoStart = s.getBoolean("autoStart", d.autoStart),
        trafficAlert = s.getBoolean("trafficAlert", d.trafficAlert),
        alertSound = s.getBoolean("alertSound", d.alertSound),
        mapStyle = s.getInt("mapStyle", d.mapStyle),
        mapHeadingUp = s.getBoolean("mapHeadingUp", d.mapHeadingUp),
        pageOrder = (s.getString("pageOrder", null) ?: d.pageOrder).let { if (it == "home,telemetry,apps,map,music" || it == "home,apps,map,music,telemetry") d.pageOrder else it },  // thứ tự mặc định cũ -> mặc định mới
        loadSource = s.getInt("loadSource", d.loadSource).coerceIn(0, 3),
        mapEngine = s.getInt("mapEngine", d.mapEngine).let { if (it == 2) 0 else it.coerceIn(0, 3) },   // 2 = Google Maps đã bỏ từ 0.20, quy về MapLibre + MapTiler
        mapTilerKey = s.getString("mapTilerKey", d.mapTilerKey) ?: d.mapTilerKey,
        gearTrim = s.getInt("gearTrim", d.gearTrim).coerceIn(0, 4),
        obdTiles = s.getString("obdTiles", d.obdTiles) ?: d.obdTiles,
        mapMarker = if (s.getInt("mapMarker", d.mapMarker) == 1) 1 else 0,  // giá trị cũ (đã bỏ chấm tròn) quy về xe bán tải
        skin = s.getInt("skin", d.skin).let { if (it == 1 || it == 2) 0 else it.coerceIn(0, 4) },
        mapTheme = s.getInt("mapTheme", d.mapTheme).coerceIn(0, 2),
        mapTilt = s.getBoolean("mapTilt", d.mapTilt),
        homeLayout = s.getInt("homeLayout", d.homeLayout).coerceIn(0, 1),
        homeCount = s.getInt("homeCount", d.homeCount).coerceIn(2, 10),
        tileBorder = s.getInt("tileBorder", d.tileBorder).coerceIn(0, 4),
        obdStyle = s.getInt("obdStyle", d.obdStyle).coerceIn(0, 6),
        obdCols = s.getInt("obdCols", d.obdCols).coerceIn(3, 6),
        homeThick = s.getInt("homeThick", d.homeThick).coerceIn(0, 3),
        homeStyle = s.getInt("homeStyle", d.homeStyle).coerceIn(0, 5),
        homeStyles = s.getString("homeStyles", d.homeStyles) ?: d.homeStyles,
        homeSizes = s.getString("homeSizes", d.homeSizes) ?: d.homeSizes,
        uiScale = s.getInt("uiScale", d.uiScale).coerceIn(0, 5),
        tpmsOn = s.getBoolean("tpmsOn", d.tpmsOn),
        tpmsUnit = s.getInt("tpmsUnit", d.tpmsUnit).coerceIn(0, 2),
        tpmsMap = s.getString("tpmsMap", d.tpmsMap) ?: d.tpmsMap,
        tpmsAbs = s.getBoolean("tpmsAbs", d.tpmsAbs),
        tpmsTrim = s.getInt("tpmsTrim", d.tpmsTrim).coerceIn(-60, 60),
        tpmsLow = s.getInt("tpmsLow", d.tpmsLow).coerceIn(5, 40),
        tpmsHigh = s.getInt("tpmsHigh", d.tpmsHigh).coerceIn(10, 60),
        navVoice = s.getBoolean("navVoice", d.navVoice),
        iconBlue = s.getBoolean("iconBlue", d.iconBlue),
        glass = s.getInt("glass", d.glass).coerceIn(0, 4),
        vehicle = s.getString("vehicle", d.vehicle) ?: d.vehicle,
        carPhoto = s.getInt("carPhoto", d.carPhoto).coerceAtLeast(0),
        homeText = s.getInt("homeText", d.homeText).coerceIn(0, HOME_COLORS.size),
        homeLine = s.getInt("homeLine", d.homeLine).coerceIn(0, HOME_COLORS.size),
        homeGear = s.getInt("homeGear", d.homeGear).coerceIn(0, HOME_COLORS.size),
        goongMapKey = s.getString("goongMapKey", d.goongMapKey) ?: d.goongMapKey,
        goongApiKey = s.getString("goongApiKey", d.goongApiKey) ?: d.goongApiKey,
        goongRoute = s.getBoolean("goongRoute", d.goongRoute),
        frameShape = s.getInt("frameShape", d.frameShape).coerceIn(0, 3),
        homeOffsets = s.getString("homeOffsets", d.homeOffsets) ?: d.homeOffsets,
        carImg = s.getInt("carImg", d.carImg).coerceIn(0, 3),
        carVer = s.getInt("carVer", d.carVer),
        gearStyle = s.getInt("gearMode", if (s.getInt("gearStyle", 0) == 3) 1 else 0).coerceIn(0, 1),
        homeTiles = s.getString("homeTiles", d.homeTiles) ?: d.homeTiles,
    )
}

fun savePrefs(c: Context, p: UiPrefs) {
    c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
        .putBoolean("dark", p.dark).putInt("accent", p.accent).putInt("accent2", p.accent2)
        .putInt("iconSize", p.iconSize).putInt("textSize", p.textSize).putInt("bgStyle", p.bgStyle)
        .putInt("cardStyle", p.cardStyle).putInt("dim", p.dim).putInt("obdSource", p.obdSource)
        .putBoolean("hideBar", p.hideBar).putBoolean("whiteIcons", p.whiteIcons).putBoolean("navaraIcons", p.navaraIcons)
        .putBoolean("titleBar", p.titleBar).putBoolean("autoStart", p.autoStart)
        .putBoolean("trafficAlert", p.trafficAlert).putBoolean("alertSound", p.alertSound).putInt("mapStyle", p.mapStyle).putBoolean("mapHeadingUp", p.mapHeadingUp).putInt("mapMarker", p.mapMarker)
        .putString("pageOrder", p.pageOrder).putInt("loadSource", p.loadSource).putString("obdTiles", p.obdTiles)
        .putInt("mapEngine", p.mapEngine).putString("mapTilerKey", p.mapTilerKey).putInt("gearTrim", p.gearTrim)
        .putInt("skin", p.skin).putInt("mapTheme", p.mapTheme).putBoolean("mapTilt", p.mapTilt).putInt("homeLayout", p.homeLayout).putBoolean("navVoice", p.navVoice).putBoolean("iconBlue", p.iconBlue).putString("vehicle", p.vehicle).putInt("carPhoto", p.carPhoto).putInt("homeText", p.homeText).putInt("homeLine", p.homeLine).putInt("homeGear", p.homeGear).putString("goongMapKey", p.goongMapKey).putString("goongApiKey", p.goongApiKey).putBoolean("goongRoute", p.goongRoute).putInt("glass", p.glass).putInt("frameShape", p.frameShape).putString("homeOffsets", p.homeOffsets)
        .putInt("carImg", p.carImg).putInt("carVer", p.carVer).putInt("gearMode", p.gearStyle).putString("homeTiles", p.homeTiles)
        .putInt("homeCount", p.homeCount).putInt("tileBorder", p.tileBorder).putInt("obdStyle", p.obdStyle).putInt("obdCols", p.obdCols).putInt("homeThick", p.homeThick).putInt("homeStyle", p.homeStyle)
        .putString("homeStyles", p.homeStyles).putString("homeSizes", p.homeSizes).putInt("uiScale", p.uiScale)
        .putBoolean("tpmsOn", p.tpmsOn).putInt("tpmsUnit", p.tpmsUnit).putString("tpmsMap", p.tpmsMap)
        .putBoolean("tpmsAbs", p.tpmsAbs).putInt("tpmsTrim", p.tpmsTrim).putInt("tpmsLow", p.tpmsLow).putInt("tpmsHigh", p.tpmsHigh).apply()
}

// Bảng màu chọn cho chữ, viền và số cấp ở trang 1 (chỉ số 0 = theo giao diện)
val HOME_COLORS = listOf(
    Color(0xFFFFFFFF), Color(0xFF38B6FF), Color(0xFF00E5FF), Color(0xFF39FF88),
    Color(0xFFFFD34D), Color(0xFFFF7A1A), Color(0xFFFF4D5E), Color(0xFFB26BFF)
)

// Bộ giao diện (p.skin): 0 = kiểu đồng hồ xe Navara (nền đen, dải xanh dương, chọn thì cam, viền xanh lá cho ô đặc biệt),
// 1 = điện tử mặt đen chữ trắng, 2 = điện tử mặt trắng chữ đen (mono != 0),
// 3 = Robot (kim loại xám, đinh tán, đèn hổ phách), 4 = SciFi (nền xanh đen, viền neon xanh lơ và tím hồng)
// Robot và SciFi dùng chung bố cục với bộ Đồng hồ xe (mono = 0) nhưng đổi toàn bộ bảng màu và kiểu khung.
class Theme(val p: UiPrefs, val bgImage: ImageBitmap?) {
    val kind = p.skin.coerceIn(0, 4)
    val mono = if (kind == 1 || kind == 2) kind else 0
    val bg = when (kind) { 2 -> Color.White; 3 -> Color(0xFF0B0D10); 4 -> Color(0xFF02040C); else -> Color.Black }
    val panel = when (kind) { 1 -> Color(0xFF121212); 2 -> Color(0xFFF0F0F0); 3 -> Color(0xFF161A20); 4 -> Color(0xFF050A1A); else -> Color(0xFF06091A) }
    val panelHi = when (kind) { 1 -> Color(0xFF242424); 2 -> Color(0xFFDDDDDD); 3 -> Color(0xFF2A313B); 4 -> Color(0xFF0E1A3A); else -> Color(0xFF1B2030) }
    val ink = when (kind) { 1 -> Color.White; 2 -> Color.Black; 3 -> Color(0xFFE6EAF0); 4 -> Color(0xFFE8FBFF); else -> Color(0xFFF4F6FA) }
    val muted = when (kind) { 1 -> Color(0xFF9A9A9A); 2 -> Color(0xFF555555); 3 -> Color(0xFF9AA4B2); 4 -> Color(0xFF7FB7D6); else -> Color(0xFFAAB3C5) }
    val accent = when (kind) { 1, 2 -> ink; 3 -> Color(0xFFFFB000); 4 -> Color(0xFF00E5FF); else -> Color(0xFFFF7A1A) }   // màu "đang chọn"
    val accent2 = when (kind) { 1, 2 -> ink; 3 -> Color(0xFF22D3EE); 4 -> Color(0xFFFF2BD6); else -> Color(0xFF2B5CFF) }
    val blue = when (kind) { 1, 2 -> ink.copy(alpha = 0.5f); 3 -> Color(0xFF8A94A6); 4 -> Color(0xFF00B8D4); else -> Color(0xFF2B5CFF) }
    val blueLight = when (kind) { 1, 2 -> ink.copy(alpha = 0.5f); 3 -> Color(0xFFC7D0DC); 4 -> Color(0xFF5CF2FF); else -> Color(0xFF38B6FF) }
    val blueDeep = when (kind) { 1, 2 -> ink.copy(alpha = 0.35f); 3 -> Color(0xFF4A5565); 4 -> Color(0xFF0A4DA8); else -> Color(0xFF1239D6) }
    val green = when (kind) { 1, 2 -> ink; 3 -> Color(0xFF4ADE80); 4 -> Color(0xFF39FF88); else -> Color(0xFF2DD36F) }   // viền ô đặc biệt
    val onAccent = if (accent.luminance() > 0.3f) Color(0xFF0A0A14) else Color.White
    val onAccent2 = if (accent2.luminance() > 0.45f) Color(0xFF0A0A14) else Color.White   // chữ trên nền màu phụ
    // Màu và kiểu khung dùng cho bộ có khung (mono = 0)
    val line = when (kind) { 3 -> Color(0xFF7B8696); 4 -> Color(0xFF12B5D0); else -> Color(0xFF2C4A8A) }                 // viền khung
    val fill = when (kind) { 3 -> listOf(Color(0xFF222831), Color(0xFF12161B)); 4 -> listOf(Color(0xFF04122B), Color(0xFF020816)); else -> listOf(Color(0xFF0B1A3D), Color(0xFF050D24)) }
    val label = when (kind) { 3 -> Color(0xFFB7C0CC); 4 -> Color(0xFF5CE1F5); else -> Color(0xFF7F9AD0) }                 // nhãn nhỏ
    val glow = when (kind) { 3 -> Color(0xFFFFB000); 4 -> Color(0xFF00E5FF); else -> Color(0xFF3D8BFF) }                  // màu số cấp đang cài
    val homeBg = when (kind) {
        3 -> listOf(Color(0xFF2A313B), Color(0xFF14181D), Color(0xFF07090C))
        4 -> listOf(Color(0xFF0A1F4D), Color(0xFF050C28), Color(0xFF010208))
        else -> listOf(Color(0xFF0E2C5C), Color(0xFF071634), Color(0xFF02050F))
    }
    val frameFill = when (kind) { 3 -> Color(0x73101419); 4 -> Color(0x66020A22); else -> Color(0x73050C20) }
    val frameLine = when (kind) { 3 -> Color(0xFF3B4452); 4 -> Color(0xFF0F6C8A); else -> Color(0xFF1F3560) }
    val cutScale = if (kind == 3) 1.5f else 1f          // Robot vát góc to hơn
    val rowHi = when (kind) { 3 -> listOf(Color(0xFF2A313B), Color(0xFF181C22)); 4 -> listOf(Color(0xFF062A52), Color(0xFF03122B)); else -> listOf(Color(0xFF0A1B5C), Color(0xFF050E33)) }
    val rowLo = when (kind) { 3 -> Color(0xFF0F1216); 4 -> Color(0xFF020716); else -> Color(0xFF030717) }
    // Khung trong suốt: nhân độ trong của màu nền khung theo p.glass để nhìn xuyên ra hình nền phía sau
    val pa = floatArrayOf(1f, 0.6f, 0.35f, 0.15f, 0f)[p.glass.coerceIn(0, 4)]
    val panelG = panel.copy(alpha = panel.alpha * pa)
    val fillG = fill.map { it.copy(alpha = it.alpha * pa) }
    val frameFillG = frameFill.copy(alpha = frameFill.alpha * pa)
    // Màu tuỳ chọn ở trang 1 (không chọn thì theo giao diện)
    private val hText: Color? = if (p.homeText in 1..HOME_COLORS.size) HOME_COLORS[p.homeText - 1] else null
    val hInk: Color = hText ?: ink
    val hMuted: Color = hText?.copy(alpha = 0.7f) ?: muted
    val hLabel: Color = hText?.copy(alpha = 0.8f) ?: label
    val hIcon: Color = hText ?: blueLight
    val homeLineC: Color? = if (p.homeLine in 1..HOME_COLORS.size) HOME_COLORS[p.homeLine - 1] else null
    val hGear: Color = if (p.homeGear in 1..HOME_COLORS.size) HOME_COLORS[p.homeGear - 1] else glow
    val pageBg = when (kind) { 3 -> listOf(Color(0xFF1B2027), Color(0xFF07090C)); 4 -> listOf(Color(0xFF061B45), Color(0xFF010208)); else -> listOf(Color(0xFF06103A), Color(0xFF01030C)) }
    val scale = floatArrayOf(0.95f, 1.1f, 1.25f, 1.4f)[p.textSize.coerceIn(0, 3)]
    val iconDp = intArrayOf(52, 60, 72, 84)[p.iconSize.coerceIn(0, 3)]
}

// Font chữ gọn kiểu đồng hồ xe: dùng họ chữ condensed có sẵn của Android (Roboto Condensed)
val ClusterFont = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

val LocalTheme = compositionLocalOf { Theme(UiPrefs(), null) }

fun loadBg(ctx: Context): ImageBitmap? = try {
    val f = File(ctx.filesDir, "bg.jpg")
    if (!f.exists()) null else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > 1600) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }
} catch (e: Exception) { null }
