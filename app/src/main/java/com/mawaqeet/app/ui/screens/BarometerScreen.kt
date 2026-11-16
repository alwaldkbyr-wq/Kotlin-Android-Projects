package com.mawaqeet.app.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberGpsFix
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.CanvasFonts
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

private const val BARO_PREFS = "baro"
private const val BARO_KEEP_MS = 48L * 3600_000L
private const val BARO_SAMPLE_MS = 10L * 60_000L

/** قراءة ضغط: (الوقت بالميلي ثانية، الضغط hPa) */
private data class BaroSample(val t: Long, val p: Float)

private fun baroLoad(ctx: Context): List<BaroSample> {
    val raw = ctx.getSharedPreferences(BARO_PREFS, Context.MODE_PRIVATE).getString("h", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    return raw.split(";").mapNotNull { part ->
        val i = part.indexOf(':')
        if (i <= 0) null else {
            val t = part.substring(0, i).toLongOrNull()
            val p = part.substring(i + 1).toFloatOrNull()
            if (t == null || p == null) null else BaroSample(t, p)
        }
    }.sortedBy { it.t }
}

/** يضيف قراءة (بفاصل دقيقة على الأقل عن الأخيرة) ويحذف ما هو أقدم من 48 ساعة */
private fun baroAdd(ctx: Context, t: Long, p: Float): List<BaroSample> {
    val list = baroLoad(ctx).filter { t - it.t <= BARO_KEEP_MS && it.t <= t }.toMutableList()
    if (list.isEmpty() || t - list.last().t >= 60_000L) list.add(BaroSample(t, p))
    val s = list.joinToString(";") { "${it.t}:${"%.2f".format(Locale.US, it.p)}" }
    ctx.getSharedPreferences(BARO_PREFS, Context.MODE_PRIVATE).edit().putString("h", s).apply()
    return list
}

/** «الضغط والارتفاع»: مقياس ضغط حي وتقدير الارتفاع واتجاه الضغط خلال 3 ساعات */
@Composable
fun BarometerScreen() {
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val o = AppTheme.ornament
    val now = env.now

    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_PRESSURE) }

    var smoothed by remember { mutableStateOf<Float?>(null) }
    var history by remember { mutableStateOf(baroLoad(ctx)) }
    var seaRef by remember { mutableStateOf(ctx.getSharedPreferences(BARO_PREFS, Context.MODE_PRIVATE).getFloat("ref", SensorManager.PRESSURE_STANDARD_ATMOSPHERE)) }
    val gps = rememberGpsFix(5000L).value

    // الاستماع للحساس مع تنعيم أسّي
    DisposableEffect(sensor) {
        if (sensor == null) return@DisposableEffect onDispose { }
        val acc = FloatArray(1) { Float.NaN }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values[0]
                acc[0] = if (acc[0].isNaN()) v else acc[0] + 0.12f * (v - acc[0])
                smoothed = acc[0]
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }

    // تسجيل قراءة عند الفتح ثم كل 10 دقائق ما دامت الشاشة مفتوحة
    val latest = rememberUpdatedState(smoothed)
    LaunchedEffect(sensor) {
        if (sensor == null) return@LaunchedEffect
        while (true) {
            var tries = 0
            while (latest.value == null && tries < 40) { delay(500L); tries++ }
            val v = latest.value
            if (v != null) history = baroAdd(ctx, System.currentTimeMillis(), v)
            delay(BARO_SAMPLE_MS)
        }
    }

    fun saveRef(v: Float) {
        val c = v.coerceIn(900f, 1100f)
        seaRef = c
        ctx.getSharedPreferences(BARO_PREFS, Context.MODE_PRIVATE).edit().putFloat("ref", c).apply()
    }

    val p = smoothed
    val altStd = if (p != null) SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, p).toDouble() else null
    val altRef = if (p != null) SensorManager.getAltitude(seaRef, p).toDouble() else null
    val gpsAlt = gps?.altitude

    // اتجاه الضغط خلال 3 ساعات (يُقدَّر من أقرب قراءة عمرها بين ساعة ونصف و4 ساعات)
    val tendency: Double? = remember(history, p, now / 60_000L) {
        if (p == null) null else {
            val t = System.currentTimeMillis()
            val ref = history.filter { t - it.t in 90 * 60_000L..4 * 3600_000L }
                .minByOrNull { abs((t - it.t) - 3 * 3600_000L) }
            if (ref == null) null else {
                val hours = (t - ref.t) / 3600_000.0
                (p - ref.p) / hours * 3.0
            }
        }
    }

    val tendencyTitle: String
    val tendencyBody: String
    val tendencyColor: Color
    val tv = tendency
    when {
        tv == null -> {
            tendencyTitle = "لا توجد بيانات كافية بعد"
            tendencyBody = "يلزم قراءة عمرها بين ساعة ونصف و4 ساعات لحساب الاتجاه. أبقِ هذه الشاشة مفتوحة قليلاً أو افتحها لاحقاً؛ تُسجَّل قراءة عند كل فتح وكل 10 دقائق."
            tendencyColor = o.muted
        }
        tv < -2.0 -> {
            tendencyTitle = "انخفاض سريع"
            tendencyBody = "انخفاض سريع: احتمال رياح قوية أو عاصفة رملية أو تقلب الطقس — احتمِ مبكراً."
            tendencyColor = o.danger
        }
        tv < -1.0 -> {
            tendencyTitle = "انخفاض تدريجي"
            tendencyBody = "الضغط ينخفض ببطء: قد يتغير الطقس خلال ساعات؛ تابع القراءات وجهّز حاجاتك."
            tendencyColor = o.warning
        }
        tv > 1.0 -> {
            tendencyTitle = "ارتفاع (تحسن)"
            tendencyBody = "تحسّن: الضغط يرتفع، والطقس يميل إلى الصفاء والاستقرار. (الارتفاع السريع قد يصاحبه رياح باردة)."
            tendencyColor = o.success
        }
        else -> {
            tendencyTitle = "مستقر"
            tendencyBody = "استقرار: الضغط ثابت تقريباً والطقس الحالي غالباً مستمر."
            tendencyColor = o.success
        }
    }

    val shareText = buildString {
        appendLine("الضغط والارتفاع")
        if (p != null) {
            appendLine("الضغط: ${Fmt.num(p.toDouble(), 1)} hPa")
            if (altRef != null) appendLine("الارتفاع التقديري (مرجع ${Fmt.num(seaRef.toDouble(), 1)} hPa): ${Fmt.num(altRef, 0)} م")
            if (tv != null) appendLine("اتجاه 3 ساعات: ${(if (tv >= 0) "+" else "−")}${Fmt.num(abs(tv), 1)} hPa — $tendencyTitle")
        }
        if (gpsAlt != null) appendLine("ارتفاع GPS: ${Fmt.num(gpsAlt, 0)} م")
    }

    ScreenScaffold(title = "الضغط والارتفاع", onBack = env.back, subtitle = "مقياس الضغط الجوي في هاتفك", actions = { ShareAction { shareText } }) {
        ScreenList {
            if (sensor == null) {
                item {
                    GlassCard(title = "لا يوجد حساس ضغط") {
                        NoteText("هذا الجهاز لا يحتوي على حساس ضغط جوي (barometer)، لذلك لا يمكن قياس الضغط ولا اتجاه الطقس منه. يمكنك الاعتماد على ارتفاع GPS أدناه.")
                    }
                }
            } else {
                item {
                    GlassCard(title = "الضغط الآن") {
                        if (p == null) {
                            NoteText("جارٍ انتظار قراءة الحساس…")
                        } else {
                            MetricRow(
                                "الضغط (hPa)" to Fmt.num(p.toDouble(), 1),
                                "الارتفاع التقديري" to (if (altRef != null) Fmt.num(altRef, 0) + " م" else "--")
                            )
                            Spacer(Modifier.height(6.dp))
                            InfoRow("الارتفاع بالمرجع القياسي 1013.25", if (altStd != null) Fmt.num(altStd, 0) + " م" else "--")
                            InfoRow("الارتفاع بمرجعك (${Fmt.num(seaRef.toDouble(), 1)} hPa)", if (altRef != null) Fmt.num(altRef, 0) + " م" else "--")
                        }
                    }
                }
                item {
                    GlassCard(title = "ضغط مستوى البحر (المرجع)") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            GlassTonalButton(onClick = { saveRef(seaRef - 1f) }, nav = true) { Text("−1") }
                            GlassTonalButton(onClick = { saveRef(seaRef - 0.1f) }, nav = true) { Text("−0.1") }
                            Text(Fmt.num(seaRef.toDouble(), 1), Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleMedium, color = o.ink, fontWeight = FontWeight.Black)
                            GlassTonalButton(onClick = { saveRef(seaRef + 0.1f) }, nav = true) { Text("+0.1") }
                            GlassTonalButton(onClick = { saveRef(seaRef + 1f) }, nav = true) { Text("+1") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassOutlinedButton(
                                onClick = {
                                    val h = gpsAlt
                                    val pp = smoothed
                                    if (h != null && pp != null) {
                                        val base = 1.0 - h / 44330.0
                                        if (base > 0) saveRef((pp / base.pow(5.255)).toFloat())
                                    }
                                },
                                enabled = gpsAlt != null && p != null,
                                nav = true
                            ) { Text("معايرة بالارتفاع من GPS") }
                            GlassOutlinedButton(onClick = { saveRef(SensorManager.PRESSURE_STANDARD_ATMOSPHERE) }, nav = true) { Text("القياسي") }
                        }
                        NoteText("أدخل ضغط مستوى البحر المعلن لمنطقتك إن عرفته، أو عاير من ارتفاع معروف (GPS)، فيصبح تقدير الارتفاع أدق.")
                    }
                }
                item {
                    GlassCard(title = "اتجاه الضغط (3 ساعات)", subtitle = tendencyTitle) {
                        if (tv != null) {
                            Text(
                                (if (tv >= 0) "+" else "−") + Fmt.num(abs(tv), 1) + " hPa / 3 ساعات",
                                style = MaterialTheme.typography.titleLarge, color = tendencyColor, fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(tendencyBody, style = MaterialTheme.typography.bodyMedium, color = tendencyColor)
                    }
                }
                item {
                    GlassCard(title = "آخر 24 ساعة") {
                        val cutoff = System.currentTimeMillis() - 24 * 3600_000L
                        val pts = history.filter { it.t >= cutoff }
                        if (pts.size < 2) {
                            NoteText("لا توجد قراءات كافية للرسم. تُسجَّل قراءة عند كل فتح للشاشة وكل 10 دقائق أثناء بقائها مفتوحة.")
                        } else {
                            BaroChart(pts, p, Modifier.fillMaxWidth().height(200.dp))
                            Spacer(Modifier.height(4.dp))
                            InfoRow("أعلى قراءة", Fmt.num(pts.maxOf { it.p }.toDouble(), 1) + " hPa")
                            InfoRow("أدنى قراءة", Fmt.num(pts.minOf { it.p }.toDouble(), 1) + " hPa")
                            InfoRow("عدد القراءات", pts.size.toString())
                        }
                    }
                }
            }
            item {
                GlassCard(title = "ارتفاع GPS") {
                    if (gps == null) {
                        NoteText("جارٍ انتظار إشارة الأقمار الصناعية… اخرج إلى مكان مكشوف")
                        GlassOutlinedButton(onClick = { env.requestGps() }, nav = true) { Text("تفعيل الموقع (GPS)") }
                    } else if (gpsAlt == null) {
                        NoteText("لم تصل قيمة الارتفاع من الأقمار بعد.")
                    } else {
                        InfoRow("الارتفاع عن سطح البحر", Fmt.num(gpsAlt, 0) + " م", emphasize = true)
                        InfoRow("دقة الموقع الأفقية", "±" + Fmt.num(gps.accuracy.toDouble(), 0) + " م")
                        NoteText("ارتفاع GPS أقل دقة من الضغط في التغيّرات الصغيرة (قد يخطئ بعشرات الأمتار)، لكنه لا يتأثر بالطقس.")
                    }
                }
            }
            item {
                Note("الارتفاع المحسوب من الضغط يتغير مع الطقس (تغيّر 1 hPa ≈ 8 أمتار)، فأعد المعايرة عند ارتفاع معروف. أما اتجاه الضغط فيُستعمل للتنبؤ القصير؛ انخفاضه السريع علامة تحذير مبكرة في الصحراء.")
            }
        }
    }
}

