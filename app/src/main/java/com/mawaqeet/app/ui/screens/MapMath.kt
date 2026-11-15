package com.mawaqeet.app.ui.screens

import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.asind
import com.mawaqeet.app.astro.atan2d
import com.mawaqeet.app.astro.acosd
import com.mawaqeet.app.astro.cosd
import com.mawaqeet.app.astro.gast
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.norm180
import com.mawaqeet.app.astro.sind
import kotlin.math.abs
import kotlin.math.hypot

/**
 * رياضيات خرائط الليل والنهار (Kotlin صرف بلا أندرويد).
 * المناطق المظلمة هي {ارتفاع الشمس < h} وهي «قلنسوة» كروية مركزها نقطة مقابلة للنقطة تحت الشمس مباشرة
 * ونصف قطرها 90 + h درجة.
 */
object MapMath {
    /** حدّ العرض المستخدم للرسم على خريطة ميركاتور */
    const val MAX_LAT = 85.0

    /** ارتفاعات حدود الليل والشفق (درجات): غروب، مدني، بحري، فلكي */
    val LEVELS = doubleArrayOf(-0.833, -6.0, -12.0, -18.0)

    class SubPoint(val lat: Double, val lon: Double)

    /** النقطة التي يكون الجرم عندها في السمت (عمودياً) لحظة [millis]: خط العرض = الميل، خط الطول = RA − GAST */
    fun subPoint(body: Body, millis: Long): SubPoint {
        val jd = jdOf(millis)
        val eq = Ephemeris.equatorial(body, jdeOf(jd))
        return SubPoint(eq.dec, norm180(eq.ra - gast(jd)))
    }

    fun subSolar(millis: Long) = subPoint(Body.SUN, millis)
    fun subLunar(millis: Long) = subPoint(Body.MOON, millis)

    /** الارتفاع الهندسي للجرم (درجات) في موقع معيّن نسبةً إلى نقطته السمتية */
    fun altitude(lat: Double, lon: Double, sub: SubPoint): Double =
        asind(sind(lat) * sind(sub.lat) + cosd(lat) * cosd(sub.lat) * cosd(lon - sub.lon))

    /** هل تحتوي المنطقة {الارتفاع < h} أحد القطبين؟ */
    fun containsPole(sub: SubPoint, h: Double) = abs(sub.lat) > -h

    /** +1 إن كان القطب الشمالي هو المظلم، −1 للجنوبي */
    fun darkPoleSign(sub: SubPoint) = if (sub.lat > 0) -1 else 1

    /**
     * خط العرض الذي يكون عنده ارتفاع الشمس = h عند خط الطول [lon]، في الجهة المتجهة إلى القطب المظلم
     * (صالح فقط حين [containsPole]).
     */
    fun boundaryLat(lon: Double, sub: SubPoint, h: Double): Double {
        val s = darkPoleSign(sub)
        val aLat = -sub.lat
        val aLon = sub.lon + 180.0
        val r = 90.0 + h
        val a = sind(aLat)
        val b = cosd(aLat) * cosd(lon - aLon)
        val rr = hypot(a, b)
        if (rr < 1e-12) return s * 90.0
        val d = acosd((cosd(r) / rr).coerceIn(-1.0, 1.0))
        val th = atan2d(a, b)
        val v = if (s > 0) th - d else th + d
        return v.coerceIn(-90.0, 90.0)
    }

    private fun clampLat(v: Double) = v.coerceIn(-MAX_LAT, MAX_LAT)

    /** مضلعات (خط عرض، خط طول) داخل [-180,180] تغطي المنطقة {ارتفاع الشمس < h} */
    fun darkPolygons(sub: SubPoint, h: Double, lonStep: Double = 2.0): List<List<Pair<Double, Double>>> {
        if (containsPole(sub, h)) {
            val s = darkPoleSign(sub)
            val pts = ArrayList<Pair<Double, Double>>()
            var lon = -180.0
            while (lon <= 180.0 + 1e-9) {
                pts.add(clampLat(boundaryLat(lon.coerceAtMost(180.0), sub, h)) to lon.coerceAtMost(180.0))
                lon += lonStep
            }
            pts.add(s * MAX_LAT to 180.0)
            pts.add(s * MAX_LAT to -180.0)
            return listOf(pts)
        }
        // قلنسوة لا تحوي قطباً: دائرة صغيرة حول النقطة المقابلة للشمس
        val aLat = -sub.lat
        val aLon = norm180(sub.lon + 180.0)
        val r = 90.0 + h
        val ring = ArrayList<Pair<Double, Double>>()
        var prev = Double.NaN
        var az = 0.0
        while (az < 360.0) {
            val lat2 = asind(sind(aLat) * cosd(r) + cosd(aLat) * sind(r) * cosd(az))
            var lon2 = aLon + atan2d(sind(az) * sind(r) * cosd(aLat), cosd(r) - sind(aLat) * sind(lat2))
            lon2 = if (prev.isNaN()) norm180(lon2) else prev + norm180(lon2 - prev)
            prev = lon2
            ring.add(lat2 to lon2)
            az += 3.0
        }
        val out = ArrayList<List<Pair<Double, Double>>>()
        for (shift in doubleArrayOf(-360.0, 0.0, 360.0)) {
            var p: List<Pair<Double, Double>> = ring.map { clampLat(it.first) to (it.second + shift) }
            p = clip(p, -180.0, true)
            p = clip(p, 180.0, false)
            if (p.size >= 3) out.add(p)
        }
        return out
    }

