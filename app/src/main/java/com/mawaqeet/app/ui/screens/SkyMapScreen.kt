package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.layout.widthIn
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.AU_KM
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.DEG
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.StarCatalog
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.ScreenScaffold
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.tan

/* ───────────────────── أدوات مشتركة بين شاشات السماء ───────────────────── */

internal object SkyPaint {
    val paint: Paint = Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text; isAntiAlias = true }
}

/** نص عربي على Canvas (الأحرف تُشكَّل صحيحاً) */
internal fun DrawScope.skyText(
    text: String, x: Float, y: Float, sizePx: Float, argb: Int,
    align: Paint.Align = Paint.Align.CENTER, bold: Boolean = false
) {
    val p = SkyPaint.paint
    p.textSize = sizePx
    p.color = argb
    p.textAlign = align
    p.isFakeBoldText = bold
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

internal fun skyBodyColor(b: Body): Color = when (b) {
    Body.SUN -> Color(0xFFFFD54F)
    Body.MOON -> Color(0xFFEDEBDD)
    Body.MERCURY -> Color(0xFFBDBDBD)
    Body.VENUS -> Color(0xFFFFF4D6)
    Body.MARS -> Color(0xFFFF6A4D)
    Body.JUPITER -> Color(0xFFFFD9A0)
    Body.SATURN -> Color(0xFFE8D28A)
    Body.URANUS -> Color(0xFF8FE3E3)
    Body.NEPTUNE -> Color(0xFF6F8CFF)
}

/** شريط التحكم بالوقت: −1 س / الآن / +1 س وشريط ±12 ساعة */
@Composable
internal fun SkyTimeBar(
    offsetMin: Float,
    onOffset: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    extra: @Composable RowScope.() -> Unit = {}
) {
    val o = AppTheme.ornament
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassChip(selected = false, onClick = { onOffset((offsetMin - 60f).coerceIn(-720f, 720f)) }, label = { Text("−1 س") })
            GlassChip(selected = abs(offsetMin) < 0.5f, onClick = { onOffset(0f) }, label = { Text("الآن") })
            GlassChip(selected = false, onClick = { onOffset((offsetMin + 60f).coerceIn(-720f, 720f)) }, label = { Text("+1 س") })
            Text(label, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
            extra()
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(value = offsetMin, onValueChange = onOffset, valueRange = -720f..720f)
        }
    }
}

internal fun skyOffsetLabel(offsetMin: Float): String =
    if (abs(offsetMin) < 0.5f) "الآن" else (if (offsetMin > 0) "+" else "−") + Fmt.num(abs(offsetMin) / 60.0, 1) + " س"

/* ───────────────────── خريطة السماء ───────────────────── */

private data class SkyPick(val star: Int, val body: Body?)

private class SkyXf(val cx: Float, val cy: Float, val s: Float, val az0: Double) {
    private val t = DoubleArray(2)
    fun at(alt: Double, az: Double): Offset {
        if (alt < -25.0) return Offset.Unspecified
        SkyMath.project(alt, az - az0, t)
        return Offset(cx + (t[0] * s).toFloat(), cy + (t[1] * s).toFloat())
    }
    fun any(alt: Double, az: Double): Offset {
        if (alt < -80.0) return Offset.Unspecified
        SkyMath.project(alt, az - az0, t)
        return Offset(cx + (t[0] * s).toFloat(), cy + (t[1] * s).toFloat())
    }
    fun dir(az: Double): Offset {
        SkyMath.project(0.0, az - az0, t)
        return Offset(t[0].toFloat(), t[1].toFloat())
    }
}

private class SkyTimes(val rise: Long?, val transit: Long?, val set: Long?, val note: String?)

