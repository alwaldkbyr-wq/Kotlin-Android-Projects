package com.mawaqeet.app.ui.screens

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.Horizontal
import com.mawaqeet.app.astro.Qibla
import com.mawaqeet.app.astro.norm180
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.DeviceOrientation
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.magneticDeclination
import com.mawaqeet.app.ui.components.rememberOrientation
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/* ───────── أدوات مشتركة بين البوصلة ثنائية وثلاثية الأبعاد ───────── */

internal class CompassBodies(val qibla: Double, val decl: Double, val sun: Horizontal, val moon: Horizontal)

/** اتجاه القبلة والانحراف المغناطيسي ومواضع الشمس والقمر (تُحدَّث كل 5 ثوانٍ) */
@Composable
internal fun rememberCompassBodies(): CompassBodies {
    val env = LocalEnv.current
    val tick = env.now / 5000L
    return remember(env.lat, env.lon, tick) {
        CompassBodies(
            Qibla.bearing(env.lat, env.lon),
            magneticDeclination(env.lat, env.lon),
            Ephemeris.horizontal(Body.SUN, env.now, env.lat, env.lon),
            Ephemeris.horizontal(Body.MOON, env.now, env.lat, env.lon)
        )
    }
}

internal fun compassVibrate(ctx: Context) {
    runCatching {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/**
 * إبقاء الشاشة مضاءة + اهتزاز مرة واحدة عند مطابقة اتجاه الهاتف للقبلة (±3°).
 * تعيد حالة «متطابق» (تُقرأ داخل الرسم فقط لتجنب إعادة التركيب).
 */
@Composable
internal fun compassEffects(oState: State<DeviceOrientation>, bodies: CompassBodies): State<Boolean> {
    val ctx = LocalContext.current
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val b by rememberUpdatedState(bodies)
    val aligned = remember {
        derivedStateOf {
            val o = oState.value
            o.available && abs(norm180(o.azimuthTrue(b.decl) - b.qibla)) <= 3.0
        }
    }
    LaunchedEffect(aligned.value) { if (aligned.value) compassVibrate(ctx) }
    return aligned
}

internal fun DrawScope.compassText(paint: Paint, text: String, x: Float, y: Float, sizePx: Float, color: Color, bold: Boolean = false) {
    paint.textSize = sizePx
    paint.color = color.toArgb()
    paint.isFakeBoldText = bold
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

internal fun newCompassPaint() = Paint().apply { typeface = com.mawaqeet.app.ui.theme.CanvasFonts.text; isAntiAlias = true; textAlign = Paint.Align.CENTER }

private val compassLetters = listOf("ش", "شق", "ق", "جق", "ج", "جغ", "غ", "شغ")

private fun compassPoint(c: Offset, r: Float, screenDeg: Double): Offset {
    val t = Math.toRadians(screenDeg)
    return Offset(c.x + r * sin(t).toFloat(), c.y - r * cos(t).toFloat())
}

private class CompassColors(val ink: Color, val muted: Color, val gold: Color, val danger: Color, val success: Color, val sun: Color, val moon: Color)

private fun DrawScope.compassDrawDial(
    heading: Double, b: CompassBodies, aligned: Boolean, hasSensor: Boolean, col: CompassColors, paint: Paint
) {
    val dp = 1.dp.toPx()
    val c = center
    val rad = size.minDimension / 2f - 40 * dp
    if (aligned) {
        for (i in 1..4) drawCircle(col.gold.copy(alpha = 0.10f), radius = rad + i * 7 * dp, center = c, style = Stroke(width = 8 * dp))
    }
    drawCircle(col.ink.copy(alpha = 0.05f), radius = rad, center = c)
    drawCircle(if (aligned) col.success else col.gold, radius = rad, center = c, style = Stroke(width = 3 * dp))
    // التدريج
    for (a in 0 until 360 step 5) {
        val lenDp = when { a % 90 == 0 -> 18; a % 45 == 0 -> 14; a % 15 == 0 -> 10; else -> 6 }
        val len = lenDp * dp
        val th = a - heading
        drawLine(
            if (a == 0) col.danger else col.ink.copy(alpha = if (a % 15 == 0) 0.85f else 0.45f),
            compassPoint(c, rad, th), compassPoint(c, rad - len, th),
            strokeWidth = (if (a % 45 == 0) 2.5f else 1.3f) * dp
        )
    }
    // أرقام كل 30° والحروف
    for (a in 0 until 360 step 30) {
        if (a % 90 == 0) continue
        rotate((a - heading).toFloat(), c) {
            compassText(paint, "$a", c.x, c.y - rad + 32 * dp, 10.sp.toPx(), col.muted)
        }
    }
    for (i in 0 until 8) {
        val a = i * 45
        rotate((a - heading).toFloat(), c) {
            compassText(paint, compassLetters[i], c.x, c.y - rad + 58 * dp, (if (i % 2 == 0) 20 else 14).sp.toPx(), if (i == 0) col.danger else col.ink, i % 2 == 0)
        }
    }
    // خط القبلة
    val qa = b.qibla - heading
    drawLine(
        col.gold.copy(alpha = 0.7f), compassPoint(c, 46 * dp, qa), compassPoint(c, rad, qa),
        strokeWidth = 2.5f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * dp, 7 * dp))
    )
    // الشمس
    val sunP = compassPoint(c, rad, b.sun.az - heading)
    val sunUp = b.sun.alt > 0
    if (sunUp) drawCircle(col.sun, 11 * dp, sunP) else drawCircle(col.sun, 11 * dp, sunP, style = Stroke(2.2f * dp))
    for (k in 0 until 8) {
        val th = k * 45.0 - heading
        drawLine(col.sun.copy(alpha = if (sunUp) 1f else 0.6f), compassPoint(sunP, 14 * dp, th), compassPoint(sunP, 19 * dp, th), strokeWidth = 2 * dp)
    }
    // القمر
    val mp = compassPoint(c, rad, b.moon.az - heading)
    val moonUp = b.moon.alt > 0
    if (moonUp) drawCircle(col.moon, 10 * dp, mp) else drawCircle(col.moon, 10 * dp, mp, style = Stroke(2.2f * dp))
    drawCircle(col.ink.copy(alpha = 0.55f), 10 * dp, mp, style = Stroke(1f * dp))
    // الكعبة (فوق الأخرى)
    val kp = compassPoint(c, rad, qa)
    drawCircle(col.gold, 16 * dp, kp)
    drawRect(Color(0xFF111111), Offset(kp.x - 8 * dp, kp.y - 8 * dp), Size(16 * dp, 16 * dp))
    drawRect(col.gold, Offset(kp.x - 8 * dp, kp.y - 4 * dp), Size(16 * dp, 2 * dp))
    // المؤشر الثابت في الأعلى
    val tip = Offset(c.x, c.y - rad - 18 * dp)
    val pointer = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - 9 * dp, tip.y - 20 * dp)
        lineTo(tip.x + 9 * dp, tip.y - 20 * dp)
        close()
    }
    drawPath(pointer, if (aligned) col.success else col.danger)
    // الاتجاه الحالي في المركز
    compassText(paint, if (hasSensor) "${norm360(heading).roundToInt() % 360}°" else "--", c.x, c.y + 8 * dp, 38.sp.toPx(), col.ink, true)
    compassText(paint, if (hasSensor) Fmt.direction(heading) else "الشمال للأعلى", c.x, c.y + 30 * dp, 13.sp.toPx(), col.muted)
}

