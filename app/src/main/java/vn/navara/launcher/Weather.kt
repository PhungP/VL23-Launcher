package vn.navara.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Vị trí GPS của Tbox S1 (module GPS gắn trong máy; cần bật dịch vụ Vị trí)
object LocationHolder {
    val loc = MutableStateFlow<Location?>(null)
    val sats = MutableStateFlow(0)       // số vệ tinh đang dùng để định vị
    val gpsOn = MutableStateFlow(true)   // dịch vụ GPS có đang bật không
    private var started = false

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (started) return
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        started = true
        gpsOn.value = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val l = object : LocationListener {
            override fun onLocationChanged(n: Location) {
                val c = loc.value
                // Ưu tiên GPS; chỉ nhận vị trí mạng khi chưa có GPS hoặc GPS đã cũ
                if (n.provider == LocationManager.GPS_PROVIDER || c == null ||
                    c.provider != LocationManager.GPS_PROVIDER || !hasFix(c)) loc.value = n
            }
            override fun onProviderEnabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) gpsOn.value = true }
            override fun onProviderDisabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) gpsOn.value = false }
            @Deprecated("API cũ")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        // Đăng ký cả khi đang tắt: bật Vị trí sau đó vẫn tự nhận tín hiệu
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                lm.requestLocationUpdates(p, 1000L, 0f, l, Looper.getMainLooper())
                lm.getLastKnownLocation(p)?.let { if (loc.value == null) loc.value = it }
            } catch (e: Exception) {
                Log.d("Loc", p + ": " + e.message)
            }
        }
        try {
            lm.registerGnssStatusCallback(object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(s: GnssStatus) {
                    var n = 0
                    for (i in 0 until s.satelliteCount) if (s.usedInFix(i)) n++
                    sats.value = n
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { Log.d("Loc", "gnss: " + e.message) }
    }
}

// Vị trí còn "tươi" (dưới 15 giây) thì coi như đang có tín hiệu
fun hasFix(l: Location?): Boolean = l != null && SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos < 15_000_000_000L

// Tốc độ theo GPS (km/h)
fun gpsKmh(l: Location?): Int = if (l != null && hasFix(l) && l.hasSpeed()) Math.round(l.speed * 3.6f) else 0

data class Weather(val temp: Int, val code: Int, val place: String)

fun weatherText(code: Int): String = when (code) {
    0 -> "Trời quang"
    1, 2 -> "Ít mây"
    3 -> "Nhiều mây"
    45, 48 -> "Sương mù"
    in 51..57 -> "Mưa phùn"
    in 61..67 -> "Mưa"
    in 71..77 -> "Tuyết"
    in 80..82 -> "Mưa rào"
    in 85..86 -> "Tuyết rào"
    in 95..99 -> "Dông"
    else -> ""
}

// Nhiệt độ ngoài trời theo vị trí hiện tại, lấy từ Open-Meteo (miễn phí, không cần key)
object WeatherManager {
    val state = MutableStateFlow<Weather?>(null)
    val error = MutableStateFlow("")            // lý do lần lấy gần nhất thất bại ("" = ổn), hiện ở trang chủ để biết vì sao
    val updatedAt = MutableStateFlow(0L)        // thời điểm lấy thành công gần nhất
    @Volatile private var usingIp = true
    private var job: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)   // đánh thức vòng lặp để lấy ngay (vừa có mạng, vừa có GPS...)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(ctx: Context) {
        if (job?.isActive == true) return
        watchNetwork(ctx)
        job = scope.launch {
            var fails = 0
            while (isActive) {
                val w = try { fetch() } catch (e: Exception) { Log.d("Weather", e.toString()); error.value = brief(e); null }
                if (w != null) {
                    state.value = w
                    updatedAt.value = System.currentTimeMillis()
                    error.value = ""
                    fails = 0
                } else fails++
                // Thất bại: thử lại sau 15, 30, 45, 60 giây (tối đa 60); thành công: 2 phút nếu đang dùng vị trí theo IP, 15 phút nếu có GPS
                val wait = if (w == null) minOf(15_000L * fails, 60_000L) else if (usingIp) 120_000L else 900_000L
                withTimeoutOrNull(wait) { wake.receive() }
            }
        }
    }

    // Khi mạng mặc định (SIM 4G) vừa có hoặc vừa được xác nhận có internet thì lấy thời tiết ngay, khỏi chờ hết chu kỳ
    private fun watchNetwork(ctx: Context) {
        try {
            val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { wake.trySend(Unit) }
                override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) {
                    if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && (state.value == null || error.value.isNotEmpty())) wake.trySend(Unit)
                }
            })
        } catch (e: Exception) { Log.d("Weather", "net: " + e.message) }
    }

    private fun brief(e: Exception): String {
        val m = (e.message ?: "").replace("\n", " ")
        return e.javaClass.simpleName + (if (m.isNotEmpty()) ": " + m.take(50) else "")
    }

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", APP_UA)
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    // Vị trí theo IP (khi chưa có GPS): thử ipwho.is, hỏng thì thử ipapi.co
    private fun ipLocation(): Triple<Double, Double, String> {
        try {
            val j = JSONObject(get("https://ipwho.is/"))
            if (j.optBoolean("success", true)) return Triple(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city", ""))
        } catch (e: Exception) { Log.d("Weather", "ipwho: " + e.message) }
        val j = JSONObject(get("https://ipapi.co/json/"))
        return Triple(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city", ""))
    }

    private fun fetch(): Weather {
        val l = LocationHolder.loc.value
        val lat: Double
        val lon: Double
        var place = ""
        if (l != null) {
            lat = l.latitude
            lon = l.longitude
        } else {
            val ip = ipLocation()
            lat = ip.first
            lon = ip.second
            place = ip.third
        }
        usingIp = l == null
        if (place.isEmpty()) {
            place = try {
                val r = JSONObject(get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=vi"))
                r.optString("locality").ifEmpty { r.optString("city") }.ifEmpty { r.optString("principalSubdivision") }
            } catch (e: Exception) { "" }
        }
        val cur = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"))
            .getJSONObject("current")
        return Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), place)
    }
}
