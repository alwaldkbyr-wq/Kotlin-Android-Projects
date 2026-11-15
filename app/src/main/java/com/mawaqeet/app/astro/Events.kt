package com.mawaqeet.app.astro

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/* ───────────────────────── الشروق والغروب والعبور ───────────────────────── */

data class RiseSet(
    val rise: Long?,
    val transit: Long?,
    val set: Long?,
    val transitAlt: Double,
    /** دائم الظهور (لا يغيب) */
    val alwaysUp: Boolean,
    /** دائم الاختفاء (لا يطلع) */
    val alwaysDown: Boolean
)

object RiseSetCalc {
    /** الارتفاع المعياري لحافة الجرم عند الشروق/الغروب (درجات) بلا انكسار */
    fun standardAltitude(body: Body, distKm: Double): Double = when (body) {
        Body.SUN -> -0.8333
        Body.MOON -> -0.5667 - Moon.semiDiameter(distKm) // اختلاف المنظر مضمّن في الإحداثيات السطحية
        else -> -0.5667
    }

    /** ارتفاع مركز الجرم الهندسي (سطحي، دون انكسار) */
    fun geoAlt(body: Body, millis: Long, lat: Double, lon: Double): Double =
        Ephemeris.horizontal(body, millis, lat, lon, withRefraction = false).alt

    /**
     * يبحث في يوم محلي يبدأ عند [dayStart] (منتصف الليل المحلي) عن الطلوع والعبور والغروب.
     * [altitude] ارتفاع مخصّص (مثل −18 للفجر)؛ إن كان null استُعمل الارتفاع المعياري.
     */
    fun compute(body: Body, dayStart: Long, lat: Double, lon: Double, altitude: Double? = null, heightM: Double = 0.0): RiseSet {
        val step = 10 * MIN_MS
        val n = (DAY_MS / step).toInt()
        val dip = if (heightM > 0) 0.0347 * sqrt(heightM) else 0.0
        fun target(t: Long): Double {
            if (altitude != null) return altitude
            val d = if (body == Body.MOON) Moon.ecliptic(jdeOf(jdOf(t))).distKm else 0.0
            return standardAltitude(body, d) - dip
        }
        fun f(t: Long) = geoAlt(body, t, lat, lon) - target(t)
        var rise: Long? = null
        var set: Long? = null
        var prevT = dayStart
        var prev = f(prevT)
        var maxAlt = -999.0
        var maxT = dayStart
        var anyUp = prev > 0
        var allUp = prev > 0
        for (i in 1..n) {
            val t = dayStart + i * step
            val v = f(t)
            if (v > 0) anyUp = true else allUp = false
            if (prev <= 0 && v > 0 && rise == null) rise = refine(prevT, t) { f(it) }
            if (prev > 0 && v <= 0 && set == null) set = refine(prevT, t) { f(it) }
            val alt = v + target(t)
            if (alt > maxAlt) { maxAlt = alt; maxT = t }
            prev = v; prevT = t
        }
        // تدقيق العبور (أعلى ارتفاع) بالبحث الذهبي حول أعلى عيّنة
        var a = maxT - step; var b = maxT + step
        repeat(30) {
            val m1 = a + (b - a) / 3; val m2 = b - (b - a) / 3
            if (geoAlt(body, m1, lat, lon) < geoAlt(body, m2, lat, lon)) a = m1 else b = m2
        }
        val transit = (a + b) / 2
        val tAlt = geoAlt(body, transit, lat, lon)
        val inDay = transit in dayStart until dayStart + DAY_MS
        return RiseSet(rise, if (inDay) transit else null, set, tAlt + refraction(tAlt), allUp && rise == null && set == null, !anyUp)
    }

    inline fun refine(a0: Long, b0: Long, f: (Long) -> Double): Long {
        var a = a0; var b = b0
        val fa0 = f(a)
        repeat(18) {
            val m = (a + b) / 2
            val fm = f(m)
            if ((fa0 <= 0) == (fm <= 0)) a = m else b = m
        }
        return (a + b) / 2
    }
}

/* ───────────────────────── أطوار القمر ───────────────────────── */

enum class PhaseKind(val ar: String, val target: Double) {
    NEW("المحاق (الاقتران)", 0.0), FIRST("التربيع الأول", 90.0), FULL("البدر", 180.0), LAST("التربيع الأخير", 270.0)
}

data class PhaseEvent(val kind: PhaseKind, val millis: Long)

