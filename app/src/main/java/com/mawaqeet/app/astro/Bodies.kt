package com.mawaqeet.app.astro

import kotlin.math.abs
import kotlin.math.sqrt

/** الأجرام التي يحسب التطبيق مواقعها */
enum class Body(val ar: String, val symbol: String) {
    SUN("الشمس", "☉"), MOON("القمر", "☾"),
    MERCURY("عطارد", "☿"), VENUS("الزهرة", "♀"), MARS("المريخ", "♂"),
    JUPITER("المشتري", "♃"), SATURN("زحل", "♄"), URANUS("أورانوس", "♅"), NEPTUNE("نبتون", "♆");

    val isPlanet get() = ordinal >= 2
}

const val AU_KM = 149_597_870.7

/* ───────────────────────── الشمس (Meeus، الفصل 25، دقة نحو 0.01°) ───────────────────────── */
object Sun {
    /** الطول البروجي الظاهري والبعد (كم) */
    fun ecliptic(jde: Double): Ecliptic {
        val t = centuries(jde)
        val l0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val m = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sind(m) +
            (0.019993 - 0.000101 * t) * sind(2 * m) + 0.000289 * sind(3 * m)
        val trueLon = l0 + c
        val v = m + c
        val r = 1.000001018 * (1 - e * e) / (1 + e * cosd(v))
        val om = 125.04 - 1934.136 * t
        val app = trueLon - 0.00569 - 0.00478 * sind(om)
        return Ecliptic(norm360(app), 0.0, r * AU_KM)
    }

    fun equatorial(jde: Double): Equatorial {
        val ec = ecliptic(jde)
        val eq = eclToEq(ec.lon, 0.0, obliquity(jde))
        return Equatorial(eq.ra, eq.dec, ec.distKm)
    }

    /** معادلة الزمن بالدقائق */
    fun equationOfTime(jde: Double): Double {
        val t = centuries(jde)
        val l0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val eq = equatorial(jde)
        val (dPsi, _) = nutation(jde)
        val e = l0 - 0.0057183 - eq.ra + dPsi * cosd(obliquity(jde))
        return norm180(e) * 4.0
    }

    /** نصف القطر الظاهري (درجات) */
    fun semiDiameter(distKm: Double) = 959.63 / 3600.0 / (distKm / AU_KM)
}

