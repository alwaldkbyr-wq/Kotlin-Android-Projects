package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OsmMap
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.MapSheet
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalDark
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private class TzCity(val name: String, val zoneId: String)

private val tzCities = listOf(
    TzCity("مكة المكرمة", "Asia/Riyadh"), TzCity("القاهرة", "Africa/Cairo"), TzCity("الجزائر", "Africa/Algiers"),
    TzCity("الرباط", "Africa/Casablanca"), TzCity("تونس", "Africa/Tunis"), TzCity("الرياض", "Asia/Riyadh"),
    TzCity("دبي", "Asia/Dubai"), TzCity("إسطنبول", "Europe/Istanbul"), TzCity("لندن", "Europe/London"),
    TzCity("باريس", "Europe/Paris"), TzCity("موسكو", "Europe/Moscow"), TzCity("طهران", "Asia/Tehran"),
    TzCity("كراتشي", "Asia/Karachi"), TzCity("دلهي", "Asia/Kolkata"), TzCity("جاكرتا", "Asia/Jakarta"),
    TzCity("كوالالمبور", "Asia/Kuala_Lumpur"), TzCity("بكين", "Asia/Shanghai"), TzCity("طوكيو", "Asia/Tokyo"),
    TzCity("نيويورك", "America/New_York"), TzCity("لوس أنجلوس", "America/Los_Angeles"), TzCity("سيدني", "Australia/Sydney")
)

private fun tzOffsetText(totalSeconds: Int): String {
    val sign = if (totalSeconds < 0) "-" else "+"
    val a = abs(totalSeconds)
    val h = a / 3600
    val m = (a / 60) % 60
    return "UTC$sign$h" + if (m != 0) ":%02d".format(java.util.Locale.US, m) else ""
}

private fun tzDiffText(diffSeconds: Int): String {
    if (diffSeconds == 0) return "نفس التوقيت"
    val h = abs(diffSeconds) / 3600.0
    val s = if (h == h.roundToInt().toDouble()) "${h.roundToInt()}" else "%.1f".format(java.util.Locale.US, h)
    return if (diffSeconds > 0) "يسبقك بـ $s س" else "يتأخر عنك بـ $s س"
}

