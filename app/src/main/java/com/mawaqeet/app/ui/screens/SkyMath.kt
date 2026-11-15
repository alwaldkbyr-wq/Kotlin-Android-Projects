package com.mawaqeet.app.ui.screens

import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.DEG
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.Equatorial
import com.mawaqeet.app.astro.Horizontal
import com.mawaqeet.app.astro.Mansions
import com.mawaqeet.app.astro.Planets
import com.mawaqeet.app.astro.RAD
import com.mawaqeet.app.astro.StarCatalog
import com.mawaqeet.app.astro.acosd
import com.mawaqeet.app.astro.asind
import com.mawaqeet.app.astro.atan2d
import com.mawaqeet.app.astro.cosd
import com.mawaqeet.app.astro.eclToEq
import com.mawaqeet.app.astro.eqToHor
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.lst
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.astro.obliquity
import com.mawaqeet.app.astro.separation
import com.mawaqeet.app.astro.sind
import com.mawaqeet.app.astro.topocentric
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** موقع جرم متحرّك (شمس/قمر/كوكب) في لحظة معيّنة */
class BodyPos(
    val body: Body,
    /** المطلع المستقيم والميل (درجات، لتاريخ اللحظة؛ سطحيان للشمس والقمر) */
    val ra: Double,
    val dec: Double,
    val distKm: Double,
    val eclLon: Double,
    /** الارتفاع والسمت الهندسيان (دون انكسار) */
    val alt: Double,
    val az: Double,
    /** القدر الظاهري */
    val mag: Double
)

/** لقطة كاملة للسماء في لحظة ومكان: كل الارتفاعات والسموت المحسوبة مسبقاً */
class SkyFrame(
    val millis: Long,
    val lat: Double,
    val lon: Double,
    val jd: Double,
    val jde: Double,
    val lstDeg: Double,
    val eps: Double,
    /** مصفوفة J2000 ← (شرق، شمال، أعلى) */
    val toEnu: DoubleArray,
    val starAlt: DoubleArray,
    val starAz: DoubleArray,
    /** لكل كوكبة ولكل خط: [alt0, az0, alt1, az1, ...] */
    val conAlt: List<List<DoubleArray>>,
    val conLabelAlt: DoubleArray,
    val conLabelAz: DoubleArray,
    /** مصفوفة بترتيب Body.ordinal */
    val bodies: Array<BodyPos>,
    /** البروج كل 5° (73 نقطة): ارتفاع وسمت */
    val eclAlt: DoubleArray,
    val eclAz: DoubleArray,
    /** خط الاستواء السماوي (73 نقطة) */
    val eqAlt: DoubleArray,
    val eqAz: DoubleArray,
    /** مراكز المنازل الثمانية والعشرين على البروج */
    val mansAlt: DoubleArray,
    val mansAz: DoubleArray,
    val moonMansion: Int,
    val moonIllum: Double,
    val moonWaxing: Boolean
) {
    fun body(b: Body): BodyPos = bodies[b.ordinal]
}

class StarTimes(
    val rise: Long?,
    val transit: Long,
    val set: Long?,
    val transitAlt: Double,
    val circumpolar: Boolean,
    val neverRises: Boolean
)

/** رياضيات السماء المشتركة بين شاشات الخريطة والكرة والكوكبات (Kotlin صرف) */
object SkyMath {

    // ───────────── المتجهات والمصفوفات ─────────────

    fun unit(ra: Double, dec: Double): DoubleArray =
        doubleArrayOf(cosd(dec) * cosd(ra), cosd(dec) * sind(ra), sind(dec))

