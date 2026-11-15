package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.AgeCalc
import com.mawaqeet.app.astro.CDate
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.HijriMethod
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.OrnateTabs
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SelectField
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate

private fun ageNextHijriBirthday(birth: LocalDate, today: LocalDate, method: HijriMethod, adj: Int): LocalDate? {
    val hb = Calendars.toHijri(birth, method, adj)
    val ht = Calendars.toHijri(today, method, adj)
    for (y in ht.year..ht.year + 2) {
        val len = runCatching { Calendars.hijriMonthLength(y, hb.month, method) }.getOrNull() ?: continue
        val g = runCatching { Calendars.fromHijri(CDate(y, hb.month, minOf(hb.day, len)), method, adj) }.getOrNull() ?: continue
        if (!g.isBefore(today)) return g
    }
    return null
}

@Composable
fun AgeScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val ctx = LocalContext.current
    val s = env.settings
    val today = env.today

    var mode by remember { mutableStateOf(0) }
    var birthG by remember { mutableStateOf<LocalDate?>(null) }
    var hy by remember { mutableStateOf("") }
    var hm by remember { mutableStateOf(1) }
    var hd by remember { mutableStateOf("") }

    var hijriError: String? = null
    val birth: LocalDate? = if (mode == 0) birthG else {
        val yy = hy.toIntOrNull(); val dd = hd.toIntOrNull()
        if (yy == null || dd == null) null
        else {
            val len = runCatching { Calendars.hijriMonthLength(yy, hm, s.hijriMethod) }.getOrNull()
            if (yy < 1 || len == null || dd < 1 || dd > len) { hijriError = "تاريخ هجري غير صالح"; null }
            else runCatching { Calendars.fromHijri(CDate(yy, hm, dd), s.hijriMethod, s.hijriAdjust) }.getOrNull()
        }
    }
    val future = birth != null && birth.isAfter(today)
    val res = if (birth != null && !future) AgeCalc.compute(birth, today, s.hijriMethod, s.hijriAdjust) else null
    val nextHijri = if (birth != null && !future) ageNextHijriBirthday(birth, today, s.hijriMethod, s.hijriAdjust) else null

    fun summary(): String {
        if (birth == null || res == null) return "لم يُحدَّد تاريخ الميلاد"
        return buildString {
            appendLine("تاريخ الميلاد: ${Calendars.weekDay(birth)} ${env.gregorian(birth)} • ${env.hijri(birth)}")
            appendLine("العمر: ${res.years} سنة و${res.months} شهر و${res.days} يوم")
            appendLine("العمر الهجري: ${res.hijriYears} سنة و${res.hijriMonths} شهر و${res.hijriDays} يوم")
            appendLine("مجموع الأيام: ${res.totalDays} (${res.totalWeeks} أسبوع، ${res.totalMonths} شهر)")
            append("بقي على عيد الميلاد: ${res.daysToBirthday} يوم (${res.nextBirthdayWeekDay})")
        }
    }

    fun pick() {
        val init = birthG ?: today.minusYears(20)
        android.app.DatePickerDialog(
            ctx, { _, yy, mm, dd -> birthG = LocalDate.of(yy, mm + 1, dd) },
            init.year, init.monthValue - 1, init.dayOfMonth
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    ScreenScaffold(title = "حساب العمر", onBack = env.back, subtitle = "بالميلادي والهجري", actions = { ShareAction { summary() } }) {
        ScreenList {
            item { OrnateTabs(listOf("تاريخ ميلادي", "تاريخ هجري"), mode) { mode = it } }
            item {
                GlassCard(title = "تاريخ الميلاد") {
                    if (mode == 0) {
                        InfoRow("التاريخ", birthG?.let { env.gregorian(it) } ?: "لم يُحدَّد")
                        Spacer(Modifier.height(6.dp))
                        GlassButton(onClick = { pick() }, Modifier.fillMaxWidth(), nav = true) { Text("اختيار تاريخ الميلاد") }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("اليوم", hd, { v -> hd = v.filter { it.isDigit() }.take(2) }, Modifier.weight(1f), number = true)
                            Field("السنة الهجرية", hy, { v -> hy = v.filter { it.isDigit() }.take(4) }, Modifier.weight(1.4f), number = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectField("الشهر", (1..12).map { it to "$it - ${Calendars.hijriMonths[it - 1]}" }, hm, { hm = it })
                        if (birth != null) InfoRow("يوافق", env.gregorian(birth))
                    }
                    if (hijriError != null && mode == 1) Text(hijriError!!, color = o.danger, modifier = Modifier.padding(top = 6.dp))
                    if (future) Text("تاريخ الميلاد بعد اليوم، اختر تاريخاً سابقاً", color = o.danger, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (birth != null && res != null) {
                item {
                    GlassCard(title = "العمر بالميلادي", subtitle = "وُلد يوم ${res.birthWeekDay}") {
                        MetricRow("سنة" to "${res.years}", "شهر" to "${res.months}", "يوم" to "${res.days}")
                        InfoRow("الميلادي", env.gregorian(birth))
                        InfoRow("الهجري", env.hijri(birth), valueColor = MaterialTheme.colorScheme.primary)
                        InfoRow("الهجري الشمسي", env.solarHijri(birth))
                    }
                }
                item {
                    GlassCard(title = "العمر بالهجري") {
                        MetricRow("سنة" to "${res.hijriYears}", "شهر" to "${res.hijriMonths}", "يوم" to "${res.hijriDays}")
                    }
                }
                item {
                    GlassCard(title = "إجمالي المدة") {
                        InfoRow("عدد الأيام", "${res.totalDays}")
                        InfoRow("عدد الأسابيع", "${res.totalWeeks}")
                        InfoRow("عدد الأشهر", "${res.totalMonths}")
                    }
                }
                item {
                    GlassCard(title = "عيد الميلاد القادم") {
                        InfoRow(
                            "الميلادي",
                            if (res.daysToBirthday == 0L) "اليوم!" else "بعد ${res.daysToBirthday} يوم (${res.nextBirthdayWeekDay})",
                            emphasize = true
                        )
                        InfoRow("التاريخ", env.gregorian(today.plusDays(res.daysToBirthday)))
                        if (nextHijri != null) {
                            val dd = java.time.temporal.ChronoUnit.DAYS.between(today, nextHijri)
                            InfoRow("الهجري", if (dd == 0L) "اليوم!" else "بعد $dd يوم (${Calendars.weekDay(nextHijri)})", emphasize = true)
                            InfoRow("التاريخ", "${env.gregorian(nextHijri)} • ${env.hijri(nextHijri)}")
                        }
                    }
                }
            }
        }
    }
}