/** خريطة المناطق الزمنية: نطاقات تقريبية حسب خطوط الطول + ساعات المدن الكبرى */
@Composable
fun TimeZoneMapScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val zone = env.zone
    val rules = zone.rules
    val nowI = Instant.ofEpochMilli(env.now)
    val myOffset = rules.getOffset(nowI).totalSeconds
    val myBand = (myOffset / 3600.0).roundToInt()
    val dst = rules.isDaylightSavings(nowI)
    val next = rules.nextTransition(nowI)

    val colA = primary.copy(alpha = 0.16f).toArgb()
    val colB = o.gold.copy(alpha = 0.16f).toArgb()
    val colMine = primary.copy(alpha = 0.50f).toArgb()
    val lineC = o.gold.copy(alpha = 0.6f).toArgb()
    val labelC = o.ink.toArgb()
    val key = remember(myBand, env.lat, env.lon, colA, colB, colMine) { Any() }
    val applied = remember { arrayOfNulls<Any>(1) }
    val zones = remember { tzCities.map { c -> c to runCatching { ZoneId.of(c.zoneId) }.getOrNull() } }

    fun cityLine(c: TzCity, z: ZoneId?): String {
        if (z == null) return "${c.name}: --"
        val off = z.rules.getOffset(nowI).totalSeconds
        return "${c.name}: ${com.mawaqeet.app.data.Fmt.time(env.now, z, env.settings.h24)} (${tzOffsetText(off)})"
    }

    ScreenScaffold(
        title = "المناطق الزمنية", immersive = true, onBack = env.back, subtitle = "نطاقات UTC وساعات المدن",
        actions = {
            ShareAction {
                "منطقتي الزمنية: ${zone.id} (${tzOffsetText(myOffset)})\n" + zones.joinToString("\n") { (c, z) -> cityLine(c, z) }
            }
        }
    ) {
        MapSheet(mapContent = {
            Box(Modifier.fillMaxSize()) {
                OsmMap(Modifier.fillMaxSize(), 25.0, env.lon, 1.8, LocalDark.current) { map ->
                    if (applied[0] !== key) {
                        applied[0] = key
                        map.overlays.clear()
                        for (n in -12..12) {
                            val west = (15.0 * n - 7.5).coerceAtLeast(-180.0)
                            val east = (15.0 * n + 7.5).coerceAtMost(180.0)
                            val pg = Polygon()
                            pg.points = listOf(
                                GeoPoint(MapMath.MAX_LAT, west), GeoPoint(MapMath.MAX_LAT, east),
                                GeoPoint(-MapMath.MAX_LAT, east), GeoPoint(-MapMath.MAX_LAT, west)
                            )
                            pg.fillPaint.color = if (n == myBand) colMine else if (n % 2 == 0) colA else colB
                            pg.outlinePaint.color = lineC
                            pg.outlinePaint.strokeWidth = 1.5f
                            pg.setInfoWindow(null)
                            map.overlays.add(pg)
                        }
                        for (n in -12..12) {
                            val lon = (15.0 * n).coerceIn(-171.0, 171.0)
                            val t = if (n == 0) "0" else if (n > 0) "+$n" else "−${-n}"
                            map.overlays.add(mapLabelMarker(map, 72.0, lon, t, labelC, 10f))
                        }
                        map.overlays.add(mapDotMarker(map, env.lat, env.lon, "موقعك", 0xFF1E88E5.toInt(), 16, "${zone.id} • ${tzOffsetText(myOffset)}"))
                    }
                }
            }
            }) {
                item {
                    GlassCard(
                        title = "منطقتك الزمنية",
                        trailing = { Pill(if (dst) "توقيت صيفي" else "توقيت عادي", if (dst) o.warning else o.success) }
                    ) {
                        InfoRow("المنطقة", zone.id)
                        InfoRow("فرق التوقيت عن UTC", tzOffsetText(myOffset), emphasize = true)
                        InfoRow("الوقت الآن", env.time(env.now, true))
                        InfoRow("التوقيت الصيفي", if (dst) "مفعّل" else "غير مفعّل")
                        if (next == null) {
                            InfoRow("التحوّل القادم", "لا يوجد (توقيت ثابت)")
                        } else {
                            InfoRow("التحوّل القادم", env.dateTime(next.instant.toEpochMilli()))
                            InfoRow("التغيّر", "${tzOffsetText(next.offsetBefore.totalSeconds)} ← ${tzOffsetText(next.offsetAfter.totalSeconds)}")
                        }
                    }
                }
                item {
                    GlassCard(title = "ساعات العالم") {
                        zones.forEach { (c, z) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, color = o.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    if (z != null) {
                                        val off = z.rules.getOffset(nowI).totalSeconds
                                        Text("${tzOffsetText(off)} • ${tzDiffText(off - myOffset)}", color = o.muted, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (z != null) {
                                    val dayDiff = java.time.Duration.between(
                                        nowI.atZone(zone).toLocalDate().atStartOfDay(), nowI.atZone(z).toLocalDate().atStartOfDay()
                                    ).toDays()
                                    if (dayDiff != 0L) Pill(if (dayDiff > 0) "غداً" else "أمس", o.info)
                                    Text(
                                        "  " + com.mawaqeet.app.data.Fmt.time(env.now, z, env.settings.h24),
                                        color = o.ink, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End
                                    )
                                } else Text("--", color = o.muted)
                            }
                        }
                    }
                }
                item { Note("حدود المناطق الزمنية تقريبية (حسب خطوط الطول): كل نطاق 15° يساوي ساعة واحدة، والنطاق الملوّن الأقوى هو نطاقك. الحدود الحقيقية تتبع الدول والتوقيت الصيفي.") }
            }
    }
}
