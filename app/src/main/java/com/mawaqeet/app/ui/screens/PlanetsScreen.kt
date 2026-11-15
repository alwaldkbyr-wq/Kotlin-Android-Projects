package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.AU_KM
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.Horizontal
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.Planets
import com.mawaqeet.app.astro.RiseSet
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.acosd
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.norm180
import com.mawaqeet.app.astro.separation
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate
import java.util.Locale

private class PlRow(
    val body: Body,
    val rs: RiseSet,
    val mag: Double,
    val distKm: Double,
    /** الاستطالة عن الشمس (درجات) */
    val elong: Double,
    /** شرق الشمس (يظهر مساءً) أم غربها (يظهر صباحاً) */
    val east: Boolean,
    /** هل يُرى بالعين المجردة في ساعات الليل */
    val tonight: Boolean,
    /** القمر فقط: الطور والإضاءة */
    val phaseName: String?,
    val illum: Double
)

private class PlData(val rows: List<PlRow>, val sunRs: RiseSet)

private fun plCompute(env: Env, date: LocalDate): PlData {
    val d0 = env.dayStart(date)
    val noon = d0 + 12 * HOUR_MS
    val jde = jdeOf(jdOf(noon))
    val sunEq = Ephemeris.equatorial(Body.SUN, jde)
    val sunEcl = Ephemeris.ecliptic(Body.SUN, jde)
    val moonState = Phases.state(noon)
    val times = longArrayOf(d0 + 21 * HOUR_MS, d0 + DAY_MS, d0 + 27 * HOUR_MS)
    val rows = Body.values().map { b ->
        val rs = RiseSetCalc.compute(b, d0, env.lat, env.lon, null, env.place.heightM)
        val eq = Ephemeris.equatorial(b, jde)
        val ecl = Ephemeris.ecliptic(b, jde)
        val elong = if (b == Body.SUN) 0.0 else separation(eq.ra, eq.dec, sunEq.ra, sunEq.dec)
        val east = norm180(ecl.lon - sunEcl.lon) > 0
        val mag = when (b) {
            Body.SUN -> -26.74
            Body.MOON -> -12.73 + 0.026 * acosd(2 * moonState.illumination - 1) + 4e-9 * Math.pow(acosd(2 * moonState.illumination - 1), 4.0)
            else -> Planets.magnitude(b, jde)
        }
        // يُرى الليلة: فوق الأفق بأكثر من 5° في أحد أوقات المساء/منتصف الليل/قبل الفجر حين تكون الشمس تحت −6°
        val tonight = b != Body.SUN && mag < 5.8 && times.any { t ->
            RiseSetCalc.geoAlt(Body.SUN, t, env.lat, env.lon) < -6.0 && RiseSetCalc.geoAlt(b, t, env.lat, env.lon) > 5.0
        }
        PlRow(
            b, rs, mag, ecl.distKm, elong, east, tonight,
            if (b == Body.MOON) Phases.phaseName(moonState.phaseAngle) else null,
            if (b == Body.MOON) moonState.illumination else 0.0
        )
    }
    return PlData(rows, rows[0].rs)
}

/** (نص الحالة، 0 نجاح / 1 معلومة / 2 خافت) */
private fun plStatus(b: Body, alt: Double, sunAlt: Double, mag: Double): Pair<String, Int> = when {
    alt <= 0.0 -> "تحت الأفق" to 2
    b == Body.SUN -> "فوق الأفق" to 0
    sunAlt < -6.0 -> if (b.isPlanet && mag > 5.8) ("فوق الأفق (يحتاج منظاراً)" to 1) else ("ظاهر الآن" to 0)
    b == Body.MOON -> "ظاهر نهاراً" to 0
    (b == Body.VENUS || b == Body.JUPITER) && alt > 10.0 -> "يُرى نهاراً بصعوبة" to 1
    else -> "فوق الأفق (ضوء النهار)" to 1
}

private fun plDistance(b: Body, km: Double): String = when {
    b == Body.MOON -> String.format(Locale.US, "%,d", km.toLong()) + " كم"
    else -> Fmt.num(km / AU_KM, 3) + " و.ف (" + Fmt.num(km / 1e6, 1) + " مليون كم)"
}

