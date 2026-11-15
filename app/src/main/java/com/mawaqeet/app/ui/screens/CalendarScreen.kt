package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.CDate
import com.mawaqeet.app.astro.CalendarKind
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.DayNightCalc
import com.mawaqeet.app.astro.Eclipse
import com.mawaqeet.app.astro.Eclipses
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.PhaseEvent
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.SeasonEvent
import com.mawaqeet.app.astro.Seasons
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.Exporter
import com.mawaqeet.app.ui.components.GlassAlertDialog
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OrnateTabs
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate

private class CalEvents(
    val phases: Map<LocalDate, List<PhaseEvent>>,
    val seasons: Map<LocalDate, List<SeasonEvent>>,
    val eclipses: Map<LocalDate, List<Eclipse>>
)

private val calWeekHeaders = listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")

private class CalNotable(val month: Int, val day: Int, val label: String)

private val calNotables = listOf(
    CalNotable(1, 1, "غرة محرّم — بداية السنة الهجرية"),
    CalNotable(1, 10, "10 محرّم"),
    CalNotable(3, 12, "12 ربيع الأول"),
    CalNotable(9, 1, "غرة رمضان — بداية الشهر"),
    CalNotable(10, 1, "غرة شوّال"),
    CalNotable(12, 9, "9 ذو الحجة (يوم عرفة)"),
    CalNotable(12, 10, "10 ذو الحجة")
)

private fun calRange(env: Env, kind: CalendarKind, a: LocalDate, b: LocalDate): String {
    val s = env.settings
    val ca = Calendars.convert(a, kind, s.hijriMethod, s.hijriAdjust)
    val cb = Calendars.convert(b, kind, s.hijriMethod, s.hijriAdjust)
    val na = Calendars.monthName(kind, ca.month, s.solarNames, s.maghrebMonths)
    val nb = Calendars.monthName(kind, cb.month, s.solarNames, s.maghrebMonths)
    return if (ca.year == cb.year && ca.month == cb.month) "$na ${ca.year}"
    else if (ca.year == cb.year) "$na – $nb ${ca.year}"
    else "$na ${ca.year} – $nb ${cb.year}"
}

private fun calDayText(env: Env, g: LocalDate, ev: CalEvents?): String = buildString {
    appendLine("${Calendars.weekDay(g)} ${env.gregorian(g)}")
    appendLine("هجري: ${env.hijri(g)}")
    appendLine("هجري شمسي: ${env.solarHijri(g)}")
    appendLine("يولياني: ${env.julian(g)}")
    appendLine("اليوم ${g.dayOfYear} من السنة")
    ev?.phases?.get(g)?.forEach { appendLine("${it.kind.ar}: ${env.time(it.millis)}") }
    ev?.seasons?.get(g)?.forEach { appendLine("${it.kind.ar}: ${env.time(it.millis)}") }
    ev?.eclipses?.get(g)?.forEach { appendLine("${it.type.ar}: ${env.time(it.max)}") }
    append(env.place.name)
}

