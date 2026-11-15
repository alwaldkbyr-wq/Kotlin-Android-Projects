package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Crescent
import com.mawaqeet.app.astro.CrescentReport
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.PhaseKind
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OsmMap
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalDark
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private data class CrescentMapRun(val lat0: Int, val lon0: Int, val lon1: Int, val cls: Char)

private fun crescentClassColor(c: Char): Color = when (c) {
    'A' -> Color(0xFF2E9E4F)
    'B' -> Color(0xFF8BC34A)
    'C' -> Color(0xFFFFC107)
    'D' -> Color(0xFFFF9800)
    'E' -> Color(0xFFF4511E)
    else -> Color(0xFFD32F2F)
}

/** تصنيف عودة (A..D) إلى سلّم الألوان نفسه */
private fun crescentOdehColor(c: Char): Color = when (c) {
    'A' -> crescentClassColor('A')
    'B' -> crescentClassColor('B')
    'C' -> crescentClassColor('D')
    else -> crescentClassColor('F')
}

private val crescentLegend = listOf(
    'A' to "سهلة بالعين", 'B' to "بالعين في ظروف جيدة", 'C' to "قد تحتاج أداة",
    'D' to "بالأداة فقط", 'E' to "صعبة حتى بالأداة", 'F' to "لا تُرى"
)

