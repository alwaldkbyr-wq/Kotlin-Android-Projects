package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.Mansions
import com.mawaqeet.app.astro.PhaseEvent
import com.mawaqeet.app.astro.PhaseKind
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class MoonPhaseRowData(val kind: PhaseKind, val millis: Long, val hijriLabel: String?)

private fun moonCountdown(ms: Long): String {
    val m = Math.max(0L, ms / 60000L)
    val d = m / 1440
    val h = (m % 1440) / 60
    val mi = m % 60
    return if (d > 0) "$d يوم $h س" else "$h س $mi د"
}

/** يرسم القمر بطوره: الجزء المضاء ثم الحد الفاصل كقطع ناقص */
@Composable
private fun MoonDiscCanvas(phaseAngle: Double, southern: Boolean, modifier: Modifier = Modifier) {
    val dark = Color(0xFF2B3446)
    val lit = Color(0xFFF4ECD2)
    val glow = AppTheme.ornament.gold
    Canvas(modifier) {
        val r = size.minDimension * 0.36f
        val c = Offset(size.width / 2f, size.height / 2f)
        // هالة
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.28f), Color.Transparent), center = c, radius = r * 1.55f),
            radius = r * 1.55f, center = c
        )
        drawCircle(dark, r, c)
        val waxing = phaseAngle < 180.0
        val cosD = cos(phaseAngle * PI / 180.0).toFloat()
        val s = (if (waxing) 1f else -1f) * (if (southern) -1f else 1f)
        val steps = 60
        val path = Path()
        for (i in 0..steps) {
            val phi = (-90.0 + 180.0 * i / steps) * PI / 180.0
            val x = c.x + s * r * cos(phi).toFloat()
            val y = c.y + r * sin(phi).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val phi = (-90.0 + 180.0 * i / steps) * PI / 180.0
            val x = c.x + s * r * cosD * cos(phi).toFloat()
            val y = c.y + r * sin(phi).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        drawPath(path, lit)
        // فوّهات خفيفة
        val craters = listOf(
            Triple(-0.30f, -0.25f, 0.16f), Triple(0.25f, -0.35f, 0.10f), Triple(0.10f, 0.20f, 0.18f),
            Triple(-0.35f, 0.30f, 0.09f), Triple(0.40f, 0.12f, 0.08f), Triple(-0.05f, -0.55f, 0.07f)
        )
        craters.forEach { (dx, dy, rr) ->
            drawCircle(Color.Black.copy(alpha = 0.10f), rr * r, Offset(c.x + dx * r, c.y + dy * r))
        }
        drawCircle(glow.copy(alpha = 0.55f), r, c, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** أطوار القمر: الطور الحالي والأطوار القادمة وجدول سنة */
@Composable
fun MoonScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val now = env.now
    val minuteKey = now / 60000L
    val st = remember(minuteKey) { Phases.state(now) }
    val upcoming = remember(minuteKey / 10) { PhaseKind.values().map { it to Phases.next(it, now) }.sortedBy { it.second } }
    val mansion = Mansions.moonMansion(now)
    val s = env.settings

    val year = rememberComputed2(env.today, s)

    val ageD = st.ageDays.toInt()
    val ageH = ((st.ageDays - ageD) * 24).toInt()
    val distFrac = ((st.distKm - 363300.0) / (405500.0 - 363300.0)).coerceIn(0.0, 1.0)
    val distNote = when {
        distFrac < 0.15 -> "قريب من الحضيض (أقرب نقطة)"
        distFrac > 0.85 -> "قريب من الأوج (أبعد نقطة)"
        else -> "مسافة متوسطة"
    }
    val phaseName = Phases.phaseName(st.phaseAngle)

    fun shareText(): String = buildString {
        appendLine("القمر الآن ${Phases.phaseEmoji(st.phaseAngle)} $phaseName")
        appendLine("العمر: $ageD يوم $ageH ساعة • الإضاءة ${Fmt.num(st.illumination * 100, 1)}%")
        appendLine("المسافة: ${Fmt.num(st.distKm, 0)} كم ($distNote)")
        upcoming.forEach { (k, t) -> appendLine("${k.ar}: ${env.dateTime(t)}") }
    }

    ScreenScaffold(title = "أطوار القمر", onBack = env.back, subtitle = env.place.name, actions = { ShareAction { shareText() } }) {
        ScreenList {
            item {
                GlassCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        MoonDiscCanvas(st.phaseAngle, env.lat < 0, Modifier.fillMaxWidth().height(230.dp))
                        Text(phaseName, color = o.gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            if (st.waxing) "يتزايد نوره" else "يتناقص نوره", color = o.muted, style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        MetricRow(
                            "الإضاءة" to "${Fmt.num(st.illumination * 100, 1)}%",
                            "العمر" to "$ageD يوم $ageH س",
                            "الاستطالة" to Fmt.deg(st.elongation)
                        )
                    }
                }
            }

            item {
                GlassCard(title = "المسافة") {
                    InfoRow("المسافة عن الأرض", "${Fmt.num(st.distKm, 0)} كم", emphasize = true)
                    InfoRow("الحضيض / الأوج (تقريباً)", "363,300 / 405,500 كم")
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                        Pill(distNote, if (distFrac < 0.15) o.success else if (distFrac > 0.85) o.warning else o.info)
                    }
                }
            }

            item {
                GlassCard(title = "الأطوار القادمة") {
                    upcoming.forEach { (k, t) ->
                        MoonPhaseRow(k.ar, env.dateTime(t), "بعد ${moonCountdown(t - now)}")
                    }
                }
            }

            item {
                GlassCard(title = "منزلة القمر") {
                    InfoRow("المنزلة الحالية", Mansions.names[mansion.first], emphasize = true)
                    InfoRow("نسبة قطع المنزلة", "${Fmt.num(mansion.second * 100, 0)}%")
                    Spacer(Modifier.height(6.dp))
                    GlassOutlinedButton(onClick = { env.navigate(Routes.MANSIONS) }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Text("منازل القمر الثمانية والعشرون")
                    }
                }
            }

            item {
                GlassCard(title = "أطوار الاثني عشر شهراً القادمة", subtitle = "مع الشهر الهجري الذي يبدأ بعد كل محاق") {
                    val data = year
                    if (data == null) Busy() else data.forEach { e ->
                        MoonPhaseRow(
                            e.kind.ar, env.dateTime(e.millis), e.hijriLabel ?: "",
                            highlight = e.kind == PhaseKind.NEW || e.kind == PhaseKind.FULL
                        )
                    }
                }
            }
            item { Note("الأطوار محسوبة فلكياً بالتوقيت المحلي. بداية الشهر الهجري المعروضة تبعاً لطريقة التقويم الهجري المختارة في الإعدادات وقد تختلف عن الرؤية الفعلية.") }
        }
    }
}