/** البوصلة ثنائية الأبعاد: قرص يدور مع الاتجاه الحقيقي ومؤشرات القبلة والشمس والقمر */
@Composable
fun Compass2DScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val oState = rememberOrientation()
    val bodies = rememberCompassBodies()
    val aligned = compassEffects(oState, bodies)
    val noSensor by remember { derivedStateOf { !oState.value.available } }
    val lowAcc by remember { derivedStateOf { oState.value.available && oState.value.accuracy < 2 } }
    val paint = remember { newCompassPaint() }
    val col = CompassColors(o.ink, o.muted, o.gold, o.danger, o.success, Color(0xFFFFB300), Color(0xFFCFD8DC))
    val b = bodies

    ScreenScaffold(
        title = "البوصلة", onBack = env.back, subtitle = "القبلة والشمس والقمر",
        actions = {
            ShareAction {
                "اتجاه القبلة: ${Fmt.deg(b.qibla)} (${Fmt.direction(b.qibla)})\nالشمس: سمت ${Fmt.deg(b.sun.az)} وارتفاع ${Fmt.deg(b.sun.alt)}\n" +
                    "القمر: سمت ${Fmt.deg(b.moon.az)} وارتفاع ${Fmt.deg(b.moon.alt)}"
            }
        }
    ) {
        ScreenList {
            item {
                Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).aspectRatio(1f)) {
                    val s = oState.value
                    val heading = if (s.available) s.azimuthTrue(b.decl) else 0.0
                    compassDrawDial(heading, b, aligned.value && s.available, s.available, col, paint)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Pill("القبلة", o.gold)
                    Pill(if (b.sun.alt > 0) "الشمس" else "الشمس (تحت الأفق)", col.sun)
                    Pill(if (b.moon.alt > 0) "القمر" else "القمر (تحت الأفق)", o.muted)
                }
            }
            if (noSensor) item {
                GlassCard(title = "لا يتوفر حساس اتجاه") {
                    Text("لا يحتوي هذا الجهاز على حساس مغناطيسي أو متجه دوران، فتُعرض البوصلة ثابتة والشمال في الأعلى. استعمل الاتجاهات المكتوبة أدناه.", color = o.muted)
                }
            }
            if (lowAcc) item {
                GlassCard(title = "المعايرة مطلوبة") {
                    Text("دقة الحساس منخفضة: حرّك الهاتف على شكل 8 في الهواء عدة مرات، وابعده عن المعادن والمغانط.", color = o.warning)
                }
            }
            item {
                GlassCard(title = "الاتجاهات الآن") {
                    InfoRow("اتجاه القبلة", "${Fmt.deg(b.qibla)} ${Fmt.direction(b.qibla)}", valueColor = o.gold, emphasize = true)
                    InfoRow("الشمس", "سمت ${Fmt.deg(b.sun.az)} • ارتفاع ${Fmt.deg(b.sun.alt)}")
                    InfoRow("القمر", "سمت ${Fmt.deg(b.moon.az)} • ارتفاع ${Fmt.deg(b.moon.alt)}")
                    InfoRow("الانحراف المغناطيسي", "${Fmt.deg(abs(b.decl))} ${if (b.decl >= 0) "شرقاً" else "غرباً"}")
                }
            }
            item { Note("الاتجاهات هنا من الشمال الجغرافي بعد تصحيح الانحراف المغناطيسي. أبقِ الهاتف مسطّحاً للحصول على أدق قراءة. عند مطابقة اتجاه الهاتف للقبلة (±3°) يتوهّج القرص ويهتز الهاتف مرة. الشمس أو القمر المجوّف: تحت الأفق.") }
        }
    }
}
