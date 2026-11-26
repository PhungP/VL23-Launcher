package vn.navara.launcher

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// ===== Mô hình dữ liệu hồ sơ xe (thuần Kotlin, không phụ thuộc Android UI) =====
// ProfileData là dạng lưu trữ / nhập / xuất. Hồ sơ dựng sẵn trong Vehicles.kt cũng chuyển được sang dạng này (để nhân bản, xuất),
// và hồ sơ người dùng chuyển ngược lại thành VehicleProfile để ứng dụng dùng (ProfileBridge.kt).

const val PROFILE_SCHEMA = 1
const val MAX_IMPORT_BYTES = 512 * 1024
const val MAX_PROFILES = 100

val FUELS = listOf("PETROL", "DIESEL")
val VEHICLE_TYPES = listOf("SEDAN", "PICKUP", "SUV", "HATCHBACK", "OTHER")
val DRIVETRAINS = listOf("", "FWD", "RWD", "AWD", "4WD")
val SKINS = listOf(0, 5, 6, 7)   // 0 Đồng hồ, 5 HUD SU-35, 6 HUD Orange, 7 HUD Cyber

/** Ngưỡng cảnh báo riêng của xe; null = dùng cài đặt chung. */
data class AlertOverride(
    val coolantWarn: Float? = null, val coolantCrit: Float? = null, val voltLow: Float? = null, val rpmMax: Float? = null,
    val boostMax: Float? = null, val tpmsLowBar: Float? = null, val tpmsHighBar: Float? = null,
) {
    val isEmpty: Boolean get() = listOf(coolantWarn, coolantCrit, voltLow, rpmMax, boostMax, tpmsLowBar, tpmsHighBar).all { it == null }
}

data class ProfileData(
    val id: String,
    val builtin: Boolean = false,
    val nickname: String = "",
    val brand: String,
    val model: String,
    val year: Int = 0,
    val variant: String = "",
    val engine: String = "",
    val displacementL: Double = 0.0,
    val fuel: String = "PETROL",
    val turbo: Int = 0,
    val gearbox: String = "",
    val drivetrain: String = "",
    val vehicleType: String = "SEDAN",
    val gearRatios: List<Double> = emptyList(),
    val finalDrive: Double = 0.0,
    val tireM: Double = 2.0,
    val wheelSize: String = "",
    val ratiosVerified: Boolean = true,
    val redline: Int = 6500,
    val rpmMax: Int = 8000,
    val idleRpm: Int = 800,
    val barMax: Int = 7000,
    val maxSpeedKmh: Int = 0,
    val fuelTankL: Double = 0.0,
    val obdProtocol: Int = 0,                       // 0 = tự động (ATSP0), 1..9 = giao thức ELM327 cố định
    val priorityPids: List<String> = emptyList(),   // PID ưu tiên hỏi thường xuyên hơn
    val skipPids: List<String> = emptyList(),       // PID không hỏi xe
    val skin: Int = 0,
    val homeTiles: String = "rpm,load,coolant,map,iat,volt",
    val homeHide: List<String> = emptyList(),
    val obdHide: List<String> = emptyList(),
    val obdAdd: List<String> = emptyList(),
    val alerts: AlertOverride = AlertOverride(),
) {
    val displayName: String get() = nickname.ifBlank { listOf(brand, model, if (year > 0) year.toString() else "", variant).filter { it.isNotBlank() }.joinToString(" ") }
}

object ProfileValidation {
    private val KEY = Regex("^[A-Za-z0-9_]{1,24}$")
    private val PID = Regex("^[0-9A-Fa-f]{2}$")

