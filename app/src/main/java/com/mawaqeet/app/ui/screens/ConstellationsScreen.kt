package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.Constellation
import com.mawaqeet.app.astro.StarCatalog
import com.mawaqeet.app.astro.atan2d
import com.mawaqeet.app.astro.asind
import com.mawaqeet.app.astro.eclToEq
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassAlertDialog
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OrnateTabs
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private val conZodiac = setOf(
    "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpius", "Sagittarius", "Capricornus", "Aquarius", "Pisces"
)

private class ConRow(
    val idx: Int,
    val c: Constellation,
    val alt: Double,
    val maxAlt: Double,
    val month: Int,
    val zodiac: Boolean
)

@Composable
fun ConstellationsScreen() {
    val env = LocalEnv.current
    val cons = StarCatalog.constellations
    val minute = (env.now / 60000L) * 60000L
    val frame = remember(minute, env.lat, env.lon) { SkyMath.frame(minute, env.lat, env.lon, env.place.heightM) }
    val months = if (env.settings.maghrebMonths) Calendars.maghrebMonths else Calendars.gregorianMonths
    val rows = remember(frame) {
        cons.mapIndexed { i, c ->
            ConRow(i, c, frame.conLabelAlt[i], 90.0 - abs(env.lat - c.labelDec), SkyMath.bestMonth(c.labelRa), c.latin in conZodiac)
        }
    }
    var tab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }

    ScreenScaffold(
        title = "خريطة الكوكبات", onBack = env.back, subtitle = "${cons.size} كوكبة",
        actions = {
            ShareAction {
                "الكوكبات فوق الأفق الآن في ${env.place.name} (${env.dateTime(minute)}):\n" +
                    rows.filter { it.alt > 0 }.sortedByDescending { it.alt }
                        .joinToString("\n") { "• ${it.c.ar} (${it.c.latin}) — ارتفاع ${Fmt.deg(it.alt, 0)}" }
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            OrnateTabs(listOf("القائمة", "خريطة السماء كاملة"), tab) { tab = it }
            if (tab == 0) {
                val shown = when (filter) {
                    1 -> rows.filter { it.alt > 0 }.sortedByDescending { it.alt }
                    2 -> rows.filter { it.zodiac }
                    else -> rows.sortedByDescending { it.alt }
                }
                ScreenList(Modifier.weight(1f)) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            GlassChip(filter == 0, { filter = 0 }, { Text("الكل") })
                            GlassChip(filter == 1, { filter = 1 }, { Text("فوق الأفق الآن") })
                            GlassChip(filter == 2, { filter = 2 }, { Text("الأبراج الاثنا عشر") })
                        }
                    }
                    items(shown, key = { it.idx }) { r ->
                        val o = AppTheme.ornament
                        GlassCard(onClick = { selected = r.idx }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.c.ar, style = MaterialTheme.typography.titleMedium, color = o.ink)
                                    Text(r.c.latin, style = MaterialTheme.typography.labelMedium, color = o.muted)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    when {
                                        r.maxAlt < 3 -> Pill("لا تُرى من موقعك", o.danger)
                                        r.alt > 0 -> Pill("فوق الأفق ${Fmt.deg(r.alt, 0)}", o.success)
                                        else -> Pill("تحت الأفق", o.muted)
                                    }
                                    if (r.zodiac) Pill("برج", o.gold, Modifier.padding(top = 4.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "أفضل رؤية: ${months[r.month - 1]}  •  أقصى ارتفاع ${Fmt.deg(r.maxAlt.coerceAtLeast(0.0), 0)}",
                                style = MaterialTheme.typography.bodySmall, color = o.muted
                            )
                        }
                    }
                    item { Note("أفضل شهر: حين تعبر الكوكبة خط الزوال نحو الساعة 21:00 بالتوقيت الشمسي المحلي. اضغط على كوكبة لعرض رسمها وأسطع نجومها.") }
                }
            } else {
                ConSkyChart(frame, { selected = it }, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    selected?.let { idx -> ConDialog(rows[idx], frame.conLabelAz[idx], months) { selected = null } }
}

/* ───────────────────── نافذة الكوكبة ───────────────────── */

private class ChartDot(val x: Float, val y: Float, val mag: Double, val name: String?)
private class ConChart(val segs: List<FloatArray>, val dots: List<ChartDot>, val named: List<Pair<String, Double>>, val ra0: Double, val dec0: Double)

private fun buildConChart(c: Constellation): ConChart {
    val stars = StarCatalog.stars
    // مركز الكوكبة: متوسط المتجهات
    var sx = 0.0; var sy = 0.0; var sz = 0.0
    for (ln in c.lines) for (k in 0 until ln.size / 2) {
        val v = SkyMath.unit(ln[2 * k], ln[2 * k + 1]); sx += v[0]; sy += v[1]; sz += v[2]
    }
    val ra0 = (atan2d(sy, sx) + 360.0) % 360.0
    val dec0 = asind(sz / sqrt(sx * sx + sy * sy + sz * sz))
    val tmp = DoubleArray(2)
    val raw = ArrayList<DoubleArray>() // x1,y1,x2,y2
    val pts = HashMap<Long, DoubleArray>() // vertex key -> x,y,ra,dec
    var minX = 1e9; var maxX = -1e9; var minY = 1e9; var maxY = -1e9
    fun proj(ra: Double, dec: Double): DoubleArray? {
        if (!SkyMath.gnomonic(ra, dec, ra0, dec0, tmp)) return null
        val x = -tmp[0]; val y = -tmp[1] // الشرق على اليسار والشمال للأعلى
        minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
        return doubleArrayOf(x, y)
    }
    for (ln in c.lines) {
        var prev: DoubleArray? = null
        for (k in 0 until ln.size / 2) {
            val ra = ln[2 * k]; val dec = ln[2 * k + 1]
            val p = proj(ra, dec)
            if (p != null) {
                pts[(ra * 1000).toLong() * 100000L + (dec * 1000).toLong()] = doubleArrayOf(p[0], p[1], ra, dec)
                if (prev != null) raw.add(doubleArrayOf(prev[0], prev[1], p[0], p[1]))
            }
            prev = p
        }
    }
    val span = max(max(maxX - minX, maxY - minY), 1e-6)
    val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
    fun nx(x: Double) = (0.5 + (x - cx) / span * 0.80).toFloat()
    fun ny(y: Double) = (0.5 + (y - cy) / span * 0.80).toFloat()
    val segs = raw.map { floatArrayOf(nx(it[0]), ny(it[1]), nx(it[2]), ny(it[3])) }
    val dots = ArrayList<ChartDot>()
    val named = ArrayList<Pair<String, Double>>()
    for (p in pts.values) {
        val st = stars.firstOrNull { abs(it.ra - p[2]) < 0.06 && abs(it.dec - p[3]) < 0.06 }
        dots.add(ChartDot(nx(p[0]), ny(p[1]), st?.mag ?: 4.6, st?.ar?.substringBefore(" (")))
        if (st != null) named.add(st.ar to st.mag)
    }
    // نُسمّي أسطع 6 نجوم فقط على الرسم
    val keep = dots.filter { it.name != null }.sortedBy { it.mag }.take(6).toSet()
    val finalDots = dots.map { if (it.name != null && it !in keep) ChartDot(it.x, it.y, it.mag, null) else it }
    return ConChart(segs, finalDots, named.sortedBy { it.second }, ra0, dec0)
}

@Composable
private fun ConDialog(r: ConRow, az: Double, months: List<String>, onClose: () -> Unit) {
    val o = AppTheme.ornament
    val chart = remember(r.idx) { buildConChart(r.c) }
    GlassAlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } },
        title = { Text(r.c.ar) },
        text = {
            Column {
                Text(r.c.latin, color = o.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val lineC = Color(0xFF9DB8E8)
                val goldArgb = o.gold.toArgb()
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF060E1E))
                ) {
                    val dp = 1.dp.toPx()
                    val w = size.width; val h = size.height
                    for (s in chart.segs) {
                        drawLine(lineC.copy(alpha = 0.75f), Offset(s[0] * w, s[1] * h), Offset(s[2] * w, s[3] * h), 1.6f * dp)
                    }
                    for (d in chart.dots) {
                        val p = Offset(d.x * w, d.y * h)
                        val rad = (1.4 + (4.8 - d.mag).coerceAtLeast(0.0) * 0.9).toFloat() * dp
                        if (d.mag < 2.0) drawCircle(Color.White.copy(alpha = 0.18f), rad * 2.4f, p)
                        drawCircle(Color.White, rad, p)
                        if (d.name != null) skyText(d.name, p.x, p.y + rad + 13f * dp, 11.5f * dp, goldArgb)
                    }
                    skyText("ش", w / 2, 16f * dp, 12f * dp, Color.White.copy(alpha = 0.45f).toArgb())
                    skyText("ق", 12f * dp, h / 2 + 4f * dp, 12f * dp, Color.White.copy(alpha = 0.45f).toArgb())
                    skyText("غ", w - 12f * dp, h / 2 + 4f * dp, 12f * dp, Color.White.copy(alpha = 0.45f).toArgb())
                }
                Spacer(Modifier.height(8.dp))
                InfoRow("الحالة الآن", if (r.alt > 0) "فوق الأفق (${Fmt.deg(r.alt, 0)})" else "تحت الأفق", valueColor = if (r.alt > 0) o.success else o.muted)
                if (r.alt > 0) InfoRow("الاتجاه", Fmt.direction(az))
                InfoRow("أفضل شهر للرؤية", months[r.month - 1])
                InfoRow("أقصى ارتفاع في موقعك", if (r.maxAlt < 0) "لا تظهر" else Fmt.deg(r.maxAlt, 0))
                InfoRow("المطلع المستقيم / الميل", "${Fmt.num(r.c.labelRa / 15.0, 1)}h  /  ${Fmt.deg(r.c.labelDec, 0)}")
                if (r.zodiac) InfoRow("النوع", "برج على دائرة البروج", valueColor = o.gold)
                if (chart.named.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("أسطع النجوم", color = o.gold, style = MaterialTheme.typography.titleSmall)
                    for ((n, m) in chart.named.take(6)) InfoRow(n, "قدر ${Fmt.num(m, 2)}")
                }
            }
        }
    )
}

