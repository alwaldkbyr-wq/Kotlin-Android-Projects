package com.mawaqeet.app.data

import androidx.compose.runtime.staticCompositionLocalOf
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.CalendarKind
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.PrayerCalc
import com.mawaqeet.app.astro.PrayerDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * بيئة الشاشات: الإعدادات الحالية والموقع والوقت، مع التنقل وتحديث الإعدادات.
 * تصل كل شاشة إليها بـ `LocalEnv.current`.
 */
class Env(
    val settings: AppSettings,
    /** الوقت الحالي (يتحدّث كل ثانية) */
    val now: Long,
    private val updater: ((AppSettings) -> AppSettings) -> Unit,
    /** فتح شاشة بمسارها من [Routes] */
    val navigate: (String) -> Unit,
    /** الرجوع للشاشة السابقة */
    val back: () -> Unit,
    /** طلب تحديد الموقع بالـ GPS */
    val requestGps: () -> Unit
) {
    val place: Place get() = settings.place
    val zone: ZoneId get() = place.zone
    val lat: Double get() = place.lat
    val lon: Double get() = place.lon

    fun update(f: (AppSettings) -> AppSettings) = updater(f)

    val today: LocalDate get() = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /** بداية اليوم المحلي (منتصف الليل) بالميلي ثانية */
    fun dayStart(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun dateTimeOf(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    /** مواقيت الصلاة ليوم معيّن في الموقع الحالي */
    fun prayers(date: LocalDate = today): PrayerDay = PrayerCalc.compute(dayStart(date), lat, lon, settings.prayerSettings)

    // ───── التنسيق ─────
    fun time(millis: Long?, seconds: Boolean = false): String = Fmt.time(millis, zone, settings.h24, seconds)
    fun date(millis: Long): String = Fmt.gregorian(dateOf(millis), settings.maghrebMonths)
    fun dateTime(millis: Long?): String = if (millis == null) "--" else "${Fmt.gregorian(dateOf(millis), settings.maghrebMonths)} • ${time(millis)}"
    fun hijri(date: LocalDate): String = Calendars.format(Calendars.toHijri(date, settings.hijriMethod, settings.hijriAdjust), CalendarKind.HIJRI)
    fun gregorian(date: LocalDate): String = Fmt.gregorian(date, settings.maghrebMonths)
    fun solarHijri(date: LocalDate): String = Calendars.format(Calendars.toSolarHijri(date), CalendarKind.SOLAR_HIJRI, settings.solarNames)
    fun julian(date: LocalDate): String = Calendars.format(Calendars.toJulian(date), CalendarKind.JULIAN, maghreb = settings.maghrebMonths)
    fun inCalendar(date: LocalDate, kind: CalendarKind): String = when (kind) {
        CalendarKind.GREGORIAN -> gregorian(date)
        CalendarKind.JULIAN -> julian(date)
        CalendarKind.HIJRI -> hijri(date)
        CalendarKind.SOLAR_HIJRI -> solarHijri(date)
    }
}

val LocalEnv = staticCompositionLocalOf<Env> { error("Env غير متوفر") }

object Fmt {
    fun time(millis: Long?, zone: ZoneId, h24: Boolean, seconds: Boolean = false): String {
        if (millis == null) return "--:--"
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        val pattern = if (h24) (if (seconds) "HH:mm:ss" else "HH:mm") else (if (seconds) "h:mm:ss" else "h:mm")
        val s = t.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
        return if (h24) s else s + (if (t.hour < 12) " ص" else " م")
    }

    fun gregorian(d: LocalDate, maghreb: Boolean): String =
        "${d.dayOfMonth} ${(if (maghreb) Calendars.maghrebMonths else Calendars.gregorianMonths)[d.monthValue - 1]} ${d.year}"

    /** مدة بالساعات والدقائق من ميلي ثانية */
    fun span(ms: Long): String {
        val totalMin = Math.max(0L, ms / 60000L)
        val h = totalMin / 60; val m = totalMin % 60
        return if (h > 0) "${h} س ${m} د" else "${m} د"
    }

    /** عدّ تنازلي hh:mm:ss */
    fun countdown(ms: Long): String {
        val s = Math.max(0L, ms / 1000L)
        return "%02d:%02d:%02d".format(Locale.US, s / 3600, (s / 60) % 60, s % 60)
    }

    fun deg(v: Double, digits: Int = 1) = "%.${digits}f°".format(Locale.US, v)
    fun num(v: Double, digits: Int = 1) = "%.${digits}f".format(Locale.US, v)

    /** درجات ودقائق: 36°45′ ش */
    fun latLon(lat: Double, lon: Double): String {
        fun dm(v: Double): String { val a = Math.abs(v); val d = a.toInt(); val m = ((a - d) * 60).toInt(); return "$d°${"%02d".format(Locale.US, m)}′" }
        return "${dm(lat)} ${if (lat >= 0) "ش" else "ج"}  ${dm(lon)} ${if (lon >= 0) "ق" else "غ"}"
    }

    /** اتجاه نصي للسمت */
    fun direction(az: Double): String {
        val names = listOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")
        return names[(((az + 22.5) % 360) / 45).toInt().coerceIn(0, 7)]
    }

    fun daysUntil(from: Long, to: Long): Long = Math.floorDiv(to - from, DAY_MS)
}
