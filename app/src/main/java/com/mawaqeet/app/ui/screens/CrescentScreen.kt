package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Crescent
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.atan2d
import com.mawaqeet.app.astro.cosd
import com.mawaqeet.app.astro.norm180
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.astro.sind
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
import com.mawaqeet.app.ui.components.magneticDeclination
import com.mawaqeet.app.ui.components.rememberPointing
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.CanvasFonts
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
                            item { CrescentGuide(rep, conj) }
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

/* ───────────────────────── دليل تحديد موضع الهلال ───────────────────────── */

private class CrescentGuidePt(val t: Long, val alt: Double, val az: Double, val sunAlt: Double)

private class CrescentGuideData(
    val sunsetAz: Double,
    val best: CrescentGuidePt,
    val track: List<CrescentGuidePt>,
    /** اتجاه الحافة المضيئة على الشاشة: 0 أعلى، 90 يمين، 180 أسفل، 270 يسار (للراصد المواجه للجرم) */
    val brightAngle: Double,
    val bestIllum: Double
)

private const val CG_STEP = 5 * 60_000L

private fun crescentGuidePt(t: Long, lat: Double, lon: Double): CrescentGuidePt {
    val m = Ephemeris.horizontal(Body.MOON, t, lat, lon, withRefraction = true)
    val sn = Ephemeris.horizontal(Body.SUN, t, lat, lon, withRefraction = false)
    return CrescentGuidePt(t, m.alt, m.az, sn.alt)
}

private fun crescentGuideCompute(sunset: Long, moonset: Long?, best: Long, lat: Double, lon: Double, illum: Double): CrescentGuideData {
    val sunsetAz = Ephemeris.horizontal(Body.SUN, sunset, lat, lon, withRefraction = false).az
    val end = minOf(moonset ?: (sunset + 120 * 60_000L), sunset + 150 * 60_000L)
    val track = ArrayList<CrescentGuidePt>()
    var t = sunset
    while (t <= end) { track += crescentGuidePt(t, lat, lon); t += CG_STEP }
    // اتجاه الحافة المضيئة: السمت الكروي من القمر إلى الشمس في الإحداثيات الأفقية
    // (يعادل زاوية الموضع من الإحداثيات الاستوائية مطروحاً منها الزاوية المداريّة)
    val m = Ephemeris.horizontal(Body.MOON, best, lat, lon, withRefraction = false)
    val sn = Ephemeris.horizontal(Body.SUN, best, lat, lon, withRefraction = false)
    val dA = sn.az - m.az
    val theta = norm360(
        atan2d(sind(dA) * cosd(sn.alt), cosd(m.alt) * sind(sn.alt) - sind(m.alt) * cosd(sn.alt) * cosd(dA))
    )
    return CrescentGuideData(sunsetAz, crescentGuidePt(best, lat, lon), track, theta, illum)
}

/** تحويل الدرجات إلى عرض اليد على بعد الذراع: قبضة ≈ 10°، إصبع ≈ 1.5° */
private fun crescentHandText(deg: Double): String {
    val d = Math.abs(deg)
    if (d < 3.0) {
        return when (val n = Math.round(d / 1.5).toInt().coerceAtLeast(1)) { 1 -> "نحو إصبع"; 2 -> "نحو إصبعين"; else -> "نحو $n أصابع" }
    }
    val halves = Math.round(d / 5.0).toInt().coerceAtLeast(1)
    val whole = halves / 2
    val half = halves % 2 == 1
    val w = when (whole) { 0 -> ""; 1 -> "قبضة"; 2 -> "قبضتين"; else -> "$whole قبضات" }
    return "نحو " + when {
        whole == 0 -> "نصف قبضة"
        half -> "$w ونصف"
        else -> w
    }
}

