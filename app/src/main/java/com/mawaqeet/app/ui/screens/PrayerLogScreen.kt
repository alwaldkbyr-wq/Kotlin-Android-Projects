package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.PrayerState
import com.mawaqeet.app.data.WorshipStore
import com.mawaqeet.app.ui.components.ChipsSelector
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OrnateTabs
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.CanvasFonts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PL_PRAYERS = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

@Composable
private fun plStateColor(s: Int): Color {
    val o = AppTheme.ornament
    return when (s) {
        PrayerState.JAMAA -> o.success
        PrayerState.ON_TIME -> MaterialTheme.colorScheme.primary
        PrayerState.LATE -> o.warning
        PrayerState.MISSED -> o.danger
        else -> o.muted
    }
}

/** سجل الصلوات والقضاء */
@Composable
fun PrayerLogScreen() {
    val env = LocalEnv.current
    var tab by remember { mutableIntStateOf(0) }
    ScreenScaffold(title = "سجل الصلوات والقضاء", onBack = env.back, subtitle = "متابعة شخصية تُحفظ على جهازك فقط") {
        ScreenList {
            item { OrnateTabs(listOf("اليوم", "الإحصاء", "القضاء"), tab) { tab = it } }
            when (tab) {
                0 -> plToday()
                1 -> plStats()
                else -> plQada()
            }
        }
    }
}

// ───────────────────────── اليوم ─────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.plToday() {
    item { PLTodayContent() }
}

@Composable
private fun PLTodayContent() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val store = WorshipStore.get(LocalContext.current)
    store.version // قراءة لإعادة الرسم
    var date by remember { mutableStateOf(env.today) }
    var expanded by remember { mutableIntStateOf(-1) }
    val day = remember(date, env.settings) { env.prayers(date) }
    val states = store.states(date)
    val streak = store.streak(env.today)

    Column {
        DayStepper(date, { date = it; expanded = -1 })
        GlassCard(title = "الصلوات الخمس", subtitle = "اضغط على الصلاة لاختيار حالتها") {
            PL_PRAYERS.forEachIndexed { i, p ->
                val st = states[i]
                Column(
                    Modifier.fillMaxWidth().clickable { expanded = if (expanded == i) -1 else i }.padding(vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.ar, Modifier.width(72.dp), color = o.ink, style = MaterialTheme.typography.titleMedium)
                        Text(env.time(day[p]), Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.bodyMedium)
                        Pill(PrayerState.labels[st], plStateColor(st))
                    }
                    if (expanded == i) {
                        Spacer(Modifier.height(8.dp))
                        ChipsSelector(
                            options = (1..4).associate { it.toString() to PrayerState.labels[it] } + ("0" to "مسح"),
                            selected = st.toString(),
                            onSelect = { store.setState(date, i, it.toInt()) }
                        )
                    }
                    if (st == PrayerState.MISSED) {
                        Spacer(Modifier.height(6.dp))
                        if (store.qadaAdded(date, i)) {
                            Text("أُضيفت إلى القضاء", color = o.success, style = MaterialTheme.typography.labelMedium)
                        } else {
                            GlassOutlinedButton(onClick = { store.addMissedToQada(date, i) }, nav = true) { Text("أضف للقضاء") }
                        }
                    }
                }
            }
        }
        GlassCard(title = "الاستمرار") {
            MetricRow(
                "أيام متتالية بالصلوات الخمس" to streak.toString(),
                "المؤدّاة اليوم المعروض" to "${states.count { PrayerState.prayed(it) }}/5"
            )
        }
    }
}

// ───────────────────────── الإحصاء ─────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.plStats() {
    item { PLStatsContent() }
}

