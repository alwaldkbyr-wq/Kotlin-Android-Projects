package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Eclipse
import com.mawaqeet.app.astro.Eclipses
import com.mawaqeet.app.astro.EclipseType
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.Exporter
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate

private data class EclipseRowData(val e: Eclipse, val alt: Double)

private fun eclipseVisibility(r: EclipseRowData): String {
    val up = r.alt > 0
    return if (r.e.isSolar) {
        if (up) "الشمس فوق الأفق وقت الذروة (${Fmt.deg(r.alt)}) — قد يُرى إن كان الموقع داخل مسار الكسوف أو منطقة الرؤية"
        else "الشمس تحت الأفق وقت الذروة — لا يُرى من موقعك"
    } else {
        if (up) "القمر فوق الأفق وقت الذروة (${Fmt.deg(r.alt)}) — يُرى من موقعك"
        else "القمر تحت الأفق وقت الذروة — لا يُرى من موقعك"
    }
}

private fun eclipseText(env: Env, r: EclipseRowData): String = buildString {
    val e = r.e
    appendLine("${e.type.ar} — ${env.dateTime(e.max)}")
    appendLine("القدر: ${Fmt.num(e.magnitude, 3)} • غاما: ${Fmt.num(e.gamma, 3)}")
    if (!e.isSolar) {
        if (e.penumbralMinutes > 0) appendLine("شبه الظل: ${Math.round(e.penumbralMinutes)} د")
        if (e.partialMinutes > 0) appendLine("الجزئي: ${Math.round(e.partialMinutes)} د")
        if (e.totalMinutes > 0) appendLine("الكلي: ${Math.round(e.totalMinutes)} د")
    }
    appendLine(eclipseVisibility(r))
    append("(${env.place.name})")
}

private fun eclipseCountdown(ms: Long): String {
    val m = Math.max(0L, ms / 60000L)
    return "${m / 1440} يوم ${(m % 1440) / 60} ساعة ${m % 60} دقيقة"
}

/** الكسوف والخسوف خلال سنوات قادمة */
@Composable
fun EclipsesScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val ctx = LocalContext.current
    val now = env.now
    var years by remember { mutableStateOf(3) }
    val year0 = env.today.year

    val rows: List<EclipseRowData>? = rememberComputed(years, year0, env.lat, env.lon, env.zone) {
        val from = env.dayStart(LocalDate.of(year0, 1, 1))
        val to = env.dayStart(LocalDate.of(year0 + years, 1, 1))
        Eclipses.between(from, to).map { e ->
            val body = if (e.isSolar) Body.SUN else Body.MOON
            EclipseRowData(e, Ephemeris.horizontal(body, e.max, env.lat, env.lon).alt)
        }
    }
    val next = rows?.firstOrNull { it.e.max > now }

    fun allText(): String = buildString {
        appendLine("الكسوف والخسوف ${year0} – ${year0 + years - 1}")
        rows?.forEach { appendLine(eclipseText(env, it)); appendLine() }
    }

    ScreenScaffold(title = "الكسوف والخسوف", onBack = env.back, subtitle = env.place.name, actions = { ShareAction { allText() } }) {
        ScreenList {
            item {
                GlassCard(title = "المدة") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 5, 10).forEach { n ->
                            GlassChip(selected = years == n, onClick = { years = n }, label = { Text(if (n == 1) "سنة" else "$n سنوات") })
                        }
                    }
                    Text("من $year0 حتى ${year0 + years - 1}", color = o.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }

            if (rows == null) item { Busy() } else {
                if (next != null) item {
                    GlassCard(title = "الحدث القادم", subtitle = next.e.type.ar) {
                        InfoRow("الموعد", env.dateTime(next.e.max), emphasize = true)
                        InfoRow("بعد", eclipseCountdown(next.e.max - now))
                    }
                }
                if (rows.isEmpty()) item { Note("لا توجد حالات في هذه الفترة.") }
                rows.forEach { r ->
                    item {
                        val e = r.e
                        val past = e.max <= now
                        GlassCard(
                            title = e.type.ar,
                            subtitle = env.dateTime(e.max),
                            trailing = {
                                if (past) Pill("مضى", o.muted) else if (r === next) Pill("القادم", o.success)
                                IconButton(onClick = { Exporter.shareText(ctx, eclipseText(env, r)) }) {
                                    Icon(Icons.Filled.Share, "مشاركة", tint = o.ink)
                                }
                            }
                        ) {
                            InfoRow("القدر", Fmt.num(e.magnitude, 3))
                            InfoRow("غاما", Fmt.num(e.gamma, 3))
                            if (!e.isSolar) {
                                if (e.penumbralMinutes > 0) InfoRow("مدة شبه الظل", "${Math.round(e.penumbralMinutes)} د")
                                if (e.partialMinutes > 0) InfoRow("مدة الطور الجزئي", "${Math.round(e.partialMinutes)} د")
                                if (e.totalMinutes > 0) InfoRow("مدة الطور الكلي", "${Math.round(e.totalMinutes)} د")
                            }
                            Text(
                                eclipseVisibility(r),
                                color = if (r.alt > 0) o.success else o.muted,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                item { Note("القدر والمدد تقريبية. رؤية الكسوف الشمسي محلياً تحتاج معرفة مسار الظل؛ الظاهر هنا هو كون الشمس فوق الأفق وقت الذروة فقط. لا تنظر إلى الشمس مباشرة دون مرشّح مناسب.") }
            }
        }
    }
}
