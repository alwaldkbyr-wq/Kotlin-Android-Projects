package com.mawaqeet.app.ui.components

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/* ───────────────────────── الخريطة (OpenStreetMap عبر osmdroid) ───────────────────────── */

object MapSetup {
    @Volatile private var done = false
    fun init(context: Context) {
        if (done) return
        val c = Configuration.getInstance()
        c.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        c.userAgentValue = context.packageName
        c.osmdroidBasePath = File(context.cacheDir, "osm")
        c.osmdroidTileCache = File(context.cacheDir, "osm/tiles")
        done = true
    }
}

/**
 * خريطة عالمية. [update] يُستدعى عند كل إعادة تركيب لإعادة رسم الطبقات:
 * امسح `map.overlays` ثم أضف Polygon / Polyline / Marker (من org.osmdroid.views.overlay).
 * تعمل دون إنترنت: اليابسة من بيانات Natural Earth المدمجة، وما يُرسم فوقها محسوب محلياً.
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    centerLat: Double = 20.0,
    centerLon: Double = 10.0,
    zoom: Double = 2.4,
    dark: Boolean = false,
    update: (MapView) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // ألوان الخريطة من الثيم: محيط، ويابسة، وحدود
    val g = com.mawaqeet.app.ui.theme.AppTheme.glass
    val o = com.mawaqeet.app.ui.theme.AppTheme.ornament
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val ocean = androidx.compose.ui.graphics.lerp(g.bgBottom, primary, if (dark) 0.10f else 0.18f)
    val land = if (dark) androidx.compose.ui.graphics.lerp(g.bgBottom, androidx.compose.ui.graphics.Color.White, 0.16f)
               else androidx.compose.ui.graphics.lerp(g.bgTop, androidx.compose.ui.graphics.Color.White, 0.75f)
    val border = androidx.compose.ui.graphics.lerp(land, o.muted, 0.55f)
    val grid = o.muted.copy(alpha = 0.22f)
    val colors = intArrayOf(ocean.toArgbInt(), land.toArgbInt(), border.toArgbInt(), grid.toArgbInt(), o.gold.copy(alpha = 0.45f).toArgbInt())
    val map = remember {
        MapSetup.init(context)
        MapView(context).apply {
            // خريطة دون اتصال: لا تُحمَّل صور من الإنترنت، واليابسة مرسومة من بيانات مدمجة
            setUseDataConnection(false)
            overlayManager.tilesOverlay.isEnabled = false
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 1.5
            maxZoomLevel = 9.0
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(centerLat, centerLon))
            // أقل تكبير يملأ ارتفاع الشاشة حتى لا تبدو الخريطة شريطاً عائماً وسط فراغ
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val hPx = v.height.toDouble()
                if (hPx > 0) {
                    val tile = org.osmdroid.util.TileSystem.getTileSize().toDouble()
                    val fill = kotlin.math.ln(hPx / tile) / kotlin.math.ln(2.0) + 0.05
                    val minZ = maxOf(1.0, fill)
                    if (kotlin.math.abs(minZoomLevel - minZ) > 0.01) {
                        minZoomLevel = minZ
                        if (zoomLevelDouble < minZ) controller.setZoom(minZ)
                    }
                }
            }
        }
    }
    val base = remember(colors.toList()) {
        map.setBackgroundColor(colors[0])
        WorldBase.overlay(context, map, colors[1], colors[2], colors[3], colors[4])
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose { lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }
    AndroidView(factory = { map }, modifier = modifier, update = { m ->
        update(m)
        // طبقة اليابسة دائماً في الأسفل حتى لو مسحت الشاشة طبقاتها
        if (m.overlays.firstOrNull() !== base) {
            m.overlays.remove(base)
            m.overlays.add(0, base)
        }
        m.invalidate()
    })
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/** بيانات الدول (Natural Earth 1:110m، ملكية عامة) من assets/world.bin */
object WorldBase {
    @Volatile private var rings: List<List<GeoPoint>>? = null

    private fun load(context: Context): List<List<GeoPoint>> {
        rings?.let { return it }
        val out = ArrayList<List<GeoPoint>>()
        runCatching {
            val bytes = context.assets.open("world.bin").use { it.readBytes() }
            val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val countries = b.int
            repeat(countries) {
                val nr = b.short.toInt()
                repeat(nr) {
                    val n = b.int
                    val pts = ArrayList<GeoPoint>(n)
                    repeat(n) {
                        val lon = b.short * 180.0 / 32767.0
                        val lat = b.short * 90.0 / 32767.0
                        pts.add(GeoPoint(lat.coerceIn(-85.0, 85.0), lon))
                    }
                    out.add(pts)
                }
            }
        }
        rings = out
        return out
    }

