package com.mawaqeet.app.ui.screens

import android.hardware.GeomagneticField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OsmMap
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.MapSheet
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalDark
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAG_STEP = 5
private const val MAG_BUCKET = 4.0
private val magLevels = listOf(-30, -20, -10, 0, 10, 20, 30)

private class MagCell(val south: Double, val north: Double, val west: Double, val east: Double, val bucket: Int)
private class MagLine(val level: Int, val pts: List<Pair<Double, Double>>)
private class MagData(val cells: List<MagCell>, val lines: List<MagLine>)

/** لون الخلية حسب فئة الانحراف: شرق دافئ، غرب بارد، وقرب الصفر رمادي */
private fun magColorArgb(bucket: Int): Int {
    val d = abs(bucket * MAG_BUCKET)
    if (bucket == 0) return 0x1E9E9E9E
    val alpha = (0x26 + 0x58 * (d / 32.0).coerceAtMost(1.0)).roundToInt()
    val rgb = if (bucket > 0) 0xE64A19 else 0x1976D2
    return (alpha shl 24) or rgb
}

private fun magCompute(time: Long): MagData {
    val lats = DoubleArray((80 - (-70)) / MAG_STEP + 1) { -70.0 + MAG_STEP * it }
    val lons = DoubleArray(360 / MAG_STEP + 1) { -180.0 + MAG_STEP * it }
    val grid = Array(lats.size) { i ->
        DoubleArray(lons.size) { j ->
            GeomagneticField(lats[i].toFloat(), lons[j].toFloat(), 0f, time).declination.toDouble()
        }
    }
    // خلايا ملوّنة (دمج الخلايا المتجاورة أفقياً ذات الفئة نفسها)
    val cells = ArrayList<MagCell>()
    val half = MAG_STEP / 2.0
    for (i in lats.indices) {
        var j = 0
        while (j < lons.size - 1) {
            val b = (grid[i][j] / MAG_BUCKET).roundToInt().coerceIn(-8, 8)
            var k = j
            while (k + 1 < lons.size - 1 && (grid[i][k + 1] / MAG_BUCKET).roundToInt().coerceIn(-8, 8) == b) k++
            cells.add(
                MagCell(
                    (lats[i] - half).coerceAtLeast(-MapMath.MAX_LAT), (lats[i] + half).coerceAtMost(MapMath.MAX_LAT),
                    lons[j] - half, (lons[k] + half).coerceAtMost(180.0), b
                )
            )
            j = k + 1
        }
    }
    val lines = ArrayList<MagLine>()
    for (lv in magLevels) for (l in MapMath.isolines(grid, lats, lons, lv.toDouble())) lines.add(MagLine(lv, l))
    return MagData(cells, lines)
}