/* ───────────────────────── القمر (Meeus، الفصل 47، حدود رئيسية) ───────────────────────── */
object Moon {
    // D, M, M', F, Σl (1e-6°), Σr (1e-3 كم)
    private val LR = arrayOf(
        intArrayOf(0, 0, 1, 0, 6288774, -20905355), intArrayOf(2, 0, -1, 0, 1274027, -3699111),
        intArrayOf(2, 0, 0, 0, 658314, -2955968), intArrayOf(0, 0, 2, 0, 213618, -569925),
        intArrayOf(0, 1, 0, 0, -185116, 48888), intArrayOf(0, 0, 0, 2, -114332, -3149),
        intArrayOf(2, 0, -2, 0, 58793, 246158), intArrayOf(2, -1, -1, 0, 57066, -152138),
        intArrayOf(2, 0, 1, 0, 53322, -170733), intArrayOf(2, -1, 0, 0, 45758, -204586),
        intArrayOf(0, 1, -1, 0, -40923, -129620), intArrayOf(1, 0, 0, 0, -34720, 108743),
        intArrayOf(0, 1, 1, 0, -30383, 104755), intArrayOf(2, 0, 0, -2, 15327, 10321),
        intArrayOf(0, 0, 1, 2, -12528, 0), intArrayOf(0, 0, 1, -2, 10980, 79661),
        intArrayOf(4, 0, -1, 0, 10675, -34782), intArrayOf(0, 0, 3, 0, 10034, -23210),
        intArrayOf(4, 0, -2, 0, 8548, -21636), intArrayOf(2, 1, -1, 0, -7888, 24208),
        intArrayOf(2, 1, 0, 0, -6766, 30824), intArrayOf(1, 0, -1, 0, -5163, -8379),
        intArrayOf(1, 1, 0, 0, 4987, -16675), intArrayOf(2, -1, 1, 0, 4036, -12831),
        intArrayOf(2, 0, 2, 0, 3994, -10445), intArrayOf(4, 0, 0, 0, 3861, -11650),
        intArrayOf(2, 0, -3, 0, 3665, 14403), intArrayOf(0, 1, -2, 0, -2689, -7003),
        intArrayOf(2, 0, -1, 2, -2602, 0), intArrayOf(2, -1, -2, 0, 2390, 10056),
        intArrayOf(1, 0, 1, 0, -2348, 6322), intArrayOf(2, -2, 0, 0, 2236, -9884),
        intArrayOf(0, 1, 2, 0, -2120, 5751), intArrayOf(0, 2, 0, 0, -2069, 0),
        intArrayOf(2, -2, -1, 0, 2048, -4950), intArrayOf(2, 0, 1, -2, -1773, 4130),
        intArrayOf(2, 0, 0, 2, -1595, 0), intArrayOf(4, -1, -1, 0, 1215, -3958),
        intArrayOf(0, 0, 2, 2, -1110, 0), intArrayOf(3, 0, -1, 0, -892, 3258),
        intArrayOf(2, 1, 1, 0, -810, 2616), intArrayOf(4, -1, -2, 0, 759, -1897),
        intArrayOf(0, 2, -1, 0, -713, -2117), intArrayOf(2, 2, -1, 0, -700, 2354),
        intArrayOf(2, 1, -2, 0, 691, 0), intArrayOf(2, -1, 0, -2, 596, 0),
        intArrayOf(4, 0, 1, 0, 549, -1423), intArrayOf(0, 0, 4, 0, 537, -1117),
        intArrayOf(4, -1, 0, 0, 520, -1571), intArrayOf(1, 0, -2, 0, -487, -1739),
        intArrayOf(2, 1, 0, -2, -399, 0), intArrayOf(0, 0, 2, -2, -381, -4421),
        intArrayOf(1, 1, 1, 0, 351, 0), intArrayOf(3, 0, -2, 0, -340, 0),
        intArrayOf(4, 0, -3, 0, 330, 0), intArrayOf(2, -1, 2, 0, 327, 0),
        intArrayOf(0, 2, 1, 0, -323, 1165), intArrayOf(1, 1, -1, 0, 299, 0),
        intArrayOf(2, 0, 3, 0, 294, 0), intArrayOf(2, 0, -1, -2, 0, 8752)
    )
    // D, M, M', F, Σb (1e-6°)
    private val B = arrayOf(
        intArrayOf(0, 0, 0, 1, 5128122), intArrayOf(0, 0, 1, 1, 280602), intArrayOf(0, 0, 1, -1, 277693),
        intArrayOf(2, 0, 0, -1, 173237), intArrayOf(2, 0, -1, 1, 55413), intArrayOf(2, 0, -1, -1, 46271),
        intArrayOf(2, 0, 0, 1, 32573), intArrayOf(0, 0, 2, 1, 17198), intArrayOf(2, 0, 1, -1, 9266),
        intArrayOf(0, 0, 2, -1, 8822), intArrayOf(2, -1, 0, -1, 8216), intArrayOf(2, 0, -2, -1, 4324),
        intArrayOf(2, 0, 1, 1, 4200), intArrayOf(2, 1, 0, -1, -3359), intArrayOf(2, -1, -1, 1, 2463),
        intArrayOf(2, -1, 0, 1, 2211), intArrayOf(2, -1, -1, -1, 2065), intArrayOf(0, 1, -1, -1, -1870),
        intArrayOf(4, 0, -1, -1, 1828), intArrayOf(0, 1, 0, 1, -1794), intArrayOf(0, 0, 0, 3, -1749),
        intArrayOf(0, 1, -1, 1, -1565), intArrayOf(1, 0, 0, 1, -1491), intArrayOf(0, 1, 1, 1, -1475),
        intArrayOf(0, 1, 1, -1, -1410), intArrayOf(0, 1, 0, -1, -1344), intArrayOf(1, 0, 0, -1, -1335),
        intArrayOf(0, 0, 3, 1, 1107), intArrayOf(4, 0, 0, -1, 1021), intArrayOf(4, 0, -1, 1, 833)
    )