@Composable
private fun CrescentGuide(r: CrescentReport, conj: Long) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val sunset = r.sunset ?: return
    val moonset = r.moonset
    val lag = r.lagMinutes
    val impossible = conj > sunset || (moonset != null && lag <= 0.0)
    val data: CrescentGuideData? = if (impossible) null else rememberComputed(sunset, moonset, env.lat, env.lon) {
        val best = sunset + ((if (lag > 0) lag else 0.0) * 4 / 9 * 60000).toLong()
        crescentGuideCompute(sunset, moonset, best, env.lat, env.lon, r.illumination)
    }

    Column(Modifier.fillMaxWidth()) {
        if (impossible) {
            GlassCard(title = "أين تنظر؟") {
                Text(
                    if (conj > sunset) "لم يقع الاقتران قبل غروب الشمس في هذا المساء، فرؤية الهلال مستحيلة فيه."
                    else "يغرب القمر قبل الشمس أو معها (المكث ${Math.round(lag)} د)، فرؤية الهلال مستحيلة في هذا المساء.",
                    color = o.danger, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text("جرّب المساء التالي من الأزرار في الأعلى.", color = o.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        } else if (data == null) {
            Busy()
        } else {
            CrescentWhereCard(r, data, sunset, moonset, lag)
            CrescentFinderCard(data, sunset, moonset)
        }
        CrescentTipsCard(impossible, lag)
    }
}

@Composable
private fun CrescentWhereCard(r: CrescentReport, d: CrescentGuideData, sunset: Long, moonset: Long?, lag: Double) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val dAz = norm180(d.best.az - d.sunsetAz)
    val side = if (dAz > 0) "يمين" else "يسار"
    val start = sunset + 10 * 60_000L
    GlassCard(title = "أين تنظر؟", subtitle = "مواجهاً نقطة الغروب") {
        InfoRow("نقطة غروب الشمس", "${Fmt.deg(d.sunsetAz)} • ${Fmt.direction(d.sunsetAz)}", emphasize = true)
        Text(
            (if (Math.abs(dAz) < 0.5) "انظر فوق نقطة الغروب مباشرة" else "انظر إلى $side نقطة الغروب بـ ${Fmt.deg(Math.abs(dAz))} (${crescentHandText(dAz)})") +
                " وفوق الأفق بـ ${Fmt.deg(d.best.alt)} (${crescentHandText(d.best.alt)}).",
            color = o.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text("القياس بذراع ممدودة: القبضة ≈ 10°، الكفّ المفتوح ≈ 15–20°، الإصبع ≈ 1–2°.", color = o.muted, style = MaterialTheme.typography.labelSmall)
        InfoRow("سمت القمر وقت الرصد الأمثل", "${Fmt.deg(d.best.az)} • ${Fmt.direction(d.best.az)}")
        InfoRow("ارتفاعه (مع الانكسار)", Fmt.deg(d.best.alt))
        OrnamentGap()
        Text("نافذة الرصد", color = o.gold, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        InfoRow("بداية الرصد (الغروب + 10 د)", env.time(start))
        InfoRow("أفضل وقت (الغروب + 4/9 المكث)", env.time(d.best.t), emphasize = true)
        InfoRow("غروب القمر (آخر فرصة)", if (moonset != null) env.time(moonset) else "لا يغرب")
        InfoRow("المكث بعد الغروب", "${Math.round(lag)} دقيقة")
        if (moonset != null && moonset <= start + 5 * 60_000L)
            Text("ينتهي الوقت قبل أن تُظلم السماء كفاية؛ الرصد بالعين شبه مستحيل ويلزم أداة.", color = o.warning, style = MaterialTheme.typography.bodySmall)

        OrnamentGap()
        Text("شكل الهلال وميله", color = o.gold, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val dev = norm180(d.brightAngle - 180.0)
        val tiltText = when {
            Math.abs(dev) < 5 -> "الحافة المضيئة إلى الأسفل مباشرة، والقرنان إلى الأعلى كابتسامة."
            dev < 0 -> "الحافة المضيئة نحو الأسفل واليمين (تميل ${Fmt.deg(Math.abs(dev), 0)} عن الرأسي)، والقرنان إلى أعلى ويسار."
            else -> "الحافة المضيئة نحو الأسفل واليسار (تميل ${Fmt.deg(Math.abs(dev), 0)} عن الرأسي)، والقرنان إلى أعلى ويمين."
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CrescentShape(d.brightAngle, d.bestIllum, Modifier.size(104.dp))
            Column(Modifier.weight(1f)) {
                Text(tiltText, color = o.ink, style = MaterialTheme.typography.bodyMedium)
                Text("القرنان يشيران إلى الأعلى بعيداً عن الشمس. الرسم تقريبي ومُكبَّر السمك لتوضيح الميل فقط.", color = o.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }

        OrnamentGap()
        Text("الارتفاع مع الزمن (كل 5 دقائق)", color = o.gold, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("الوقت", "ارتفاع القمر", "السمت", "الشمس تحت الأفق").forEach {
                Text(it, Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        d.track.forEach { p ->
            val dep = -p.sunAlt
            val good = dep in 4.0..6.0 && p.alt > 2.0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    .background(if (good) o.gold.copy(alpha = 0.20f) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp)
            ) {
                listOf(
                    env.time(p.t) + if (good) " ★" else "",
                    Fmt.deg(p.alt), Fmt.deg(p.az, 0), Fmt.deg(Math.max(dep, 0.0))
                ).forEach {
                    Text(it, Modifier.weight(1f), color = if (good) o.ink else o.muted, style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (good) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Text("★ الصفوف المظللة: الشمس بين 4° و6° تحت الأفق والقمر فوق 2° — الأنسب للرصد.", color = o.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun OrnamentGap() { Spacer(Modifier.height(10.dp)) }

@Composable
private fun CrescentShape(brightAngle: Double, illum: Double, modifier: Modifier) {
    val o = AppTheme.ornament
    val faint = o.muted.copy(alpha = 0.28f)
    val lit = Color(0xFFFFE9A8)
    val line = o.muted.copy(alpha = 0.5f)
    // مبالغة في السمك للوضوح
    val k = (illum * 6).coerceIn(0.10, 0.42).toFloat()
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val rad = size.minDimension / 2 - 8f
        drawLine(line, Offset(2f, size.height - 3f), Offset(size.width - 2f, size.height - 3f), strokeWidth = 2f)
        drawCircle(faint, radius = rad, center = c)
        rotate(degrees = (brightAngle - 90.0).toFloat(), pivot = c) {
            val rx = rad * (1 - 2 * k)
            val path = Path().apply {
                arcTo(Rect(c.x - rad, c.y - rad, c.x + rad, c.y + rad), -90f, 180f, true)
                arcTo(Rect(c.x - rx, c.y - rad, c.x + rx, c.y + rad), 90f, -180f, false)
                close()
            }
            drawPath(path, lit)
        }
        drawCircle(line, radius = rad, center = c, style = Stroke(width = 1.5f))
    }
}

@Composable
private fun CrescentFinderCard(d: CrescentGuideData, sunset: Long, moonset: Long?) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val pointing = rememberPointing().value
    val decl = remember(env.lat, env.lon) { magneticDeclination(env.lat, env.lon) }
    val heading = norm360(pointing.azimuthMagnetic + decl)
    val pAlt = pointing.altitude
    val avail = pointing.available

    val now = env.now
    val live = d.track.isNotEmpty() && now >= sunset && now <= d.track.last().t + CG_STEP
    val tgt = if (live) d.track.minByOrNull { Math.abs(it.t - now) } ?: d.best else d.best
    val dAz = norm180(tgt.az - heading)
    val dAlt = tgt.alt - pAlt

    var maxOff = 0.0
    var maxAlt = 0.0
    d.track.forEach { maxOff = Math.max(maxOff, Math.abs(norm180(it.az - d.sunsetAz))); maxAlt = Math.max(maxAlt, it.alt) }
    val half = Math.max(30.0, maxOff + 10.0).coerceAtMost(90.0)
    val altMax = Math.max(20.0, maxAlt + 6.0)
    val center = d.sunsetAz

    val ink = o.ink.toArgb()
    val mutedC = o.muted
    val gold = o.gold
    val info = o.info
    val sunColor = o.warning
    val txt = remember(ink) {
        android.graphics.Paint().apply {
            color = ink; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            typeface = CanvasFonts.text
        }
    }

    GlassCard(title = "وجّه هاتفك", subtitle = "شريط الأفق حول نقطة الغروب") {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val w = size.width
            val h = size.height
            val padB = 30f
            val padT = 10f
            fun xOf(az: Double) = (w / 2 + norm180(az - center) / half * (w / 2 - 10f)).toFloat()
            fun yOf(alt: Double) = (h - padB - alt / altMax * (h - padB - padT)).toFloat()
            // الأرض والأفق
            drawRect(mutedC.copy(alpha = 0.18f), topLeft = Offset(0f, yOf(0.0)), size = androidx.compose.ui.geometry.Size(w, h - yOf(0.0)))
            drawLine(mutedC, Offset(0f, yOf(0.0)), Offset(w, yOf(0.0)), strokeWidth = 2f)
            // تدريج السمت كل 10°
            var k = -Math.floor(half / 10.0) * 10.0
            while (k <= half) {
                val x = xOf(center + k)
                drawLine(mutedC.copy(alpha = 0.5f), Offset(x, yOf(0.0)), Offset(x, yOf(0.0) + 8f), strokeWidth = 1.5f)
                drawContext.canvas.nativeCanvas.drawText(Math.round(norm360(center + k)).toInt().toString(), x, h - 6f, txt)
                k += 10.0
            }
            // مسار القمر
            d.track.forEachIndexed { i, p ->
                if (p.alt > -1.0) drawCircle(if (p.t == d.best.t) gold else mutedC, radius = if (p.t == d.best.t) 9f else 4.5f, center = Offset(xOf(p.az), yOf(Math.max(p.alt, 0.0))),
                    style = if (p.t == d.best.t) Stroke(3f) else androidx.compose.ui.graphics.drawscope.Fill)
                if (i == 0) drawContext.canvas.nativeCanvas.drawText("☾", xOf(p.az), yOf(Math.max(p.alt, 0.0)) - 14f, txt)
            }
            // الشمس عند نقطة الغروب
            drawCircle(sunColor, radius = 10f, center = Offset(xOf(center), yOf(0.0)))
            // خط اتجاه الهاتف وارتفاعه
            if (avail) {
                val off = norm180(heading - center)
                val hx = xOf(heading)
                val a = if (Math.abs(off) > half) 0.45f else 1f
                drawLine(info.copy(alpha = a), Offset(hx, padT), Offset(hx, yOf(0.0)), strokeWidth = 4f)
                if (pAlt in 0.0..altMax) drawLine(info.copy(alpha = 0.6f), Offset(hx - 22f, yOf(pAlt)), Offset(hx + 22f, yOf(pAlt)), strokeWidth = 3f)
            }
        }
        Text("● الشمس عند الغروب   ○ موضع الهلال كل 5 دقائق (الذهبي: الأمثل)   ┃ اتجاه ظهر الهاتف", color = o.muted, style = MaterialTheme.typography.labelSmall)
        if (!avail) {
            Text("حساسات الاتجاه غير متوفرة في هذا الجهاز؛ استعمل الأرقام أعلاه والبوصلة.", color = o.warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        } else {
            InfoRow("اتجاه الهاتف الآن", "${Fmt.deg(heading, 0)} • ارتفاع ${Fmt.deg(pAlt, 0)}")
            val ok = Math.abs(dAz) < 3 && Math.abs(dAlt) < 3
            Text(
                (if (live) "موضع الهلال الآن" else "موضع الهلال وقت الرصد الأمثل") + ": " +
                    if (ok) "الهلال أمامك تقريباً — جرّب المنظار." else
                        "${if (dAz > 0) "إلى يمينك" else "إلى يسارك"} بـ ${Fmt.deg(Math.abs(dAz), 0)} ${if (dAlt > 0) "وأعلى" else "وأخفض"} بـ ${Fmt.deg(Math.abs(dAlt), 0)}.",
                color = if (ok) o.success else o.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text("أدر الهاتف حتى يتطابق الخط مع موضع الهلال. أمسك الهاتف قائماً وظهره نحو الأفق، وابتعد عن المعادن والمغانط لدقة البوصلة. الانحراف المغناطيسي مصحَّح (${Fmt.deg(decl)}).",
            color = o.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CrescentTipsCard(impossible: Boolean, lag: Double) {
    val o = AppTheme.ornament
    val tips = listOf(
        "اختر أفقاً غربياً مكشوفاً ومنبسطاً بعيداً عن أضواء المدينة والغبار؛ والمكان المرتفع أفضل.",
        "احضر قبل الغروب وحدّد نقطة الغروب. لا تنظر إلى الشمس أبداً، ولا توجّه المنظار أو التلسكوب قرب الشمس قبل أن تغيب تماماً.",
        "ابدأ المسح ببطء بالمنظار على بعد بضع درجات فوق الأفق، يمين نقطة الغروب أو يسارها كما حُسب أعلاه. وإذا وجدته فجرّب بالعين المجردة.",
        "الهلال رفيع جداً وخافت وقريب من الأفق وسط وهج الشفق؛ والغبش والرطوبة يقلّلان الرؤية، والدقائق الأولى بعد الغروب غالباً ساطعة جداً.",
        "دوّن وقت الرؤية وظروفها (الصفاء، الغيوم، الأداة المستعملة). وقد تلتبس الزهرة أو كوكب لامع آخر بالهلال.",
        "هذه حسابات فلكية؛ والقرار في بداية الشهر يعود إلى الجهات المختصة في كل بلد."
    )
    GlassCard(title = "نصائح الرصد") {
        if (impossible) Text("الرصد مستحيل في هذا المساء بحسب الحسابات${if (lag <= 0) " (المكث ${Math.round(lag)} د)" else ""}؛ النصائح أدناه للمساء الذي يليه.",
            color = o.danger, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        tips.forEach { t ->
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = o.gold, style = MaterialTheme.typography.bodyMedium)
                Text(t, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
