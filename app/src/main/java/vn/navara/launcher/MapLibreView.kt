package vn.navara.launcher

import android.location.Location
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

// Bản đồ vector MapLibre (mã nguồn mở, không phí SDK) dùng tile và style của MapTiler Cloud (dữ liệu OpenStreetMap, style riêng của MapTiler)
// hoặc của Goong (goong = true, dữ liệu bản đồ Việt Nam).
// Xe vẫn được vẽ bằng Compose ở giữa màn hình; bản đồ chỉ nhận lệnh từ code (tắt mọi cử chỉ), nên vuốt đổi trang không bị nhầm.
// Gói MapTiler miễn phí giới hạn dùng phi thương mại, bắt buộc hiện logo MapTiler và ghi nguồn OpenStreetMap trên bản đồ.
// Khoá / mở khoá cảm ứng bản đồ: khoá = bản đồ chỉ nhận lệnh từ code và bám theo xe; mở khoá = kéo, zoom bằng tay và không bám theo xe
object MapTouch { val free = MutableStateFlow(false) }

// Lệnh zoom từ nút + / − khi bản đồ đang mở khoá (n tăng dần để mỗi lần bấm là một lệnh mới, d = +1 hoặc −1 bậc)
data class ZoomCmd(val n: Int, val d: Int)

private const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"

