package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DEG
import com.mawaqeet.app.astro.StarCatalog
import com.mawaqeet.app.astro.asind
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.lst
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.rememberPointing
import com.mawaqeet.app.ui.components.FullscreenToggle
import com.mawaqeet.app.ui.components.TopBarSpace
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.mawaqeet.app.ui.components.glass
import com.mawaqeet.app.ui.components.magneticDeclination
import com.mawaqeet.app.ui.components.rememberOrientation
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SPH_DIST = 3.4

/* ───────────────────── الكاميرا ───────────────────── */

/** كاميرا منظورية: r (يمين)، u (أعلى)، f (أمام) في إحداثيات (شرق، شمال، أعلى) */
private class SphCam(
    val r: DoubleArray, val u: DoubleArray, val f: DoubleArray,
    val dist: Double, val focal: Double, val cx: Double, val cy: Double
) {
    fun toCam(x: Double, y: Double, z: Double, out: DoubleArray) {
        out[0] = r[0] * x + r[1] * y + r[2] * z
        out[1] = u[0] * x + u[1] * y + u[2] * z
        out[2] = f[0] * x + f[1] * y + f[2] * z + dist
    }

    /** يملأ out بـ (x, y الشاشة، العمق). false إن كانت النقطة خلف الكاميرا */
    fun proj(x: Double, y: Double, z: Double, out: DoubleArray): Boolean {
        toCam(x, y, z, out)
        if (out[2] < 0.05) return false
        val k = focal / out[2]
        val sx = cx + out[0] * k
        val sy = cy - out[1] * k
        out[0] = sx; out[1] = sy
        return true
    }
}

private fun sphCamera(yaw: Double, pitch: Double, inside: Boolean, w: Float, h: Float, zoom: Float): SphCam {
    val y = yaw * DEG; val p = pitch * DEG
    val cy = cos(y); val sy = sin(y); val cp = cos(p); val sp = sin(p)
    val f = doubleArrayOf(cp * sy, cp * cy, sp)
    val r = doubleArrayOf(cy, -sy, 0.0)
    val u = doubleArrayOf(-sy * sp, -cy * sp, cp)
    val base = min(w, h) * (if (inside) 0.9 else 1.45)
    return SphCam(r, u, f, if (inside) 0.0 else SPH_DIST, base * zoom, w / 2.0, h * 0.42)
}

/* ───────────────────── البيانات الثابتة (تُحسب مرة واحدة) ───────────────────── */

private class SphStatic {
    val stars = StarCatalog.stars.filter { it.mag <= 4.0 }
    val nS = stars.size
    val sVec = DoubleArray(3 * nS)
    val sColor: Array<Color>
    val cons = StarCatalog.constellations
    val lineVec: DoubleArray
    val nLv: Int
    val segA: IntArray
    val segB: IntArray
    val labVec = DoubleArray(3 * cons.size)
    val horizon = circle(96) { a -> doubleArrayOf(sin(a), cos(a), 0.0) }
    val meridian = circle(96) { a -> doubleArrayOf(0.0, cos(a), sin(a)) }
    val alt30 = circle(96) { a -> doubleArrayOf(cos(30 * DEG) * sin(a), cos(30 * DEG) * cos(a), sin(30 * DEG)) }
    val alt60 = circle(96) { a -> doubleArrayOf(cos(60 * DEG) * sin(a), cos(60 * DEG) * cos(a), sin(60 * DEG)) }
    val spokes: DoubleArray
    val eqVec = circle(72) { a -> doubleArrayOf(cos(a), sin(a), 0.0) }
    val eclVec = circle(72) { a -> doubleArrayOf(cos(a), sin(a) * cos(23.4393 * DEG), sin(a) * sin(23.4393 * DEG)) }
    val groundRing = circle(72) { a -> doubleArrayOf(sin(a), cos(a), -0.02) }
    val groundMid = circle(72) { a -> doubleArrayOf(0.66 * sin(a), 0.66 * cos(a), -0.02) }
    val groundIn = circle(72) { a -> doubleArrayOf(0.33 * sin(a), 0.33 * cos(a), -0.02) }
    val groundSpokes: DoubleArray