@Composable
fun CalendarScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val ctx = LocalContext.current
    val s = env.settings
    val kinds = CalendarKind.values()
    val kind = kinds[s.mainCalendar.coerceIn(0, 3)]
    val secKind = if (kind == CalendarKind.GREGORIAN) CalendarKind.HIJRI else CalendarKind.GREGORIAN
    val today = env.today

    var ym by remember(kind) { mutableStateOf<Pair<Int, Int>?>(null) }
    var sel by remember { mutableStateOf<LocalDate?>(null) }

    val todayC = Calendars.convert(today, kind, s.hijriMethod, s.hijriAdjust)
    val cur = ym ?: (todayC.year to todayC.month)
    val (cy, cm) = cur

    val first = runCatching { Calendars.toGregorian(CDate(cy, cm, 1), kind, s.hijriMethod, s.hijriAdjust) }.getOrNull() ?: today
    val len = runCatching { Calendars.monthLength(cy, cm, kind, s.hijriMethod) }.getOrNull() ?: 30
    val last = first.plusDays((len - 1).toLong())
    val offset = (first.dayOfWeek.value + 1) % 7

    val events = rememberComputed(first, len, env.zone) {
        val from = env.dayStart(first)
        val to = env.dayStart(last.plusDays(1))
        val ph = Phases.between(from, to).groupBy { env.dateOf(it.millis) }
        val se = (first.year..last.year).flatMap { Seasons.of(it) }.filter { it.millis in from until to }.groupBy { env.dateOf(it.millis) }
        val ec = Eclipses.between(from, to).groupBy { env.dateOf(it.max) }
        CalEvents(ph, se, ec)
    }

    ScreenScaffold(title = "التقويم", subtitle = "${kind.ar} • ${env.place.name}", topLevel = true) {
        ScreenList {
            item {
                OrnateTabs(listOf("ميلادي", "يولياني", "هجري", "هجري شمسي"), s.mainCalendar) { i ->
                    env.update { it.copy(mainCalendar = i) }
                }
            }
            // رأس الشهر
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { ym = if (cm == 1) (cy - 1 to 12) else (cy to cm - 1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "الشهر السابق", tint = o.ink)
                        }
                        Column(Modifier.weight(1f).clickable { ym = null }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${Calendars.monthName(kind, cm, s.solarNames, s.maghrebMonths)} $cy ${Calendars.suffix(kind)}",
                                style = MaterialTheme.typography.titleLarge, color = o.ink, textAlign = TextAlign.Center
                            )
                            Text(calRange(env, secKind, first, last), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            if (ym != null) Text("اضغط للعودة إلى الشهر الحالي", fontSize = 11.sp, color = o.muted)
                        }
                        IconButton(onClick = { ym = if (cm == 12) (cy + 1 to 1) else (cy to cm + 1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "الشهر التالي", tint = o.ink)
                        }
                    }
                }
            }
            // الشبكة
            item {
                GlassCard {
                    Row(Modifier.fillMaxWidth()) {
                        calWeekHeaders.forEachIndexed { i, h ->
                            Text(
                                h, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, fontSize = 10.5.sp,
                                color = if (i == 6) o.success else o.gold, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val cells = offset + len
                    val rows = (cells + 6) / 7
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val idx = r * 7 + c
                                val d = idx - offset + 1
                                if (d < 1 || d > len) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    val g = first.plusDays((d - 1).toLong())
                                    val secDay = if (secKind == CalendarKind.GREGORIAN) g.dayOfMonth
                                    else Calendars.toHijri(g, s.hijriMethod, s.hijriAdjust).day
                                    CalCell(
                                        Modifier.weight(1f), d, secDay,
                                        isToday = g == today, friday = c == 6,
                                        phase = events?.phases?.containsKey(g) == true,
                                        season = events?.seasons?.containsKey(g) == true,
                                        eclipse = events?.eclipses?.containsKey(g) == true
                                    ) { sel = g }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                        CalLegend("أطوار القمر", o.gold)
                        CalLegend("فصول", o.success)
                        CalLegend("كسوف/خسوف", o.danger)
                    }
                }
            }
            // مناسبات هجرية
            item {
                val found = (0 until len).mapNotNull { i ->
                    val g = first.plusDays(i.toLong())
                    val h = Calendars.toHijri(g, s.hijriMethod, s.hijriAdjust)
                    calNotables.firstOrNull { it.month == h.month && it.day == h.day }?.let { g to it }
                }
                GlassCard(title = "تواريخ هجرية بارزة") {
                    if (found.isEmpty()) Text("لا توجد تواريخ بارزة في هذا الشهر", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                    found.forEach { (g, n) ->
                        InfoRow(n.label, "${Calendars.weekDay(g)} ${env.gregorian(g)}")
                    }
                }
            }
            item {
                Note(
                    "التاريخ الهجري محسوب بطريقة «${s.hijriMethod.ar}» وقد يختلف بيوم عن الرؤية المحلية للهلال. " +
                        "يمكن ضبط التعديل (±2 يوم) من الإعدادات؛ وهو يُعتمد أصلاً مع الحساب المدني، لكنه مطبّق هنا على الطريقتين."
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(onClick = { env.navigate(Routes.CONVERTER) }, Modifier.weight(1f), nav = true) { Text("تحويل التاريخ") }
                    GlassOutlinedButton(onClick = { env.navigate(Routes.TABLES) }, Modifier.weight(1f), nav = true) { Text("الجداول") }
                }
            }
        }
    }

    val d = sel
    if (d != null) {
        val noon = env.dayStart(d) + 12 * HOUR_MS
        val moon = remember(d) { Phases.state(noon) }
        val dn = remember(d, env.place) { DayNightCalc.compute(env.dayStart(d), env.lat, env.lon, env.place.heightM) }
        val txt = calDayText(env, d, events)
        GlassAlertDialog(
            onDismissRequest = { sel = null },
            confirmButton = { GlassButton(onClick = { Exporter.shareText(ctx, txt) }, nav = true) { Text("مشاركة") } },
            dismissButton = { GlassTonalButton(onClick = { sel = null }, nav = true) { Text("إغلاق") } },
            title = { Text("${Calendars.weekDay(d)} ${env.gregorian(d)}") },
            text = {
                Column {
                    InfoRow("ميلادي", env.gregorian(d))
                    InfoRow("هجري", env.hijri(d), valueColor = MaterialTheme.colorScheme.primary)
                    InfoRow("هجري شمسي", env.solarHijri(d))
                    InfoRow("يولياني", env.julian(d))
                    InfoRow("اليوم من السنة", "${d.dayOfYear}")
                    InfoRow("طور القمر", "${Phases.phaseEmoji(moon.phaseAngle)} ${Phases.phaseName(moon.phaseAngle)} (${Fmt.num(moon.illumination * 100, 0)}%)")
                    InfoRow("الشروق", env.time(dn.sunrise))
                    InfoRow("الغروب", env.time(dn.sunset))
                    val evs = buildList {
                        events?.phases?.get(d)?.forEach { add("${it.kind.ar} • ${env.time(it.millis)}") }
                        events?.seasons?.get(d)?.forEach { add("${it.kind.ar} • ${env.time(it.millis)}") }
                        events?.eclipses?.get(d)?.forEach { add("${it.type.ar} • ${env.time(it.max)}") }
                    }
                    if (evs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("أحداث اليوم", color = o.gold, style = MaterialTheme.typography.titleSmall)
                        evs.forEach { Text(it, color = o.ink, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        )
    }
}

@Composable
private fun CalLegend(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.5.sp, color = AppTheme.ornament.muted)
    }
}

@Composable
private fun CalCell(
    modifier: Modifier, main: Int, sec: Int, isToday: Boolean, friday: Boolean,
    phase: Boolean, season: Boolean, eclipse: Boolean, onClick: () -> Unit
) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.padding(1.5.dp).height(56.dp)
            .then(if (isToday) Modifier.background(primary.copy(alpha = 0.22f), shape) else if (friday) Modifier.background(o.success.copy(alpha = 0.08f), shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(
            "$main", fontSize = 18.sp, fontWeight = FontWeight.Black,
            color = if (isToday) primary else if (friday) o.success else o.ink
        )
        Text("$sec", fontSize = 10.sp, color = if (sec == 1) o.gold else o.muted, fontWeight = if (sec == 1) FontWeight.Bold else FontWeight.Normal)
        Row(Modifier.height(7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (phase) Box(Modifier.size(5.dp).background(o.gold, CircleShape))
            if (season) Box(Modifier.size(5.dp).background(o.success, CircleShape))
            if (eclipse) Box(Modifier.size(5.dp).background(o.danger, CircleShape))
        }
    }
}
