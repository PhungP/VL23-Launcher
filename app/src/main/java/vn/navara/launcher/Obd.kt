package vn.navara.launcher

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

data class ObdData(
    val speed: Int = 0,
    val rpm: Int = 0,
    val coolant: Int? = null,
    val load: Int? = null,
    val intake: Int? = null,
    val volt: Float? = null,
    val boost: Float? = null,
    val ambient: Int? = null,
    val gear: Int? = null,        // số đang cài (1..7)
    val gearEst: Boolean = false, // true = suy ra từ vòng tua/tốc độ, không phải đọc trực tiếp từ xe
    val ex: Map<String, Float> = emptyMap(),   // mọi thông số mở rộng đọc được từ xe (khoá theo tên) cho trang theo dõi OBD
    val pidCount: Int = 0,                      // số PID mà xe báo là hỗ trợ
    val status: String = "Chưa kết nối OBD",
)

interface Transport {
    val label: String
    suspend fun request(cmd: String, timeoutMs: Long = 3000): String
    fun close()
}

private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// Tên adapter thường gặp (iCar/Vgate hay hiện là "Android-Vlink" hoặc "IOS-Vlink")
fun looksLikeObd(name: String?) =
    name != null && Regex("icar|vgate|obd|elm|v-link|vlink", RegexOption.IGNORE_CASE).containsMatchIn(name)

// Một thông số OBD mode 01: pid, tên khoá, số byte dữ liệu tối thiểu, công thức giải mã (SAE J1979)
private class Pid(val pid: String, val key: String, val n: Int, val f: (List<Int>) -> Float)

private fun u16(b: List<Int>) = b[0] * 256 + b[1]

private val PIDS = listOf(
    Pid("0C", "rpm", 2) { u16(it) / 4f },
    Pid("0D", "speed", 1) { it[0].toFloat() },
    Pid("0B", "map", 1) { it[0].toFloat() },
    Pid("04", "load04", 1) { it[0] * 100f / 255f },
    Pid("49", "pedal", 1) { it[0] * 100f / 255f },
    Pid("62", "torque", 1) { it[0] - 125f },
    Pid("05", "coolant", 1) { it[0] - 40f },
    Pid("0F", "iat", 1) { it[0] - 40f },
    Pid("5C", "oil", 1) { it[0] - 40f },
    Pid("10", "maf", 2) { u16(it) / 100f },
    Pid("11", "throttle", 1) { it[0] * 100f / 255f },
    Pid("78", "egt", 3) { (it[1] * 256 + it[2]) / 10f - 40f },
    Pid("23", "rail", 2) { u16(it) * 0.01f },
    Pid("5E", "fuelRate", 2) { u16(it) / 20f },
    Pid("61", "torqueDemand", 1) { it[0] - 125f },
    Pid("8E", "friction", 1) { it[0] - 125f },
    Pid("63", "torqueRef", 2) { u16(it).toFloat() },
    Pid("0E", "timing", 1) { it[0] / 2f - 64f },
    Pid("2C", "egr", 1) { it[0] * 100f / 255f },
    Pid("43", "abs43", 2) { u16(it) * 100f / 255f },
    Pid("46", "ambient", 1) { it[0] - 40f },
    Pid("33", "baro", 1) { it[0].toFloat() },
    Pid("42", "ctrlVolt", 2) { u16(it) / 1000f },
    Pid("2F", "fuelLevel", 1) { it[0] * 100f / 255f },
    Pid("1F", "runtime", 2) { u16(it).toFloat() },
    Pid("31", "distClear", 2) { u16(it).toFloat() },
    Pid("21", "distMil", 2) { u16(it).toFloat() },
    Pid("30", "warmups", 1) { it[0].toFloat() },
    Pid("A6", "odo", 4) { ((it[0].toLong() shl 24) or (it[1].toLong() shl 16) or (it[2].toLong() shl 8) or it[3].toLong()) / 10f },
    Pid("4A", "pedalE", 1) { it[0] * 100f / 255f },
    Pid("4C", "throttleCmd", 1) { it[0] * 100f / 255f },
    Pid("45", "relThrottle", 1) { it[0] * 100f / 255f },
    Pid("5D", "injTiming", 2) { u16(it) / 128f - 210f },
    Pid("0A", "fuelPress", 1) { it[0] * 3f },
    Pid("01", "status", 1) { it[0].toFloat() },
)

