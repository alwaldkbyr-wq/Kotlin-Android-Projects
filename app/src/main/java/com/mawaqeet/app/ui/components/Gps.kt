package com.mawaqeet.app.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** قراءة GPS حيّة (تعمل دون إنترنت: الأقمار الصناعية فقط) */
@Immutable
data class GpsFix(
    val lat: Double,
    val lon: Double,
    /** الارتفاع عن سطح البحر (متر) إن توفر */
    val altitude: Double?,
    /** دقة الموقع (متر) */
    val accuracy: Float,
    /** السرعة (م/ث) إن توفرت */
    val speed: Float?,
    /** اتجاه الحركة (درجات من الشمال) إن توفر */
    val bearing: Float?,
    val time: Long,
    val provider: String
)

fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * يتابع الموقع من الأقمار الصناعية مباشرة (GPS) بفاصل [intervalMs]، ويتوقف عند مغادرة الشاشة.
 * يعيد null حتى أول قراءة، أو إن لم يُمنح إذن الموقع (اطلبه بـ env.requestGps()).
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberGpsFix(intervalMs: Long = 1000L, enabled: Boolean = true): State<GpsFix?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<GpsFix?>(null) }
    DisposableEffect(enabled, intervalMs, hasLocationPermission(context)) {
        if (!enabled || !hasLocationPermission(context)) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun toFix(l: Location) = GpsFix(
            l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else null, if (l.hasAccuracy()) l.accuracy else 999f,
            if (l.hasSpeed()) l.speed else null, if (l.hasBearing()) l.bearing else null, l.time, l.provider ?: ""
        )
        // آخر موقع معروف فوراً
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }?.let { state.value = toFix(it) }
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) { state.value = toFix(l) }
            @Deprecated("") override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        val providers = buildList {
            if (fine && runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) add(LocationManager.GPS_PROVIDER)
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) add(LocationManager.NETWORK_PROVIDER)
        }
        providers.forEach { p -> runCatching { lm.requestLocationUpdates(p, intervalMs, 0f, listener, Looper.getMainLooper()) } }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }
    return state
}

/** أدوات الإحداثيات والمسافات */
object Geo {
    const val R_EARTH = 6371008.8

    /** المسافة على سطح الأرض بالمتر (هافرساين) */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * R_EARTH * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    /** الاتجاه الابتدائي من النقطة الأولى إلى الثانية (درجات من الشمال الحقيقي) */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    /** نقطة على بعد [distM] باتجاه [bearingDeg] */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distM: Double): Pair<Double, Double> {
        val d = distM / R_EARTH; val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat); val l1 = Math.toRadians(lon)
        val p2 = kotlin.math.asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + kotlin.math.atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return Math.toDegrees(p2) to ((Math.toDegrees(l2) + 540) % 360 - 180)
    }

    fun formatDistance(m: Double): String = when {
        m < 1000 -> "${m.toInt()} م"
        m < 10_000 -> String.format(java.util.Locale.US, "%.2f كم", m / 1000)
        else -> String.format(java.util.Locale.US, "%.1f كم", m / 1000)
    }

    /** درجات ودقائق وثوانٍ: 36°45'13.7" N */
    fun dms(v: Double, lat: Boolean): String {
        val a = abs(v); val d = floor(a).toInt(); val mf = (a - d) * 60; val m = floor(mf).toInt(); val s = (mf - m) * 60
        val h = if (lat) (if (v >= 0) "N" else "S") else (if (v >= 0) "E" else "W")
        return String.format(java.util.Locale.US, "%d°%02d'%04.1f\" %s", d, m, s, h)
    }

    /** درجات ودقائق عشرية: 36°45.228' N */
    fun dm(v: Double, lat: Boolean): String {
        val a = abs(v); val d = floor(a).toInt(); val m = (a - d) * 60
        val h = if (lat) (if (v >= 0) "N" else "S") else (if (v >= 0) "E" else "W")
        return String.format(java.util.Locale.US, "%d°%06.3f' %s", d, m, h)
    }

    /** إحداثيات UTM على مرجع WGS84: «31S 505318 4067542» */
    fun utm(lat: Double, lon: Double): String {
        if (lat < -80 || lat > 84) return "خارج نطاق UTM"
        val zone = floor((lon + 180) / 6).toInt() + 1
        val a = 6378137.0; val f = 1 / 298.257223563; val e2 = f * (2 - f); val ep2 = e2 / (1 - e2); val k0 = 0.9996
        val phi = Math.toRadians(lat); val lam = Math.toRadians(lon); val lam0 = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val n = a / sqrt(1 - e2 * sin(phi) * sin(phi)); val t = tan(phi) * tan(phi); val c = ep2 * cos(phi) * cos(phi)
        val aa = cos(phi) * (lam - lam0)
        val m = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * phi - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * phi) +
            (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * phi) - (35 * e2 * e2 * e2 / 3072) * sin(6 * phi))
        val x = k0 * n * (aa + (1 - t + c) * aa * aa * aa / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * Math.pow(aa, 5.0) / 120) + 500000.0
        var y = k0 * (m + n * tan(phi) * (aa * aa / 2 + (5 - t + 9 * c + 4 * c * c) * Math.pow(aa, 4.0) / 24 + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * Math.pow(aa, 6.0) / 720))
        if (lat < 0) y += 10_000_000.0
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        val band = bands[((lat + 80) / 8).toInt().coerceIn(0, bands.length - 1)]
        return String.format(java.util.Locale.US, "%d%c %d %d", zone, band, x.toLong(), y.toLong())
    }

    @Suppress("unused") private const val TWO_PI = 2 * PI
}