@Composable
private fun NoteText(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 3.dp), style = MaterialTheme.typography.bodySmall, color = AppTheme.ornament.muted)
}

private fun DrawScope.baroText(text: String, x: Float, y: Float, sizePx: Float, color: Color, align: android.graphics.Paint.Align) {
    val pt = android.graphics.Paint().apply {
        textSize = sizePx
        this.color = color.toArgb()
        textAlign = align
        isAntiAlias = true
        typeface = CanvasFonts.text
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, pt)
}

/** مخطط خطي للضغط (الوقت من اليسار إلى اليمين، الأحدث عند اليمين) */
@Composable
private fun BaroChart(points: List<BaroSample>, live: Float?, modifier: Modifier) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val muted = o.muted
    val ink = o.ink
    val nowMs = System.currentTimeMillis()
    val data = if (live != null) points + BaroSample(nowMs, live) else points
    Canvas(modifier) {
        val left = 44.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val t0 = nowMs - 24 * 3600_000L
        val minP0 = data.minOf { it.p }
        val maxP0 = data.maxOf { it.p }
        val mid = (minP0 + maxP0) / 2f
        val half = maxOf((maxP0 - minP0) / 2f + 0.3f, 1.0f)
        val lo = mid - half
        val hi = mid + half
        fun x(t: Long) = left + (right - left) * ((t - t0).toFloat() / (24 * 3600_000L).toFloat()).coerceIn(0f, 1f)
        fun y(v: Float) = bottom - (bottom - top) * ((v - lo) / (hi - lo))
        // الشبكة
        for (i in 0..4) {
            val v = lo + (hi - lo) * i / 4f
            val yy = y(v)
            drawLine(muted.copy(alpha = 0.25f), Offset(left, yy), Offset(right, yy), 1.dp.toPx())
            baroText(Fmt.num(v.toDouble(), 1), left - 4.dp.toPx(), yy + 4.dp.toPx(), 10.dp.toPx(), muted, android.graphics.Paint.Align.RIGHT)
        }
        val labels = listOf(0 to "-24س", 6 to "-18س", 12 to "-12س", 18 to "-6س", 24 to "الآن")
        labels.forEach { (h, txt) ->
            val xx = left + (right - left) * h / 24f
            drawLine(muted.copy(alpha = 0.18f), Offset(xx, top), Offset(xx, bottom), 1.dp.toPx())
            baroText(txt, xx, size.height - 6.dp.toPx(), 10.dp.toPx(), muted, android.graphics.Paint.Align.CENTER)
        }
        // الخط
        val path = Path()
        data.forEachIndexed { i, s ->
            val px = x(s.t)
            val py = y(s.p)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        data.forEach { s -> drawCircle(primary, 2.5.dp.toPx(), Offset(x(s.t), y(s.p))) }
        val last = data.last()
        drawCircle(ink, 4.dp.toPx(), Offset(x(last.t), y(last.p)))
    }
}
