package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.widthIn
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.OsmMap
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenScaffold
import androidx.compose.foundation.layout.navigationBarsPadding
import com.mawaqeet.app.ui.components.glass
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalDark
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.roundToInt

/* ───────── أدوات رسم مشتركة بين شاشات الخرائط ───────── */

/** علامة دائرية صغيرة على الخريطة */
internal fun mapDotMarker(map: MapView, lat: Double, lon: Double, title: String, color: Int, sizeDp: Int = 16, snippet: String? = null): Marker {
    val d = map.resources.displayMetrics.density
    val px = (sizeDp * d).toInt().coerceAtLeast(4)
    val dr = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((2 * d).toInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
        setSize(px, px)
    }
    val m = Marker(map)
    m.position = GeoPoint(lat, lon)
    m.title = title
    if (snippet != null) m.snippet = snippet
    m.icon = dr
    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    return m
}

/** تسمية نصية على الخريطة (بلا نافذة معلومات) */
internal fun mapLabelMarker(map: MapView, lat: Double, lon: Double, text: String, color: Int, sizeSp: Float = 11f): Marker {
    val d = map.resources.displayMetrics.density
    val paint = Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text;
        isAntiAlias = true; textSize = sizeSp * d; this.color = color; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(3f * d, 0f, 0f, 0xFFFFFFFF.toInt())
    }
    val w = (paint.measureText(text) + 12 * d).toInt().coerceAtLeast(8)
    val h = (paint.textSize * 1.6f).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    AndroidCanvas(bmp).drawText(text, w / 2f, h * 0.7f, paint)
    val m = Marker(map)
    m.position = GeoPoint(lat, lon)
    m.icon = BitmapDrawable(map.resources, bmp)
    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    m.setInfoWindow(null)
    return m
}

private class DayNightSnap(
    val timeMs: Long,
    val sun: MapMath.SubPoint,
    val moon: MapMath.SubPoint,
    val layers: List<List<List<Pair<Double, Double>>>>
)

