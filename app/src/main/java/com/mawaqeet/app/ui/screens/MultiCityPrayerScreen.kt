package com.mawaqeet.app.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerCalc
import com.mawaqeet.app.astro.PrayerDay
import com.mawaqeet.app.data.Cities
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.Place
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.TableRowCells
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import org.json.JSONArray
import java.time.Instant

private const val MC_MAX = 6
private val mcPrayers = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

private class McRow(val place: Place, val day: PrayerDay, val tomorrowFajr: Long?)

private fun mcSame(a: Place, b: Place) = a.name == b.name && Math.abs(a.lat - b.lat) < 0.01 && Math.abs(a.lon - b.lon) < 0.01

private fun mcLoad(ctx: Context): List<Place> = runCatching {
    val s = ctx.getSharedPreferences("multicity", Context.MODE_PRIVATE).getString("cities", null) ?: return@runCatching emptyList<Place>()
    val a = JSONArray(s)
    (0 until a.length()).map { Place.fromJson(a.getJSONObject(it)) }.take(MC_MAX)
}.getOrDefault(emptyList())

private fun mcSave(ctx: Context, list: List<Place>) {
    val a = JSONArray()
    list.forEach { a.put(it.toJson()) }
    ctx.getSharedPreferences("multicity", Context.MODE_PRIVATE).edit().putString("cities", a.toString()).apply()
}

@Composable
fun MultiCityPrayerScreen() {
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val o = AppTheme.ornament
    var date by remember { mutableStateOf(env.today) }
    var picked by remember { mutableStateOf(mcLoad(ctx)) }
    var query by remember { mutableStateOf("") }
    val saved = env.settings.places
    val places = saved + picked.filter { p -> saved.none { mcSame(it, p) } }
    val h24 = env.settings.h24

    fun setPicked(l: List<Place>) { picked = l; mcSave(ctx, l) }

    val rows = rememberComputed(date, places, env.settings) {
        val ps = env.settings.prayerSettings
        places.map { p ->
            val s = ps.copy(heightM = p.heightM)
            val day = PrayerCalc.compute(date.atStartOfDay(p.zone).toInstant().toEpochMilli(), p.lat, p.lon, s)
            val nxt = PrayerCalc.compute(date.plusDays(1).atStartOfDay(p.zone).toInstant().toEpochMilli(), p.lat, p.lon, s)
            McRow(p, day, nxt[Prayer.FAJR])
        }
    }

    ScreenScaffold(
        title = "المواقيت في عدة مدن", onBack = env.back, subtitle = "كل مدينة بتوقيتها المحلي",
        actions = {
            ShareAction {
                buildString {
                    appendLine("مواقيت الصلاة — ${env.gregorian(date)}")
                    rows?.forEach { r ->
                        appendLine("${r.place.name} (${r.place.zoneId}): " + mcPrayers.joinToString(" • ") { "${it.ar} ${Fmt.time(r.day[it], r.place.zone, h24)}" })
                    }
                }
            }
        }
    ) {
        ScreenList {
            item { DayStepper(date, { date = it }) }
            if (rows == null) item { Busy() } else {
                item { SectionTitle("جدول المواقيت") }
                item {
                    GlassCard {
                        TableRowCells(listOf("المدينة") + mcPrayers.map { it.ar }, header = true, weights = listOf(1.7f, 1f, 1f, 1f, 1f, 1f, 1f))
                        rows.forEach { r ->
                            TableRowCells(
                                listOf(r.place.name) + mcPrayers.map { Fmt.time(r.day[it], r.place.zone, true) },
                                weights = listOf(1.7f, 1f, 1f, 1f, 1f, 1f, 1f)
                            )
                        }
                    }
                }
                item { Note("الجدول بنظام 24 ساعة وبتوقيت كل مدينة المحلي. تُحسب المواقيت بإعدادات الحساب الحالية لديك.") }

                item { SectionTitle("الساعة والصلاة القادمة") }
                rows.forEach { r ->
                    item {
                        val p = r.place
                        val cityToday = Instant.ofEpochMilli(env.now).atZone(p.zone).toLocalDate()
                        val next: Pair<String, Long>? = if (cityToday == date) {
                            r.day.next(env.now)?.let { it.first.ar to it.second } ?: r.tomorrowFajr?.let { "الفجر (غداً)" to it }
                        } else null
                        GlassCard(title = p.name, subtitle = p.zoneId) {
                            InfoRow("الساعة الآن", Fmt.time(env.now, p.zone, h24, true), emphasize = true)
                            InfoRow("تاريخ المدينة اليوم", env.gregorian(cityToday))
                            if (next != null) {
                                InfoRow("الصلاة القادمة", "${next.first} ${Fmt.time(next.second, p.zone, h24)}", valueColor = MaterialTheme.colorScheme.primary)
                                InfoRow("المتبقي", Fmt.countdown(next.second - env.now))
                            } else {
                                InfoRow("الصلاة القادمة", "-- (اختر تاريخ اليوم)")
                            }
                            Spacer(Modifier.height(6.dp))
                            MetricRow(
                                Prayer.FAJR.ar to Fmt.time(r.day[Prayer.FAJR], p.zone, h24),
                                Prayer.DHUHR.ar to Fmt.time(r.day[Prayer.DHUHR], p.zone, h24),
                                Prayer.ASR.ar to Fmt.time(r.day[Prayer.ASR], p.zone, h24)
                            )
                            MetricRow(
                                Prayer.SUNRISE.ar to Fmt.time(r.day[Prayer.SUNRISE], p.zone, h24),
                                Prayer.MAGHRIB.ar to Fmt.time(r.day[Prayer.MAGHRIB], p.zone, h24),
                                Prayer.ISHA.ar to Fmt.time(r.day[Prayer.ISHA], p.zone, h24)
                            )
                        }
                    }
                }
            }

            item { SectionTitle("مدن أخرى (حتى $MC_MAX)") }
            item {
                GlassCard(title = "المضافة: ${picked.size} من $MC_MAX") {
                    picked.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge)
                            Text(p.zoneId, color = o.muted, style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { setPicked(picked.filterIndexed { j, _ -> j != i }) }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (picked.isEmpty()) Text("لم تضف مدناً بعد. مواقعك المحفوظة تظهر دائماً.", color = o.muted, style = MaterialTheme.typography.bodySmall)
                    if (picked.size < MC_MAX) {
                        Spacer(Modifier.height(6.dp))
                        Field("ابحث عن مدينة لإضافتها", query, { query = it })
                        val q = query.trim()
                        if (q.isNotEmpty()) {
                            val res = Cities.all.filter { c -> c.name.contains(q, ignoreCase = true) && places.none { mcSame(it, c) } }
                            if (res.isEmpty()) Text("لا توجد نتائج", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                            res.take(8).forEach { c ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { if (picked.size < MC_MAX) { setPicked(picked + c); query = "" } }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(c.name, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge)
                                    Text(c.zoneId, color = o.muted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (res.size > 8) Text("… ${res.size - 8} نتيجة أخرى، أضف حروفاً لتضييق البحث", color = o.muted, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text("بلغت الحد الأقصى؛ احذف مدينة لإضافة غيرها.", color = o.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
