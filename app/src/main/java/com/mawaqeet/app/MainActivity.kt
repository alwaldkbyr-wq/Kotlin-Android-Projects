package com.mawaqeet.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
private val Emerald = Color(0xFF1C7663)
private val Gold = Color(0xFFE7C987)
private val LightBg = Color(0xFFF1F5F3)
private val LightSurface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF183448)

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
    var darkMode by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var locationMessage by remember { mutableStateOf("لم يُحدّد الموقع بعد") }
    var locationListener by remember { mutableStateOf<LocationListener?>(null) }
    val manager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    fun acceptLocation(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        locationMessage = "تم تحديد موقعك"
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
                    locationMessage = if (providers.isEmpty()) "خدمة الموقع متوقفة؛ فعّلها من الإعدادات" else "جارٍ البحث عن موقعك… أبقِ التطبيق مفتوحاً"
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

    DisposableEffect(Unit) {
        onDispose { locationListener?.let { manager.removeUpdates(it) } }
    }

    val today = LocalDate.now()
    val hijri = remember(today) { HijriCalendarInfo.from(today) }
    val moon = remember(today) { MoonInfo.forDate(today) }
    val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
    val times = if (latitude != null && longitude != null)
        PrayerCalculator.calculate(today, latitude!!, longitude!!, offset) else null

    val colors = if (darkMode) darkColorScheme(
        primary = Emerald, secondary = Gold, background = Night, surface = DeepBlue,
        onBackground = Color(0xFFEAF2F0), onSurface = Color(0xFFEAF2F0)
    ) else lightColorScheme(
        primary = Emerald, secondary = Color(0xFF9B742E), background = LightBg,
        surface = LightSurface, onBackground = Ink, onSurface = Ink
    )

    MaterialTheme(colorScheme = colors) {
        val foreground = if (darkMode) Color(0xFFEAF2F0) else Ink
        val muted = if (darkMode) Color(0xFFB9C9CE) else Color(0xFF647985)
        val accent = if (darkMode) Gold else Color(0xFF946C25)
        val background = if (darkMode) Night else LightBg
        Scaffold(
            containerColor = background,
            bottomBar = {
                NavigationBar(containerColor = if (darkMode) Color(0xEE0C2233) else Color(0xF2FFFFFF)) {
                    AppTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = if (darkMode) Emerald.copy(alpha = .22f) else Emerald.copy(alpha = .12f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AmbientBackdrop(darkMode)
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { androidx.compose.animation.fadeIn(tween(260)) togetherWith androidx.compose.animation.fadeOut(tween(180)) },
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
                                    Text("مَوَاقِيت", color = foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Text("بين هدي السماء ودقّة الفلك", color = accent, fontSize = 12.sp)
                                }
                                Box(Modifier.size(46.dp).clip(CircleShape).background(if (darkMode) Color.White.copy(.09f) else Emerald.copy(.09f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.NightsStay, contentDescription = null, tint = accent, modifier = Modifier.size(25.dp))
                                }
                            }
                        }
                        when (tab) {
                            AppTab.HOME -> {
                                item { HomePage(today, hijri.fullDate, moon.phaseName, locationMessage, latitude, longitude, times, accent, foreground, muted, darkMode, { selectedTab = AppTab.PRAYER }, { selectedTab = AppTab.MOON }, { selectedTab = AppTab.SKY }, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) }
                            }
                            AppTab.PRAYER -> {
                                item { PrayerPage(today, hijri.fullDate, times, locationMessage, latitude, longitude, accent, foreground, muted, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) }
                            }
                            AppTab.MOON -> {
                                item { MoonPage(moon, accent, foreground, muted, darkMode) }
                            }
                            AppTab.SKY -> {
                                item { SkyPage(times, accent, foreground, muted) }
                            }
                            AppTab.SETTINGS -> {
                                item { SettingsPage(darkMode, { darkMode = it }, locationMessage, accent, foreground, muted, { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) }
                            }
                        }
                        item { Text("مواقيت • إصدار تجريبي 0.3.0", color = muted.copy(alpha = .65f), fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientBackdrop(dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = -34f, targetValue = 34f,
        animationSpec = infiniteRepeatable(tween(8500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val pulse by transition.animateFloat(
        initialValue = .22f, targetValue = .48f,
        animationSpec = infiniteRepeatable(tween(6200), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(Modifier.fillMaxSize().background(if (dark) Brush.verticalGradient(listOf(Night, DeepBlue, Color(0xFF102D35))) else Brush.verticalGradient(listOf(LightBg, Color(0xFFE4EFEB), Color(0xFFF7F8F4))))) {
        Box(Modifier.offset(x = drift.dp, y = 90.dp).size(230.dp).align(Alignment.TopEnd).blur(62.dp)
            .background((if (dark) Emerald else Color(0xFF72B6A0)).copy(alpha = pulse), CircleShape))
        Box(Modifier.offset(x = (-drift).dp, y = 390.dp).size(190.dp).align(Alignment.CenterStart).blur(58.dp)
            .background((if (dark) Color(0xFF315F8A) else Color(0xFFB8C9D9)).copy(alpha = pulse * .7f), CircleShape))
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
    GlassPanel(dark) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = accent)
            Column(Modifier.weight(1f)) {
                Text(location, color = fg, fontWeight = FontWeight.SemiBold)
                if (lat != null && lon != null) Text("%.4f°, %.4f°".format(Locale.US, lat, lon), color = accent, fontSize = 11.sp)
            }
            TextButton(onClick = onLocate) { Text("تحديد", color = accent) }
        }
        Text("إن تأخر GPS، جرّب الوقوف قرب نافذة أو في مكان مفتوح.", color = muted, fontSize = 10.sp)
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
    accent: Color, fg: Color, muted: Color, onLocate: () -> Unit, onSettings: () -> Unit) {
    PageHeading("مواقيت الصلاة", "حسابات شمسية محلية حسب إحداثياتك", accent, fg)
    GlassPanel() {
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
            Text("تُستخدم حالياً زاوية فجر 18° وعشاء 17°، والعصر على المذهب القياسي. قد تختلف النتائج عن تقويم بلدك.", color = muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
    GlassPanel() {
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
private fun SkyPage(times: PrayerTimes?, accent: Color, fg: Color, muted: Color) {
    PageHeading("مرصد السماء", "الشمس والنهار ومعلومات الرصد", accent, fg)
    GlassPanel() {
        Text("الشمس والنهار", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (times == null) Text("حدّد موقعك من صفحة الرئيسية لعرض بيانات الشمس.", color = muted)
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("الشروق", times.sunrise, accent, fg, muted)
            Metric("الغروب", times.sunset, accent, fg, muted)
            Metric("طول النهار", times.dayLength, accent, fg, muted)
        }
    }
    GlassPanel() {
        Text("خريطة الرصد", color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text("ستُضاف في مراحل لاحقة مواقع النجوم والكواكب، والارتفاع والسمت، ومسارات الأجرام خلال الليل.", color = muted, fontSize = 12.sp, lineHeight = 19.sp)
        Text("لا تتطلب الحسابات الفلكية الأساسية اتصالاً بالإنترنت عند اكتمال وحدتها المحلية.", color = muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsPage(dark: Boolean, onDarkChange: (Boolean) -> Unit, location: String, accent: Color, fg: Color, muted: Color,
    onLocate: () -> Unit, onSettings: () -> Unit) {
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
        Text("الموقع", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(location, color = muted, fontSize = 12.sp)
        Button(onClick = onLocate) { Text("تحديث الموقع") }
        TextButton(onClick = onSettings) { Text("إعدادات خدمات الموقع") }
    }
    GlassPanel(dark) {
        Text("عن التطبيق", color = accent, fontWeight = FontWeight.Bold)
        Text("مواقيت 0.3.0 • نسخة تطويرية. بعض الحسابات والمؤشرات ما زالت تقريبية وقيد التطوير.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
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
    val pressed by animateFloatAsState(1f, label = "tile")
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(if (dark) Color.White.copy(.08f) else Color.White.copy(.8f))
        .border(1.dp, accent.copy(alpha = .22f), RoundedCornerShape(22.dp)).clickable { onClick() }.padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subtitle, color = fg.copy(alpha = .7f), fontSize = 10.sp)
    }
}

@Composable
private fun GlassPanel(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val fill = if (dark) listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .055f))
        else listOf(Color.White.copy(alpha = .88f), Color.White.copy(alpha = .58f))
    val line = if (dark) Color.White.copy(alpha = .2f) else Color.White.copy(alpha = .9f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(Brush.linearGradient(fill))
            .border(1.dp, line, RoundedCornerShape(25.dp))
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
