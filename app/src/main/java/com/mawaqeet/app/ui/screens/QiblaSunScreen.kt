package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.Qibla
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.Sun
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.norm180
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.CanvasFonts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/* ───────────── حسابات خالصة ───────────── */

private data class QsMoment(val time: Long, val alt: Double, val az: Double)

/** لحظات اليوم التي يساوي فيها سمت الشمس [targetAz] والشمس فوق الأفق، بدقة أقل من دقيقة */
private fun qsDayMoments(startMs: Long, endMs: Long, lat: Double, lon: Double, heightM: Double, targetAz: Double): List<QsMoment> {
    val out = ArrayList<QsMoment>()
    var t = startMs
    var prev = Ephemeris.horizontal(Body.SUN, t, lat, lon, true, heightM)
    while (t < endMs) {
        val n = t + MIN_MS
        val cur = Ephemeris.horizontal(Body.SUN, n, lat, lon, true, heightM)
        val gp = norm180(prev.az - targetAz)
        val gc = norm180(cur.az - targetAz)
        if (prev.alt > 0 && cur.alt > 0 && abs(gp) < 90 && abs(gc) < 90 && ((gp <= 0 && gc > 0) || (gp >= 0 && gc < 0))) {
            val f = if (gc == gp) 0.0 else -gp / (gc - gp)
            val tr = t + (f * MIN_MS).toLong()
            val h = Ephemeris.horizontal(Body.SUN, tr, lat, lon, true, heightM)
            if (out.isEmpty() || tr - out.last().time > 5 * MIN_MS) out += QsMoment(tr, h.alt, h.az)
        }
        prev = cur; t = n
    }
    return out
}

private data class QsOverhead(val time: Long, val zenithDistDeg: Double, val antipode: Boolean)

private fun qsSunDec(t: Long): Double = Sun.equatorial(jdeOf(jdOf(t))).dec