@Composable
fun MapLibrePane(
    modifier: Modifier, loc: Location?, dark: Boolean, zoom: Int,
    pois: List<Poi>, headingUp: Boolean, marker: Int, key: String,
    theme: Int = 0, tilt: Boolean = false, goong: Boolean = false,
    free: Boolean = false, recenterTick: Int = 0, zoomCmd: ZoomCmd = ZoomCmd(0, 0), onFreeZoom: (Int) -> Unit = {}
) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val owner = ctx as? ComponentActivity
    val orange = Color(0xFFFF7A00)

    // Hướng xe đã làm mượt (độ cộng dồn); đứng yên thì giữ hướng cuối
    var cum by remember { mutableStateOf(0f) }
    LaunchedEffect(loc) {
        if (loc != null && loc.hasBearing() && loc.speed > 1.5f) {
            cum += ((loc.bearing - cum) % 360f + 540f) % 360f - 180f
        }
    }
    val ang by animateFloatAsState(cum, tween(700))

    // pixelRatio theo mật độ của giao diện (đã co giãn theo màn hình) để chữ và nét bản đồ cùng cỡ trên 800×480, 1280×720, 1920×1080...
    val mapView = remember(dens) {
        MapLibre.getInstance(ctx.applicationContext)
        MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true).pixelRatio(dens)).also { it.onCreate(null) }
    }
    val freeNow by rememberUpdatedState(free)
    val onZoomNow by rememberUpdatedState(onFreeZoom)
    var camTick by remember { mutableStateOf(0) }
    DisposableEffect(owner, mapView) {
        val lc = owner?.lifecycle
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lc?.addObserver(obs)
        onDispose {
            lc?.removeObserver(obs)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.setAllGesturesEnabled(false)
            m.uiSettings.isCompassEnabled = false
            // Bản đồ mở khoá: vẽ lại xe theo toạ độ khi bản đồ di chuyển; dừng tay thì đồng bộ mức zoom để nút + / − và chế độ bám xe dùng đúng mức
            m.addOnCameraMoveListener { camTick++ }
            m.addOnCameraIdleListener {
                camTick++
                if (freeNow) onZoomNow((m.cameraPosition.zoom + 1.0).roundToInt().coerceIn(12, 19))
            }
            val pad = (8 * dens).toInt()
            m.uiSettings.setAttributionGravity(Gravity.BOTTOM or Gravity.END)
            m.uiSettings.setAttributionMargins(0, 0, pad, pad)
            m.uiSettings.setLogoGravity(Gravity.BOTTOM or Gravity.END)
            m.uiSettings.setLogoMargins(0, 0, pad, (44 * dens).toInt())
            map = m
        }
    }

    // Style sáng / tối của MapTiler; đổi style thì thêm lại lớp vết đường và điểm cảnh báo
    // Kiểu bản đồ: 0 Streets (chi tiết đường và địa điểm), 1 OpenStreetMap (giống bản đồ OSM chuẩn), 2 Bright. OpenStreetMap không có bản tối nên dùng Streets tối.
    val styleName = when {
        theme == 1 && !dark -> "openstreetmap"
        theme == 2 -> if (dark) "bright-v2-dark" else "bright-v2"
        else -> if (dark) "streets-v2-dark" else "streets-v2"
    }
    // Goong (key = khoá Maptiles của Goong): 0 Đường phố, 1 Sáng (không có bản tối nên dùng bản tối chung), 2 Dẫn đường (ngày/đêm)
    val goongStyle = when (theme) {
        1 -> if (dark) "goong_map_dark" else "goong_light_v2"
        2 -> if (dark) "navigation_night" else "navigation_day"
        else -> if (dark) "goong_map_dark" else "goong_map_web"
    }
    val styleUrl = if (goong) "https://tiles.goong.io/assets/" + goongStyle + ".json?api_key=" + key
    else "https://api.maptiler.com/maps/" + styleName + "/style.json?key=" + key
    LaunchedEffect(map, styleUrl) {
        val m = map ?: return@LaunchedEffect
        styleReady = false
        m.setStyle(styleUrl) { style ->
            if (!goong) tuneStyle(style, dark)   // tuneStyle dựa trên cấu trúc OpenMapTiles của MapTiler, không áp dụng cho style Goong
            // Tuyến dẫn đường: viền trắng bên dưới, nét xanh dương bên trên; điểm đến chấm xanh lá
            style.addSource(GeoJsonSource("route-src", EMPTY))
            style.addLayer(
                LineLayer("route-casing", "route-src").withProperties(
                    PropertyFactory.lineColor("#ffffff"), PropertyFactory.lineWidth(11f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
                )
            )
            style.addLayer(
                LineLayer("route-line", "route-src").withProperties(
                    PropertyFactory.lineColor("#2F7BFF"), PropertyFactory.lineWidth(7f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
                )
            )
            style.addSource(GeoJsonSource("dest-src", EMPTY))
            style.addLayer(
                CircleLayer("dest-dot", "dest-src").withProperties(
                    PropertyFactory.circleRadius(10f), PropertyFactory.circleColor("#22C55E"),
                    PropertyFactory.circleStrokeColor("#ffffff"), PropertyFactory.circleStrokeWidth(3f)
                )
            )
            style.addSource(GeoJsonSource("trail-src", EMPTY))
            style.addLayer(
                LineLayer("trail-line", "trail-src").withProperties(
                    PropertyFactory.lineColor("#ff7a00"), PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
                )
            )
            style.addSource(GeoJsonSource("poi-src", EMPTY))
            style.addLayer(
                CircleLayer("poi-dots", "poi-src").withProperties(
                    PropertyFactory.circleRadius(8f), PropertyFactory.circleColor(Expression.get("c")),
                    PropertyFactory.circleStrokeColor("#ffffff"), PropertyFactory.circleStrokeWidth(2.5f)
                )
            )
            styleReady = true
        }
    }
    LaunchedEffect(styleReady) {
        if (styleReady) slow = false else { delay(9000); if (!styleReady) slow = true }
    }

    // Tuyến dẫn đường (vẽ lại khi đổi tuyến hoặc khi style bản đồ nạp lại)
    val navSt by Navigator.state.collectAsState()
    LaunchedEffect(navSt.ver, styleReady, map) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val r = navSt.route
        val line = if (r == null) EMPTY else {
            "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" +
                r.pts.joinToString(",") { "[" + it[1] + "," + it[0] + "]" } + "]}}"
        }
        val dot = if (r == null) EMPTY else
            "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[" + r.dest.lng + "," + r.dest.lat + "]}}"
        m.style?.getSourceAs<GeoJsonSource>("route-src")?.setGeoJson(line)
        m.style?.getSourceAs<GeoJsonSource>("dest-src")?.setGeoJson(dot)
    }

    // Vết đường xe đã đi
    val trailVer by Trail.version.collectAsState()
    LaunchedEffect(trailVer, styleReady, map) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val pts = synchronized(Trail.pts) { Trail.pts.takeLast(400).toList() }
        val json = if (pts.size < 2) EMPTY else {
            "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" +
                pts.joinToString(",") { "[" + it[1] + "," + it[0] + "]" } + "]}}"
        }
        m.style?.getSourceAs<GeoJsonSource>("trail-src")?.setGeoJson(json)
    }

    // Camera, biển giới hạn, điểm cảnh báo
    LaunchedEffect(pois, styleReady, map) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val json = if (pois.isEmpty()) EMPTY else {
            "{\"type\":\"FeatureCollection\",\"features\":[" + pois.joinToString(",") { p ->
                val c = when (p.kind) { 2 -> "#B36BFF"; 3 -> "#FFFFFF"; else -> "#E8383D" }
                "{\"type\":\"Feature\",\"properties\":{\"c\":\"" + c + "\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[" + p.lng + "," + p.lat + "]}}"
            } + "]}"
        }
        m.style?.getSourceAs<GeoJsonSource>("poi-src")?.setGeoJson(json)
    }
    // Cử chỉ: khoá thì tắt hết, mở khoá thì cho kéo, zoom, xoay (không nghiêng)
    LaunchedEffect(map, free) {
        val m = map ?: return@LaunchedEffect
        m.uiSettings.setAllGesturesEnabled(false)
        if (free) {
            m.uiSettings.isScrollGesturesEnabled = true
            m.uiSettings.isZoomGesturesEnabled = true
            m.uiSettings.isDoubleTapGesturesEnabled = true
            m.uiSettings.isQuickZoomGesturesEnabled = true
            m.uiSettings.isRotateGesturesEnabled = true
            m.uiSettings.isTiltGesturesEnabled = false
        }
    }
    // Nút + / − khi mở khoá: zoom tương đối quanh tâm bản đồ hiện tại
    LaunchedEffect(zoomCmd) {
        val m = map ?: return@LaunchedEffect
        if (zoomCmd.n == 0 || !free) return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.zoomBy(zoomCmd.d.toDouble()), 250)
    }
    // Nút về vị trí xe khi mở khoá: đưa bản đồ về xe một lần, vẫn giữ trạng thái mở khoá
    LaunchedEffect(recenterTick) {
        if (recenterTick == 0) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val l = loc ?: return@LaunchedEffect
        val b = if (headingUp) ((cum % 360f) + 360f) % 360f else 0f
        val cp = CameraPosition.Builder().target(LatLng(l.latitude, l.longitude)).zoom(zoom - 1.0).bearing(b.toDouble()).tilt(if (tilt) 45.0 else 0.0).build()
        m.easeCamera(CameraUpdateFactory.newCameraPosition(cp), 600)
    }
    LaunchedEffect(loc, headingUp, zoom, tilt, map, free) {
        if (free) return@LaunchedEffect   // mở khoá: không giành quyền điều khiển camera với ngón tay
        val m = map ?: return@LaunchedEffect
        val l = loc ?: return@LaunchedEffect
        val b = if (headingUp) ((cum % 360f) + 360f) % 360f else 0f
        // tile vector 512px nên zoom nhỏ hơn 1 bậc so với bản đồ ảnh 256px để cùng tỉ lệ
        val cp = CameraPosition.Builder().target(LatLng(l.latitude, l.longitude)).zoom(zoom - 1.0).bearing(b.toDouble()).tilt(if (tilt) 45.0 else 0.0).build()
        m.easeCamera(CameraUpdateFactory.newCameraPosition(cp), 800)
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // Xe nằm giữa màn hình, quay theo hướng chạy (hoặc luôn hướng lên khi bản đồ xoay theo xe)
        Canvas(Modifier.fillMaxSize()) {
            if (loc != null) {
                var anchor = Offset(size.width / 2f, size.height / 2f)
                var rot = if (headingUp) 0f else ang
                val m = map
                if (free && m != null && camTick >= 0) {   // mở khoá: đặt xe đúng toạ độ trên bản đồ đã kéo, mũi tên xoay bù theo góc bản đồ
                    val pt = m.projection.toScreenLocation(LatLng(loc.latitude, loc.longitude))
                    anchor = Offset(pt.x, pt.y)
                    rot = ang - m.cameraPosition.bearing.toFloat()
                }
                if (marker == 1) rotate(rot, anchor) { drawArrow(anchor, dens, orange) }
                else rotate(rot, anchor) { drawTruck(anchor, dens, orange) }
            }
        }
        if (slow) {
            Text(
                if (goong) "Đang tải bản đồ Goong… kiểm tra mạng hoặc khoá Maptiles" else "Đang tải bản đồ MapTiler… kiểm tra mạng hoặc khoá API",
                color = Color(0xFF8A94B0), fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 90.dp)
            )
        }
    }
}

