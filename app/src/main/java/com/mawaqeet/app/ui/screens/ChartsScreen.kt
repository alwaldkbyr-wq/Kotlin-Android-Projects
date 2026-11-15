package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.PrayerCalc
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.cosd
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

private class ChartLine(val name: String, val color: Color, val xs: DoubleArray, val ys: DoubleArray)

private class ChartYearData(
    val dayLen: DoubleArray, val sunrise: DoubleArray, val sunset: DoubleArray,
    val fajr: DoubleArray, val isha: DoubleArray, val monthStarts: List<Int>
)

private class ChartDayData(val sun: DoubleArray, val moon: DoubleArray, val spanHours: Double)

private fun chartRange(lo: Double, hi: Double, candidates: List<Double>, maxTicks: Int = 6): Triple<Double, Double, Double> {
    var a = lo
    var b = hi
    if (!(b - a > 1e-9)) { a -= 1.0; b += 1.0 }
    for (st in candidates) {
        val fa = floor(a / st)
        val fb = ceil(b / st)
        if (fb - fa <= maxTicks) return Triple(fa * st, fb * st, st)
    }
    val st = candidates.last()
    return Triple(floor(a / st) * st, ceil(b / st) * st, st)
}

private fun chartMinMax(vararg arrs: DoubleArray): Pair<Double, Double> {
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (arr in arrs) for (v in arr) if (!v.isNaN()) { if (v < lo) lo = v; if (v > hi) hi = v }
    return if (lo > hi) (0.0 to 1.0) else (lo to hi)
}

private fun chartHourLabel(v: Double): String = "%02d:00".format(Locale.US, ((Math.round(v).toInt() % 24) + 24) % 24)

@Composable
private fun ChartsLegend(items: List<Pair<String, Color>>) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (name, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text(name, color = o.ink, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** رسم خطي بسيط: محاور وشبكة وتسميات وخطوط وعلامة عمودية اختيارية */
@Composable
private fun ChartsLineChart(
    lines: List<ChartLine>,
    xMin: Double, xMax: Double, yMin: Double, yMax: Double, yStep: Double,
    xTicks: List<Pair<Double, String>>,
    yLabel: (Double) -> String,
    marker: Double? = null,
    hLine: Double? = null,
    height: Dp = 220.dp
) {
    val o = AppTheme.ornament
    val grid = o.ink.copy(alpha = 0.13f)
    val labelColor = o.muted.toArgb()
    val markColor = o.ink
    val zeroColor = o.ink.copy(alpha = 0.55f)
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val padL = 42.dp.toPx()
        val padR = 10.dp.toPx()
        val padT = 8.dp.toPx()
        val padB = 24.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val xr = if (xMax - xMin > 1e-9) xMax - xMin else 1.0
        val yr = if (yMax - yMin > 1e-9) yMax - yMin else 1.0
        fun px(x: Double): Float = padL + (((x - xMin) / xr).toFloat()) * w
        fun py(y: Double): Float = padT + (1f - ((y - yMin) / yr).toFloat()) * h

        val paint = android.graphics.Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text;
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = labelColor
        }
        // الشبكة الأفقية وتسميات المحور الصادي
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        var v = yMin
        var guard = 0
        while (v <= yMax + yStep * 0.001 && guard < 40) {
            val y = py(v)
            drawLine(grid, Offset(padL, y), Offset(padL + w, y), 1f)
            drawIntoCanvas { c -> c.nativeCanvas.drawText(yLabel(v), padL - 6.dp.toPx(), y + paint.textSize / 3f, paint) }
            v += yStep
            guard++
        }
        // الشبكة العمودية وتسميات المحور السيني
        paint.textAlign = android.graphics.Paint.Align.CENTER
        xTicks.forEach { (x, label) ->
            val xx = px(x)
            drawLine(grid, Offset(xx, padT), Offset(xx, padT + h), 1f)
            drawIntoCanvas { c -> c.nativeCanvas.drawText(label, xx, padT + h + 16.dp.toPx(), paint) }
        }
        if (hLine != null && hLine >= yMin && hLine <= yMax) {
            drawLine(zeroColor, Offset(padL, py(hLine)), Offset(padL + w, py(hLine)), 1.5.dp.toPx())
        }
        lines.forEach { ln ->
            val path = Path()
            var pen = false
            for (i in ln.xs.indices) {
                val y = ln.ys[i]
                if (y.isNaN()) { pen = false; continue }
                val x = px(ln.xs[i])
                val yy = py(y)
                if (!pen) { path.moveTo(x, yy); pen = true } else path.lineTo(x, yy)
            }
            drawPath(path, ln.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (marker != null && marker >= xMin && marker <= xMax) {
            val xx = px(marker)
            drawLine(
                markColor, Offset(xx, padT), Offset(xx, padT + h), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))
            )
        }
    }
}

