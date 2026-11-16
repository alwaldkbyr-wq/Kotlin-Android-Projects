package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.HOUR_MS
import com.mawaqeet.app.astro.Horizontal
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.Star
import com.mawaqeet.app.astro.StarCatalog
import com.mawaqeet.app.astro.Sun
import com.mawaqeet.app.astro.eqToHor
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.lst
import com.mawaqeet.app.astro.norm180
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.CanvasFonts
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** «الاتجاه بالشمس والنجوم»: معرفة الشمال دون بوصلة بحساب الشمس والنجوم لموقعك ووقتك */
@Composable
fun NaturalNavScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val now = env.now
    val lat = env.lat
    val lon = env.lon
    val north = lat >= 0

    val sun = Ephemeris.horizontal(Body.SUN, now, lat, lon)
    val moon = Ephemeris.horizontal(Body.MOON, now, lat, lon)
    val dayStart = env.dayStart(env.today)

    // الظهيرة الشمسية واتجاه الظل عندها
    val noon = remember(dayStart, lat, lon) { RiseSetCalc.compute(Body.SUN, dayStart, lat, lon) }
    val noonDec = remember(noon.transit) {
        noon.transit?.let { t -> Sun.equatorial(jdeOf(jdOf(t))).dec }
    }

    // القمر: الطور وطلوعه وغروبه اليوم
    val moonState = remember(now / 60_000L) { Phases.state(now) }
    val moonRs = remember(dayStart, lat, lon) { RiseSetCalc.compute(Body.MOON, dayStart, lat, lon) }
    val moonRiseAz = remember(moonRs.rise, lat, lon) { moonRs.rise?.let { Ephemeris.horizontal(Body.MOON, it, lat, lon).az } }
    val moonSetAz = remember(moonRs.set, lat, lon) { moonRs.set?.let { Ephemeris.horizontal(Body.MOON, it, lat, lon).az } }

    // النجوم: الارتفاع والسمت الآن (تُحدَّث كل 10 ثوانٍ)
    val skyStars: List<Pair<Star, Horizontal>> = remember(now / 10_000L, lat, lon) {
        val jd = jdOf(now)
        val jde = jdeOf(jd)
        val l = lst(jd, lon)
        StarCatalog.stars.map { s ->
            val p = SkyMath.precess(s.ra, s.dec, jde)
            s to eqToHor(p.ra, p.dec, lat, l)
        }
    }
    val byName: Map<String, Horizontal> = remember(skyStars) { skyStars.associate { it.first.latin to it.second } }

    // الوقت الشمسي المحلي
    val eot = Sun.equationOfTime(jdeOf(jdOf(now)))
    val solarMs = now + (lon / 15.0 * HOUR_MS).toLong() + (eot * MIN_MS).toLong()
    val solarOfDay = Math.floorMod(solarMs, DAY_MS)
    val offsetMs = env.zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds * 1000L
    val clockOfDay = Math.floorMod(now + offsetMs, DAY_MS)
    var diffMin = (solarOfDay - clockOfDay) / 60000.0
    if (diffMin > 720) diffMin -= 1440.0
    if (diffMin < -720) diffMin += 1440.0
    val solarText = nnClock(solarOfDay)

    val sunUp = sun.alt > 3.0
    val shadowAz = norm360(sun.az + 180.0)
    val shadowLen = if (sun.alt > 0.5) 1.0 / tan(Math.toRadians(sun.alt)) else Double.NaN
    val shadowLenText = when {
        shadowLen.isNaN() -> "--"
        shadowLen > 50 -> "أكثر من 50 م"
        else -> Fmt.num(shadowLen, 2) + " م"
    }
    val delta = norm180(-sun.az) // موضع الشمال بالنسبة لاتجاه الشمس (موجب = يمين)
    val sunInstruction = when {
        abs(delta) < 3 -> "الشمس في جهة الشمال تقريباً: الشمال أمامك مباشرة."
        abs(delta) > 177 -> "الشمس في جهة الجنوب تقريباً: الشمال خلفك مباشرة."
        else -> {
            val side = if (delta > 0) "يمينك" else "يسارك"
            val extra = if (abs(delta) > 100) " (أي خلف كتفك " + (if (delta > 0) "الأيمن" else "الأيسر") + ")" else ""
            "قف ووجهك نحو الشمس: الشمال على $side بزاوية ${Fmt.num(abs(delta), 0)}°$extra"
        }
    }

    val dark = sun.alt < -6.0
    val polaris = byName["Polaris"]
    val dubhe = byName["Dubhe"]
    val merak = byName["Merak"]
    val schedar = byName["Schedar"]
    val caph = byName["Caph"]
    val acrux = byName["Acrux"]
    val gacrux = byName["Gacrux"]
    val alnilam = byName["Alnilam"]
    val polarisVisible = polaris != null && polaris.alt > 0.5 && lat > 2.0
    val brightNow = remember(skyStars) {
        skyStars.filter { it.first.mag <= 1.9 && it.second.alt > 10.0 }.sortedBy { it.first.mag }.take(8)
    }

    fun upText(h: Horizontal?): String =
        if (h == null) "--" else if (h.alt > 0) "فوق الأفق • ${Fmt.direction(h.az)} • ارتفاع ${Fmt.num(h.alt, 0)}°" else "تحت الأفق الآن"

    val shareText = buildString {
        appendLine("الاتجاه بالشمس والنجوم — ${Fmt.latLon(lat, lon)}")
        appendLine("الشمس: سمت ${Fmt.num(sun.az, 0)}° (${Fmt.direction(sun.az)}) • ارتفاع ${Fmt.num(sun.alt, 0)}°")
        if (sunUp) {
            appendLine(sunInstruction)
            appendLine("اتجاه الظل: ${Fmt.num(shadowAz, 0)}° • طول ظل عصا 1 م: $shadowLenText")
        }
        noon.transit?.let { appendLine("الظهيرة الشمسية: ${env.time(it)}") }
        appendLine("الوقت الشمسي المحلي: $solarText")
        if (polarisVisible && polaris != null) appendLine("النجم القطبي: ارتفاع ${Fmt.num(polaris.alt, 0)}° نحو الشمال الحقيقي")
        appendLine("الطرق تقريبية بدقة 10–15° تقريباً.")
    }

    ScreenScaffold(title = "الاتجاه بالشمس والنجوم", onBack = env.back, subtitle = "معرفة الشمال دون بوصلة", actions = { ShareAction { shareText } }) {
        ScreenList {
            item {
                GlassCard(title = "الأفق الآن", subtitle = Fmt.latLon(lat, lon)) {
                    NNDiagram(sun, moon, if (polarisVisible) polaris else null, shadowLen, Modifier.fillMaxWidth().height(290.dp))
                    NNPara("الدائرة هي الأفق والمركز فوق رأسك (قمة السماء) والشمال في الأعلى. ☀ الشمس • ☾ القمر • ★ النجم القطبي • الخط السميك ظل عصا قائمة.")
                }
            }

            // ───────── بالشمس ─────────
            item { SectionTitle("بالشمس (نهاراً)") }
            item {
                GlassCard(title = "الشمس الآن") {
                    MetricRow(
                        "السمت" to Fmt.deg(sun.az, 0),
                        "الارتفاع" to Fmt.deg(sun.alt, 0),
                        "الجهة" to Fmt.direction(sun.az)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (sunUp) {
                        NNStrong(sunInstruction)
                        InfoRow("اتجاه الظل", "${Fmt.num(shadowAz, 0)}° (${Fmt.direction(shadowAz)})")
                        InfoRow("طول ظل عصا بطول 1 م", shadowLenText)
                        NNPara("الظل يشير دائماً إلى عكس جهة الشمس. كلما ارتفعت الشمس قصر الظل ودقّت القراءة المحلية للاتجاه.")
                    } else if (sun.alt > -1.0) {
                        NNPara("الشمس عند الأفق: هي الآن قريبة من الشرق (شروق) أو الغرب (غروب) بحسب الوقت، وهذا أفضل وقت لمعرفة الشرق والغرب تقريباً: السمت الفعلي ${Fmt.num(sun.az, 0)}° (${Fmt.direction(sun.az)}).")
                    } else {
                        NNPara("الشمس تحت الأفق الآن؛ استعمل النجوم أو القمر في الأقسام التالية.")
                    }
                }
            }
            item {
                GlassCard(title = "الظهيرة الشمسية") {
                    val t = noon.transit
                    if (t == null) {
                        NNPara("لا توجد ظهيرة محددة لهذا اليوم في موقعك.")
                    } else {
                        InfoRow("وقت الظهيرة الشمسية (بتوقيت الساعة)", env.time(t), emphasize = true)
                        val dec = noonDec
                        if (dec != null) {
                            val gap = lat - dec
                            val msg = when {
                                abs(gap) < 2.0 -> "الشمس تكاد تكون فوق رأسك عند الظهيرة، فيقصر الظل جداً ولا يصلح لتحديد الشمال."
                                gap > 0 -> "عند هذا الوقت تكون الشمس في أعلى ارتفاع لها في جهة الجنوب، فيشير ظل العصا إلى الشمال الحقيقي تماماً."
                                else -> "عند هذا الوقت تكون الشمس في أعلى ارتفاع لها في جهة الشمال (الميل الشمسي أكبر من خط عرضك)، فيشير ظل العصا إلى الجنوب الحقيقي تماماً."
                            }
                            NNPara(msg)
                        }
                        NNPara("أقصر ظل في اليوم هو لحظة الظهيرة الشمسية، وهو أدقّ خط شمال–جنوب تحصل عليه.")
                    }
                }
            }
            item {
                GlassCard(title = "الوقت الشمسي المحلي") {
                    MetricRow(
                        "الوقت الشمسي" to solarText,
                        "الفرق عن ساعتك" to nnSigned(diffMin) + " د"
                    )
                    NNPara("الساعة الشمسية: الظهيرة فيها عند الثانية عشرة تماماً. تحسبها من خط طولك ومعادلة الزمن (الآن ${nnSigned(eot)} د). فرق ساعتك الرسمية عنها يشمل المنطقة الزمنية والتوقيت الصيفي إن وُجد.")
                }
            }
            item {
                GlassCard(title = "طريقة عصا الظل (الأدق)") {
                    NNSteps(
                        listOf(
                            "ثبّت عصا مستقيمة بطول متر تقريباً (أو أي شيء قائم) في أرض مستوية مشمسة.",
                            "ضع علامة (حجر) على رأس الظل الآن: هذه العلامة الأولى وهي تقع جهة الغرب.",
                            "انتظر نحو 15 دقيقة (أطول أفضل) حتى يتحرك الظل عدة سنتيمترات، ثم علّم رأس الظل الجديد: العلامة الثانية جهة الشرق.",
                            "ارسم خطاً بين العلامتين: هو خط شرق–غرب في أي مكان من الأرض وفي أي فصل. قف وقدمك اليسرى عند العلامة الأولى (الغرب) واليمنى عند الثانية (الشرق): فيكون الشمال أمامك مباشرة."
                        )
                    )
                    NNPara("الطريقة مأخوذة من دليل الجيش الأمريكي FM 21-76 وتصلح من الصباح إلى العصر.")
                }
            }
            item {
                GlassCard(title = "طريقة الساعة التناظرية") {
                    if (north) {
                        NNSteps(
                            listOf(
                                "افرد الساعة أفقياً وأدِرها حتى يشير عقرب الساعات (القصير) نحو الشمس.",
                                "الجنوب هو منتصف الزاوية بين عقرب الساعات والرقم 12.",
                                "الشمال في الاتجاه المعاكس لذلك."
                            )
                        )
                    } else {
                        NNSteps(
                            listOf(
                                "افرد الساعة أفقياً وأدِرها حتى يشير الرقم 12 نحو الشمس.",
                                "الشمال هو منتصف الزاوية بين الرقم 12 وعقرب الساعات.",
                                "الجنوب في الاتجاه المعاكس لذلك."
                            )
                        )
                    }
                    InfoRow("اضبط الساعة على", solarText + " (الوقت الشمسي)")
                    val err = abs(diffMin) * 0.25
                    NNPara("الطريقة تفترض أن الساعة تشير إلى الوقت الشمسي المحلي. إن استعملت ساعتك الرسمية فالخطأ ≈ ${Fmt.num(err, 0)}° الآن (ربع درجة لكل دقيقة فرق)؛ والأفضل ضبطها على الوقت الشمسي أعلاه. استعمل ساعة بعقارب فقط، وتقلّ دقة الطريقة قرب خط الاستواء حين تكون الشمس شبه عمودية.")
                }
            }

            // ───────── بالنجوم ─────────
            item { SectionTitle("بالنجوم (ليلاً)") }
            item {
                GlassCard(title = "النجم القطبي (الشمال الحقيقي)") {
                    if (!dark) NNWarn("ما زال الضوء ظاهراً؛ تظهر النجوم بعد الغروب بنحو 30–40 دقيقة.")
                    if (lat <= 2.0) {
                        NNPara("لا يظهر النجم القطبي من خط عرضك (يحتاج خط عرض أكبر من نحو 2° شمالاً). استعمل مسار النجوم (طلوع الجبار شرقاً) أو القمر.")
                    } else if (polaris != null) {
                        MetricRow(
                            "ارتفاعه" to Fmt.deg(polaris.alt, 1),
                            "سمته" to Fmt.deg(polaris.az, 1),
                            "خط عرضك" to Fmt.deg(lat, 1)
                        )
                        NNPara(
                            if (polaris.alt > 0.5) "النجم القطبي فوق الأفق الآن في اتجاه الشمال الحقيقي (انحرافه عنه أقل من درجة ونصف). ارتفاعه فوق الأفق يساوي خط عرضك تقريباً؛ فإذا وقفت وأنت تنظر إليه فالشمال أمامك."
                            else "النجم القطبي تحت الأفق الآن (قريب جداً منه)."
                        )
                        if (lat < 15) NNPara("قرب خط الاستواء يكون النجم القطبي منخفضاً جداً وغالباً محجوباً بالغبار فاستعن بالكوكبات التالية.")
                    }
                }
            }
            item {
                GlassCard(title = "العثور عليه: الدب الأكبر وذات الكرسي") {
                    InfoRow("الدب الأكبر: ظهر (Dubhe)", upText(dubhe))
                    InfoRow("الدب الأكبر: المراق (Merak)", upText(merak))
                    InfoRow("ذات الكرسي: الصدر (Schedar)", upText(schedar))
                    InfoRow("ذات الكرسي: الكف الخضيب (Caph)", upText(caph))
                    Spacer(Modifier.height(6.dp))
                    NNPara("بنات نعش (الدب الأكبر): النجمان الخارجيان في «الكأس»، المراق ثم ظهر (Merak ثم Dubhe)، يشكّلان المؤشّر. مدّ خطاً بينهما من المراق إلى الظهر وكرّر المسافة بينهما نحو 5 مرات فتصل إلى النجم القطبي.")
                    NNPara("ذات الكرسي (Cassiopeia) على شكل حرف W أو M في الجهة المقابلة للنجم القطبي. ارسم خطاً عمودياً من وسط الحرف نحو النجم القطبي: هي تساعدك حين يغيب الدب الأكبر تحت الأفق.")
                    NNPara("لأن السماء تدور، قد يكون أحد الكوكبتين منخفضاً أو غائباً؛ ابحث عن الأخرى.")
                }
            }
            item {
                GlassCard(title = "الصليب الجنوبي (للمواقع الجنوبية)") {
                    if (lat > 27) {
                        NNPara("لا يظهر الصليب الجنوبي من خط عرضك الشمالي الحالي. هذه الطريقة لمن هم جنوب خط عرض 27° شمالاً تقريباً.")
                    } else {
                        InfoRow("ألفا الصليب (Acrux)", upText(acrux))
                        InfoRow("غاما الصليب (Gacrux)", upText(gacrux))
                        NNPara("مدّ المحور الطويل للصليب من غاما إلى ألفا (من الرأس إلى القدم) نحو 4.5 أضعاف طوله، ثم أنزل خطاً عمودياً من تلك النقطة إلى الأفق: موضع هبوطه هو الجنوب الحقيقي تقريباً.")
                        NNPara("القطب الجنوبي السماوي ارتفاعه فوق الأفق الجنوبي يساوي " + Fmt.num(if (lat < 0) -lat else 0.0, 0) + "° (خط عرضك بالقيمة المطلقة)." + if (lat >= 0) " وهو تحت الأفق في نصف الكرة الشمالي." else "")
                    }
                }
            }
            item {
                GlassCard(title = "حزام الجبار") {
                    InfoRow("النظام (Alnilam) — النجم الأوسط", upText(alnilam))
                    NNPara("نجوم حزام الجبار الثلاثة تقع تقريباً على خط الاستواء السماوي، فتطلع من الشرق تماماً وتغرب في الغرب تماماً تقريباً من أي مكان على الأرض؛ فاستعملها لمعرفة الشرق والغرب في الليالي التي ترى فيها الجبار (في فصل الشتاء في نصف الكرة الشمالي).")
                }
            }
            item {
                GlassCard(title = "نجوم ظاهرة الآن للاتجاه") {
                    if (!dark) NNWarn("ما زال النهار أو الشفق؛ القائمة تعرض ما سيظهر بعد الغروب.")
                    if (brightNow.isEmpty()) {
                        NNPara("لا توجد نجوم ساطعة فوق 10° الآن.")
                    } else {
                        brightNow.forEach { (s, h) ->
                            InfoRow(s.ar + " (" + s.latin + ")", "${Fmt.direction(h.az)} • ${Fmt.num(h.az, 0)}° • ارتفاع ${Fmt.num(h.alt, 0)}°")
                        }
                        NNPara("السمت يقاس من الشمال الحقيقي باتجاه الشرق (0 شمال، 90 شرق، 180 جنوب، 270 غرب). حدّد نجماً لامعاً من القائمة، ثم تحرّك بالنسبة له وحافظ على الاتجاه: النجوم تتحرك مع الوقت فاختر نجماً جديداً كل 15–20 دقيقة.")
                    }
                }
            }

            // ───────── بالقمر ─────────
            item { SectionTitle("بالقمر") }
            item {
                GlassCard(title = "القمر الآن", subtitle = Phases.phaseName(moonState.phaseAngle) + " • إضاءة " + Fmt.num(moonState.illumination * 100, 0) + "%") {
                    if (moon.alt > 0) {
                        MetricRow("السمت" to Fmt.deg(moon.az, 0), "الارتفاع" to Fmt.deg(moon.alt, 0), "الجهة" to Fmt.direction(moon.az))
                    } else {
                        NNPara("القمر تحت الأفق الآن.")
                    }
                    InfoRow("طلوع القمر اليوم", (if (moonRs.rise == null) "--" else env.time(moonRs.rise)) + (moonRiseAz?.let { " • " + Fmt.direction(it) + " ${Fmt.num(it, 0)}°" } ?: ""))
                    InfoRow("غروب القمر اليوم", (if (moonRs.set == null) "--" else env.time(moonRs.set)) + (moonSetAz?.let { " • " + Fmt.direction(it) + " ${Fmt.num(it, 0)}°" } ?: ""))
                    Spacer(Modifier.height(6.dp))
                    NNPara("القمر مثل الشمس يطلع من جهة الشرق تقريباً ويغرب في الغرب؛ مواضع الطلوع والغروب أعلاه بالحساب لموقعك.")
                    if (moonState.illumination in 0.02..0.5) {
                        NNPara(
                            if (north) "الهلال: ارسم خطاً وهمياً يمرّ بقرنَي الهلال وامدده نحو الأفق؛ فيشير تقريباً نحو الجنوب في نصف الكرة الشمالي."
                            else "الهلال: ارسم خطاً وهمياً يمرّ بقرنَي الهلال وامدده نحو الأفق؛ فيشير تقريباً نحو الشمال في نصف الكرة الجنوبي."
                        )
                    } else {
                        NNPara("طريقة القرنين تصلح للهلال فقط (إضاءة أقل من نحو النصف). والبدر يطلع قرب غروب الشمس من الشرق، فيدلّك على الشرق وقتها.")
                    }
                    NNWarn("طريقة القرنين تقريبية جداً (قد يصل الخطأ إلى 20° أو أكثر) وتتأثر بالموسم وبخط العرض؛ استعملها حين لا توجد وسيلة أفضل.")
                }
            }
            item { Note("دقة هذه الطرق نحو 10–15° إذا طُبّقت بعناية، فهي تكفي للتوجّه العام وليست بديلاً عن البوصلة أو GPS. تحقّق من الاتجاه بأكثر من طريقة حين تستطيع.") }
        }
    }
}

