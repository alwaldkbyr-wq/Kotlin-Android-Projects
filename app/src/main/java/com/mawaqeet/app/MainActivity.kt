package com.mawaqeet.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.Place
import com.mawaqeet.app.data.SettingsStore
import com.mawaqeet.app.notify.PrayerScheduler
import com.mawaqeet.app.ui.AppRoot
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.DevicePerf
import com.mawaqeet.app.ui.components.LocalPerf
import com.mawaqeet.app.ui.components.PerfConfig
import com.mawaqeet.app.ui.theme.AppPalette
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalMotion
import com.mawaqeet.app.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

class MawaqeetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrayerScheduler.createChannels(this)
        com.mawaqeet.app.ui.theme.CanvasFonts.init(this)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var store: SettingsStore
    private val stack = mutableStateListOf(Routes.HOME)

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) locate() else toast("لم يُسمح بالوصول إلى الموقع؛ يمكنك اختيار مدينتك من قائمة المواقع")
    }
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = SettingsStore.get(this)
        savedInstanceState?.getStringArrayList("stack")?.let { if (it.isNotEmpty()) { stack.clear(); stack.addAll(it) } }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // تحديث موقع GPS تلقائياً عند الفتح إن كان الموقع الحالي من GPS
        if (store.value.place.gps && hasLocationPermission()) locate(silent = true)

        setContent {
            val settings by store.flow.collectAsStateWithLifecycle()
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1000L - now % 1000L)
                }
            }
            // إعادة جدولة التنبيهات وتحديث الودجات عند تغيّر الإعدادات
            LaunchedEffect(settings) {
                // حساب المواقيت وجدولة المنبّهات خارج الخيط الرئيسي
                withContext(Dispatchers.Default) {
                    runCatching {
                        PrayerScheduler.reschedule(applicationContext)
                        Widgets.updateAll(applicationContext)
                    }
                }
            }
            val perf = PerfConfig.of(settings.perfMode, DevicePerf.tier(this))
            val palette = AppPalette.of(settings.palette)
            val dark = palette.forcedDark ?: settings.dark
            val env = Env(
                settings = settings,
                now = now,
                updater = { f -> store.update(f) },
                navigate = { r -> if (r in Routes.tabs) selectTab(r) else stack.add(r) },
                back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                requestGps = { requestLocation() }
            )
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalPerf provides perf,
                LocalMotion provides settings.motion,
                LocalEnv provides env
            ) {
                var splash by rememberSaveable { mutableStateOf(true) }
                AppTheme(dark = dark, palette = palette) {
                    androidx.compose.foundation.layout.Box {
                        AppRoot(stack = stack.toList(), onTab = { selectTab(it) })
                        if (splash) com.mawaqeet.app.ui.SplashOverlay { splash = false }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // إعادة جدولة التنبيه (مثلاً بعد منح إذن المنبهات الدقيقة)
        Thread { runCatching { PrayerScheduler.reschedule(applicationContext) } }.start()
    }

    override fun onStop() {
        super.onStop()
        // تحديث أيقونة التطبيق لتطابق الثيم الحالي عند مغادرته
        AppIcon.apply(applicationContext, store.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("stack", ArrayList(stack))
    }

    private fun selectTab(route: String) {
        stack.clear()
        stack.add(route)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) locate()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @SuppressLint("MissingPermission")
    private fun locate(silent: Boolean = false) {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // إذن «الموقع التقريبي» وحده لا يسمح بمزوّد GPS ولا السلبي (يرمي SecurityException)
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { fine || it == LocationManager.NETWORK_PROVIDER }
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { if (!silent) toast("خدمة الموقع متوقفة؛ فعّلها من إعدادات الهاتف"); return }
        // آخر موقع معروف فوراً، ثم قراءة حديثة
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { accept(it, silent = true) }
        if (!silent) toast("جارٍ تحديد موقعك…")
        val provider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, mainExecutor) { loc -> if (loc != null) accept(loc, silent) else if (!silent) toast("تعذّر تحديد الموقع الآن") }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(l: Location) = accept(l, silent)
                    @Deprecated("") override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            if (!silent) toast("تعذّر تحديد الموقع الآن")
        }
    }

    private fun accept(l: Location, silent: Boolean) {
        // اسم الموقع من أقرب مدينة في القائمة المدمجة (دون إنترنت)
        val near = com.mawaqeet.app.data.Cities.all.minByOrNull {
            com.mawaqeet.app.astro.Qibla.distanceKm(l.latitude, l.longitude, it.lat, it.lon)
        }
        val dKm = near?.let { com.mawaqeet.app.astro.Qibla.distanceKm(l.latitude, l.longitude, it.lat, it.lon) } ?: 1e9
        val gpsName = when {
            near == null || dKm > 150 -> "موقعي (GPS)"
            dKm < 15 -> near.name
            else -> "قرب ${near.name}"
        }
        val gpsPlace = Place(
            name = gpsName,
            lat = l.latitude, lon = l.longitude,
            zoneId = ZoneId.systemDefault().id,
            heightM = if (l.hasAltitude()) l.altitude.coerceIn(0.0, 6000.0) else 0.0,
            gps = true
        )
        store.update { s ->
            val i = s.places.indexOfFirst { it.gps }
            val places = if (i >= 0) s.places.toMutableList().also { it[i] = gpsPlace } else listOf(gpsPlace) + s.places
            s.copy(places = places, placeIndex = places.indexOfFirst { it.gps })
        }
        if (!silent) toast(String.format(Locale.US, "تم تحديد الموقع: %.4f، %.4f", l.latitude, l.longitude))
    }
}
