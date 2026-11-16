package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.DayNight
import com.mawaqeet.app.astro.DayNightCalc
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import java.util.Locale

/* ───────────── نماذج داخلية ───────────── */

private class WhHour(val time: Long, val alt: Double)

private class WhData(
    val dn: DayNight,
    /** عينات ارتفاع الشمس كل 10 دقائق من الشروق إلى الغروب */
    val samples: List<WhHour>,
    val hourly: List<WhHour>,
    /** وقت بلوغ 35° صعوداً ونزولاً (إن وُجد) */
    val up35: Long?,
    val down35: Long?,
    val moonRise: Long?,
    val moonSet: Long?,
    val moonUpTonight: Boolean,
    val moonIllum: Double,
    val nightRef: Long
)

private const val WH_LIMIT = 35.0

private fun whLevel(alt: Double): Int = when { alt > 45 -> 2; alt >= 25 -> 1; else -> 0 }

@Composable
private fun whColor(level: Int): Color = when (level) {
    2 -> AppTheme.ornament.danger
    1 -> AppTheme.ornament.warning
    else -> AppTheme.ornament.success
}

private fun whLabel(level: Int): String = when (level) {
    2 -> "حر شديد: استرح في الظل"
    1 -> "حذر"
    else -> "مناسب للحركة"
}

private fun whF(v: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)