data class MoonState(
    val elongation: Double,       // استطالة القمر عن الشمس (درجات)
    val phaseAngle: Double,       // فرق الطول البروجي 0..360
    val illumination: Double,     // نسبة الإضاءة 0..1
    val ageDays: Double,          // عمر القمر منذ آخر اقتران
    val waxing: Boolean,
    val distKm: Double,
    val lastNew: Long,
    val nextNew: Long,
    val nextFull: Long
)

object Phases {
    const val SYNODIC = 29.530588853

    /** فرق الطول البروجي بين القمر والشمس 0..360 */
    fun lonDiff(millis: Long): Double {
        val jde = jdeOf(jdOf(millis))
        return norm360(Moon.ecliptic(jde).lon - Sun.ecliptic(jde).lon)
    }

    /** أول حدث لطور معيّن بعد الوقت المعطى */
    fun next(kind: PhaseKind, after: Long): Long {
        // تقدير أولي: الفرق الحالي ومعدّل تغيّر وسطي 12.19° يومياً
        val d = norm360(kind.target - lonDiff(after))
        var guess = after + (d / 360.0 * SYNODIC * DAY_MS).toLong()
        repeat(6) {
            val diff = norm180(lonDiff(guess) - kind.target)
            guess -= (diff / 12.190749 * DAY_MS).toLong()
        }
        if (guess < after) return next(kind, after + 2 * DAY_MS)
        return guess
    }

    fun previous(kind: PhaseKind, before: Long): Long {
        val d = norm360(lonDiff(before) - kind.target)
        var guess = before - (d / 360.0 * SYNODIC * DAY_MS).toLong()
        repeat(6) {
            val diff = norm180(lonDiff(guess) - kind.target)
            guess -= (diff / 12.190749 * DAY_MS).toLong()
        }
        if (guess > before) return previous(kind, before - 2 * DAY_MS)
        return guess
    }

    /** كل الأطوار في فترة */
    fun between(from: Long, to: Long): List<PhaseEvent> {
        val out = ArrayList<PhaseEvent>()
        for (k in PhaseKind.values()) {
            var t = next(k, from)
            while (t < to) { out += PhaseEvent(k, t); t = next(k, t + 20 * DAY_MS) }
        }
        return out.sortedBy { it.millis }
    }

    fun state(millis: Long): MoonState {
        val jde = jdeOf(jdOf(millis))
        val m = Moon.equatorial(jde)
        val s = Sun.equatorial(jde)
        val elong = separation(m.ra, m.dec, s.ra, s.dec)
        val i = atan2d(s.distKm * sind(elong), m.distKm - s.distKm * cosd(elong))
        val illum = (1 + cosd(i)) / 2
        val diff = lonDiff(millis)
        val lastNew = previous(PhaseKind.NEW, millis)
        return MoonState(
            elong, diff, illum, (millis - lastNew) / DAY_MS.toDouble(), diff < 180, m.distKm,
            lastNew, next(PhaseKind.NEW, millis), next(PhaseKind.FULL, millis)
        )
    }

    fun phaseName(diff: Double): String = when {
        diff < 6 || diff >= 354 -> "محاق"
        diff < 84 -> "هلال متزايد"
        diff < 96 -> "التربيع الأول"
        diff < 174 -> "أحدب متزايد"
        diff < 186 -> "بدر"
        diff < 264 -> "أحدب متناقص"
        diff < 276 -> "التربيع الأخير"
        else -> "هلال متناقص"
    }

    fun phaseEmoji(diff: Double): String = when {
        diff < 22.5 || diff >= 337.5 -> "🌑"
        diff < 67.5 -> "🌒"
        diff < 112.5 -> "🌓"
        diff < 157.5 -> "🌔"
        diff < 202.5 -> "🌕"
        diff < 247.5 -> "🌖"
        diff < 292.5 -> "🌗"
        else -> "🌘"
    }
}

/* ───────────────────────── الفصول (الاعتدالان والانقلابان) ───────────────────────── */

enum class SeasonKind(val ar: String, val lon: Double, val month: Int) {
    MARCH_EQUINOX("الاعتدال الربيعي", 0.0, 3), JUNE_SOLSTICE("الانقلاب الصيفي", 90.0, 6),
    SEPTEMBER_EQUINOX("الاعتدال الخريفي", 180.0, 9), DECEMBER_SOLSTICE("الانقلاب الشتوي", 270.0, 12)
}

data class SeasonEvent(val kind: SeasonKind, val millis: Long)

