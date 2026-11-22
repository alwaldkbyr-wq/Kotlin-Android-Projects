package com.mawaqeet.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import com.mawaqeet.app.MainActivity
import com.mawaqeet.app.R
import com.mawaqeet.app.astro.CalendarKind
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.Mansions
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerCalc
import com.mawaqeet.app.astro.PrayerDay
import com.mawaqeet.app.data.AppSettings
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.SettingsStore
import java.time.LocalDate
import java.util.Locale

/** تحديث كل الودجات (التاريخ، الصلاة القادمة، القمر) وجدولة التحديث التالي */
object Widgets {
    private const val REQ_REFRESH = 4201

    private fun clickIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 4200,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun ids(context: Context, mgr: AppWidgetManager, cls: Class<*>): IntArray =
        mgr.getAppWidgetIds(ComponentName(context, cls))

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        runCatching { DateWidget.update(app, mgr, ids(app, mgr, DateWidget::class.java)) }
        runCatching { PrayerWidget.update(app, mgr, ids(app, mgr, PrayerWidget::class.java)) }
        runCatching { MoonWidget.update(app, mgr, ids(app, mgr, MoonWidget::class.java)) }
        runCatching { GlassPrayerWidget.update(app, mgr, ids(app, mgr, GlassPrayerWidget::class.java)) }
        runCatching { GlassHijriWidget.update(app, mgr, ids(app, mgr, GlassHijriWidget::class.java)) }
        runCatching { GlassDayWidget.update(app, mgr, ids(app, mgr, GlassDayWidget::class.java)) }
        runCatching { scheduleRefresh(app) }
    }

    /** منبّه غير دقيق عند منتصف الليل المحلي أو عند الصلاة القادمة (أيهما أسبق) لتحديث الودجات */
    private fun scheduleRefresh(context: Context) {
        val s = SettingsStore.get(context).value
        val zone = s.place.zone
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 2000
        val nextPrayer = nextPrayer(s, now)?.second?.plus(3000)
        val at = if (nextPrayer != null && nextPrayer < midnight) nextPrayer else midnight
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(context, DateWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        val pi = PendingIntent.getBroadcast(context, REQ_REFRESH, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
    }

    /** الصلاة القادمة (بدون الشروق) من اليوم أو الغد */
    fun nextPrayer(s: AppSettings, now: Long): Pair<Prayer, Long>? {
        val p = s.place
        val today = LocalDate.now(p.zone)
        for (d in 0..1) {
            val start = today.plusDays(d.toLong()).atStartOfDay(p.zone).toInstant().toEpochMilli()
            val day = PrayerCalc.compute(start, p.lat, p.lon, s.prayerSettings)
            day.next(now)?.let { return it }
        }
        return null
    }

    internal fun dayTimes(s: AppSettings, date: LocalDate): PrayerDay {
        val p = s.place
        return PrayerCalc.compute(date.atStartOfDay(p.zone).toInstant().toEpochMilli(), p.lat, p.lon, s.prayerSettings)
    }

    internal fun open(context: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(R.id.root, clickIntent(context))
    }

    internal fun push(mgr: AppWidgetManager, ids: IntArray, views: RemoteViews) {
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, views)
    }
}

class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
        Widgets.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // منبّه التحديث الدوري يصل بلا معرّفات: نحدّث كل الودجات
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE && intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) == null) {
            Widgets.updateAll(context)
        } else super.onReceive(context, intent)
    }

    companion object {
        fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val s = SettingsStore.get(context).value
            val date = LocalDate.now(s.place.zone)
            val v = RemoteViews(context.packageName, R.layout.widget_date)
            v.setImageViewBitmap(R.id.img, WidgetText.lines(context, listOf(
                WidgetText.Line(Calendars.weekDay(date), 15f, WidgetText.GOLD, display = false, bold = true),
                WidgetText.Line(Calendars.format(Calendars.toHijri(date, s.hijriMethod, s.hijriAdjust), CalendarKind.HIJRI), 25f, WidgetText.TEXT, display = true, bold = true),
                WidgetText.Line(Fmt.gregorian(date, s.maghrebMonths), 15f, WidgetText.TEXT),
                WidgetText.Line(Calendars.format(Calendars.toSolarHijri(date), CalendarKind.SOLAR_HIJRI, s.solarNames), 13f, WidgetText.MUTED)
            )))
            Widgets.open(context, v)
            Widgets.push(mgr, ids, v)
        }
    }
}

