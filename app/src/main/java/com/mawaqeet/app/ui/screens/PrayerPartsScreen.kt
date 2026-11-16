package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerDay
import com.mawaqeet.app.astro.RiseSet
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.Sun
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.data.Env
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.TableRowCells
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

/* ───────────────────────── البيانات ───────────────────────── */

private class PPData(
    val prevSun: RiseSet, val sun: RiseSet, val nextSun: RiseSet,
    val pr: PrayerDay, val prevPr: PrayerDay, val nextPr: PrayerDay,
    /** وقت اصفرار الشمس التقريبي (ارتفاعها ≈ 10° قبل الغروب) */
    val yellow: Long?,
    /** معادلة الزمن بالدقائق */
    val eot: Double
)

private fun ppBuild(env: Env, date: LocalDate): PPData {
    val ds = env.dayStart(date)
    val prev = env.dayStart(date.minusDays(1))
    val next = env.dayStart(date.plusDays(1))
    val sun = RiseSetCalc.compute(Body.SUN, ds, env.lat, env.lon)
    val yellow = RiseSetCalc.compute(Body.SUN, ds, env.lat, env.lon, 10.0).set
    val t = sun.transit ?: (ds + 12 * HOUR_MS)
    return PPData(
        RiseSetCalc.compute(Body.SUN, prev, env.lat, env.lon), sun,
        RiseSetCalc.compute(Body.SUN, next, env.lat, env.lon),
        env.prayers(date), env.prayers(date.minusDays(1)), env.prayers(date.plusDays(1)),
        yellow, Sun.equationOfTime(jdeOf(jdOf(t)))
    )
}

private class PPWin(val label: String, val start: Long?, val end: Long?, val strictFrom: Long? = null) {
    fun active(now: Long) = start != null && end != null && now >= start && now < end
}

private fun ppWindows(d: PPData): List<PPWin> {
    val sr = d.pr[Prayer.SUNRISE]
    val dh = d.pr[Prayer.DHUHR]
    val tr = d.sun.transit ?: dh
    val set = d.sun.set
    return listOf(
        PPWin("من بعد صلاة الفجر حتى ترتفع الشمس قدر رمح", d.pr[Prayer.FAJR], sr?.plus(15 * MIN_MS)),
        PPWin("عند قيام الشمس في كبد السماء (قبيل الزوال)", tr?.minus(5 * MIN_MS), dh ?: tr),
        PPWin("من بعد صلاة العصر حتى الغروب", d.pr[Prayer.ASR], set, set?.minus(15 * MIN_MS))
    )
}

private class PPLive(
    val name: String, val start: Long, val end: Long, val endLabel: String,
    val preferredEnd: Long? = null, val preferredLabel: String = "", val approx: Boolean = false
)

private fun ppLive(d: PPData, now: Long): PPLive? {
    val fajr = d.pr[Prayer.FAJR]; val sr = d.pr[Prayer.SUNRISE]; val dh = d.pr[Prayer.DHUHR]
    val asr = d.pr[Prayer.ASR]; val mg = d.pr[Prayer.MAGHRIB]; val ish = d.pr[Prayer.ISHA]
    val nFajr = d.nextPr[Prayer.FAJR]; val pIsha = d.prevPr[Prayer.ISHA]; val pMg = d.prevPr[Prayer.MAGHRIB]
    fun inn(a: Long?, b: Long?) = a != null && b != null && now >= a && now < b
    return when {
        inn(fajr, sr) -> PPLive("الفجر", fajr!!, sr!!, "ينتهي عند الشروق")
        inn(sr, dh) -> PPLive("ما بين الشروق والظهر (وقت الضحى)", sr!!, dh!!, "حتى دخول الظهر")
        inn(dh, asr) -> PPLive("الظهر", dh!!, asr!!, "ينتهي بدخول العصر")
        inn(asr, mg) -> PPLive(
            "العصر", asr!!, mg!!, "وقت الضرورة حتى المغرب",
            d.yellow?.takeIf { it > asr }, "الوقت المختار حتى اصفرار الشمس", true
        )
        inn(mg, ish) -> PPLive("المغرب", mg!!, ish!!, "ينتهي بدخول العشاء")
        ish != null && nFajr != null && now >= ish && now < nFajr -> PPLive(
            "العشاء", ish, nFajr, "وقت الضرورة حتى الفجر",
            mg?.let { it + (nFajr - it) / 2 }, "الوقت المختار حتى منتصف الليل"
        )
        pIsha != null && fajr != null && now < fajr -> PPLive(
            "العشاء", pIsha, fajr, "وقت الضرورة حتى الفجر",
            pMg?.let { it + (fajr - it) / 2 }, "الوقت المختار حتى منتصف الليل"
        )
        else -> null
    }
}

