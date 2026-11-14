package com.mawaqeet.app

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

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
 * Lightweight offline solar-position and prayer-time calculations.
 * Default angles: Muslim World League (Fajr 18°, Isha 17°).
 * Results are approximate; high-latitude adjustments and local authority
 * conventions will be added in a later iteration.
 */
object PrayerCalculator {
    fun calculate(date: LocalDate, latitude: Double, longitude: Double, utcOffsetHours: Double): PrayerTimes {
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
        fun asrTime(): Double {
            val factor = 1.0
            val angle = Math.toDegrees(atan(1.0 / (factor + tan(abs(lat - decl)))))
            return timeAt(angle, false)
        }
        fun fmt(hours: Double): String {
            if (hours.isNaN() || hours.isInfinite()) return "--:--"
            val mins = ((hours * 60.0).toInt() % 1440 + 1440) % 1440
            return "%02d:%02d".format(mins / 60, mins % 60)
        }
        val sunrise = timeAt(-0.833, true)
        val sunset = timeAt(-0.833, false)
        val fajr = timeAt(-18.0, true)
        val isha = timeAt(-17.0, false)
        val asr = asrTime()
        val length = if (sunrise.isNaN() || sunset.isNaN()) "--:--" else {
            val m = ((sunset - sunrise) * 60).toInt().coerceAtLeast(0)
            "%02d:%02d".format(m / 60, m % 60)
        }
        return PrayerTimes(fmt(fajr), fmt(sunrise), fmt(noon), fmt(asr), fmt(sunset), fmt(sunset), fmt(isha), length)
    }

    private fun abs(value: Double) = kotlin.math.abs(value)
}
