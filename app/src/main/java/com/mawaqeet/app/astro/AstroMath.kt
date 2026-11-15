package com.mawaqeet.app.astro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * أدوات رياضية وفلكية أساسية (خوارزميات Jean Meeus، «Astronomical Algorithms»).
 * كل الزوايا بالدرجات ما لم يُذكر غير ذلك، وكل الأوقات بالميلي ثانية منذ 1970 (UTC).
 */

const val DEG = PI / 180.0
const val RAD = 180.0 / PI
const val DAY_MS = 86_400_000L
const val HOUR_MS = 3_600_000L
const val MIN_MS = 60_000L

fun sind(d: Double) = sin(d * DEG)
fun cosd(d: Double) = cos(d * DEG)
fun tand(d: Double) = tan(d * DEG)
fun asind(x: Double) = asin(x.coerceIn(-1.0, 1.0)) * RAD
fun acosd(x: Double) = acos(x.coerceIn(-1.0, 1.0)) * RAD
fun atan2d(y: Double, x: Double) = atan2(y, x) * RAD

fun norm360(d: Double): Double { val r = d % 360.0; return if (r < 0) r + 360.0 else r }
/** يحوّل الزاوية إلى المجال (-180, 180] */
fun norm180(d: Double): Double { val r = norm360(d); return if (r > 180.0) r - 360.0 else r }

/** اليوم اليولياني (UT) من الوقت بالميلي ثانية */
fun jdOf(millis: Long): Double = millis / 86_400_000.0 + 2440587.5
fun millisOf(jd: Double): Long = Math.round((jd - 2440587.5) * 86_400_000.0)

/** الفرق ΔT = TT − UT بالثواني (تقريب Espenak–Meeus) */
fun deltaT(jd: Double): Double {
    val y = 2000.0 + (jd - 2451544.5) / 365.25
    return when {
        y < 1900 -> { val t = (y - 1860) / 100; 7.62 + 57.37 * t - 1001.0 * t * t }
        y < 1920 -> { val t = y - 1900; -2.79 + 1.494119 * t - 0.0598939 * t * t + 0.0061966 * t * t * t - 0.000197 * t * t * t * t }
        y < 1941 -> { val t = y - 1920; 21.20 + 0.84493 * t - 0.076100 * t * t + 0.0020936 * t * t * t }
        y < 1961 -> { val t = y - 1950; 29.07 + 0.407 * t - t * t / 233 + t * t * t / 2547 }
        y < 1986 -> { val t = y - 1975; 45.45 + 1.067 * t - t * t / 260 - t * t * t / 718 }
        y < 2005 -> { val t = y - 2000; 63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t + 0.000651814 * t * t * t * t }
        y < 2050 -> { val t = y - 2000; 62.92 + 0.32217 * t + 0.005589 * t * t }
        y < 2150 -> -20 + 32 * ((y - 1820) / 100) * ((y - 1820) / 100) - 0.5628 * (2150 - y)
        else -> { val u = (y - 1820) / 100; -20 + 32 * u * u }
    }
}

/** اليوم اليولياني بالزمن الأرضي TT */
fun jdeOf(jdUt: Double) = jdUt + deltaT(jdUt) / 86400.0

/** القرون اليوليانية منذ J2000 */
fun centuries(jde: Double) = (jde - 2451545.0) / 36525.0

/** ميل دائرة البروج الحقيقي (مع التحوّل) */
fun obliquity(jde: Double): Double {
    val t = centuries(jde)
    val eps0 = 23.0 + 26.0 / 60 + 21.448 / 3600 - (46.8150 * t + 0.00059 * t * t - 0.001813 * t * t * t) / 3600
    return eps0 + nutation(jde).second
}

/** التحوّل في الطول والميل (درجات) — النموذج المختصر */
fun nutation(jde: Double): Pair<Double, Double> {
    val t = centuries(jde)
    val om = norm360(125.04452 - 1934.136261 * t)
    val l = norm360(280.4665 + 36000.7698 * t)
    val lp = norm360(218.3165 + 481267.8813 * t)
    val dPsi = (-17.20 * sind(om) - 1.32 * sind(2 * l) - 0.23 * sind(2 * lp) + 0.21 * sind(2 * om)) / 3600
    val dEps = (9.20 * cosd(om) + 0.57 * cosd(2 * l) + 0.10 * cosd(2 * lp) - 0.09 * cosd(2 * om)) / 3600
    return dPsi to dEps
}

/** الزمن النجمي الظاهري في غرينتش (درجات) */
fun gast(jdUt: Double): Double {
    val t = (jdUt - 2451545.0) / 36525.0
    val gmst = 280.46061837 + 360.98564736629 * (jdUt - 2451545.0) + 0.000387933 * t * t - t * t * t / 38710000.0
    val jde = jdeOf(jdUt)
    val (dPsi, _) = nutation(jde)
    return norm360(gmst + dPsi * cosd(obliquity(jde)))
}

/** الزمن النجمي المحلي (درجات) */
fun lst(jdUt: Double, lonEast: Double) = norm360(gast(jdUt) + lonEast)

data class Equatorial(val ra: Double, val dec: Double, val distKm: Double = 0.0)
data class Ecliptic(val lon: Double, val lat: Double, val distKm: Double = 0.0)
data class Horizontal(val alt: Double, val az: Double)