    /** Trả về danh sách lỗi (tiếng Việt); rỗng = hợp lệ. */
    fun validate(d: ProfileData): List<String> {
        val e = ArrayList<String>()
        fun text(label: String, v: String, max: Int = 60) { if (v.length > max) e.add("$label dài quá $max ký tự") }
        if (d.brand.isBlank() && d.nickname.isBlank()) e.add("Cần nhập hãng xe hoặc tên gọi")
        if (d.model.isBlank() && d.nickname.isBlank()) e.add("Cần nhập dòng xe (model) hoặc tên gọi")
        text("Tên gọi", d.nickname); text("Hãng", d.brand); text("Model", d.model); text("Phiên bản", d.variant)
        text("Động cơ", d.engine, 100); text("Hộp số", d.gearbox, 100); text("Cỡ mâm", d.wheelSize, 30)
        if (d.id.isBlank() || d.id.length > 64 || !d.id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) e.add("Mã hồ sơ không hợp lệ")
        if (d.year != 0 && d.year !in 1950..2100) e.add("Năm sản xuất phải từ 1950 đến 2100")
        if (d.displacementL != 0.0 && d.displacementL !in 0.2..10.0) e.add("Dung tích động cơ phải từ 0,2 đến 10 lít")
        if (d.fuel !in FUELS) e.add("Loại nhiên liệu không hợp lệ")
        if (d.turbo !in 0..2) e.add("Số turbo phải là 0, 1 hoặc 2")
        if (d.vehicleType !in VEHICLE_TYPES) e.add("Loại xe không hợp lệ")
        if (d.drivetrain !in DRIVETRAINS) e.add("Hệ dẫn động không hợp lệ")
        if (d.tireM !in 1.2..3.5) e.add("Chu vi lốp phải từ 1,2 đến 3,5 mét")
        if (d.finalDrive != 0.0 && d.finalDrive !in 1.0..8.0) e.add("Tỉ số cầu cuối phải từ 1 đến 8")
        if (d.gearRatios.size > 10) e.add("Tối đa 10 cấp số")
        if (d.gearRatios.any { it !in 0.3..8.0 }) e.add("Tỉ số hộp số phải từ 0,3 đến 8")
        if (d.gearRatios.zipWithNext().any { (a, b) -> b >= a }) e.add("Tỉ số hộp số phải giảm dần từ số 1 trở đi")
        if (d.gearRatios.isNotEmpty() && d.finalDrive <= 0.0) e.add("Có tỉ số hộp số thì phải có tỉ số cầu cuối")
        if (d.redline !in 2000..12000) e.add("Vạch đỏ vòng tua phải từ 2000 đến 12000")
        if (d.rpmMax !in 2000..12000 || d.rpmMax < d.redline) e.add("Thang vòng tua phải từ 2000 đến 12000 và không nhỏ hơn vạch đỏ")
        if (d.idleRpm !in 400..1500) e.add("Vòng tua không tải phải từ 400 đến 1500")
        if (d.barMax !in 1000..12000) e.add("Thang dải đèn vòng tua phải từ 1000 đến 12000")
        if (d.maxSpeedKmh != 0 && d.maxSpeedKmh !in 80..400) e.add("Tốc độ tối đa phải từ 80 đến 400 km/h")
        if (d.fuelTankL != 0.0 && d.fuelTankL !in 5.0..300.0) e.add("Dung tích bình nhiên liệu phải từ 5 đến 300 lít")
        if (d.obdProtocol !in 0..9) e.add("Giao thức OBD phải từ 0 (tự động) đến 9")
        if ((d.priorityPids + d.skipPids).any { !PID.matches(it) }) e.add("PID phải gồm 2 ký tự hex, ví dụ 0C")
        if (d.priorityPids.size > 6) e.add("Tối đa 6 PID ưu tiên")
        if (d.priorityPids.any { it.uppercase() in d.skipPids.map { s -> s.uppercase() } }) e.add("Một PID không thể vừa ưu tiên vừa bị bỏ qua")
        if (d.priorityPids.any { it.uppercase() in setOf("0C", "0D") }) e.add("Vòng tua (0C) và tốc độ (0D) luôn được hỏi mỗi vòng, không cần đưa vào PID ưu tiên")
        if (d.skin !in SKINS) e.add("Bộ giao diện không hợp lệ")
        if (d.homeTiles.length > 200 || !d.homeTiles.split(",").all { it.isEmpty() || KEY.matches(it) }) e.add("Danh sách ô thông số trang 1 không hợp lệ")
        for ((label, l) in listOf("Ô ẩn trang 1" to d.homeHide, "Thông số ẩn ở trang OBD" to d.obdHide, "Thông số thêm ở trang OBD" to d.obdAdd)) {
            if (l.size > 60 || l.any { !KEY.matches(it) }) e.add("$label không hợp lệ")
        }
        val a = d.alerts
        a.coolantWarn?.let { if (it !in 80f..130f) e.add("Ngưỡng nước làm mát cảnh báo phải từ 80 đến 130°C") }
        a.coolantCrit?.let { if (it !in 85f..140f) e.add("Ngưỡng nước làm mát nghiêm trọng phải từ 85 đến 140°C") }
        if (a.coolantWarn != null && a.coolantCrit != null && a.coolantCrit <= a.coolantWarn) e.add("Ngưỡng nghiêm trọng phải lớn hơn ngưỡng cảnh báo")
        a.voltLow?.let { if (it !in 9f..14f) e.add("Ngưỡng điện áp thấp phải từ 9 đến 14 V") }
        a.rpmMax?.let { if (it !in 1500f..12000f) e.add("Ngưỡng vòng tua quá cao phải từ 1500 đến 12000") }
        a.boostMax?.let { if (it !in 0.3f..5f) e.add("Ngưỡng tăng áp phải từ 0,3 đến 5 bar") }
        a.tpmsLowBar?.let { if (it !in 0.8f..3.5f) e.add("Ngưỡng áp suất lốp thấp phải từ 0,8 đến 3,5 bar") }
        a.tpmsHighBar?.let { if (it !in 1.5f..6f) e.add("Ngưỡng áp suất lốp cao phải từ 1,5 đến 6 bar") }
        if (a.tpmsLowBar != null && a.tpmsHighBar != null && a.tpmsHighBar <= a.tpmsLowBar) e.add("Ngưỡng lốp cao phải lớn hơn ngưỡng lốp thấp")
        return e
    }
}

