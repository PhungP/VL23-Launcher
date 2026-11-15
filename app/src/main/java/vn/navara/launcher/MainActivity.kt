@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package vn.navara.launcher

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.BatteryChargingFull

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.BiasAlignment
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Màu lấy từ Theme (đổi trong Cài đặt giao diện)
private val Acc: Color @Composable get() = LocalTheme.current.accent
private val Acc2: Color @Composable get() = LocalTheme.current.accent2
private val Ink: Color @Composable get() = LocalTheme.current.ink
private val Muted: Color @Composable get() = LocalTheme.current.muted
private val PanelHi: Color @Composable get() = LocalTheme.current.panelHi

// Tăng giá trị mỗi lần bấm nút Home để quay về trang chủ
val homeEvents = MutableStateFlow(0)

data class GpsState(val loc: Location? = null, val fix: Boolean = false, val sats: Int = 0, val on: Boolean = true, val kmh: Int = 0)

@Composable
fun rememberGps(): GpsState {
    val l by LocationHolder.loc.collectAsState()
    val sats by LocationHolder.sats.collectAsState()
    val on by LocationHolder.gpsOn.collectAsState()
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    return GpsState(l, tick >= 0 && hasFix(l), sats, on, gpsKmh(l))
}

fun gpsShort(g: GpsState): String = when {
    !g.on -> "GPS đang tắt"
    g.fix -> if (g.sats > 0) "GPS ${g.sats} vệ tinh" else "Vị trí qua mạng"
    else -> "GPS đang tìm sóng…"
}

fun gpsLabel(g: GpsState): String =
    if (g.on && g.fix) gpsShort(g) + " • ±${g.loc?.accuracy?.toInt() ?: 0} m" else gpsShort(g)

class Nav(val page: Int, val go: (Int) -> Unit, val settings: () -> Unit, val dialer: () -> Unit, val order: List<String>)

class MainActivity : ComponentActivity() {
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            LocationHolder.start(applicationContext)
            ObdManager.restart(applicationContext, loadPrefs(applicationContext).obdSource)
        }

    override fun onResume() {
        super.onResume()
        MediaRefresh.tick++   // quay lại launcher (ví dụ từ Foobar2000): đọc lại bài đang phát ngay
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPerms()
        addOnNewIntentListener { homeEvents.value = homeEvents.value + 1 }
        setContent { Root { hide -> applyBars(hide) } }
    }

    private fun applyBars(hide: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            c.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Deprecated("Launcher: giữ nguyên màn hình chính")
    override fun onBackPressed() {}

    private fun requestPerms() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            need += Manifest.permission.BLUETOOTH_SCAN
            need += Manifest.permission.BLUETOOTH_CONNECT
        }
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray()) else LocationHolder.start(applicationContext)
    }
}

// ================= Icon app dạng trắng (silhouette) =================
data class AppEntry(val label: String, val pkg: String, val white: ImageBitmap, val color: ImageBitmap)

private fun toWhite(b: Bitmap, diff: Boolean): Bitmap {
    val n = b.width
    val px = IntArray(n * n)
    b.getPixels(px, 0, n, 0, 0, n, n)
    var useDiff = false
    var br = 0f; var bg = 0f; var bb = 0f
    if (diff) {
        val c = listOf(px[8 * n + 8], px[8 * n + n - 9], px[(n - 9) * n + 8], px[(n - 9) * n + n - 9])
        val a = c.map { (it ushr 24) / 255f }.average().toFloat()
        if (a >= 0.4f) {
            useDiff = true
            br = c.map { (it shr 16) and 0xFF }.average().toFloat()
            bg = c.map { (it shr 8) and 0xFF }.average().toFloat()
            bb = c.map { it and 0xFF }.average().toFloat()
        }
    }
    for (i in px.indices) {
        val p = px[i]
        val a = (p ushr 24) / 255f
        var o = a
        if (useDiff) {
            val d = (abs(((p shr 16) and 0xFF) - br) + abs(((p shr 8) and 0xFF) - bg) + abs((p and 0xFF) - bb)) / 765f
            o = min(1f, d * 2.5f) * a
        }
        px[i] = ((o * 255).toInt() shl 24) or 0xFFFFFF
    }
    val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, n, 0, 0, n, n)
    return out
}