/* ───────────────────── خريطة السماء كاملة (RA/Dec) ───────────────────── */

@Composable
private fun ConSkyChart(frame: SkyFrame, onOpen: (Int) -> Unit, modifier: Modifier) {
    val o = AppTheme.ornament
    val cons = StarCatalog.constellations
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    val goldC = o.gold
    val inkArgb = o.ink.toArgb()
    val goldArgb = o.gold.toArgb()
    // البروج (خط البعد 0) وحزام الأبراج ±8°
    val ecl = remember {
        Array(121) { i -> val e = eclToEq(i * 3.0, 0.0, 23.44); doubleArrayOf(e.ra, e.dec) }
    }
    val band = remember {
        ArrayList<DoubleArray>().also { l ->
            for (lon in 0 until 360 step 4) {
                val a = eclToEq(lon.toDouble(), 8.0, 23.44); val b = eclToEq(lon + 4.0, 8.0, 23.44)
                val c = eclToEq(lon + 4.0, -8.0, 23.44); val d = eclToEq(lon.toDouble(), -8.0, 23.44)
                l.add(doubleArrayOf(a.ra, a.dec, b.ra, b.dec, c.ra, c.dec, d.ra, d.dec))
            }
        }
    }
    Column(modifier) {
        Canvas(
            Modifier.weight(1f).fillMaxWidth().clipToBounds()
                .onSizeChanged { chartSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panDelta, zoom, _ ->
                        val w = chartSize.width.toFloat(); val h = chartSize.height.toFloat()
                        val ns = (scale * zoom).coerceIn(1f, 10f)
                        val k = ns / scale
                        val c = Offset(w / 2f, h / 2f)
                        var np = (centroid - c) - ((centroid - c) - pan) * k + panDelta
                        val limX = w * ns / 2f; val limY = w * ns / 4f + h / 4f
                        np = Offset(np.x.coerceIn(-limX, limX), np.y.coerceIn(-limY, limY))
                        scale = ns
                        pan = np
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = chartSize.width.toFloat(); val h = chartSize.height.toFloat()
                        val wc = w * scale; val hc = wc / 2f
                        val ox = w / 2f + pan.x - wc / 2f; val oy = h / 2f + pan.y - hc / 2f
                        var best = -1; var bd = Float.MAX_VALUE
                        for (i in cons.indices) {
                            val p = Offset(ox + ((360.0 - cons[i].labelRa) / 360.0 * wc).toFloat(), oy + ((90.0 - cons[i].labelDec) / 180.0 * hc).toFloat())
                            val d = (p - tap).getDistance()
                            if (d < bd) { bd = d; best = i }
                        }
                        if (best >= 0 && bd < 40f * density) onOpen(best)
                    }
                }
        ) {
            val dp = 1.dp.toPx()
            val w = size.width; val h = size.height
            val wc = w * scale; val hc = wc / 2f
            val ox = w / 2f + pan.x - wc / 2f; val oy = h / 2f + pan.y - hc / 2f
            fun sx(ra: Double) = ox + ((360.0 - ra) / 360.0 * wc).toFloat()
            fun sy(dec: Double) = oy + ((90.0 - dec) / 180.0 * hc).toFloat()

            drawRect(Color(0xFF060E1E), Offset(ox, oy), Size(wc, hc))
            // الشبكة
            val grid = Color.White.copy(alpha = 0.08f)
            for (hh in 0..24 step 2) {
                val x = sx(hh * 15.0)
                drawLine(grid, Offset(x, oy), Offset(x, oy + hc), 1f * dp)
                skyText("${hh}h", x, oy.coerceAtLeast(0f) + 12f * dp, 10f * dp, Color.White.copy(alpha = 0.5f).toArgb())
            }
            for (d in -60..60 step 30) {
                val y = sy(d.toDouble())
                drawLine(if (d == 0) Color(0xFF7FD6E8).copy(alpha = 0.35f) else grid, Offset(ox, y), Offset(ox + wc, y), (if (d == 0) 1.4f else 1f) * dp)
                skyText("${d}°", (ox + 16f * dp).coerceAtLeast(16f * dp), y - 3f * dp, 10f * dp, Color.White.copy(alpha = 0.5f).toArgb())
            }
            // حزام الأبراج
            val bandPath = Path()
            for (q in band) {
                if (abs(q[0] - q[2]) > 180 || abs(q[0] - q[4]) > 180 || abs(q[0] - q[6]) > 180) continue
                bandPath.moveTo(sx(q[0]), sy(q[1])); bandPath.lineTo(sx(q[2]), sy(q[3]))
                bandPath.lineTo(sx(q[4]), sy(q[5])); bandPath.lineTo(sx(q[6]), sy(q[7])); bandPath.close()
            }
            drawPath(bandPath, goldC.copy(alpha = 0.13f))
            // دائرة البروج
            val ep = Path()
            for (i in ecl.indices) {
                val x = sx(ecl[i][0]); val y = sy(ecl[i][1])
                if (i == 0 || abs(ecl[i][0] - ecl[i - 1][0]) > 180) ep.moveTo(x, y) else ep.lineTo(x, y)
            }
            drawPath(ep, goldC.copy(alpha = 0.9f), style = Stroke(1.5f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * dp, 6f * dp))))
            // خطوط الكوكبات
            val lp = Path()
            for (c in cons) for (ln in c.lines) for (k in 1 until ln.size / 2) {
                if (abs(ln[2 * k] - ln[2 * k - 2]) > 180) continue
                lp.moveTo(sx(ln[2 * k - 2]), sy(ln[2 * k - 1])); lp.lineTo(sx(ln[2 * k]), sy(ln[2 * k + 1]))
            }
            drawPath(lp, Color(0xFF9DB8E8).copy(alpha = 0.55f), style = Stroke(1.1f * dp))
            // النجوم
            val zk = sqrt(scale)
            for (st in StarCatalog.stars) {
                val r = ((0.7 + (4.2 - st.mag).coerceAtLeast(0.0) * 0.55) * zk).toFloat() * dp
                val p = Offset(sx(st.ra), sy(st.dec))
                drawCircle(Color(SkyMath.starColor(st.latin)), r, p)
                if (scale > 2.4f && st.mag < 2.1) skyText(st.ar.substringBefore(" ("), p.x, p.y + r + 10f * dp, 9f * dp, Color.White.copy(alpha = 0.7f).toArgb())
            }
            // أسماء الكوكبات (الأبراج بالذهبي)
            for (c in cons) {
                val zod = c.latin in conZodiac
                skyText(c.ar, sx(c.labelRa), sy(c.labelDec), (if (zod) 12.5f else 11f) * dp, if (zod) goldArgb else Color(0xFFD5E0FA).copy(alpha = 0.85f).toArgb(), bold = zod)
            }
            // الشمس والقمر والكواكب الآن
            for (b in Body.values()) {
                val bp = frame.body(b)
                val p = Offset(sx(bp.ra), sy(bp.dec))
                val col = skyBodyColor(b)
                drawCircle(col, (if (b == Body.SUN || b == Body.MOON) 6f else 3.6f) * dp, p)
                if (b == Body.SUN || b == Body.MOON || scale > 1.6f) skyText(b.ar, p.x, p.y - 8f * dp, 10.5f * dp, col.toArgb(), bold = true)
            }
            drawRect(o.gold.copy(alpha = 0.5f), Offset(ox, oy), Size(wc, hc), style = Stroke(1.2f * dp))
        }
        Text(
            "المحور الأفقي: المطلع المستقيم (24h ← 0h)، الرأسي: الميل. الشريط الذهبي حزام الأبراج حول دائرة البروج. قرّب بإصبعين واضغط اسم كوكبة لتفاصيلها.",
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = o.muted, style = MaterialTheme.typography.bodySmall
        )
    }
}
