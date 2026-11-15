package com.mawaqeet.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mawaqeet.app.astro.CalendarKind
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale

/** «التواريخ»: كل ما يخص يوماً واحداً في التقاويم الأربعة، مع الأيام اليوليانية وترتيب اليوم والأسبوع */
@Composable
fun DatesScreen() {
    val env = LocalEnv.current
    val s = env.settings
    var epoch by rememberSaveable { mutableStateOf(env.today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(epoch)
    val isToday = date == env.today

    // اللحظة: الآن إن كان اليوم الحالي، وإلا الظهر المحلي لذلك اليوم
    val moment = if (isToday) env.now else env.dayStart(date) + 12 * 3_600_000L
    val jd = jdOf(moment)
    val hijri = Calendars.toHijri(date, s.hijriMethod, s.hijriAdjust)
    val solar = Calendars.toSolarHijri(date)
    val julian = Calendars.toJulian(date)
    val hijriYearStart = Calendars.fromHijri(com.mawaqeet.app.astro.CDate(hijri.year, 1, 1), s.hijriMethod, s.hijriAdjust)
    val hijriYearEnd = Calendars.fromHijri(com.mawaqeet.app.astro.CDate(hijri.year + 1, 1, 1), s.hijriMethod, s.hijriAdjust)
    val hijriDays = ChronoUnit.DAYS.between(hijriYearStart, hijriYearEnd)
    val solarStart = Calendars.fromSolarHijri(com.mawaqeet.app.astro.CDate(solar.year, 1, 1))
    val yearLen = date.lengthOfYear()
    val fromToday = ChronoUnit.DAYS.between(env.today, date)

    fun f(v: Double, d: Int) = String.format(Locale.US, "%.${d}f", v)
    val rows = listOf(
        "يوم الأسبوع" to Calendars.weekDay(date),
        "ميلادي" to "${date.dayOfMonth} ${Calendars.gregorianMonths[date.monthValue - 1]} / ${Calendars.maghrebMonths[date.monthValue - 1]} / ${Calendars.syriacMonths[date.monthValue - 1]} ${date.year}",
        "هجري قمري" to Calendars.format(hijri, CalendarKind.HIJRI),
        "هجري شمسي" to Calendars.format(solar, CalendarKind.SOLAR_HIJRI, s.solarNames),
        "يولياني (التقويم الشرقي)" to Calendars.format(julian, CalendarKind.JULIAN, maghreb = s.maghrebMonths),
        "اليوم اليولياني" to f(jd, 8),
        "اليوم اليولياني المعدّل" to f(jd - 2400000.5, 5)
    )

    ScreenScaffold(
        title = "التواريخ", onBack = env.back, subtitle = "اليوم في كل التقاويم",
        actions = { ShareAction { (listOf("التواريخ — ${env.place.name}") + rows.map { "${it.first}: ${it.second}" }).joinToString("\n") } }
    ) {
        ScreenList {
            item { DayStepper(date, { epoch = it.toEpochDay() }) }
            item {
                GlassCard(title = Calendars.weekDay(date), subtitle = if (isToday) "اليوم" else if (fromToday > 0) "بعد $fromToday يوماً" else "قبل ${-fromToday} يوماً") {
                    rows.drop(1).forEach { (k, v) -> InfoRow(k, v, valueColor = if (k == "هجري قمري") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified) }
                }
            }
            item {
                GlassCard(title = "ترتيب اليوم") {
                    InfoRow("يوم السنة الميلادية", "${date.dayOfYear} من $yearLen")
                    InfoRow("المتبقي من السنة", "${yearLen - date.dayOfYear} يوماً")
                    InfoRow("رقم الأسبوع (ISO)", "${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}")
                    InfoRow("الربع", "${(date.monthValue - 1) / 3 + 1}")
                    InfoRow("يوم السنة الهجرية", "${ChronoUnit.DAYS.between(hijriYearStart, date) + 1} من $hijriDays")
                    InfoRow("يوم السنة الهجرية الشمسية", "${ChronoUnit.DAYS.between(solarStart, date) + 1}")
                    InfoRow("أيام الشهر الهجري", "${Calendars.hijriMonthLength(hijri.year, hijri.month, s.hijriMethod)}")
                }
            }
            item {
                GlassCard(title = "السنوات") {
                    InfoRow("السنة الميلادية", if (date.isLeapYear) "كبيسة (366 يوماً)" else "بسيطة (365 يوماً)")
                    InfoRow("السنة الهجرية", if (hijriDays >= 355) "كبيسة ($hijriDays يوماً)" else "بسيطة ($hijriDays يوماً)")
                    InfoRow("السنة الهجرية الشمسية", if (Calendars.isSolarLeap(solar.year)) "كبيسة (366 يوماً)" else "بسيطة (365 يوماً)")
                }
            }
            item {
                GlassCard {
                    androidx.compose.foundation.layout.Row {
                        GlassTonalButton(onClick = { env.navigate(Routes.AGE) }) { Text("حساب العمر") }
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
                        GlassTonalButton(onClick = { env.navigate(Routes.CONVERTER) }) { Text("تحويل تاريخ") }
                    }
                }
            }
            item {
                Note(
                    if (isToday) "اليوم اليولياني محسوب للحظة الحالية بالتوقيت العالمي، ويبدأ عند منتصف النهار."
                    else "اليوم اليولياني محسوب لظهر هذا اليوم بالتوقيت المحلي."
                )
            }
        }
    }
}
