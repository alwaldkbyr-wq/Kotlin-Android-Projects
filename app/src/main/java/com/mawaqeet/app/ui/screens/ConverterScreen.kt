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
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.CDate
import com.mawaqeet.app.astro.CalendarKind
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SelectField
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** يحوّل الحقول إلى تاريخ ميلادي، أو يعيد رسالة خطأ */
private fun convParse(env: Env, kind: CalendarKind, y: String, m: Int, d: String): Pair<LocalDate?, String?> {
    val yy = y.trim().toIntOrNull() ?: return null to "أدخل سنة صحيحة"
    val dd = d.trim().toIntOrNull() ?: return null to "أدخل يوماً صحيحاً"
    if (yy < 1 || yy > 9999) return null to "السنة خارج المجال (1 - 9999)"
    val s = env.settings
    val len = runCatching { Calendars.monthLength(yy, m, kind, s.hijriMethod) }.getOrNull() ?: return null to "تاريخ غير صالح"
    if (dd < 1 || dd > len) return null to "اليوم يجب أن يكون بين 1 و$len في هذا الشهر"
    val g = runCatching { Calendars.toGregorian(CDate(yy, m, dd), kind, s.hijriMethod, s.hijriAdjust) }.getOrNull()
        ?: return null to "تاريخ غير صالح"
    return g to null
}

private fun convMonths(env: Env, kind: CalendarKind): List<Pair<Int, String>> =
    (1..12).map { it to "$it - ${Calendars.monthName(kind, it, env.settings.solarNames, env.settings.maghrebMonths)}" }

@Composable
fun ConverterScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val s = env.settings
    val kinds = CalendarKind.values()

    var kind by remember { mutableStateOf(CalendarKind.GREGORIAN) }
    val t0 = env.today
    var y by remember { mutableStateOf(t0.year.toString()) }
    var m by remember { mutableStateOf(t0.monthValue) }
    var d by remember { mutableStateOf(t0.dayOfMonth.toString()) }

    fun load(g: LocalDate, k: CalendarKind) {
        val c = Calendars.convert(g, k, s.hijriMethod, s.hijriAdjust)
        y = c.year.toString(); m = c.month; d = c.day.toString()
    }

    val (g, err) = convParse(env, kind, y, m, d)

    fun summary(): String = if (g == null) "" else buildString {
        appendLine("${Calendars.weekDay(g)}")
        kinds.forEach { appendLine("${it.ar}: ${env.inCalendar(g, it)}") }
        append("اليوم اليولياني (JDN): ${Calendars.jdn(g)}")
    }

    ScreenScaffold(
        title = "تحويل التاريخ", onBack = env.back, subtitle = "بين التقاويم الأربعة",
        actions = { ShareAction { summary().ifBlank { "تاريخ غير صالح" } } }
    ) {
        ScreenList {
            item {
                GlassCard(title = "التقويم المصدر") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(CalendarKind.GREGORIAN to "ميلادي", CalendarKind.JULIAN to "يولياني", CalendarKind.HIJRI to "هجري", CalendarKind.SOLAR_HIJRI to "شمسي").forEach { (k, name) ->
                            GlassChip(selected = kind == k, onClick = {
                                val cur = g ?: env.today
                                kind = k
                                load(cur, k)
                            }, label = { Text(name, maxLines = 1) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("اليوم", d, { v -> d = v.filter { it.isDigit() }.take(2) }, Modifier.weight(1f), number = true)
                        Field("السنة", y, { v -> y = v.filter { it.isDigit() }.take(4) }, Modifier.weight(1.3f), number = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    SelectField("الشهر", convMonths(env, kind), m, { m = it })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTonalButton(onClick = { g?.let { load(it.minusDays(1), kind) } }, Modifier.weight(1f), enabled = g != null, nav = true) { Text("اليوم السابق") }
                        GlassTonalButton(onClick = { load(env.today, kind) }, Modifier.weight(1f), nav = true) { Text("اليوم") }
                        GlassTonalButton(onClick = { g?.let { load(it.plusDays(1), kind) } }, Modifier.weight(1f), enabled = g != null, nav = true) { Text("اليوم التالي") }
                    }
                    if (err != null) {
                        Text(err, color = o.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (g != null) {
                item {
                    GlassCard(title = "النتيجة", subtitle = Calendars.weekDay(g)) {
                        kinds.forEach { k ->
                            InfoRow(
                                k.ar, env.inCalendar(g, k),
                                valueColor = if (k == kind) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
                                emphasize = k == kind
                            )
                        }
                    }
                }
                item {
                    GlassCard(title = "تفاصيل") {
                        val diff = ChronoUnit.DAYS.between(env.today, g)
                        InfoRow("اليوم من الأسبوع", Calendars.weekDay(g))
                        InfoRow("اليوم اليولياني (JDN)", Calendars.jdn(g).toString())
                        InfoRow("اليوم من السنة (ميلادي)", g.dayOfYear.toString())
                        InfoRow(
                            "الفرق عن اليوم",
                            when {
                                diff == 0L -> "هو اليوم"
                                diff > 0 -> "بعد $diff يوم"
                                else -> "منذ ${-diff} يوم"
                            }
                        )
                    }
                }
            }
        }
    }
}
