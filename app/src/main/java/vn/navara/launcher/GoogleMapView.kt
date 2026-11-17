package vn.navara.launcher

import android.annotation.SuppressLint
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

// ===== Bản đồ Google Maps (Maps JavaScript API chạy trong WebView) =====
// Người dùng tự nhập khoá API trong Cài đặt. Dùng WebView nên không cần Google Play Services trên đầu xe.
// Bản đồ vector (mapId) cho phép xoay theo hướng xe và nghiêng 3D; có lớp giao thông; tuyến dẫn đường vẽ đè lên bản đồ.

// WebView không nhận thao tác chạm: vuốt đổi trang không bị bản đồ chặn (giống các bản đồ khác trong ứng dụng)
private class StaticWebView(c: android.content.Context) : WebView(c) {
    override fun onTouchEvent(event: MotionEvent?): Boolean = false
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
}

private fun mapsHtml(key: String, dark: Boolean): String {
    val k = key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }   // khoá chỉ gồm chữ, số, gạch
    val scheme = if (dark) "DARK" else "LIGHT"
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body,#m{height:100%;margin:0;background:#101418}</style></head><body><div id="m"></div>
<script>
var map, car, routeLine, destDot, trafficLayer;
function init() {
  map = new google.maps.Map(document.getElementById('m'), {
    center: {lat: 21.0285, lng: 105.8542}, zoom: 16, mapId: 'DEMO_MAP_ID', colorScheme: '$scheme',
    disableDefaultUI: true, gestureHandling: 'none', keyboardShortcuts: false, clickableIcons: false
  });
  car = new google.maps.Marker({map: map, position: map.getCenter(), zIndex: 10, icon: {
    path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW, scale: 6, fillColor: '#FF7A00', fillOpacity: 1,
    strokeColor: '#FFFFFF', strokeWeight: 2, rotation: 0}});
  trafficLayer = new google.maps.TrafficLayer();
  Android.ready();
}
function setPos(lat, lng, heading, zoom, tilt, arrow) {
  if (!map) return;
  var p = {lat: lat, lng: lng};
  map.moveCamera({center: p, zoom: zoom, heading: heading, tilt: tilt});
  car.setPosition(p);
  var ic = car.getIcon(); ic.rotation = arrow; car.setIcon(ic);
}
function setTraffic(on) { if (trafficLayer) trafficLayer.setMap(on ? map : null); }
function setRoute(pts, dlat, dlng) {
  if (!map) return;
  if (routeLine) { routeLine.setMap(null); routeLine = null; }
  if (destDot) { destDot.setMap(null); destDot = null; }
  if (!pts || pts.length < 2) return;
  routeLine = new google.maps.Polyline({map: map, path: pts.map(function(a){return {lat:a[0], lng:a[1]};}),
    strokeColor: '#2F7BFF', strokeOpacity: 0.95, strokeWeight: 8, zIndex: 5});
  destDot = new google.maps.Marker({map: map, position: {lat: dlat, lng: dlng}, zIndex: 6, icon: {
    path: google.maps.SymbolPath.CIRCLE, scale: 9, fillColor: '#22C55E', fillOpacity: 1, strokeColor: '#FFFFFF', strokeWeight: 3}});
}
window.gm_authFailure = function() { Android.fail('auth'); };
</script>
<script async src="https://maps.googleapis.com/maps/api/js?key=$k&callback=init&v=weekly"></script>
</body></html>"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleMapPane(
    modifier: Modifier, loc: Location?, dark: Boolean, zoom: Int, headingUp: Boolean, tilt: Boolean,
    apiKey: String, traffic: Boolean
) {
    val ctx = LocalContext.current
    var ready by remember(apiKey, dark) { mutableStateOf(false) }
    var failed by remember(apiKey, dark) { mutableStateOf<String?>(null) }
    var web by remember(apiKey, dark) { mutableStateOf<WebView?>(null) }
    var log by remember(apiKey, dark) { mutableStateOf("") }   // dòng lỗi cuối cùng Google báo (ví dụ ApiNotActivatedMapError)
    val ns by Navigator.state.collectAsState()

    // Hướng xe đã làm mượt (độ cộng dồn); đứng yên thì giữ hướng cuối
    var cum by remember { mutableStateOf(0f) }
    LaunchedEffect(loc) {
        if (loc != null && loc.hasBearing() && loc.speed > 1.5f) {
            cum += ((loc.bearing - cum) % 360f + 540f) % 360f - 180f
        }
    }
    val ang by animateFloatAsState(cum, tween(700))

    // Không tải được sau 15 giây: báo lỗi mạng hoặc khoá
    LaunchedEffect(apiKey, dark) {
        delay(15000)
        if (!ready && failed == null) failed = "net"
    }
    LaunchedEffect(loc, zoom, headingUp, tilt, ready) {
        val w = web ?: return@LaunchedEffect
        if (!ready || loc == null) return@LaunchedEffect
        val hd = if (headingUp) ang else 0f
        val arrow = if (headingUp) 0f else ang
        w.evaluateJavascript("setPos(${loc.latitude},${loc.longitude},${hd},${zoom},${if (tilt) 60 else 0},${arrow})", null)
    }
    LaunchedEffect(traffic, ready) {
        if (ready) web?.evaluateJavascript("setTraffic($traffic)", null)
    }
    LaunchedEffect(ns.ver, ready) {
        if (!ready) return@LaunchedEffect
        val r = ns.route
        val js = if (r == null) "setRoute(null,0,0)" else
            "setRoute([" + r.pts.joinToString(",") { "[" + it[0] + "," + it[1] + "]" } + "]," + r.dest.lat + "," + r.dest.lng + ")"
        web?.evaluateJavascript(js, null)
    }

    // Đổi khoá hoặc đổi sáng/tối thì dựng lại bản đồ (colorScheme chỉ đặt được lúc khởi tạo)
    key(apiKey, dark) {
        Box(modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    StaticWebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(0xFF101418.toInt())
                        val ui = Handler(Looper.getMainLooper())
                        addJavascriptInterface(object {
                            @JavascriptInterface fun ready() { ui.post { ready = true } }
                            @JavascriptInterface fun fail(m: String) { ui.post { failed = m } }
                        }, "Android")
                        webViewClient = WebViewClient()
                        // Bắt thông báo lỗi của Google Maps để hiện đúng nguyên nhân (khoá sai loại, chưa bật API, chưa bật thanh toán...)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                                val txt = m.message()
                                if (txt.contains("JavaScript API error") || txt.contains("MapError")) ui.post { log = txt.take(200); failed = "auth" }
                                else if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) ui.post { if (log.isEmpty()) log = txt.take(200) }
                                return true
                            }
                        }
                        // Địa chỉ gốc giả để khoá giới hạn theo "Websites" (https://localhost/*) nhận được
                        loadDataWithBaseURL("https://localhost/", mapsHtml(apiKey, dark), "text/html", "utf-8", null)
                        web = this
                    }
                },
                onRelease = { it.destroy() }
            )
            val detail = if (log.isNotBlank()) "\n\nChi tiết từ Google: " + log else ""
            val msg = when {
                failed == "auth" -> "Google Maps từ chối khoá API.\nKhoá phải bật \"Maps JavaScript API\" (khác với Maps SDK for Android), bật thanh toán, và đặt giới hạn \"Websites\" với https://localhost/* hoặc không giới hạn." + detail
                failed == "net" && !ready -> "Không tải được Google Maps.\nKiểm tra kết nối mạng và khoá API." + detail
                !ready -> "Đang tải Google Maps…"
                else -> null
            }
            if (msg != null) {
                Text(
                    msg, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE6101418)).padding(16.dp)
                )
            }
        }
    }
}