/** Áp ngưỡng riêng của xe lên cấu hình cảnh báo chung (chỉ các trường có giá trị). */
fun AlertConfig.withOverrides(o: AlertOverride): AlertConfig = copy(
    coolantWarn = o.coolantWarn ?: coolantWarn, coolantCrit = o.coolantCrit ?: coolantCrit, voltLow = o.voltLow ?: voltLow,
    rpmMax = o.rpmMax ?: rpmMax, boostMax = o.boostMax ?: boostMax, tpmsLowBar = o.tpmsLowBar ?: tpmsLowBar, tpmsHighBar = o.tpmsHighBar ?: tpmsHighBar,
)

// ---------- JSON ----------

class Parsed(val data: ProfileData?, val errors: List<String>)

class Envelope(val kind: String, val schema: Int, val activeId: String?, val items: List<Parsed>, val fatal: String?)

object ProfileJson {
    fun toJson(d: ProfileData): JSONObject = JSONObject().apply {
        put("id", d.id); put("nickname", d.nickname); put("brand", d.brand); put("model", d.model); put("year", d.year); put("variant", d.variant)
        put("engine", d.engine); put("displacementL", d.displacementL); put("fuel", d.fuel); put("turbo", d.turbo)
        put("gearbox", d.gearbox); put("drivetrain", d.drivetrain); put("vehicleType", d.vehicleType)
        put("gearRatios", JSONArray(d.gearRatios)); put("finalDrive", d.finalDrive); put("tireM", d.tireM); put("wheelSize", d.wheelSize)
        put("ratiosVerified", d.ratiosVerified)
        put("redline", d.redline); put("rpmMax", d.rpmMax); put("idleRpm", d.idleRpm); put("barMax", d.barMax)
        put("maxSpeedKmh", d.maxSpeedKmh); put("fuelTankL", d.fuelTankL)
        put("obdProtocol", d.obdProtocol); put("priorityPids", JSONArray(d.priorityPids)); put("skipPids", JSONArray(d.skipPids))
        put("skin", d.skin); put("homeTiles", d.homeTiles)
        put("homeHide", JSONArray(d.homeHide)); put("obdHide", JSONArray(d.obdHide)); put("obdAdd", JSONArray(d.obdAdd))
        if (!d.alerts.isEmpty) put("alerts", JSONObject().apply {
            d.alerts.coolantWarn?.let { put("coolantWarn", it.toDouble()) }; d.alerts.coolantCrit?.let { put("coolantCrit", it.toDouble()) }
            d.alerts.voltLow?.let { put("voltLow", it.toDouble()) }; d.alerts.rpmMax?.let { put("rpmMax", it.toDouble()) }
            d.alerts.boostMax?.let { put("boostMax", it.toDouble()) }; d.alerts.tpmsLowBar?.let { put("tpmsLowBar", it.toDouble()) }
            d.alerts.tpmsHighBar?.let { put("tpmsHighBar", it.toDouble()) }
        })
    }