fun whiteGlyph(d: Drawable): Bitmap {
    val n = 160
    if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
        var layer: Drawable? = null
        if (Build.VERSION.SDK_INT >= 33) layer = d.monochrome
        if (layer == null) layer = d.foreground
        if (layer != null) {
            val big = layer.toBitmap(n * 3 / 2, n * 3 / 2)
            return toWhite(Bitmap.createBitmap(big, n / 4, n / 4, n, n), false)
        }
    }
    return toWhite(d.toBitmap(n, n), true)
}

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val ic = it.loadIcon(pm)
            AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, whiteGlyph(ic).asImageBitmap(), ic.toBitmap(160, 160).asImageBitmap())
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun open(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

fun launchPkg(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { open(ctx, it) }
}

private fun now(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun mmss(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun clockText(): String {
    var t by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) { while (true) { t = now(); delay(1000) } }
    return t
}

// Thẻ kiểu đồng hồ xe: nền xanh đen, viền mảnh; đang chọn thì viền cam; ô đặc biệt (special) viền xanh lá
@Composable
fun Modifier.card(shape: Shape = RoundedCornerShape(14.dp), selected: Boolean = false, special: Boolean = false): Modifier {
    val t = LocalTheme.current
    val line = when {
        special -> t.green
        selected -> t.accent
        else -> t.ink.copy(alpha = if (t.mono != 0) 0.45f else 0.28f)
    }
    return this.clip(shape).background(t.panelG).border(if (selected || special) 2.5.dp else 1.5.dp, line, shape)
}

// Khung thiết kế rộng 640dp, cao tối thiểu 384dp (5:3, màn Navara 800x480 => x1.25); màn 4:3 800x600 cũng khớp
@Composable
fun Root(applyBars: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val obd = rememberObd()
    var prefs by remember { mutableStateOf(loadPrefs(ctx)) }
    var bg by remember { mutableStateOf<ImageBitmap?>(null) }
    var settings by remember { mutableStateOf(false) }
    var dialer by remember { mutableStateOf(false) }
    val theme = remember(prefs, bg) { Theme(prefs, bg) }
    val update: (UiPrefs) -> Unit = { prefs = it; savePrefs(ctx, it) }

    LaunchedEffect(Unit) {
        bg = withContext(Dispatchers.IO) { loadBg(ctx) }
        LocationHolder.start(ctx)
        WeatherManager.start(ctx)
        TrafficAlert.start(ctx)
    }
    LaunchedEffect(prefs.trafficAlert) { TrafficAlert.enabled = prefs.trafficAlert }
    LaunchedEffect(prefs.loadSource) { ObdManager.loadSource = prefs.loadSource }
    LaunchedEffect(prefs.gearTrim) { ObdManager.gearTrim = prefs.gearTrim }
    LaunchedEffect(prefs.obdSource) { ObdManager.restart(ctx, prefs.obdSource) }
    LaunchedEffect(prefs.hideBar) { applyBars(prefs.hideBar) }

    BoxWithConstraints(Modifier.fillMaxSize().background(theme.bg)) {
        val d = minOf(constraints.maxWidth / 640f, constraints.maxHeight / 384f)
        CompositionLocalProvider(LocalTheme provides theme, LocalDensity provides Density(d, theme.scale), LocalPrefUpdate provides update) {
            ProvideTextStyle(TextStyle(fontFamily = if (theme.mono == 0) ClusterFont else FontFamily.Default)) {
            Box(Modifier.fillMaxSize()) {
                Background()
                LauncherApp(update, { settings = true }, { dialer = true })
                if (settings) {
                    SettingsScreen(prefs, obd, update, { uri: Uri ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ctx.contentResolver.openInputStream(uri)?.use { i ->
                                    File(ctx.filesDir, "bg.jpg").outputStream().use { o -> i.copyTo(o) }
                                }
                            }
                            bg = withContext(Dispatchers.IO) { loadBg(ctx) }
                            update(prefs.copy(bgStyle = 3))
                        }
                    }, { uri: Uri ->
                        scope.launch {
                            withContext(Dispatchers.IO) { saveCarImage(ctx, uri) }
                            update(prefs.copy(carImg = 1, carVer = prefs.carVer + 1))
                        }
                    }) { settings = false }
                }
                if (dialer) DialerScreen { dialer = false }
            }
            }
        }
    }
}

@Composable
fun Background() {
    val t = LocalTheme.current
    if (t.mono != 0) return   // bộ Số đen / Số trắng: nền đen hoặc trắng trơn; các bộ còn lại dùng hình nền chọn trong Cài đặt
    when (t.p.bgStyle) {
        1 -> Topo(Modifier.fillMaxSize(), t.accent2.copy(alpha = if (t.p.dark) 0.35f else 0.25f), null)
        2 -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.bg, t.accent2.copy(alpha = 0.35f)))))
        3 -> {
            val img = t.bgImage
            if (img != null) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = floatArrayOf(0.35f, 0.55f, 0.75f)[t.p.dim.coerceIn(0, 2)])))
            }
        }
        else -> {}
    }
}