// Chỉnh style MapTiler sau khi nạp (dựa trên cấu trúc OpenMapTiles: lớp "transportation", "transportation_name", "poi", "housenumber"):
// - tên đường cỡ vừa phải, viền chữ dày hơn cho dễ đọc
// - hiện nhiều địa điểm hơn (cửa hàng, siêu thị, cây xăng, nhà ga...) từ mức zoom 14,5 và số nhà từ mức 16,5 nếu dữ liệu có
// - bản đồ tối: tô sáng các đường (trừ viền, đường sắt, phà) để nổi trên nền tối, đường lớn sáng hơn đường nhỏ
private fun tuneStyle(style: org.maplibre.android.maps.Style, dark: Boolean) {
    try {
        for (layer in style.layers) {
            if (layer is SymbolLayer) {
                when (layer.sourceLayer) {
                    "transportation_name" -> layer.setProperties(
                        PropertyFactory.textSize(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(11, 10f), Expression.stop(14, 12f), Expression.stop(16, 14f), Expression.stop(19, 17f)
                            )
                        ),
                        PropertyFactory.textHaloWidth(2f)
                    )
                    "poi" -> {
                        layer.setMinZoom(14.5f)
                        layer.setFilter(Expression.literal(true))   // bỏ lọc theo thứ hạng để hiện nhiều địa điểm hơn
                        layer.setProperties(
                            PropertyFactory.textSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(15, 11f), Expression.stop(17, 13f), Expression.stop(19, 15f)
                                )
                            ),
                            PropertyFactory.textOptional(true),
                            PropertyFactory.textHaloWidth(1.8f)
                        )
                    }
                    "housenumber" -> {
                        layer.setMinZoom(16.5f)
                        layer.setProperties(
                            PropertyFactory.textSize(
                                Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(16, 10f), Expression.stop(19, 14f))
                            )
                        )
                    }
                }
            }
            if (dark && layer is LineLayer && layer.sourceLayer == "transportation") {
                val id = layer.id.lowercase()
                if (id.contains("casing") || id.contains("outline") || id.contains("rail") || id.contains("ferry") ||
                    id.contains("aeroway") || id.contains("tunnel")) continue
                val col = when {
                    id.contains("motorway") || id.contains("trunk") -> "#FFC46B"
                    id.contains("primary") -> "#FFFFFF"
                    id.contains("secondary") -> "#F0F3F8"
                    id.contains("tertiary") -> "#DDE3EC"
                    id.contains("path") || id.contains("pedestrian") || id.contains("track") || id.contains("steps") -> "#98A2B3"
                    else -> "#CBD3DF"
                }
                layer.setProperties(PropertyFactory.lineColor(col))
            }
        }
    } catch (e: Exception) {
        android.util.Log.d("MapLibreView", "tuneStyle: " + e.message)
    }
}