@Composable
private fun PLStatsContent() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val store = WorshipStore.get(LocalContext.current)
    store.version
    var range by remember { mutableIntStateOf(7) }
    val today = env.today

    // counts[prayer][state]
    val counts = Array(5) { IntArray(5) }
    for (k in 0 until range) {
        val st = store.states(today.minusDays(k.toLong()))
        for (i in 0 until 5) counts[i][st[i]]++
    }
    fun pct(num: Int, den: Int) = if (den == 0) 0 else Math.round(num * 100f / den)
    val logged = counts.sumOf { c -> c[1] + c[2] + c[3] + c[4] }
    val onTime = counts.sumOf { it[1] }
    val jamaa = counts.sumOf { it[2] }
    val late = counts.sumOf { it[3] }
    val missed = counts.sumOf { it[4] }

    val cJamaa = o.success
    val cOn = MaterialTheme.colorScheme.primary
    val cLate = o.warning
    val cMissed = o.danger
    val track = o.muted.copy(alpha = 0.18f)
    val labelColor = o.muted.toArgb()

    Column {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            ChipsSelector(mapOf("7" to "آخر 7 أيام", "30" to "آخر 30 يوماً"), range.toString()) { range = it.toInt() }
        }
        GlassCard(title = "النسب خلال الفترة", subtitle = "من الصلوات المسجّلة فقط") {
            if (logged == 0) {
                Text("لا توجد صلوات مسجّلة في هذه الفترة بعد.", color = o.muted)
            } else {
                MetricRow(
                    "في جماعة" to "${pct(jamaa, logged)}%",
                    "في وقتها" to "${pct(onTime, logged)}%",
                    "متأخرة" to "${pct(late, logged)}%",
                    "فائتة" to "${pct(missed, logged)}%"
                )
            }
        }
        GlassCard(title = "لكل صلاة") {
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                val w = size.width; val h = size.height
                val bottom = h - 26.dp.toPx()
                val top = 6.dp.toPx()
                val slot = w / 5f
                val bw = slot * 0.5f
                val paint = android.graphics.Paint().apply {
                    textSize = 12.sp.toPx(); color = labelColor; textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true; typeface = CanvasFonts.text
                }
                // الترتيب من اليمين إلى اليسار: الفجر أولاً
                PL_PRAYERS.forEachIndexed { i, p ->
                    val cx = w - slot * (i + 0.5f)
                    val x = cx - bw / 2
                    val c = counts[i]
                    val tot = c[1] + c[2] + c[3] + c[4]
                    drawRoundRect(track, Offset(x, top), Size(bw, bottom - top), CornerRadius(6.dp.toPx()))
                    if (tot > 0) {
                        var y = bottom
                        val parts = listOf(c[2] to cJamaa, c[1] to cOn, c[3] to cLate, c[4] to cMissed)
                        for ((n, col) in parts) {
                            if (n == 0) continue
                            val hh = (bottom - top) * n / tot
                            drawRect(col, Offset(x, y - hh), Size(bw, hh))
                            y -= hh
                        }
                    }
                    drawContext.canvas.nativeCanvas.drawText(p.ar, cx, h - 6.dp.toPx(), paint)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Pill("جماعة", cJamaa); Pill("في وقتها", cOn); Pill("متأخرة", cLate); Pill("فائتة", cMissed)
            }
        }
        PLHeatCard(store)
    }
}

