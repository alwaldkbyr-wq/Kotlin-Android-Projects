package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerCalc
import com.mawaqeet.app.astro.Qibla
import com.mawaqeet.app.data.Cities
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.Place
import com.mawaqeet.app.ui.components.BigButton
import com.mawaqeet.app.ui.components.ChipsSelector
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.GpsFix
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.TableRowCells
import com.mawaqeet.app.ui.components.rememberGpsFix
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneOffset

/** منطقة زمنية تقديرية من خط الطول (ساعات صحيحة) حين لا تُعرف المنطقة الحقيقية */
private fun travelZoneGuess(lon: Double): String =
    ZoneOffset.ofHours(Math.round(lon / 15.0).toInt().coerceIn(-12, 14)).id

private fun travelZoneIsGuess(p: Place) = p.zoneId == "Z" || p.zoneId.startsWith("+") || p.zoneId.startsWith("-")

private val travelPrayers = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

@Composable
fun TravelPrayerScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val gps = rememberGpsFix(2000L).value
    var from by remember { mutableStateOf<Place?>(null) }
    var dest by remember { mutableStateOf<Place?>(null) }
    var date by remember { mutableStateOf(env.today) }
    val start = from ?: env.place
    val d = dest

    ScreenScaffold(
        title = "صلاة المسافر", onBack = env.back, subtitle = "المسافة والمواقيت والقبلة عند الوجهة",
        actions = {
            ShareAction {
                if (d == null) "صلاة المسافر: اختر الوجهة أولاً"
                else {
                    val km = Qibla.distanceKm(start.lat, start.lon, d.lat, d.lon)
                    "صلاة المسافر\nمن: ${start.name}\nإلى: ${d.name}\nالمسافة على الدائرة العظمى: ${Fmt.num(km, 0)} كم (تقريبية)\nاتجاه القبلة عند الوجهة: ${Fmt.deg(Qibla.bearing(d.lat, d.lon))}"
                }
            }
        }
    ) {
        ScreenList {
            item { TravelPicker("مكان الانطلاق", true, from ?: env.place, gps) { from = it } }
            item { TravelPicker("الوجهة", false, dest, gps) { dest = it } }
            if (d == null) {
                item { Note("اختر الوجهة من قائمة المدن أو من موقعك الحالي أو بإدخال الإحداثيات لتظهر المسافة والمواقيت.") }
            } else {
                val km = Qibla.distanceKm(start.lat, start.lon, d.lat, d.lon)
                val roadLo = km * 1.2
                val roadHi = km * 1.4
                item { SectionTitle("المسافة") }
                item {
                    GlassCard(title = "${start.name} ← ${d.name}") {
                        MetricRow("على الدائرة العظمى" to "${Fmt.num(km, 0)} كم", "بالطريق (تقريباً)" to "${Fmt.num(roadLo, 0)}–${Fmt.num(roadHi, 0)} كم")
                        Spacer(Modifier.height(8.dp))
                        val (txt, col) = when {
                            km < 80.0 -> "أقل من المسافة المتداولة (نحو 80 كم)" to o.muted
                            km < 89.0 -> "قريبة من الحد المتداول (80–89 كم)" to o.warning
                            else -> "تتجاوز المسافة المتداولة (نحو 80–89 كم)" to o.success
                        }
                        Pill(txt, col)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "المسافة المحسوبة خط مستقيم على سطح الأرض؛ والطريق الفعلي أطول عادةً بنحو 20% إلى 40% (تقدير تقريبي يختلف حسب الطريق). " +
                                "ومسافة قصر الصلاة المتداولة نحو أربعة بُرُد، أي ما يقارب 80 كم عند كثيرين (وتُذكر أرقام حتى نحو 89 كم عند غيرهم). " +
                                "وللعلماء في تقديرها وفي كونها بالطريق أو بالخط المستقيم خلاف، ويرى بعضهم الرجوع إلى العُرف دون تحديد مسافة ثابتة؛ فاسأل من تثق بعلمه عند الحاجة.",
                            color = o.muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item { SectionTitle("مواقيت الصلاة عند الوجهة") }
                item { DayStepper(date, { date = it }) }
                item {
                    val ps = env.settings.prayerSettings
                    val destDay = PrayerCalc.compute(date.atStartOfDay(d.zone).toInstant().toEpochMilli(), d.lat, d.lon, ps.copy(heightM = d.heightM))
                    val startDay = PrayerCalc.compute(date.atStartOfDay(start.zone).toInstant().toEpochMilli(), start.lat, start.lon, ps.copy(heightM = start.heightM))
                    val offD = d.zone.rules.getOffset(Instant.ofEpochMilli(env.now)).totalSeconds
                    val offS = start.zone.rules.getOffset(Instant.ofEpochMilli(env.now)).totalSeconds
                    val dh = (offD - offS) / 3600.0
                    GlassCard(title = d.name, subtitle = d.zoneId) {
                        InfoRow("الوقت الآن عند الوجهة", Fmt.time(env.now, d.zone, env.settings.h24, true), emphasize = true)
                        InfoRow("فرق التوقيت عن الانطلاق", (if (dh >= 0) "+" else "−") + Fmt.num(Math.abs(dh), 1) + " ساعة")
                        Spacer(Modifier.height(6.dp))
                        TableRowCells(listOf("الصلاة", "عند الوجهة", "عند الانطلاق"), header = true)
                        travelPrayers.forEach { p ->
                            TableRowCells(listOf(p.ar, Fmt.time(destDay[p], d.zone, env.settings.h24), Fmt.time(startDay[p], start.zone, env.settings.h24)))
                        }
                    }
                }
                if (travelZoneIsGuess(d)) item { Note("المنطقة الزمنية للوجهة مقدّرة من خط الطول (ساعات صحيحة) فقد تختلف الساعة عن الساعة الرسمية؛ اختر مدينة من القائمة لتوقيت أدق.") }
                item { Note("تُحسب المواقيت بطريقة الحساب وإعداداتك الحالية.") }

                item { SectionTitle("القبلة") }
                item {
                    val qd = Qibla.bearing(d.lat, d.lon)
                    val qs = Qibla.bearing(start.lat, start.lon)
                    GlassCard(title = "اتجاه القبلة") {
                        MetricRow("عند الوجهة" to Fmt.deg(qd), "عند الانطلاق" to Fmt.deg(qs))
                        InfoRow("اتجاه الوجهة", Fmt.direction(qd))
                        InfoRow("المسافة إلى الكعبة من الوجهة", "${Fmt.num(Qibla.distanceKm(d.lat, d.lon), 0)} كم")
                        Text("الاتجاه بالدرجات من الشمال الحقيقي مع عقارب الساعة.", color = o.muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item { SectionTitle("القصر والجمع") }
            item {
                GlassCard(title = "القصر") {
                    Text(
                        "القصر: أن تُصلّى الصلاة الرباعية (الظهر والعصر والعشاء) ركعتين. ويُشترط عادةً: سفر مباح يبلغ المسافة المعتبرة، ونية السفر، ومفارقة عمران البلد، وألا يقتدي بمقيم. " +
                            "وتختلف المذاهب في حدّ المسافة، وفي مدة الإقامة التي ينقطع بها السفر، وفي كون القصر واجباً أو سنة أو رخصة.",
                        color = primary, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                GlassCard(title = "الجمع") {
                    Text(
                        "الجمع: ضمّ الظهر مع العصر، أو المغرب مع العشاء، في وقت إحداهما (تقديماً أو تأخيراً). ويُذكر له عند من يقول به: السفر، والمطر أو المرض أو المشقة الشديدة ونحوها. " +
                            "وللعلماء في شروطه وفي جوازه في الإقامة خلاف، وهو رخصة لا ينبغي اتخاذه عادة.",
                        color = o.ink, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item { Note("هذه المعلومات عامة للتعريف، وليست فتوى. للحالات الخاصة راجع أهل العلم الموثوقين.") }
        }
    }
}

@Composable
private fun TravelPicker(label: String, allowApp: Boolean, value: Place?, gps: GpsFix?, onValue: (Place) -> Unit) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    var mode by remember { mutableStateOf(if (allowApp) "app" else "city") }
    var query by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    val modes = linkedMapOf<String, String>().apply {
        if (allowApp) put("app", "موقعي في التطبيق")
        put("city", "مدينة")
        put("gps", "GPS")
        put("manual", "إحداثيات")
    }
    GlassCard(title = label, subtitle = value?.let { it.name + " • " + Fmt.latLon(it.lat, it.lon) } ?: "لم تُحدَّد بعد") {
        ChipsSelector(modes, mode) {
            mode = it
            if (it == "app") onValue(env.place)
        }
        Spacer(Modifier.height(8.dp))
        when (mode) {
            "city" -> {
                Field("ابحث عن مدينة", query, { query = it })
                val qq = query.trim()
                if (qq.isEmpty()) {
                    Text("اكتب اسم المدينة للبحث في ${Cities.all.size} مدينة", color = o.muted, style = MaterialTheme.typography.labelMedium)
                } else {
                    val res = Cities.all.filter { it.name.contains(qq, ignoreCase = true) }
                    if (res.isEmpty()) Text("لا توجد نتائج", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                    res.take(8).forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { onValue(c); query = "" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(c.name, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge)
                            Text(Fmt.latLon(c.lat, c.lon), color = o.muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (res.size > 8) Text("… ${res.size - 8} نتيجة أخرى، أضف حروفاً لتضييق البحث", color = o.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            "gps" -> {
                if (gps == null) {
                    Text("جارٍ انتظار إشارة الأقمار الصناعية… اخرج إلى مكان مكشوف", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    BigButton("تحديد موقعي بالـ GPS", Icons.Filled.MyLocation, nav = true) { env.requestGps() }
                } else {
                    Text("موقعك الحالي: ${Fmt.latLon(gps.lat, gps.lon)} (الدقة ${Fmt.num(gps.accuracy.toDouble(), 0)} م)", color = o.ink, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    GlassTonalButton(onClick = {
                        // قرب الموقع المحفوظ نستعمل منطقته الزمنية، وإلا نقدّرها من خط الطول
                        val near = com.mawaqeet.app.ui.components.Geo.distanceM(gps.lat, gps.lon, env.lat, env.lon) < 300_000.0
                        onValue(Place("موقع GPS", gps.lat, gps.lon, if (near) env.place.zoneId else travelZoneGuess(gps.lon), gps.altitude ?: 0.0))
                    }, nav = true) { Text("اعتماد موقعي الحالي") }
                }
            }
            "manual" -> {
                Field("خط العرض (−90 إلى 90)", lat, { lat = it }, number = true)
                Spacer(Modifier.height(6.dp))
                Field("خط الطول (−180 إلى 180)", lon, { lon = it }, number = true)
                val la = lat.trim().replace(',', '.').toDoubleOrNull()
                val lo = lon.trim().replace(',', '.').toDoubleOrNull()
                val ok = la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0
                Spacer(Modifier.height(6.dp))
                GlassTonalButton(
                    onClick = { if (ok) onValue(Place("إحداثيات ${Fmt.num(la!!, 3)}، ${Fmt.num(lo!!, 3)}", la, lo, travelZoneGuess(lo))) },
                    enabled = ok, nav = true
                ) { Text("اعتماد الإحداثيات") }
            }
        }
    }
}