object Seasons {
    fun of(year: Int): List<SeasonEvent> = SeasonKind.values().map { k ->
        val jd0 = calendarToJd(year, k.month, 10.0)
        val a = jd0; val b = jd0 + 25
        val jd = bisect(a, b) { jd -> norm180(Sun.ecliptic(jdeOf(jd)).lon - k.lon) }
        SeasonEvent(k, millisOf(jd))
    }
}

/* ───────────────────────── الكسوف والخسوف ───────────────────────── */

enum class EclipseType(val ar: String) {
    SOLAR_TOTAL("كسوف كلي"), SOLAR_ANNULAR("كسوف حلقي"), SOLAR_HYBRID("كسوف هجين"), SOLAR_PARTIAL("كسوف جزئي"),
    LUNAR_TOTAL("خسوف كلي"), LUNAR_PARTIAL("خسوف جزئي"), LUNAR_PENUMBRAL("خسوف شبه ظلي")
}

data class Eclipse(
    val type: EclipseType,
    val max: Long,
    /** القدر: نسبة القطر المحجوب (للخسوف: قدر الظل أو شبه الظل) */
    val magnitude: Double,
    /** أقرب مسافة لمحور الظل بأنصاف أقطار الأرض (للكسوف) */
    val gamma: Double,
    /** مدة الطور الجزئي والكلي بالدقائق (للخسوف) */
    val partialMinutes: Double = 0.0,
    val totalMinutes: Double = 0.0,
    val penumbralMinutes: Double = 0.0
) { val isSolar get() = type.ordinal <= 3 }

object Eclipses {
    /** كل حالات الكسوف والخسوف بين وقتين */
    fun between(from: Long, to: Long): List<Eclipse> {
        val out = ArrayList<Eclipse>()
        var t = from
        while (t < to) {
            val nm = Phases.next(PhaseKind.NEW, t)
            if (nm < to) solar(nm)?.let { out += it }
            val fm = Phases.next(PhaseKind.FULL, t)
            if (fm < to) lunar(fm)?.let { out += it }
            t = minOf(nm, fm) + 10 * DAY_MS
        }
        return out.distinctBy { it.max / (DAY_MS) }.sortedBy { it.max }
    }

    private fun geom(millis: Long): DoubleArray {
        val jde = jdeOf(jdOf(millis))
        val m = Moon.equatorial(jde)
        val s = Sun.equatorial(jde)
        return doubleArrayOf(m.ra, m.dec, m.distKm, s.ra, s.dec, s.distKm)
    }

    /** يبحث عن أقل مسافة زاوية حول وقت معطى */
    private inline fun minimize(center: Long, f: (Long) -> Double): Long {
        var a = center - 6 * HOUR_MS; var b = center + 6 * HOUR_MS
        repeat(40) {
            val m1 = a + (b - a) / 3; val m2 = b - (b - a) / 3
            if (f(m1) < f(m2)) b = m2 else a = m1
        }
        return (a + b) / 2
    }

    fun solar(newMoon: Long): Eclipse? {
        val tMax = minimize(newMoon) { val g = geom(it); separation(g[0], g[1], g[3], g[4]) }
        val g = geom(tMax)
        val sep = separation(g[0], g[1], g[3], g[4])
        val moonPar = Moon.parallax(g[2]); val sunPar = 8.794 / 3600 / (g[5] / AU_KM)
        val moonSd = Moon.semiDiameter(g[2]); val sunSd = Sun.semiDiameter(g[5])
        // gamma: بعد محور ظل القمر عن مركز الأرض بأنصاف أقطارها
        val gamma = sep / (moonPar - sunPar)
        val u = 0.0046
        if (gamma > 1.5433 + u) return null
        val central = gamma < 0.9972
        val surfaceSd = moonSd * (1 + 0.0167 * kotlin.math.sqrt(kotlin.math.max(0.0, 1 - gamma * gamma)))
        val type = when {
            !central -> EclipseType.SOLAR_PARTIAL
            surfaceSd > sunSd && moonSd > sunSd -> EclipseType.SOLAR_TOTAL
            surfaceSd > sunSd -> EclipseType.SOLAR_HYBRID
            else -> EclipseType.SOLAR_ANNULAR
        }
        val mag = if (type == EclipseType.SOLAR_PARTIAL) ((1.5433 + u - gamma) / (0.5461 + 2 * u)).coerceIn(0.0, 1.0) else surfaceSd / sunSd
        return Eclipse(type, tMax, mag, gamma)
    }

