package com.mawaqeet.app

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class PrayerMethod(
    val title: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0
) {
    MUSLIM_WORLD_LEAGUE("رابطة العالم الإسلامي", 18.0, 17.0),
    UMM_AL_QURA("أم القرى", 18.5, 0.0, 90),
    EGYPTIAN("الهيئة المصرية", 19.5, 17.5),
    KARACHI("جامعة العلوم الإسلامية - كراتشي", 18.0, 18.0),
    NORTH_AMERICA("أمريكا الشمالية (ISNA)", 15.0, 15.0)
}

data class PrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val sunset: String,
    val maghrib: String,
    val isha: String,
    val dayLength: String
)

/**
 * Offline solar prayer-time calculation.
 * Results are estimates and should be checked against the local official timetable.
 */
object PrayerCalculator {
    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        utcOffsetHours: Double,
        method: PrayerMethod = PrayerMethod.MUSLIM_WORLD_LEAGUE,
        hanafiAsr: Boolean = false
    ): PrayerTimes {
        val n = date.dayOfYear.toDouble()
        val gamma = 2.0 * Math.PI / 365.0 * (n - 1.0)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma)
                - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma)
                - 0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma)
                - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val lat = Math.toRadians(latitude.coerceIn(-89.9, 89.9))
        val noon = 12.0 + utcOffsetHours - longitude / 15.0 - eq / 60.0

        fun hourAngle(altitudeDegrees: Double): Double {
            val alt = Math.toRadians(altitudeDegrees)
            val c = (sin(alt) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
            return if (c !in -1.0..1.0) Double.NaN else Math.toDegrees(acos(c)) / 15.0
        }
        fun timeAt(angle: Double, morning: Boolean): Double {
            val h = hourAngle(angle)
            return if (h.isNaN()) Double.NaN else noon + if (morning) -h else h
        }
        val asrFactor = if (hanafiAsr) 2.0 else 1.0
        val asrAngle = Math.toDegrees(atan(1.0 / (asrFactor + tan(abs(lat - decl)))))
        fun fmt(hours: Double): String {
            if (hours.isNaN() || hours.isInfinite()) return "--:--"
            val mins = ((hours * 60.0).toInt() % 1440 + 1440) % 1440
            return "%02d:%02d".format(mins / 60, mins % 60)
        }

        val sunrise = timeAt(-0.833, true)
        val sunset = timeAt(-0.833, false)
        val maghrib = sunset
        val fajr = timeAt(-method.fajrAngle, true)
        val isha = if (method.ishaIntervalMinutes > 0) {
            maghrib + method.ishaIntervalMinutes / 60.0
        } else {
            timeAt(-method.ishaAngle, false)
        }
        val asr = timeAt(asrAngle, false)
        val length = if (sunrise.isNaN() || sunset.isNaN()) "--:--" else {
            val minutes = ((sunset - sunrise) * 60).toInt().coerceAtLeast(0)
            "%02d:%02d".format(minutes / 60, minutes % 60)
        }
        return PrayerTimes(fmt(fajr), fmt(sunrise), fmt(noon), fmt(asr), fmt(sunset), fmt(maghrib), fmt(isha), length)
    }

    private fun abs(value: Double) = kotlin.math.abs(value)
}