@Composable
private fun PLHeatCard(store: WorshipStore) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    var ym by remember { mutableStateOf(YearMonth.from(env.today)) }
    val first = ym.atDay(1)
    // الأسبوع يبدأ الأحد
    val offset = first.dayOfWeek.value % 7
    val cells = offset + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    val dayNames = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")
    val monthNames = com.mawaqeet.app.astro.Calendars.gregorianMonths

    GlassCard(title = "خريطة الشهر", subtitle = "كلما زاد اللون زاد عدد الصلوات المؤدّاة") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "السابق", tint = o.ink) }
            Text("${monthNames[ym.monthValue - 1]} ${ym.year}", Modifier.weight(1f), color = o.ink, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "التالي", tint = o.ink) }
        }
        Row(Modifier.fillMaxWidth()) {
            dayNames.forEach { Text(it, Modifier.weight(1f), color = o.muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    val dayNum = idx - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val d = ym.atDay(dayNum)
                            val n = store.states(d).count { PrayerState.prayed(it) }
                            val miss = store.states(d).any { it == PrayerState.MISSED }
                            val bg = if (n == 0) o.muted.copy(alpha = 0.12f) else primary.copy(alpha = 0.2f + 0.16f * n)
                            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    drawRoundRect(bg, cornerRadius = CornerRadius(6.dp.toPx()))
                                    if (miss) drawRoundRect(o.danger, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                                    if (d == env.today) drawRoundRect(o.gold, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
                                }
                                Text(dayNum.toString(), color = o.ink, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── القضاء ─────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.plQada() {
    item { PLQadaCalc() }
    item { PLQadaCounters() }
    item { Note("يُستحب المبادرة بالقضاء؛ وفي تفاصيله خلاف بين المذاهب.") }
}

@Composable
private fun PLQadaCalc() {
    val env = LocalEnv.current
    val store = WorshipStore.get(LocalContext.current)
    var mode by remember { mutableStateOf("span") }
    var years by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf(env.today.toString()) }

    val total: Int? = if (mode == "span") {
        val y = years.toIntOrNull() ?: 0; val m = months.toIntOrNull() ?: 0; val d = days.toIntOrNull() ?: 0
        if (y < 0 || m < 0 || d < 0) null else (y * 365 + m * 30 + d).takeIf { it > 0 }
    } else {
        val f = runCatching { LocalDate.parse(from.trim()) }.getOrNull()
        val t = runCatching { LocalDate.parse(to.trim()) }.getOrNull()
        if (f != null && t != null && !t.isBefore(f)) (ChronoUnit.DAYS.between(f, t) + 1).toInt() else null
    }

    GlassCard(title = "حاسبة القضاء", subtitle = "قدّر عدد الصلوات المطلوب قضاؤها") {
        ChipsSelector(mapOf("span" to "مدة (سنوات/أشهر/أيام)", "dates" to "من تاريخ إلى تاريخ"), mode) { mode = it }
        Spacer(Modifier.height(8.dp))
        if (mode == "span") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("سنوات", years, { years = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), number = true)
                Field("أشهر", months, { months = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), number = true)
                Field("أيام", days, { days = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), number = true)
            }
        } else {
            Field("من (yyyy-MM-dd)", from, { from = it.take(10) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Field("إلى (yyyy-MM-dd)", to, { to = it.take(10) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        if (total == null) {
            Text("أدخل مدة أو تاريخين صحيحين.", color = AppTheme.ornament.muted, style = MaterialTheme.typography.bodySmall)
        } else {
            InfoRow("عدد الأيام (تقريباً)", total.toString())
            InfoRow("لكل صلاة", total.toString())
            InfoRow("المجموع", (total * 5).toString(), emphasize = true)
            Spacer(Modifier.height(8.dp))
            GlassButton(onClick = { store.addQadaAll(total) }, Modifier.fillMaxWidth(), nav = true) { Text("أضف إلى عداد القضاء") }
            Text("السنة محسوبة 365 يوماً والشهر 30 يوماً.", color = AppTheme.ornament.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PLQadaCounters() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val store = WorshipStore.get(LocalContext.current)
    store.version
    var perDay by remember { mutableIntStateOf(5) }
    val total = store.qadaTotal()

    GlassCard(title = "عدّاد القضاء المتبقي") {
        PL_PRAYERS.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.ar, Modifier.width(64.dp), color = o.ink, style = MaterialTheme.typography.titleSmall)
                Text(store.qada(i).toString(), Modifier.weight(1f), color = o.gold, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                GlassOutlinedButton(onClick = { store.addQada(i, -1) }, nav = true, enabled = store.qada(i) > 0, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("−1 صلّيت") }
                Spacer(Modifier.width(6.dp))
                GlassOutlinedButton(onClick = { store.addQada(i, 1) }, nav = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("+1") }
            }
        }
        Spacer(Modifier.height(6.dp))
        InfoRow("المجموع المتبقي", total.toString(), emphasize = true)
        if (total > 0) {
            Spacer(Modifier.height(6.dp))
            Text("عدد صلوات القضاء في اليوم: $perDay", color = o.ink, style = MaterialTheme.typography.bodyMedium)
            Slider(value = perDay.toFloat(), onValueChange = { perDay = Math.round(it).coerceIn(1, 30) }, valueRange = 1f..30f)
            val daysNeeded = (total + perDay - 1) / perDay
            InfoRow("المدة المقدّرة", "$daysNeeded يوماً")
            InfoRow("تاريخ الانتهاء المقدّر", env.gregorian(env.today.plusDays(daysNeeded.toLong())))
        }
    }
}