    fun lunar(fullMoon: Long): Eclipse? {
        fun shadowGeom(t: Long): DoubleArray {
            val g = geom(t)
            // مركز الظل = النقطة المقابلة للشمس
            val ra = norm360(g[3] + 180); val dec = -g[4]
            val sep = separation(g[0], g[1], ra, dec)
            val moonPar = Moon.parallax(g[2]); val sunPar = 8.794 / 3600 / (g[5] / AU_KM)
            val sunSd = Sun.semiDiameter(g[5]); val moonSd = Moon.semiDiameter(g[2])
            val umbra = 1.02 * (moonPar + sunPar - sunSd)
            val penumbra = 1.02 * (moonPar + sunPar + sunSd)
            return doubleArrayOf(sep, umbra, penumbra, moonSd)
        }
        val tMax = minimize(fullMoon) { shadowGeom(it)[0] }
        val s = shadowGeom(tMax)
        val (sep, umbra, pen, sd) = listOf(s[0], s[1], s[2], s[3])
        if (sep > pen + sd) return null
        val umbMag = (umbra + sd - sep) / (2 * sd)
        val penMag = (pen + sd - sep) / (2 * sd)
        val type = when {
            umbMag >= 1 -> EclipseType.LUNAR_TOTAL
            umbMag > 0 -> EclipseType.LUNAR_PARTIAL
            else -> EclipseType.LUNAR_PENUMBRAL
        }
        // المدد: سرعة القمر النسبية للظل نحو 0.5° في الساعة تقريباً — نحسبها عددياً
        fun duration(r: Double): Double {
            if (sep >= r) return 0.0
            fun d(t: Long) = shadowGeom(t)[0] - r
            val a = RiseSetCalc.refine(tMax - 5 * HOUR_MS, tMax) { -d(it) }
            val b = RiseSetCalc.refine(tMax, tMax + 5 * HOUR_MS) { d(it) }
            return (b - a) / 60000.0
        }
        return Eclipse(
            type, tMax, if (umbMag > 0) umbMag else penMag, sep / (Moon.parallax(geom(tMax)[2])),
            partialMinutes = duration(umbra + sd), totalMinutes = duration(umbra - sd), penumbralMinutes = duration(pen + sd)
        )
    }
}

/* ───────────────────────── منازل القمر الثمانية والعشرون ───────────────────────── */

object Mansions {
    val names = listOf(
        "الشرطان", "البطين", "الثريا", "الدبران", "الهقعة", "الهنعة", "الذراع", "النثرة", "الطرف", "الجبهة",
        "الزبرة", "الصرفة", "العواء", "السماك", "الغفر", "الزبانى", "الإكليل", "القلب", "الشولة", "النعائم",
        "البلدة", "سعد الذابح", "سعد بلع", "سعد السعود", "سعد الأخبية", "الفرغ المقدم", "الفرغ المؤخر", "بطن الحوت"
    )
    /** بداية المنازل: نحو طول نجم الشرطان (β الحمل) في البروج، مع المبادرة */
    fun startLon(jde: Double) = 33.97 + 1.3969 * centuries(jde)
    const val SPAN = 360.0 / 28.0

    fun indexOf(lon: Double, jde: Double) = (floor(norm360(lon - startLon(jde)) / SPAN).toInt()) % 28
    /** المنزلة الحالية للقمر ونسبة قطعه منها */
    fun moonMansion(millis: Long): Pair<Int, Double> {
        val jde = jdeOf(jdOf(millis))
        val lon = Moon.ecliptic(jde).lon
        val rel = norm360(lon - startLon(jde))
        return (floor(rel / SPAN).toInt() % 28) to ((rel % SPAN) / SPAN)
    }
    /** منزلة الشمس (الطالع الشمسي) */
    fun sunMansion(millis: Long): Int { val jde = jdeOf(jdOf(millis)); return indexOf(Sun.ecliptic(jde).lon, jde) }
    /** الطول البروجي لبداية المنزلة [i] */
    fun lonOf(i: Int, jde: Double) = norm360(startLon(jde) + i * SPAN)

    /** وقت دخول الشمس في كل منزلة خلال سنة (الأنواء) */
    fun sunEntries(fromMillis: Long): List<Pair<Int, Long>> {
        val out = ArrayList<Pair<Int, Long>>()
        var t = fromMillis
        var cur = sunMansion(t)
        val end = fromMillis + 366 * DAY_MS
        while (t < end) {
            val n = t + DAY_MS
            val m = sunMansion(n)
            if (m != cur) {
                val at = RiseSetCalc.refine(t, n) { if (sunMansion(it) == cur) -1.0 else 1.0 }
                out += m to at; cur = m
            }
            t = n
        }
        return out
    }
}