object ObdManager {
    const val OK = "OBD đã kết nối"
    private val _data = MutableStateFlow(ObdData())
    val data: StateFlow<ObdData> = _data
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var source = 0
    private var baro = 101

    // Nguồn cho thanh "engine load": 0 tự động (mô-men → chân ga → tính toán), 1 tính toán PID 04, 2 chân ga PID 49, 3 mô-men PID 62
    @Volatile var loadSource = 0

    // Bộ lọc loại giá trị nhảy bất thường (ví dụ nhiệt độ nước đột ngột về -40°C)
    private val lastGood = HashMap<String, Float>()
    private val pending = HashMap<String, Pair<Float, Int>>()
    private val med = ArrayDeque<Float>()
    private var suffix = ""   // "1" = bảo ELM327 chỉ chờ 1 phản hồi, nhanh hơn
    private var rr = 0
    private var zeroRpm = 0   // số lần liên tiếp đọc ra vòng tua 0

    // ---- Số đang cài ----
    // Hộp số tự động 7 cấp Navara NP300 (tỉ số theo tài liệu Nissan), cầu 3.357. Chu vi lốp 255/60R18 ~ 2.398 m.
    // Nếu lắp lốp khác, sửa TIRE_M (mét) cho khớp thì số hiển thị mới chuẩn.
    private val GEAR = doubleArrayOf(4.886, 3.169, 2.027, 1.411, 1.000, 0.864, 0.774)
    private const val FINAL_DRIVE = 3.357
    private const val TIRE_M = 2.398
    private var a4Fail = 0          // số lần PID 01A4 (số thực tế theo chuẩn J1979) không có phản hồi
    private var hwGear: Int? = null // số đọc trực tiếp từ xe qua PID 01A4 (nếu xe hỗ trợ)
    private var gCand: Int? = null
    private var gCnt = 0

    private fun st(m: String) = _data.update { it.copy(status = m) }

    fun restart(ctx: Context, src: Int) {
        job?.cancel()
        TorqueServer.stop()
        job = null
        source = src
        a4Fail = 0; hwGear = null; gCand = null; gCnt = 0
        lastGood.clear(); pending.clear(); med.clear(); suffix = ""; rr = 0; zeroRpm = 0; gearNullSince = 0L; kEma = 0.0
        _data.value = ObdData()
        start(ctx)
    }

