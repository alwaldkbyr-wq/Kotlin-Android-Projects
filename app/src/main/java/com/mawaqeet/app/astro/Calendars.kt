package com.mawaqeet.app.astro

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.floor

/** تاريخ عام في أي تقويم */
data class CDate(val year: Int, val month: Int, val day: Int)

enum class CalendarKind(val ar: String) {
    GREGORIAN("الميلادي"), JULIAN("اليولياني (الشرقي القديم)"),
    HIJRI("الهجري القمري"), SOLAR_HIJRI("الهجري الشمسي")
}

enum class HijriMethod(val ar: String) {
    UMM_AL_QURA("أم القرى"), CIVIL("الحساب المدني (الجدولي)")
}

/** صيغ أسماء أشهر الهجري الشمسي */
enum class SolarNames(val ar: String, val months: List<String>) {
    ARABIC_ZODIAC("الأبراج (عربية)", listOf("الحمل", "الثور", "الجوزاء", "السرطان", "الأسد", "السنبلة", "الميزان", "العقرب", "القوس", "الجدي", "الدلو", "الحوت")),
    IRANIAN("إيرانية (فارسية)", listOf("فروردين", "أرديبهشت", "خرداد", "تير", "مرداد", "شهريور", "مهر", "آبان", "آذر", "دي", "بهمن", "إسفند")),
    AFGHAN_DARI("أفغانية (فارسية دري)", listOf("حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله", "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت")),
    AFGHAN_PASHTO("أفغانية (بشتو)", listOf("وری", "غویی", "غبرگولی", "چنګاښ", "زمری", "وږی", "تله", "لړم", "لیندۍ", "مرغومی", "سلواغه", "کب")),
    KURDISH("كردية", listOf("خاکەلێوە", "گوڵان", "جۆزەردان", "پووشپەڕ", "گەلاوێژ", "خەرمانان", "ڕەزبەر", "گەڵاڕێزان", "سەرماوەز", "بەفرانبار", "ڕێبەندان", "ڕەشەمێ"))
}

object Calendars {
    val gregorianMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    /** الأسماء المتداولة في الجزائر والمغرب العربي */
    val maghrebMonths = listOf("جانفي", "فيفري", "مارس", "أفريل", "ماي", "جوان", "جويلية", "أوت", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val syriacMonths = listOf("كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول")
    val hijriMonths = listOf("محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة")
    val weekDays = listOf("الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد") // ISO: الإثنين = 1

    /** رقم اليوم الثابت: اليوم اليولياني عند الظهر (عدد صحيح) */
    fun jdn(g: LocalDate): Long = g.toEpochDay() + 2440588L
    fun fromJdn(jdn: Long): LocalDate = LocalDate.ofEpochDay(jdn - 2440588L)

    /* ── اليولياني ── */
    fun toJulian(g: LocalDate): CDate { val c = jdToCalendar(jdn(g).toDouble(), gregorian = false); return CDate(c.year, c.month, c.day) }
    fun fromJulian(d: CDate): LocalDate = fromJdn(Math.round(calendarToJd(d.year, d.month, d.day.toDouble(), gregorian = false) + 0.5))

    /* ── الهجري الجدولي (المدني) ── */
    private fun civilToJdn(y: Int, m: Int, d: Int): Long =
        d + Math.ceil(29.5 * (m - 1)).toLong() + (y - 1) * 354L + floor((3 + 11.0 * y) / 30).toLong() + 1948439L
    private fun civilFromJdn(jdn: Long): CDate {
        val y = floor((30.0 * (jdn - 1948439) + 10646) / 10631).toInt()
        val m = minOf(12, Math.ceil((jdn - (29 + civilToJdn(y, 1, 1))) / 29.5).toInt() + 1).coerceAtLeast(1)
        val d = (jdn - civilToJdn(y, m, 1) + 1).toInt()
        return CDate(y, m, d)
    }

    /** الهجري القمري لتاريخ ميلادي، مع تعديل بالأيام */
    fun toHijri(g: LocalDate, method: HijriMethod, adjust: Int = 0): CDate {
        val shifted = g.plusDays(adjust.toLong())
        return when (method) {
            HijriMethod.CIVIL -> civilFromJdn(jdn(shifted))
            HijriMethod.UMM_AL_QURA -> try {
                val h = HijrahDate.from(shifted)
                CDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
            } catch (_: Exception) { civilFromJdn(jdn(shifted)) }
        }
    }

    fun fromHijri(d: CDate, method: HijriMethod, adjust: Int = 0): LocalDate {
        val base = when (method) {
            HijriMethod.CIVIL -> fromJdn(civilToJdn(d.year, d.month, d.day))
            HijriMethod.UMM_AL_QURA -> try { LocalDate.from(HijrahDate.of(d.year, d.month, d.day)) } catch (_: Exception) { fromJdn(civilToJdn(d.year, d.month, d.day)) }
        }
        return base.minusDays(adjust.toLong())
    }

    fun hijriMonthLength(year: Int, month: Int, method: HijriMethod): Int = when (method) {
        HijriMethod.CIVIL -> (civilToJdn(if (month == 12) year + 1 else year, if (month == 12) 1 else month + 1, 1) - civilToJdn(year, month, 1)).toInt()
        HijriMethod.UMM_AL_QURA -> try { HijrahDate.of(year, month, 1).lengthOfMonth() } catch (_: Exception) { if (month % 2 == 1) 30 else 29 }
    }

    /* ── الهجري الشمسي (خوارزمية جلالي) ── */
    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)

    private data class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun div(a: Int, b: Int) = a / b
    private fun mod(a: Int, b: Int) = a - (a / b) * b