    fun envelope(kind: String, profiles: List<ProfileData>, activeId: String? = null, appVersion: String = "", nowMs: Long = System.currentTimeMillis()): String =
        JSONObject().apply {
            put("schema", PROFILE_SCHEMA); put("kind", kind); put("app", "VL23 Launcher"); put("appVersion", appVersion); put("exportedAt", nowMs)
            if (activeId != null) put("activeId", activeId)
            put("profiles", JSONArray().also { a -> profiles.forEach { a.put(toJson(it)) } })
        }.toString(2)

    // Đọc từng trường có kiểm tra kiểu; sai kiểu thì ghi lỗi chứ không ép kiểu ngầm. Khoá lạ bị bỏ qua (không bao giờ được thực thi).
    private class R(val o: JSONObject, val errors: MutableList<String>) {
        fun raw(k: String): Any? = o.opt(k).takeIf { it != null && it != JSONObject.NULL }
        fun str(k: String, def: String = ""): String = when (val v = raw(k)) { null -> def; is String -> v; else -> { errors.add("\"$k\" phải là chuỗi"); def } }
        fun int(k: String, def: Int): Int = when (val v = raw(k)) {
            null -> def
            is Int -> v; is Long -> v.toInt()
            is Number -> if (v.toDouble() == Math.floor(v.toDouble())) v.toInt() else { errors.add("\"$k\" phải là số nguyên"); def }
            else -> { errors.add("\"$k\" phải là số"); def }
        }
        fun dbl(k: String, def: Double): Double = when (val v = raw(k)) { null -> def; is Number -> v.toDouble(); else -> { errors.add("\"$k\" phải là số"); def } }
        fun bool(k: String, def: Boolean): Boolean = when (val v = raw(k)) { null -> def; is Boolean -> v; else -> { errors.add("\"$k\" phải là true/false"); def } }
        fun strs(k: String): List<String> = when (val v = raw(k)) {
            null -> emptyList()
            is JSONArray -> (0 until v.length()).mapNotNull { i -> (v.opt(i) as? String) ?: run { errors.add("\"$k\" chỉ chứa chuỗi"); null } }
            else -> { errors.add("\"$k\" phải là danh sách"); emptyList() }
        }
        fun dbls(k: String): List<Double> = when (val v = raw(k)) {
            null -> emptyList()
            is JSONArray -> (0 until v.length()).mapNotNull { i -> (v.opt(i) as? Number)?.toDouble() ?: run { errors.add("\"$k\" chỉ chứa số"); null } }
            else -> { errors.add("\"$k\" phải là danh sách số"); emptyList() }
        }
        fun optF(a: JSONObject, k: String): Float? = when (val v = a.opt(k)) { null, JSONObject.NULL -> null; is Number -> v.toFloat(); else -> { errors.add("alerts.$k phải là số"); null } }
    }