@Composable
fun WaterHeatScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament

    // ───── مدخلات مخطط الماء ─────
    var temp by remember { mutableFloatStateOf(35f) }
    var activity by remember { mutableIntStateOf(1) }
    var people by remember { mutableIntStateOf(1) }
    var liters by remember { mutableFloatStateOf(10f) }
    var activeHours by remember { mutableFloatStateOf(12f) }

    val hot = temp > 38f
    // لتر/ساعة للشخص (تقدير تقريبي): راحة في الظل، مشي خفيف، مشي في الشمس أو عمل
    val rate = when (activity) {
        0 -> if (hot) 0.5 else 0.25
        1 -> if (hot) 0.75 else 0.4
        else -> if (hot) 1.0 else 0.5
    }
    val restRate = if (hot) 0.5 else 0.25
    val act = activeHours.toInt().coerceIn(1, 20)
    val perPersonDay = if (activity == 0) restRate * 24 else rate * act + restRate * (24 - act)
    val groupHourly = rate * people
    val groupDaily = perPersonDay * people
    val hoursLast = if (groupHourly > 0) liters / groupHourly else 0.0
    val daysLast = if (groupDaily > 0) liters / groupDaily else 0.0

    // ───── الشمس والقمر اليوم ─────
    val today = env.today
    val data = rememberComputed(today, env.lat, env.lon, env.zone) {
        val ds = env.dayStart(today)
        val dn = DayNightCalc.compute(ds, env.lat, env.lon)
        val rise = dn.sunrise; val set = dn.sunset
        val samples = ArrayList<WhHour>()
        if (rise != null && set != null && set > rise) {
            var t = rise
            while (t <= set) { samples.add(WhHour(t, Ephemeris.horizontal(Body.SUN, t, env.lat, env.lon).alt)); t += 600_000L }
        }
        val hourly = ArrayList<WhHour>()
        if (rise != null && set != null) {
            var t = (Math.floorDiv(rise, 3_600_000L) + 1) * 3_600_000L
            // الساعات التامة بالتوقيت المحلي
            val offset = env.zone.rules.getOffset(java.time.Instant.ofEpochMilli(rise)).totalSeconds * 1000L
            t = (Math.floorDiv(rise + offset, 3_600_000L) + 1) * 3_600_000L - offset
            while (t <= set) { hourly.add(WhHour(t, Ephemeris.horizontal(Body.SUN, t, env.lat, env.lon).alt)); t += 3_600_000L }
        }
        var up: Long? = null; var down: Long? = null
        for (i in 1 until samples.size) {
            val a = samples[i - 1]; val b = samples[i]
            if (up == null && a.alt < WH_LIMIT && b.alt >= WH_LIMIT) up = a.time + ((WH_LIMIT - a.alt) / (b.alt - a.alt) * (b.time - a.time)).toLong()
            if (a.alt >= WH_LIMIT && b.alt < WH_LIMIT) down = a.time + ((a.alt - WH_LIMIT) / (a.alt - b.alt) * (b.time - a.time)).toLong()
        }
        val nightRef = dn.midnight ?: ((set ?: (ds + 18 * 3_600_000L)) + 6 * 3_600_000L)
        val m0 = RiseSetCalc.compute(Body.MOON, ds, env.lat, env.lon)
        val m1 = RiseSetCalc.compute(Body.MOON, ds + DAY_MS, env.lat, env.lon)
        val from = set ?: (ds + 18 * 3_600_000L)
        val to = from + 16 * 3_600_000L
        val mRise = listOfNotNull(m0.rise, m1.rise).firstOrNull { it in from..to }
        val mSet = listOfNotNull(m0.set, m1.set).firstOrNull { it in from..to }
        val moonAlt = Ephemeris.horizontal(Body.MOON, nightRef, env.lat, env.lon).alt
        WhData(dn, samples, hourly, up, down, mRise, mSet, moonAlt > 0, Phases.state(nightRef).illumination, nightRef)
    }

    ScreenScaffold(
        title = "الماء والحرارة", onBack = env.back, subtitle = "خطط لمائك وحركتك في الصحراء",
        actions = {
            ShareAction {
                buildString {
                    appendLine("خطة الماء (تقدير تقريبي)")
                    appendLine("الحرارة ${temp.toInt()}°م، ${people} شخص، ماء متاح ${whF(liters.toDouble())} لتر")
                    appendLine("الحاجة اليومية للمجموعة: ${whF(groupDaily)} لتر")
                    appendLine("يكفي نحو ${whF(daysLast)} يوم (${whF(hoursLast, 0)} ساعة بالنشاط المحدد)")
                    append("المرجع: دليل البقاء للجيش الأمريكي FM 21-76")
                }
            }
        }
    ) {
        ScreenList {
            // ───── مخطط الماء ─────
            item {
                GlassCard(title = "مخطط الماء", subtitle = "كم يكفيك الماء الذي معك؟") {
                    WhSliderRow("درجة حرارة الهواء", "${temp.toInt()}°م", temp, 15f..55f, 39) { temp = it }
                    Spacer(Modifier.height(6.dp))
                    Text("النشاط", color = o.muted, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("راحة في الظل", "مشي خفيف", "مشي في الشمس أو عمل").forEachIndexed { i, s ->
                            GlassChip(selected = activity == i, onClick = { activity = i }, label = { Text(s, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("عدد الأشخاص", Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.bodyMedium)
                        GlassTonalButton(onClick = { if (people > 1) people-- }, nav = true) { Text("−", fontSize = 20.sp) }
                        Text("$people", Modifier.width(48.dp), color = o.ink, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        GlassTonalButton(onClick = { if (people < 30) people++ }, nav = true) { Text("+", fontSize = 20.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    WhSliderRow("الماء المتاح", "${whF(liters.toDouble())} لتر", liters, 0f..100f, 199) { liters = Math.round(it * 2f) / 2f }
                    if (activity != 0) WhSliderRow("ساعات النشاط في اليوم", "$act ساعة", activeHours, 1f..16f, 14) { activeHours = it }
                }
            }
            item {
                val enough = hoursLast >= 24 || daysLast >= 1.0
                GlassCard(title = "النتيجة", subtitle = "تقدير تقريبي وليس قياساً دقيقاً") {
                    MetricRow(
                        "للشخص/ساعة" to "${whF(rate, 2)} لتر",
                        "للشخص/يوم" to "${whF(perPersonDay)} لتر",
                        "للمجموعة/يوم" to "${whF(groupDaily)} لتر"
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoRow("يكفي بالنشاط المحدد", "${whF(hoursLast, 1)} ساعة", emphasize = true,
                        valueColor = if (hoursLast < 12) o.danger else if (hoursLast < 24) o.warning else o.success)
                    InfoRow("يكفي بالحاجة اليومية الكاملة", "${whF(daysLast, 1)} يوم", emphasize = true,
                        valueColor = if (daysLast < 1) o.danger else if (daysLast < 2) o.warning else o.success)
                    if (!enough) {
                        Spacer(Modifier.height(4.dp))
                        Text("الماء أقل من حاجة يوم كامل: قلّل الحركة في الحر، وتحرك ليلاً أو فجراً، وفكّر في طلب المساعدة.", color = o.danger, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("لا تقنّن الماء إلى حد العطش: اشرب بانتظام؛ العطش ليس مؤشراً موثوقاً", color = o.warning, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    Note("المرجع: دليل البقاء للجيش الأمريكي FM 21-76 (نحو 0.5 لتر/ساعة دون 38°م و1 لتر/ساعة فوقها أثناء النشاط). أرقام الراحة في الظل والمشي الخفيف تقدير تقريبي منّا. تتغير الحاجة بالوزن والملابس والرياح والصحة.")
                }
            }

            // ───── الحرارة والشمس ─────
            item { SectionTitle("حرارة الشمس اليوم في ${env.place.name}") }
            if (data == null) item { Busy() } else {
                val dn = data.dn
                if (dn.sunrise == null || dn.sunset == null) {
                    item { GlassCard(title = "الشمس") { Text(if (dn.polarDay) "الشمس لا تغيب اليوم في هذا الموقع." else "الشمس لا تطلع اليوم في هذا الموقع.", color = o.ink) } }
                } else {
                    item {
                        GlassCard(title = "ارتفاع الشمس خلال النهار", subtitle = "من الشروق ${env.time(dn.sunrise)} إلى الغروب ${env.time(dn.sunset)}") {
                            WhChart(data, env.now)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Pill("أكثر من 45°", o.danger); Pill("25–45°", o.warning); Pill("أقل من 25°", o.success)
                            }
                        }
                    }
                    item {
                        GlassCard(title = "نوافذ السفر المقترحة", subtitle = "حين يقل ارتفاع الشمس عن 35°") {
                            val cd = dn.civilDawn; val cu = dn.civilDusk
                            if (data.up35 == null && data.down35 == null) {
                                if (dn.noonAlt < WH_LIMIT) {
                                    InfoRow("طوال النهار", "${env.time(cd ?: dn.sunrise)} – ${env.time(cu ?: dn.sunset)}", emphasize = true)
                                    Note("الشمس لا تبلغ 35° اليوم، فالنهار كله مناسب نسبياً.")
                                } else {
                                    InfoRow("لا نافذة نهارية", "الشمس فوق 35° طوال النهار", valueColor = o.danger)
                                }
                            } else {
                                InfoRow("الصباح", "${env.time(cd ?: dn.sunrise)} – ${env.time(data.up35 ?: dn.sunset)}", emphasize = true, valueColor = o.success)
                                InfoRow("المساء", "${env.time(data.down35 ?: dn.sunrise)} – ${env.time(cu ?: dn.sunset)}", emphasize = true, valueColor = o.success)
                                if (data.up35 != null && data.down35 != null)
                                    InfoRow("استراحة الظهيرة", "${env.time(data.up35)} – ${env.time(data.down35)}", valueColor = o.danger)
                            }
                            Note("من الفجر المدني إلى بلوغ الشمس 35°، ومن هبوطها دونها إلى الشفق المدني. السفر ليلاً أبرد وأوفر للماء إن كان الضوء كافياً (قمر أو كشاف) والطريق معروفاً.")
                        }
                    }
                    item {
                        GlassCard(title = "ساعة بساعة") {
                            data.hourly.forEach { h ->
                                val lv = whLevel(h.alt)
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(env.time(h.time), Modifier.width(72.dp), color = o.ink, fontWeight = FontWeight.Bold)
                                    Text("${whF(h.alt, 0)}°", Modifier.width(48.dp), color = o.muted)
                                    Pill(whLabel(lv), whColor(lv))
                                }
                            }
                        }
                    }

                    // ───── ما تبقى من الضوء ─────
                    item {
                        GlassCard(title = "الضوء المتبقي", subtitle = "عدٌّ تنازلي حي") {
                            WhCountdownRow("الغروب", dn.sunset, env.now) { env.time(it) }
                            WhCountdownRow("نهاية الضوء المفيد (الشفق المدني)", dn.civilDusk, env.now) { env.time(it) }
                            WhCountdownRow("بدء الليل الفلكي", dn.astroDusk, env.now) { env.time(it) }
                        }
                    }
                }
                item {
                    GlassCard(title = "قمر الليلة") {
                        InfoRow("طلوع القمر", env.time(data.moonRise))
                        InfoRow("غروب القمر", env.time(data.moonSet))
                        InfoRow("الإضاءة", "${whF(data.moonIllum * 100, 0)}%")
                        val bright = data.moonIllum > 0.5 && data.moonUpTonight
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (bright) "ليلة مقمرة: يمكن المشي ليلاً" else "ليلة مظلمة",
                            color = if (bright) o.success else o.warning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black
                        )
                        Note(
                            if (bright) "القمر فوق الأفق في منتصف الليل وإضاءته كبيرة. التزم طريقاً معروفاً وانتبه لخطر الحفر والصخور."
                            else "لا تتحرك ليلاً دون كشاف وطريق معروف؛ الأفضل التوقف والاحتماء حتى الفجر."
                        )
                    }
                }
            }

            // ───── الإجهاد الحراري ─────
            item { SectionTitle("الإجهاد الحراري") }
            item {
                GlassCard(title = "الإنهاك الحراري", subtitle = "إسعاف أولي عام وليس تشخيصاً") {
                    WhBullet("العلامات: تعرق غزير، شحوب، صداع، دوخة، غثيان، تعب وضعف، تشنج عضلات، نبض سريع، جلد بارد رطب.")
                    Text("ما تفعله فوراً", color = o.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    WhBullet("انقل المصاب إلى الظل أو مكان أبرد، واجعله يستلقي مع رفع ساقيه قليلاً.")
                    WhBullet("فكّ الملابس الضيقة أو انزع الزائد منها.")
                    WhBullet("بلّل الجلد بالماء وروّح عليه بالهواء.")
                    WhBullet("إن كان واعياً تماماً: رشفات صغيرة متكررة من الماء.")
                    WhBullet("إن لم يتحسن خلال 30 دقيقة أو ساءت حالته فاعتبرها ضربة شمس.")
                }
            }
            item {
                GlassCard(title = "ضربة الشمس: حالة طارئة", subtitle = "اتصل برقم الطوارئ في بلدك فوراً") {
                    WhBullet("العلامات: حرارة جسم عالية جداً (فوق 40°م)، جلد حار وجاف أو متعرق، تخليط أو هذيان، تشنجات، فقدان وعي، نبض سريع.")
                    Text("ما تفعله فوراً", color = o.danger, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    WhBullet("اطلب الإسعاف فوراً عبر رقم الطوارئ في بلدك وأرسل موقعك من شاشة «الطوارئ».")
                    WhBullet("نقله إلى الظل وتبريده بسرعة: ماء على الجسم كله ومراوحة، وكمادات باردة على الرقبة والإبط والفخذين.")
                    WhBullet("لا تسقِه إن كان فاقد الوعي أو مشوّش الإدراك.")
                    WhBullet("إن توقف تنفسه فابدأ الإنعاش القلبي الرئوي إن كنت تعرفه.")
                }
            }
            item { Note("معلومات عامة للتوعية، ولا تحل محل استشارة طبية.") }
        }
    }
}

/* ───────────── مكوّنات داخلية ───────────── */

@Composable
private fun WhSliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    val o = AppTheme.ornament
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, color = o.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun WhBullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("•", color = AppTheme.ornament.gold, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = AppTheme.ornament.ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WhCountdownRow(label: String, t: Long?, now: Long, fmt: (Long) -> String) {
    val o = AppTheme.ornament
    if (t == null) { InfoRow(label, "--"); return }
    if (t > now) InfoRow(label, "${Fmt.countdown(t - now)}  (${fmt(t)})", emphasize = true, valueColor = o.success)
    else InfoRow(label, "مضى منذ ${Fmt.span(now - t)}  (${fmt(t)})", valueColor = o.muted)
}

/** منحنى ارتفاع الشمس ملوّن بحسب مستوى الحر، مع خط 35° وعلامة الوقت الحالي */
@Composable
private fun WhChart(d: WhData, now: Long) {
    val o = AppTheme.ornament
    val danger = o.danger; val warn = o.warning; val ok = o.success; val muted = o.muted
    val pts = d.samples
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        if (pts.size < 2) return@Canvas
        val t0 = pts.first().time.toDouble(); val t1 = pts.last().time.toDouble()
        val maxAlt = maxOf(50.0, (pts.maxOf { it.alt } + 5))
        fun px(t: Long) = ((t - t0) / (t1 - t0) * size.width).toFloat()
        fun py(a: Double) = (size.height - (a.coerceAtLeast(0.0) / maxAlt * size.height)).toFloat()
        // خطوط 25 و45 و35
        for (a in listOf(25.0, 45.0)) {
            drawLine(muted.copy(alpha = 0.35f), Offset(0f, py(a)), Offset(size.width, py(a)), strokeWidth = 1f)
        }
        drawLine(muted.copy(alpha = 0.7f), Offset(0f, py(WH_LIMIT)), Offset(size.width, py(WH_LIMIT)), strokeWidth = 2f)
        drawLine(muted.copy(alpha = 0.5f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val c = when (whLevel((a.alt + b.alt) / 2)) { 2 -> danger; 1 -> warn; else -> ok }
            drawLine(c, Offset(px(a.time), py(a.alt)), Offset(px(b.time), py(b.alt)), strokeWidth = 7f, cap = StrokeCap.Round)
        }
        if (now in pts.first().time..pts.last().time) {
            drawLine(muted, Offset(px(now), 0f), Offset(px(now), size.height), strokeWidth = 2f)
        }
    }
}
