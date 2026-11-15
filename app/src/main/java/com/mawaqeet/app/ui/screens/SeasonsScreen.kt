package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.SeasonEvent
import com.mawaqeet.app.astro.SeasonKind
import com.mawaqeet.app.astro.Seasons
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme

private class SeasonsData(val events: List<SeasonEvent>, val nextMarch: Long)

private val seasonNames = listOf("الربيع", "الصيف", "الخريف", "الشتاء")

private fun seasonSpan(ms: Long): String {
    val d = ms / DAY_MS
    val h = (ms % DAY_MS) / HOUR_MS
    return "$d يوم و$h ساعة"
}

private fun seasonRel(ms: Long, now: Long): String {
    val diff = ms - now
    val t = seasonSpan(Math.abs(diff))
    return if (diff >= 0) "بعد $t" else "منذ $t"
}

private fun seasonName(env: Env, kind: SeasonKind): String {
    val north = env.lat >= 0
    return seasonNames[(kind.ordinal + (if (north) 0 else 2)) % 4]
}

@Composable
fun SeasonsScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    var year by remember { mutableStateOf(env.today.year) }
    val data = rememberComputed(year) {
        val ev = Seasons.of(year)
        SeasonsData(ev, Seasons.of(year + 1).first().millis)
    }
    val hemi = if (env.lat >= 0) "النصف الشمالي" else "النصف الجنوبي"

    fun summary(): String = buildString {
        appendLine("الفصول الفلكية $year ($hemi)")
        data?.events?.forEach {
            appendLine("${it.kind.ar} (بداية ${seasonName(env, it.kind)}): ${env.dateTime(it.millis)} • ${Calendars.weekDay(env.dateOf(it.millis))}")
        }
    }

    ScreenScaffold(title = "الفصول", onBack = env.back, subtitle = "الاعتدالان والانقلابان • $hemi", actions = { ShareAction { summary() } }) {
        ScreenList {
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { year -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "السنة السابقة", tint = o.ink) }
                        Text("$year", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = o.ink, textAlign = TextAlign.Center)
                        IconButton(onClick = { year += 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "السنة التالية", tint = o.ink) }
                    }
                }
            }
            if (data == null) {
                item { Busy() }
            } else {
                data.events.forEachIndexed { i, e ->
                    item {
                        val end = if (i < 3) data.events[i + 1].millis else data.nextMarch
                        val date = env.dateOf(e.millis)
                        GlassCard(title = e.kind.ar, subtitle = "بداية ${seasonName(env, e.kind)} في $hemi") {
                            InfoRow("التاريخ (ميلادي)", env.gregorian(date))
                            InfoRow("التاريخ (هجري)", env.hijri(date))
                            InfoRow("اليوم", Calendars.weekDay(date))
                            InfoRow("الوقت المحلي", env.time(e.millis), emphasize = true)
                            InfoRow(if (e.millis >= env.now) "المتبقي" else "مضى", seasonRel(e.millis, env.now), valueColor = MaterialTheme.colorScheme.primary)
                            InfoRow("مدة هذا الفصل الفلكي", seasonSpan(end - e.millis))
                            if (e.kind == SeasonKind.MARCH_EQUINOX) {
                                InfoRow("الهجري الشمسي", env.solarHijri(date))
                            }
                        }
                    }
                }
                item {
                    Note(
                        "الاعتدال الربيعي (آذار/مارس) هو مطلع السنة الهجرية الشمسية وعيد النيروز؛ يبدأ الشهر الأول (الحمل / فروردين) عند هذا الاعتدال تقريباً. " +
                            "الأوقات بالتوقيت المحلي للموقع، وأسماء الفصول مبنية على موقعك (${if (env.lat >= 0) "شمال" else "جنوب"} خط الاستواء)."
                    )
                }
            }
        }
    }
}
