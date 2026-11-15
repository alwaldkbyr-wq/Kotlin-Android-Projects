package com.mawaqeet.app.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.DayNight
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerDay
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * «قرص السماء»: دائرة الأربع والعشرين ساعة لليوم الحالي.
 * منتصف الليل في الأسفل والظهر في الأعلى، والشروق يساراً (الشرق) والغروب يميناً (الغرب).
 * القوس الذهبي هو النهار، والأقواس المتدرجة الشفق، والنقاط مواقيت الصلاة، والكرة المضيئة هي الآن.
 */
@Composable
fun SkyDial(
    now: Long,
    dayStart: Long,
    day: DayNight?,
    prayers: PrayerDay?,
    next: Pair<Prayer, Long>?,
    moonIllum: Double,
    moonWaxing: Boolean,
    modifier: Modifier = Modifier
) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val night = Color(0xFF2B2F7A)
    val dayCol = Color(0xFFFFC46B)
    val twilight = Color(0xFF7C6BD6)
    Box(modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val dp = density
            val c = center
            val r = size.minDimension / 2f - 26f * dp
            val track = 13f * dp
            fun ang(t: Long): Float = 90f + ((t - dayStart).toFloat() / DAY_MS) * 360f
            fun pt(a: Float, rr: Float) = Offset(c.x + rr * cos(Math.toRadians(a.toDouble())).toFloat(), c.y + rr * sin(Math.toRadians(a.toDouble())).toFloat())
            val box = Size(r * 2, r * 2)
            val tl = Offset(c.x - r, c.y - r)
            fun arc(t1: Long?, t2: Long?, color: Color, w: Float = track) {
                if (t1 == null || t2 == null) return
                val a1 = ang(t1); var sw = ang(t2) - a1
                if (sw < 0) sw += 360f
                drawArc(color, a1, sw, false, tl, box, style = Stroke(w, cap = StrokeCap.Butt))
            }
            // الليل كاملاً ثم الشفق ثم النهار فوقه
            drawCircle(night.copy(alpha = 0.55f), r, c, style = Stroke(track))
            if (day != null) {
                if (day.polarDay) drawCircle(dayCol, r, c, style = Stroke(track))
                arc(day.astroDawn, day.sunrise, twilight.copy(alpha = 0.45f))
                arc(day.nauticalDawn, day.sunrise, twilight.copy(alpha = 0.55f))
                arc(day.civilDawn, day.sunrise, Color(0xFFE38BB0).copy(alpha = 0.6f))
                arc(day.sunset, day.astroDusk, twilight.copy(alpha = 0.45f))
                arc(day.sunset, day.nauticalDusk, twilight.copy(alpha = 0.55f))
                arc(day.sunset, day.civilDusk, Color(0xFFE38BB0).copy(alpha = 0.6f))
                if (day.sunrise != null && day.sunset != null) {
                    val a1 = ang(day.sunrise); var sw = ang(day.sunset) - a1; if (sw < 0) sw += 360f
                    drawArc(
                        Brush.sweepGradient(listOf(dayCol.copy(alpha = 0.7f), dayCol, Color(0xFFFFE7A8), dayCol, dayCol.copy(alpha = 0.7f)), c),
                        a1, sw, false, tl, box, style = Stroke(track, cap = StrokeCap.Round)
                    )
                }
            }
            // حلقة داخلية رفيعة وعلامات الساعات
            drawCircle(o.muted.copy(alpha = 0.25f), r - track * 1.3f, c, style = Stroke(1f * dp))
            val paint = Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text; isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 10f * dp; color = o.muted.toArgb() }
            for (h in 0 until 24) {
                val a = 90f + h * 15f
                val major = h % 6 == 0
                drawLine(o.muted.copy(alpha = if (major) 0.7f else 0.35f), pt(a, r - track * 1.3f), pt(a, r - track * (if (major) 2.0f else 1.65f)), (if (major) 1.6f else 1f) * dp)
                if (major) {
                    val p = pt(a, r - track * 2.75f)
                    drawContext.canvas.nativeCanvas.drawText("%02d".format(java.util.Locale.US, h), p.x, p.y + 3.5f * dp, paint)
                }
            }
            // مواقيت الصلاة على الحلقة
            if (prayers != null) {
                val lp = Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text; isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 10.5f * dp }
                Prayer.values().forEach { p ->
                    val t = prayers[p] ?: return@forEach
                    val a = ang(t)
                    val isNext = next?.first == p && next.second == t
                    val col = if (isNext) primary else o.ink
                    drawCircle(Color.Black.copy(alpha = 0.35f), 5.5f * dp, pt(a, r))
                    drawCircle(col, (if (isNext) 4.5f else 3.2f) * dp, pt(a, r))
                    val lpnt = pt(a, r + track * 1.35f)
                    lp.color = col.toArgb(); lp.isFakeBoldText = isNext
                    drawContext.canvas.nativeCanvas.drawText(p.ar, lpnt.x, lpnt.y + 4f * dp, lp)
                }
            }
            // الآن: كرة مضيئة (شمس نهاراً، وقمر ليلاً) وعقرب خافت
            val an = ang(now)
            val isDay = day?.let { d -> d.polarDay || (d.sunrise != null && d.sunset != null && now in d.sunrise..d.sunset) } ?: false
            val pNow = pt(an, r)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, primary.copy(alpha = 0.5f)), c, pNow), c, pNow, 1.2f * dp)
            val glow = if (isDay) dayCol else Color(0xFFDDE6FF)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), Color.Transparent), pNow, track * 1.6f), track * 1.6f, pNow)
            drawCircle(glow, track * 0.55f, pNow)
            if (!isDay) dialMoon(pNow, track * 0.55f, moonIllum, moonWaxing)
        }
        // المركز: الساعة والصلاة القادمة
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(env.time(now), style = MaterialTheme.typography.displaySmall, color = o.ink, fontWeight = FontWeight.Black)
            Text(":" + "%02d".format(java.util.Locale.US, (now / 1000) % 60), color = o.muted, fontSize = 13.sp)
            if (next != null) {
                Text(next.first.ar, style = MaterialTheme.typography.titleMedium, color = primary, modifier = Modifier.padding(top = 6.dp))
                Text("بعد " + Fmt.countdown(next.second - now), style = MaterialTheme.typography.labelLarge, color = o.ink, textAlign = TextAlign.Center)
            }
        }
    }
}

/** ظل القمر على كرة «الآن» ليلاً */
private fun DrawScope.dialMoon(c: Offset, r: Float, illum: Double, waxing: Boolean) {
    // جزء مظلم يغطي ما لا يضيئه الطور
    val shift = (r * 2f * illum).toFloat()
    val dx = if (waxing) -shift else shift
    clipPath(Path().apply { addOval(Rect(c, r)) }) {
        drawCircle(Color(0xFF1B2050).copy(alpha = 0.85f), r * 0.98f, Offset(c.x + dx, c.y))
    }
}
