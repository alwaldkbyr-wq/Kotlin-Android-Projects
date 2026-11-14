package com.mawaqeet.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.compositionLocalOf
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Night = Color(0xFF071522)
private val DeepBlue = Color(0xFF102B40)
private val Emerald = Color(0xFF1F5F66)
private val Gold = Color(0xFFB8935A)
private val Navy = Color(0xFF1C3157)
private val LightBg = Color(0xFFF4F0E6)
private val LightSurface = Color(0xFFFFFCF5)
private val Ink = Color(0xFF1B2A4A)

private data class ThemePalette(
    val name: String, val primary: Color, val accent: Color,
    val lightBg: Color, val darkBg: Color, val darkSurface: Color,
    val textDark: Color = Color(0xFF1B2A4A), val textLight: Color = Color(0xFFF5F2EA)
)
private val LocalMotionEnabled = compositionLocalOf { true }
private val GlassCardSequence = AtomicInteger(0)

private val ThemePalettes = listOf(
    ThemePalette("أخضر وعاجي", Color(0xFF164B3B), Color(0xFFB8935A), Color(0xFFF5F0E6), Color(0xFF102B26), Color(0xFF1C4037)),
    ThemePalette("كحلي وذهبي", Color(0xFF172554), Color(0xFFD6AD60), Color(0xFFF4F0E6), Color(0xFF101A35), Color(0xFF202E52)),
    ThemePalette("تركوازي وأبيض", Color(0xFF087F70), Color(0xFF1ABC9C), Color(0xFFFFFFFF), Color(0xFF082D2B), Color(0xFF10423E)),
    ThemePalette("بترولي ورملي", Color(0xFF155E63), Color(0xFFE8DCC5), Color(0xFFE8DCC5), Color(0xFF103B3E), Color(0xFF1A4B4D)),
    ThemePalette("بنفسجي وفضي", Color(0xFF493568), Color(0xFFD9D9E2), Color(0xFFF0EFF5), Color(0xFF241A36), Color(0xFF38294E)),
    ThemePalette("أسود وذهبي", Color(0xFF222222), Color(0xFFC5A059), Color(0xFFF5F1E8), Color(0xFF171717), Color(0xFF2B2B2B)),
    ThemePalette("زيتوني وبيج", Color(0xFF606C38), Color(0xFFF0E8D5), Color(0xFFF0E8D5), Color(0xFF2D3420), Color(0xFF424B2B))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MawaqeetApp() }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("الرئيسية", Icons.Default.Home),
    PRAYER("الصلاة", Icons.Default.AccessTime),
    MOON("القمر", Icons.Default.NightsStay),
    SKY("الفلك", Icons.Default.Explore),
    SETTINGS("الإعدادات", Icons.Default.Settings)
}