    init {
        for (i in 0 until nS) {
            val v = SkyMath.unit(stars[i].ra, stars[i].dec)
            sVec[3 * i] = v[0]; sVec[3 * i + 1] = v[1]; sVec[3 * i + 2] = v[2]
        }
        sColor = Array(nS) { Color(SkyMath.starColor(stars[it].latin)) }
        val verts = ArrayList<Double>()
        val a = ArrayList<Int>(); val b = ArrayList<Int>()
        for (c in cons) for (ln in c.lines) {
            val base = verts.size / 3
            val np = ln.size / 2
            for (k in 0 until np) {
                val v = SkyMath.unit(ln[2 * k], ln[2 * k + 1])
                verts.add(v[0]); verts.add(v[1]); verts.add(v[2])
                if (k > 0) { a.add(base + k - 1); b.add(base + k) }
            }
        }
        lineVec = verts.toDoubleArray(); nLv = verts.size / 3
        segA = a.toIntArray(); segB = b.toIntArray()
        for ((i, c) in cons.withIndex()) {
            val v = SkyMath.unit(c.labelRa, c.labelDec)
            labVec[3 * i] = v[0]; labVec[3 * i + 1] = v[1]; labVec[3 * i + 2] = v[2]
        }
        val sp = ArrayList<Double>()
        for (az in 0 until 360 step 30) {
            for (k in 0..18) {
                val el = k * 5.0 * DEG
                sp.add(cos(el) * sin(az * DEG)); sp.add(cos(el) * cos(az * DEG)); sp.add(sin(el))
            }
        }
        spokes = sp.toDoubleArray()
        val gs = ArrayList<Double>()
        for (az in 0 until 360 step 30) {
            gs.add(0.0); gs.add(0.0); gs.add(-0.02)
            gs.add(sin(az * DEG)); gs.add(cos(az * DEG)); gs.add(-0.02)
        }
        groundSpokes = gs.toDoubleArray()
    }

    private fun circle(n: Int, f: (Double) -> DoubleArray): DoubleArray {
        val out = DoubleArray(3 * (n + 1))
        for (i in 0..n) {
            val v = f(i * 2 * Math.PI / n)
            out[3 * i] = v[0]; out[3 * i + 1] = v[1]; out[3 * i + 2] = v[2]
        }
        return out
    }
}

private fun sphXf(m: DoubleArray, src: DoubleArray, dst: DoubleArray, n: Int) {
    for (i in 0 until n) {
        val x = src[3 * i]; val y = src[3 * i + 1]; val z = src[3 * i + 2]
        dst[3 * i] = m[0] * x + m[1] * y + m[2] * z
        dst[3 * i + 1] = m[3] * x + m[4] * y + m[5] * z
        dst[3 * i + 2] = m[6] * x + m[7] * y + m[8] * z
    }
}

/** ذاكرة الإطار: تُعاد الحسابات الثقيلة فقط حين يتغيّر الوقت أو المكان */
private class SphCache(val st: SphStatic) {
    var lastT = Long.MIN_VALUE
    var lastLat = 999.0
    var lastLon = 999.0
    val enuS = DoubleArray(3 * st.nS)
    val enuL = DoubleArray(3 * st.nLv)
    val enuLab = DoubleArray(3 * st.cons.size)
    val enuEq = DoubleArray(st.eqVec.size)
    val enuEcl = DoubleArray(st.eclVec.size)
    var eq2enu = DoubleArray(9)
    val tmp = DoubleArray(3)
    val path = Path()
    val lx = DoubleArray(st.nLv)
    val ly = DoubleArray(st.nLv)
    val lok = BooleanArray(st.nLv)
    val bodyEnu = DoubleArray(3 * Body.values().size)

    fun update(t: Long, lat: Double, lon: Double) {
        if (t == lastT && lat == lastLat && lon == lastLon) return
        lastT = t; lastLat = lat; lastLon = lon
        val jd = jdOf(t)
        val jde = jdeOf(jd)
        val l = lst(jd, lon)
        eq2enu = SkyMath.eqToEnuMatrix(l, lat)
        val m = SkyMath.mul(eq2enu, SkyMath.precessionMatrix(jde))
        sphXf(m, st.sVec, enuS, st.nS)
        sphXf(m, st.lineVec, enuL, st.nLv)
        sphXf(m, st.labVec, enuLab, st.cons.size)
        sphXf(eq2enu, st.eqVec, enuEq, st.eqVec.size / 3)
        sphXf(eq2enu, st.eclVec, enuEcl, st.eclVec.size / 3)
    }
}

/* ───────────────────── الرسم ───────────────────── */

private fun DrawScope.sphPoly(
    cam: SphCam, arr: DoubleArray, c: SphCache, color: Color, width: Float,
    dash: PathEffect? = null, minUp: Double = -2.0
) {
    val path = c.path
    path.rewind()
    val tmp = c.tmp
    var pen = false
    val n = arr.size / 3
    for (i in 0 until n) {
        val z = arr[3 * i + 2]
        if (z < minUp || !cam.proj(arr[3 * i], arr[3 * i + 1], z, tmp)) { pen = false; continue }
        if (!pen) { path.moveTo(tmp[0].toFloat(), tmp[1].toFloat()); pen = true } else path.lineTo(tmp[0].toFloat(), tmp[1].toFloat())
    }
    drawPath(path, color, style = Stroke(width, pathEffect = dash))
}