private fun ppMs(ms: Long): String {
    val m = Math.max(0L, ms) / 60000L
    val s = (Math.max(0L, ms) / 1000L) % 60
    return "%d د %02d ث".format(Locale.US, m, s)
}

private fun ppHm(ms: Long, seconds: Boolean = false): String {
    val t = Math.floorMod(ms, 24L * HOUR_MS) / 1000L
    return if (seconds) "%02d:%02d:%02d".format(Locale.US, t / 3600, (t / 60) % 60, t % 60)
    else "%02d:%02d".format(Locale.US, t / 3600, (t / 60) % 60)
}

private fun ppEot(eot: Double): String {
    val sec = Math.round(Math.abs(eot) * 60.0)
    return "${if (eot < 0) "-" else "+"}${sec / 60} د ${"%02d".format(Locale.US, sec % 60)} ث"
}

private fun ppRel(env: Env, d: LocalDate): String {
    val n = ChronoUnit.DAYS.between(env.today, d)
    return when (n) { 0L -> "اليوم"; 1L -> "غداً"; 2L -> "بعد يومين"; else -> "بعد $n أيام" }
}

private class PPFast(val date: LocalDate, val note: String)

private fun ppFasting(env: Env): Triple<LocalDate, LocalDate, List<PPFast>> {
    val t = env.today
    var mon = t; while (mon.dayOfWeek.value != 1) mon = mon.plusDays(1)
    var thu = t; while (thu.dayOfWeek.value != 4) thu = thu.plusDays(1)
    val s = env.settings
    val bid = ArrayList<PPFast>()
    var i = 0
    while (i < 45 && bid.isEmpty()) {
        val g = t.plusDays(i.toLong())
        val h = Calendars.toHijri(g, s.hijriMethod, s.hijriAdjust)
        if (h.day in 13..15) {
            var k = 0
            while (k < 3) {
                val gg = g.plusDays(k.toLong())
                val hh = Calendars.toHijri(gg, s.hijriMethod, s.hijriAdjust)
                if (hh.day in 13..15) {
                    val note = when {
                        hh.month == 12 && hh.day == 13 -> "أيام التشريق: لا يُصام فيها"
                        hh.month == 9 -> "رمضان: الصيام فرض"
                        else -> ""
                    }
                    bid += PPFast(gg, note)
                }
                k++
            }
        }
        i++
    }
    return Triple(mon, thu, bid)
}

/* ───────────────────────── الشاشة ───────────────────────── */

/** «تفصيل الأوقات»: الوقت الحالي وأوقات الكراهة وأجزاء الليل والنهار والساعات الزمانية والتوقيت الغروبي والوقت الشمسي */
@Composable
fun PrayerPartsScreen() {
    val env = LocalEnv.current
    var date by remember { mutableStateOf(env.today) }
    val data = rememberComputed(date, env.settings) { ppBuild(env, date) }
    val isToday = date == env.today

    ScreenScaffold(
        title = "تفصيل الأوقات", onBack = env.back, subtitle = env.place.name,
        actions = { ShareAction { data?.let { ppShare(env, date, it) } ?: "" } }
    ) {
        ScreenList {
            item { DayStepper(date, { date = it }) }
            if (data == null) {
                item { Busy() }
            } else {
                if (isToday) item { PPLiveCard(env, data) }
                item { PPDislikedCard(env, data, isToday) }
                item { PPNightCard(env, data) }
                item { PPDayCard(env, data) }
                item { PPHoursCard(env, data, isToday) }
                item { PPGharoubiCard(env, data, isToday) }
                item { PPSolarCard(env, data, isToday) }
                item { PPFastingCard(env) }
                item { Note("الأوقات محسوبة بطريقة الحساب المختارة في إعدادات الصلاة. ما فيه خلاف بين أهل العلم مذكور باختصار، ويُرجع في التفاصيل إلى أهل العلم الموثوقين.") }
            }
        }
    }
}