    fun start(ctx: Context) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = scope.launch { if (source == 1) runTorque() else runDirect(app) }
    }

    // ---------- Nguồn Torque Pro (web upload) ----------
    private suspend fun runTorque() {
        st("Torque Pro: chờ dữ liệu…")
        TorqueServer.start(scope) { fromTorque(it) }
        while (currentCoroutineContext().isActive) {
            delay(2000)
            val hit = TorqueServer.lastHit
            if (hit == 0L) st("Torque Pro: chưa nhận dữ liệu (bật Upload trong Torque)")
            else if (System.currentTimeMillis() - hit > 6000) _data.value = ObdData(status = "Torque Pro: mất dữ liệu")
        }
    }

    private fun fromTorque(m: Map<String, String>) {
        fun f(k: String) = m[k]?.toFloatOrNull()
        _data.update { d ->
            val map = f("kb")
            val b = f("k33") ?: 101f
            d.copy(
                rpm = f("kc")?.toInt() ?: d.rpm,
                speed = f("kd")?.toInt() ?: d.speed,
                coolant = f("k5")?.toInt()?.takeIf { it > -35 } ?: d.coolant,
                load = f("k4")?.toInt() ?: d.load,
                intake = f("kf")?.toInt() ?: d.intake,
                ambient = f("k46")?.toInt() ?: d.ambient,
                volt = f("k42") ?: f("kff1238") ?: d.volt,
                boost = if (map != null) ((map - b) / 100f).coerceAtLeast(0f) else d.boost,
                status = OK,
            )
        }
        updateGear()
    }

    // ---------- Kết nối trực tiếp ----------
    private suspend fun runDirect(app: Context) {
        delay(600)
        while (currentCoroutineContext().isActive) {
            var t: Transport? = null
            var lbl = ""
            var err = ""
            try {
                st("Đang tìm adapter OBD…")
                t = openClassic() ?: openBle(app)
                if (t == null) {
                    st("Không thấy adapter OBD (kiểm tra tên, nguồn, quyền)")
                    delay(5000)
                    continue
                }
                lbl = t.label
                st("Đã nối $lbl, đang khởi tạo…")
                delay(400)
                for (c in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) {
                    try {
                        Log.d("Obd", c + " -> " + t.request(c, if (c == "ATZ") 6000 else 3000))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw IOException(c + ": " + e.message)
                    }
                }
                // Dò giao thức có thể mất vài giây đến hơn 10 giây, nên chờ lâu và không ngắt kết nối
                var ecu = false
                while (!ecu) {
                    st("Đang kết nối ECU… (bật điện hoặc nổ máy)")
                    val r = soft(t, "0100", 15000)
                    ecu = r != null && r.replace(" ", "").contains("4100")
                    if (!ecu) delay(2500)
                }
                lockEcu(t)
                poll(t)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                err = e.javaClass.simpleName + ": " + e.message
                Log.d("Obd", err)
            } finally {
                t?.close()
            }
            _data.update { ObdData(status = "Mất kết nối ($err) $lbl") }
            delay(3000)
        }
    }

    private suspend fun soft(t: Transport, cmd: String, ms: Long): String? =
        try { t.request(cmd, ms) } catch (e: IOException) { if (e.message == "timeout") null else throw e }

    // Xe có nhiều ECU (động cơ, hộp số...) cùng trả lời một lệnh OBD. Nếu lấy dòng trả lời đầu tiên, khi thì được ECU động cơ
    // (vòng tua 800), khi thì ECU khác (0), làm số liệu chập chờn và danh sách PID hỗ trợ đổi liên tục.
    // Ở đây ghim lại ECU động cơ (ưu tiên địa chỉ 7E8) bằng bộ lọc nhận của adapter.
    private suspend fun lockEcu(t: Transport) {
        try {
            soft(t, "ATH1", 2000)
            val r = soft(t, "010C", 3000)
            soft(t, "ATH0", 2000)
            val heads = ArrayList<String>()
            if (r != null) {
                for (line in r.split('\r', '\n')) {
                    val c = line.trim().replace(" ", "").uppercase()
                    // định dạng có tiêu đề CAN 11 bit: [tiêu đề 3 ký tự][độ dài 2 ký tự]410C...
                    if (c.length > 9 && c.indexOf("410C") == 5 && c.substring(0, 3).all { it in '0'..'9' || it in 'A'..'F' }) {
                        heads.add(c.substring(0, 3))
                    }
                }
            }
            Log.d("Obd", "ECU phản hồi 010C: $heads")
            val hdr = heads.firstOrNull { it == "7E8" } ?: heads.minOrNull()
            if (hdr != null) {
                val ok = soft(t, "ATCRA $hdr", 2000)
                Log.d("Obd", "ATCRA $hdr -> $ok")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            Log.d("Obd", "lockEcu: " + e.message)
        }
    }

    // Hỏi xe PID nào được hỗ trợ (các mặt nạ 0100, 0120, 0140, ...)
    private suspend fun discover(t: Transport): Set<String> {
        val out = HashSet<String>()
        var base = 0
        while (base <= 0xA0) {
            val p = "%02X".format(base)
            val r = soft(t, "01$p", 4000) ?: break
            val b = parse(r, p) ?: break
            if (b.size < 4) break
            val mask = (b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()
            for (i in 1..32) {
                if (((mask shr (32 - i)) and 1L) == 1L) out.add("%02X".format(base + i))
            }
            if (!out.contains("%02X".format(base + 32))) break
            base += 32
        }
        return out
    }

    // PID nuôi thanh engine load, được hỏi ở mỗi vòng
    private fun loadPid(sup: Set<String>): String = when (loadSource) {
        1 -> "04"
        2 -> if ("49" in sup) "49" else "04"
        3 -> if ("62" in sup) "62" else "04"
        else -> if ("62" in sup) "62" else if ("49" in sup) "49" else "04"
    }

    private suspend fun poll(t: Transport) {
        val sup = HashSet<String>(discover(t))
        if (sup.isEmpty()) sup.addAll(listOf("0B", "05", "04", "0F", "46", "33"))
        sup.addAll(listOf("0C", "0D"))
        _data.update { it.copy(pidCount = sup.size) }
        var n = 0
        var fails = 0
        var empty = 0
        while (true) {
            val lp = loadPid(sup)
            val core = listOf("0C", "0D", "0B", lp)
            val rest = PIDS.filter { it.pid in sup && it.pid !in core }
            // Vòng tua + tốc độ được hỏi MỖI vòng (để số đang cài cập nhật nhanh); boost và tải động cơ luân phiên;
            // các thông số khác cứ 3 vòng hỏi một lần. Mỗi vòng chỉ 3 đến 4 lệnh thay vì 5 như trước.
            val list = ArrayList<Pid>()
            PIDS.firstOrNull { it.pid == "0C" }?.let { list.add(it) }
            PIDS.firstOrNull { it.pid == "0D" }?.let { list.add(it) }
            val slow = if (n % 2 == 0) "0B" else lp
            PIDS.firstOrNull { it.pid == slow && it.pid in sup }?.let { list.add(it) }
            if (rest.isNotEmpty() && n % 3 == 0) {
                list.add(rest[rr % rest.size])
                rr = (rr + 1) % rest.size
            }
            var got = false
            for (p in list) {
                val r = soft(t, "01" + p.pid, 3000)
                if (r == null) {
                    if (++fails >= 6) throw IOException("không phản hồi")
                    continue
                }
                val b = parse(r, p.pid) ?: continue
                fails = 0
                got = true
                applyPid(p, b)
                if (p.pid == "0D") updateGear()   // vừa có cặp vòng tua và tốc độ mới: tính số ngay, không chờ hết vòng
            }
            updateDerived()
            // Số thực tế theo chuẩn J1979 (PID A4): rất ít xe hỗ trợ, nếu không có thì dùng ước lượng
            if ("A4" in sup && a4Fail < 3 && n % 3 == 0) {
                val r = soft(t, "01A4", 2000)
                val b = if (r == null) null else parse(r, "A4")
                if (b != null && b.size >= 2 && (b[0] and 1) != 0) {
                    a4Fail = 0
                    val g = b[1] ushr 4
                    hwGear = if (g in 1..9) g else null
                } else a4Fail++
            }
            updateGear()
            if (n % 10 == 0) {
                soft(t, "ATRV", 2000)?.let { r ->
                    Regex("[0-9]+\\.?[0-9]*").find(r)?.value?.toFloatOrNull()?.let { v -> _data.update { it.copy(volt = v) } }
                }
            }
            if (got) {
                empty = 0
                _data.update { if (it.status == OK) it else it.copy(status = OK) }
            } else {
                empty++
                if (empty >= 6) st("ECU chưa trả dữ liệu (bật điện hoặc nổ máy)")
                delay(300)
            }
            n++
        }
    }

    // ----- Ước lượng cấp số từ tỉ lệ vòng tua / tốc độ -----
    // Hiệu chỉnh theo Cài đặt (0..4 ứng với -6%, -3%, 0, +3%, +6% vòng tua dự kiến trên mỗi km/h)
    @Volatile var gearTrim = 2
    private var kEma = 0.0
    private var gearNullSince = 0L

    // Tốc độ dùng để tính số: ưu tiên tốc độ từ ECU; nếu ECU báo thấp mà GPS thấy xe đang chạy thì dùng GPS
    private fun speedForGear(d: ObdData): Float {
        if (d.speed >= 5) return d.speed.toFloat()
        val l = LocationHolder.loc.value
        if (l != null && hasFix(l) && l.hasSpeed() && l.speed > 2.5f) return l.speed * 3.6f
        return d.speed.toFloat()
    }

    // Tỉ lệ vòng tua / tốc độ (vòng/phút trên mỗi km/h) mong đợi ở số g, có tính hiệu chỉnh trong Cài đặt
    private fun gearK(g: Int): Double =
        GEAR[g - 1] * FINAL_DRIVE * 1000.0 / (60.0 * TIRE_M) * (1.0 + 0.03 * (gearTrim - 2))

    private fun gearErr(k: Double, g: Int): Double = abs(ln(k / gearK(g)))

    // Số hiển thị liền mạch và nhanh:
    // - làm mượt tỉ lệ vòng tua/tốc độ nhẹ tay (mẫu mới chiếm 60%) để theo kịp lúc chuyển số
    // - khớp rất sát (sai lệch dưới 3,5%) thì đổi số ngay; khớp vừa thì cần 2 mẫu liên tiếp (khoảng nửa giây)
    // - xe vừa lăn bánh (dưới 25 km/h) mà vòng tua cao hơn tỉ số số 1 (biến mô trượt) vẫn tính là số 1
    // - lúc chuyển số / đạp côn / xe chậm thì giữ số cũ, chỉ xoá sau 3 giây liên tục không ước lượng được
    private fun updateGear() {
        val hw = hwGear
        if (hw != null) {
            _data.update { it.copy(gear = hw, gearEst = false) }
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val d = _data.value
        val spd = speedForGear(d)
        if (spd >= 10f && d.rpm >= 600) {
            val k = d.rpm / spd.toDouble()
            kEma = if (kEma <= 0.0) k else kEma * 0.4 + k * 0.6
            var best = 1
            var bestErr = 9.0
            for (g in 1..7) {
                val e = gearErr(kEma, g)
                if (e < bestErr) { bestErr = e; best = g }
            }
            if (spd < 25f && kEma > gearK(1) && kEma < gearK(1) * 1.8) { best = 1; bestErr = 0.05 }
            val tol = if (spd < 20f) 0.11 else 0.08
            val cur = d.gear
            var cand: Int? = if (bestErr < tol) best else null
            if (cur != null && cand != null && cand != cur) {
                val curErr = gearErr(kEma, cur)
                if (curErr < tol && bestErr > curErr - 0.025) cand = cur
            }
            if (cand != null) {
                gearNullSince = 0L
                if (cand == gCand) gCnt++ else { gCand = cand; gCnt = 1 }
                val need = if (bestErr < 0.035) 1 else 2
                if (cand != cur && gCnt >= need) _data.update { it.copy(gear = cand, gearEst = true) }
                return
            }
        } else kEma = 0.0
        gCand = null
        gCnt = 0
        if (gearNullSince == 0L) gearNullSince = now
        if (now - gearNullSince > 3000L && _data.value.gear != null) _data.update { it.copy(gear = null, gearEst = true) }
    }

    // Loại giá trị nhảy bất thường: chỉ chấp nhận bước nhảy lớn khi nó lặp lại 3 lần liên tiếp
    private fun stable(key: String, v: Float, maxJump: Float): Float? {
        val prev = lastGood[key]
        if (prev == null || abs(v - prev) <= maxJump) {
            lastGood[key] = v
            pending.remove(key)
            return v
        }
        val pend = pending[key]
        if (pend != null && abs(v - pend.first) <= maxJump) {
            if (pend.second + 1 >= 3) {
                lastGood[key] = v
                pending.remove(key)
                return v
            }
            pending[key] = Pair(pend.first, pend.second + 1)
        } else pending[key] = Pair(v, 1)
        return null
    }

    private fun applyPid(p: Pid, b: List<Int>) {
        if (b.size < p.n) return
        if (p.key == "status") {
            val mil = if ((b[0] and 0x80) != 0) 1f else 0f
            val dtc = (b[0] and 0x7F).toFloat()
            _data.update { it.copy(ex = it.ex + mapOf("mil" to mil, "dtc" to dtc)) }
            return
        }
        var v: Float = try { p.f(b) } catch (e: Exception) { return }
        when (p.key) {
            "coolant", "oil", "iat", "ambient" -> {
                if (b[0] == 0) return                       // 0 = không có dữ liệu, sẽ thành -40°C
                if (v < -30f || v > 160f) return
                v = stable(p.key, v, if (p.key == "coolant") 8f else 15f) ?: return
            }
            "egt" -> if (v < -30f || v > 1200f) return
            "runtime", "distClear", "distMil" -> if (b[0] == 255 && b[1] == 255) return   // 0xFFFF = không có dữ liệu
            "load04" -> {
                med.addLast(v)
                while (med.size > 3) med.removeFirst()
                v = med.sorted()[med.size / 2]               // trung vị 3 lần đọc, bỏ nhiễu
            }
            "rpm" -> {
                if (v > 9000f) return
                // Đang nổ máy mà đọc ra 0 một vài lần thường là nhiễu: chỉ tin khi 0 lặp lại 3 lần liên tiếp
                if (v < 50f) {
                    zeroRpm++
                    if (zeroRpm < 3 && _data.value.rpm > 300) return
                } else zeroRpm = 0
            }
            "speed" -> if (v > 300f) return
        }
        _data.update { d ->
            val n = d.copy(ex = d.ex + (p.key to v))
            when (p.key) {
                "rpm" -> n.copy(rpm = v.roundToInt())
                "speed" -> n.copy(speed = v.roundToInt())
                "map" -> n.copy(boost = ((v - baro) / 100f).coerceAtLeast(0f))
                "coolant" -> n.copy(coolant = v.roundToInt())
                "iat" -> n.copy(intake = v.roundToInt())
                "ambient" -> n.copy(ambient = v.roundToInt())
                "baro" -> { baro = v.roundToInt(); n }
                else -> n
            }
        }
    }

    // Tải động cơ + các giá trị suy ra (mô-men Nm, công suất kW, mức tiêu thụ L/100km)
    private fun updateDerived() {
        _data.update { d ->
            val ex = d.ex
            val l04 = ex["load04"]
            val pedal = ex["pedal"]
            val tq = ex["torque"]?.coerceAtLeast(0f)
            val load: Float? = when (loadSource) {
                1 -> l04
                2 -> pedal ?: l04
                3 -> tq ?: l04
                else -> tq ?: pedal ?: l04
            }
            val extra = HashMap<String, Float>()
            val ref = ex["torqueRef"]
            if (tq != null && ref != null) {
                val nm = tq / 100f * ref
                extra["torqueNm"] = nm
                extra["power"] = nm * d.rpm / 9549f
            }
            val fr = ex["fuelRate"]
            if (fr != null && d.speed >= 5) extra["lper100"] = fr / d.speed * 100f
            d.copy(load = load?.roundToInt()?.coerceIn(0, 100) ?: d.load, ex = ex + extra)
        }
    }

    fun parse(raw: String, p: String): List<Int>? {
        for (line in raw.split('\r', '\n')) {
            val c = line.trim().replace(" ", "")
            if (c.startsWith("41$p", ignoreCase = true)) {
                return c.substring(4).chunked(2).mapNotNull { it.toIntOrNull(16) }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun openClassic(): Transport? {
        val ad = BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!ad.isEnabled) return null
        val dev = ad.bondedDevices.firstOrNull { looksLikeObd(it.name) } ?: return null
        return try {
            ad.cancelDiscovery()
            val s = dev.createRfcommSocketToServiceRecord(SPP)
            s.connect()
            SocketTransport(s)
        } catch (e: Exception) { null }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openBle(ctx: Context): Transport? {
        val ad = BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!ad.isEnabled) return null
        val scanner = ad.bluetoothLeScanner ?: return null
        val found = CompletableDeferred<BluetoothDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                if (looksLikeObd(r.scanRecord?.deviceName ?: r.device.name)) found.complete(r.device)
            }
        }
        scanner.startScan(cb)
        val dev = withTimeoutOrNull(10000) { found.await() }
        scanner.stopScan(cb)
        if (dev == null) return null
        val t = BleTransport(ctx, dev)
        return if (t.connect()) t else { t.close(); null }
    }
}

class SocketTransport(private val s: BluetoothSocket) : Transport {
    @SuppressLint("MissingPermission")
    override val label: String = "BT cổ điển " + s.remoteDevice.name
    private val ins = s.inputStream
    private val outs = s.outputStream
    override suspend fun request(cmd: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        while (ins.available() > 0) ins.read()
        outs.write((cmd + "\r").toByteArray()); outs.flush()
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (ins.available() > 0) {
                val c = ins.read().toChar()
                if (c == '>') return@withContext sb.toString()
                sb.append(c)
            } else delay(10)
        }
        throw IOException("timeout")
    }
    override fun close() { runCatching { s.close() } }
}

@SuppressLint("MissingPermission")
class BleTransport(private val ctx: Context, private val dev: BluetoothDevice) : Transport {
    override var label: String = "BLE " + dev.name
    private var gatt: BluetoothGatt? = null
    private var wr: BluetoothGattCharacteristic? = null
    private val ready = CompletableDeferred<Boolean>()
    private var pending: CompletableDeferred<String>? = null
    private val buf = StringBuilder()

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else { ready.complete(false); pending?.completeExceptionally(IOException("disconnected")) }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            for (sv in g.services) {
                Log.d("Obd", "svc " + sv.uuid + " chars=" + sv.characteristics.joinToString { it.uuid.toString().take(8) + ":" + it.properties })
            }
            for (s in g.services) {
                val id = s.uuid.toString()
                if (id.startsWith("00001800") || id.startsWith("00001801")) continue
                val w = s.characteristics.firstOrNull {
                    (it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                }
                val n = s.characteristics.firstOrNull {
                    (it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                }
                if (w != null && n != null) {
                    wr = w
                    label = "BLE " + dev.name + " svc=" + id.take(8)
                    g.setCharacteristicNotification(n, true)
                    val d = n.getDescriptor(CCC)
                    if (d != null) { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
                    else ready.complete(true)
                    return
                }
            }
            ready.complete(false)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { ready.complete(status == BluetoothGatt.GATT_SUCCESS) }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            synchronized(buf) {
                buf.append(String(c.value))
                if (buf.contains('>')) {
                    pending?.complete(buf.toString().replace(">", ""))
                    buf.clear()
                }
            }
        }
    }

    suspend fun connect(): Boolean {
        gatt = dev.connectGatt(ctx, false, cb)
        return withTimeoutOrNull(8000) { ready.await() } == true
    }

    override suspend fun request(cmd: String, timeoutMs: Long): String {
        val d = CompletableDeferred<String>()
        synchronized(buf) { buf.clear(); pending = d }
        val w = wr ?: throw IOException("no characteristic")
        w.writeType = if ((w.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        w.value = (cmd + "\r").toByteArray()
        gatt?.writeCharacteristic(w)
        return withTimeoutOrNull(timeoutMs) { d.await() } ?: throw IOException("timeout")
    }

    override fun close() { runCatching { gatt?.disconnect(); gatt?.close() } }
}