fun eclToEq(lon: Double, lat: Double, eps: Double): Equatorial {
    val ra = atan2d(sind(lon) * cosd(eps) - tand(lat) * sind(eps), cosd(lon))
    val dec = asind(sind(lat) * cosd(eps) + cosd(lat) * sind(eps) * sind(lon))
    return Equatorial(norm360(ra), dec)
}

fun eqToEcl(ra: Double, dec: Double, eps: Double): Ecliptic {
    val lon = atan2d(sind(ra) * cosd(eps) + tand(dec) * sind(eps), cosd(ra))
    val lat = asind(sind(dec) * cosd(eps) - cosd(dec) * sind(eps) * sind(ra))
    return Ecliptic(norm360(lon), lat)
}

/** الارتفاع والسمت (السمت من الشمال باتجاه الشرق) دون انكسار */
fun eqToHor(ra: Double, dec: Double, lat: Double, lstDeg: Double): Horizontal {
    val h = lstDeg - ra
    val alt = asind(sind(lat) * sind(dec) + cosd(lat) * cosd(dec) * cosd(h))
    val az = atan2d(-cosd(dec) * sind(h), sind(dec) * cosd(lat) - cosd(dec) * sind(lat) * cosd(h))
    return Horizontal(alt, norm360(az))
}

/** تحويل عكسي: من الأفق إلى خط الاستواء السماوي */
fun horToEq(alt: Double, az: Double, lat: Double, lstDeg: Double): Equatorial {
    val dec = asind(sind(lat) * sind(alt) + cosd(lat) * cosd(alt) * cosd(az))
    val h = atan2d(-sind(az) * cosd(alt), sind(alt) * cosd(lat) - cosd(alt) * sind(lat) * cosd(az))
    return Equatorial(norm360(lstDeg - h), dec)
}

/** انكسار الضوء الجوي (درجات) لارتفاع ظاهري (Sæmundsson) */
fun refraction(trueAlt: Double): Double {
    if (trueAlt < -2.0) return 0.0
    val r = 1.02 / tand(trueAlt + 10.3 / (trueAlt + 5.11)) / 60.0
    return if (r < 0) 0.0 else r
}

/** المسافة الزاوية بين نقطتين على الكرة (درجات) */
fun separation(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double =
    acosd(sind(dec1) * sind(dec2) + cosd(dec1) * cosd(dec2) * cosd(ra1 - ra2))

/** تحويل إحداثيات مركزية الأرض إلى سطحية (اختلاف المنظر) — للقمر خاصة */
fun topocentric(eq: Equatorial, lat: Double, lonEast: Double, heightM: Double, jdUt: Double): Equatorial {
    if (eq.distKm <= 0) return eq
    val u = atan2d(0.99664719 * tand(lat), 1.0)
    val rhoSin = 0.99664719 * sind(u) + heightM / 6378140.0 * sind(lat)
    val rhoCos = cosd(u) + heightM / 6378140.0 * cosd(lat)
    val sinPi = 6378.14 / eq.distKm
    val h = lst(jdUt, lonEast) - eq.ra
    val dRa = atan2d(-rhoCos * sinPi * sind(h), cosd(eq.dec) - rhoCos * sinPi * cosd(h))
    val dec = atan2d((sind(eq.dec) - rhoSin * sinPi) * cosd(dRa), cosd(eq.dec) - rhoCos * sinPi * cosd(h))
    return Equatorial(norm360(eq.ra + dRa), dec, eq.distKm)
}

/** يوم الأسبوع واليوم اليولياني لتاريخ تقويمي (ميلادي أو يولياني) عند منتصف الليل */
fun calendarToJd(y: Int, m: Int, d: Double, gregorian: Boolean = true): Double {
    var yy = y; var mm = m
    if (mm <= 2) { yy -= 1; mm += 12 }
    val b = if (gregorian) { val a = floor(yy / 100.0); 2 - a + floor(a / 4) } else 0.0
    return floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + d + b - 1524.5
}

data class CalDate(val year: Int, val month: Int, val day: Int)

fun jdToCalendar(jd: Double, gregorian: Boolean = true): CalDate {
    val z = floor(jd + 0.5)
    val a = if (gregorian) { val al = floor((z - 1867216.25) / 36524.25); z + 1 + al - floor(al / 4) } else z
    val b = a + 1524
    val c = floor((b - 122.1) / 365.25)
    val d = floor(365.25 * c)
    val e = floor((b - d) / 30.6001)
    val day = (b - d - floor(30.6001 * e)).toInt()
    val month = (if (e < 14) e - 1 else e - 13).toInt()
    val year = (if (month > 2) c - 4716 else c - 4715).toInt()
    return CalDate(year, month, day)
}

/** بحث ثنائي عن جذر دالة متصلة بين نقطتين بإشارتين مختلفتين */
inline fun bisect(a0: Double, b0: Double, iterations: Int = 40, f: (Double) -> Double): Double {
    var a = a0; var b = b0
    var fa = f(a)
    repeat(iterations) {
        val m = (a + b) / 2
        val fm = f(m)
        if ((fa <= 0 && fm <= 0) || (fa > 0 && fm > 0)) { a = m; fa = fm } else b = m
    }
    return (a + b) / 2
}

fun hypot3(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z)
fun absD(x: Double) = abs(x)