    /** الموقع البروجي الظاهري مركزي الأرض */
    fun ecliptic(jde: Double): Ecliptic {
        val t = centuries(jde)
        val lp = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t + t * t * t / 538841 - t * t * t * t / 65194000)
        val d = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t + t * t * t / 545868 - t * t * t * t / 113065000)
        val m = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t + t * t * t / 24490000)
        val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t + t * t * t / 69699 - t * t * t * t / 14712000)
        val f = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t - t * t * t / 3526000 + t * t * t * t / 863310000)
        val a1 = norm360(119.75 + 131.849 * t)
        val a2 = norm360(53.09 + 479264.290 * t)
        val a3 = norm360(313.45 + 481266.484 * t)
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        var sl = 0.0; var sr = 0.0; var sb = 0.0
        for (r in LR) {
            val arg = r[0] * d + r[1] * m + r[2] * mp + r[3] * f
            val ef = when (abs(r[1])) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sl += r[4] * ef * sind(arg)
            sr += r[5] * ef * cosd(arg)
        }
        for (r in B) {
            val arg = r[0] * d + r[1] * m + r[2] * mp + r[3] * f
            val ef = when (abs(r[1])) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sb += r[4] * ef * sind(arg)
        }
        sl += 3958 * sind(a1) + 1962 * sind(lp - f) + 318 * sind(a2)
        sb += -2235 * sind(lp) + 382 * sind(a3) + 175 * sind(a1 - f) + 175 * sind(a1 + f) + 127 * sind(lp - mp) - 115 * sind(lp + mp)
        val (dPsi, _) = nutation(jde)
        val lon = norm360(lp + sl / 1e6 + dPsi)
        val lat = sb / 1e6
        val dist = 385000.56 + sr / 1000.0
        return Ecliptic(lon, lat, dist)
    }

    fun equatorial(jde: Double): Equatorial {
        val ec = ecliptic(jde)
        val eq = eclToEq(ec.lon, ec.lat, obliquity(jde))
        return Equatorial(eq.ra, eq.dec, ec.distKm)
    }

    /** نصف القطر الظاهري مركزي الأرض (درجات) */
    fun semiDiameter(distKm: Double) = asind(1737.4 / distKm)
    /** اختلاف المنظر الأفقي (درجات) */
    fun parallax(distKm: Double) = asind(6378.14 / distKm)
}

/* ───────────────────── الكواكب (عناصر مدارية تقريبية من JPL، 1800–2050) ───────────────────── */
object Planets {
    // a, ȧ, e, ė, I, İ, L, L̇, ϖ, ϖ̇, Ω, Ω̇ (لكل قرن)
    private val EL = mapOf(
        Body.MERCURY to doubleArrayOf(0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081),
        Body.VENUS to doubleArrayOf(0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418),
        Body.SUN to doubleArrayOf(1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0), // الأرض
        Body.MARS to doubleArrayOf(1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343),
        Body.JUPITER to doubleArrayOf(5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106),
        Body.SATURN to doubleArrayOf(9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794),
        Body.URANUS to doubleArrayOf(19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589),
        Body.NEPTUNE to doubleArrayOf(30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664)
    )

    /** إحداثيات شمسية المركز على مستوى البروج J2000 بالوحدة الفلكية */
    private fun helio(body: Body, t: Double): DoubleArray {
        val k = EL.getValue(body)
        val a = k[0] + k[1] * t
        val e = k[2] + k[3] * t
        val i = k[4] + k[5] * t
        val l = k[6] + k[7] * t
        val w = k[8] + k[9] * t
        val o = k[10] + k[11] * t
        val argP = w - o
        val m = norm180(l - w)
        var ea = m + RAD * e * sind(m)
        repeat(8) { ea -= (ea - RAD * e * sind(ea) - m) / (1 - e * cosd(ea)) }
        val xp = a * (cosd(ea) - e)
        val yp = a * sqrt(1 - e * e) * sind(ea)
        val x = (cosd(argP) * cosd(o) - sind(argP) * sind(o) * cosd(i)) * xp + (-sind(argP) * cosd(o) - cosd(argP) * sind(o) * cosd(i)) * yp
        val y = (cosd(argP) * sind(o) + sind(argP) * cosd(o) * cosd(i)) * xp + (-sind(argP) * sind(o) + cosd(argP) * cosd(o) * cosd(i)) * yp
        val z = (sind(argP) * sind(i)) * xp + (cosd(argP) * sind(i)) * yp
        return doubleArrayOf(x, y, z)
    }

