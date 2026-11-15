package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Qibla
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.OsmMap
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.MapSheet
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.magneticDeclination
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalDark
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

/** يقسم مساراً على خط التاريخ (±180) إلى أجزاء حتى لا يُرسم خط أفقي عبر الخريطة */
private fun qiblaSplit(path: List<Pair<Double, Double>>): List<List<Pair<Double, Double>>> {
    val out = ArrayList<List<Pair<Double, Double>>>()
    var cur = ArrayList<Pair<Double, Double>>()
    for (i in path.indices) {
        val p = path[i]
        if (i > 0) {
            val q = path[i - 1]
            if (abs(p.second - q.second) > 180.0) {
                val edge = if (q.second > 0) 180.0 else -180.0
                val pl = if (p.second < 0) p.second + 360.0 else p.second - 360.0
                val t = (edge - q.second) / (pl - q.second)
                val lat = q.first + t * (p.first - q.first)
                cur.add(lat to edge)
                out.add(cur)
                cur = ArrayList()
                cur.add(lat to -edge)
            }
        }
        cur.add(p)
    }
    out.add(cur)
    return out.filter { it.size >= 2 }
}

/** القبلة على الخريطة: خط الدائرة العظمى من موقعك إلى الكعبة */
@Composable
fun QiblaMapScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val lat = env.lat
    val lon = env.lon
    val bearing = Qibla.bearing(lat, lon)
    val dist = Qibla.distanceKm(lat, lon)
    val decl = remember(lat, lon) { magneticDeclination(lat, lon) }
    val magBearing = norm360(bearing - decl)
    val path = remember(lat, lon) { Qibla.greatCircle(lat, lon, n = 96) }
    val mid = path[path.size / 2]
    val zoom = when {
        dist < 300 -> 8.0
        dist < 1500 -> 5.5
        dist < 5000 -> 3.8
        else -> 2.6
    }
    val sunDates = remember(env.today.year) {
        val y = env.today.year
        Qibla.sunAtKaabaZenith(y) + Qibla.sunAtKaabaZenith(y + 1)
    }
    val upcoming = sunDates.filter { it >= env.now - 6 * 3_600_000L }.take(2)

    val key = remember(lat, lon) { Any() }
    val applied = remember { arrayOfNulls<Any>(1) }
    val gold = 0xFFD4A017.toInt()

    ScreenScaffold(
        title = "القبلة على الخريطة", immersive = true, onBack = env.back, subtitle = "أقصر مسار إلى الكعبة المشرّفة",
        actions = {
            ShareAction {
                "اتجاه القبلة من ${Fmt.latLon(lat, lon)}: ${Fmt.deg(bearing)} (${Fmt.direction(bearing)})\nالمسافة إلى الكعبة: ${Fmt.num(dist, 0)} كم"
            }
        }
    ) {
        MapSheet(mapContent = {
            OsmMap(Modifier.fillMaxSize(), mid.first, mid.second, zoom, LocalDark.current) { map ->
                if (applied[0] !== key) {
                    applied[0] = key
                    map.overlays.clear()
                    qiblaSplit(path).forEach { seg ->
                        val pl = Polyline()
                        pl.setPoints(seg.map { GeoPoint(it.first, it.second) })
                        pl.outlinePaint.color = gold
                        pl.outlinePaint.strokeWidth = 9f
                        pl.setInfoWindow(null)
                        map.overlays.add(pl)
                    }
                    map.overlays.add(mapDotMarker(map, Qibla.KAABA_LAT, Qibla.KAABA_LON, "الكعبة المشرّفة", 0xFF111111.toInt(), 20, "21.4225° ش  39.8262° ق"))
                    map.overlays.add(mapDotMarker(map, lat, lon, "موقعك", 0xFF1E88E5.toInt(), 18, Fmt.latLon(lat, lon)))
                }
            }
            }) {
                item {
                    GlassCard(title = "اتجاه القبلة") {
                        MetricRow(
                            "من الشمال الحقيقي" to Fmt.deg(bearing),
                            "الاتجاه" to Fmt.direction(bearing),
                            "المسافة" to "${Fmt.num(dist, 0)} كم"
                        )
                        InfoRow("بالنسبة للبوصلة العادية (مغناطيسي)", Fmt.deg(magBearing), emphasize = true)
                        InfoRow("الانحراف المغناطيسي هنا", "${Fmt.deg(abs(decl))} ${if (decl >= 0) "شرقاً" else "غرباً"}")
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            GlassButton(onClick = { env.navigate(Routes.COMPASS_2D) }, nav = true) {
                                Icon(Icons.Filled.Explore, null)
                                Text("  افتح البوصلة")
                            }
                        }
                    }
                }
                item {
                    GlassCard(title = "تعامد الشمس على الكعبة", subtitle = "للتحقق من القبلة بالظل") {
                        if (upcoming.isEmpty()) Text("--", color = o.muted)
                        upcoming.forEach { InfoRow(env.date(it), env.time(it)) }
                        Text(
                            "في هذه اللحظة تكون الشمس فوق الكعبة تماماً، فتشير ظلال الأجسام الرأسية في الاتجاه المعاكس للقبلة (القبلة هي ناحية الشمس).",
                            color = o.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                item { Note("الخط الذهبي هو أقصر مسار (دائرة عظمى) وقد يبدو منحنياً على الخريطة المسطّحة.") }
            }
    }
}