    /** طبقة واحدة تجمع اليابسة وخطوط الطول والعرض */
    fun overlay(context: Context, map: MapView, land: Int, border: Int, grid: Int, equator: Int): org.osmdroid.views.overlay.FolderOverlay {
        val folder = org.osmdroid.views.overlay.FolderOverlay()
        // شبكة كل 30°
        for (lat in -60..60 step 30) {
            folder.add(org.osmdroid.views.overlay.Polyline(map).apply {
                setPoints((-180..180 step 10).map { GeoPoint(lat.toDouble(), it.toDouble()) })
                outlinePaint.color = if (lat == 0) equator else grid
                outlinePaint.strokeWidth = if (lat == 0) 2f else 1f
                infoWindow = null
            })
        }
        for (lon in -180..150 step 30) {
            folder.add(org.osmdroid.views.overlay.Polyline(map).apply {
                setPoints((-84..84 step 6).map { GeoPoint(it.toDouble(), lon.toDouble()) })
                outlinePaint.color = grid
                outlinePaint.strokeWidth = 1f
                infoWindow = null
            })
        }
        // تُستثنى الحلقات الملتفة حول الأرض (القارة القطبية الجنوبية) لأن رسمها يتشوّه عند خط التاريخ
        load(context).filter { r -> r.size >= 3 && (r.maxOf { it.longitude } - r.minOf { it.longitude }) < 300.0 }.forEach { ring ->
            folder.add(org.osmdroid.views.overlay.Polygon(map).apply {
                points = ring
                fillPaint.color = land
                outlinePaint.color = border
                outlinePaint.strokeWidth = 1.2f
                infoWindow = null
            })
        }
        return folder
    }
}

/* ───────────────────────── اتجاه الهاتف (البوصلة) ───────────────────────── */

@Immutable
data class DeviceOrientation(
    /** اتجاه أعلى الهاتف من الشمال المغناطيسي (درجات) */
    val azimuthMagnetic: Double = 0.0,
    /** الميل للأمام والخلف (درجات): 0 مسطّح، 90 قائم */
    val pitch: Double = 0.0,
    /** الميل الجانبي (درجات) */
    val roll: Double = 0.0,
    /** دقة الحساس: 0 غير موثوق … 3 عالية */
    val accuracy: Int = 3,
    val available: Boolean = true
) {
    /** الاتجاه من الشمال الجغرافي بعد تصحيح الانحراف المغناطيسي */
    fun azimuthTrue(declination: Double) = ((azimuthMagnetic + declination) % 360 + 360) % 360
}

/** الانحراف المغناطيسي (درجات، موجب شرقاً) بنموذج WMM المدمج في أندرويد */
fun magneticDeclination(lat: Double, lon: Double, heightM: Double = 0.0, time: Long = System.currentTimeMillis()): Double =
    GeomagneticField(lat.toFloat(), lon.toFloat(), heightM.toFloat(), time).declination.toDouble()

/**
 * يتابع اتجاه الهاتف بحساس متجه الدوران (أو المغناطيس + التسارع إن لم يتوفر)،
 * مع تنعيم القراءات. يتوقف تلقائياً عند مغادرة الشاشة.
 */
@Composable
fun rememberOrientation(): State<DeviceOrientation> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(DeviceOrientation()) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (rv == null && (acc == null || mag == null)) {
            state.value = DeviceOrientation(available = false)
            return@DisposableEffect onDispose { }
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val r = FloatArray(9); val out = FloatArray(9); val o = FloatArray(3)
        val g = FloatArray(3); val m = FloatArray(3)
        var hasG = false; var hasM = false
        var sinA = 0.0; var cosA = 1.0; var pitchS = 0.0; var rollS = 0.0
        var accuracy = 3
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(r, e.values)
                    Sensor.TYPE_ACCELEROMETER -> { System.arraycopy(e.values, 0, g, 0, 3); hasG = true; if (rv != null) return }
                    Sensor.TYPE_MAGNETIC_FIELD -> { System.arraycopy(e.values, 0, m, 0, 3); hasM = true; if (rv != null) return }
                }
                if (rv == null) {
                    if (!hasG || !hasM) return
                    if (!SensorManager.getRotationMatrix(r, null, g, m)) return
                }
                @Suppress("DEPRECATION")
                val rot = wm.defaultDisplay.rotation
                val (ax, ay) = when (rot) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(r, ax, ay, out)
                SensorManager.getOrientation(out, o)
                // اتجاه ثابت مهما كان ميلان الهاتف: مجموع إسقاطَي «أعلى الشاشة» و«ظهر الهاتف» على المستوى الأفقي
                // (حين يكون مسطّحاً يحدده أعلى الشاشة، وحين يكون قائماً يحدده ظهره) فلا يقفز كما في getOrientation
                val he = out[1].toDouble() - out[2].toDouble()
                val hn = out[4].toDouble() - out[5].toDouble()
                val hl = kotlin.math.sqrt(he * he + hn * hn)
                val k = 0.2
                if (hl > 1e-3) {
                    sinA = sinA * (1 - k) + (he / hl) * k
                    cosA = cosA * (1 - k) + (hn / hl) * k
                }
                pitchS = pitchS * (1 - k) + Math.toDegrees(-o[1].toDouble()) * k
                rollS = rollS * (1 - k) + Math.toDegrees(o[2].toDouble()) * k
                val az = (Math.toDegrees(atan2(sinA, cosA)) + 360) % 360
                state.value = DeviceOrientation(az, pitchS, rollS, accuracy, true)
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) { if (s.type == Sensor.TYPE_MAGNETIC_FIELD || s.type == Sensor.TYPE_ROTATION_VECTOR) accuracy = a }
        }
        if (rv != null) sm.registerListener(listener, rv, SensorManager.SENSOR_DELAY_GAME)
        if (mag != null) sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_GAME)
        if (rv == null && acc != null) sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

