package com.mawaqeet.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.widget.RemoteViews
import com.mawaqeet.app.R
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.SettingsStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** أدوات رسم الودجات الزجاجية (صور نقطية بخطوط «ثمانية») */
internal object GlassDraw {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val W80 = 0xCCFFFFFF.toInt()
    const val W75 = 0xBFFFFFFF.toInt()
    const val W70 = 0xB3FFFFFF.toInt()
    const val W35 = 0x59FFFFFF
    const val W25 = 0x40FFFFFF
    const val W15 = 0x26FFFFFF

    /** أقصى عدد بكسلات للصورة الواحدة (~1.4 ميغابايت ARGB_8888) */
    private const val MAX_PIXELS = 350_000

    /** مقاس صورة بالبكسل مع k = بكسل لكل dp بعد تحديد الذاكرة */
    class Size(val w: Int, val h: Int, val k: Float)

    fun size(context: Context, wDp: Float, hDp: Float): Size {
        var k = context.resources.displayMetrics.density.coerceIn(1f, 3f)
        val wd = max(wDp, 20f)
        val hd = max(hDp, 4f)
        val area = wd * k * hd * k
        if (area > MAX_PIXELS) k *= sqrt(MAX_PIXELS / area)
        return Size(max(1, (wd * k).toInt()), max(1, (hd * k).toInt()), k)
    }

    /** مقاس الودجة بوحدة dp من خيارات المضيف مع قيم احتياطية */
    fun optionsDp(mgr: AppWidgetManager, id: Int, defW: Float, defH: Float): Pair<Float, Float> {
        val o: Bundle? = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return (if (w > 0) w.toFloat() else defW) to (if (h > 0) h.toFloat() else defH)
    }

    fun paint(context: Context, display: Boolean, bold: Boolean, px: Float, color: Int, shadowK: Float = 0f): TextPaint {
        val tf: Typeface = WidgetText.font(context, display, bold)
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = px
            this.color = color
            textAlign = Paint.Align.CENTER
            if (shadowK > 0f) setShadowLayer(4f * shadowK, 0f, 1f * shadowK, 0x55000000)
        }
    }

    /** يصغّر الخط حتى يتسع النص في العرض المعطى */
    fun fit(p: TextPaint, text: String, maxW: Float) {
        val w = p.measureText(text)
        if (w > maxW && w > 0f) p.textSize = p.textSize * maxW / w
    }

    fun drawCentered(c: Canvas, text: String, cx: Float, cy: Float, p: TextPaint) {
        val fm = p.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    /** صورة نص واحد بحجمه الطبيعي مع هامش للظل */
    fun textBitmap(context: Context, text: String, dp: Float, color: Int, display: Boolean, bold: Boolean, shadow: Boolean): Bitmap {
        val k = context.resources.displayMetrics.density.coerceIn(1f, 3f)
        val p = paint(context, display, bold, dp * k, color, if (shadow) k else 0f)
        val pad = 6f * k
        val w = (p.measureText(text) + 2 * pad).toInt().coerceAtLeast(8)
        val fm = p.fontMetrics
        val h = ((fm.descent - fm.ascent) + 2 * pad).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawText(text, w / 2f, pad - fm.ascent, p)
        return bmp
    }
}

class GlassPrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // يضمن جدولة منبّه التحديث عند كل صلاة ومنتصف الليل
        Widgets.updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        update(context, appWidgetManager, intArrayOf(appWidgetId))
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

            // الصلاة السابقة: آخر وقت صلاة (بلا الشروق) لا يتجاوز الآن
            val all = ArrayList<Long>()
            for (d in -1L..1L) {
                val pd = Widgets.dayTimes(s, today.plusDays(d))
                for (p in Prayer.entries) if (p != Prayer.SUNRISE) pd[p]?.let { all.add(it) }
            }
            val prev = all.filter { it <= now }.maxOrNull()
            val fraction = if (next != null && prev != null && next.second > prev)
                ((now - prev).toFloat() / (next.second - prev).toFloat()).coerceIn(0f, 1f) else 0f

            val prayers = Prayer.entries.filter { it != Prayer.SUNRISE }
            val cells = prayers.map { p ->
                val t = Fmt.time(day[p], place.zone, s.h24).replace(" ص", "").replace(" م", "")
                Triple(p.ar, t, next != null && next.first == p)
            }

            for (id in ids) {
                val (wDp, _) = GlassDraw.optionsDp(mgr, id, 320f, 110f)
                val v = RemoteViews(context.packageName, R.layout.widget_glass_prayer)

                val fmt = if (s.h24) "H:mm" else "h:mm"
                v.setCharSequence(R.id.clock, "setFormat12Hour", fmt)
                v.setCharSequence(R.id.clock, "setFormat24Hour", fmt)
                v.setString(R.id.clock, "setTimeZone", place.zone.id)

                val nameText = next?.first?.ar ?: "--"
                v.setImageViewBitmap(R.id.name, GlassDraw.textBitmap(context, nameText, 46f, GlassDraw.WHITE, display = true, bold = true, shadow = true))
                v.setImageViewBitmap(R.id.until, GlassDraw.textBitmap(context,
                    if (next != null) "بقي على ${next.first.ar}" else "الصلاة القادمة", 12f, GlassDraw.W70, display = false, bold = false, shadow = false))

                if (next != null) {
                    v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + (next.second - now), null, true)
                    v.setChronometerCountDown(R.id.countdown, true)
                } else {
                    v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime(), null, false)
                }

                val innerW = wDp - 28f
                v.setImageViewBitmap(R.id.row, rowBitmap(context, innerW, cells))
                v.setImageViewBitmap(R.id.progress, progressBitmap(context, innerW, fraction))
                Widgets.open(context, v)
                mgr.updateAppWidget(id, v)
            }
        }

        /** الصلوات الخمس من اليمين إلى اليسار؛ القادمة داخل كبسولة زجاجية */
        private fun rowBitmap(context: Context, wDp: Float, cells: List<Triple<String, String, Boolean>>): Bitmap {
            val sz = GlassDraw.size(context, wDp, 44f)
            val k = sz.k
            val bmp = Bitmap.createBitmap(sz.w, sz.h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val w = sz.w.toFloat()
            val h = sz.h.toFloat()
            val colW = w / max(cells.size, 1)
            val nameP = GlassDraw.paint(context, false, false, 11f * k, GlassDraw.W80)
            val timeP = GlassDraw.paint(context, true, true, 15f * k, GlassDraw.WHITE)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = GlassDraw.W15 }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, k); color = GlassDraw.W35 }
            cells.forEachIndexed { i, (name, time, hot) ->
                val cx = w - colW * i - colW / 2f
                if (hot) {
                    val inset = max(1f, k)
                    val r = RectF(cx - colW / 2f + 3f * k, inset, cx + colW / 2f - 3f * k, h - inset)
                    c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
                    c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, stroke)
                }
                GlassDraw.fit(timeP, time, colW - 10f * k)
                val nf = nameP.fontMetrics
                val tf = timeP.fontMetrics
                val nameH = nf.descent - nf.ascent
                val timeH = tf.descent - tf.ascent
                val gap = 1f * k
                val top = (h - (nameH + timeH + gap)) / 2f
                c.drawText(name, cx, top - nf.ascent, nameP)
                c.drawText(time, cx, top + nameH + gap - tf.ascent, timeP)
                timeP.textSize = 15f * k
            }
            return bmp
        }

        /** شريط التقدم: مسار شبه شفاف وتعبئة بيضاء تنمو من اليمين */
        private fun progressBitmap(context: Context, wDp: Float, fraction: Float): Bitmap {
            val sz = GlassDraw.size(context, wDp, 6f)
            val bmp = Bitmap.createBitmap(sz.w, sz.h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val w = sz.w.toFloat()
            val h = sz.h.toFloat()
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassDraw.W25 }
            val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassDraw.WHITE }
            val rect = RectF(0f, 0f, w, h)
            c.drawRoundRect(rect, h / 2f, h / 2f, track)
            if (fraction > 0f) {
                val path = Path().apply { addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW) }
                c.save()
                c.clipPath(path)
                val fw = max(w * fraction, h)
                c.drawRect(w - fw, 0f, w, h, fillP)
                c.restore()
            }
            return bmp
        }
    }
}

class GlassHijriWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        update(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        private val GREG = DateTimeFormatter.ofPattern("yyyy / MM / dd", Locale.US)

        fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val s = SettingsStore.get(context).value
            val date = LocalDate.now(s.place.zone)
            val hj = Calendars.toHijri(date, s.hijriMethod, s.hijriAdjust)
            val month = Calendars.hijriMonths[(hj.month - 1).coerceIn(0, 11)]
            val greg = date.format(GREG)
            for (id in ids) {
                val (wDp, hDp) = GlassDraw.optionsDp(mgr, id, 160f, 160f)
                val v = RemoteViews(context.packageName, R.layout.widget_glass_square)
                v.setImageViewBitmap(R.id.img, render(context, wDp - 8f, hDp - 8f, hj.day.toString(), month, greg))
                Widgets.open(context, v)
                mgr.updateAppWidget(id, v)
            }
        }

        private fun render(context: Context, wDp: Float, hDp: Float, day: String, month: String, greg: String): Bitmap {
            val sz = GlassDraw.size(context, wDp, hDp)
            val k = sz.k
            val w = sz.w.toFloat()
            val h = sz.h.toFloat()
            val bmp = Bitmap.createBitmap(sz.w, sz.h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val dayP = GlassDraw.paint(context, true, true, h * 0.75f, 0x59FFFFFF)
            GlassDraw.fit(dayP, day, w * 0.9f)
            GlassDraw.drawCentered(c, day, w / 2f, h * 0.46f, dayP)
            val monthP = GlassDraw.paint(context, true, true, h * 0.2f, GlassDraw.WHITE, k)
            GlassDraw.fit(monthP, month, w * 0.88f)
            GlassDraw.drawCentered(c, month, w / 2f, h * 0.46f, monthP)
            val gregP = GlassDraw.paint(context, false, false, h * 0.085f, GlassDraw.W75)
            GlassDraw.fit(gregP, greg, w * 0.88f)
            GlassDraw.drawCentered(c, greg, w / 2f, h * 0.86f, gregP)
            return bmp
        }
    }
}

class GlassDayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        update(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val s = SettingsStore.get(context).value
            val date = LocalDate.now(s.place.zone)
            val hj = Calendars.toHijri(date, s.hijriMethod, s.hijriAdjust)
            val sub = "${hj.day} ${Calendars.hijriMonths[(hj.month - 1).coerceIn(0, 11)]}"
            val name = Calendars.weekDay(date)
            for (id in ids) {
                val (wDp, hDp) = GlassDraw.optionsDp(mgr, id, 160f, 160f)
                val v = RemoteViews(context.packageName, R.layout.widget_glass_square)
                v.setImageViewBitmap(R.id.img, render(context, wDp - 8f, hDp - 8f, name, sub))
                Widgets.open(context, v)
                mgr.updateAppWidget(id, v)
            }
        }

        private fun render(context: Context, wDp: Float, hDp: Float, name: String, sub: String): Bitmap {
            val sz = GlassDraw.size(context, wDp, hDp)
            val k = sz.k
            val w = sz.w.toFloat()
            val h = sz.h.toFloat()
            val bmp = Bitmap.createBitmap(sz.w, sz.h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val nameP = GlassDraw.paint(context, true, true, min(h * 0.34f, w * 0.34f), GlassDraw.WHITE, k)
            GlassDraw.fit(nameP, name, w * 0.88f)
            GlassDraw.drawCentered(c, name, w / 2f, h * 0.46f, nameP)
            val subP = GlassDraw.paint(context, false, false, h * 0.09f, GlassDraw.W75)
            GlassDraw.fit(subP, sub, w * 0.88f)
            GlassDraw.drawCentered(c, sub, w / 2f, h * 0.74f, subP)
            return bmp
        }
    }
}