/** قرص الأرض تحت الراصد، مقصوص على مستوى الكاميرا القريب */
private fun DrawScope.sphGround(cam: SphCam, c: SphCache, color: Color) {
    val g = c.st.groundRing
    val n = g.size / 3
    val cs = Array(n) { i -> DoubleArray(3).also { cam.toCam(g[3 * i], g[3 * i + 1], g[3 * i + 2], it) } }
    val near = 0.05
    val path = c.path
    path.rewind()
    var started = false
    fun add(px: Double, py: Double, pz: Double) {
        val k = cam.focal / pz
        val x = (cam.cx + px * k).toFloat(); val y = (cam.cy - py * k).toFloat()
        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
    }
    for (i in 0 until n) {
        val a = cs[i]; val b = cs[(i + 1) % n]
        val ain = a[2] >= near; val bin = b[2] >= near
        if (ain) add(a[0], a[1], a[2])
        if (ain != bin) {
            val t = (near - a[2]) / (b[2] - a[2])
            add(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, near)
        }
    }
    if (started) { path.close(); drawPath(path, color) }
}

@Composable
fun Sphere3DScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val st = remember { SphStatic() }
    val cache = remember { SphCache(st) }

    var inside by remember { mutableStateOf(true) }
    var yaw by remember { mutableDoubleStateOf(if (env.lat >= 0) 180.0 else 0.0) }
    var pitch by remember { mutableDoubleStateOf(35.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var timeOffsetMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(60) }
    var follow by remember { mutableStateOf(false) }
    var sensorOk by remember { mutableStateOf(true) }
    var showLines by remember { mutableStateOf(true) }
    var showGrid by remember { mutableStateOf(false) }
    var showNames by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(true) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val fYaw = remember { mutableDoubleStateOf(0.0) }
    val fPitch = remember { mutableDoubleStateOf(30.0) }
    // الاتجاه المعروض يقترب من قراءة الحساس بنعومة في كل إطار (60 إطاراً في الثانية)
    // بدل القفز مع كل قراءة، فتتحرك السماء بسلاسة مهما كان إيقاع الحساس
    val dYaw = remember { mutableDoubleStateOf(0.0) }
    val dPitch = remember { mutableDoubleStateOf(30.0) }
    // محاور الشاشة من الحساس (الهدف) والمعروضة (المنعّمة) — كاميرا «نافذة» حقيقية تتبع الهاتف بميلانه كله
    val tBasis = remember { DoubleArray(9) }
    val dBasis = remember { DoubleArray(9) }
    val basisTick = remember { mutableLongStateOf(0L) }
    var sensorRate by remember { mutableIntStateOf(0) }
    var sensorFused by remember { mutableStateOf(true) }

    val nowState = rememberUpdatedState(env.now)
    val latState = rememberUpdatedState(env.lat)
    val lonState = rememberUpdatedState(env.lon)

    fun resetView(toInside: Boolean) {
        inside = toInside
        yaw = if (toInside) (if (env.lat >= 0) 180.0 else 0.0) else 0.0
        pitch = if (toInside) 35.0 else -25.0
        zoom = 1f
    }

    // حلقة الحركة: لا تعمل إلا أثناء التشغيل
    LaunchedEffect(playing, speed) {
        if (playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9).coerceAtMost(0.1)
                last = now
                timeOffsetMs += (dt * speed * 1000.0).toLong()
            }
        }
    }
    LaunchedEffect(follow) { if (!follow) sensorOk = true }
    LaunchedEffect(follow) {
        if (!follow) return@LaunchedEffect
        var first = true
        var last = 0L
        while (true) {
            val t = withFrameNanos { it }
            val dt = if (last == 0L) 0.016 else ((t - last) / 1e9).coerceIn(0.0, 0.1)
            last = t
            if (tBasis.all { it == 0.0 }) continue
            if (first) { System.arraycopy(tBasis, 0, dBasis, 0, 9); first = false }
            else {
                var diff = 0.0
                for (i in 0..8) diff = maxOf(diff, kotlin.math.abs(tBasis[i] - dBasis[i]))
                // الهاتف ساكن: لا حاجة لإعادة الرسم
                if (diff < 2e-4 && basisTick.longValue != 0L) continue
                val k = 1.0 - kotlin.math.exp(-dt / 0.06)
                for (i in 0..8) dBasis[i] += (tBasis[i] - dBasis[i]) * k
            }
            sphOrthonormalize(dBasis)
            dYaw.doubleValue = fYaw.doubleValue; dPitch.doubleValue = fPitch.doubleValue
            basisTick.longValue = t
        }
    }

    // مواقع الأجرام: دقيقة بدقيقة عند التوقف، ونحو 15 دقيقة أثناء التشغيل
    val bodyKey by remember {
        derivedStateOf {
            val t = nowState.value + timeOffsetMs
            if (playing) t / 900_000L else t / 60_000L
        }
    }
    val bodies = remember(bodyKey, env.lat, env.lon) {
        SkyMath.bodyPositions(if (playing) bodyKey * 900_000L else bodyKey * 60_000L, env.lat, env.lon, env.place.heightM)
    }
    val illum = remember(bodies) { SkyMath.moonIllumination(bodies) }
    val minuteLabel by remember { derivedStateOf { (nowState.value + timeOffsetMs) / 60_000L } }
    val offsetMin by remember { derivedStateOf { (timeOffsetMs / 60_000L).toFloat().coerceIn(-720f, 720f) } }

    val inkArgb = o.ink.toArgb()
    val goldArgb = o.gold.toArgb()

    var full by rememberSaveable { mutableStateOf(false) }
    // مواقع الأجرام على الشاشة في آخر رسم (لاختيار جرم باللمس)
    val hits = remember { SphHits(StarCatalog.stars.size) }
    var pickedBody by remember { mutableStateOf<Body?>(null) }
    var pickedStar by remember { mutableStateOf<com.mawaqeet.app.astro.Star?>(null) }
    BackHandler(enabled = full) { full = false }
    ScreenScaffold(
        title = "الكرة السماوية ثلاثية الأبعاد", onBack = env.back, subtitle = "تحاكي سماء ${env.place.name}",
        immersive = true, fullscreen = full, actions = { FullscreenToggle(full) { full = !full } }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (follow) SphereHeadingTracker(env.lat, env.lon, tBasis, { r, f -> sensorRate = r; sensorFused = f }) { az, p, ok -> fYaw.doubleValue = az; fPitch.doubleValue = p; sensorOk = ok }

            Canvas(
                Modifier.fillMaxSize().clipToBounds()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zc, _ ->
                            zoom = (zoom * zc).coerceIn(0.5f, 8f)
                            if (!follow) {
                                if (inside) {
                                    val focal = min(canvasSize.width, canvasSize.height) * 0.9 * zoom
                                    val k = Math.toDegrees(1.0 / focal)
                                    yaw = (yaw - pan.x * k + 360.0) % 360.0
                                    pitch = (pitch + pan.y * k).coerceIn(-90.0, 90.0)
                                } else {
                                    yaw = (yaw + pan.x * 0.35 + 360.0) % 360.0
                                    pitch = (pitch - pan.y * 0.35).coerceIn(-90.0, 90.0)
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { resetView(inside) },
                            onTap = { pos ->
                                val lim = 34f * density
                                var best = lim * lim; var bb: Body? = null; var bs = -1
                                for (k in hits.bx.indices) if (hits.bv[k]) {
                                    val d = (hits.bx[k] - pos.x) * (hits.bx[k] - pos.x) + (hits.by[k] - pos.y) * (hits.by[k] - pos.y)
                                    if (d < best) { best = d; bb = Body.values()[k]; bs = -1 }
                                }
                                for (k in 0 until hits.n) if (hits.sv[k]) {
                                    val d = (hits.sx[k] - pos.x) * (hits.sx[k] - pos.x) + (hits.sy[k] - pos.y) * (hits.sy[k] - pos.y)
                                    if (d < best * 0.7f) { best = d; bb = null; bs = k }
                                }
                                pickedBody = bb
                                pickedStar = if (bb == null && bs >= 0) hits.star[bs] else null
                            }
                        )
                    }
            ) {
                val dp = 1.dp.toPx()
                val w = size.width; val h = size.height
                val t = nowState.value + timeOffsetMs
                val c = cache
                c.update(t, latState.value, lonState.value)
                val yawE = if (follow) dYaw.doubleValue else yaw
                val pitchE = if (follow) dPitch.doubleValue else pitch
                val cam = if (follow && inside && basisTick.longValue != 0L) {
                    val b = dBasis
                    SphCam(
                        doubleArrayOf(b[0], b[1], b[2]), doubleArrayOf(b[3], b[4], b[5]), doubleArrayOf(b[6], b[7], b[8]),
                        0.0, min(w, h) * 0.9 * zoom, w / 2.0, h / 2.0
                    )
                } else sphCamera(yawE, pitchE, inside, w, h, zoom)
                val tmp = c.tmp
                val ctr = Offset(cam.cx.toFloat(), cam.cy.toFloat())

                // الأجرام في إحداثيات الأفق
                for (b in Body.values()) {
                    val bp = bodies[b.ordinal]
                    val v = SkyMath.unit(bp.ra, bp.dec)
                    val m = c.eq2enu
                    c.bodyEnu[3 * b.ordinal] = m[0] * v[0] + m[1] * v[1] + m[2] * v[2]
                    c.bodyEnu[3 * b.ordinal + 1] = m[3] * v[0] + m[4] * v[1] + m[5] * v[2]
                    c.bodyEnu[3 * b.ordinal + 2] = m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
                }
                val sunUp = c.bodyEnu[3 * Body.SUN.ordinal + 2]
                val tDay = if (inside) ((asind(sunUp) + 18.0) / 18.0).coerceIn(0.0, 1.0).toFloat() else 0f
                val starA = 1f - 0.92f * tDay

                // الخلفية
                drawRect(if (inside) lerp(Color(0xFF050B18), Color(0xFF3A78B5), tDay) else Color(0xFF040913))
                if (!inside) {
                    val sr = (cam.focal / sqrt(SPH_DIST * SPH_DIST - 1.0)).toFloat()
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF1B3358).copy(alpha = 0.55f), Color(0xFF0A1630).copy(alpha = 0.15f)), center = ctr, radius = sr), sr, ctr)
                    drawCircle(Color.White.copy(alpha = 0.18f), sr, ctr, style = Stroke(1.2f * dp))
                    // قرص الأفق شبه الشفاف
                    val hp = Path()
                    var pen = false
                    val hz = c.st.horizon
                    for (i in 0 until hz.size / 3) {
                        cam.proj(hz[3 * i], hz[3 * i + 1], hz[3 * i + 2], tmp)
                        if (!pen) { hp.moveTo(tmp[0].toFloat(), tmp[1].toFloat()); pen = true } else hp.lineTo(tmp[0].toFloat(), tmp[1].toFloat())
                    }
                    hp.close()
                    drawPath(hp, Color(0xFF3FA796).copy(alpha = 0.20f))
                    // الراصد
                    if (cam.proj(0.0, 0.0, 0.0, tmp)) {
                        val q = Offset(tmp[0].toFloat(), tmp[1].toFloat())
                        drawCircle(o.gold, 3.5f * dp, q)
                        skyText("أنت", q.x, q.y - 8f * dp, 10.5f * dp, goldArgb)
                    }
                } else {
                    sphGround(cam, c, Color(0xFF16241D))
                    val gl = Color(0xFF86B79A).copy(alpha = 0.22f)
                    sphPoly(cam, c.st.groundIn, c, gl, 1f * dp)
                    sphPoly(cam, c.st.groundMid, c, gl, 1f * dp)
                    sphPoly(cam, c.st.groundRing, c, gl, 1f * dp)
                    val gs = c.st.groundSpokes
                    val gp = c.path
                    gp.rewind()
                    for (i in 0 until gs.size / 6) {
                        if (cam.proj(gs[6 * i], gs[6 * i + 1], gs[6 * i + 2], tmp)) {
                            val x0 = tmp[0].toFloat(); val y0 = tmp[1].toFloat()
                            if (cam.proj(gs[6 * i + 3], gs[6 * i + 4], gs[6 * i + 5], tmp)) { gp.moveTo(x0, y0); gp.lineTo(tmp[0].toFloat(), tmp[1].toFloat()) }
                        }
                    }
                    drawPath(gp, gl, style = Stroke(1f * dp))
                }
                val minUp = if (inside) -0.001 else -2.0

                // الشبكة
                if (showGrid) {
                    val gc = Color.White.copy(alpha = 0.10f)
                    sphPoly(cam, c.st.alt30, c, gc, 1f * dp, null, minUp)
                    sphPoly(cam, c.st.alt60, c, gc, 1f * dp, null, minUp)
                    val sp = c.st.spokes
                    val per = 19
                    for (s in 0 until sp.size / (3 * per)) {
                        c.path.rewind()
                        var pen = false
                        for (k in 0 until per) {
                            val i = s * per + k
                            if (!cam.proj(sp[3 * i], sp[3 * i + 1], sp[3 * i + 2], tmp)) { pen = false; continue }
                            if (!pen) { c.path.moveTo(tmp[0].toFloat(), tmp[1].toFloat()); pen = true } else c.path.lineTo(tmp[0].toFloat(), tmp[1].toFloat())
                        }
                        drawPath(c.path, gc, style = Stroke(1f * dp))
                    }
                }
                // الدوائر الكبرى: الاستواء والبروج والزوال والأفق
                sphPoly(cam, c.enuEq, c, Color(0xFF7FD6E8).copy(alpha = 0.50f), 1.3f * dp, null, minUp)
                sphPoly(cam, c.enuEcl, c, o.gold.copy(alpha = 0.90f), 1.6f * dp, PathEffect.dashPathEffect(floatArrayOf(10f * dp, 6f * dp)), minUp)
                sphPoly(cam, c.st.meridian, c, Color(0xFF8BE0A8).copy(alpha = 0.40f), 1.2f * dp, null, minUp)
                sphPoly(cam, c.st.horizon, c, o.gold.copy(alpha = 0.95f), 2.2f * dp, null, -2.0)

                // خطوط الكوكبات: تُتجاهل القطع التي طرفاها خلف الكاميرا
                if (showLines) {
                    val el = c.enuL
                    for (i in 0 until c.st.nLv) {
                        val ok = cam.proj(el[3 * i], el[3 * i + 1], el[3 * i + 2], tmp)
                        c.lok[i] = ok
                        if (ok) { c.lx[i] = tmp[0]; c.ly[i] = tmp[1] }
                    }
                    val lp = c.path
                    lp.rewind()
                    val sa = c.st.segA; val sb = c.st.segB
                    for (k in sa.indices) {
                        val a = sa[k]; val b = sb[k]
                        if (!c.lok[a] || !c.lok[b]) continue
                        if (inside && (el[3 * a + 2] < -0.02 || el[3 * b + 2] < -0.02)) continue
                        lp.moveTo(c.lx[a].toFloat(), c.ly[a].toFloat()); lp.lineTo(c.lx[b].toFloat(), c.ly[b].toFloat())
                    }
                    drawPath(lp, Color(0xFF9DB8E8).copy(alpha = (if (inside) 0.32f else 0.26f) * (0.4f + 0.6f * starA)), style = Stroke(1f * dp))
                    if (showNames) {
                        val lb = c.enuLab
                        for (i in c.st.cons.indices) {
                            if (inside && lb[3 * i + 2] < 0.02) continue
                            if (!cam.proj(lb[3 * i], lb[3 * i + 1], lb[3 * i + 2], tmp)) continue
                            val da = if (inside) 1f else (0.35f + 0.65f * ((SPH_DIST + 1 - tmp[2]) / 2).toFloat().coerceIn(0f, 1f))
                            skyText(c.st.cons[i].ar, tmp[0].toFloat(), tmp[1].toFloat(), 11f * dp, Color(0xFFBFD0F5).copy(alpha = 0.7f * da * (0.4f + 0.6f * starA)).toArgb())
                        }
                    }
                }

                // النجوم
                val es = c.enuS
                hits.n = c.st.nS
                for (i in 0 until c.st.nS) {
                    hits.sv[i] = false
                    val up = es[3 * i + 2]
                    if (inside && up < -0.01) continue
                    if (!cam.proj(es[3 * i], es[3 * i + 1], up, tmp)) continue
                    hits.sv[i] = true; hits.sx[i] = tmp[0].toFloat(); hits.sy[i] = tmp[1].toFloat(); hits.star[i] = c.st.stars[i]
                    var a = starA
                    if (!inside) {
                        a *= 0.35f + 0.65f * ((SPH_DIST + 1 - tmp[2]) / 2).toFloat().coerceIn(0f, 1f)
                        if (up < 0) a *= 0.55f
                    }
                    val mag = c.st.stars[i].mag
                    val r = (SkyMath.starRadius(mag) * (if (inside) 1.0 else 0.8) * (0.8 + 0.2 * zoom.coerceAtMost(2.5f))).toFloat() * dp
                    val col = c.st.sColor[i]
                    val p = Offset(tmp[0].toFloat(), tmp[1].toFloat())
                    if (mag < 1.6) drawCircle(col.copy(alpha = 0.15f * a), r * 2.6f, p)
                    drawCircle(col.copy(alpha = a), r, p)
                    if (showNames && mag < 1.7 && a > 0.4f) skyText(c.st.stars[i].ar.substringBefore(" ("), p.x, p.y + r + 11f * dp, 10f * dp, Color.White.copy(alpha = 0.7f * a).toArgb())
                }

                // القطبان والسمت والجهات
                val lat = latState.value
                val ncp = doubleArrayOf(0.0, cos(lat * DEG), sin(lat * DEG))
                fun mark(x: Double, y: Double, z: Double, text: String, col: Int, size: Float, ring: Boolean) {
                    if (inside && z < 0.0) return
                    if (!cam.proj(x, y, z, tmp)) return
                    val p = Offset(tmp[0].toFloat(), tmp[1].toFloat())
                    if (ring) drawCircle(Color(col), 5f * dp, p, style = Stroke(1.3f * dp))
                    skyText(text, p.x, p.y - (if (ring) 9f else -5f) * dp, size * dp, col)
                }
                if (showNames) {
                    mark(ncp[0], ncp[1], ncp[2], "القطب الشمالي", 0xFF7FD6E8.toInt(), 10.5f, true)
                    mark(-ncp[0], -ncp[1], -ncp[2], "القطب الجنوبي", 0xFF7FD6E8.toInt(), 10.5f, true)
                    mark(0.0, 0.0, 1.0, "السمت", 0xFF8BE0A8.toInt(), 10.5f, true)
                }
                val rr = if (inside) 1.0 else 1.12
                val e4 = if (inside) sin(4 * DEG) else 0.0
                val c4 = if (inside) cos(4 * DEG) else 1.0
                val cards = arrayOf(0 to "ش", 90 to "ق", 180 to "ج", 270 to "غ")
                val cardCol = if (inside) 0xFFFFFFFF.toInt() else inkArgb
                for ((az, txt) in cards) {
                    mark(sin(az * DEG) * c4 * rr, cos(az * DEG) * c4 * rr, e4, txt, if (az == 0) goldArgb else cardCol, 17f, false)
                }

                // الشمس والقمر والكواكب
                for (b in Body.values()) {
                    val i3 = 3 * b.ordinal
                    hits.bv[b.ordinal] = false
                    val ex = c.bodyEnu[i3]; val ey = c.bodyEnu[i3 + 1]; val ez = c.bodyEnu[i3 + 2]
                    if (inside && ez < -0.01) continue
                    if (!cam.proj(ex, ey, ez, tmp)) continue
                    hits.bv[b.ordinal] = true; hits.bx[b.ordinal] = tmp[0].toFloat(); hits.by[b.ordinal] = tmp[1].toFloat()
                    val p = Offset(tmp[0].toFloat(), tmp[1].toFloat())
                    val col = skyBodyColor(b)
                    var da = 1f
                    if (!inside) { da = 0.4f + 0.6f * ((SPH_DIST + 1 - tmp[2]) / 2).toFloat().coerceIn(0f, 1f); if (ez < 0) da *= 0.6f }
                    when (b) {
                        Body.SUN -> {
                            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE082).copy(alpha = 0.85f * da), Color.Transparent), center = p, radius = 36f * dp), 36f * dp, p)
                            drawCircle(col.copy(alpha = da), 8.5f * dp, p)
                        }
                        Body.MOON -> {
                            val si = 3 * Body.SUN.ordinal
                            var ang = 0f
                            val sp = DoubleArray(3)
                            if (cam.proj(c.bodyEnu[si], c.bodyEnu[si + 1], c.bodyEnu[si + 2], sp)) {
                                ang = Math.toDegrees(atan2(sp[1] - p.y, sp[0] - p.x)).toFloat()
                            }
                            drawCircle(Color.White.copy(alpha = 0.10f * da), 18f * dp, p)
                            skyDrawMoon(p, (if (inside) 9.5f else 7.5f) * dp, illum, ang)
                        }
                        else -> {
                            val r = ((3.2 + (1.5 - bodies[b.ordinal].mag) * 0.5).coerceIn(3.0, 6.5)).toFloat() * dp
                            drawCircle(col.copy(alpha = 0.22f * da), r * 2.4f, p)
                            drawCircle(col.copy(alpha = da), r, p)
                        }
                    }
                    skyText(b.ar, p.x, p.y - 14f * dp, 12f * dp, col.copy(alpha = da).toArgb(), bold = true)
                }
            }

            // مؤشرات علوية
            val headingText = if (follow) "يتبع اتجاه الهاتف" else if (inside)
                "النظر نحو ${Fmt.direction((yaw % 360.0 + 360.0) % 360.0)} • الارتفاع ${Fmt.deg(pitch, 0)}"
            else "المشاهدة من جهة ${Fmt.direction(((yaw + 180.0) % 360.0 + 360.0) % 360.0)}"
            if (full) FullscreenToggle(true, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(10.dp).glass(RoundedCornerShape(50), shadow = false)) { full = false }
            if (!full) Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = TopBarSpace), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    env.dateTime(minuteLabel * 60_000L) + (if (abs(offsetMin) > 0.5f || playing) "  •  " + skyOffsetLabel(offsetMin) else ""),
                    Modifier.glass(RoundedCornerShape(50), shadow = false).padding(horizontal = 14.dp, vertical = 6.dp),
                    color = o.ink, style = MaterialTheme.typography.labelLarge
                )
                if (follow) SphFollowReadout(fYaw, fPitch, sensorRate, sensorFused)
                else Text(
                    headingText,
                    Modifier.padding(top = 4.dp).glass(RoundedCornerShape(50), shadow = false).padding(horizontal = 12.dp, vertical = 3.dp),
                    color = o.muted, style = MaterialTheme.typography.labelMedium
                )
                if (follow && !sensorOk) Text("مستشعر الاتجاه غير متاح على هذا الجهاز", color = o.danger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }

            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().navigationBarsPadding()) {
            if (pickedBody != null || pickedStar != null) {
                SkyObjectCard(pickedBody, pickedStar, nowState.value + timeOffsetMs, onClose = { pickedBody = null; pickedStar = null })
            }
            if (!full) Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    .glass(RoundedCornerShape(24.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip(inside, { if (!inside) resetView(true) }, { Text("من الداخل") })
                    GlassChip(!inside, { if (inside) resetView(false) }, { Text("من الخارج") })
                    GlassChip(follow, { follow = !follow }, { Text("اتبع الهاتف") })
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { panelOpen = !panelOpen }) {
                        Icon(if (panelOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, "إظهار/إخفاء اللوحة", tint = o.ink)
                    }
                }
                if (panelOpen) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { playing = !playing }) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "إيقاف" else "تشغيل", tint = MaterialTheme.colorScheme.primary)
                        }
                        GlassChip(speed == 60, { speed = 60 }, { Text("×60") })
                        GlassChip(speed == 600, { speed = 600 }, { Text("×600") })
                        GlassChip(speed == 3600, { speed = 3600 }, { Text("×3600") })
                    }
                    SkyTimeBar(offsetMin, { timeOffsetMs = (it * 60_000f).toLong() }, skyOffsetLabel(offsetMin))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassChip(showLines, { showLines = !showLines }, { Text("الكوكبات") })
                        GlassChip(showGrid, { showGrid = !showGrid }, { Text("الشبكة") })
                        GlassChip(showNames, { showNames = !showNames }, { Text("الأسماء") })
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SphereHeadingTracker(
    lat: Double, lon: Double, basisOut: DoubleArray,
    onDiag: (Int, Boolean) -> Unit,
    onChange: (Double, Double, Boolean) -> Unit
) {
    // القراءة هنا في جسم الدالة (لا داخل SideEffect) حتى تُعاد الدالة مع كل قراءة جديدة من الحساس
    val p = rememberPointing().value
    val decl = remember(lat, lon) { magneticDeclination(lat, lon) }
    SideEffect {
        // من الشمال المغناطيسي إلى الحقيقي: تدوير المحاور حول الشاقول بزاوية الانحراف
        val cd = kotlin.math.cos(Math.toRadians(decl)); val sd = kotlin.math.sin(Math.toRadians(decl))
        val b = p.basis
        for (v in 0..2) {
            val e = b[3 * v]; val n = b[3 * v + 1]
            basisOut[3 * v] = e * cd + n * sd
            basisOut[3 * v + 1] = -e * sd + n * cd
            basisOut[3 * v + 2] = b[3 * v + 2]
        }
        onDiag(p.rate, p.fused)
        onChange(((p.azimuthMagnetic + decl) % 360.0 + 360.0) % 360.0, p.altitude.coerceIn(-89.0, 89.0), p.available)
    }
}

