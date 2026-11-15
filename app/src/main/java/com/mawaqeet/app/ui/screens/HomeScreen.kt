package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.DayNight
import com.mawaqeet.app.astro.DayNightCalc
import com.mawaqeet.app.astro.Eclipse
import com.mawaqeet.app.astro.Eclipses
import com.mawaqeet.app.astro.Mansions
import com.mawaqeet.app.astro.PhaseEvent
import com.mawaqeet.app.astro.PhaseKind
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerDay
import com.mawaqeet.app.astro.Qibla
import com.mawaqeet.app.astro.RiseSet
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.SeasonEvent
import com.mawaqeet.app.astro.Seasons
import com.mawaqeet.app.astro.fmtDuration
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.TileSpec
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.components.tileGrid
import com.mawaqeet.app.ui.theme.AppTheme

private class HomeData(
    val day: DayNight,
    val moonRs: RiseSet,
    val prayers: PrayerDay,
    val tomorrowFajr: Long?,
    val seasons: List<SeasonEvent>,
    val eclipses: List<Eclipse>,
    val phases: List<PhaseEvent>
)

private fun homeCompute(env: Env): HomeData {
    val today = env.today
    val ds = env.dayStart(today)
    val now = env.now
    val day = DayNightCalc.compute(ds, env.lat, env.lon, env.place.heightM)
    val moonRs = RiseSetCalc.compute(Body.MOON, ds, env.lat, env.lon)
    val prayers = env.prayers(today)
    val tomorrow = env.prayers(today.plusDays(1))[Prayer.FAJR]
    val y = today.year
    val seasons = (Seasons.of(y) + Seasons.of(y + 1))
    val eclipses = Eclipses.between(now, now + 366 * DAY_MS)
    val phases = Phases.between(now, now + 45 * DAY_MS)
    return HomeData(day, moonRs, prayers, tomorrow, seasons, eclipses, phases)
}

@Composable
fun HomeScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val today = env.today
    val data = rememberComputed(today, env.place, env.settings) { homeCompute(env) }
    val moon = remember(env.now / 60000L) { Phases.state(env.now) }
    val mansion = remember(env.now / 600000L) { Mansions.moonMansion(env.now) }
    val qBearing = remember(env.lat, env.lon) { Qibla.bearing(env.lat, env.lon) }
    val qDist = remember(env.lat, env.lon) { Qibla.distanceKm(env.lat, env.lon) }

    val nextPrayer: Pair<Prayer, Long>? = data?.let { d ->
        d.prayers.next(env.now) ?: d.tomorrowFajr?.let { Prayer.FAJR to it }
    }
    val nextSeason = data?.seasons?.filter { it.millis > env.now }?.minByOrNull { it.millis }
    val nextEclipse = data?.eclipses?.filter { it.max > env.now }?.minByOrNull { it.max }
    val nextNew = data?.phases?.firstOrNull { it.kind == PhaseKind.NEW && it.millis > env.now }
    val nextFull = data?.phases?.firstOrNull { it.kind == PhaseKind.FULL && it.millis > env.now }
    val phaseName = Phases.phaseName(moon.phaseAngle)
    val phaseEmoji = Phases.phaseEmoji(moon.phaseAngle)

    fun summary(): String = buildString {
        appendLine("الساهور — ${env.place.name}")
        appendLine("${Calendars.weekDay(today)} ${env.gregorian(today)}")
        appendLine(env.hijri(today))
        appendLine(env.solarHijri(today))
        appendLine(env.julian(today))
        if (data != null) {
            appendLine()
            appendLine("مواقيت الصلاة:")
            Prayer.values().forEach { appendLine("${it.ar}: ${env.time(data.prayers[it])}") }
            appendLine()
            appendLine("الشروق: ${env.time(data.day.sunrise)} • الغروب: ${env.time(data.day.sunset)}")
            appendLine("طلوع القمر: ${env.time(data.moonRs.rise)} • غروب القمر: ${env.time(data.moonRs.set)}")
        }
        append("القمر: $phaseName ${phaseEmoji} (إضاءة ${Fmt.num(moon.illumination * 100, 0)}%)")
    }

    ScreenScaffold(
        title = "الساهور", subtitle = env.place.name, topLevel = true,
        actions = {
            IconButton(onClick = { env.navigate(Routes.LOCATIONS) }) { Icon(Icons.Filled.Place, "الموقع") }
            IconButton(onClick = { env.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "الإعدادات") }
        }
    ) {
        ScreenList {
            // ───── قرص السماء: اليوم كله في دائرة ─────
            item {
                GlassCard(onClick = { env.navigate(Routes.PRAYER) }) {
                    SkyDial(
                        now = env.now, dayStart = env.dayStart(today), day = data?.day, prayers = data?.prayers,
                        next = nextPrayer, moonIllum = moon.illumination, moonWaxing = moon.waxing
                    )
                    if (data != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Prayer.values().forEach { p ->
                                val isNext = nextPrayer?.first == p
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(p.ar, fontSize = 12.sp, color = if (isNext) MaterialTheme.colorScheme.primary else o.muted, maxLines = 1)
                                    Text(
                                        env.time(data.prayers[p]), fontSize = 14.sp, maxLines = 1,
                                        color = if (isNext) MaterialTheme.colorScheme.primary else o.ink,
                                        fontWeight = if (isNext) FontWeight.Black else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ───── التاريخ بالتقاويم الأربعة ─────
            item {
                GlassCard(title = Calendars.weekDay(today), subtitle = env.hijri(today), onClick = { env.navigate(Routes.DATES) }) {
                    InfoRow("ميلادي", env.gregorian(today))
                    InfoRow("هجري قمري", env.hijri(today), valueColor = MaterialTheme.colorScheme.primary)
                    InfoRow("هجري شمسي", env.solarHijri(today))
                    InfoRow("يولياني", env.julian(today))
                }
            }

            // ───── الشمس والقمر ─────
            item {
                GlassCard(title = "الشمس والقمر", subtitle = null, onClick = { env.navigate(Routes.SUN_MOON) }) {
                    if (data == null) {
                        Text("جارٍ الحساب…", color = o.muted)
                    } else {
                        MetricRow(
                            "الشروق" to env.time(data.day.sunrise),
                            "الغروب" to env.time(data.day.sunset),
                            "طول النهار" to (if (data.day.sunrise == null || data.day.sunset == null) "--" else fmtDuration(data.day.dayLengthMin))
                        )
                        MetricRow(
                            "طلوع القمر" to env.time(data.moonRs.rise),
                            "غروب القمر" to env.time(data.moonRs.set),
                            "الإضاءة" to "${Fmt.num(moon.illumination * 100, 0)}%"
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(phaseEmoji, fontSize = 34.sp)
                        Column(Modifier.weight(1f)) {
                            Text(phaseName, style = MaterialTheme.typography.titleMedium, color = o.ink)
                            Text("عمر القمر ${Fmt.num(moon.ageDays, 1)} يوم", style = MaterialTheme.typography.bodySmall, color = o.muted)
                        }
                    }
                }
            }
        }
    }
}