@Composable
fun MawaqeetApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("mawaqeet_preferences", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", true)) }
    var motionEnabled by remember { mutableStateOf(preferences.getBoolean("motion_enabled", true)) }
    var themeIndex by remember { mutableIntStateOf(preferences.getInt("theme_index", 0).coerceIn(0, ThemePalettes.lastIndex)) }
    var prayerMethodIndex by remember { mutableIntStateOf(preferences.getInt("prayer_method", 0).coerceIn(0, PrayerMethod.values().lastIndex)) }
    var hanafiAsr by remember { mutableStateOf(preferences.getBoolean("hanafi_asr", false)) }
    val palette = ThemePalettes[themeIndex]
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var showSplash by remember { mutableStateOf(true) }
    var latitude by remember { mutableStateOf(preferences.getString("latitude", null)?.toDoubleOrNull()) }
    var longitude by remember { mutableStateOf(preferences.getString("longitude", null)?.toDoubleOrNull()) }
    var locationMessage by remember { mutableStateOf(if (preferences.contains("latitude")) "تم حفظ موقعك على هذا الجهاز" else "سيُطلب إذن الموقع عند أول تشغيل فقط") }
    var locationListener by remember { mutableStateOf<LocationListener?>(null) }
    val manager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    fun acceptLocation(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        locationMessage = "تم حفظ موقعك على هذا الجهاز"
        preferences.edit().putString("latitude", location.latitude.toString()).putString("longitude", location.longitude.toString()).putLong("location_saved_at", System.currentTimeMillis()).apply()
        locationListener?.let { manager.removeUpdates(it) }
        locationListener = null
    }

    val locate: () -> Unit = {
        try {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                locationMessage = "نحتاج إذن الموقع أولاً"
            } else {
                val providers = manager.getProviders(true).filter {
                    it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER || it == LocationManager.PASSIVE_PROVIDER
                }
                val cached = providers.mapNotNull { provider ->
                    try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null }
                }.maxByOrNull { it.time }
                if (cached != null) acceptLocation(cached)
                else {
                    locationMessage = if (providers.isEmpty()) "خدمة الموقع متوقفة؛ فعّلها من الإعدادات" else "جارٍ حفظ موقعك لأول مرة…"
                    locationListener?.let { manager.removeUpdates(it) }
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) = acceptLocation(location)
                        @Deprecated("Deprecated by Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) { locationMessage = "تم تفعيل مزوّد الموقع؛ جارٍ التحديد…" }
                        override fun onProviderDisabled(provider: String) {
                            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                                !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                                locationMessage = "خدمة الموقع متوقفة؛ فعّلها من الإعدادات"
                            }
                        }
                    }
                    locationListener = listener
                    providers.forEach { provider ->
                        try { manager.requestLocationUpdates(provider, 1500L, 0f, listener, Looper.getMainLooper()) }
                        catch (_: Exception) { }
                    }
                }
            }
        } catch (_: SecurityException) {
            locationMessage = "لم تُمنح صلاحية الموقع"
        } catch (_: Exception) {
            locationMessage = "تعذّر تحديد الموقع؛ تحقق من إعدادات الجهاز"
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) locate()
        else locationMessage = "لم تمنح إذن الموقع. يمكنك تفعيله من إعدادات التطبيق."
    }

    LaunchedEffect(Unit) {
        if (latitude == null && !preferences.getBoolean("location_permission_prompted", false)) {
            preferences.edit().putBoolean("location_permission_prompted", true).apply()
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (latitude == null &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
             ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locate()
        }
    }

    DisposableEffect(Unit) {
        onDispose { locationListener?.let { manager.removeUpdates(it) } }
    }

    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val currentDate = LocalDate.now()
            if (today != currentDate) today = currentDate
            delay(60_000)
        }
    }
    val hijri = remember(today) { HijriCalendarInfo.from(today) }
    val moon = remember(today) { MoonInfo.forDate(today) }
    val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
    val times = if (latitude != null && longitude != null)
        PrayerCalculator.calculate(today, latitude!!, longitude!!, offset, PrayerMethod.values()[prayerMethodIndex], hanafiAsr) else null

    val colors = if (darkMode) darkColorScheme(
        primary = palette.primary, secondary = palette.accent, background = palette.darkBg, surface = palette.darkSurface,
        onBackground = palette.textLight, onSurface = palette.textLight
    ) else lightColorScheme(
        primary = palette.primary, secondary = palette.accent, background = palette.lightBg,
        surface = LightSurface, onBackground = palette.textDark, onSurface = palette.textDark
    )

    LaunchedEffect(Unit) {
        delay(350)
        showSplash = false
    }

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography().let { base ->
            base.copy(
                bodyLarge = base.bodyLarge.copy(fontFamily = MawaqeetTextFont),
                bodyMedium = base.bodyMedium.copy(fontFamily = MawaqeetTextFont),
                bodySmall = base.bodySmall.copy(fontFamily = MawaqeetTextFont),
                titleLarge = base.titleLarge.copy(fontFamily = MawaqeetDisplayFont),
                titleMedium = base.titleMedium.copy(fontFamily = MawaqeetDisplayFont),
                headlineLarge = base.headlineLarge.copy(fontFamily = MawaqeetDisplayFont),
                headlineMedium = base.headlineMedium.copy(fontFamily = MawaqeetDisplayFont),
                headlineSmall = base.headlineSmall.copy(fontFamily = MawaqeetDisplayFont)
            )
        }
    ) {
      CompositionLocalProvider(LocalMotionEnabled provides motionEnabled) {
        val splashOffset by animateFloatAsState(
            targetValue = if (showSplash) 0f else -1f,
            animationSpec = tween(if (motionEnabled) 550 else 160),
            label = "basmala-splash"
        )
        val foreground = if (darkMode) palette.textLight else palette.textDark
        val muted = if (darkMode) Color(0xFFC2D0CE) else Color(0xFF52636A)
        val accent = if (darkMode) palette.accent else palette.primary
        val background = if (darkMode) palette.darkBg else palette.lightBg
        Scaffold(
            containerColor = background,
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        .shadow(if (motionEnabled) 18.dp else 0.dp, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(if (darkMode) listOf(Color(0xDD24485A), Color(0xCC102B40)) else listOf(Color(0xF7FFFFFF), Color(0xDDF5F0E6))))
                        .border(1.dp, Color.White.copy(alpha = if (motionEnabled) .48f else .18f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 5.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTab.values().forEach { tab ->
                        val selected = selectedTab == tab
                        val pill by animateFloatAsState(if (selected) 1f else 0f, animationSpec = if (motionEnabled) spring(dampingRatio = .6f, stiffness = 320f) else tween(160), label = "nav-pill")
                        val iconLift by animateFloatAsState(if (selected) -3f else 0f, animationSpec = if (motionEnabled) spring(dampingRatio = .5f, stiffness = 400f) else tween(160), label = "nav-icon")
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).clickable { selectedTab = tab }.padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selected) {
                                    Box(Modifier.fillMaxWidth(.82f).height(34.dp).graphicsLayer { scaleX = .92f + pill * .08f; scaleY = .88f + pill * .12f }
                                        .clip(RoundedCornerShape(20.dp)).background(Emerald.copy(alpha = if (darkMode) .38f else .14f))
                                        .border(1.dp, Gold.copy(alpha = .32f), RoundedCornerShape(20.dp)))
                                    Text("⌒", color = accent, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-15).dp))
                                }
                                Icon(tab.icon, contentDescription = tab.label, tint = if (selected) accent else muted,
                                    modifier = Modifier.offset(y = iconLift.dp).size(21.dp))
                            }
                            Text(tab.label, color = if (selected) accent else muted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AmbientBackdrop(darkMode, palette)
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (!motionEnabled) androidx.compose.animation.fadeIn(tween(160)) togetherWith androidx.compose.animation.fadeOut(tween(160))
                        else (slideInHorizontally(initialOffsetX = { full -> full / 3 }, animationSpec = spring(dampingRatio = .8f, stiffness = 180f)) + scaleIn(initialScale = .92f, animationSpec = tween(380, delayMillis = 60))) togetherWith
                            (slideOutHorizontally(targetOffsetX = { full -> -full / 5 }, animationSpec = tween(380)) + scaleOut(targetScale = .94f, animationSpec = tween(380)) + androidx.compose.animation.fadeOut(tween(260)))
                    },
                    label = "page-transition"
                ) { tab ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("۞  مَوَاقِيت", color = foreground, fontSize = 29.sp, fontWeight = FontWeight.Bold, fontFamily = MawaqeetDisplayFont)
                                    Text("بين هدي السماء ودقّة الفلك", color = accent, fontSize = 12.sp)
                                    Text("━━━━ ◆ ۞ ◆ ━━━━", color = accent.copy(alpha = .72f), fontSize = 10.sp)
                                }
                                Box(Modifier.size(46.dp).clip(CircleShape).background(if (darkMode) Color.White.copy(.09f) else Emerald.copy(.09f)),
                                    contentAlignment = Alignment.Center) {
                                    Image(painter = painterResource(id = R.drawable.mawaqeet_logo), contentDescription = "شعار مواقيت", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                }
                            }
                        }
                        when (tab) {
                            AppTab.HOME -> {
                                item { HomePage(today, hijri.fullDate, moon.phaseName, locationMessage, latitude, longitude, times, accent, foreground, muted, darkMode, { selectedTab = AppTab.PRAYER }, { selectedTab = AppTab.MOON }, { selectedTab = AppTab.SKY }, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate() else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }, { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) }
                            }
                            AppTab.PRAYER -> {
                                item { PrayerPage(today, hijri.fullDate, times, locationMessage, latitude, longitude, accent, foreground, muted, darkMode, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate() else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }, { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) }
                            }
                            AppTab.MOON -> {
                                item { MoonPage(moon, accent, foreground, muted, darkMode) }
                            }
                            AppTab.SKY -> {
                                item { SkyPage(times, accent, foreground, muted, darkMode) }
                            }
                            AppTab.SETTINGS -> {
                                item { SettingsPage(darkMode, { darkMode = it; preferences.edit().putBoolean("dark_mode", it).apply() }, motionEnabled, { motionEnabled = it; preferences.edit().putBoolean("motion_enabled", it).apply() }, locationMessage, themeIndex, { themeIndex = it; preferences.edit().putInt("theme_index", it).apply() }, prayerMethodIndex, { prayerMethodIndex = it; preferences.edit().putInt("prayer_method", it).apply() }, hanafiAsr, { hanafiAsr = it; preferences.edit().putBoolean("hanafi_asr", it).apply() }, accent, foreground, muted, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate() else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }, { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) }
                            }
                        }
                        item { Text("مواقيت • إصدار 1.0.3", color = muted.copy(alpha = .65f), fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                }
                if (showSplash || splashOffset > -0.99f) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { translationY = splashOffset * size.height }
                            .background(if (darkMode) palette.darkBg else palette.lightBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("۞", color = palette.accent, fontSize = 58.sp)
                            Text("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", color = foreground, fontSize = 23.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                            Text("مَوَاقِيت", color = accent, fontSize = 15.sp, letterSpacing = 5.sp)
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun AmbientBackdrop(dark: Boolean, palette: ThemePalette) {
    val motion = LocalMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "ambient")
    val driftAnimated by transition.animateFloat(
        initialValue = -34f, targetValue = 34f,
        animationSpec = infiniteRepeatable(tween(8500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val drift = if (motion) driftAnimated else 0f
    val pulseAnimated by transition.animateFloat(
        initialValue = .18f, targetValue = .42f,
        animationSpec = infiniteRepeatable(tween(7200), RepeatMode.Reverse),
        label = "pulse"
    )
    val pulse = if (motion) pulseAnimated else .22f
    val waterAnimated by transition.animateFloat(
        initialValue = -22f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "water-drift"
    )
    val water = if (motion) waterAnimated else 0f
    Box(Modifier.fillMaxSize().background(if (dark) Brush.verticalGradient(listOf(palette.darkBg, palette.darkSurface, palette.primary.copy(alpha = .78f))) else Brush.verticalGradient(listOf(palette.lightBg, Color(0xFFE7F0EB), Color(0xFFFFFCF5))))) {
        Box(Modifier.offset(x = drift.dp, y = 90.dp).size(230.dp).align(Alignment.TopEnd).blur(62.dp)
            .background((if (dark) Emerald else Color(0xFF72B6A0)).copy(alpha = pulse), CircleShape))
        Box(Modifier.offset(x = (-drift).dp, y = 390.dp).size(190.dp).align(Alignment.CenterStart).blur(58.dp)
            .background((if (dark) Color(0xFF315F8A) else Color(0xFFB8C9D9)).copy(alpha = pulse * .7f), CircleShape))
        Box(Modifier.offset(x = water.dp, y = (-70).dp).size(175.dp).align(Alignment.BottomEnd).blur(64.dp)
            .background((if (dark) Color(0xFFB8935A) else Color(0xFF1F5F66)).copy(alpha = pulse * .55f), CircleShape))
        if (dark) StarField()
    }
}

@Composable
private fun HomePage(
    today: LocalDate, hijri: String, phase: String, location: String,
    lat: Double?, lon: Double?, times: PrayerTimes?, accent: Color, fg: Color, muted: Color,
    dark: Boolean, onPrayer: () -> Unit, onMoon: () -> Unit, onSky: () -> Unit,
    onLocate: () -> Unit, onLocationSettings: () -> Unit
) {
    GlassPanel(dark) {
        Text(today.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))), color = fg, fontSize = 13.sp)
        Text(hijri, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("التاريخ الهجري الحسابي • قد يختلف عن إعلان الرؤية الشرعية", color = muted, fontSize = 10.sp)
    }
    if (lat == null || lon == null) GlassPanel(dark) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = accent)
            Column(Modifier.weight(1f)) {
                Text(location, color = fg, fontWeight = FontWeight.SemiBold)
                if (lat != null && lon != null) Text("%.4f°, %.4f°".format(Locale.US, lat, lon), color = accent, fontSize = 11.sp)
            }
            TextButton(onClick = onLocate) { Text("تحديد", color = accent) }
        }
        Text("سيُحفظ موقعك على هذا الجهاز لاستخدامه تلقائياً في حساب مواقيت الأيام القادمة.", color = muted, fontSize = 10.sp)
        if (location.contains("متوقفة") || location.contains("فعّلها")) TextButton(onClick = onLocationSettings) { Text("فتح إعدادات الموقع", color = accent) }
    }
    SectionTitle("لوحتك السماوية", "ملخص يومك في لمحة", fg, muted)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickTile("مواقيت الصلاة", "عرض الصلوات", Icons.Default.AccessTime, accent, fg, Modifier.weight(1f), onPrayer, dark)
        QuickTile("القمر والهلال", phase, Icons.Default.NightsStay, accent, fg, Modifier.weight(1f), onMoon, dark)
    }
    GlassPanel(dark) {
        Text("الصلاة القادمة", color = accent, fontWeight = FontWeight.Bold)
        if (times == null) Text("حدّد موقعك لعرض مواقيت الصلاة.", color = muted)
        else {
            PrayerRow("الفجر", times.fajr, fg, accent)
            PrayerRow("الظهر", times.dhuhr, fg, accent)
            PrayerRow("العصر", times.asr, fg, accent)
            PrayerRow("المغرب", times.maghrib, fg, accent)
            PrayerRow("العشاء", times.isha, fg, accent)
        }
        TextButton(onClick = onPrayer) { Text("كل المواقيت ←", color = accent) }
    }
    GlassPanel(dark) {
        Text("نافذة الفلك", color = accent, fontWeight = FontWeight.Bold)
        Text("تابع الشروق والغروب وطول النهار، واستكشف المؤشرات القمرية.", color = muted, fontSize = 12.sp)
        TextButton(onClick = onSky) { Text("استكشف السماء ←", color = accent) }
    }
}