    /** الموقع البروجي مركزي الأرض (مع تصحيح زمن الضوء والمبادرة التقريبية من J2000) */
    fun ecliptic(body: Body, jde: Double): Ecliptic {
        require(body.isPlanet)
        val t = centuries(jde)
        val earth = helio(Body.SUN, t)
        var p = helio(body, t)
        var dx = p[0] - earth[0]; var dy = p[1] - earth[1]; var dz = p[2] - earth[2]
        val dist = hypot3(dx, dy, dz)
        val tau = 0.0057755183 * dist / 36525.0 // زمن الضوء بالقرون
        p = helio(body, t - tau)
        dx = p[0] - earth[0]; dy = p[1] - earth[1]; dz = p[2] - earth[2]
        val lonJ2000 = atan2d(dy, dx)
        val lat = atan2d(dz, sqrt(dx * dx + dy * dy))
        val precession = 1.396971 * t + 0.0003086 * t * t
        val (dPsi, _) = nutation(jde)
        return Ecliptic(norm360(lonJ2000 + precession + dPsi), lat, hypot3(dx, dy, dz) * AU_KM)
    }

    /** بعد الكوكب عن الشمس (وحدة فلكية) لحساب القدر */
    fun sunDistanceAu(body: Body, jde: Double): Double { val p = helio(body, centuries(jde)); return hypot3(p[0], p[1], p[2]) }

    /** القدر الظاهري التقريبي */
    fun magnitude(body: Body, jde: Double): Double {
        val r = sunDistanceAu(body, jde)
        val d = ecliptic(body, jde).distKm / AU_KM
        val earthR = sunDistanceAu(Body.SUN, jde)
        val phase = acosd((r * r + d * d - earthR * earthR) / (2 * r * d))
        val f = 5 * kotlin.math.log10(r * d)
        return when (body) {
            Body.MERCURY -> -0.42 + f + 0.0380 * phase - 0.000273 * phase * phase + 0.000002 * phase * phase * phase
            Body.VENUS -> -4.40 + f + 0.0009 * phase + 0.000239 * phase * phase - 0.00000065 * phase * phase * phase
            Body.MARS -> -1.52 + f + 0.016 * phase
            Body.JUPITER -> -9.40 + f + 0.005 * phase
            Body.SATURN -> -8.88 + f + 0.044 * phase
            Body.URANUS -> -7.19 + f
            Body.NEPTUNE -> -6.87 + f
            else -> 0.0
        }
    }
}

/** واجهة موحّدة لموقع أي جرم */
object Ephemeris {
    fun ecliptic(body: Body, jde: Double): Ecliptic = when (body) {
        Body.SUN -> Sun.ecliptic(jde)
        Body.MOON -> Moon.ecliptic(jde)
        else -> Planets.ecliptic(body, jde)
    }

    fun equatorial(body: Body, jde: Double): Equatorial {
        val ec = ecliptic(body, jde)
        val eq = eclToEq(ec.lon, ec.lat, obliquity(jde))
        return Equatorial(eq.ra, eq.dec, ec.distKm)
    }

    /** الارتفاع والسمت للراصد (مع اختلاف المنظر للقمر، ومع الانكسار إن طُلب) */
    fun horizontal(body: Body, millis: Long, lat: Double, lon: Double, withRefraction: Boolean = true, heightM: Double = 0.0): Horizontal {
        val jd = jdOf(millis)
        var eq = equatorial(body, jdeOf(jd))
        if (body == Body.MOON || body == Body.SUN) eq = topocentric(eq, lat, lon, heightM, jd)
        val h = eqToHor(eq.ra, eq.dec, lat, lst(jd, lon))
        return if (withRefraction) Horizontal(h.alt + refraction(h.alt), h.az) else h
    }
}