/** تصحيح المحاور لتبقى متعامدة بطول 1 (يمين، أعلى، أمام) */
private fun sphOrthonormalize(b: DoubleArray) {
    fun norm(o: Int) { val l = kotlin.math.sqrt(b[o] * b[o] + b[o + 1] * b[o + 1] + b[o + 2] * b[o + 2]).coerceAtLeast(1e-9); b[o] /= l; b[o + 1] /= l; b[o + 2] /= l }
    norm(6)
    val d = b[0] * b[6] + b[1] * b[7] + b[2] * b[8]
    b[0] -= d * b[6]; b[1] -= d * b[7]; b[2] -= d * b[8]
    norm(0)
    // أعلى = يمين × أمام
    b[3] = b[1] * b[8] - b[2] * b[7]
    b[4] = b[2] * b[6] - b[0] * b[8]
    b[5] = b[0] * b[7] - b[1] * b[6]
}


/** مواقع النجوم والأجرام على الشاشة في آخر إطار */
private class SphHits(size: Int) {
    var n = 0
    val sx = FloatArray(size); val sy = FloatArray(size); val sv = BooleanArray(size)
    val star = arrayOfNulls<com.mawaqeet.app.astro.Star>(size)
    val bx = FloatArray(9); val by = FloatArray(9); val bv = BooleanArray(9)
}


/** قراءة حيّة لاتجاه الهاتف أثناء «اتبع الهاتف» (تُعاد رسمها وحدها مع الحساس) */
@Composable
private fun SphFollowReadout(yaw: androidx.compose.runtime.MutableDoubleState, pitch: androidx.compose.runtime.MutableDoubleState, rate: Int, fused: Boolean) {
    val o = AppTheme.ornament
    val y = yaw.doubleValue; val p = pitch.doubleValue
    Text(
        "يتبع الهاتف • ${Fmt.direction(y)} ${Fmt.deg(y, 0)} • الارتفاع ${Fmt.deg(p, 0)} • ${rate}/ث ${if (fused) "" else "(بوصلة)"}",
        Modifier.padding(top = 4.dp).glass(RoundedCornerShape(50), shadow = false).padding(horizontal = 12.dp, vertical = 3.dp),
        color = o.muted, style = MaterialTheme.typography.labelMedium
    )
}