@Composable
private fun PrayerPage(today: LocalDate, hijri: String, times: PrayerTimes?, location: String, lat: Double?, lon: Double?,
    accent: Color, fg: Color, muted: Color, dark: Boolean, onLocate: () -> Unit, onSettings: () -> Unit) {
    PageHeading("مواقيت الصلاة", "حسابات شمسية محلية حسب إحداثياتك", accent, fg)
    GlassPanel(dark) {
        Text(today.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))), color = fg)
        Text(hijri, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(location + if (lat != null && lon != null) " • %.3f، %.3f".format(Locale.US, lat, lon) else "", color = muted, fontSize = 11.sp)
        if (times == null) {
            Text("لا يمكن حساب المواقيت قبل تحديد الموقع.", color = muted)
            Button(onClick = onLocate) { Text("تحديد موقعي") }
            TextButton(onClick = onSettings) { Text("إعدادات الموقع") }
        } else {
            PrayerRow("الفجر", times.fajr, fg, accent)
            PrayerRow("الشروق", times.sunrise, muted, muted)
            PrayerRow("الظهر", times.dhuhr, fg, accent)
            PrayerRow("العصر", times.asr, fg, accent)
            PrayerRow("المغرب", times.maghrib, fg, accent)
            PrayerRow("العشاء", times.isha, fg, accent)
            HorizontalDivider(color = accent.copy(alpha = .25f))
            Text("تُستخدم طريقة الحساب والمذهب المحددان في الإعدادات. قد تختلف النتائج عن تقويم الجهات الرسمية.", color = muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
    GlassPanel(dark) {
        Text("تنبيه الدقة", color = accent, fontWeight = FontWeight.Bold)
        Text("الحساب الحالي تأسيسي، ولا يطبق بعد تعديلات خطوط العرض العالية أو إعدادات الهيئات المحلية. قارن النتائج بتقويم المسجد أو الجهة الرسمية.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun MoonPage(moon: MoonInfo, accent: Color, fg: Color, muted: Color, dark: Boolean) {
    PageHeading("القمر والهلال", "مؤشرات طور القمر وعمره", accent, fg)
    GlassPanel(dark) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(moon.emoji, fontSize = 82.sp)
        }
        Text(moon.phaseName, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric("عمر القمر", moon.ageDays + " يوم", accent, fg, muted)
            Metric("الإضاءة", moon.illumination.toString() + "٪", accent, fg, muted)
            Metric("للمحاق", moon.daysToNewMoon + " يوم", accent, fg, muted)
        }
    }
    GlassPanel(dark) {
        Text("هل يُرى الهلال؟", color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("هذه النسخة تعرض طوراً قمرياً تقريبياً فقط. لا تستنتج منها ثبوت الهلال أو إمكان رؤيته؛ فذلك يحتاج حساب الاقتران والاستطالة والارتفاع ومكث القمر وعرض الهلال والظروف الجوية، مع الرجوع إلى الإعلان الشرعي المحلي.", color = muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
    GlassPanel(dark) {
        Text("ما القادم؟", color = accent, fontWeight = FontWeight.Bold)
        Text("سيُطوّر هذا القسم ليعرض بيانات الاقتران وموقع القمر بالنسبة للأفق ومؤشرات الرؤية وفق نماذج فلكية متخصصة.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SkyPage(times: PrayerTimes?, accent: Color, fg: Color, muted: Color, dark: Boolean) {
    PageHeading("مرصد السماء", "الشمس والنهار ومعلومات الرصد", accent, fg)
    GlassPanel(dark) {
        Text("الشمس والنهار", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (times == null) Text("حدّد موقعك من صفحة الرئيسية لعرض بيانات الشمس.", color = muted)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("الشروق", times.sunrise, accent, fg, muted)
            Metric("الغروب", times.sunset, accent, fg, muted)
            Metric("طول النهار", times.dayLength, accent, fg, muted)
        }
    }
    GlassPanel(dark) {
        Text("خريطة الرصد", color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("ستُضاف في مراحل لاحقة مواقع النجوم والكواكب، والارتفاع والسمت، ومسارات الأجرام خلال الليل.", color = muted, fontSize = 12.sp, lineHeight = 19.sp)
        Text("لا تتطلب الحسابات الفلكية الأساسية اتصالاً بالإنترنت عند اكتمال وحدتها المحلية.", color = muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsPage(dark: Boolean, onDarkChange: (Boolean) -> Unit, motion: Boolean, onMotionChange: (Boolean) -> Unit, location: String, themeIndex: Int, onThemeChange: (Int) -> Unit, accent: Color, fg: Color, muted: Color,
    prayerMethodIndex: Int, onPrayerMethodChange: (Int) -> Unit, hanafiAsr: Boolean, onHanafiAsrChange: (Boolean) -> Unit,
    accent: Color, fg: Color, muted: Color, onLocate: () -> Unit, onSettings: () -> Unit) {
    var methodExpanded by remember { mutableStateOf(false) }
    PageHeading("الإعدادات", "اجعل مواقيت أقرب إلى طريقتك", accent, fg)
    GlassPanel(dark) {
        Text("المظهر", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (dark) "الوضع الداكن" else "الوضع الفاتح", color = fg, fontWeight = FontWeight.SemiBold)
                Text("تبديل فوري بين مظهرين زجاجيين", color = muted, fontSize = 11.sp)
            }
            Switch(checked = dark, onCheckedChange = onDarkChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !dark, onClick = { onDarkChange(false) }, label = { Text("فاتح") })
            FilterChip(selected = dark, onClick = { onDarkChange(true) }, label = { Text("داكن") })
        }
    }
    GlassPanel(dark) {
        Text("الثيمات", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("اختر الهوية اللونية التي تفضلها", color = muted, fontSize = 11.sp)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ThemePalettes.chunked(2).forEachIndexed { rowIndex, rowThemes ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowThemes.forEachIndexed { columnIndex, theme ->
                        val index = rowIndex * 2 + columnIndex
                        FilterChip(
                            selected = themeIndex == index,
                            onClick = { onThemeChange(index) },
                            label = { Text(theme.name, fontSize = 10.sp) },
                            leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(theme.primary).border(1.dp, theme.accent, CircleShape)) }
                        )
                    }
                }
            }
        }
    }
    GlassPanel(dark) {
        Text("حساب مواقيت الصلاة", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("اختر طريقة الحساب الأقرب إلى تقويم بلدك", color = muted, fontSize = 11.sp)
        Box {
            OutlinedButton(onClick = { methodExpanded = true }) {
                Text(PrayerMethod.values()[prayerMethodIndex].title)
            }
            DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                PrayerMethod.values().forEachIndexed { index, method ->
                    DropdownMenuItem(text = { Text(method.title) }, onClick = {
                        onPrayerMethodChange(index)
                        methodExpanded = false
                    })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("العصر على المذهب الحنفي", color = fg, fontWeight = FontWeight.SemiBold)
                Text("استخدم ظلّ المثلين بدلاً من ظلّ المثل", color = muted, fontSize = 11.sp)
            }
            Switch(checked = hanafiAsr, onCheckedChange = onHanafiAsrChange)
        }
        Text("قد تختلف النتائج عن التوقيت الرسمي بسبب إعدادات الجهات المحلية وخطوط العرض؛ راجع تقويم بلدك.", color = muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
    GlassPanel(dark) {
        Text("الحركة", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (motion) "الحركات مفعّلة" else "تقليل الحركة", color = fg, fontWeight = FontWeight.SemiBold)
                Text("إيقاف الحركات لتجربة أبسط وأخف", color = muted, fontSize = 11.sp)
            }
            Switch(checked = motion, onCheckedChange = onMotionChange)
        }
    }
    GlassPanel(dark) {
        Text("الموقع", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(location, color = muted, fontSize = 12.sp)
        Button(onClick = onLocate) { Text("تحديث الموقع") }
        TextButton(onClick = onSettings) { Text("إعدادات خدمات الموقع") }
    }
    GlassPanel(dark) {
        Text("عن التطبيق", color = accent, fontWeight = FontWeight.Bold)
        Text("مواقيت 1.0.3 • بعض المؤشرات الفلكية ما زالت تقريبية وقيد التطوير.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String, accent: Color, fg: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = accent, fontSize = 12.sp)
    }
}

@Composable
private fun Metric(label: String, value: String, accent: Color, fg: Color, muted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = muted, fontSize = 10.sp)
        Text(value, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, icon: ImageVector, accent: Color, fg: Color,
    modifier: Modifier, onClick: () -> Unit, dark: Boolean) {
    Column(modifier.shadow(12.dp, RoundedCornerShape(24.dp), clip = false).clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(if (dark) listOf(Color.White.copy(.17f), Color.White.copy(.055f)) else listOf(Color.White.copy(.92f), Color.White.copy(.62f))))
        .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(24.dp))
        .clickable { onClick() }.padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subtitle, color = fg.copy(alpha = .7f), fontSize = 10.sp)
    }
}

@Composable
private fun GlassPanel(dark: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val motion = LocalMotionEnabled.current
    val cardIndex = remember { GlassCardSequence.getAndIncrement() % 6 }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (motion) delay(55L + cardIndex * 75L)
        entered = true
    }
    val fall by animateFloatAsState(targetValue = if (entered) 0f else if (motion) -34f else 0f, animationSpec = if (motion) spring(dampingRatio = .68f, stiffness = 170f) else tween(160), label = "glass-card-fall")
    val reveal by animateFloatAsState(targetValue = if (entered) 1f else if (motion) .94f else 1f, animationSpec = if (motion) spring(dampingRatio = .68f, stiffness = 170f) else tween(160), label = "glass-card-scale")
    val fill = if (dark) listOf(Color.White.copy(alpha = .15f), Color.White.copy(alpha = .055f))
        else listOf(Color.White.copy(alpha = .86f), Color(0xFFF7F1E5).copy(alpha = .72f))
    val line = if (dark) Color.White.copy(alpha = .2f) else Color.White.copy(alpha = .9f)
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            translationY = fall
            scaleX = reveal
            scaleY = reveal
            alpha = if (entered) 1f else .55f
        }.shadow(if (motion) 16.dp else 0.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(fill.first(), fill.last(), fill.first().copy(alpha = .08f))))
            .border(1.dp, line, RoundedCornerShape(28.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String, fg: Color, muted: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = muted, fontSize = 10.sp)
    }
}

@Composable
private fun PrayerRow(name: String, time: String, fg: Color, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = fg, fontSize = 14.sp)
        Text(time, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StarField() {
    Canvas(Modifier.fillMaxSize()) {
        val points = listOf(.08f to .09f, .83f to .07f, .69f to .18f, .19f to .27f, .92f to .34f,
            .43f to .39f, .11f to .52f, .77f to .61f, .29f to .72f, .91f to .83f, .57f to .91f, .04f to .94f)
        points.forEachIndexed { index, point ->
            drawCircle(Color.White.copy(alpha = if (index % 3 == 0) .48f else .2f),
                radius = if (index % 4 == 0) 2f else 1.1f,
                center = androidx.compose.ui.geometry.Offset(size.width * point.first, size.height * point.second))
        }
    }
}

private data class HijriCalendarInfo(val fullDate: String) {
    companion object {
        private val months = listOf("محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة")
        fun from(date: LocalDate): HijriCalendarInfo = try {
            val h = HijrahDate.from(date)
            val day = h.get(ChronoField.DAY_OF_MONTH)
            val month = h.get(ChronoField.MONTH_OF_YEAR)
            val year = h.get(ChronoField.YEAR)
            HijriCalendarInfo("$day ${months[(month - 1).coerceIn(0, 11)]} $year هـ")
        } catch (_: Exception) { HijriCalendarInfo("تعذّر تحويل التاريخ الهجري") }
    }
}

private data class MoonInfo(val ageDays: String, val illumination: Int, val phaseName: String, val emoji: String, val daysToNewMoon: String) {
    companion object {
        fun forDate(date: LocalDate): MoonInfo {
            val epoch = LocalDate.of(2000, 1, 6)
            val days = java.time.temporal.ChronoUnit.DAYS.between(epoch, date).toDouble() - .26
            val month = 29.530588853
            val age = ((days % month) + month) % month
            val phase = age / month
            val illumination = ((1.0 - kotlin.math.cos(2.0 * Math.PI * phase)) * 50.0).toInt()
            val (name, emoji) = when {
                age < 1.5 -> "المحاق" to "🌑"
                age < 6.5 -> "هلال متزايد" to "🌒"
                age < 8.5 -> "التربيع الأول" to "🌓"
                age < 13.5 -> "أحدب متزايد" to "🌔"
                age < 16.5 -> "البدر" to "🌕"
                age < 21.5 -> "أحدب متناقص" to "🌖"
                age < 23.5 -> "التربيع الأخير" to "🌗"
                age < 28.0 -> "هلال متناقص" to "🌘"
                else -> "اقتراب المحاق" to "🌑"
            }
            return MoonInfo("%.1f".format(Locale.US, age), illumination, name, emoji, "%.1f".format(Locale.US, month - age))
        }
    }
}