/* ───────────────────────── اتجاه التصويب (لعرض السماء بالهاتف) ───────────────────────── */

class Pointing(
    /** سمت الاتجاه الذي تشير إليه الكاميرا الخلفية من الشمال المغناطيسي (درجات) */
    val azimuthMagnetic: Double = 0.0,
    /** ارتفاعه فوق الأفق (درجات) */
    val altitude: Double = 0.0,
    val available: Boolean = true,
    /** محاور الشاشة في إحداثيات العالم (شرق، شمال، أعلى) — مغناطيسية: يمين[0..2]، أعلى[3..5]، أمام[6..8] */
    val basis: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0),
    /** عدد قراءات الحساس في الثانية (للتشخيص) */
    val rate: Int = 0,
    /** مصدر القراءة: true متجه الدوران، false التسارع والبوصلة */
    val fused: Boolean = true
)

/**
 * يحسب الاتجاه الذي يشير إليه ظهر الهاتف (الكاميرا الخلفية) مباشرة من مصفوفة الدوران،
 * فيبقى ثابتاً حين يُرفع الهاتف نحو السماء (بلا قفل الزوايا الذي يصيب getOrientation).
 * التنعيم يتم على المتجه نفسه لا على الزوايا، فلا يقفز عند 0°/360°.
 */
@Composable
fun rememberPointing(): State<Pointing> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Pointing()) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (rv == null && (acc == null || mag == null)) {
            state.value = Pointing(available = false)
            return@DisposableEffect onDispose { }
        }
        val r = FloatArray(9)
        val g = FloatArray(3); val m = FloatArray(3)
        var hasG = false; var hasM = false
        var vx = 0.0; var vy = 1.0; var vz = 0.0
        var first = true
        var lastEmit = 0L
        // آخر قراءة من حساس متجه الدوران؛ إن تأخر (أجهزة بلا جيروسكوب أو حساس بطيء) نحسب من التسارع والمغناطيس
        var lastRv = 0L
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rr = FloatArray(9)
        val sb = DoubleArray(9)
        var sbInit = false
        var count = 0; var rateStart = System.nanoTime(); var rate = 0
        var fusedNow = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = System.nanoTime()
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(r, e.values)
                        lastRv = now; fusedNow = true
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        for (i in 0..2) g[i] = if (hasG) g[i] + (e.values[i] - g[i]) * 0.2f else e.values[i]
                        hasG = true
                        if (now - lastRv < 250_000_000L) return
                        if (!hasM || !SensorManager.getRotationMatrix(r, null, g, m)) return
                        fusedNow = false
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        for (i in 0..2) m[i] = if (hasM) m[i] + (e.values[i] - m[i]) * 0.2f else e.values[i]
                        hasM = true
                        return
                    }
                    else -> return
                }
                count++
                if (now - rateStart > 1_000_000_000L) { rate = count; count = 0; rateStart = now }
                // مراعاة دوران الشاشة (طولي/عرضي)
                @Suppress("DEPRECATION")
                val rotNow = wm.defaultDisplay.rotation
                val (ax, ay) = when (rotNow) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(r, ax, ay, rr)
                // أعمدة المصفوفة = محاور الشاشة في العالم: X يمين، Y أعلى، −Z أمام (خلف الهاتف)
                val tgt = doubleArrayOf(
                    rr[0].toDouble(), rr[3].toDouble(), rr[6].toDouble(),
                    rr[1].toDouble(), rr[4].toDouble(), rr[7].toDouble(),
                    -rr[2].toDouble(), -rr[5].toDouble(), -rr[8].toDouble()
                )
                val k = if (!sbInit) 1.0 else 0.5
                for (i in 0..8) sb[i] += (tgt[i] - sb[i]) * k
                sbInit = true
                val x = sb[6]; val y = sb[7]; val z = sb[8]
                vx = x; vy = y; vz = z
                first = false
                if (now - lastEmit < 12_000_000L) return
                lastEmit = now
                val n = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-9)
                val az = (Math.toDegrees(atan2(vx, vy)) + 360) % 360
                val alt = Math.toDegrees(kotlin.math.asin((vz / n).coerceIn(-1.0, 1.0)))
                state.value = Pointing(az, alt, true, sb.copyOf(), rate, fusedNow)
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        if (rv != null) sm.registerListener(listener, rv, SensorManager.SENSOR_DELAY_GAME)
        if (acc != null) sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
        if (mag != null) sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