@Composable
fun PlanetsScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    var date by remember { mutableStateOf(env.today) }
    val isToday = date == env.today
    val data = rememberComputedPlanets(env, date)
    val minute = env.now / 60_000L
    val nowPos = remember(minute, env.lat, env.lon) {
        Body.values().map { Ephemeris.horizontal(it, minute * 60_000L, env.lat, env.lon) }
    }
    val sunAltNow = nowPos[Body.SUN.ordinal].alt

    ScreenScaffold(
        title = "الكواكب", onBack = env.back, subtitle = "${env.place.name} • ${env.gregorian(date)}",
        actions = {
            ShareAction {
                val d = data
                if (d == null) "" else buildString {
                    appendLine("الشمس والقمر والكواكب — ${env.gregorian(date)} (${env.place.name})")
                    for (r in d.rows) {
                        appendLine(
                            "• ${r.body.ar}: طلوع ${env.time(r.rs.rise)} | عبور ${env.time(r.rs.transit)} | غروب ${env.time(r.rs.set)}" +
                                " | القدر ${Fmt.num(r.mag, 1)}" + (if (r.body != Body.SUN) " | الاستطالة ${Fmt.deg(r.elong, 0)}" else "")
                        )
                    }
                }
            }
        }
    ) {
        ScreenList {
            item { DayStepper(date, { date = it }) }
            if (data == null) {
                item { Busy() }
            } else {
                // ملخص
                item {
                    val naked = data.rows.filter { it.tonight && it.body.isPlanet }
                    GlassCard(title = "السماء ${if (isToday) "الآن" else "في هذا اليوم"}") {
                        if (isToday) {
                            val period = when {
                                sunAltNow > 0 -> "نهار"
                                sunAltNow > -6 -> "غسق مدني"
                                sunAltNow > -12 -> "غسق بحري"
                                sunAltNow > -18 -> "غسق فلكي"
                                else -> "ليل"
                            }
                            InfoRow("الشمس", "ارتفاع ${Fmt.deg(sunAltNow)} • $period")
                        }
                        InfoRow(
                            "كواكب الليلة بالعين المجردة",
                            if (naked.isEmpty()) "لا شيء ظاهر بوضوح" else naked.joinToString("، ") { it.body.ar },
                            valueColor = if (naked.isEmpty()) o.muted else o.success
                        )
                        if (data.sunRs.rise != null && data.sunRs.set != null) {
                            InfoRow("طول النهار", Fmt.span(data.sunRs.set - data.sunRs.rise))
                        }
                    }
                }
                for (r in data.rows) item(key = r.body.ordinal) { PlanetCard(r, isToday, nowPos[r.body.ordinal], sunAltNow) }
                item { Note("القدر الأصغر (الأكثر سالبية) يعني لمعاناً أكبر. «و.ف» وحدة فلكية = 149.6 مليون كم. الاستطالة هي البعد الزاوي عن الشمس؛ الكوكب الشرقي يُرى بعد الغروب والغربي قبل الشروق. الحسابات عند ظهر اليوم المحلي بدقة تقريبية.") }
            }
        }
    }
}

@Composable
private fun rememberComputedPlanets(env: Env, date: LocalDate): PlData? =
    com.mawaqeet.app.ui.components.rememberComputed(date, env.lat, env.lon, env.place.heightM, env.zone) { plCompute(env, date) }

@Composable
private fun PlanetCard(r: PlRow, isToday: Boolean, now: Horizontal, sunAlt: Double) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val b = r.body
    val rs = r.rs
    val (statusText, statusKind) = plStatus(b, now.alt, sunAlt, r.mag)
    GlassCard(
        title = "${b.symbol} ${b.ar}",
        subtitle = when (b) {
            Body.SUN -> "نجمنا"
            Body.MOON -> "${r.phaseName ?: ""} • إضاءة ${Fmt.num(r.illum * 100, 0)}%"
            else -> "قدر ${Fmt.num(r.mag, 1)}"
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                if (isToday) Pill(statusText, when (statusKind) { 0 -> o.success; 1 -> o.info; else -> o.muted })
                else if (!rs.alwaysDown) Pill("أقصى ارتفاع ${Fmt.deg(rs.transitAlt, 0)}", o.info)
                if (b == Body.VENUS || b == Body.MERCURY) {
                    Pill(
                        if (r.elong < 10) "قريب من الشمس" else if (r.east) "نجم المساء" else "نجم الصباح",
                        if (r.elong < 10) o.muted else o.gold, Modifier.padding(top = 4.dp)
                    )
                } else if (b.isPlanet && r.elong > 165) {
                    Pill("قرب المقابلة", o.warning, Modifier.padding(top = 4.dp))
                } else if (b.isPlanet && r.mag > 5.8) {
                    Pill("يحتاج منظاراً", o.muted, Modifier.padding(top = 4.dp))
                }
            }
        }
    ) {
        MetricRow("الطلوع" to env.time(rs.rise), "العبور" to env.time(rs.transit), "الغروب" to env.time(rs.set))
        if (rs.alwaysUp) Text("لا يغيب في هذا اليوم", color = o.success, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 2.dp))
        if (rs.alwaysDown) Text("لا يطلع في هذا اليوم", color = o.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 2.dp))
        if (isToday) {
            InfoRow("الارتفاع والسمت الآن", "${Fmt.deg(now.alt)} • ${Fmt.deg(now.az, 0)} ${Fmt.direction(now.az)}")
        } else {
            InfoRow("أقصى ارتفاع (عند العبور)", Fmt.deg(rs.transitAlt))
        }
        if (b != Body.SUN) {
            InfoRow("القدر الظاهري", Fmt.num(r.mag, 1), valueColor = if (b.isPlanet && r.mag > 5.8) o.muted else Color.Unspecified)
            InfoRow("الاستطالة عن الشمس", Fmt.deg(r.elong, 0) + (if (r.elong > 1) (if (r.east) " شرق الشمس" else " غرب الشمس") else ""))
        }
        InfoRow("المسافة عن الأرض", plDistance(b, r.distKm))
        if (b == Body.SUN && rs.rise != null && rs.set != null) InfoRow("مدة النهار", Fmt.span(rs.set - rs.rise))
    }
}