/** خريطة الليل والنهار: خط الظل (Terminator) والشفق المدني والبحري والفلكي لأي لحظة */
@Composable
fun DayNightMapScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    var offsetMin by remember { mutableFloatStateOf(0f) }
    val shift = (offsetMin / 5f).roundToInt() * 5
    val timeMs = (env.now / 60_000L) * 60_000L + shift * 60_000L

    val snap = remember(timeMs) {
        val sun = MapMath.subSolar(timeMs)
        DayNightSnap(timeMs, sun, MapMath.subLunar(timeMs), emptyList())
    }
    val key = remember(snap, env.lat, env.lon) { Any() }
    val applied = remember { arrayOfNulls<Any>(1) }

    val alt = Ephemeris.horizontal(Body.SUN, timeMs, env.lat, env.lon, false).alt
    val (state, stateColor) = when {
        alt > -0.833 -> "نهار" to o.warning
        alt > -6 -> "شفق مدني" to o.gold
        alt > -12 -> "شفق بحري" to o.info
        alt > -18 -> "شفق فلكي" to MaterialTheme.colorScheme.primary
        else -> "ليل" to o.muted
    }

    ScreenScaffold(title = "الليل والنهار", onBack = env.back, subtitle = "خط الظل والشفق على الخريطة", immersive = true) {
        Box(Modifier.fillMaxSize()) {
            OsmMap(Modifier.fillMaxSize(), 20.0, env.lon, 2.0, LocalDark.current) { map ->
                if (applied[0] !== key) {
                    applied[0] = key
                    map.overlays.clear()
                    map.overlays.add(NightShadeOverlay(snap.sun))
                    map.overlays.add(mapDotMarker(map, snap.moon.lat, snap.moon.lon, "نقطة القمر العمودية", 0xFF90A4AE.toInt(), 16))
                    map.overlays.add(mapDotMarker(map, snap.sun.lat, snap.sun.lon, "الشمس عمودية هنا", 0xFFFFB300.toInt(), 20, Fmt.latLon(snap.sun.lat, snap.sun.lon)))
                    map.overlays.add(mapDotMarker(map, env.lat, env.lon, "موقعك", 0xFF1E88E5.toInt(), 16))
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().navigationBarsPadding().padding(12.dp)
                    .glass(RoundedCornerShape(26.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(env.time(timeMs), color = o.ink, style = MaterialTheme.typography.headlineSmall)
                        Text(env.date(timeMs), color = o.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Pill(state, stateColor)
                    Text("  ${Fmt.deg(alt)}", color = o.ink, style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = offsetMin, onValueChange = { offsetMin = it },
                    valueRange = -1440f..1440f, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (shift == 0) "الآن" else "${if (shift > 0) "+" else "−"}${Fmt.span(kotlin.math.abs(shift) * 60_000L)}",
                        Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.labelLarge
                    )
                    GlassButton(onClick = { offsetMin = 0f }, enabled = shift != 0, nav = true) { Text("الآن") }
                }
                Text(
                    "الخط الذهبي: حدّ الغروب والشروق • الطبقات الأغمق: شفق مدني ثم بحري ثم فلكي.",
                    color = o.muted, fontSize = 11.sp, textAlign = TextAlign.Start, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


/**
 * تظليل الليل والشفق مرسوماً مباشرة على الشاشة: لكل عمود من البكسلات يُحسب مجال خطوط العرض
 * التي يكون فيها ارتفاع الشمس أقل من كل حدّ، فيبقى صحيحاً مع تكرار الخريطة أفقياً وعند القطبين.
 */
private class NightShadeOverlay(private val sub: MapMath.SubPoint) : org.osmdroid.views.overlay.Overlay() {
    private val levels = MapMath.LEVELS
    private val fill = android.graphics.Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text; style = android.graphics.Paint.Style.FILL; color = 0x2E00061A }
    private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text;
        style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f; color = 0xCCFFC107.toInt()
    }
    private val pt = android.graphics.Point()

    override fun draw(c: android.graphics.Canvas, mv: org.osmdroid.views.MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mv.projection
        val w = mv.width; val h = mv.height
        if (w <= 0 || h <= 0) return
        val step = 3
        fun yOf(lat: Double): Float {
            pj.toPixels(org.osmdroid.util.GeoPoint(lat.coerceIn(-84.9, 84.9), 0.0), pt)
            return pt.y.toFloat()
        }
        val sinD = com.mawaqeet.app.astro.sind(sub.lat); val cosD = com.mawaqeet.app.astro.cosd(sub.lat)
        var prevUp = Float.NaN; var prevDown = Float.NaN
        var x = 0
        while (x < w) {
            val lon = pj.fromPixels(x + step / 2, h / 2).longitude
            val cosH = com.mawaqeet.app.astro.cosd(lon - sub.lon)
            // ارتفاع الشمس على هذا الخط: R·cos(φ − φ0) ، والظلام حيث R·cos(φ − φ0) < sin(h)
            val a = sinD; val b = cosD * cosH
            val r = kotlin.math.hypot(a, b)
            val phi0 = com.mawaqeet.app.astro.atan2d(a, b)
            levels.forEachIndexed { i, lv ->
                val k = com.mawaqeet.app.astro.sind(lv) / r
                when {
                    k >= 1.0 -> c.drawRect(x.toFloat(), 0f, (x + step).toFloat(), h.toFloat(), fill)
                    k > -1.0 -> {
                        val d = com.mawaqeet.app.astro.acosd(k)
                        val top = phi0 + d      // مظلم شمال هذا الخط
                        val bottom = phi0 - d   // ومظلم جنوب هذا
                        if (top < 90.0) c.drawRect(x.toFloat(), 0f, (x + step).toFloat(), yOf(top), fill)
                        if (bottom > -90.0) c.drawRect(x.toFloat(), yOf(bottom), (x + step).toFloat(), h.toFloat(), fill)
                        if (i == 0) {
                            val yu = if (top < 90.0) yOf(top) else Float.NaN
                            val yd = if (bottom > -90.0) yOf(bottom) else Float.NaN
                            if (!yu.isNaN() && !prevUp.isNaN() && kotlin.math.abs(yu - prevUp) < h / 4) c.drawLine((x - step).toFloat(), prevUp, x.toFloat(), yu, line)
                            if (!yd.isNaN() && !prevDown.isNaN() && kotlin.math.abs(yd - prevDown) < h / 4) c.drawLine((x - step).toFloat(), prevDown, x.toFloat(), yd, line)
                            prevUp = yu; prevDown = yd
                        }
                    }
                    else -> if (i == 0) { prevUp = Float.NaN; prevDown = Float.NaN }
                }
            }
            x += step
        }
    }
}
