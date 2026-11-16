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
import java.time.DayOfWeek
import java.time.LocalDate

/** جدولة تنبيهات الصلاة: منبّه واحد للحدث القادم، يُعاد ضبطه بعد كل إطلاق أو تغيير إعدادات */
object PrayerScheduler {
    const val CHANNEL_SOUND = "prayer"
    const val CHANNEL_SILENT = "prayer_silent"
    private const val REQ_ALARM = 4101
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_TIME = "time"
    const val EXTRA_BEFORE = "before"
    /** نوع الحدث: غيابه يعني صلاة (توافق مع المنبّهات القديمة) */
    const val EXTRA_KIND = "kind"
    const val CHANNEL_REMIND = "reminders"
    const val KIND_PRAYER = "prayer"
    const val KIND_FRIDAY = "friday"
    const val KIND_QIYAM = "qiyam"
    const val KIND_DUHA = "duha"
    const val KIND_IMSAK = "imsak"

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
        val remind = NotificationChannel(CHANNEL_REMIND, "تذكيرات", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تذكير الجمعة وقيام الليل والضحى والإمساك"
        }
        nm.createNotificationChannel(remind)
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

    /** حدث مجدول: النوع، الصلاة (للصلوات فقط)، وقت الحدث، وقت التنبيه، دقائق «قبل» */
    data class Scheduled(val kind: String, val prayer: Prayer?, val time: Long, val at: Long, val before: Int)

    /** أقرب حدث قادم بين الصلوات والتذكيرات الإضافية */
    fun nextAlarm(s: AppSettings, now: Long): Scheduled? {
        val place = s.place
        val zone = place.zone
        val today = LocalDate.now(zone)
        val before = s.notifyBefore.coerceAtLeast(0)
        val fridayBefore = s.fridayBefore.coerceAtLeast(0)
        val extras = s.remindFriday || s.remindQiyam || s.remindDuha || s.remindImsak
        var best: Scheduled? = null
        fun consider(e: Scheduled) {
            if (e.at > now && (best == null || e.at < best!!.at)) best = e
        }
        // اليوم السابق مطلوب لليلة التي بدأت أمس وثلثها الأخير بعد منتصف الليل
        val first = if (s.remindQiyam) -1 else 0
        val last = if (extras) 2 else 1
        val days = HashMap<Int, Map<Prayer, Long?>>()
        for (d in first..last) {
            val date = today.plusDays(d.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            days[d] = PrayerCalc.compute(start, place.lat, place.lon, s.prayerSettings).times
        }
        for (d in 0..1) {
            val day = days[d] ?: continue
            for (p in Prayer.entries) {
                if (!s.notifyFor(p)) continue
                val t = day[p] ?: continue
                consider(Scheduled(KIND_PRAYER, p, t, t - before * 60_000L, before))
            }
        }
        for (d in first..1) {
            val day = days[d] ?: continue
            val date = today.plusDays(d.toLong())
            if (d >= 0) {
                if (s.remindFriday && date.dayOfWeek == DayOfWeek.FRIDAY) {
                    val dhuhr = day[Prayer.DHUHR]
                    if (dhuhr != null) consider(Scheduled(KIND_FRIDAY, null, dhuhr, dhuhr - fridayBefore * 60_000L, fridayBefore))
                }
                if (s.remindDuha) {
                    val sr = day[Prayer.SUNRISE]
                    if (sr != null) consider(Scheduled(KIND_DUHA, null, sr + 20 * 60_000L, sr + 20 * 60_000L, 0))
                }
                if (s.remindImsak) {
                    val fajr = day[Prayer.FAJR]
                    if (fajr != null) consider(Scheduled(KIND_IMSAK, null, fajr, fajr - 10 * 60_000L, 10))
                }
            }
            if (s.remindQiyam) {
                // بداية الثلث الأخير من الليل: من مغرب هذا اليوم إلى فجر اليوم التالي
                val maghrib = day[Prayer.MAGHRIB]
                val nextFajr = days[d + 1]?.get(Prayer.FAJR)
                if (maghrib != null && nextFajr != null && nextFajr > maghrib) {
                    val at = maghrib + (nextFajr - maghrib) * 2 / 3
                    consider(Scheduled(KIND_QIYAM, null, at, at, 0))
                }
            }
        }
        return best
    }

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val s = SettingsStore.get(app).value
        val ev = runCatching { nextAlarm(s, System.currentTimeMillis()) }.getOrNull()
        if (ev == null) {
            runCatching { am.cancel(alarmIntent(app)) }
            return
        }
        val at = ev.at
        val pi = alarmIntent(app) {
            putExtra(EXTRA_KIND, ev.kind)
            putExtra(EXTRA_PRAYER, ev.prayer?.ordinal ?: -1)
            putExtra(EXTRA_TIME, ev.time)
            putExtra(EXTRA_BEFORE, ev.before)
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
            val kind = intent.getStringExtra(PrayerScheduler.EXTRA_KIND) ?: PrayerScheduler.KIND_PRAYER
            if (kind == PrayerScheduler.KIND_PRAYER) {
                val prayer = Prayer.entries.getOrNull(ordinal)
                if (prayer != null) show(app, prayer, time, before)
            } else {
                showReminder(app, kind, time, before)
            }
        } finally {
            PrayerScheduler.reschedule(app)
            Widgets.updateAll(app)
        }
    }

    private fun showReminder(context: Context, kind: String, time: Long, before: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val s = SettingsStore.get(context).value
        val place = s.place
        PrayerScheduler.createChannels(context)
        val (title, body, id) = when (kind) {
            PrayerScheduler.KIND_FRIDAY -> Triple("تذكير الجمعة", "اقترب وقت الجمعة — اغتسل وبكّر، واقرأ سورة الكهف", 300)
            PrayerScheduler.KIND_QIYAM -> Triple("قيام الليل", "بدأ الثلث الأخير من الليل — وقت القيام والدعاء والاستغفار", 301)
            PrayerScheduler.KIND_DUHA -> Triple("صلاة الضحى", "حان وقت صلاة الضحى", 302)
            else -> Triple("الإمساك", "بقي $before دقائق على أذان الفجر — أمسك عن الطعام والشراب", 303)
        }
        val detail = "${Fmt.time(time, place.zone, s.h24)} • ${place.name}"
        val open = PendingIntent.getActivity(
            context, 300 + (id - 300),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, PrayerScheduler.CHANNEL_REMIND)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$detail"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
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
