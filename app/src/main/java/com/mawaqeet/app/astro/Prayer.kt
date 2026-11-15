package com.mawaqeet.app.astro

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max

/** طرق حساب مواقيت الصلاة: زاوية الفجر، وزاوية العشاء (أو فاصل بالدقائق بعد المغرب) */
enum class PrayerMethod(val ar: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0, val maghribAngle: Double = 0.0) {
    ALGERIA("وزارة الشؤون الدينية الجزائرية", 18.0, 17.0),
    MWL("رابطة العالم الإسلامي", 18.0, 17.0),
    UMM_AL_QURA("أم القرى (مكة)", 18.5, 0.0, 90),
    EGYPT("الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    KARACHI("جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0),
    ISNA("أمريكا الشمالية (ISNA)", 15.0, 15.0),
    MOROCCO("وزارة الأوقاف المغربية", 19.0, 17.0),
    TUNISIA("تونس", 18.0, 18.0),
    GULF("الخليج", 19.5, 0.0, 90),
    KUWAIT("الكويت", 18.0, 17.5),
    QATAR("قطر", 18.0, 0.0, 90),
    TURKEY("رئاسة الشؤون الدينية التركية", 18.0, 17.0),
    FRANCE("اتحاد المنظمات الإسلامية بفرنسا", 12.0, 12.0),
    SINGAPORE("سنغافورة وماليزيا", 20.0, 18.0),
    TEHRAN("معهد الجيوفيزياء بطهران", 17.7, 14.0, 0, 4.5)
}

enum class HighLatRule(val ar: String) {
    NONE("بلا تعديل"), MIDDLE_OF_NIGHT("منتصف الليل"), SEVENTH("سُبع الليل"), ANGLE_BASED("نسبة الزاوية")
}

enum class Prayer(val ar: String) {
    FAJR("الفجر"), SUNRISE("الشروق"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء")
}

data class PrayerSettings(
    val method: PrayerMethod = PrayerMethod.ALGERIA,
    val hanafiAsr: Boolean = false,
    val highLat: HighLatRule = HighLatRule.ANGLE_BASED,
    /** تعديلات يدوية بالدقائق لكل صلاة بترتيب Prayer */
    val offsets: IntArray = IntArray(6),
    /** دقائق احتياط بعد الزوال للظهر وبعد الغروب للمغرب (كما تفعل التقاويم الرسمية) */
    val dhuhrSafety: Int = 0,
    val maghribSafety: Int = 0,
    val heightM: Double = 0.0
)

data class PrayerDay(val times: Map<Prayer, Long?>) {
    operator fun get(p: Prayer) = times[p]
    /** الصلاة القادمة بعد وقت معيّن (مع الشروق) */
    fun next(now: Long, includeSunrise: Boolean = false): Pair<Prayer, Long>? =
        times.entries.filter { (includeSunrise || it.key != Prayer.SUNRISE) && it.value != null && it.value!! > now }
            .minByOrNull { it.value!! }?.let { it.key to it.value!! }
}

object PrayerCalc {
    fun compute(dayStart: Long, lat: Double, lon: Double, s: PrayerSettings): PrayerDay {
        val sun = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, heightM = s.heightM)
        val noon = sun.transit ?: (dayStart + ((12 - lon / 15.0) * HOUR_MS).toLong())
        val fajrE = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, -s.method.fajr).rise
        val ishaE = if (s.method.ishaMinutes > 0) null else RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, -s.method.isha).set
        val maghribAngleT = if (s.method.maghribAngle > 0) RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, -s.method.maghribAngle).set else null

        // العصر: ظل الشيء مثله (أو مثليه) زائد ظل الزوال
        val jde = jdeOf(jdOf(noon))
        val dec = Sun.equatorial(jde).dec
        val factor = if (s.hanafiAsr) 2.0 else 1.0
        val asrAlt = atan(1.0 / (factor + tand(abs(lat - dec)))) * RAD
        val asrE = RiseSetCalc.compute(Body.SUN, dayStart, lat, lon, asrAlt).set

        val sunrise = sun.rise
        val sunset = sun.set
        var fajr = fajrE
        var maghrib = maghribAngleT ?: sunset
        var isha = if (s.method.ishaMinutes > 0) maghrib?.let { it + s.method.ishaMinutes * MIN_MS } else ishaE

        // تعديل خطوط العرض العليا: حين يغيب الفجر أو العشاء أو يتجاوز الحد المعقول
        if (s.highLat != HighLatRule.NONE && sunrise != null && sunset != null) {
            val nextRise = sunrise + DAY_MS
            val night = nextRise - sunset
            fun portion(angle: Double) = when (s.highLat) {
                HighLatRule.MIDDLE_OF_NIGHT -> night / 2
                HighLatRule.SEVENTH -> night / 7
                else -> (night * angle / 60.0).toLong()
            }
            val fajrLimit = sunrise - portion(s.method.fajr)
            if (fajr == null || fajr < fajrLimit) fajr = fajrLimit
            if (s.method.ishaMinutes == 0) {
                val ishaLimit = sunset + portion(s.method.isha)
                if (isha == null || isha > ishaLimit) isha = ishaLimit
            }
        }
        maghrib = maghrib?.let { it + s.maghribSafety * MIN_MS }
        val dhuhr = noon + max(1, s.dhuhrSafety) * MIN_MS
        val raw = mapOf(
            Prayer.FAJR to fajr, Prayer.SUNRISE to sunrise, Prayer.DHUHR to dhuhr,
            Prayer.ASR to asrE, Prayer.MAGHRIB to maghrib, Prayer.ISHA to isha
        )
        return PrayerDay(raw.mapValues { (p, t) -> t?.let { it + s.offsets[p.ordinal] * MIN_MS } })
    }
}