class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val s = SettingsStore.get(context).value
            val place = s.place
            val now = System.currentTimeMillis()
            val today = LocalDate.now(place.zone)
            val day = Widgets.dayTimes(s, today)
            val next = Widgets.nextPrayer(s, now)
            val v = RemoteViews(context.packageName, R.layout.widget_prayer)
            if (next != null) {
                v.setImageViewBitmap(R.id.head, WidgetText.lines(context, listOf(
                    WidgetText.Line("الصلاة القادمة: ${next.first.ar}", 15f, WidgetText.GOLD, bold = true),
                    WidgetText.Line(Fmt.time(next.second, place.zone, s.h24), 32f, WidgetText.TEXT, display = true, bold = true)
                )))
                val base = SystemClock.elapsedRealtime() + (next.second - now)
                v.setChronometer(R.id.countdown, base, null, true)
                v.setChronometerCountDown(R.id.countdown, true)
            } else {
                v.setImageViewBitmap(R.id.head, WidgetText.lines(context, listOf(
                    WidgetText.Line(place.name, 15f, WidgetText.GOLD, bold = true),
                    WidgetText.Line("--:--", 32f, WidgetText.TEXT, display = true, bold = true)
                )))
                v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime(), null, false)
            }
            val cells = Prayer.entries.map { p ->
                val t = Fmt.time(day[p], place.zone, s.h24).replace(" ص", "").replace(" م", "")
                Triple(p.ar, t, next != null && next.first == p && next.second == day[p])
            }
            v.setImageViewBitmap(R.id.row, WidgetText.columns(context, cells))
            Widgets.open(context, v)
            Widgets.push(mgr, ids, v)
        }
    }
}

class MoonWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val m = Phases.state(now)
            val mansion = Mansions.names[Mansions.moonMansion(now).first]
            val v = RemoteViews(context.packageName, R.layout.widget_moon)
            v.setTextViewText(R.id.emoji, Phases.phaseEmoji(m.phaseAngle))
            v.setImageViewBitmap(R.id.img, WidgetText.lines(context, listOf(
                WidgetText.Line(Phases.phaseName(m.phaseAngle), 17f, WidgetText.TEXT, display = true, bold = true),
                WidgetText.Line("الإضاءة ${String.format(Locale.US, "%.0f", m.illumination * 100)}%  •  العمر ${String.format(Locale.US, "%.1f", m.ageDays)} يوم", 12.5f, WidgetText.MUTED),
                WidgetText.Line("المنزلة: $mansion", 12.5f, WidgetText.GOLD)
            )))
            Widgets.open(context, v)
            Widgets.push(mgr, ids, v)
        }
    }
}


/**
 * نصوص الودجات تُرسم صوراً بخطوط «ثمانية» نفسها، لأن الشاشة الرئيسية لا تحمّل خطوط التطبيق في RemoteViews.
 */
object WidgetText {
    val TEXT = 0xFFEEF1FF.toInt()
    val GOLD = 0xFFF5C26B.toInt()
    val MUTED = 0xFFA9B1D6.toInt()

    class Line(val text: String, val sp: Float, val color: Int, val display: Boolean = false, val bold: Boolean = false)

    internal fun font(context: Context, display: Boolean, bold: Boolean): android.graphics.Typeface {
        val id = when {
            display && bold -> R.font.thmanyah_serif_display_bold
            display -> R.font.thmanyah_serif_display_medium
            bold -> R.font.thmanyah_serif_text_bold
            else -> R.font.thmanyah_serif_text_regular
        }
        return runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, id) }.getOrNull() ?: android.graphics.Typeface.DEFAULT
    }

    private fun paint(context: Context, l: Line, scale: Float) = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(context, l.display, l.bold); textSize = l.sp * scale; color = l.color
        textAlign = android.graphics.Paint.Align.CENTER
    }

    /** أسطر متوسّطة فوق بعضها */
    fun lines(context: Context, lines: List<Line>): android.graphics.Bitmap {
        val scale = context.resources.displayMetrics.scaledDensity.coerceAtMost(3f)
        val paints = lines.map { paint(context, it, scale) }
        val w = (paints.zip(lines).maxOf { (p, l) -> p.measureText(l.text) } + 16 * scale).toInt().coerceAtLeast(10)
        val heights = paints.map { val fm = it.fontMetrics; fm.descent - fm.ascent }
        val h = (heights.sum() + 4 * scale).toInt().coerceAtLeast(10)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        var y = 2 * scale
        paints.forEachIndexed { i, p -> y += -p.fontMetrics.ascent; c.drawText(lines[i].text, w / 2f, y, p); y += p.fontMetrics.descent }
        return bmp
    }

    /** أعمدة الصلوات الست: الاسم فوق الوقت، من اليمين إلى اليسار، والقادمة بالذهبي */
    fun columns(context: Context, cells: List<Triple<String, String, Boolean>>): android.graphics.Bitmap {
        val scale = context.resources.displayMetrics.scaledDensity.coerceAtMost(3f)
        val colW = 58 * scale
        val w = (colW * cells.size).toInt()
        val nameP = paint(context, Line("", 11.5f, MUTED), scale)
        val timeP = paint(context, Line("", 13f, TEXT, display = true, bold = true), scale)
        val h = ((nameP.fontMetrics.descent - nameP.fontMetrics.ascent) + (timeP.fontMetrics.descent - timeP.fontMetrics.ascent) + 6 * scale).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        cells.forEachIndexed { i, (name, time, hot) ->
            val x = w - colW * i - colW / 2
            nameP.color = if (hot) GOLD else MUTED
            timeP.color = if (hot) GOLD else TEXT
            val y1 = 2 * scale - nameP.fontMetrics.ascent
            c.drawText(name, x, y1, nameP)
            c.drawText(time, x, y1 + nameP.fontMetrics.descent - timeP.fontMetrics.ascent + 2 * scale, timeP)
        }
        return bmp
    }
}
