package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DayNight
import com.mawaqeet.app.astro.DayNightCalc
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.RiseSet
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.fmtDuration
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.TableRowCells
import com.mawaqeet.app.ui.theme.AppTheme

private val sunMoonBandColors = listOf(
    Color(0xFF0B1B33), // ليل
    Color(0xFF1E3A66), // شفق فلكي
    Color(0xFF3F6BA8), // شفق بحري
    Color(0xFFE8895A), // شفق مدني
    Color(0xFFFFD86B)  // نهار
)
private val sunMoonBandNames = listOf("ليل", "شفق فلكي", "شفق بحري", "شفق مدني", "نهار")

private fun sunMoonBand(alt: Double): Int = when {
    alt > -0.8333 -> 4
    alt > -6.0 -> 3
    alt > -12.0 -> 2
    alt > -18.0 -> 1
    else -> 0
}

private fun sunMoonRiseText(t: Long?, none: String, env: com.mawaqeet.app.data.Env): String = if (t == null) none else env.time(t)

/** الشمس والقمر: الشروق والغروب والشفق والموضع الحالي */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SunMoonScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    var date by remember { mutableStateOf(env.today) }
    val isToday = date == env.today
    val start = env.dayStart(date)
    val end = env.dayStart(date.plusDays(1))
    val heightM = env.place.heightM

    val dn: DayNight = remember(date, env.settings) { DayNightCalc.compute(start, env.lat, env.lon, heightM) }
    val moon: RiseSet = remember(date, env.settings) { RiseSetCalc.compute(Body.MOON, start, env.lat, env.lon) }
    // أصناف الضوء كل 5 دقائق لشريط اليوم
    val bands: IntArray = remember(date, env.settings) {
        val n = ((end - start) / (5 * MIN_MS)).toInt()
        IntArray(n) { i -> sunMoonBand(Ephemeris.horizontal(Body.SUN, start + i * 5 * MIN_MS + 150_000L, env.lat, env.lon, withRefraction = false).alt) }
    }

    val refTime = if (isToday) env.now else start + 12 * HOUR_MS
    val sunH = Ephemeris.horizontal(Body.SUN, env.now, env.lat, env.lon)
    val moonH = Ephemeris.horizontal(Body.MOON, refTime, env.lat, env.lon)
    val minuteKey = refTime / 60000L
    val moonState = remember(minuteKey) { Phases.state(refTime) }

    fun sunText(): String = when {
        dn.polarDay -> "الشمس لا تغيب في هذا اليوم"
        dn.polarNight -> "الشمس لا تطلع في هذا اليوم"
        else -> ""
    }
    val noRise = "لا يطلع في هذا اليوم"
    val noSet = "لا يغرب في هذا اليوم"

    fun shareText(): String = buildString {
        appendLine("الشمس والقمر — ${env.place.name}")
        appendLine("${env.gregorian(date)} • ${env.hijri(date)}")
        appendLine("الشروق: ${env.time(dn.sunrise)}   الغروب: ${env.time(dn.sunset)}   الزوال: ${env.time(dn.noon)}")
        appendLine("طول النهار: ${fmtDuration(dn.dayLengthMin)}   طول الليل: ${fmtDuration(dn.nightLengthMin)}")
        appendLine("الفجر الفلكي: ${env.time(dn.astroDawn)}   الشفق المدني: ${env.time(dn.civilDusk)}")
        appendLine("طلوع القمر: ${sunMoonRiseText(moon.rise, noRise, env)}   غروبه: ${sunMoonRiseText(moon.set, noSet, env)}")
        appendLine("إضاءة القمر: ${Fmt.num(moonState.illumination * 100, 0)}%")
    }

    ScreenScaffold(title = "الشمس والقمر", onBack = env.back, subtitle = env.place.name, actions = { ShareAction { shareText() } }) {
        ScreenList {
            item { DayStepper(date, { date = it }) }

            item {
                GlassCard(title = "الشمس ☉", subtitle = sunText().ifEmpty { null }) {
                    MetricRow("الشروق" to env.time(dn.sunrise), "الزوال" to env.time(dn.noon), "الغروب" to env.time(dn.sunset))
                    InfoRow("ارتفاع الشمس عند الزوال", Fmt.deg(dn.noonAlt))
                    InfoRow("طول النهار", fmtDuration(dn.dayLengthMin))
                    InfoRow("طول الليل", fmtDuration(dn.nightLengthMin))
                    if (isToday) {
                        InfoRow("الارتفاع الآن", Fmt.deg(sunH.alt), valueColor = if (sunH.alt > 0) o.success else o.muted)
                        InfoRow("السمت الآن", "${Fmt.deg(sunH.az)}  ${Fmt.direction(sunH.az)}")
                    }
                }
            }

            item {
                GlassCard(title = "الشفق", subtitle = "الشمس تحت الأفق بـ 6° و12° و18°") {
                    TableRowCells(listOf("النوع", "الصباح (بداية)", "المساء (نهاية)"), header = true, weights = listOf(1f, 1.2f, 1.2f))
                    TableRowCells(listOf("مدني −6°", env.time(dn.civilDawn), env.time(dn.civilDusk)), weights = listOf(1f, 1.2f, 1.2f))
                    TableRowCells(listOf("بحري −12°", env.time(dn.nauticalDawn), env.time(dn.nauticalDusk)), weights = listOf(1f, 1.2f, 1.2f))
                    TableRowCells(listOf("فلكي −18°", env.time(dn.astroDawn), env.time(dn.astroDusk)), weights = listOf(1f, 1.2f, 1.2f))
                    Note("«--:--» تعني أن الشفق لا ينتهي أو لا يبدأ في هذا اليوم عند هذا الموقع.")
                }
            }

            item {
                GlassCard(title = "القمر ☾", subtitle = "الطور ${Phases.phaseName(moonState.phaseAngle)}" + if (!isToday) " (عند الظهر)" else "") {
                    MetricRow(
                        "الطلوع" to sunMoonRiseText(moon.rise, "—", env),
                        "العبور" to sunMoonRiseText(moon.transit, "—", env),
                        "الغروب" to sunMoonRiseText(moon.set, "—", env)
                    )
                    if (moon.rise == null) InfoRow("الطلوع", noRise)
                    if (moon.set == null) InfoRow("الغروب", noSet)
                    InfoRow("ارتفاع القمر عند العبور", if (moon.transit != null) Fmt.deg(moon.transitAlt) else "--")
                    InfoRow(if (isToday) "الارتفاع الآن" else "الارتفاع عند الظهر", Fmt.deg(moonH.alt), valueColor = if (moonH.alt > 0) o.success else o.muted)
                    InfoRow(if (isToday) "السمت الآن" else "السمت عند الظهر", "${Fmt.deg(moonH.az)}  ${Fmt.direction(moonH.az)}")
                    InfoRow("المسافة", "${Fmt.num(moonState.distKm, 0)} كم")
                    InfoRow("الإضاءة", "${Fmt.num(moonState.illumination * 100, 1)}%")
                }
            }

            item {
                GlassCard(title = "ساعات النهار والليل", subtitle = "من 00:00 إلى 24:00 بالتوقيت المحلي") {
                    val tickColor = o.muted.toArgb()
                    val tickCompose = o.muted
                    val nowFrac = if (isToday) ((env.now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f) else -1f
                    val markColor = o.ink
                    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                        val barTop = 6.dp.toPx()
                        val barH = 34.dp.toPx()
                        val w = size.width
                        val n = bands.size
                        if (n > 0) {
                            val cw = w / n
                            var i = 0
                            while (i < n) {
                                var j = i
                                while (j + 1 < n && bands[j + 1] == bands[i]) j++
                                drawRect(sunMoonBandColors[bands[i]], Offset(i * cw, barTop), Size((j - i + 1) * cw + 0.8f, barH))
                                i = j + 1
                            }
                        }
                        val spanH = (end - start) / HOUR_MS.toFloat()
                        val paint = android.graphics.Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text;
                            isAntiAlias = true; textSize = 11.sp.toPx(); color = tickColor; textAlign = android.graphics.Paint.Align.CENTER
                        }
                        var h = 0
                        while (h <= 24) {
                            val x = w * (h / spanH.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            drawLine(tickCompose, Offset(x, barTop + barH), Offset(x, barTop + barH + 5.dp.toPx()), 1.5f)
                            drawIntoCanvas { c -> c.nativeCanvas.drawText("%02d".format(java.util.Locale.US, h % 24), x, barTop + barH + 20.dp.toPx(), paint) }
                            h += 3
                        }
                        if (nowFrac >= 0f) {
                            val x = w * nowFrac
                            drawLine(markColor, Offset(x, barTop - 4.dp.toPx()), Offset(x, barTop + barH + 2.dp.toPx()), 3f)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sunMoonBandNames.forEachIndexed { i, nm ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(sunMoonBandColors[i]))
                                Spacer(Modifier.width(5.dp))
                                Text(nm, color = o.ink, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (isToday) Note("الخط الفاصل يمثل الوقت الحالي.")
                }
            }
        }
    }
}