/** رؤية الهلال: معياريّ يالوب وعودة وخريطة الرؤية العالمية */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrescentScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val s = env.settings
    val now = env.now

    val conj: Long = remember(env.today) {
        val prev = Phases.previous(PhaseKind.NEW, now)
        if (now - prev <= 3 * DAY_MS) prev else Phases.next(PhaseKind.NEW, now)
    }
    val conjDate = env.dateOf(conj)
    var mode by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf(0) }
    val evening = conjDate.plusDays(sel.toLong())

    val reports: List<CrescentReport?>? = rememberComputed(conj, s) {
        (0..2).map { Crescent.report(env.dayStart(conjDate.plusDays(it.toLong())), env.lat, env.lon) }
    }
    val rep: CrescentReport? = reports?.getOrNull(sel)

    val grid: List<CrescentMapRun>? = if (mode == 1) rememberComputed(conj, sel) {
        val utc = evening.toEpochDay() * DAY_MS
        val runs = ArrayList<CrescentMapRun>()
        var lat = -60
        while (lat < 60) {
            var curCls = '-'
            var runStart = -180
            var lon = -180
            while (lon < 180) {
                val c = Crescent.quickClass(utc, lat + 3.0, lon + 3.0, conj) ?: '-'
                if (c != curCls) {
                    if (curCls != '-' && lon > runStart) runs += CrescentMapRun(lat, runStart, lon, curCls)
                    curCls = c; runStart = lon
                }
                lon += 6
            }
            if (curCls != '-') runs += CrescentMapRun(lat, runStart, 180, curCls)
            lat += 6
        }
        runs
    } else null

    fun shareText(): String = buildString {
        appendLine("رؤية الهلال — ${env.place.name}")
        appendLine("الاقتران: ${env.dateTime(conj)}")
        appendLine("مساء ${env.gregorian(evening)}")
        if (rep != null) {
            appendLine("الغروب ${env.time(rep.sunset)} • غروب القمر ${env.time(rep.moonset)} • المكث ${Math.round(rep.lagMinutes)} د")
            appendLine("عمر القمر ${Fmt.num(rep.ageHours, 1)} س • الارتفاع ${Fmt.deg(rep.moonAlt)} • الاستطالة ${Fmt.deg(rep.elongation)}")
            appendLine("يالوب: ${rep.yallop} (q=${Fmt.num(rep.yallopQ, 3)}) — ${rep.yallopText}")
            appendLine("عودة: ${rep.odeh} (V=${Fmt.num(rep.odehV, 2)}) — ${rep.odehText}")
        }
        appendLine("تنبيه: توقعات فلكية فقط؛ الإعلان الرسمي يعتمد على الرؤية الفعلية والجهات المختصة.")
    }

    ScreenScaffold(title = "رؤية الهلال", onBack = env.back, subtitle = env.place.name, actions = { ShareAction { shareText() } }) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("التفاصيل") })
                GlassChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("خريطة الرؤية") })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { i ->
                    GlassChip(selected = sel == i, onClick = { sel = i }, label = { Text("المساء ${i + 1}") })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mode == 0) {
                    ScreenList {
                        item {
                            GlassCard(title = "الاقتران") {
                                InfoRow("لحظة الاقتران", env.dateTime(conj), emphasize = true)
                                InfoRow(if (conj > now) "بعد" else "مضى", Fmt.span(Math.abs(conj - now)))
                                InfoRow("مساء يوم", "${env.gregorian(evening)} • ${env.hijri(evening)}")
                            }
                        }
                        if (reports == null) item { Busy() }
                        else if (rep == null) item { Note("لا تغرب الشمس في هذه الليلة عند موقعك، فلا يُحسب تقرير الرؤية.") }
                        else {
                            item { CrescentDetails(rep) }
                        }
                        item {
                            Note("هذه توقعات فلكية فقط (معيار يالوب 1997 ومعيار عودة 2004)، والإعلان الرسمي عن بداية الشهر يعتمد على الرؤية الفعلية وعلى الجهات المختصة في بلدك.")
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            val overlays = remember(grid) {
                                grid?.map { r ->
                                    val c = crescentClassColor(r.cls)
                                    Polygon().apply {
                                        points = listOf(
                                            GeoPoint(r.lat0.toDouble(), r.lon0.toDouble()), GeoPoint(r.lat0 + 6.0, r.lon0.toDouble()),
                                            GeoPoint(r.lat0 + 6.0, r.lon1.toDouble()), GeoPoint(r.lat0.toDouble(), r.lon1.toDouble())
                                        )
                                        fillPaint.color = c.copy(alpha = if (r.cls == 'F') 0.22f else 0.5f).toArgb()
                                        outlinePaint.color = 0
                                        outlinePaint.strokeWidth = 0f
                                        setOnClickListener { _, _, _ -> false }
                                    }
                                }
                            }
                            val placeName = env.place.name
                            val myLat = env.lat
                            val myLon = env.lon
                            OsmMap(Modifier.fillMaxSize(), myLat, myLon, 2.6, dark = LocalDark.current) { map ->
                                if (overlays != null && map.tag !== overlays) {
                                    map.overlays.clear()
                                    map.overlays.addAll(overlays)
                                    map.overlays.add(Marker(map).apply {
                                        position = GeoPoint(myLat, myLon)
                                        title = placeName
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    })
                                    map.tag = overlays
                                }
                            }
                            if (grid == null) Busy(Modifier.align(Alignment.Center))
                        }
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                crescentLegend.forEach { (k, t) -> Pill("$k • $t", crescentClassColor(k)) }
                            }
                            Text(
                                "تصنيف يالوب للمساء ${sel + 1} (${env.gregorian(evening)}). كل شيء محسوب على هاتفك دون إنترنت.",
                                color = o.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrescentDetails(r: CrescentReport) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    Column(Modifier.fillMaxWidth()) {
    GlassCard(title = "الغروب والمكث") {
        MetricRow("غروب الشمس" to env.time(r.sunset), "غروب القمر" to env.time(r.moonset), "المكث" to "${Math.round(r.lagMinutes)} د")
        if (r.moonset == null) Text("لا يغرب القمر في هذه الليلة.", color = o.muted, style = MaterialTheme.typography.bodySmall)
    }
    GlassCard(title = "موضع الهلال عند وقت الرصد الأمثل", subtitle = "غروب الشمس + 4/9 المكث") {
        InfoRow("عمر القمر", "${Fmt.num(r.ageHours, 1)} ساعة")
        InfoRow("ارتفاع القمر", Fmt.deg(r.moonAlt))
        InfoRow("الاستطالة (ARCL)", Fmt.deg(r.elongation))
        InfoRow("فرق الارتفاع (ARCV)", Fmt.deg(r.arcv))
        InfoRow("فرق السمت (DAZ)", Fmt.deg(r.daz))
        InfoRow("عرض الهلال (W)", "${Fmt.num(r.width, 2)}′")
        InfoRow("نسبة الإضاءة", "${Fmt.num(r.illumination * 100, 2)}%")
    }
    GlassCard(title = "معيار يالوب") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("q = ${Fmt.num(r.yallopQ, 3)}", Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Pill("الفئة ${r.yallop}", crescentClassColor(r.yallop))
        }
        Spacer(Modifier.height(4.dp))
        Text(r.yallopText, color = o.muted, style = MaterialTheme.typography.bodyMedium)
    }
    GlassCard(title = "معيار عودة") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("V = ${Fmt.num(r.odehV, 2)}", Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Pill("الفئة ${r.odeh}", crescentOdehColor(r.odeh))
        }
        Spacer(Modifier.height(4.dp))
        Text(r.odehText, color = o.muted, style = MaterialTheme.typography.bodyMedium)
    }
}
}