/** أوقات مرور الشمس بسمت نقطة (lat, lon) في سنة معيّنة: يوم يساوي فيه ميل الشمس عرض النقطة، ثم وقت العبور في ذلك اليوم */
private fun qsOverheadTimes(year: Int, lat: Double, lon: Double, antipode: Boolean): List<QsOverhead> {
    val out = ArrayList<QsOverhead>()
    val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + DAY_MS / 2
    val days = LocalDate.of(year, 1, 1).lengthOfYear()
    // منطقة تقريبية بخط طول النقطة لتحديد «اليوم المحلي» الذي يقع فيه العبور
    val zone: ZoneId = ZoneOffset.ofHours(Math.round(lon / 15.0).toInt().coerceIn(-12, 14))
    for (i in 0 until days) {
        val a = start + i * DAY_MS
        val b = a + DAY_MS
        val fa = qsSunDec(a) - lat
        val fb = qsSunDec(b) - lat
        if ((fa < 0) == (fb < 0)) continue
        var lo = a; var hi = b
        repeat(32) {
            val mid = (lo + hi) / 2
            if ((qsSunDec(mid) - lat < 0) == (fa < 0)) lo = mid else hi = mid
        }
        val t0 = (lo + hi) / 2
        val d0 = Instant.ofEpochMilli(t0).atZone(zone).toLocalDate()
        var best: Long? = null
        for (k in -1..1) {
            val ds = d0.plusDays(k.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val tr = RiseSetCalc.compute(Body.SUN, ds, lat, lon).transit ?: continue
            if (best == null || abs(tr - t0) < abs(best - t0)) best = tr
        }
        val tb = best ?: continue
        val alt = Ephemeris.horizontal(Body.SUN, tb, lat, lon, false).alt
        out += QsOverhead(tb, 90.0 - alt, antipode)
    }
    return out
}

private class QsData(val toSun: List<QsMoment>, val shadowToQibla: List<QsMoment>, val overhead: List<QsOverhead>)

private fun qsLatLonText(v: Double) = String.format(Locale.US, "%.1f", v)

/* ───────────── الشاشة ───────────── */

@Composable
fun QiblaSunScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val h24 = env.settings.h24
    val riyadh = remember { ZoneId.of("Asia/Riyadh") }
    var date by remember { mutableStateOf(env.today) }
    val q = Qibla.bearing(env.lat, env.lon)
    val place = env.place
    val year = env.today.year

    val data = rememberComputed(date, place, year) {
        val s = env.dayStart(date)
        val e = env.dayStart(date.plusDays(1))
        val a = qsDayMoments(s, e, place.lat, place.lon, place.heightM, q)
        val b = qsDayMoments(s, e, place.lat, place.lon, place.heightM, norm360(q + 180.0))
        val ov = (qsOverheadTimes(year, Qibla.KAABA_LAT, Qibla.KAABA_LON, false) + qsOverheadTimes(year + 1, Qibla.KAABA_LAT, Qibla.KAABA_LON, false) +
            qsOverheadTimes(year, -Qibla.KAABA_LAT, Qibla.KAABA_LON - 180.0, true) + qsOverheadTimes(year + 1, -Qibla.KAABA_LAT, Qibla.KAABA_LON - 180.0, true))
            .sortedBy { it.time }
        QsData(a, b, ov)
    }

    // الشمس الآن
    val live = Ephemeris.horizontal(Body.SUN, env.now, env.lat, env.lon, true, place.heightM)
    val diff = norm180(q - live.az)
    val liveText = when {
        live.alt <= 0 -> "الشمس تحت الأفق الآن؛ استعمل لحظات اليوم أدناه أو رصد الشمس فوق الكعبة."
        abs(diff) < 1.0 -> "الشمس الآن في اتجاه القبلة تقريباً: استقبلها."
        diff > 0 -> "استقبل الشمس ثم استدر ${Fmt.num(abs(diff), 0)}° إلى اليمين"
        else -> "استقبل الشمس ثم استدر ${Fmt.num(abs(diff), 0)}° إلى اليسار"
    }

    fun momentsText(l: List<QsMoment>) = if (l.isEmpty()) "لا يوجد اليوم" else l.joinToString("، ") { env.time(it.time) }
    fun overheadLine(x: QsOverhead): String {
        val kind = if (x.antipode) "الشمس فوق النقطة المقابلة للكعبة" else "الشمس فوق الكعبة"
        return "$kind: ${env.date(x.time)} ${env.time(x.time)} (بتوقيتك) • ${Fmt.gregorian(Instant.ofEpochMilli(x.time).atZone(riyadh).toLocalDate(), env.settings.maghrebMonths)} ${Fmt.time(x.time, riyadh, h24)} (بتوقيت مكة)"
    }

    ScreenScaffold(
        title = "القبلة بالشمس والظل", onBack = env.back, subtitle = "حدّد القبلة دون بوصلة",
        actions = {
            ShareAction {
                buildString {
                    appendLine("القبلة بالشمس والظل — ${place.name}")
                    appendLine("اتجاه القبلة: ${Fmt.deg(q)} من الشمال الحقيقي")
                    data?.let { d ->
                        appendLine("${env.date(env.dayStart(date))}")
                        appendLine("الشمس في اتجاه القبلة: ${momentsText(d.toSun)}")
                        appendLine("ظل عمود رأسي نحو القبلة (الشمس عكس القبلة): ${momentsText(d.shadowToQibla)}")
                        d.overhead.forEach { appendLine(overheadLine(it)) }
                    }
                }
            }
        }
    ) {
        ScreenList {
            item {
                GlassCard(title = "الشمس الآن", subtitle = "${place.name} • القبلة ${Fmt.deg(q)} (${Fmt.direction(q)})") {
                    QsDial(
                        q, live.az, live.alt, o.ink, o.muted, o.gold, primary,
                        Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(liveText, color = primary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("سمت الشمس", "${Fmt.deg(live.az)} (${Fmt.direction(live.az)})")
                    InfoRow("ارتفاع الشمس", Fmt.deg(live.alt))
                    InfoRow("اتجاه ظل عمود رأسي", if (live.alt > 0) Fmt.deg(norm360(live.az + 180.0)) else "--")
                    InfoRow("الزاوية بين الشمس والقبلة", Fmt.deg(abs(diff)))
                }
            }
            item { DayStepper(date, { date = it }) }
            if (data == null) item { Busy() } else {
                item { SectionTitle("لحظات اليوم") }
                item { QsMomentsCard("الشمس في اتجاه القبلة", "استقبل الشمس مباشرة تكن مستقبلاً القبلة؛ ويكون ظلك خلفك.", data.toSun, { env.time(it) }) }
                item { QsMomentsCard("القبلة عكس الشمس (على امتداد الظل)", "يشير ظل عمود رأسي إلى القبلة؛ قف وظهرك إلى الشمس. (هي نفسها لحظة «ظل العمود نحو القبلة».)", data.shadowToQibla, { env.time(it) }) }
                item { Note("تُحسب اللحظات دقيقة بدقيقة وفق ارتفاع الشمس الظاهري فوق الأفق، وتُفضّل حين يكون ارتفاعها بين 10° و75° لوضوح الظل. حدّد الاتجاه بوتد أو عمود رأسي على أرض مستوية.") }

                item { SectionTitle("رصد الشمس فوق الكعبة") }
                item {
                    GlassCard(title = "تعامد الشمس على مكة", subtitle = "في $year و${year + 1}") {
                        Text(
                            "في لحظة تعامد الشمس على الكعبة (خط العرض ${qsLatLonText(Qibla.KAABA_LAT)}° شمالاً) يتجه ظل أي جسم رأسي بعيداً عن القبلة، فتكون القبلة نحو الشمس. " +
                                "وفي لحظة تعامدها على النقطة المقابلة (${qsLatLonText(-Qibla.KAABA_LAT)}°، ${qsLatLonText(Qibla.KAABA_LON - 180.0)}°) يشير الظل نحو القبلة؛ ويصحّ ذلك حيث تكون الشمس فوق الأفق في تلك اللحظة.",
                            color = o.muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                data.overhead.forEach { x ->
                    item {
                        val hz = Ephemeris.horizontal(Body.SUN, x.time, env.lat, env.lon, true, place.heightM)
                        val up = hz.alt > 0
                        val past = x.time < env.now
                        GlassCard(
                            title = if (x.antipode) "الشمس فوق النقطة المقابلة للكعبة" else "الشمس فوق الكعبة",
                            trailing = {
                                if (past) Pill("مضى", o.muted)
                                else Pill("بعد ${Fmt.daysUntil(env.now, x.time)} يوماً", o.success)
                            }
                        ) {
                            val rd = Instant.ofEpochMilli(x.time).atZone(riyadh)
                            InfoRow("بتوقيت موقعك", "${env.date(x.time)} • ${env.time(x.time)}", emphasize = true)
                            InfoRow("بتوقيت مكة (Asia/Riyadh)", "${Fmt.gregorian(rd.toLocalDate(), env.settings.maghrebMonths)} • ${Fmt.time(x.time, riyadh, h24)}")
                            InfoRow("الشمس عن السمت (للتحقق)", Fmt.deg(x.zenithDistDeg, 2))
                            InfoRow("الشمس في موقعك لحظتها", if (up) "فوق الأفق: ارتفاع ${Fmt.deg(hz.alt)}، سمت ${Fmt.deg(hz.az)}" else "تحت الأفق", valueColor = if (up) o.success else o.danger)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (!up) "لا يصلح هذا الرصد في موقعك لأن الشمس تحت الأفق في تلك اللحظة."
                                else if (x.antipode) "اجعل ظهرك إلى الشمس: ظل أي عمود رأسي يشير نحو القبلة."
                                else "استقبل الشمس: ظل أي عمود رأسي يشير بعيداً عن القبلة، والقبلة نحو الشمس.",
                                color = if (up) o.ink else o.muted, style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item { Note("الأوقات محسوبة فلكياً وقد تختلف عن الجداول المنشورة بدقيقة أو أقل. تُستعمل الأرصاد على بُعد آلاف الكيلومترات من مكة بشرط أن تكون الشمس مرئية.") }
            }
        }
    }
}

@Composable
private fun QsMomentsCard(title: String, hint: String, list: List<QsMoment>, time: (Long?) -> String) {
    val o = AppTheme.ornament
    GlassCard(title = title) {
        if (list.isEmpty()) {
            Text("لا يوجد اليوم", color = o.muted, style = MaterialTheme.typography.titleMedium)
            Text("لا تمرّ الشمس بهذا الاتجاه فوق الأفق في هذا اليوم.", color = o.muted, style = MaterialTheme.typography.bodySmall)
        } else {
            list.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(time(m.time), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                    Text("ارتفاع الشمس ${Fmt.deg(m.alt, 0)}", color = o.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(hint, color = o.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** قرص الاتجاهات: الشمال أعلى، خط القبلة، اتجاه الشمس واتجاه الظل */
@Composable
private fun QsDial(q: Double, sunAz: Double, sunAlt: Double, ink: Color, muted: Color, gold: Color, primary: Color, modifier: Modifier) {
    val sunColor = Color(0xFFFFC83D)
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f * 0.86f
        fun pt(az: Double, rad: Float): Offset {
            val a = Math.toRadians(az)
            return Offset(c.x + rad * sin(a).toFloat(), c.y - rad * cos(a).toFloat())
        }
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
            typeface = CanvasFonts.text; textSize = 13.sp.toPx()
        }
        fun label(t: String, p: Offset, color: Color) {
            paint.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(t, p.x, p.y + paint.textSize / 3f, paint)
        }
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.12f), Color.Transparent), c, r), r, c)
        drawCircle(muted.copy(alpha = 0.6f), r, c, style = Stroke(1.5.dp.toPx()))
        drawCircle(muted.copy(alpha = 0.25f), r * 0.5f, c, style = Stroke(1.dp.toPx()))
        for (d in 0 until 360 step 10) {
            val major = d % 90 == 0
            val len = if (major) 0.09f else if (d % 30 == 0) 0.055f else 0.03f
            drawLine(muted.copy(alpha = if (major) 0.9f else 0.5f), pt(d.toDouble(), r), pt(d.toDouble(), r * (1f - len)), 1.2.dp.toPx())
        }
        val lr = r * 1.0f + 14.dp.toPx()
        label("شمال", pt(0.0, lr), ink); label("شرق", pt(90.0, lr), muted)
        label("جنوب", pt(180.0, lr), muted); label("غرب", pt(270.0, lr), muted)

        // خط القبلة
        drawLine(gold, c, pt(q, r), 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(gold, 7.dp.toPx(), pt(q, r))
        label("القبلة", pt(q, r * 0.72f).let { Offset(it.x, it.y) }, gold)

        if (sunAlt > 0) {
            // الظل: عكس الشمس؛ يقصر كلما ارتفعت الشمس
            val len = (0.22f + 0.6f * (1f - (sunAlt / 90.0).toFloat())).coerceIn(0.2f, 0.85f)
            val shadowEnd = pt(sunAz + 180.0, r * len)
            drawLine(ink.copy(alpha = 0.85f), c, shadowEnd, 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            drawCircle(ink, 4.dp.toPx(), shadowEnd)
            // الشمس
            val sp = pt(sunAz, r * 0.8f)
            drawCircle(Brush.radialGradient(listOf(sunColor.copy(alpha = 0.7f), Color.Transparent), sp, 26.dp.toPx()), 26.dp.toPx(), sp)
            drawCircle(sunColor, 9.dp.toPx(), sp)
            drawLine(sunColor.copy(alpha = 0.35f), c, sp, 1.dp.toPx())
        } else {
            label("الشمس تحت الأفق", Offset(c.x, c.y + r * 0.25f), muted)
        }
        drawCircle(ink, 5.dp.toPx(), c)
        label("الظل", pt(if (sunAlt > 0) sunAz + 180.0 else 180.0, r * 0.95f).let { if (sunAlt > 0) it else Offset(-1000f, -1000f) }, ink)
    }
}