    fun fromJson(o: JSONObject, newId: () -> String): Parsed {
        val errs = ArrayList<String>()
        val r = R(o, errs)
        val idIn = r.str("id")
        val a = o.optJSONObject("alerts")
        val d = ProfileData(
            id = idIn.ifBlank { newId() }, builtin = false,
            nickname = r.str("nickname"), brand = r.str("brand"), model = r.str("model"), year = r.int("year", 0), variant = r.str("variant"),
            engine = r.str("engine"), displacementL = r.dbl("displacementL", 0.0), fuel = r.str("fuel", "PETROL"), turbo = r.int("turbo", 0),
            gearbox = r.str("gearbox"), drivetrain = r.str("drivetrain"), vehicleType = r.str("vehicleType", "SEDAN"),
            gearRatios = r.dbls("gearRatios"), finalDrive = r.dbl("finalDrive", 0.0), tireM = r.dbl("tireM", 2.0), wheelSize = r.str("wheelSize"),
            ratiosVerified = r.bool("ratiosVerified", true),
            redline = r.int("redline", 6500), rpmMax = r.int("rpmMax", 8000), idleRpm = r.int("idleRpm", 800), barMax = r.int("barMax", 7000),
            maxSpeedKmh = r.int("maxSpeedKmh", 0), fuelTankL = r.dbl("fuelTankL", 0.0),
            obdProtocol = r.int("obdProtocol", 0), priorityPids = r.strs("priorityPids").map { it.uppercase() }, skipPids = r.strs("skipPids").map { it.uppercase() },
            skin = r.int("skin", 0), homeTiles = r.str("homeTiles", "rpm,load,coolant,map,iat,volt"),
            homeHide = r.strs("homeHide"), obdHide = r.strs("obdHide"), obdAdd = r.strs("obdAdd"),
            alerts = if (a == null) AlertOverride() else AlertOverride(r.optF(a, "coolantWarn"), r.optF(a, "coolantCrit"), r.optF(a, "voltLow"), r.optF(a, "rpmMax"), r.optF(a, "boostMax"), r.optF(a, "tpmsLowBar"), r.optF(a, "tpmsHighBar")),
        )
        if (errs.isNotEmpty()) return Parsed(null, errs)
        val v = ProfileValidation.validate(d)
        return Parsed(if (v.isEmpty()) d else null, v)
    }

    /** Đọc tệp JSON: gói chuẩn (schema 1), hoặc dạng cũ/viết tay không có schema (schema 0: mảng hồ sơ hoặc một hồ sơ). Schema mới hơn thì từ chối. */
    fun parseEnvelope(text: String, newId: () -> String): Envelope {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) return Envelope("", 0, null, emptyList(), "Tệp quá lớn (tối đa ${MAX_IMPORT_BYTES / 1024} KB)")
        val t = text.trim().removePrefix("﻿")
        try {
            val arr: JSONArray; var schema = 0; var kind = ""; var active: String? = null
            when {
                t.startsWith("[") -> arr = JSONArray(t)
                t.startsWith("{") -> {
                    val o = JSONObject(t)
                    if (o.has("schema")) {
                        val s = o.opt("schema")
                        if (s !is Number) return Envelope("", 0, null, emptyList(), "Trường schema phải là số")
                        schema = s.toInt()
                    }
                    if (schema > PROFILE_SCHEMA) return Envelope("", schema, null, emptyList(), "Tệp tạo bởi phiên bản mới hơn (schema $schema, ứng dụng hỗ trợ tới $PROFILE_SCHEMA). Hãy cập nhật ứng dụng.")
                    if (schema < 0) return Envelope("", schema, null, emptyList(), "Schema không hợp lệ")
                    kind = o.optString("kind", ""); active = o.optString("activeId", "").ifBlank { null }
                    arr = o.optJSONArray("profiles") ?: if (o.has("brand") || o.has("model") || o.has("nickname")) JSONArray().put(o) else return Envelope(kind, schema, null, emptyList(), "Tệp không có danh sách hồ sơ")
                }
                else -> return Envelope("", 0, null, emptyList(), "Không phải tệp JSON")
            }
            if (arr.length() > MAX_PROFILES) return Envelope(kind, schema, active, emptyList(), "Quá nhiều hồ sơ (tối đa $MAX_PROFILES)")
            val items = (0 until arr.length()).map { i ->
                val o = arr.optJSONObject(i)
                if (o == null) Parsed(null, listOf("Mục ${i + 1} không phải đối tượng")) else fromJson(o, newId)
            }
            return Envelope(kind, schema, active, items, null)
        } catch (e: JSONException) {
            return Envelope("", 0, null, emptyList(), "Tệp JSON bị lỗi: " + (e.message ?: "không đọc được").take(120))
        }
    }
}