    /** (شرق، شمال، أعلى) لارتفاع وسمت */
    fun enu(alt: Double, az: Double): DoubleArray =
        doubleArrayOf(cosd(alt) * sind(az), cosd(alt) * cosd(az), sind(alt))

    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += a[i * 3 + k] * b[k * 3 + j]
            r[i * 3 + j] = s
        }
        return r
    }

    fun apply(m: DoubleArray, x: Double, y: Double, z: Double): DoubleArray = doubleArrayOf(
        m[0] * x + m[1] * y + m[2] * z,
        m[3] * x + m[4] * y + m[5] * z,
        m[6] * x + m[7] * y + m[8] * z
    )

    private fun rz(deg: Double): DoubleArray {
        val c = cosd(deg); val s = sind(deg)
        return doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0)
    }

    // ───────────── المبادرة (Meeus، الفصل 21) ─────────────

    /** [ζ, z, θ] بالدرجات من J2000 إلى لحظة [jde] */
    fun precessAngles(jde: Double): DoubleArray {
        val t = (jde - 2451545.0) / 36525.0
        val zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0
        val z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0
        val th = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0
        return doubleArrayOf(zeta, z, th)
    }

    /** يحوّل (ra, dec) من J2000 إلى لحظة [jde] */
    fun precess(ra: Double, dec: Double, jde: Double): Equatorial {
        val a = precessAngles(jde)
        val zeta = a[0]; val z = a[1]; val th = a[2]
        val aa = cosd(dec) * sind(ra + zeta)
        val bb = cosd(th) * cosd(dec) * cosd(ra + zeta) - sind(th) * sind(dec)
        val cc = sind(th) * cosd(dec) * cosd(ra + zeta) + cosd(th) * sind(dec)
        return Equatorial(norm360(atan2d(aa, bb) + z), asind(cc))
    }

    /** مصفوفة المبادرة (تُطبَّق على متجه J2000 فتعطي متجه اللحظة) */
    fun precessionMatrix(jde: Double): DoubleArray {
        val a = precessAngles(jde)
        val c = cosd(a[2]); val s = sind(a[2])
        val m2 = doubleArrayOf(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c)
        return mul(rz(a[1]), mul(m2, rz(a[0])))
    }

    /** مصفوفة الاستواء ← الأفق (شرق، شمال، أعلى) بالزمن النجمي المحلي وخط العرض */
    fun eqToEnuMatrix(lstDeg: Double, lat: Double): DoubleArray {
        val sl = sind(lstDeg); val cl = cosd(lstDeg)
        val sp = sind(lat); val cp = cosd(lat)
        return doubleArrayOf(
            -sl, cl, 0.0,
            -sp * cl, -sp * sl, cp,
            cp * cl, cp * sl, sp
        )
    }

    /** مصفوفة J2000 ← الأفق لراصد في لحظة معيّنة (مبادرة + دوران الأرض) */
    fun j2000ToEnu(millis: Long, lat: Double, lon: Double): DoubleArray {
        val jd = jdOf(millis)
        return mul(eqToEnuMatrix(lst(jd, lon), lat), precessionMatrix(jdeOf(jd)))
    }

    // ───────────── الإسقاط على قرص السماء ─────────────

    /**
     * إسقاط مجسَّم (stereographic): السمت في المركز، الأفق على الدائرة الواحدية،
     * الشمال للأعلى والشرق لليسار (كما ننظر إلى السماء من تحتها).
     * يملأ out[0]=x و out[1]=y (y نحو الأسفل كإحداثيات الشاشة) ويعيد نصف القطر.
     */
    fun project(alt: Double, az: Double, out: DoubleArray): Double {
        val r = tan((90.0 - alt) * DEG / 2.0)
        val a = az * DEG
        out[0] = -r * sin(a)
        out[1] = -r * cos(a)
        return r
    }

    /** العكس: من نقطة على القرص إلى ارتفاع وسمت */
    fun unproject(x: Double, y: Double): Horizontal {
        val r = hypot(x, y)
        val alt = 90.0 - 2.0 * atan(r) * RAD
        val az = if (r < 1e-9) 0.0 else norm360(atan2d(-x, -y))
        return Horizontal(alt, az)
    }

    /** إسقاط gnomonic حول المركز (ra0, dec0): ξ شرقاً، η شمالاً. false إن كانت النقطة خلف المركز */
    fun gnomonic(ra: Double, dec: Double, ra0: Double, dec0: Double, out: DoubleArray): Boolean {
        val dr = ra - ra0
        val cosc = sind(dec0) * sind(dec) + cosd(dec0) * cosd(dec) * cosd(dr)
        if (cosc <= 0.05) return false
        out[0] = cosd(dec) * sind(dr) / cosc
        out[1] = (cosd(dec0) * sind(dec) - sind(dec0) * cosd(dec) * cosd(dr)) / cosc
        return true
    }

    /** مواقع الشمس والقمر والكواكب (مطلع/ميل للحظة، سطحية للشمس والقمر) مع الارتفاع والسمت والقدر */
    fun bodyPositions(millis: Long, lat: Double, lon: Double, heightM: Double = 0.0): Array<BodyPos> {
        val jd = jdOf(millis)
        val jde = jdeOf(jd)
        val l = lst(jd, lon)
        val eps = obliquity(jde)
        val all = Body.values()
        val res = arrayOfNulls<BodyPos>(all.size)
        for (b in all) {
            val ec = Ephemeris.ecliptic(b, jde)
            var eq = eclToEq(ec.lon, ec.lat, eps)
            eq = Equatorial(eq.ra, eq.dec, ec.distKm)
            if (b == Body.SUN || b == Body.MOON) eq = topocentric(eq, lat, lon, heightM, jd)
            val h = eqToHor(eq.ra, eq.dec, lat, l)
            val mag = when (b) {
                Body.SUN -> -26.74
                Body.MOON -> 0.0
                else -> Planets.magnitude(b, jde)
            }
            res[b.ordinal] = BodyPos(b, eq.ra, eq.dec, ec.distKm, ec.lon, h.alt, h.az, mag)
        }
        val sun = res[Body.SUN.ordinal]!!
        val moon = res[Body.MOON.ordinal]!!
        val ph = phaseAngle(sun, moon)
        val moonMag = -12.73 + 0.026 * abs(ph) + 4e-9 * Math.pow(abs(ph), 4.0)
        res[Body.MOON.ordinal] = BodyPos(Body.MOON, moon.ra, moon.dec, moon.distKm, moon.eclLon, moon.alt, moon.az, moonMag)
        return Array(all.size) { res[it]!! }
    }

    private fun phaseAngle(sun: BodyPos, moon: BodyPos): Double {
        val elong = separation(moon.ra, moon.dec, sun.ra, sun.dec)
        return atan2d(sun.distKm * sind(elong), moon.distKm - sun.distKm * cosd(elong))
    }

    /** نسبة إضاءة القمر 0..1 */
    fun moonIllumination(b: Array<BodyPos>): Double =
        (1 + cosd(phaseAngle(b[Body.SUN.ordinal], b[Body.MOON.ordinal]))) / 2

    fun moonWaxing(b: Array<BodyPos>): Boolean =
        norm360(b[Body.MOON.ordinal].eclLon - b[Body.SUN.ordinal].eclLon) < 180.0

    // ───────────── اللقطة الكاملة ─────────────

    private val starVecs: Array<DoubleArray> by lazy {
        Array(StarCatalog.stars.size) { unit(StarCatalog.stars[it].ra, StarCatalog.stars[it].dec) }
    }

    fun frame(millis: Long, lat: Double, lon: Double, heightM: Double = 0.0): SkyFrame {
        val jd = jdOf(millis)
        val jde = jdeOf(jd)
        val l = lst(jd, lon)
        val eps = obliquity(jde)
        val m = mul(eqToEnuMatrix(l, lat), precessionMatrix(jde))

        val ns = starVecs.size
        val sAlt = DoubleArray(ns); val sAz = DoubleArray(ns)
        for (i in 0 until ns) {
            val v = starVecs[i]
            val e = m[0] * v[0] + m[1] * v[1] + m[2] * v[2]
            val n = m[3] * v[0] + m[4] * v[1] + m[5] * v[2]
            val u = m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
            sAlt[i] = asind(u); sAz[i] = norm360(atan2d(e, n))
        }

        val cons = StarCatalog.constellations
        val conAlt = ArrayList<List<DoubleArray>>(cons.size)
        val labAlt = DoubleArray(cons.size); val labAz = DoubleArray(cons.size)
        for ((ci, c) in cons.withIndex()) {
            val polys = ArrayList<DoubleArray>(c.lines.size)
            for (ln in c.lines) {
                val np = ln.size / 2
                val out = DoubleArray(np * 2)
                for (k in 0 until np) {
                    val pe = precess(ln[2 * k], ln[2 * k + 1], jde)
                    val h = eqToHor(pe.ra, pe.dec, lat, l)
                    out[2 * k] = h.alt; out[2 * k + 1] = h.az
                }
                polys.add(out)
            }
            conAlt.add(polys)
            val pl = precess(c.labelRa, c.labelDec, jde)
            val hl = eqToHor(pl.ra, pl.dec, lat, l)
            labAlt[ci] = hl.alt; labAz[ci] = hl.az
        }

        // الأجرام وطور القمر
        val bodies = bodyPositions(millis, lat, lon, heightM)
        val moon = bodies[Body.MOON.ordinal]
        val illum = moonIllumination(bodies)
        val waxing = moonWaxing(bodies)

        // البروج والاستواء
        val eclAlt = DoubleArray(73); val eclAz = DoubleArray(73)
        val eqAlt = DoubleArray(73); val eqAz = DoubleArray(73)
        for (i in 0..72) {
            val e1 = eclToEq(i * 5.0, 0.0, eps)
            val h1 = eqToHor(e1.ra, e1.dec, lat, l)
            eclAlt[i] = h1.alt; eclAz[i] = h1.az
            val h2 = eqToHor(i * 5.0, 0.0, lat, l)
            eqAlt[i] = h2.alt; eqAz[i] = h2.az
        }
        val mAlt = DoubleArray(28); val mAz = DoubleArray(28)
        for (i in 0 until 28) {
            val lonI = Mansions.lonOf(i, jde) + Mansions.SPAN / 2
            val e1 = eclToEq(lonI, 0.0, eps)
            val h1 = eqToHor(e1.ra, e1.dec, lat, l)
            mAlt[i] = h1.alt; mAz[i] = h1.az
        }
        val moonMansion = Mansions.indexOf(moon.eclLon, jde)

        return SkyFrame(
            millis, lat, lon, jd, jde, l, eps, m, sAlt, sAz, conAlt, labAlt, labAz, bodies,
            eclAlt, eclAz, eqAlt, eqAz, mAlt, mAz, moonMansion, illum, waxing
        )
    }

    // ───────────── طلوع وعبور وغروب نجم ─────────────

    private const val SIDEREAL_MS = 86_164_091L

    /** أوقات نجم (ra, dec للحظة الحالية) في اليوم المحلي الذي يبدأ عند [dayStart] */
    fun starTimes(ra: Double, dec: Double, dayStart: Long, lat: Double, lon: Double): StarTimes {
        val rate = 360.98564736629
        val l0 = lst(jdOf(dayStart), lon)
        val transit = dayStart + (norm360(ra - l0) / rate * DAY_MS).toLong()
        val transitAlt = 90.0 - abs(lat - dec)
        val denom = cosd(lat) * cosd(dec)
        val cosH = if (denom < 1e-9) (if (sind(lat) * sind(dec) > 0) -2.0 else 2.0)
        else (sind(-0.5667) - sind(lat) * sind(dec)) / denom
        if (cosH > 1.0) return StarTimes(null, transit, null, transitAlt, false, true)
        if (cosH < -1.0) return StarTimes(null, transit, null, transitAlt, true, false)
        val half = (acosd(cosH) / rate * DAY_MS).toLong()
        fun fit(t0: Long): Long {
            var t = t0
            while (t < dayStart) t += SIDEREAL_MS
            while (t >= dayStart + DAY_MS) t -= SIDEREAL_MS
            return t
        }
        return StarTimes(fit(transit - half), transit, fit(transit + half), transitAlt, false, false)
    }

    // ───────────── مساعدات للكوكبات ─────────────

    private val cumDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365)

    /** رقم اليوم في السنة (1..365) الذي يعبر فيه مطلع [ra] خط الزوال عند 21:00 بالتوقيت الشمسي المحلي */
    fun bestDayOfYear(ra: Double): Int {
        // الزمن النجمي عند 21:00 = مطلع الشمس + 135°
        val raSun = norm360(ra - 135.0)
        val lonSun = norm360(atan2d(sind(raSun), cosd(raSun) * cosd(23.44)))
        var d = norm360(lonSun - 280.0) / 0.98565 + 1.0
        if (d >= 366.0) d -= 365.25
        return d.toInt().coerceIn(1, 365)
    }

    /** الشهر (1..12) المناظر لرقم اليوم في السنة */
    fun monthOfDay(doy: Int): Int {
        for (m in 1..12) if (doy <= cumDays[m]) return m
        return 12
    }

    fun bestMonth(ra: Double): Int = monthOfDay(bestDayOfYear(ra))

    /** لون النجم التقريبي (ARGB) بحسب اسمه اللاتيني: الحمراء والبرتقالية والزرقاء */
    fun starColor(latin: String): Long = when (latin) {
        "Betelgeuse", "Antares", "Aldebaran", "Arcturus", "Alphard", "Menkar", "Kochab", "Mirach", "Schedar", "Hamal", "Alphecca" -> 0xFFFFB27AL
        "Capella", "Pollux", "Dubhe", "Alkaid", "Sadr", "Polaris", "Canopus", "Procyon", "Mirfak" -> 0xFFFFF1C8L
        "Rigel", "Spica", "Sirius", "Vega", "Regulus", "Altair", "Achernar", "Bellatrix", "Hadar", "Acrux", "Mimosa",
        "Adhara", "Shaula", "Alnilam", "Alnitak", "Mintaka", "Deneb", "Fomalhaut", "Castor", "Elnath", "Alnair" -> 0xFFBFD4FFL
        else -> 0xFFFFFFFFL
    }

    /** نصف قطر نجم بالـ dp حسب قدره */
    fun starRadius(mag: Double): Double = (0.9 + (4.2 - mag) * 0.75).coerceIn(0.9, 5.2)

    /** اتجاه الشمس/القمر وتحويلات صغيرة: cos/sin بالدرجات */
    fun cosDeg(d: Double) = cos(d * DEG)
    fun sinDeg(d: Double) = sin(d * DEG)
}