/** الرسوم البيانية: السنة واليوم والقمر */
@Composable
fun ChartsScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    var year by remember { mutableStateOf(env.today.year) }
    val s = env.settings

    val yd: ChartYearData? = rememberComputed(year, s) {
        val first = LocalDate.of(year, 1, 1)
        val n = first.lengthOfYear()
        val dayLen = DoubleArray(n); val sr = DoubleArray(n); val ss = DoubleArray(n)
        val fj = DoubleArray(n); val ih = DoubleArray(n)
        val months = ArrayList<Int>()
        for (i in 0 until n) {
            val d = first.plusDays(i.toLong())
            if (d.dayOfMonth == 1) months += i
            val ds = env.dayStart(d)
            fun hr(t: Long?): Double {
                if (t == null) return Double.NaN
                val ldt = env.dateTimeOf(t)
                val extra = when {
                    ldt.toLocalDate().isAfter(d) -> 24.0
                    ldt.toLocalDate().isBefore(d) -> -24.0
                    else -> 0.0
                }
                return ldt.hour + ldt.minute / 60.0 + extra
            }
            val sun = RiseSetCalc.compute(Body.SUN, ds, env.lat, env.lon, heightM = env.place.heightM)
            val rise = sun.rise
            val set = sun.set
            dayLen[i] = when {
                sun.alwaysUp -> 24.0
                sun.alwaysDown -> 0.0
                rise != null && set != null -> ((set - rise + DAY_MS) % DAY_MS) / HOUR_MS.toDouble()
                else -> Double.NaN
            }
            sr[i] = hr(rise); ss[i] = hr(set)
            val p = PrayerCalc.compute(ds, env.lat, env.lon, s.prayerSettings)
            fj[i] = hr(p[Prayer.FAJR]); ih[i] = hr(p[Prayer.ISHA])
        }
        ChartYearData(dayLen, sr, ss, fj, ih, months)
    }

    val dd: ChartDayData? = rememberComputed(env.today, env.lat, env.lon, env.zone) {
        val start = env.dayStart(env.today)
        val end = env.dayStart(env.today.plusDays(1))
        val n = ((end - start) / (10 * MIN_MS)).toInt() + 1
        val sun = DoubleArray(n); val moon = DoubleArray(n)
        for (i in 0 until n) {
            val t = start + i * 10 * MIN_MS
            sun[i] = Ephemeris.horizontal(Body.SUN, t, env.lat, env.lon).alt
            moon[i] = Ephemeris.horizontal(Body.MOON, t, env.lat, env.lon).alt
        }
        ChartDayData(sun, moon, (end - start) / HOUR_MS.toDouble())
    }

    val illum: DoubleArray? = rememberComputed(env.today) {
        val start = env.dayStart(env.today)
        DoubleArray(241) { i -> (1 - cosd(Phases.lonDiff(start + i * 6 * HOUR_MS))) / 2 * 100.0 }
    }

    ScreenScaffold(title = "الرسوم البيانية", onBack = env.back, subtitle = env.place.name) {
        ScreenList {
            // السنة: طول النهار
            item {
                GlassCard(title = "طول النهار خلال السنة", subtitle = "بالساعات") {
                    ChartsYearStepper(year) { year = it }
                    if (yd == null) Busy() else {
                        val n = yd.dayLen.size
                        val (lo, hi) = chartMinMax(yd.dayLen)
                        val (y0, y1, st) = chartRange(lo, hi, listOf(0.5, 1.0, 2.0, 3.0, 6.0))
                        val xs = DoubleArray(n) { it.toDouble() }
                        val ticks = yd.monthStarts.mapIndexed { k, i -> i.toDouble() to "${k + 1}" }
                        ChartsLineChart(
                            listOf(ChartLine("طول النهار", primary, xs, yd.dayLen)),
                            0.0, (n - 1).toDouble(), y0, y1, st, ticks,
                            { "%.1f".format(Locale.US, it) },
                            marker = if (year == env.today.year) (env.today.dayOfYear - 1).toDouble() else null
                        )
                        Text("المحور الأفقي: أشهر السنة الميلادية (1–12)", color = o.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // السنة: الشروق والغروب والفجر والعشاء
            item {
                GlassCard(title = "الشروق والغروب والفجر والعشاء", subtitle = "التوقيت المحلي خلال السنة") {
                    ChartsYearStepper(year) { year = it }
                    if (yd == null) Busy() else {
                        val n = yd.dayLen.size
                        val xs = DoubleArray(n) { it.toDouble() }
                        val (lo, hi) = chartMinMax(yd.sunrise, yd.sunset, yd.fajr, yd.isha)
                        val (y0, y1, st) = chartRange(lo, hi, listOf(1.0, 2.0, 3.0, 4.0, 6.0, 12.0), 8)
                        val cFajr = o.info
                        val cRise = o.warning
                        val cSet = o.danger
                        val cIsha = primary
                        val ticks = yd.monthStarts.mapIndexed { k, i -> i.toDouble() to "${k + 1}" }
                        ChartsLineChart(
                            listOf(
                                ChartLine("الفجر", cFajr, xs, yd.fajr), ChartLine("الشروق", cRise, xs, yd.sunrise),
                                ChartLine("الغروب", cSet, xs, yd.sunset), ChartLine("العشاء", cIsha, xs, yd.isha)
                            ),
                            0.0, (n - 1).toDouble(), y0, y1, st, ticks, { chartHourLabel(it) },
                            marker = if (year == env.today.year) (env.today.dayOfYear - 1).toDouble() else null,
                            height = 250.dp
                        )
                        ChartsLegend(listOf("الفجر" to cFajr, "الشروق" to cRise, "الغروب" to cSet, "العشاء" to cIsha))
                    }
                }
            }

            // اليوم: ارتفاع الشمس والقمر
            item {
                GlassCard(title = "ارتفاع الشمس والقمر اليوم", subtitle = "بالدرجات، كل 10 دقائق") {
                    if (dd == null) Busy() else {
                        val n = dd.sun.size
                        val xs = DoubleArray(n) { it * 10.0 / 60.0 }
                        val (lo, hi) = chartMinMax(dd.sun, dd.moon)
                        val (y0, y1, st) = chartRange(Math.min(lo, 0.0), Math.max(hi, 0.0), listOf(5.0, 10.0, 15.0, 20.0, 30.0))
                        val cSun = o.warning
                        val cMoon = o.info
                        val ticks = (0..24 step 3).filter { it <= dd.spanHours + 0.01 }.map { it.toDouble() to "%02d".format(Locale.US, it % 24) }
                        val nowH = (env.now - env.dayStart(env.today)) / HOUR_MS.toDouble()
                        ChartsLineChart(
                            listOf(ChartLine("الشمس", cSun, xs, dd.sun), ChartLine("القمر", cMoon, xs, dd.moon)),
                            0.0, dd.spanHours, y0, y1, st, ticks, { "%.0f°".format(Locale.US, it) },
                            marker = nowH, hLine = 0.0
                        )
                        ChartsLegend(listOf("الشمس" to cSun, "القمر" to cMoon, "الوقت الحالي (خط متقطع)" to o.ink))
                    }
                }
            }

            // إضاءة القمر 60 يوماً
            item {
                GlassCard(title = "إضاءة القمر خلال 60 يوماً", subtitle = "النسبة المئوية للجزء المضاء") {
                    if (illum == null) Busy() else {
                        val xs = DoubleArray(illum.size) { it * 0.25 }
                        val base = env.dayStart(env.today)
                        val ticks = (0..60 step 10).map { dd2 ->
                            val d = env.dateOf(base + dd2 * DAY_MS + 3 * HOUR_MS)
                            dd2.toDouble() to "${d.dayOfMonth}/${d.monthValue}"
                        }
                        ChartsLineChart(
                            listOf(ChartLine("الإضاءة", primary, xs, illum)),
                            0.0, 60.0, 0.0, 100.0, 25.0, ticks, { "%.0f%%".format(Locale.US, it) },
                            marker = 0.0 + (env.now - base) / DAY_MS.toDouble()
                        )
                        Text("الأعلى = بدر، الأدنى = محاق", color = o.muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                    }
                }
            }
            item { Note("كل الرسوم محسوبة فلكياً لموقعك الحالي وإعدادات الصلاة المختارة. المحور الأفقي من اليسار إلى اليمين.") }
        }
    }
}

@Composable
private fun ChartsYearStepper(year: Int, onChange: (Int) -> Unit) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(year - 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "السنة السابقة", tint = o.ink) }
        Text("$year", Modifier.weight(1f), textAlign = TextAlign.Center, color = o.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(year + 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "السنة التالية", tint = o.ink) }
    }
}
