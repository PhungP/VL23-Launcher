package vn.navara.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ===== TPMS: đọc cảm biến áp suất lốp mua ngoài qua Bluetooth LE (chỉ nghe quảng bá, không ghép đôi) =====
// Hỗ trợ họ cảm biến nắp van phổ biến (tên TPMS1_xxxxxx ... TPMS4_xxxxxx, gói quảng bá 16 byte):
//   byte 0-5 địa chỉ, 6-9 áp suất (uint32 little-endian, 0,001 kPa), 10-13 nhiệt độ (int32 little-endian, 0,01 °C), 14 pin %, 15 cờ cảnh báo.
// Loại cảm biến khác (ví dụ tên "BR") chỉ được liệt kê kèm dữ liệu thô để đối chiếu, chưa giải mã.
// Cảm biến loại này báo áp suất tuyệt đối nên mặc định trừ áp khí quyển (tắt được trong Cài đặt nếu cảm biến của bạn báo áp suất lốp sẵn).

class TpmsReading(val kpaRaw: Float, val tempC: Float, val battery: Int, val alarm: Int)

// Cảm biến đã nghe thấy (hiện trong Cài đặt để gán bánh xe)
class TpmsSeen(val addr: String, val name: String, val rssi: Int, val hex: String, val reading: TpmsReading?, val at: Long)

// Giá trị đã quy ra áp suất lốp (bar) của một bánh xe
class TpmsWheel(val bar: Float, val tempC: Float, val battery: Int, val at: Long)

val TPMS_WHEELS = listOf("FL", "FR", "RL", "RR")
fun tpmsWheelName(w: String): String = when (w) {
    "FL" -> "Trước trái"
    "FR" -> "Trước phải"
    "RL" -> "Sau trái"
    else -> "Sau phải"
}

// "FL=AA:BB;FR=..." -> bản đồ bánh xe -> địa chỉ
fun parseTpmsMap(s: String): Map<String, String> = s.split(";").mapNotNull { e ->
    val kv = e.split("=")
    if (kv.size == 2 && kv[0] in TPMS_WHEELS && kv[1].isNotBlank()) kv[0] to kv[1] else null
}.toMap()

fun tpmsMapToString(m: Map<String, String>): String = m.entries.joinToString(";") { it.key + "=" + it.value }

// Đổi bar sang đơn vị hiển thị
fun tpmsConv(bar: Float, unit: Int): Float = when (unit) { 1 -> bar * 14.5038f; 2 -> bar * 100f; else -> bar }
fun tpmsUnitLabel(unit: Int): String = when (unit) { 1 -> "psi"; 2 -> "kPa"; else -> "bar" }
fun tpmsDec(unit: Int): Int = when (unit) { 1 -> 1; 2 -> 0; else -> 2 }

// Khoá thông số của ô trang 1: "tpmsFL" = áp suất, "tpmsFLt" = nhiệt độ
fun tpmsValue(m: Map<String, TpmsWheel>, key: String): Float? {
    if (!key.startsWith("tpms") || key.length < 6) return null
    val w = m[key.substring(4, 6)] ?: return null
    if (System.currentTimeMillis() - w.at > 20 * 60_000L) return null   // cảm biến báo thưa (1-5 phút khi đỗ): quá 20 phút thì coi như mất tín hiệu
    return if (key.endsWith("t")) w.tempC else w.bar
}

object TpmsManager {
    val wheels = MutableStateFlow<Map<String, TpmsWheel>>(emptyMap())
    val seen = MutableStateFlow<List<TpmsSeen>>(emptyList())
    val scanning = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var cb: ScanCallback? = null
    private val table = HashMap<String, TpmsSeen>()
    @Volatile private var on = false
    @Volatile private var map: Map<String, String> = emptyMap()
    @Volatile private var abs = true
    @Volatile private var trimBar = 0f

    // Gọi mỗi khi đổi cài đặt TPMS; tính lại giá trị ngay (đổi hiệu chỉnh không cần chờ cảm biến báo lại)
    fun configure(enabled: Boolean, mapStr: String, absolute: Boolean, trim001: Int) {
        map = parseTpmsMap(mapStr)
        abs = absolute
        trimBar = trim001 / 100f
        publish()
        if (enabled == on) return
        on = enabled
        if (enabled) {
            job?.cancel()
            job = scope.launch {
                while (isActive && on) {
                    if (cb == null) startScan()
                    scanning.value = cb != null
                    delay(15000)
                }
            }
        } else {
            job?.cancel()
            stopScan()
            scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        try {
            val ad = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!ad.isEnabled) return
            val sc = ad.bluetoothLeScanner ?: return
            val c = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) { handle(result) }
                override fun onScanFailed(errorCode: Int) { cb = null; scanning.value = false }
            }
            sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), c)
            cb = c
        } catch (e: Exception) {
            cb = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val c = cb ?: return
        cb = null
        try { BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(c) } catch (e: Exception) { }
    }

    private fun hex(b: ByteArray?): String = if (b == null) "" else b.joinToString("") { String.format("%02x", it.toInt() and 0xFF) }

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    private fun decode(name: String, rec: ScanRecord): TpmsReading? {
        if (!name.startsWith("TPMS", ignoreCase = true)) return null
        val md = rec.manufacturerSpecificData ?: return null
        for (i in 0 until md.size()) {
            val b = md.valueAt(i) ?: continue
            if (b.size < 16) continue
            val kpa = u32(b, 6) / 1000f
            val t = u32(b, 10).toInt() / 100f
            if (kpa < 0f || kpa > 1500f || t < -50f || t > 200f) continue   // giá trị vô lý: bỏ qua
            return TpmsReading(kpa, t, b[14].toInt() and 0xFF, b[15].toInt() and 0xFF)
        }
        return null
    }

    private fun handle(r: ScanResult) {
        val rec = r.scanRecord ?: return
        val name = rec.deviceName ?: ""
        if (!(name.startsWith("TPMS", ignoreCase = true) || name == "BR")) return
        val addr = r.device.address ?: return
        val reading = decode(name, rec)
        synchronized(table) { table[addr] = TpmsSeen(addr, name, r.rssi, hex(rec.bytes), reading, System.currentTimeMillis()) }
        publish()
    }

    // Gán cảm biến cho bánh xe: ưu tiên bảng người dùng đã gán; cảm biến chưa gán thì theo số trong tên (TPMS1 trước trái, 2 trước phải, 3 sau trái, 4 sau phải)
    private fun publish() {
        val list: List<TpmsSeen> = synchronized(table) { table.values.toList() }
        seen.value = list.sortedBy { it.name + it.addr }
        val byWheel = HashMap<String, TpmsSeen>()
        val assigned = map.values.toSet()
        for ((w, a) in map) list.firstOrNull { it.addr == a }?.let { byWheel[w] = it }
        for (s in list) {
            if (s.addr in assigned) continue
            val n = Regex("(?i)^TPMS([1-4])").find(s.name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val w = TPMS_WHEELS[n - 1]
            if (w !in byWheel && w !in map) byWheel[w] = s
        }
        val out = HashMap<String, TpmsWheel>()
        for ((w, s) in byWheel) {
            val rd = s.reading ?: continue
            val gaugeKpa = if (abs) rd.kpaRaw - 101.3f else rd.kpaRaw
            out[w] = TpmsWheel((gaugeKpa / 100f + trimBar).coerceAtLeast(0f), rd.tempC, rd.battery, s.at)
        }
        wheels.value = out
    }
}
