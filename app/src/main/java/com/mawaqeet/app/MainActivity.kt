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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DeepGreen = Color(0xFF123C32)
private val Gold = Color(0xFFD7B56D)
private val Paper = Color(0xFFF6F4EC)

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
                locationMessage = "تم تحديد الموقع عبر GPS"
            } else {
                locationMessage = "جارٍ انتظار إشارة GPS… قد يستغرق التحديد الأول وقتاً"
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        latitude = location.latitude
                        longitude = location.longitude
                        locationMessage = "تم تحديد الموقع عبر GPS"
                        manager.removeUpdates(this)
                    }
                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        locationMessage = "يرجى تفعيل GPS من إعدادات الجهاز"
                    }
                }
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
            }
        } catch (_: SecurityException) {
            locationMessage = "لم تُمنح صلاحية الموقع"
        } catch (_: Exception) {
            locationMessage = "تعذّر الوصول إلى GPS؛ تحقق من تفعيله"
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readGps() else locationMessage = "يلزم السماح بالموقع لاستخدام GPS"
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = DeepGreen,
        secondary = Gold,
        background = Paper,
        surface = Color.White
    )) {
        CompositionLocalProvider {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Paper),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مَوَاقِيت", color = DeepGreen, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("تقويمك الإسلامي… معك أينما كنت", color = Color(0xFF69766F), fontSize = 13.sp)
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepGreen),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))),
                                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Gold)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(locationMessage, color = Color.White, fontSize = 14.sp)
                                    if (latitude != null && longitude != null)
                                        Text("%.4f, %.4f".format(Locale.US, latitude, longitude), color = Gold, fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) readGps()
                                    else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }) { Text("تحديد", color = Gold, fontWeight = FontWeight.Bold) }
                            }
                            Text("يعمل تحديد الموقع عبر مستقبل GPS ولا يحتاج إلى الإنترنت.", color = Color(0xFFCFDAD4), fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Text("مواقيت الصلاة", color = DeepGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (latitude == null || longitude == null) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                            Text("حدّد موقعك أولاً لعرض المواقيت المحسوبة محلياً دون اتصال.", Modifier.padding(18.dp), color = Color.DarkGray)
                        }
                    } else {
                        val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0
                        val times = PrayerCalculator.calculate(LocalDate.now(), latitude!!, longitude!!, offset)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                    Text("الشمس والسماء", color = DeepGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = Gold)
                                Text("الشروق", fontSize = 12.sp, color = Color.Gray)
                                Text(if (latitude == null) "--:--" else PrayerCalculator.calculate(LocalDate.now(), latitude!!, longitude!!,
                                    ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0).sunrise,
                                    color = DeepGreen, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = Gold)
                                Text("الغروب", fontSize = 12.sp, color = Color.Gray)
                                Text(if (latitude == null) "--:--" else PrayerCalculator.calculate(LocalDate.now(), latitude!!, longitude!!,
                                    ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0).sunset,
                                    color = DeepGreen, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.NightsStay, contentDescription = null, tint = DeepGreen)
                                Text("طول النهار", fontSize = 12.sp, color = Color.Gray)
                                Text(if (latitude == null) "--:--" else PrayerCalculator.calculate(LocalDate.now(), latitude!!, longitude!!,
                                    ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600.0).dayLength,
                                    color = DeepGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    Text("المرصد الفلكي", color = DeepGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE9E5D8)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("القمر والنجوم", color = DeepGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("سيُضاف حساب طور القمر ومواقع الأجرام السماوية ومساراتها في التحديثات القادمة، مع الحفاظ على العمل دون إنترنت.",
                                color = Color(0xFF59645E), fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
                item {
                    Text("الإصدار الأولي 0.1.0 • الحسابات فلكية محلية تقريبية", color = Color.Gray, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(name: String, time: String, highlighted: Boolean = true) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (highlighted) Color.White else Color(0xFFECE9DF)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = DeepGreen, fontWeight = FontWeight.SemiBold)
            Text(time, color = DeepGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