@Composable
private fun rememberComputed2(today: java.time.LocalDate, s: com.mawaqeet.app.data.AppSettings): List<MoonPhaseRowData>? {
    val env = LocalEnv.current
    return com.mawaqeet.app.ui.components.rememberComputed(today, s) {
        val from = env.dayStart(today)
        val events: List<PhaseEvent> = Phases.between(from, from + 366 * DAY_MS)
        events.map { e ->
            var label: String? = null
            if (e.kind == PhaseKind.NEW) {
                val d0 = env.dateOf(e.millis)
                for (k in 0..3) {
                    val d = d0.plusDays(k.toLong())
                    val h = Calendars.toHijri(d, s.hijriMethod, s.hijriAdjust)
                    if (h.day == 1) {
                        label = "بداية ${Calendars.hijriMonths[h.month - 1]} ${h.year} هـ • ${env.gregorian(d)}"
                        break
                    }
                }
            }
            MoonPhaseRowData(e.kind, e.millis, label)
        }
    }
}

@Composable
private fun MoonPhaseRow(title: String, whenText: String, extra: String, highlight: Boolean = false) {
    val o = AppTheme.ornament
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = if (highlight) o.gold else o.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(whenText, color = o.ink, style = MaterialTheme.typography.bodyMedium)
        }
        if (extra.isNotEmpty()) Text(extra, color = o.muted, style = MaterialTheme.typography.labelMedium)
    }
}