/* ───────────────────────── القبلة والمسافات ───────────────────────── */
object Qibla {
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    /** اتجاه القبلة من الشمال الجغرافي (درجات) */
    fun bearing(lat: Double, lon: Double): Double = initialBearing(lat, lon, KAABA_LAT, KAABA_LON)

    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = lon2 - lon1
        return norm360(atan2d(sind(dLon) * cosd(lat2), cosd(lat1) * sind(lat2) - sind(lat1) * cosd(lat2) * cosd(dLon)))
    }

    /** المسافة على الدائرة العظمى (كم) */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double = KAABA_LAT, lon2: Double = KAABA_LON): Double =
        6371.0 * acosd(sind(lat1) * sind(lat2) + cosd(lat1) * cosd(lat2) * cosd(lon2 - lon1)) * DEG

    /** نقاط مسار الدائرة العظمى من الموقع إلى الكعبة (لرسم الخط على الخريطة) */
    fun greatCircle(lat1: Double, lon1: Double, lat2: Double = KAABA_LAT, lon2: Double = KAABA_LON, n: Int = 64): List<Pair<Double, Double>> {
        val d = acosd(sind(lat1) * sind(lat2) + cosd(lat1) * cosd(lat2) * cosd(lon2 - lon1)) * DEG
        // نفس النقطة أو النقطة المقابلة تماماً (sin d = 0): لا مسار محدد، وتفادي القسمة على صفر
        if (d < 1e-9 || kotlin.math.abs(kotlin.math.sin(d)) < 1e-9) return listOf(lat1 to lon1)
        return (0..n).map { i ->
            val f = i.toDouble() / n
            val a = kotlin.math.sin((1 - f) * d) / kotlin.math.sin(d)
            val b = kotlin.math.sin(f * d) / kotlin.math.sin(d)
            val x = a * cosd(lat1) * cosd(lon1) + b * cosd(lat2) * cosd(lon2)
            val y = a * cosd(lat1) * sind(lon1) + b * cosd(lat2) * sind(lon2)
            val z = a * sind(lat1) + b * sind(lat2)
            atan2d(z, kotlin.math.sqrt(x * x + y * y)) to atan2d(y, x)
        }
    }

    /** أيام «تعامد الشمس» على الكعبة تقريباً: حين يكون اتجاه الشمس هو القبلة (للتحقق من القبلة بالظل) */
    fun sunAtKaabaZenith(year: Int): List<Long> {
        val out = ArrayList<Long>()
        var t = millisOf(calendarToJd(year, 1, 1.0))
        val end = millisOf(calendarToJd(year + 1, 1, 1.0))
        var prev = Sun.equatorial(jdeOf(jdOf(t))).dec - KAABA_LAT
        while (t < end) {
            val n = t + DAY_MS
            val v = Sun.equatorial(jdeOf(jdOf(n))).dec - KAABA_LAT
            if ((prev < 0) != (v < 0)) {
                // يوم التعامد: نأخذ وقت الزوال في مكة
                val day = (n / DAY_MS) * DAY_MS
                RiseSetCalc.compute(Body.SUN, day, KAABA_LAT, KAABA_LON).transit?.let { out += it }
            }
            prev = v; t = n
        }
        return out
    }
}