/* ───────────────────────── رؤية الهلال (معياري يالوب وعودة) ───────────────────────── */

data class CrescentReport(
    val conjunction: Long,
    val sunset: Long?,
    val moonset: Long?,
    val lagMinutes: Double,       // مكث القمر بعد الشمس
    val ageHours: Double,         // عمر الهلال عند الغروب
    val moonAlt: Double,          // ارتفاع القمر عند الغروب
    val elongation: Double,       // الاستطالة (ARCL)
    val arcv: Double,             // فرق الارتفاع
    val daz: Double,              // فرق السمت
    val width: Double,            // عرض الهلال بالدقائق القوسية
    val illumination: Double,
    val yallopQ: Double,
    val yallop: Char,             // A..F
    val odehV: Double,
    val odeh: Char                // A..D
) {
    val yallopText get() = when (yallop) {
        'A' -> "يُرى بالعين المجردة بسهولة"
        'B' -> "يُرى بالعين المجردة في ظروف جيدة"
        'C' -> "قد يحتاج أداة بصرية لتحديد موضعه ثم يُرى بالعين"
        'D' -> "يُرى بالأداة البصرية فقط"
        'E' -> "لا يُرى بالأدوات البصرية العادية"
        else -> "لا يُرى (تحت حد دانجون أو قبل الاقتران)"
    }
    val odehText get() = when (odeh) {
        'A' -> "ممكنة بالعين المجردة"
        'B' -> "ممكنة بالأداة البصرية وقد تُرى بالعين"
        'C' -> "ممكنة بالأداة البصرية فقط"
        else -> "غير ممكنة حتى بالأدوات"
    }
}

object Crescent {
    /**
     * تقرير رؤية الهلال لموقع وليلة معيّنة. [dayStart] منتصف ليل اليوم المحلي.
     * يُحسب عند «أفضل وقت» = الغروب + 4/9 المكث (يالوب).
     */
    fun report(dayStart: Long, lat: Double, lon: Double): CrescentReport? {
        val sun = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon)
        val sunset = sun.set ?: return null
        val conj = Phases.previous(PhaseKind.NEW, sunset)
        var moonset = RiseSetCalc.compute(Body.MOON, dayStart, lat, lon).set
        if (moonset != null && moonset < sunset) moonset = RiseSetCalc.compute(Body.MOON, dayStart + DAY_MS, lat, lon).set
        val lag = if (moonset != null) (moonset - sunset) / 60000.0 else 0.0
        val best = sunset + ((if (lag > 0) lag else 0.0) * 4 / 9 * 60000).toLong()
        return at(best, sunset, moonset, conj, lat, lon, lag)
    }

    fun at(best: Long, sunset: Long?, moonset: Long?, conj: Long, lat: Double, lon: Double, lag: Double): CrescentReport {
        val jd = jdOf(best)
        val jde = jdeOf(jd)
        val lstD = lst(jd, lon)
        val mGeo = Moon.equatorial(jde)
        val mTop = topocentric(mGeo, lat, lon, 0.0, jd)
        val s = Sun.equatorial(jde)
        val mh = eqToHor(mTop.ra, mTop.dec, lat, lstD)
        val sh = eqToHor(s.ra, s.dec, lat, lstD)
        val mhGeo = eqToHor(mGeo.ra, mGeo.dec, lat, lstD)
        val arcl = separation(mTop.ra, mTop.dec, s.ra, s.dec)
        val arcv = mhGeo.alt - sh.alt
        val daz = norm180(sh.az - mh.az)
        // نصف القطر السطحي بالدقائق
        val sd = 0.27245 * Moon.parallax(mGeo.distKm) * 60 * (1 + sind(mh.alt) * sind(Moon.parallax(mGeo.distKm)))
        val w = sd * (1 - cosd(arcl))
        val q = (arcv - (11.8371 - 6.3226 * w + 0.7319 * w * w - 0.1018 * w * w * w)) / 10.0
        val v = arcv - (-0.1018 * w * w * w + 0.7319 * w * w - 6.3226 * w + 7.1651)
        val ageH = (best - conj) / 3_600_000.0
        val beforeConj = ageH < 0 || (moonset != null && sunset != null && moonset < sunset)
        val yc = when {
            beforeConj -> 'F'
            q > 0.216 -> 'A'; q > -0.014 -> 'B'; q > -0.160 -> 'C'; q > -0.232 -> 'D'; q > -0.293 -> 'E'; else -> 'F'
        }
        val oc = when { beforeConj -> 'D'; v >= 5.65 -> 'A'; v >= 2.0 -> 'B'; v >= -0.96 -> 'C'; else -> 'D' }
        val illum = (1 - cosd(arcl)) / 2
        return CrescentReport(conj, sunset, moonset, lag, ageH, mh.alt + refraction(mh.alt), arcl, arcv, daz, w, illum, q, yc, v, oc)
    }

    /**
     * تصنيف يالوب السريع لنقطة على الخريطة (للخرائط الشبكية): يستعمل الغروب التحليلي
     * للشمس لتقليل الحساب. يعيد null إن لم تغرب الشمس.
     */
    fun quickClass(dayUtcMidnight: Long, lat: Double, lon: Double, conj: Long): Char? {
        // تقدير الغروب: منتصف النهار الشمسي + زاوية الساعة
        val noonGuess = dayUtcMidnight + ((12.0 - lon / 15.0) * HOUR_MS).toLong()
        val jde = jdeOf(jdOf(noonGuess))
        val s = Sun.equatorial(jde)
        val cosH = (sind(-0.8333) - sind(lat) * sind(s.dec)) / (cosd(lat) * cosd(s.dec))
        if (cosH.isNaN() || cosH < -1 || cosH > 1) return null
        val h = acosd(cosH)
        val eot = Sun.equationOfTime(jde)
        val sunset = dayUtcMidnight + ((12.0 - lon / 15.0 - eot / 60.0 + h / 15.0) * HOUR_MS).toLong()
        if (sunset < conj) return 'F'
        // مكث تقريبي: نبحث عن غروب القمر خلال 3 ساعات
        var moonset: Long? = null
        var prev = RiseSetCalc.geoAlt(Body.MOON, sunset, lat, lon) + 0.83
        if (prev <= 0) return 'F'
        var t = sunset
        while (t < sunset + 4 * HOUR_MS) {
            val n = t + 10 * MIN_MS
            val v = RiseSetCalc.geoAlt(Body.MOON, n, lat, lon) + 0.83
            if (prev > 0 && v <= 0) { moonset = n; break }
            prev = v; t = n
        }
        val lag = ((moonset ?: (sunset + 4 * HOUR_MS)) - sunset) / 60000.0
        val best = sunset + (lag * 4 / 9 * 60000).toLong()
        return at(best, sunset, moonset, conj, lat, lon, lag).yallop
    }
}

