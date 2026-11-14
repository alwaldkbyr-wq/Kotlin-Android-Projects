package com.mawaqeet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Night = Color(0xFF071A2B)
private val DeepBlue = Color(0xFF102D43)
private val Emerald = Color(0xFF1B6656)
private val Gold = Color(0xFFE4C27A)
private val Mist = Color(0xFFEAF2F0)
private val Glass = Color.White.copy(alpha = 0.105f)
private val GlassLine = Color.White.copy(alpha = 0.22f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MawaqeetApp() }
    }
}

@Composable
fun MawaqeetApp() {
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var locationMessage by remember { mutableStateOf("لم يُحدّد الموقع بعد") }
    val context = LocalContext.current

    fun readGps() {
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (last != null) {
                latitude = last.latitude
                longitude = last.longitude
                locationMessage = "الموقع محدد عبر GPS"
            } else {
                locationMessage = "بانتظار إشارة الأقمار الصناعية…"
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        latitude = location.latitude
                        longitude = location.longitude
                        locationMessage = "الموقع محدد عبر GPS"
                        manager.removeUpdates(this)
                    }
                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        locationMessage = "فعّل GPS من إعدادات الجهاز"
                    }
                }
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
            }
        } catch (_: SecurityException) {
            locationMessage = "لم تُمنح صلاحية الموقع"
        } catch (_: Exception) {
            locationMessage = "تعذّر الوصول إلى GPS"
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readGps() else locationMessage = "يلزم السماح بالموقع لاستخدام GPS"
    }

    val today = LocalDate.now()
    val hijri = remember(today) { HijriCalendarInfo.from(today) }
    val moon = remember(today) { MoonInfo.forDate(today) }
    val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
    val times = if (latitude != null && longitude != null)
        PrayerCalculator.calculate(today, latitude!!, longitude!!, offset) else null

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Emerald, secondary = Gold, background = Night, surface = DeepBlue,
        onBackground = Mist, onSurface = Mist
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, DeepBlue, Color(0xFF153D43))))) {
            StarField()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("مَوَاقِيت", color = Mist, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                            Text("بين هدي السماء ودقّة الفلك", color = Gold.copy(alpha = .9f), fontSize = 12.sp)
                        }
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Glass).border(1.dp, GlassLine, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.NightsStay, contentDescription = "الهلال", tint = Gold, modifier = Modifier.size(25.dp))
                        }
                    }
                }
                item {
                    GlassPanel {
                        Text(today.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))),
                            color = Mist, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(hijri.fullDate, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("التاريخ الهجري الحسابي • قد يختلف عن إعلان الرؤية الشرعية",
                            color = Mist.copy(alpha = .68f), fontSize = 10.sp)
                    }
                }
                item {
                    GlassPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Gold)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(locationMessage, color = Mist, fontWeight = FontWeight.SemiBold)
                                if (latitude != null && longitude != null)
                                    Text("%.4f°, %.4f°".format(Locale.US, latitude, longitude),
                                        color = Gold, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) readGps()
                                else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }) { Text("تحديد", color = Gold, fontWeight = FontWeight.Bold) }
                        }
                        Text("تحديد GPS مباشر دون خرائط أو اتصال بالإنترنت.", color = Mist.copy(alpha = .65f), fontSize = 10.sp)
                    }
                }
                item {
                    SectionTitle("مواقيت الصلاة", "حساب محلي حسب الإحداثيات")
                    GlassPanel {
                        if (times == null) {
                            Text("حدّد موقعك عبر GPS لعرض المواقيت.", color = Mist)
                        } else {
                            PrayerRow("الفجر", times.fajr)
                            PrayerRow("الشروق", times.sunrise, false)
                            PrayerRow("الظهر", times.dhuhr)
                            PrayerRow("العصر", times.asr)
                            PrayerRow("المغرب", times.maghrib)
                            PrayerRow("العشاء", times.isha)
                        }
                    }
                }
                item {
                    SectionTitle("مراقبة الهلال", "الجانب الفلكي من التقويم الهجري")
                    GlassPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(62.dp).clip(CircleShape).background(Color(0xFF192D43))
                                .border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(moon.emoji, fontSize = 34.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(moon.phaseName, color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("عمر القمر التقريبي: ${moon.ageDays} يوم", color = Mist, fontSize = 12.sp)
                                Text("الإضاءة التقريبية: ${moon.illumination}٪", color = Mist.copy(alpha = .7f), fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = GlassLine)
                        Text("موعد الاقتران القادم: ${moon.daysToNewMoon} يوم تقريباً", color = Mist, fontSize = 13.sp)
                        Text("هذه مؤشرات فلكية أولية وليست حكماً بإمكان رؤية الهلال؛ الرؤية تتأثر بالموقع والطقس والشفافية والأفق، ويُعتمد الإعلان الشرعي المحلي لدخول الشهر.",
                            color = Mist.copy(alpha = .68f), fontSize = 10.sp, lineHeight = 16.sp)
                    }
                }
                item {
                    SectionTitle("الشمس والنهار", "حركة الشمس في موقعك")
                    GlassPanel {
                        if (times == null) Text("تظهر البيانات بعد تحديد الموقع.", color = Mist)
                        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SkyMetric("الشروق", times.sunrise, "☀")
                            SkyMetric("الغروب", times.sunset, "☼")
                            SkyMetric("طول النهار", times.dayLength, "◷")
                        }
                    }
                }
                item {
                    GlassPanel {
                        Text("الرصد الفلكي", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("ستُضاف لاحقاً مواقع النجوم والكواكب والارتفاع والسمت ومسارات الأجرام، بحسابات تعمل محلياً بالكامل.",
                            color = Mist.copy(alpha = .75f), fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
                item {
                    Text("مواقيت • إصدار تأسيسي 0.2.0", color = Mist.copy(alpha = .45f), fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun StarField() {
    Canvas(Modifier.fillMaxSize()) {
        val points = listOf(
            .08f to .09f, .83f to .07f, .69f to .18f, .19f to .27f, .92f to .34f,
            .43f to .39f, .11f to .52f, .77f to .61f, .29f to .72f, .91f to .83f,
            .57f to .91f, .04f to .94f, .49f to .12f, .35f to .06f
        )
        points.forEachIndexed { index, p ->
            drawCircle(Color.White.copy(alpha = if (index % 3 == 0) .55f else .24f),
                radius = if (index % 4 == 0) 2.1f else 1.2f,
                center = androidx.compose.ui.geometry.Offset(size.width * p.first, size.height * p.second))
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .15f), Color.White.copy(alpha = .055f))))
            .border(1.dp, GlassLine, RoundedCornerShape(24.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Mist, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Gold.copy(alpha = .72f), fontSize = 10.sp)
    }
}

@Composable
private fun PrayerRow(name: String, time: String, normal: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = if (normal) Mist else Mist.copy(alpha = .65f), fontSize = 14.sp)
        Text(time, color = if (normal) Gold else Mist.copy(alpha = .72f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SkyMetric(label: String, value: String, symbol: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, color = Gold, fontSize = 22.sp)
        Text(label, color = Mist.copy(alpha = .7f), fontSize = 10.sp)
        Text(value, color = Mist, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private data class HijriCalendarInfo(val fullDate: String) {
    companion object {
        private val months = listOf(
            "محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"
        )
        fun from(date: LocalDate): HijriCalendarInfo {
            return try {
                val h = HijrahDate.from(date)
                val day = h.get(ChronoField.DAY_OF_MONTH)
                val month = h.get(ChronoField.MONTH_OF_YEAR)
                val year = h.get(ChronoField.YEAR)
                HijriCalendarInfo("$day ${months[(month - 1).coerceIn(0, 11)]} $year هـ")
            } catch (_: Exception) {
                HijriCalendarInfo("تعذّر تحويل التاريخ الهجري")
            }
        }
    }
}

private data class MoonInfo(
    val ageDays: String,
    val illumination: Int,
    val phaseName: String,
    val emoji: String,
    val daysToNewMoon: String
) {
    companion object {
        // Approximate synodic phase from a known new-moon epoch. Not a visibility model.
        fun forDate(date: LocalDate): MoonInfo {
            val epoch = LocalDate.of(2000, 1, 6)
            val days = java.time.temporal.ChronoUnit.DAYS.between(epoch, date).toDouble() - 0.26
            val synodicMonth = 29.530588853
            val age = ((days % synodicMonth) + synodicMonth) % synodicMonth
            val phase = age / synodicMonth
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
            return MoonInfo("%.1f".format(Locale.US, age), illumination, name, emoji,
                "%.1f".format(Locale.US, synodicMonth - age))
        }
    }
}