@Composable
private fun PPLiveCard(env: Env, d: PPData) {
    val o = AppTheme.ornament
    val now = env.now
    val live = ppLive(d, now)
    val wins = ppWindows(d)
    val dis = wins.firstOrNull { it.active(now) }
    val strict = dis != null && dis.strictFrom != null && now >= dis.strictFrom
    GlassCard(title = "الآن", subtitle = env.time(now, true)) {
        if (live == null) {
            Text("تعذّر تحديد الوقت الحالي في هذا الموقع (قد لا تتوفر بعض المواقيت).", color = o.muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            val total = (live.end - live.start).coerceAtLeast(1L)
            val frac = ((now - live.start).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("وقت ${live.name}", color = o.gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(Fmt.countdown(live.end - now), color = o.ink, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("المتبقي — ${live.endLabel} (${env.time(live.end)})", color = o.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            MetricRow("بدأ منذ" to Fmt.span(now - live.start), "يتبقى" to Fmt.span(live.end - now), "بدايته" to env.time(live.start))
            if (live.preferredEnd != null) {
                val inPreferred = now < live.preferredEnd
                Spacer(Modifier.height(6.dp))
                InfoRow(live.preferredLabel + if (live.approx) " (تقريبي)" else "", env.time(live.preferredEnd))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inPreferred) Pill("الآن في الوقت المختار", o.success) else Pill("الآن في وقت الضرورة", o.warning)
                }
                if (live.approx) Note("اصفرار الشمس: تقدير عند ارتفاع الشمس نحو 10° فوق الأفق.")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (dis != null) {
            Pill(if (strict) "وقت كراهة الآن — اصفرار الشمس (أشدّها)" else "وقت كراهة الآن", o.danger)
            Text(dis.label, color = o.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        } else {
            Pill("ليس وقت كراهة الآن", o.success)
        }
    }
}

@Composable
private fun PPDislikedCard(env: Env, d: PPData, isToday: Boolean) {
    val o = AppTheme.ornament
    val wins = ppWindows(d)
    GlassCard(title = "أوقات الكراهة", subtitle = "الأوقات التي يُنهى عن التنفّل المطلق فيها") {
        wins.forEach { w ->
            val on = isToday && w.active(env.now)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.label, color = if (on) o.danger else o.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.Black else FontWeight.Medium)
                    if (w.strictFrom != null) {
                        Text("أشدّها عند اصفرار الشمس: من ${env.time(w.strictFrom)} (قبل الغروب بنحو 15 د)", color = o.muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (on) { Pill("الآن", o.danger); Spacer(Modifier.width(8.dp)) }
                Text("${env.time(w.start)} – ${env.time(w.end)}", color = if (on) o.danger else o.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
        Note("تُقضى الفرائض الفائتة في هذه الأوقات، وتجوز عند كثير من أهل العلم الصلوات ذوات الأسباب كتحية المسجد. مقدار الرمح نحو 15 دقيقة تقريباً.")
    }
}

@Composable
private fun PPNightCard(env: Env, d: PPData) {
    val mg = d.pr[Prayer.MAGHRIB]
    val fajrN = d.nextPr[Prayer.FAJR]
    GlassCard(title = "أجزاء الليل", subtitle = "من المغرب إلى الفجر") {
        if (mg == null || fajrN == null || fajrN <= mg) {
            Text("تعذّر حساب أجزاء الليل لهذا اليوم.", color = AppTheme.ornament.muted)
        } else {
            val n = fajrN - mg
            InfoRow("طول الليل (المغرب → الفجر)", Fmt.span(n))
            InfoRow("بداية الليل (المغرب)", env.time(mg))
            InfoRow("بداية الثلث الثاني", env.time(mg + n / 3))
            InfoRow("منتصف الليل", env.time(mg + n / 2), emphasize = true)
            InfoRow("بداية الثلث الأخير (وقت النزول والقيام)", env.time(mg + n * 2 / 3), emphasize = true)
            InfoRow("السَّحَر (السدس الأخير)", env.time(fajrN - n / 6))
            InfoRow("نهاية الليل (الفجر)", env.time(fajrN))
        }
        val set = d.sun.set; val rise = d.nextSun.rise
        if (set != null && rise != null && rise > set) {
            Spacer(Modifier.height(6.dp))
            InfoRow("للمقارنة: الغروب → الشروق", Fmt.span(rise - set))
            InfoRow("منتصف ما بين الغروب والشروق", env.time(set + (rise - set) / 2))
        }
    }
}

@Composable
private fun PPDayCard(env: Env, d: PPData) {
    val fajr = d.pr[Prayer.FAJR]; val sr = d.pr[Prayer.SUNRISE]; val mg = d.pr[Prayer.MAGHRIB]
    val tr = d.sun.transit ?: d.pr[Prayer.DHUHR]
    val best = if (sr != null && d.sun.set != null && d.sun.set > sr) sr + (d.sun.set - sr) / 4 else null
    GlassCard(title = "أجزاء النهار") {
        InfoRow("الإمساك (قبل الفجر بـ 10 د، احتياطاً)", env.time(fajr?.minus(10 * MIN_MS)))
        InfoRow("الفجر الصادق (بدء الصيام)", env.time(fajr))
        InfoRow("مدة الصيام (الفجر → المغرب)", if (fajr != null && mg != null) Fmt.span(mg - fajr) else "--")
        InfoRow("الشروق", env.time(sr))
        InfoRow("بدء الضحى (بعد الشروق بنحو 15 د)", env.time(sr?.plus(15 * MIN_MS)))
        InfoRow("أفضل وقت الضحى (حين تَرمَض الفِصال) — تقريبي", env.time(best), emphasize = true)
        InfoRow("نهاية الضحى (قبل الزوال بنحو 5 د)", env.time(tr?.minus(5 * MIN_MS)))
        InfoRow("وقت الزوال (انتصاف الشمس)", env.time(tr))
        Note("«ترمض الفصال»: حين يشتدّ حرّ الرمل فتبرك الفِصال (صغار الإبل). حُسب هنا تقريباً بعد ربع النهار من الشروق.")
    }
}

@Composable
private fun PPHoursCard(env: Env, d: PPData, isToday: Boolean) {
    val o = AppTheme.ornament
    val rise = d.sun.rise; val set = d.sun.set; val nextRise = d.nextSun.rise; val prevSet = d.prevSun.set
    GlassCard(title = "الساعات الزمانية", subtitle = "النهار والليل كلٌّ مقسوم على 12 جزءاً متساوياً") {
        if (rise == null || set == null || nextRise == null || set <= rise || nextRise <= set) {
            Text("لا شروق أو غروب معتاد في هذا اليوم بهذا الموقع، فلا تُحسب الساعات الزمانية.", color = o.muted)
            return@GlassCard
        }
        val dh = (set - rise) / 12
        val nh = (nextRise - set) / 12
        MetricRow("ساعة النهار" to ppMs(dh), "ساعة الليل" to ppMs(nh))
        if (isToday) {
            val now = env.now
            val txt = when {
                now >= rise && now < set -> "الساعة ${((now - rise) / dh + 1).coerceAtMost(12)} من النهار"
                now >= set && now < nextRise -> "الساعة ${((now - set) / nh + 1).coerceAtMost(12)} من الليل"
                prevSet != null && now < rise && now >= prevSet -> {
                    val h = (rise - prevSet) / 12
                    "الساعة ${((now - prevSet) / h.coerceAtLeast(1L) + 1).coerceAtMost(12)} من الليل"
                }
                else -> "--"
            }
            Spacer(Modifier.height(6.dp))
            Pill("الساعة الزمانية الحالية: $txt", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        TableRowCells(listOf("الساعة", "بداية ساعة النهار", "بداية ساعة الليل"), header = true)
        for (i in 0 until 12) {
            TableRowCells(listOf("${i + 1}", env.time(rise + dh * i), env.time(set + nh * i)))
        }
        Note("ساعة الجمعة الأخيرة الواردة في الحديث: تُفهم عند الجمهور بأنها آخر ساعة بعد العصر، أي قبيل المغرب. وقيل غير ذلك.")
    }
}

@Composable
private fun PPGharoubiCard(env: Env, d: PPData, isToday: Boolean) {
    val sunsets = listOfNotNull(d.prevSun.set, d.sun.set, d.nextSun.set)
    fun since(t: Long): Long? = sunsets.filter { it <= t }.maxOrNull()?.let { t - it }
    GlassCard(title = "التوقيت الغروبي", subtitle = "اليوم يبدأ من الغروب (الساعة 0:00)") {
        if (isToday) {
            val s = since(env.now)
            InfoRow("الوقت الآن بالتوقيت الغروبي", if (s != null) ppHm(s, true) else "--", emphasize = true)
            InfoRow("بتوقيت الساعة", env.time(env.now, true))
        }
        Prayer.values().forEach { p ->
            val t = d.pr[p]
            val s = if (t != null) since(t) else null
            InfoRow(p.ar, if (t != null && s != null) "${ppHm(s)}  (${env.time(t)})" else "--")
        }
        Note("يُعدّ الوقت من لحظة الغروب مرتفعاً حتى 24 ساعة. من يستعمل ساعة من 12 يجعل الغروب 12:00، فيضيف 12 إلى القيم أعلاه ثم يطرح 12 عند التجاوز. المواقيت قبل المغرب تُنسب إلى غروب اليوم السابق.")
    }
}

@Composable
private fun PPSolarCard(env: Env, d: PPData, isToday: Boolean) {
    val offsetMin = env.lon / 15.0 * 60.0
    val clockOffMin = env.zone.rules.getOffset(Instant.ofEpochMilli(env.now)).totalSeconds / 60.0
    val diffMin = offsetMin + d.eot - clockOffMin
    GlassCard(title = "الوقت الشمسي", subtitle = "حسب خط طول الموقع ${Fmt.num(env.lon, 3)}°") {
        if (isToday) {
            val mean = env.now + (offsetMin * MIN_MS).toLong()
            val app = mean + (d.eot * MIN_MS).toLong()
            val h24 = env.settings.h24
            InfoRow("الوقت الشمسي الحقيقي (الظاهري)", Fmt.time(app, ZoneOffset.UTC, h24, true), emphasize = true)
            InfoRow("الوقت الشمسي المتوسط", Fmt.time(mean, ZoneOffset.UTC, h24, true))
            InfoRow("توقيت الساعة", env.time(env.now, true))
        } else {
            InfoRow("زوال الشمس الحقيقي (12:00 شمسي)", env.time(d.sun.transit, true))
        }
        InfoRow("معادلة الزمن لهذا اليوم", ppEot(d.eot))
        InfoRow("فرق الشمسي الحقيقي عن الساعة", "${if (diffMin < 0) "-" else "+"}${Fmt.num(Math.abs(diffMin), 1)} د")
        Note("معادلة الزمن: الفرق بين الوقت الشمسي الحقيقي والمتوسط بسبب ميل محور الأرض وشكل مدارها. القيمة الموجبة تعني أن الشمس الحقيقية متقدمة عن المتوسطة.")
    }
}

@Composable
private fun PPFastingCard(env: Env) {
    val o = AppTheme.ornament
    val s = env.settings
    val f = remember(env.today, s.hijriMethod, s.hijriAdjust) { ppFasting(env) }
    GlassCard(title = "أيام الصيام المستحبة القادمة") {
        InfoRow("الاثنين القادم (${ppRel(env, f.first)})", "${env.gregorian(f.first)}", emphasize = true)
        Text(env.hijri(f.first), color = o.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        InfoRow("الخميس القادم (${ppRel(env, f.second)})", "${env.gregorian(f.second)}", emphasize = true)
        Text(env.hijri(f.second), color = o.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        if (f.third.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("الأيام البيض (13 و14 و15 من الشهر الهجري)", color = o.gold, style = MaterialTheme.typography.titleSmall)
            f.third.forEach { b ->
                InfoRow("${env.hijri(b.date)} (${ppRel(env, b.date)})", env.gregorian(b.date))
                if (b.note.isNotEmpty()) Text(b.note, color = o.warning, style = MaterialTheme.typography.labelSmall)
            }
        }
        Note("صيام الاثنين والخميس وثلاثة أيام من كل شهر (وأشهرها الأيام البيض) من السنن المأثورة. يعتمد التاريخ الهجري على طريقة التقويم المختارة في الإعدادات وقد يختلف بيوم بحسب رؤية الهلال.")
    }
}

private fun ppShare(env: Env, date: LocalDate, d: PPData): String = buildString {
    appendLine("تفصيل الأوقات — ${env.place.name}")
    appendLine("${env.gregorian(date)} • ${env.hijri(date)}")
    appendLine()
    Prayer.values().forEach { appendLine("${it.ar}: ${env.time(d.pr[it])}") }
    appendLine()
    appendLine("أوقات الكراهة:")
    ppWindows(d).forEach { appendLine("- ${it.label}: ${env.time(it.start)} – ${env.time(it.end)}") }
    val mg = d.pr[Prayer.MAGHRIB]; val fN = d.nextPr[Prayer.FAJR]
    if (mg != null && fN != null && fN > mg) {
        val n = fN - mg
        appendLine()
        appendLine("منتصف الليل: ${env.time(mg + n / 2)}")
        appendLine("الثلث الأخير: ${env.time(mg + n * 2 / 3)}")
        appendLine("السحر: ${env.time(fN - n / 6)}")
    }
    appendLine("الوقت الشمسي — معادلة الزمن: ${ppEot(d.eot)}")
}