    /** قصّ مضلع على نصف مستوى خط طول (Sutherland–Hodgman) */
    private fun clip(poly: List<Pair<Double, Double>>, bound: Double, keepGreater: Boolean): List<Pair<Double, Double>> {
        if (poly.isEmpty()) return poly
        fun inside(p: Pair<Double, Double>) = if (keepGreater) p.second >= bound else p.second <= bound
        val out = ArrayList<Pair<Double, Double>>()
        for (i in poly.indices) {
            val cur = poly[i]
            val nxt = poly[(i + 1) % poly.size]
            val ci = inside(cur)
            val ni = inside(nxt)
            if (ci) out.add(cur)
            if (ci != ni) {
                val t = (bound - cur.second) / (nxt.second - cur.second)
                out.add((cur.first + t * (nxt.first - cur.first)) to bound)
            }
        }
        return out
    }

    /**
     * خطوط تساوي القيمة [level] على شبكة (marching squares) ثم وصل المقاطع في خطوط متصلة.
     * [grid][i][j] = القيمة عند (lats[i], lons[j]). تُعاد قوائم نقاط (خط عرض، خط طول).
     */
    fun isolines(grid: Array<DoubleArray>, lats: DoubleArray, lons: DoubleArray, level: Double): List<List<Pair<Double, Double>>> {
        val h = lats.size
        val w = lons.size
        val pos = HashMap<Int, Pair<Double, Double>>()
        val adj = HashMap<Int, ArrayList<Int>>()
        fun hId(i: Int, j: Int) = (i * w + j) * 2
        fun vId(i: Int, j: Int) = (i * w + j) * 2 + 1
        fun hPoint(i: Int, j: Int): Int {
            val id = hId(i, j)
            if (!pos.containsKey(id)) {
                val a = grid[i][j]; val b = grid[i][j + 1]
                val t = (level - a) / (b - a)
                pos[id] = lats[i] to (lons[j] + t * (lons[j + 1] - lons[j]))
            }
            return id
        }
        fun vPoint(i: Int, j: Int): Int {
            val id = vId(i, j)
            if (!pos.containsKey(id)) {
                val a = grid[i][j]; val b = grid[i + 1][j]
                val t = (level - a) / (b - a)
                pos[id] = (lats[i] + t * (lats[i + 1] - lats[i])) to lons[j]
            }
            return id
        }
        fun link(a: Int, b: Int) {
            adj.getOrPut(a) { ArrayList() }.add(b)
            adj.getOrPut(b) { ArrayList() }.add(a)
        }
        for (i in 0 until h - 1) for (j in 0 until w - 1) {
            val a = grid[i][j] >= level; val b = grid[i][j + 1] >= level
            val c = grid[i + 1][j + 1] >= level; val d = grid[i + 1][j] >= level
            val ids = ArrayList<Int>(4) // بترتيب: أعلى، يمين، أسفل، يسار
            if (a != b) ids.add(hPoint(i, j))
            if (b != c) ids.add(vPoint(i, j + 1))
            if (d != c) ids.add(hPoint(i + 1, j))
            if (a != d) ids.add(vPoint(i, j))
            if (ids.size == 2) link(ids[0], ids[1])
            else if (ids.size == 4) { link(ids[0], ids[1]); link(ids[2], ids[3]) }
        }
        val visited = HashSet<Int>()
        val out = ArrayList<List<Pair<Double, Double>>>()
        fun walk(start: Int) {
            val line = ArrayList<Pair<Double, Double>>()
            var cur = start
            var prev = -1
            while (true) {
                visited.add(cur)
                line.add(pos[cur]!!)
                val next = adj[cur]!!.firstOrNull { it != prev && !visited.contains(it) } ?: break
                prev = cur
                cur = next
            }
            if (line.size >= 2) out.add(line)
        }
        for ((id, n) in adj) if (n.size == 1 && id !in visited) walk(id)
        for (id in adj.keys) if (id !in visited) walk(id)
        return out
    }
}
