package com.mawaqeet.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.DayNightCalc
import com.mawaqeet.app.astro.Eclipses
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.Seasons
import com.mawaqeet.app.astro.fmtDuration
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.Exporter
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SelectField
import com.mawaqeet.app.ui.components.TableRowCells
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate
import java.util.Locale

private class TablesData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val title: String,
    val subtitle: String,
    val file: String
)

private val tableTypes = listOf(
    "مواقيت الصلاة لشهر", "مواقيت الصلاة لسنة كاملة", "الشروق والغروب وطلوع القمر لشهر",
    "أطوار القمر لسنة", "تحويل التقاويم لشهر", "الكسوف والخسوف", "الفصول"
)

private fun tblShort(g: LocalDate) = "%02d/%02d/%d".format(Locale.US, g.dayOfMonth, g.monthValue, g.year)

private fun tblHijri(env: Env, g: LocalDate): String {
    val h = Calendars.toHijri(g, env.settings.hijriMethod, env.settings.hijriAdjust)
    return "${h.day}/${h.month}/${h.year}"
}

private fun tblDays(first: LocalDate, count: Int) = (0 until count).map { first.plusDays(it.toLong()) }

private fun tblPrayerRows(env: Env, days: List<LocalDate>) = days.map { g ->
    val p = env.prayers(g)
    listOf(tblShort(g), tblHijri(env, g), Calendars.weekDay(g)) + Prayer.values().map { env.time(p[it]) }
}

private fun tblBuild(env: Env, type: Int, year: Int, month: Int, n: Int): TablesData {
    val place = env.place.name
    val sub = "$place • ${Fmt.latLon(env.lat, env.lon)}"
    val ms = "%02d".format(Locale.US, month)
    val first = LocalDate.of(year, month, 1)
    val monthDays = tblDays(first, first.lengthOfMonth())
    val monthName = (if (env.settings.maghrebMonths) Calendars.maghrebMonths else Calendars.gregorianMonths)[month - 1]
    return when (type) {
        0 -> TablesData(
            listOf("التاريخ", "الهجري", "اليوم") + Prayer.values().map { it.ar },
            tblPrayerRows(env, monthDays), "مواقيت الصلاة — $monthName $year", "$sub • ${env.settings.method.ar}", "مواقيت-الصلاة-$year-$ms"
        )
        1 -> {
            val jan = LocalDate.of(year, 1, 1)
            TablesData(
                listOf("التاريخ", "الهجري", "اليوم") + Prayer.values().map { it.ar },
                tblPrayerRows(env, tblDays(jan, jan.lengthOfYear())), "مواقيت الصلاة — سنة $year", "$sub • ${env.settings.method.ar}", "مواقيت-الصلاة-$year"
            )
        }
        2 -> TablesData(
            listOf("التاريخ", "اليوم", "الشروق", "الغروب", "طول النهار", "طلوع القمر", "غروب القمر", "الإضاءة"),
            monthDays.map { g ->
                val ds = env.dayStart(g)
                val dn = DayNightCalc.compute(ds, env.lat, env.lon, env.place.heightM)
                val mo = RiseSetCalc.compute(Body.MOON, ds, env.lat, env.lon)
                val ill = Phases.state(ds + 12 * HOUR_MS).illumination
                listOf(
                    tblShort(g), Calendars.weekDay(g), env.time(dn.sunrise), env.time(dn.sunset),
                    if (dn.sunrise == null || dn.sunset == null) "--" else fmtDuration(dn.dayLengthMin),
                    env.time(mo.rise), env.time(mo.set), "${Fmt.num(ill * 100, 0)}%"
                )
            },
            "الشمس والقمر — $monthName $year", sub, "الشمس-والقمر-$year-$ms"
        )
        3 -> {
            val ev = Phases.between(env.dayStart(LocalDate.of(year, 1, 1)), env.dayStart(LocalDate.of(year + 1, 1, 1)))
            TablesData(
                listOf("الطور", "التاريخ", "اليوم", "الوقت", "الهجري"),
                ev.map { e ->
                    val g = env.dateOf(e.millis)
                    listOf(e.kind.ar, tblShort(g), Calendars.weekDay(g), env.time(e.millis), tblHijri(env, g))
                },
                "أطوار القمر — سنة $year", sub, "أطوار-القمر-$year"
            )
        }
        4 -> TablesData(
            listOf("ميلادي", "هجري", "هجري شمسي", "يولياني", "اليوم"),
            monthDays.map { g ->
                listOf(env.gregorian(g), env.hijri(g), env.solarHijri(g), env.julian(g), Calendars.weekDay(g))
            },
            "تحويل التقاويم — $monthName $year", "التاريخ الهجري: ${env.settings.hijriMethod.ar}", "تحويل-التقاويم-$year-$ms"
        )
        5 -> {
            val ev = Eclipses.between(env.dayStart(LocalDate.of(year, 1, 1)), env.dayStart(LocalDate.of(year + n, 1, 1)))
            TablesData(
                listOf("النوع", "التاريخ", "اليوم", "الوقت (الذروة)", "القدر"),
                ev.map { e ->
                    val g = env.dateOf(e.max)
                    listOf(e.type.ar, tblShort(g), Calendars.weekDay(g), env.time(e.max), Fmt.num(e.magnitude, 2))
                },
                "الكسوف والخسوف ${year}–${year + n - 1}", "$sub • الأوقات بالتوقيت المحلي", "الكسوف-والخسوف-$year-${year + n - 1}"
            )
        }
        else -> TablesData(
            listOf("السنة", "الحدث", "التاريخ", "اليوم", "الوقت"),
            (year until year + n).flatMap { y ->
                Seasons.of(y).map { e ->
                    val g = env.dateOf(e.millis)
                    listOf("$y", e.kind.ar, tblShort(g), Calendars.weekDay(g), env.time(e.millis))
                }
            },
            "الفصول ${year}–${year + n - 1}", "$sub • الأوقات بالتوقيت المحلي", "الفصول-$year-${year + n - 1}"
        )
    }
}

