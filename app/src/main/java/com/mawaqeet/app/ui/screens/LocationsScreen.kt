package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.data.Cities
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.Place
import com.mawaqeet.app.ui.components.BigButton
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassAlertDialog
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.ZoneId
import java.util.Locale

private val locCommonZones = listOf(
    "Africa/Algiers", "Africa/Casablanca", "Africa/Tunis", "Africa/Tripoli", "Africa/Cairo", "Africa/Khartoum",
    "Asia/Riyadh", "Asia/Kuwait", "Asia/Qatar", "Asia/Dubai", "Asia/Muscat", "Asia/Baghdad", "Asia/Amman",
    "Asia/Beirut", "Asia/Damascus", "Asia/Jerusalem", "Asia/Aden", "Europe/Istanbul", "Europe/London",
    "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Europe/Rome", "Europe/Moscow", "America/New_York",
    "America/Chicago", "America/Los_Angeles", "America/Toronto", "America/Sao_Paulo", "Africa/Lagos",
    "Africa/Nairobi", "Africa/Johannesburg", "Asia/Tehran", "Asia/Karachi", "Asia/Kolkata", "Asia/Dhaka",
    "Asia/Jakarta", "Asia/Kuala_Lumpur", "Asia/Singapore", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney", "UTC"
)

@Composable
fun LocationsScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val s = env.settings
    val cur = env.place
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    fun addPlace(p: Place) {
        env.update { st ->
            val existing = st.places.indexOfFirst { !it.gps && it.name == p.name && Math.abs(it.lat - p.lat) < 0.01 && Math.abs(it.lon - p.lon) < 0.01 }
            if (existing >= 0) st.copy(placeIndex = existing)
            else st.copy(places = st.places + p, placeIndex = st.places.size)
        }
    }

    ScreenScaffold(title = "المواقع", onBack = env.back, subtitle = "اختر مدينتك أو حدّد موقعك") {
        ScreenList {
            item {
                GlassCard(title = "الموقع الحالي") {
                    Text(cur.name, style = MaterialTheme.typography.headlineSmall, color = o.ink)
                    Spacer(Modifier.height(4.dp))
                    InfoRow("الإحداثيات", Fmt.latLon(cur.lat, cur.lon))
                    InfoRow("المنطقة الزمنية", cur.zoneId)
                    InfoRow("الارتفاع", "${Fmt.num(cur.heightM, 0)} م")
                    if (cur.gps) InfoRow("المصدر", "GPS")
                    Spacer(Modifier.height(8.dp))
                    BigButton("تحديد موقعي بالـ GPS", Icons.Filled.MyLocation, nav = true) { env.requestGps() }
                }
            }
            item { SectionTitle("مواقعي المحفوظة") }
            item {
                GlassCard {
                    s.places.forEachIndexed { i, p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { env.update { it.copy(placeIndex = i) } }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (p.gps) Icons.Filled.MyLocation else Icons.Filled.Place, null,
                                tint = if (i == s.placeIndex) MaterialTheme.colorScheme.primary else o.muted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = if (i == s.placeIndex) MaterialTheme.colorScheme.primary else o.ink, style = MaterialTheme.typography.titleSmall)
                                Text(Fmt.latLon(p.lat, p.lon) + " • " + p.zoneId, color = o.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (s.places.size > 1) {
                                IconButton(onClick = {
                                    env.update { st ->
                                        if (st.places.size <= 1) st
                                        else {
                                            val np = st.places.toMutableList().also { it.removeAt(i) }
                                            val idx = when {
                                                st.placeIndex > i -> st.placeIndex - 1
                                                st.placeIndex == i -> 0
                                                else -> st.placeIndex
                                            }
                                            st.copy(places = np, placeIndex = idx.coerceIn(0, np.size - 1))
                                        }
                                    }
                                }) { Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("إضافة مدينة") }
            item {
                GlassCard {
                    Field("ابحث عن مدينة (الجزائر، عربية، عالمية)", query, { query = it })
                    Spacer(Modifier.height(8.dp))
                    val q = query.trim()
                    val results = if (q.isEmpty()) Cities.all.take(12) else Cities.all.filter { it.name.contains(q, ignoreCase = true) }
                    if (q.isEmpty()) Text("اكتب اسم المدينة للبحث في ${Cities.all.size} مدينة", color = o.muted, style = MaterialTheme.typography.labelMedium)
                    if (results.isEmpty()) Text("لا توجد نتائج", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                    results.take(40).forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { addPlace(c) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(c.name, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge)
                            Text(Fmt.latLon(c.lat, c.lon), color = o.muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (results.size > 40) Text("… ${results.size - 40} نتيجة أخرى، أضف حروفاً لتضييق البحث", color = o.muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    GlassOutlinedButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إدخال يدوي للإحداثيات")
                    }
                }
            }
            item { Note("الإحداثيات تُحفظ على هاتفك فقط، وتُستعمل لحساب المواقيت والأفلاك دون اتصال بالإنترنت.") }
        }
    }

    if (manual) LocManualDialog(onDismiss = { manual = false }, onSave = { addPlace(it); manual = false })
}

@Composable
private fun LocManualDialog(onDismiss: () -> Unit, onSave: (Place) -> Unit) {
    val o = AppTheme.ornament
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("0") }
    var zone by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var zoneQuery by remember { mutableStateOf("") }
    var showZones by remember { mutableStateOf(false) }

    val la = lat.trim().replace(',', '.').toDoubleOrNull()
    val lo = lon.trim().replace(',', '.').toDoubleOrNull()
    val h = height.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    val valid = name.isNotBlank() && la != null && la in -90.0..90.0 && lo != null && lo in -180.0..180.0
    val allZones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val zq = zoneQuery.trim()
    val zones = if (zq.isEmpty()) locCommonZones else allZones.filter { it.contains(zq, ignoreCase = true) }.take(30)

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدخال موقع يدوياً") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("اسم المكان", name, { name = it })
                Field("خط العرض (−90 إلى 90)", lat, { lat = it }, number = true)
                Field("خط الطول (−180 إلى 180)", lon, { lon = it }, number = true)
                Field("الارتفاع عن سطح البحر (متر)", height, { height = it }, number = true)
                Text("المنطقة الزمنية: $zone", color = o.ink, style = MaterialTheme.typography.titleSmall)
                Field("ابحث عن منطقة زمنية", zoneQuery, { zoneQuery = it; showZones = true })
                Column(Modifier.fillMaxWidth()) {
                    zones.take(if (showZones || zq.isNotEmpty()) 30 else 8).forEach { z ->
                        Text(
                            z,
                            Modifier.fillMaxWidth().clickable { zone = z }.padding(vertical = 7.dp),
                            color = if (z == zone) MaterialTheme.colorScheme.primary else o.ink,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (zq.isEmpty() && !showZones) TextButton(onClick = { showZones = true }) { Text("المزيد…") }
                }
                if (!valid) Text("أدخل اسماً وإحداثيات صحيحة", color = o.muted, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (valid) onSave(Place(name.trim(), la!!, lo!!, zone, h.coerceIn(-500.0, 9000.0))) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