    private fun jalCal(jy: Int): JalCal {
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): CDate {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return CDate(gy, gm, gd)
    }

    fun toSolarHijri(g: LocalDate): CDate {
        val jdn = g2d(g.year, g.monthValue, g.dayOfMonth)
        val gy = d2g(jdn).year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return CDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return CDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun fromSolarHijri(d: CDate): LocalDate {
        val r = jalCal(d.year)
        val jdn = g2d(r.gy, 3, r.march) + (d.month - 1) * 31 - div(d.month, 7) * (d.month - 7) + d.day - 1
        val g = d2g(jdn)
        return LocalDate.of(g.year, g.month, g.day)
    }

    fun isSolarLeap(year: Int) = jalCal(year).leap == 0
    fun solarMonthLength(year: Int, month: Int) = when {
        month <= 6 -> 31
        month <= 11 -> 30
        else -> if (isSolarLeap(year)) 30 else 29
    }

    /* ── واجهة موحّدة ── */
    fun convert(g: LocalDate, kind: CalendarKind, hijriMethod: HijriMethod, hijriAdjust: Int): CDate = when (kind) {
        CalendarKind.GREGORIAN -> CDate(g.year, g.monthValue, g.dayOfMonth)
        CalendarKind.JULIAN -> toJulian(g)
        CalendarKind.HIJRI -> toHijri(g, hijriMethod, hijriAdjust)
        CalendarKind.SOLAR_HIJRI -> toSolarHijri(g)
    }

    fun toGregorian(d: CDate, kind: CalendarKind, hijriMethod: HijriMethod, hijriAdjust: Int): LocalDate = when (kind) {
        CalendarKind.GREGORIAN -> LocalDate.of(d.year, d.month, d.day)
        CalendarKind.JULIAN -> fromJulian(d)
        CalendarKind.HIJRI -> fromHijri(d, hijriMethod, hijriAdjust)
        CalendarKind.SOLAR_HIJRI -> fromSolarHijri(d)
    }

    fun monthLength(year: Int, month: Int, kind: CalendarKind, hijriMethod: HijriMethod): Int = when (kind) {
        CalendarKind.GREGORIAN -> LocalDate.of(year, month, 1).lengthOfMonth()
        CalendarKind.JULIAN -> if (month == 2) (if (year % 4 == 0) 29 else 28) else LocalDate.of(2001, month, 1).lengthOfMonth()
        CalendarKind.HIJRI -> hijriMonthLength(year, month, hijriMethod)
        CalendarKind.SOLAR_HIJRI -> solarMonthLength(year, month)
    }

    fun monthName(kind: CalendarKind, month: Int, solarNames: SolarNames = SolarNames.ARABIC_ZODIAC, maghreb: Boolean = false): String = when (kind) {
        CalendarKind.GREGORIAN, CalendarKind.JULIAN -> (if (maghreb) maghrebMonths else gregorianMonths)[month - 1]
        CalendarKind.HIJRI -> hijriMonths[month - 1]
        CalendarKind.SOLAR_HIJRI -> solarNames.months[month - 1]
    }

    fun suffix(kind: CalendarKind) = when (kind) {
        CalendarKind.GREGORIAN -> "م"; CalendarKind.JULIAN -> "يو"; CalendarKind.HIJRI -> "هـ"; CalendarKind.SOLAR_HIJRI -> "هـ ش"
    }

    fun format(d: CDate, kind: CalendarKind, solarNames: SolarNames = SolarNames.ARABIC_ZODIAC, maghreb: Boolean = false) =
        "${d.day} ${monthName(kind, d.month, solarNames, maghreb)} ${d.year} ${suffix(kind)}"

    fun weekDay(g: LocalDate) = weekDays[g.dayOfWeek.value - 1]
}

/* ───────────────────────── حساب العمر ───────────────────────── */

data class AgeResult(
    val years: Int, val months: Int, val days: Int,
    val totalDays: Long, val totalWeeks: Long, val totalMonths: Int,
    val hijriYears: Int, val hijriMonths: Int, val hijriDays: Int,
    val daysToBirthday: Long, val nextBirthdayWeekDay: String,
    val birthWeekDay: String
)

object AgeCalc {
    private fun diff(a: CDate, b: CDate, monthLen: (Int, Int) -> Int): Triple<Int, Int, Int> {
        var y = b.year - a.year; var m = b.month - a.month; var d = b.day - a.day
        if (d < 0) {
            m -= 1
            val pm = if (b.month == 1) 12 else b.month - 1
            val py = if (b.month == 1) b.year - 1 else b.year
            d += monthLen(py, pm)
        }
        if (m < 0) { y -= 1; m += 12 }
        return Triple(y, m, d)
    }

    fun compute(birth: LocalDate, today: LocalDate, method: HijriMethod, adjust: Int): AgeResult {
        val (y, m, d) = diff(CDate(birth.year, birth.monthValue, birth.dayOfMonth), CDate(today.year, today.monthValue, today.dayOfMonth)) { yy, mm -> LocalDate.of(yy, mm, 1).lengthOfMonth() }
        val hb = Calendars.toHijri(birth, method, adjust)
        val ht = Calendars.toHijri(today, method, adjust)
        val (hy, hm, hd) = diff(hb, ht) { yy, mm -> Calendars.hijriMonthLength(yy, mm, method) }
        val total = today.toEpochDay() - birth.toEpochDay()
        var next = birth.withYear(today.year)
        if (next.isBefore(today)) next = next.plusYears(1)
        return AgeResult(
            y, m, d, total, total / 7, y * 12 + m, hy, hm, hd,
            next.toEpochDay() - today.toEpochDay(), Calendars.weekDay(next), Calendars.weekDay(birth)
        )
    }
}