@Composable
fun TitleBar(obd: ObdData) {
    val t = LocalTheme.current
    val ok = obd.status == ObdManager.OK
    val g = rememberGps()
    Row(
        Modifier.fillMaxWidth().height(32.dp).background(t.panel)
            .drawBehind { drawLine(t.blue, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Carlinkit Tbox S1", color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(if (g.fix) "GPS ✓" else "GPS ---", color = t.muted, fontSize = 18.sp)
        Text(if (ok) "   OBD ✓" else "   OBD ---", color = t.muted, fontSize = 18.sp)
        Text("   " + clockText(), color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LauncherApp(update: (UiPrefs) -> Unit, openSettings: () -> Unit, openDialer: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTheme.current
    val obd = rememberObd()
    var media by remember { mutableStateOf(MediaInfo()) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    val order = parsePageOrder(t.p.pageOrder)
    val pager = rememberPagerState { order.size }
    val scope = rememberCoroutineScope()
    val home by homeEvents.collectAsState()
    val grant = { open(ctx, Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }
    LaunchedEffect(MediaRefresh.tick) {
        watchMediaSessions(ctx)
        while (true) { media = withContext(Dispatchers.IO) { readMedia(ctx) }
            if (media.playing) media.controller?.packageName?.let { MusicResume.remember(ctx, it) }
            delay(1000)
        }
    }
    LaunchedEffect(home) { if (home > 0) pager.animateScrollToPage(order.indexOf("home")) }
    // Ghi lại đường xe đi (kể cả khi đang ở trang khác) để vẽ lên bản đồ
    LaunchedEffect(Unit) { LocationHolder.loc.collect { Trail.add(it) } }

    // Âm báo (ở mọi trang): vượt tốc độ cho phép, hoặc sắp tới camera / biển giới hạn
    val tr by TrafficAlert.state.collectAsState()
    val gp = rememberGps()
    val spdNow = if (obd.status == ObdManager.OK) obd.speed else gp.kmh
    val overNow = t.p.trafficAlert && overLimit(spdNow, tr.limit)
    LaunchedEffect(overNow, t.p.alertSound) {
        while (overNow && t.p.alertSound) { TrafficAlert.beep(); delay(8000) }
    }
    LaunchedEffect(tr.poi, t.p.alertSound) {
        if (tr.poi != null && t.p.trafficAlert && t.p.alertSound) TrafficAlert.beep()
    }

    val go: (Int) -> Unit = { p -> scope.launch { pager.animateScrollToPage(p) } }
    val nav = Nav(pager.currentPage, go, openSettings, openDialer, order)

    Column(Modifier.fillMaxSize()) {
        if (t.p.titleBar) TitleBar(obd)
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            when (order[page]) {
                "home" -> HomePage(obd, media, grant, nav)
                "telemetry" -> TelemetryPage(obd, nav, update)
                "apps" -> AppsPage(apps, obd, nav)
                "map" -> MapPage(obd, nav, update)
                else -> MusicPage(media, grant, nav)
            }
        }
    }
}

fun weatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> Icons.Filled.WbSunny
    in 51..67, in 80..82 -> Icons.Filled.WaterDrop
    in 71..77, in 85..86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.FlashOn
    else -> Icons.Filled.Cloud
}

@Composable
fun HomePage(obd: ObdData, media: MediaInfo, onGrant: () -> Unit, nav: Nav) {
    // Bộ giao diện điện tử (Cài đặt > Bộ giao diện) dùng bố cục số 7 đoạn; màu đen/trắng đã được Theme lo cho mọi trang
    val th = LocalTheme.current
    // Trang 1 có ảnh xe riêng nên không hiện hình nền chung: phủ nền đặc phía sau
    Box(Modifier.fillMaxSize().background(th.bg)) {
        if (th.mono != 0) HomeDigital(obd, media, onGrant, nav)
        else if (th.p.homeLayout == 1) HomeSport(obd, media, onGrant, nav)
        else HomeClassic(obd, media, onGrant, nav)
    }
}

// Dòng mô tả thời tiết; chưa lấy được thì nói rõ lý do (mạng, giờ hệ thống sai...), dữ liệu quá cũ thì đánh dấu
@Composable
fun weatherLine(ww: Weather?): String {
    val err by WeatherManager.error.collectAsState()
    val upd by WeatherManager.updatedAt.collectAsState()
    if (ww == null) return if (err.isEmpty()) "Đang lấy thời tiết…" else "Chưa lấy được thời tiết: $err"
    val stale = upd > 0L && System.currentTimeMillis() - upd > 40 * 60_000L
    return weatherText(ww.code) + (if (ww.place.isNotEmpty()) " • " + ww.place else "") + (if (stale) " • (cũ)" else "")
}

// Ảnh xe ở trang 1: hiện trọn cả chiếc xe; chỉ làm mờ nhẹ bốn góc để hoà vào nền đen
@Composable
private fun HeroPhoto(modifier: Modifier) = CarImage(modifier, 0.80f)

// Ô đang phát nhỏ, không khung: ảnh bìa, tên bài, ca sĩ, album. Bấm vào để sang trang phát nhạc (chưa cấp quyền thì mở cấp quyền)
@Composable
private fun MiniNowPlaying(media: MediaInfo, onGrant: () -> Unit, nav: Nav) {
    val art = media.art
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
            if (!media.hasAccess) onGrant() else nav.order.indexOf("music").let { if (it >= 0) nav.go(it) }
        }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, tint = Ink, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            when {
                !media.hasAccess -> Text("Chạm để cấp quyền đọc nhạc đang phát", color = Ink, fontSize = 16.sp, maxLines = 2)
                media.title.isEmpty() -> Text("Chưa có nhạc đang phát", color = Muted, fontSize = 17.sp)
                else -> {
                    Text(media.title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (media.artist.isNotEmpty()) Text(media.artist, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (media.album.isNotEmpty()) Text(media.album, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HomeClassic(obd: ObdData, media: MediaInfo, onGrant: () -> Unit, nav: Nav) = HomeCluster(obd, media, onGrant, nav)

// Trang 1 bố cục thể thao (như bản v36, đã bỏ dải số dưới đồng hồ): bên trái đồng hồ vòng tua dạng cung có la bàn và số cấp lớn ở giữa,
// bốn ô chỉ số; bên phải giờ, thời tiết, ảnh xe, ô đang phát.
@Composable
private fun HomeSport(obd: ObdData, media: MediaInfo, onGrant: () -> Unit, nav: Nav) {
    val ww by WeatherManager.state.collectAsState()
    val obdOk = obd.status == ObdManager.OK
    val temp = if (ww != null) "${ww!!.temp}°C" else "--"
    val line = weatherLine(ww)
    val rpm = smoothF(obd.rpm.toFloat(), 200)
    val boost = smoothF(obd.boost ?: 0f, 240)
    val load = smoothF((obd.load ?: 0).toFloat(), 240)
    val heading = rememberHeading()
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SportGauge(rpm, obd.gear, obdOk, heading, Modifier.fillMaxWidth().weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("BOOST", if (obdOk) "%.2f bar".format(boost) else "--", if (obdOk) boost / 2f else null, Modifier.weight(1f))
                StatTile("LOAD", if (obdOk && obd.load != null) "${load.roundToInt()}%" else "--", if (obdOk) load / 100f else null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cool = obd.coolant
                StatTile("COOLANT", if (obdOk && cool != null) "$cool°C" else "--", if (obdOk && cool != null) (cool - 40) / 80f else null, Modifier.weight(1f), warn = cool != null && cool >= 105)
                StatTile("BATTERY", if (obdOk && obd.volt != null) "%.1f V".format(obd.volt) else "--", null, Modifier.weight(1f))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(clockText(), color = Ink, fontSize = 60.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ww != null) weatherIcon(ww!!.code) else Icons.Filled.Cloud, null, tint = Ink, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(temp, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(line, color = Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            HeroPhoto(Modifier.fillMaxWidth().weight(1f))
            MiniNowPlaying(media, onGrant, nav)
        }
    }
}

@Composable
private fun DigiBar(label: String, value: String, f: Float) {
    val t = LocalTheme.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = t.muted, fontSize = 14.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        LedBar(f, t.ink, t.ink.copy(alpha = 0.08f), Modifier.fillMaxWidth().height(9.dp))
    }
}

// Trang 1 kiểu đồng hồ điện tử (nền đen chữ trắng hoặc nền trắng chữ đen): số 7 đoạn cho vòng tua, tốc độ, số, giờ
@Composable
private fun HomeDigital(obd: ObdData, media: MediaInfo, onGrant: () -> Unit, nav: Nav) {
    val t = LocalTheme.current
    val w by WeatherManager.state.collectAsState()
    val g = rememberGps()
    val ok = obd.status == ObdManager.OK
    val spd = (if (ok) obd.speed else g.kmh).coerceIn(0, 999)
    val off = t.ink.copy(alpha = 0.08f)
    val mono = FontFamily.Monospace
    val rpmShow = smoothF(obd.rpm.toFloat(), 200).roundToInt().coerceIn(0, 9999)
    val boost = smoothF(obd.boost ?: 0f, 240)
    val temp = if (w != null) "${w!!.temp}°C" else "--"
    Column(Modifier.fillMaxSize().padding(12.dp)) {
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("RPM", color = t.muted, fontSize = 14.sp, fontFamily = mono, letterSpacing = 2.sp)
                    SevenSeg(if (ok) "%4d".format(rpmShow) else "----", 58.dp, t.ink, off)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("GEAR", color = t.muted, fontSize = 14.sp, fontFamily = mono, letterSpacing = 2.sp)
                    SevenSeg(if (ok) (obd.gear?.toString() ?: "-") else "-", 58.dp, t.ink, off)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("SPEED", color = t.muted, fontSize = 14.sp, fontFamily = mono, letterSpacing = 2.sp)
                    SevenSeg("%3d".format(spd), 40.dp, t.ink, off)
                }
                Text("km/h", color = t.muted, fontSize = 17.sp, fontFamily = mono, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
            }
            DigiBar("BOOST", "%.2f bar".format(boost), boost / 2f)
            DigiBar("LOAD", obd.load?.let { "$it%" } ?: "--", (obd.load ?: 0) / 100f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("COOL " + (obd.coolant?.let { "$it°C" } ?: "--"), color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = mono)
                Text("BATT " + (obd.volt?.let { "%.1fV".format(it) } ?: "--"), color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = mono)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                SevenSegClock(clockText(), 78.dp, t.ink, off)
                Spacer(Modifier.height(8.dp))
                Text(temp + "  " + weatherLine(w), color = t.ink, fontSize = 17.sp, fontFamily = mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            NowPlayingCard(media, onGrant)
        }
    }
    Spacer(Modifier.height(6.dp))
    GearLed(if (ok) obd.gear else null, Modifier.fillMaxWidth(), 48.dp)
    }
}

@Composable
fun BoostGauge(boost: Float, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val acc = Acc
    val track = Ink.copy(alpha = 0.16f)
    val muted = Muted
    Canvas(modifier) {
        val stroke = size.width * 0.07f
        val r = minOf(size.width / 2 - stroke * 1.9f, size.height - stroke * 1.65f - 14.dp.toPx())
        if (r <= 0f) return@Canvas
        val c = Offset(size.width / 2, size.height - stroke * 0.5f)
        val tl = Offset(c.x - r, c.y - r)
        val box = Size(r * 2, r * 2)
        drawArc(track, 180f, 180f, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
        val f = (boost / 2f).coerceIn(0f, 1f)
        drawArc(acc, 180f, maxOf(180f * f, 2f), false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
        val a = Math.toRadians(180.0 + 180.0 * f)
        val tip = Offset(c.x + (r * 0.8f * cos(a)).toFloat(), c.y + (r * 0.8f * sin(a)).toFloat())
        drawLine(acc, c, tip, strokeWidth = stroke * 0.22f, cap = StrokeCap.Round)
        drawCircle(acc, stroke * 0.6f, c)
        val lr = r + stroke * 1.15f
        listOf("0" to 180.0, "1" to 270.0, "2" to 360.0).forEach { (t, deg) ->
            val q = Math.toRadians(deg)
            drawText(
                textMeasurer = tm, text = t,
                topLeft = Offset(c.x + (lr * cos(q)).toFloat() - 5.dp.toPx(), c.y + (lr * sin(q)).toFloat() - 9.dp.toPx()),
                style = TextStyle(color = muted, fontSize = 18.sp)
            )
        }
    }
}

// Thanh thông số màu xanh dùng chung cho TURBO BOOST và ENGINE LOAD
@Composable
fun StatBar(label: String, value: String, f0: Float) {
    val f = smoothF(f0, 240)
    Column(Modifier.fillMaxWidth(0.9f)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Muted, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = Acc2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.copy(alpha = 0.16f))) {
            Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acc2))
        }
    }
}

@Composable
fun LoadBar(load: Int?) {
    val f = ((load ?: 0) / 100f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(0.9f)) {
        Row(Modifier.fillMaxWidth()) {
            Text("ENGINE LOAD", color = Muted, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text(load?.let { "$it%" } ?: "--", color = Acc, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.copy(alpha = 0.16f))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Brush.horizontalGradient(listOf(Acc2, Acc))))
        }
    }
}

// Hình xe Navara (ảnh thật: res/drawable-nodpi/navara_photo.png)
@Composable
fun TruckArt(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.navara_photo), null,
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
    }
}

// Thanh tiến trình có núm tròn
@Composable
fun Bar(f: Float, modifier: Modifier = Modifier) {
    val track = Ink.copy(alpha = 0.16f)
    val fill = Acc2
    Canvas(modifier.fillMaxWidth().height(24.dp)) {
        val h = 6.dp.toPx()
        val y = (size.height - h) / 2
        val x = size.width * f.coerceIn(0f, 1f)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawRoundRect(fill, Offset(0f, y), Size(x, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawCircle(fill, 10.dp.toPx(), Offset(maxOf(x, 10.dp.toPx()), size.height / 2))
    }
}

@Composable
fun NowPlayingCard(media: MediaInfo, onGrant: () -> Unit) {
    val art = media.art
    Row(
        Modifier.fillMaxWidth().card().clickable(enabled = !media.hasAccess) { onGrant() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)).background(PanelHi), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, tint = Acc2, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ĐANG PHÁT", color = Acc, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            when {
                !media.hasAccess -> Text("Chạm để cấp quyền đọc nhạc đang phát", color = Ink, fontSize = 18.sp)
                media.title.isEmpty() -> Text("Chưa có nhạc đang phát", color = Muted, fontSize = 18.sp)
                else -> {
                    Text(media.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(media.artist, color = Muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Bar(if (media.dur > 0) media.pos.toFloat() / media.dur else 0f)
                }
            }
        }
    }
}

@Composable
fun AppsPage(apps: List<AppEntry>, obd: ObdData, nav: Nav) {
    val ctx = LocalContext.current
    val t = LocalTheme.current
    var sel by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        // Cỡ biểu tượng theo "Kích thước icon" trong Cài đặt: Nhỏ 6 cột, Vừa 5 cột (mặc định) đều hiện đủ 3 hàng kèm tên; Lớn và Rất lớn 4 cột, 2 hàng
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sz = t.p.iconSize.coerceIn(0, 3)
            val cols = intArrayOf(6, 5, 4, 4)[sz]
            val rowsVis = floatArrayOf(3f, 3f, 2f, 2f)[sz]
            val cellW = (maxWidth - 32.dp - 8.dp * (cols - 1)) / cols
            val rowH = maxHeight / rowsVis
            // phần dành cho chữ tên ứng dụng (15sp theo cỡ chữ) + khoảng đệm; phần còn lại của mỗi hàng giao cho biểu tượng
            val labelH = (15f * t.scale * 1.25f + 5f).dp
            val tile = minOf(cellW - 6.dp, rowH - labelH)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps, key = { it.pkg }) { a ->
                    Column(
                        Modifier.clickable { sel = a.pkg; launchPkg(ctx, a.pkg) }.padding(1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val gk = if (t.p.navaraIcons) navaraIconKey(a.pkg, a.label) else null
                        Box(Modifier.size(tile).card(selected = a.pkg == sel), contentAlignment = Alignment.Center) {
                            if (gk != null) NavaraGlyph(gk, Modifier.size(tile * 0.72f), selected = a.pkg == sel)
                            else if (t.p.whiteIcons) Image(a.white, a.label, (if (t.p.iconBlue && t.mono == 0 && a.pkg != sel) Modifier.size(tile * 0.76f).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent { drawContent(); drawRect(Brush.verticalGradient(listOf(Color.White, Color.White, t.blueLight)), blendMode = BlendMode.SrcIn) } else Modifier.size(tile * 0.76f)), colorFilter = if (a.pkg == sel) androidx.compose.ui.graphics.ColorFilter.tint(t.accent) else if (t.mono != 0) androidx.compose.ui.graphics.ColorFilter.tint(t.ink) else null)
                            else Image(a.color, a.label, Modifier.size(tile * 0.84f))
                        }
                        Text(a.label, color = if (a.pkg == sel) Acc else Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        BottomBar(nav, obd.boost, true, Modifier.fillMaxWidth())
    }
}

@Composable
fun Chip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.card(RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        content = content
    )
}

// Nút tròn và ô thông tin trên bản đồ: màu theo bản đồ (sáng/tối), không theo giao diện chung
@Composable
private fun MapBtn(bg: Color, border: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(bg).border(2.dp, border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun MapChip(bg: Color, modifier: Modifier = Modifier, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (border != null) Modifier.border(2.dp, border, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        content = content
    )
}

// Trang bản đồ: chỉ có bản đồ (kèm nút zoom, giới hạn tốc độ, cảnh báo camera)
@Composable
fun MapPage(obd: ObdData, nav: Nav, update: (UiPrefs) -> Unit) {
    var zoom by remember { mutableStateOf(17) }
    val ctx = LocalContext.current
    val t = LocalTheme.current
    // Màu bản đồ độc lập với giao diện: 0 theo giao diện, 1 luôn sáng, 2 luôn tối
    val mapDark = when (t.p.mapStyle) { 1 -> false; 2 -> true; else -> if (t.mono != 0) t.mono == 1 else true }
    val hu = t.p.mapHeadingUp
    val ovBg = if (mapDark) Color(0xF2141A2E) else Color(0xF2FFFFFF)
    val ovInk = if (mapDark) Color(0xFFF2F5FA) else Color(0xFF1A1F2E)
    val g = rememberGps()
    val l = g.loc
    var showNav by remember { mutableStateOf(false) }
    val ns by Navigator.state.collectAsState()
    val spd = if (obd.status == ObdManager.OK) obd.speed else g.kmh
    val tr by TrafficAlert.state.collectAsState()
    val on = t.p.trafficAlert
    val lim = if (on) tr.limit else null
    val over = overLimit(spd, lim)
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(over) {
        flash = false
        while (over) { flash = !flash; delay(500) }
        flash = false
    }

    val dockH = (3 + dockHeightDp(t.p.iconSize)).dp
    Box(Modifier.fillMaxSize()) {
            val mapKey = t.p.mapTilerKey.ifBlank { BuildConfig.MAPTILER_KEY }.trim()
            if (t.p.mapEngine == 0 && mapKey.isNotBlank()) {
                MapLibrePane(Modifier.fillMaxSize(), l, mapDark, zoom, if (on) tr.pois else emptyList(), hu, t.p.mapMarker, mapKey, t.p.mapTheme, t.p.mapTilt)
            } else {
                MapPane(Modifier.fillMaxSize(), l, mapDark, zoom, 0.5f, if (on) tr.pois else emptyList(), hu, t.p.mapMarker)
                if (t.p.mapEngine == 0) {
                    MapChip(ovBg, Modifier.align(Alignment.BottomCenter).padding(bottom = dockH + 10.dp)) {
                        Text("Chưa có khoá MapTiler (vào Cài đặt) – đang dùng bản đồ OSM đơn giản", color = ovInk, fontSize = 15.sp)
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(bottom = dockH)) {
            // Dẫn đường chỉ có trên bản đồ MapLibre (cần vẽ tuyến lên bản đồ vector)
            val naviOk = t.p.mapEngine == 0 && mapKey.isNotBlank()
            // Nút zoom to; bản đồ không nhận thao tác chạm/kéo nên vuốt đổi trang không bị nhầm
            Column(Modifier.align(Alignment.CenterEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (naviOk) {
                    MapBtn(ovBg, if (ns.route != null) t.accent else ovInk, { showNav = true }) {
                        Icon(Icons.Filled.Navigation, null, tint = t.accent, modifier = Modifier.size(32.dp))
                    }
                }
                listOf("+" to 1, "−" to -1).forEach { (label, d) ->
                    MapBtn(ovBg, ovInk, { zoom = (zoom + d).coerceIn(12, 19) }) {
                        Text(label, color = ovInk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            MapChip(
                ovBg,
                Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clickable(enabled = !g.on) { open(ctx, Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                if (l != null) t.green else null
            ) {
                Text("VỊ TRÍ HIỆN TẠI", color = ovInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !g.on -> "GPS tắt – chạm để bật"
                        l != null -> "%.5f, %.5f".format(l.latitude, l.longitude)
                        else -> "Đang tìm GPS…"
                    },
                    color = ovInk, fontSize = 17.sp
                )
            }
            // Nút đổi nhanh: màu bản đồ sáng/tối và hướng bản đồ (Bắc ở trên / theo hướng xe)
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = if (lim != null) 86.dp else 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MapBtn(ovBg, ovInk, { update(t.p.copy(mapStyle = if (mapDark) 1 else 2)) }) {
                    Icon(if (mapDark) Icons.Filled.LightMode else Icons.Filled.DarkMode, null, tint = ovInk, modifier = Modifier.size(30.dp))
                }
                // Nút 2D / 3D: 3D nghiêng bản đồ để nhìn xa phía trước (bản đồ MapLibre); hướng bản đồ chỉnh trong Cài đặt
                val tilt = t.p.mapTilt
                Row(Modifier.height(54.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, ovInk, RoundedCornerShape(12.dp)).background(ovBg)) {
                    listOf("2D", "3D").forEachIndexed { i, label ->
                        val on = (i == 1) == tilt
                        Box(
                            Modifier.fillMaxHeight().background(if (on) t.accent else Color.Transparent)
                                .clickable { update(t.p.copy(mapTilt = i == 1)) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, color = if (on) t.onAccent else ovInk, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (lim != null) SpeedSign(lim, flash, Modifier.align(Alignment.TopEnd).padding(10.dp))
            val poi = tr.poi
            if (on && poi != null) PoiBanner(poi, tr.poiDist, Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 74.dp))
            MapChip(ovBg, Modifier.align(Alignment.BottomStart).padding(10.dp).clickable { open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))) }) {
                Icon(Icons.Filled.LocationOn, null, tint = t.accent2, modifier = Modifier.size(34.dp))
                Text("Maps", color = t.accent2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            // Dẫn đường: khung chỉ dẫn phía trên, thanh thời gian còn lại và nút Dừng ở dưới
            if (naviOk) {
                if (ns.route != null) {
                    NavBanner(ns, ovBg, ovInk, t.accent, t.onAccent, Modifier.align(Alignment.TopCenter).padding(top = 76.dp))
                    NavSummary(ns, ovBg, ovInk, { Navigator.stop() }, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                } else {
                    NavStatusChip(ns, ovBg, ovInk, Modifier.align(Alignment.TopCenter).padding(top = 76.dp))
                }
                if (showNav) NavSearchOverlay(ovBg, ovInk, t.accent, t.onAccent, l) { showNav = false }
            }
        }
        BottomBar(nav, null, false, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), mapDark)
    }
}

// Mở giao diện của ứng dụng đang phát nhạc (Foobar2000...) để người dùng chọn bài khác trong danh sách phát của nó.
// Ưu tiên màn hình mà chính ứng dụng nhạc đăng ký với media session; không có thì mở ứng dụng theo tên gói.
fun openPlayerApp(ctx: Context, c: MediaController?) {
    try {
        val pi = c?.sessionActivity
        if (pi != null) { pi.send(); return }
    } catch (e: Exception) { }
    try {
        val pkg = c?.packageName
        val li = if (pkg != null) ctx.packageManager.getLaunchIntentForPackage(pkg) else null
        if (li != null) { open(ctx, li); return }
    } catch (e: Exception) { }
    open(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
}

// Trang 4: chỉ chơi nhạc. Trái: ảnh album + thông số file. Phải: bài đang phát + điều khiển. Dưới cùng: spectrum.
@Composable
fun MusicPage(media: MediaInfo, onGrant: () -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var starting by remember { mutableStateOf(false) }
    val c = media.controller
    val art = media.art
    var perm by remember { mutableStateOf(AudioSpecReader.hasPermission(ctx)) }
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perm = it }
    var spec by remember { mutableStateOf<AudioSpec?>(null) }
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(media.title, media.artist, media.dur, perm) {
        spec = null
        tried = false
        if (perm && media.title.isNotEmpty()) {
            spec = withContext(Dispatchers.IO) { AudioSpecReader.read(ctx, media.title, media.artist, media.dur) }
            tried = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Trái: ảnh album phủ kín cả ô (rộng bằng ô, khoảng nửa trang); thông số bài hát nằm ở đáy ảnh ----
            Box(Modifier.weight(0.95f).fillMaxHeight().card().clickable { openPlayerApp(ctx, c) }, contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.MusicNote, null, tint = Acc2, modifier = Modifier.size(72.dp))
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                        .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 10.dp)
                ) {
                    val sp = spec
                    if (sp != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(sp.format.ifEmpty { "AUDIO" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            if (sp.bitrateKbps > 0) Text("  ${sp.bitrateKbps} kbps", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                        }
                        val chs = when (sp.channels) { 0 -> ""; 1 -> "Mono"; 2 -> "Stereo"; else -> "${sp.channels} kênh" }
                        val mb = if (sp.sizeBytes > 0) String.format(Locale.US, "%.1f MB", sp.sizeBytes / 1048576.0) else ""
                        val rate = if (sp.sampleRate > 0) khz(sp.sampleRate) + (if (sp.bitDepth > 0) " • ${sp.bitDepth} bit" else "") else ""
                        val line = listOf(rate, chs, mb).filter { it.isNotEmpty() }.joinToString(" • ")
                        if (line.isNotEmpty()) Text(line, color = Color(0xE6FFFFFF), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(
                            when {
                                !perm -> "Cần quyền đọc file nhạc để hiện bitrate và tần số lấy mẫu"
                                media.title.isEmpty() -> "Chưa có nhạc đang phát"
                                tried -> "Không đọc được thông số của bài này"
                                else -> "Đang đọc thông số…"
                            },
                            color = Color(0xE6FFFFFF), fontSize = 16.sp
                        )
                        if (!perm) {
                            Spacer(Modifier.height(6.dp))
                            Chip(Modifier.clickable { askPerm.launch(AudioSpecReader.permission()) }) {
                                Text("Cấp quyền", color = Acc2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // ---- Phải: bài đang phát ----
            Column(Modifier.weight(1.05f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.fillMaxWidth()) {
                    Text("ĐANG PHÁT", color = Acc, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (media.title.isEmpty()) (if (starting) "Đang khởi động trình phát…" else "Chưa có nhạc đang phát") else media.title,
                        color = if (media.title.isEmpty()) Muted else Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                    if (media.artist.isNotEmpty()) Text(media.artist, color = Muted, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (media.album.isNotEmpty()) {
                        Text("ALBUM", color = Acc, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        Text(media.album, color = Ink, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Ink, modifier = Modifier.size(48.dp).clickable { c?.transportControls?.skipToPrevious() })
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Acc)
                                                        .clickable {
                                if (c == null) {
                                    // chưa có trình phát nào chạy (vừa khởi động xe): đánh thức ứng dụng nhạc đã dùng lần trước và phát
                                    if (!starting) {
                                        starting = true
                                        scope.launch { MusicResume.start(ctx); starting = false }
                                    }
                                } else if (media.playing) c.transportControls.pause() else c.transportControls.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = LocalTheme.current.onAccent, modifier = Modifier.size(38.dp))
                    }
                    Icon(Icons.Filled.SkipNext, null, tint = Ink, modifier = Modifier.size(48.dp).clickable { c?.transportControls?.skipToNext() })
                }
                Spacer(Modifier.height(4.dp))
                Bar(if (media.dur > 0) media.pos.toFloat() / media.dur else 0f)
                Text(mmss(media.pos) + " / " + mmss(media.dur), color = Muted, fontSize = 17.sp)
            }
        }
        // ---- Dưới cùng: spectrum (hiệu ứng mô phỏng) ----
        Spectrum(media.playing, Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp, vertical = 4.dp), bands = 28, segs = 6, gap = 3f)
        BottomBar(nav, null, false, Modifier.fillMaxWidth())
    }
}

@Composable
fun Topo(modifier: Modifier, line: Color, route: Color?) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (k in 0 until 9) {
            val y0 = h * (0.08f + k * 0.11f)
            val path = Path().apply {
                moveTo(0f, y0)
                var x = 0f
                while (x < w) {
                    quadraticBezierTo(x + w / 8, y0 - 22f - 5f * k, x + w / 4, y0)
                    x += w / 4
                }
            }
            drawPath(path, line, style = Stroke(2f))
        }
        if (route != null) {
            val rp = Path().apply {
                moveTo(0f, h * 0.95f)
                cubicTo(w * 0.3f, h * 0.9f, w * 0.4f, h * 0.5f, w * 0.6f, h * 0.45f)
                cubicTo(w * 0.8f, h * 0.4f, w * 0.85f, h * 0.2f, w * 0.95f, 0f)
            }
            drawPath(rp, route, style = Stroke(6f, cap = StrokeCap.Round))
        }
    }
}

// Hiệu ứng thanh phổ âm thanh (11 dải x 12 vạch). Chạy khi đang phát nhạc, hạ xuống khi tạm dừng.
// Đây là hiệu ứng mô phỏng; muốn phổ thật cần quyền RECORD_AUDIO + android.media.audiofx.Visualizer.
@Composable
fun Spectrum(playing: Boolean, modifier: Modifier, bands: Int = 11, segs: Int = 12, gap: Float = 5f) {
    val shape = floatArrayOf(0.70f, 0.95f, 0.95f, 0.90f, 0.80f, 0.65f, 0.60f, 0.62f, 0.45f, 0.35f, 0.35f)
    val level = remember { FloatArray(bands) }
    val target = remember { FloatArray(bands) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(playing) {
        val rnd = java.util.Random()
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (n - last > 110_000_000L) {
                    last = n
                    for (i in 0 until bands) target[i] = if (playing) shape[i * shape.size / bands] * (0.30f + 0.70f * rnd.nextFloat()) else 0f
                }
                for (i in 0 until bands) {
                    val d = target[i] - level[i]
                    level[i] += d * (if (d > 0) 0.35f else 0.10f)
                }
                tick = n
            }
        }
    }
    val lit0 = Acc2
    val th = LocalTheme.current
    val offCol = if (th.mono != 0) th.ink.copy(alpha = 0.08f) else Color(0xFF1B120A)
    Canvas(modifier) {
        tick.hashCode()
        val bw = (size.width - gap * (bands - 1)) / bands
        val sh = (size.height - gap * (segs - 1)) / segs
        val hot = if (th.mono != 0) th.ink else Color(0xFFF2611A)
        for (b in 0 until bands) {
            val lit = Math.round(level[b] * segs)
            for (s in 0 until segs) {
                val x = b * (bw + gap)
                val y = size.height - (s + 1) * sh - s * gap
                val on = s < lit
                val c = if (on) (if (s >= segs - 3) hot else lit0) else offCol
                if (on) drawRoundRect(c.copy(alpha = 0.25f), Offset(x - 2f, y - 2f), Size(bw + 4f, sh + 4f), CornerRadius(5f))
                drawRoundRect(c, Offset(x, y), Size(bw, sh), CornerRadius(3f))
            }
        }
    }
}

@Composable
fun Player(media: MediaInfo, modifier: Modifier, onGrant: () -> Unit) {
    val c = media.controller
    val art = media.art
    Column(modifier.fillMaxWidth(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).card(), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.MusicNote, null, tint = Acc2, modifier = Modifier.size(36.dp))
        }
        Text(
            if (media.title.isEmpty()) "CHƯA CÓ NHẠC ĐANG PHÁT" else media.title.uppercase(),
            color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
        )
        if (!media.hasAccess) {
            Text("Chạm để cấp quyền đọc nhạc đang phát", color = Acc, fontSize = 17.sp, modifier = Modifier.clickable { onGrant() })
        } else {
            Text(media.artist, color = Muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SkipPrevious, null, tint = Ink, modifier = Modifier.size(44.dp).clickable { c?.transportControls?.skipToPrevious() })
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Acc)
                    .clickable { if (media.playing) c?.transportControls?.pause() else c?.transportControls?.play() },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = LocalTheme.current.onAccent, modifier = Modifier.size(36.dp))
            }
            Icon(Icons.Filled.SkipNext, null, tint = Ink, modifier = Modifier.size(44.dp).clickable { c?.transportControls?.skipToNext() })
        }
        Spacer(Modifier.height(2.dp))
        Bar(if (media.dur > 0) media.pos.toFloat() / media.dur else 0f)
        Text(mmss(media.pos) + " / " + mmss(media.dur), color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun MiniGauge(boost: Float, color: Color) {
    Canvas(Modifier.size(38.dp, 26.dp)) {
        val stroke = 5.dp.toPx()
        val d = size.width - stroke
        drawArc(color, 180f, 180f, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        val a = Math.toRadians(180.0 + 180.0 * (boost / 2f).coerceIn(0f, 1f))
        val c = Offset(size.width / 2, stroke / 2 + d / 2)
        drawLine(color, c, Offset(c.x + (d * 0.4f * cos(a)).toFloat(), c.y + (d * 0.4f * sin(a)).toFloat()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
fun NavItem(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit, ink: Color? = null, icon: @Composable (Color) -> Unit) {
    val c = if (active) Acc else (ink ?: Ink)
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(c)
        Box(Modifier.padding(top = 4.dp).width(30.dp).height(3.dp).background(if (active) Acc else Color.Transparent))
    }
}

// Chiều cao thanh dock (dp) theo cỡ icon; dải số ở trang chủ dùng cùng giá trị để ngang hàng với dock
fun dockHeightDp(iconSize: Int): Int = intArrayOf(26, 30, 34, 38)[iconSize.coerceIn(0, 3)] + 14

// Nhận dữ liệu OBD tối đa ~7 lần/giây: xe trả dữ liệu dồn dập nếu vẽ lại theo từng giá trị sẽ làm quá tải CPU của Tbox
// (nhạc giật, giao diện đứng). StateFlow tự bỏ qua các giá trị trung gian, chỉ lấy giá trị mới nhất sau mỗi lần nghỉ.
@Composable
fun rememberObd(): ObdData {
    val st = produceState(ObdManager.data.value) {
        ObdManager.data.collect { value = it; delay(140) }
    }
    return st.value
}

@Composable
// mapDark: null = theo giao diện; true/false = dock tối/sáng theo bản đồ (dùng ở trang bản đồ)
fun BottomBar(nav: Nav, boost: Float?, showBoost: Boolean, modifier: Modifier = Modifier, mapDark: Boolean? = null) {
    val ctx = LocalContext.current
    val t = LocalTheme.current
    val obd = rememberObd()
    val bi = intArrayOf(26, 30, 34, 38)[t.p.iconSize.coerceIn(0, 3)]
    val cluster = t.mono == 0
    val bg = if (mapDark != null) Color.Transparent else if (cluster) Color(0xFF050608).copy(alpha = t.pa) else when (mapDark) { null -> t.panel.copy(alpha = 0.96f); true -> Color(0xF2141A2E); false -> Color(0xF2FFFFFF) }
    val ink: Color? = if (mapDark == false) Color(0xFF1A1F2E) else if (cluster) null else when (mapDark) { null -> null; true -> Color(0xFFF2F5FA); false -> Color(0xFF1A1F2E) }
    val strip = if (cluster) Brush.horizontalGradient(listOf(t.blueDeep, t.blueLight, t.blueDeep)) else Brush.horizontalGradient(listOf(t.ink.copy(alpha = 0.4f), t.ink.copy(alpha = 0.4f)))
    Column(modifier) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(strip))
    Row(
        Modifier.fillMaxWidth().height(dockHeightDp(t.p.iconSize).dp).background(bg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val m = Modifier.weight(1f)
        // Các trang theo đúng thứ tự đã chọn trong Cài đặt, rồi tới Gọi và Cài đặt
        nav.order.forEachIndexed { i, id ->
            NavItem(pageName(id), nav.page == i, m, { nav.go(i) }, ink) { c ->
                when (id) {
                    "home" -> MiniGauge(obd.boost ?: 0f, c)
                    "telemetry" -> Icon(Icons.Filled.Speed, null, tint = c, modifier = Modifier.size(bi.dp))
                    "apps" -> Icon(Icons.Filled.Apps, null, tint = c, modifier = Modifier.size(bi.dp))
                    "map" -> Icon(Icons.Filled.LocationOn, null, tint = c, modifier = Modifier.size(bi.dp))
                    else -> Icon(Icons.Filled.MusicNote, null, tint = c, modifier = Modifier.size(bi.dp))
                }
            }
        }
        NavItem("Gọi", false, m, nav.dialer, ink) { Icon(Icons.Filled.Call, null, tint = it, modifier = Modifier.size(bi.dp)) }
        NavItem("Cài đặt", false, m, nav.settings, ink) { Icon(Icons.Filled.Settings, null, tint = it, modifier = Modifier.size(bi.dp)) }
    }
    }
}