/* ───────────────────────── الليل والنهار والشفق ───────────────────────── */

data class DayNight(
    val sunrise: Long?, val sunset: Long?, val noon: Long?,
    val civilDawn: Long?, val civilDusk: Long?,
    val nauticalDawn: Long?, val nauticalDusk: Long?,
    val astroDawn: Long?, val astroDusk: Long?,
    val dayLengthMin: Double, val nightLengthMin: Double,
    val midnight: Long?, val lastThird: Long?,
    val noonAlt: Double, val polarDay: Boolean, val polarNight: Boolean
)

object DayNightCalc {
    fun compute(dayStart: Long, lat: Double, lon: Double, heightM: Double = 0.0): DayNight {
        val s = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, heightM = heightM)
        val c = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, -6.0)
        val n = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, -12.0)
        val a = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, -18.0)
        val nextRise = RiseSetCalc.compute(Body.SUN, dayStart + DAY_MS, lat, lon).rise
        val dayLen = when {
            s.alwaysUp -> 1440.0
            s.alwaysDown -> 0.0
            s.rise != null && s.set != null -> (s.set - s.rise) / 60000.0
            else -> 0.0
        }
        var mid: Long? = null; var third: Long? = null
        if (s.set != null && nextRise != null) {
            val night = nextRise - s.set
            mid = s.set + night / 2
            third = s.set + night * 2 / 3
        }
        return DayNight(
            s.rise, s.set, s.transit, c.rise, c.set, n.rise, n.set, a.rise, a.set,
            dayLen, 1440.0 - dayLen, mid, third, s.transitAlt, s.alwaysUp, s.alwaysDown
        )
    }
}

fun fmtDuration(minutes: Double): String {
    if (minutes.isNaN()) return "--"
    val m = Math.round(abs(minutes)).toInt()
    return "%d س %02d د".format(java.util.Locale.US, m / 60, m % 60)
}