@Composable
private fun TablesStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "إنقاص", tint = o.ink) }
        Text(value, Modifier.width(110.dp), color = o.ink, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "زيادة", tint = o.ink) }
    }
}

@Composable
fun TablesScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val ctx = LocalContext.current
    val today = env.today

    var type by remember { mutableStateOf(0) }
    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.monthValue) }
    var n by remember { mutableStateOf(10) }

    val needsMonth = type == 0 || type == 2 || type == 4
    val needsN = type == 5 || type == 6
    val monthNames = if (env.settings.maghrebMonths) Calendars.maghrebMonths else Calendars.gregorianMonths

    val data = rememberComputed(type, year, month, n, env.settings, env.place) { tblBuild(env, type, year, month, n) }

    fun act(block: () -> Unit) {
        runCatching(block).onFailure { Toast.makeText(ctx, "تعذّر إنشاء الملف", Toast.LENGTH_SHORT).show() }
    }
    fun pdf(d: TablesData) = Exporter.pdf(ctx, d.file, d.title, d.subtitle, d.headers, d.rows)
    fun csv(d: TablesData) = Exporter.csv(ctx, d.file, d.headers, d.rows)

    ScreenScaffold(title = "الجداول والتصدير", onBack = env.back, subtitle = "PDF • CSV/Excel • طباعة • مشاركة") {
        ScreenList {
            item {
                GlassCard(title = "نوع الجدول") {
                    SelectField("الجدول", tableTypes.mapIndexed { i, t -> i to t }, type, { type = it })
                    Spacer(Modifier.height(6.dp))
                    TablesStepper("السنة", "$year", { year -= 1 }, { year += 1 })
                    if (needsMonth) TablesStepper("الشهر", monthNames[month - 1], { month = if (month == 1) 12 else month - 1 }, { month = if (month == 12) 1 else month + 1 })
                    if (needsN) TablesStepper("عدد السنوات", "$n", { if (n > 1) n -= 1 }, { if (n < 30) n += 1 })
                }
            }
            if (data == null) {
                item { Busy() }
            } else {
                item {
                    GlassCard(title = data.title, subtitle = "${data.rows.size} سطر • ${data.subtitle}") {
                        val colW = if (data.headers.size > 6) 74.dp else 92.dp
                        Box(Modifier.horizontalScroll(rememberScrollState())) {
                            androidx.compose.foundation.layout.Column(Modifier.width(colW * data.headers.size)) {
                                TableRowCells(data.headers, header = true)
                                data.rows.take(40).forEach { TableRowCells(it) }
                            }
                        }
                        if (data.rows.size > 40) {
                            Text("المعروض أول 40 سطراً فقط؛ الملف المُصدَّر يحوي كل الأسطر (${data.rows.size}).", fontSize = 11.sp, color = o.muted, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                item {
                    GlassCard(title = "تصدير ومشاركة") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(onClick = { act { Exporter.open(ctx, pdf(data), "application/pdf") } }, Modifier.weight(1f), nav = true) { Text("PDF") }
                            GlassButton(onClick = { act { Exporter.open(ctx, csv(data), "text/csv") } }, Modifier.weight(1f), nav = true) { Text("CSV / Excel") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassTonalButton(onClick = { act { Exporter.shareFile(ctx, pdf(data), "application/pdf") } }, Modifier.weight(1f), nav = true) { Text("مشاركة PDF") }
                            GlassTonalButton(onClick = { act { Exporter.shareFile(ctx, csv(data), "text/csv") } }, Modifier.weight(1f), nav = true) { Text("مشاركة CSV") }
                        }
                        Spacer(Modifier.height(8.dp))
                        GlassOutlinedButton(onClick = { act { Exporter.print(ctx, pdf(data), data.file) } }, Modifier.fillMaxWidth(), nav = true) {
                            Icon(Icons.Filled.Print, null)
                            Spacer(Modifier.width(6.dp))
                            Text("طباعة")
                        }
                    }
                }
                item { Note("المشاركة تتم عبر نافذة النظام: بلوتوث، واي فاي مباشر، بريد أو تطبيقات المراسلة. ملف CSV يفتحه Excel مباشرة.") }
            }
        }
    }
}
