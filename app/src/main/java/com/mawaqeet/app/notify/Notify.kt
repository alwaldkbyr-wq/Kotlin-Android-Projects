package com.mawaqeet.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mawaqeet.app.MainActivity
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerCalc
import com.mawaqeet.app.data.AppSettings
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.SettingsStore
import com.mawaqeet.app.widget.Widgets
import java.time.LocalDate

/** جدولة تنبيهات الصلاة: منبّه واحد للحدث القادم، يُعاد ضبطه بعد كل إطلاق أو تغيير إعدادات */
object PrayerScheduler {
    const val CHANNEL_SOUND = "prayer"
    const val CHANNEL_SILENT = "prayer_silent"
    private const val REQ_ALARM = 4101
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_TIME = "time"
    const val EXTRA_BEFORE = "before"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val loud = NotificationChannel(CHANNEL_SOUND, "تنبيهات الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيه عند دخول وقت الصلاة مع صوت الإشعار الافتراضي"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
        }
        val silent = NotificationChannel(CHANNEL_SILENT, "تنبيهات الصلاة (صامتة)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تنبيه عند دخول وقت الصلاة بدون صوت"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(loud)
        nm.createNotificationChannel(silent)
    }

    private fun alarmIntent(context: Context, extras: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(context, PrayerAlarmReceiver::class.java).apply(extras)
        return PendingIntent.getBroadcast(context, REQ_ALARM, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** الحدث القادم: (الصلاة، وقتها، وقت التنبيه) أو null */
    fun nextEvent(s: AppSettings, now: Long): Triple<Prayer, Long, Long>? {
        val place = s.place
        val zone = place.zone
        val today = LocalDate.now(zone)
        val before = s.notifyBefore.coerceAtLeast(0)
        var best: Triple<Prayer, Long, Long>? = null
        for (d in 0..1) {
            val start = today.plusDays(d.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val day = PrayerCalc.compute(start, place.lat, place.lon, s.prayerSettings)
            for (p in Prayer.entries) {
                if (!s.notifyFor(p)) continue
                val t = day[p] ?: continue
                val at = t - before * 60_000L
                if (at > now && (best == null || at < best.third)) best = Triple(p, t, at)
            }
        }
        return best
    }

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val s = SettingsStore.get(app).value
        val ev = runCatching { nextEvent(s, System.currentTimeMillis()) }.getOrNull()
        if (ev == null) {
            runCatching { am.cancel(alarmIntent(app)) }
            return
        }
        val (prayer, time, at) = ev
        val pi = alarmIntent(app) {
            putExtra(EXTRA_PRAYER, prayer.ordinal)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_BEFORE, s.notifyBefore.coerceAtLeast(0))
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        try {
            val ordinal = intent.getIntExtra(PrayerScheduler.EXTRA_PRAYER, -1)
            val time = intent.getLongExtra(PrayerScheduler.EXTRA_TIME, 0L)
            val before = intent.getIntExtra(PrayerScheduler.EXTRA_BEFORE, 0)
            val prayer = Prayer.entries.getOrNull(ordinal)
            if (prayer != null) show(app, prayer, time, before)
        } finally {
            PrayerScheduler.reschedule(app)
            Widgets.updateAll(app)
        }
    }

    private fun show(context: Context, prayer: Prayer, time: Long, before: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val s = SettingsStore.get(context).value
        val place = s.place
        PrayerScheduler.createChannels(context)

        val text = when {
            prayer == Prayer.SUNRISE && before > 0 -> "بقي $before ${if (before == 1) "دقيقة" else "دقائق"} على الشروق"
            prayer == Prayer.SUNRISE -> "طلعت الشمس — انتهى وقت الفجر"
            before > 0 -> "بقي $before ${if (before == 1) "دقيقة" else "دقائق"} على صلاة ${prayer.ar}"
            else -> "حان الآن وقت صلاة ${prayer.ar}"
        }
        val detail = "${Fmt.time(time, place.zone, s.h24)} • ${place.name}"

        val open = PendingIntent.getActivity(
            context, 100 + prayer.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = if (s.notifySound) PrayerScheduler.CHANNEL_SOUND else PrayerScheduler.CHANNEL_SILENT
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(text)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (s.notifySound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(200 + prayer.ordinal, n) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        PrayerScheduler.createChannels(app)
        PrayerScheduler.reschedule(app)
        Widgets.updateAll(app)
    }
}