/** خريطة المجال المغناطيسي للأرض (نموذج WMM): الانحراف المغناطيسي وخطوط تساويه */
@Composable
fun MagneticMapScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val data = rememberComputed(0) { magCompute(System.currentTimeMillis()) }
    val field = remember(env.lat, env.lon, env.today) {
        GeomagneticField(env.lat.toFloat(), env.lon.toFloat(), 0f, System.currentTimeMillis())
    }
    val dec = field.declination.toDouble()
    val dip = field.inclination.toDouble()
    val total = field.fieldStrength.toDouble()
    val horiz = field.horizontalStrength.toDouble()
    val vert = total * kotlin.math.sin(Math.toRadians(dip))
    val decText = "${Fmt.deg(abs(dec))} ${if (dec >= 0) "شرقاً" else "غرباً"}"

    val key = remember(data, env.lat, env.lon) { Any() }
    val applied = remember { arrayOfNulls<Any>(1) }

    ScreenScaffold(
        title = "المجال المغناطيسي", immersive = true, onBack = env.back, subtitle = "الانحراف المغناطيسي (WMM)",
        actions = {
            ShareAction {
                "المجال المغناطيسي عند ${Fmt.latLon(env.lat, env.lon)}\nالانحراف: $decText\nالميل: ${Fmt.deg(dip)}\n" +
                    "الشدة الكلية: ${Fmt.num(total, 0)} nT\nالمركبة الأفقية: ${Fmt.num(horiz, 0)} nT"
            }
        }
    ) {
        MapSheet(mapContent = {
            Box(Modifier.fillMaxSize()) {
                OsmMap(Modifier.fillMaxSize(), 25.0, env.lon, 1.8, LocalDark.current) { map ->
                    val d = data
                    if (d != null && applied[0] !== key) {
                        applied[0] = key
                        map.overlays.clear()
                        d.cells.forEach { c ->
                            val pg = Polygon()
                            pg.points = listOf(
                                GeoPoint(c.north, c.west), GeoPoint(c.north, c.east),
                                GeoPoint(c.south, c.east), GeoPoint(c.south, c.west)
                            )
                            pg.fillPaint.color = magColorArgb(c.bucket)
                            pg.outlinePaint.color = 0x00000000
                            pg.outlinePaint.strokeWidth = 0f
                            pg.setInfoWindow(null)
                            map.overlays.add(pg)
                        }
                        d.lines.forEach { l ->
                            val pl = Polyline()
                            pl.setPoints(l.pts.map { GeoPoint(it.first, it.second) })
                            pl.outlinePaint.color = when {
                                l.level == 0 -> 0xFF2E7D32.toInt()
                                l.level > 0 -> 0xCCE64A19.toInt()
                                else -> 0xCC1976D2.toInt()
                            }
                            pl.outlinePaint.strokeWidth = if (l.level == 0) 6f else 2.5f
                            pl.setInfoWindow(null)
                            map.overlays.add(pl)
                        }
                        d.lines.filter { it.pts.size >= 14 }.forEach { l ->
                            val m = l.pts[l.pts.size / 2]
                            if (abs(m.first) < 75) {
                                val t = if (l.level == 0) "0°" else if (l.level > 0) "${l.level}°E" else "${-l.level}°W"
                                map.overlays.add(mapLabelMarker(map, m.first, m.second, t, 0xFF1B2A4A.toInt(), 10f))
                            }
                        }
                        map.overlays.add(mapDotMarker(map, env.lat, env.lon, "موقعك", 0xFF1E88E5.toInt(), 16, decText))
                    }
                }
                if (data == null) Busy(Modifier.align(Alignment.Center))
            }
            }) {
                item {
                    GlassCard(
                        title = "عند موقعك", subtitle = Fmt.latLon(env.lat, env.lon),
                        trailing = { Pill(if (dec >= 0) "انحراف شرقي" else "انحراف غربي", if (dec >= 0) o.danger else o.info) }
                    ) {
                        MetricRow("الانحراف" to Fmt.deg(abs(dec)), "الميل (Dip)" to Fmt.deg(dip), "الشدة الكلية" to "${Fmt.num(total, 0)} nT")
                        InfoRow("الانحراف المغناطيسي", decText, emphasize = true)
                        InfoRow("الميل المغناطيسي", "${Fmt.deg(abs(dip))} ${if (dip >= 0) "للأسفل" else "للأعلى"}")
                        InfoRow("الشدة الكلية", "${Fmt.num(total, 0)} nT (${Fmt.num(total / 1000.0, 1)} µT)")
                        InfoRow("المركبة الأفقية", "${Fmt.num(horiz, 0)} nT")
                        InfoRow("المركبة الرأسية", "${Fmt.num(vert, 0)} nT")
                    }
                }
                item {
                    GlassCard(title = "دليل الألوان") {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                            for (b in -7..7) Box(Modifier.weight(1f).height(16.dp).background(Color(magColorArgb(b)).copy(alpha = 0.9f)))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Text("غرب 28°", color = o.info, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("صفر", color = o.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text("شرق 28°", color = o.danger, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("الخط الأخضر السميك: الانحراف صفر (agonic) • الخطوط الحمراء: شرقي • الزرقاء: غربي (كل 10°).", color = o.muted, fontSize = 12.sp)
                    }
                }
                item {
                    Note("المصدر: نموذج المجال المغناطيسي العالمي WMM المدمج في أندرويد (ارتفاع سطح البحر). إبرة البوصلة العادية تشير إلى الشمال المغناطيسي لا الجغرافي: الاتجاه الجغرافي = الاتجاه المغناطيسي + الانحراف (الشرقي موجب).")
                }
            }
    }
}