@Composable
fun SkyMapScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    var offsetMin by remember { mutableFloatStateOf(0f) }
    var showStars by remember { mutableStateOf(true) }
    var showLines by remember { mutableStateOf(true) }
    var showNames by remember { mutableStateOf(true) }
    var showMansions by remember { mutableStateOf(true) }
    var showPlanets by remember { mutableStateOf(true) }
    var follow by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var picked by remember { mutableStateOf<SkyPick?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val heading = remember { mutableDoubleStateOf(0.0) }
    var sensorOk by remember { mutableStateOf(true) }

    val tMs = ((env.now + (offsetMin * 60000f).toLong()) / 60000L) * 60000L
    val frame = remember(tMs, env.lat, env.lon, env.place.heightM) { SkyMath.frame(tMs, env.lat, env.lon, env.place.heightM) }
    val frameState = rememberUpdatedState(frame)
    val flags = rememberUpdatedState(booleanArrayOf(showStars, showPlanets))

    LaunchedEffect(follow) { if (!follow) { heading.doubleValue = 0.0; sensorOk = true } }

    val inkArgb = o.ink.toArgb()
    val mutedArgb = o.muted.toArgb()
    val gold = o.gold
    val goldArgb = o.gold.toArgb()

    var full by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = full) { full = false }
    ScreenScaffold(
        title = "خريطة السماء", onBack = env.back, subtitle = "السماء فوق ${env.place.name}",
        immersive = true, fullscreen = full, actions = { FullscreenToggle(full) { full = !full } }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (follow) SkyHeadingTracker(env.lat, env.lon) { az, ok -> heading.doubleValue = az; sensorOk = ok }

            fun makeXf(): SkyXf {
                val w = canvasSize.width.toFloat(); val h = canvasSize.height.toFloat()
                val base = min(w, h) / 2f * 0.86f
                return SkyXf(w / 2f + pan.x, h * 0.45f + pan.y, base * scale, heading.doubleValue)
            }

            Canvas(
                Modifier.fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, panDelta, zoom, _ ->
                            val w = canvasSize.width.toFloat(); val h = canvasSize.height.toFloat()
                            val base = min(w, h) / 2f * 0.86f
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            val k = newScale / scale
                            val c = Offset(w / 2f, h * 0.45f)
                            var np = (centroid - c) - ((centroid - c) - pan) * k + panDelta
                            val lim = base * newScale * 1.1f
                            np = Offset(np.x.coerceIn(-lim, lim), np.y.coerceIn(-lim, lim))
                            scale = newScale
                            pan = if (newScale <= 1.001f) Offset.Zero else np
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { tap ->
                            val f = frameState.value
                            val xf = makeXf()
                            var best: SkyPick? = null
                            var bestD = Float.MAX_VALUE
                            val fl = flags.value
                            val density = this.density
                            if (fl[0]) {
                                val stars = StarCatalog.stars
                                for (i in stars.indices) {
                                    if (stars[i].mag > 3.6 || f.starAlt[i] < 0.0) continue
                                    val p = xf.at(f.starAlt[i], f.starAz[i])
                                    if (!p.isSpecified) continue
                                    val d = (p - tap).getDistance()
                                    if (d < 26f * density && d < bestD) { bestD = d; best = SkyPick(i, null) }
                                }
                            }
                            for (b in Body.values()) {
                                if (b.isPlanet && !fl[1]) continue
                                val bp = f.body(b)
                                val p = xf.at(bp.alt, bp.az)
                                if (!p.isSpecified || bp.alt < -1.0) continue
                                val d = (p - tap).getDistance() - 10f * density
                                if (d < 26f * density && d < bestD) { bestD = d; best = SkyPick(-1, b) }
                            }
                            picked = best
                        })
                    }
            ) {
                val dp = 1.dp.toPx()
                val xf = makeXf()
                val s = xf.s
                val f = frame
                val center = Offset(xf.cx, xf.cy)
                val sunAlt = f.body(Body.SUN).alt
                val tDay = ((sunAlt + 18.0) / 18.0).coerceIn(0.0, 1.0).toFloat()
                val zenith = lerp(Color(0xFF030814), Color(0xFF2F6DB0), tDay)
                val horizon = lerp(Color(0xFF0E2038), Color(0xFF9CC7EA), tDay)
                val starA = 1f - 0.92f * tDay
                val zoomK = (1f + (scale - 1f) * 0.12f).coerceAtMost(1.8f)
                val disc = Path().apply { addOval(Rect(center, s)) }

                clipPath(disc) {
                    drawCircle(brush = Brush.radialGradient(listOf(zenith, horizon), center = center, radius = s), radius = s, center = center)

                    // الشبكة
                    val gridC = Color.White.copy(alpha = 0.07f)
                    for (a in doubleArrayOf(30.0, 60.0)) {
                        drawCircle(gridC, radius = (tan((90.0 - a) * DEG / 2.0) * s).toFloat(), center = center, style = Stroke(1f * dp))
                    }
                    for (az in 0 until 360 step 30) {
                        val p = xf.at(0.0, az.toDouble())
                        if (p.isSpecified) drawLine(gridC, center, p, 1f * dp)
                    }

                    // مسار متعدد القطع من مصفوفتي ارتفاع/سمت
                    fun polyPath(alts: DoubleArray, azs: DoubleArray): Path {
                        val path = Path()
                        var pen = false
                        for (i in alts.indices) {
                            val p = xf.at(alts[i], azs[i])
                            if (!p.isSpecified) { pen = false; continue }
                            if (!pen) { path.moveTo(p.x, p.y); pen = true } else path.lineTo(p.x, p.y)
                        }
                        return path
                    }
                    drawPath(polyPath(f.eqAlt, f.eqAz), Color(0xFF7FD6E8).copy(alpha = 0.30f), style = Stroke(1.2f * dp))
                    drawPath(
                        polyPath(f.eclAlt, f.eclAz), gold.copy(alpha = 0.85f),
                        style = Stroke(1.6f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * dp, 6f * dp)))
                    )

                    // خطوط الكوكبات
                    if (showLines) {
                        val lp = Path()
                        for (polys in f.conAlt) for (pl in polys) {
                            var pen = false
                            for (k in 0 until pl.size / 2) {
                                val p = xf.at(pl[2 * k], pl[2 * k + 1])
                                if (!p.isSpecified) { pen = false; continue }
                                if (!pen) { lp.moveTo(p.x, p.y); pen = true } else lp.lineTo(p.x, p.y)
                            }
                        }
                        drawPath(lp, Color(0xFF9DB8E8).copy(alpha = 0.30f * (0.4f + 0.6f * starA)), style = Stroke(1f * dp))
                        if (showNames) {
                            val cons = StarCatalog.constellations
                            for (ci in cons.indices) {
                                if (f.conLabelAlt[ci] < 2.0) continue
                                val p = xf.at(f.conLabelAlt[ci], f.conLabelAz[ci])
                                if (p.isSpecified) skyText(cons[ci].ar, p.x, p.y, 11.5f * dp, Color(0xFFBFD0F5).copy(alpha = 0.62f * (0.35f + 0.65f * starA)).toArgb())
                            }
                        }
                    }

                    // النجوم
                    if (showStars) {
                        val stars = StarCatalog.stars
                        val nameMag = (1.8 + (scale - 1f) * 0.9).coerceAtMost(4.5)
                        for (i in stars.indices) {
                            val alt = f.starAlt[i]
                            if (alt < -2.0) continue
                            val p = xf.at(alt, f.starAz[i])
                            if (!p.isSpecified) continue
                            val st = stars[i]
                            val r = (SkyMath.starRadius(st.mag) * zoomK).toFloat() * dp
                            val c = Color(SkyMath.starColor(st.latin))
                            if (st.mag < 1.6) drawCircle(c.copy(alpha = 0.16f * starA), r * 2.8f, p)
                            drawCircle(c.copy(alpha = starA), r, p)
                            if (showNames && st.mag <= nameMag && starA > 0.4f) {
                                skyText(st.ar.substringBefore(" ("), p.x, p.y + r + 11f * dp, 10f * dp, Color.White.copy(alpha = 0.72f * starA).toArgb())
                            }
                        }
                    }

                    // منازل القمر
                    if (showMansions) {
                        val names = com.mawaqeet.app.astro.Mansions.names
                        for (i in 0 until 28) {
                            if (f.mansAlt[i] < -3.0) continue
                            val p = xf.at(f.mansAlt[i], f.mansAz[i])
                            if (!p.isSpecified) continue
                            val cur = i == f.moonMansion
                            if (cur) {
                                drawCircle(gold, 5.5f * dp, p)
                                drawCircle(gold.copy(alpha = 0.9f), 10f * dp, p, style = Stroke(1.6f * dp))
                            } else drawCircle(o.goldSoft.copy(alpha = 0.85f), 2.6f * dp, p)
                            skyText((i + 1).toString(), p.x, p.y - 7f * dp, 9f * dp, goldArgb)
                            if (showNames || cur) skyText(names[i], p.x, p.y + 15f * dp, (if (cur) 11.5f else 9.5f) * dp, if (cur) goldArgb else o.goldSoft.copy(alpha = 0.85f).toArgb(), bold = cur)
                        }
                    }

                    // الكواكب
                    if (showPlanets) {
                        for (b in Body.values()) {
                            if (!b.isPlanet) continue
                            val bp = f.body(b)
                            if (bp.alt < -2.0) continue
                            val p = xf.at(bp.alt, bp.az)
                            if (!p.isSpecified) continue
                            val r = ((3.2 + (1.5 - bp.mag) * 0.5).coerceIn(3.0, 6.5) * zoomK).toFloat() * dp
                            val c = skyBodyColor(b)
                            drawCircle(c.copy(alpha = 0.22f), r * 2.4f, p)
                            drawCircle(c, r, p)
                            if (showNames) skyText(b.ar, p.x, p.y - r - 5f * dp, 12f * dp, c.toArgb(), bold = true)
                        }
                    }

                    // الشمس والقمر
                    val sun = f.body(Body.SUN); val moon = f.body(Body.MOON)
                    val sp = xf.at(sun.alt, sun.az)
                    if (sp.isSpecified) {
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE082).copy(alpha = 0.85f), Color.Transparent), center = sp, radius = 38f * dp), 38f * dp, sp)
                        drawCircle(skyBodyColor(Body.SUN), 8.5f * dp, sp)
                        if (showNames) skyText("الشمس", sp.x, sp.y - 14f * dp, 12f * dp, skyBodyColor(Body.SUN).toArgb(), bold = true)
                    }
                    val mp = xf.at(moon.alt, moon.az)
                    if (mp.isSpecified) {
                        val sAny = xf.any(sun.alt, sun.az)
                        val ang = if (sAny.isSpecified) Math.toDegrees(atan2((sAny.y - mp.y).toDouble(), (sAny.x - mp.x).toDouble())).toFloat()
                        else if (f.moonWaxing) 0f else 180f
                        drawCircle(Color.White.copy(alpha = 0.10f), 20f * dp, mp)
                        skyDrawMoon(mp, 9.5f * dp, f.moonIllum, ang)
                        if (showNames) skyText("القمر", mp.x, mp.y - 15f * dp, 12f * dp, skyBodyColor(Body.MOON).toArgb(), bold = true)
                    }

                    // التحديد
                    picked?.let { pk ->
                        val p = if (pk.body != null) f.body(pk.body).let { xf.at(it.alt, it.az) } else xf.at(f.starAlt[pk.star], f.starAz[pk.star])
                        if (p.isSpecified) drawCircle(gold, 15f * dp, p, style = Stroke(2f * dp))
                    }
                    // السمت
                    drawLine(Color.White.copy(alpha = 0.25f), center - Offset(5f * dp, 0f), center + Offset(5f * dp, 0f), 1f * dp)
                    drawLine(Color.White.copy(alpha = 0.25f), center - Offset(0f, 5f * dp), center + Offset(0f, 5f * dp), 1f * dp)
                }
                // حافة الأفق والجهات (الشرق على اليسار)
                drawCircle(gold.copy(alpha = 0.85f), s, center, style = Stroke(2f * dp))
                val cards = arrayOf(0.0 to "ش", 90.0 to "ق", 180.0 to "ج", 270.0 to "غ")
                for ((az, txt) in cards) {
                    val d = xf.dir(az)
                    val x = center.x + d.x * (s + 18f * dp)
                    val y = center.y + d.y * (s + 18f * dp)
                    skyText(txt, x, y + 6f * dp, 17f * dp, if (az == 0.0) goldArgb else inkArgb, bold = true)
                }
            }

            // شريط الوقت العلوي
            if (full) FullscreenToggle(true, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp).glass(RoundedCornerShape(50), shadow = false)) { full = false }
            if (!full) Text(
                env.dateTime(tMs) + "  •  " + skyOffsetLabel(offsetMin),
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = TopBarSpace).glass(RoundedCornerShape(50), shadow = false).padding(horizontal = 14.dp, vertical = 6.dp),
                color = o.ink, style = MaterialTheme.typography.labelLarge
            )
            if (follow && !sensorOk) {
                Text(
                    "مستشعر الاتجاه غير متاح على هذا الجهاز", Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = TopBarSpace + 40.dp),
                    color = o.danger, style = MaterialTheme.typography.labelMedium
                )
            }
            if (scale > 1.01f) {
                IconButton(onClick = { scale = 1f; pan = Offset.Zero }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = if (full) 8.dp else TopBarSpace, end = 8.dp).glass(RoundedCornerShape(50), shadow = false)) {
                    Icon(Icons.Filled.Refresh, "إعادة الضبط", tint = o.ink)
                }
            }

            if (!full) Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().navigationBarsPadding()) {
                picked?.let { pk -> SkyObjectCard(pk.body, if (pk.body == null) StarCatalog.stars[pk.star] else null, frame.millis, onClose = { picked = null }) }
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SkyTimeBar(offsetMin, { offsetMin = it }, skyOffsetLabel(offsetMin))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassChip(showStars, { showStars = !showStars }, { Text("النجوم") })
                        GlassChip(showLines, { showLines = !showLines }, { Text("الكوكبات") })
                        GlassChip(showNames, { showNames = !showNames }, { Text("الأسماء") })
                        GlassChip(showMansions, { showMansions = !showMansions }, { Text("المنازل") })
                        GlassChip(showPlanets, { showPlanets = !showPlanets }, { Text("الكواكب") })
                        GlassChip(follow, { follow = !follow }, { Text("اتبع الهاتف") })
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyHeadingTracker(lat: Double, lon: Double, onHeading: (Double, Boolean) -> Unit) {
    // القراءة هنا في جسم الدالة (لا داخل SideEffect) حتى تُعاد الدالة مع كل قراءة جديدة من الحساس
    val o = rememberOrientation().value
    val decl = remember(lat, lon) { magneticDeclination(lat, lon) }
    SideEffect { onHeading(o.azimuthTrue(decl), o.available) }
}

/** القمر بقرص مظلَّل: [lightAngle] اتجاه الشمس على الشاشة بالدرجات */
internal fun DrawScope.skyDrawMoon(c: Offset, r: Float, illum: Double, lightAngle: Float) {
    val lit = Path().apply {
        arcTo(Rect(-r, -r, r, r), -90f, 180f, true)
        val b = (r * (1 - 2 * illum)).toFloat()
        val bw = abs(b)
        arcTo(Rect(-bw, -r, bw, r), 90f, if (b > 0) -180f else 180f, false)
        close()
    }
    withTransform({
        translate(c.x, c.y)
        rotate(lightAngle, Offset.Zero)
    }) {
        drawCircle(Color(0xFF3B4254), r, Offset.Zero)
        drawPath(lit, Color(0xFFF3EFD8))
        drawCircle(Color.White.copy(alpha = 0.35f), r, Offset.Zero, style = Stroke(r * 0.08f))
    }
}

@Composable
private fun SkyInfoCard(pick: SkyPick, f: SkyFrame, onClose: () -> Unit) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val dayStart = env.dayStart(env.dateOf(f.millis))
    val stars = StarCatalog.stars
    val times = remember(pick, dayStart, env.lat, env.lon) {
        if (pick.body != null) {
            val rs = RiseSetCalc.compute(pick.body, dayStart, env.lat, env.lon, null, env.place.heightM)
            SkyTimes(rs.rise, rs.transit, rs.set, if (rs.alwaysUp) "لا يغيب اليوم" else if (rs.alwaysDown) "لا يطلع اليوم" else null)
        } else {
            val bp = SkyMath.precess(stars[pick.star].ra, stars[pick.star].dec, f.jde)
            val st = SkyMath.starTimes(bp.ra, bp.dec, dayStart, env.lat, env.lon)
            SkyTimes(st.rise, st.transit, st.set, if (st.circumpolar) "نجم دائم الظهور" else if (st.neverRises) "لا يظهر في هذا الموقع" else null)
        }
    }
    val title: String; val sub: String; val alt: Double; val az: Double; val mag: Double
    if (pick.body != null) {
        val bp = f.body(pick.body)
        title = pick.body.ar; sub = if (pick.body.isPlanet) "كوكب" else if (pick.body == Body.SUN) "نجمنا" else "تابع الأرض"
        alt = bp.alt; az = bp.az; mag = bp.mag
    } else {
        val st = stars[pick.star]
        title = st.ar; sub = st.latin; alt = f.starAlt[pick.star]; az = f.starAz[pick.star]; mag = st.mag
    }
    GlassCard(
        title = title, subtitle = sub,
        trailing = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = o.muted) } }
    ) {
        MetricRow(
            "الارتفاع" to Fmt.deg(alt),
            "السمت" to Fmt.deg(az, 0),
            "القدر" to Fmt.num(mag, 2)
        )
        MetricRow("الطلوع" to env.time(times.rise), "العبور" to env.time(times.transit), "الغروب" to env.time(times.set))
        if (pick.body != null) {
            val bp = f.body(pick.body)
            if (pick.body == Body.MOON) InfoRow("المسافة", String.format(Locale.US, "%,d", bp.distKm.toLong()) + " كم")
            else InfoRow("المسافة", Fmt.num(bp.distKm / AU_KM, 3) + " و.ف")
        }
        InfoRow("الحالة", (if (alt > 0) "فوق الأفق" else "تحت الأفق") + " • " + Fmt.direction(az) + (times.note?.let { " • $it" } ?: ""), valueColor = if (alt > 0) o.success else o.muted)
    }
}