// ───────────── عناصر مساعدة خاصة بهذه الشاشة ─────────────

private fun nnClock(msOfDay: Long): String {
    val m = (msOfDay / 60000L).toInt()
    return "%02d:%02d".format(Locale.US, m / 60, m % 60)
}

private fun nnSigned(v: Double): String = (if (v >= 0) "+" else "−") + Fmt.num(abs(v), 1)

@Composable
private fun NNPara(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium, color = AppTheme.ornament.ink)
}

@Composable
private fun NNStrong(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun NNWarn(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 3.dp), style = MaterialTheme.typography.bodySmall, color = AppTheme.ornament.warning)
}

@Composable
private fun NNSteps(steps: List<String>) {
    Column {
        steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${i + 1}.", color = AppTheme.ornament.gold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppTheme.ornament.ink)
            }
        }
    }
}

private fun DrawScope.nnText(text: String, x: Float, y: Float, sizePx: Float, color: Color) {
    val p = android.graphics.Paint().apply {
        textSize = sizePx
        this.color = color.toArgb()
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = CanvasFonts.text
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

/** رسم الأفق: الشمال في الأعلى، المركز قمة السماء، ونصف القطر يمثل (90 − الارتفاع) */
@Composable
private fun NNDiagram(sun: Horizontal, moon: Horizontal, polaris: Horizontal?, shadowLen: Double, modifier: Modifier) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val ink = o.ink
    val muted = o.muted
    val gold = o.gold
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f - 28.dp.toPx()
        val c = Offset(cx, cy)
        fun pt(az: Double, alt: Double): Offset {
            val rr = r * ((90.0 - alt).coerceIn(0.0, 90.0) / 90.0).toFloat()
            val a = Math.toRadians(az)
            return Offset(cx + rr * sin(a).toFloat(), cy - rr * cos(a).toFloat())
        }
        drawCircle(muted.copy(alpha = 0.6f), r, c, style = Stroke(1.6.dp.toPx()))
        drawCircle(muted.copy(alpha = 0.25f), r * 2f / 3f, c, style = Stroke(1.dp.toPx()))
        drawCircle(muted.copy(alpha = 0.25f), r / 3f, c, style = Stroke(1.dp.toPx()))
        drawLine(muted.copy(alpha = 0.3f), Offset(cx, cy - r), Offset(cx, cy + r), 1.dp.toPx())
        drawLine(muted.copy(alpha = 0.3f), Offset(cx - r, cy), Offset(cx + r, cy), 1.dp.toPx())
        val ts = 15.dp.toPx()
        nnText("ش", cx, cy - r - 8.dp.toPx(), ts, primary)
        nnText("ج", cx, cy + r + 19.dp.toPx(), ts, ink)
        nnText("ق", cx + r + 14.dp.toPx(), cy + 5.dp.toPx(), ts, ink)
        nnText("غ", cx - r - 14.dp.toPx(), cy + 5.dp.toPx(), ts, ink)

        // عصا وظلها
        if (sun.alt > 2.0 && !shadowLen.isNaN()) {
            val frac = (min(shadowLen, 4.0) / 4.0).toFloat().coerceIn(0.08f, 1f)
            val a = Math.toRadians(norm360(sun.az + 180.0))
            val end = Offset(cx + r * 0.92f * frac * sin(a).toFloat(), cy - r * 0.92f * frac * cos(a).toFloat())
            drawLine(ink, c, end, 5.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(ink, 4.dp.toPx(), c)

        // الشمس
        if (sun.alt > -2.0) {
            val p = pt(sun.az, sun.alt.coerceAtLeast(0.0))
            drawCircle(gold.copy(alpha = 0.3f), 20.dp.toPx(), p)
            drawCircle(gold, 10.dp.toPx(), p)
            nnText("☀", p.x, p.y - 17.dp.toPx(), 13.dp.toPx(), gold)
        }
        // القمر
        if (moon.alt > 0) {
            val p = pt(moon.az, moon.alt)
            drawCircle(ink.copy(alpha = 0.25f), 12.dp.toPx(), p)
            drawCircle(ink.copy(alpha = 0.8f), 7.dp.toPx(), p)
            nnText("☾", p.x, p.y - 15.dp.toPx(), 13.dp.toPx(), ink)
        }
        // النجم القطبي
        if (polaris != null && polaris.alt > 0) {
            val p = pt(polaris.az, polaris.alt)
            nnText("★", p.x, p.y + 5.dp.toPx(), 16.dp.toPx(), primary)
        }
    }
}
