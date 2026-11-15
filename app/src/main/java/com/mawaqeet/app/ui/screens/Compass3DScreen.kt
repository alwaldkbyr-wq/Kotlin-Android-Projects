package com.mawaqeet.app.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberOrientation
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/* ───────── رياضيات الإسقاط المنظوري (خاصة بهذه الشاشة) ───────── */

/**
 * إطار الأرض بإحداثيات الهاتف (x يمين، y أعلى الشاشة، z خارج الشاشة نحو العين):
 * [up] اتجاه «الأعلى» الحقيقي، [fwd] الاتجاه الأفقي الموافق لأعلى الهاتف، [right] الاتجاه الأفقي على يمينه.
 */
private class C3Frame(val up: DoubleArray, val fwd: DoubleArray, val right: DoubleArray)

private fun c3Cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
    a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
)

private fun c3Frame(pitchDeg: Double, rollDeg: Double): C3Frame {
    val p = Math.toRadians(pitchDeg)
    val r = Math.toRadians(rollDeg)
    val up = doubleArrayOf(-cos(p) * sin(r), sin(p), cos(p) * cos(r))
    var f = doubleArrayOf(-up[1] * up[0], 1.0 - up[1] * up[1], -up[1] * up[2])
    val n = sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2])
    f = if (n < 1e-3) doubleArrayOf(0.0, 0.0, -1.0) else doubleArrayOf(f[0] / n, f[1] / n, f[2] / n)
    return C3Frame(up, f, c3Cross(f, up))
}

/** اتجاه أفقي بزاوية [deltaDeg] (باتجاه عقارب الساعة) من أعلى الهاتف */
private fun c3Dir(f: C3Frame, deltaDeg: Double): DoubleArray {
    val t = Math.toRadians(deltaDeg)
    val c = cos(t); val s = sin(t)
    return doubleArrayOf(f.fwd[0] * c + f.right[0] * s, f.fwd[1] * c + f.right[1] * s, f.fwd[2] * c + f.right[2] * s)
}

private class C3Pt(val o: Offset, val k: Float)

/** إسقاط منظوري: العين على بعد [cam] (بوحدات نصف قطر القرص) على محور z */
private fun c3Project(v: DoubleArray, c: Offset, scale: Float, cam: Double = 3.4): C3Pt {
    val k = cam / (cam - v[2])
    return C3Pt(Offset(c.x + (v[0] * k * scale).toFloat(), c.y - (v[1] * k * scale).toFloat()), k.toFloat())
}

private fun c3Add(a: DoubleArray, b: DoubleArray, s: Double) = doubleArrayOf(a[0] + b[0] * s, a[1] + b[1] * s, a[2] + b[2] * s)

private val c3Letters = listOf("ش", "شق", "ق", "جق", "ج", "جغ", "غ", "شغ")

private class C3Pin(val dir: DoubleArray, val height: Double, val color: Color, val hollow: Boolean, val label: String)

private fun DrawScope.c3Draw(
    heading: Double, pitch: Double, roll: Double, hasSensor: Boolean, aligned: Boolean,
    b: CompassBodies, ink: Color, muted: Color, gold: Color, danger: Color, success: Color, paint: Paint
) {
    val dp = 1.dp.toPx()
    val f = c3Frame(pitch, roll)
    val scale = size.minDimension * 0.30f
    val c = Offset(size.width / 2f, size.height * 0.46f)
    fun ground(az: Double, r: Double): DoubleArray = c3Dir(f, az - heading).let { doubleArrayOf(it[0] * r, it[1] * r, it[2] * r) }

    // القرص
    val rim = Path()
    for (i in 0..120) {
        val p = c3Project(ground(i * 3.0, 1.0), c, scale).o
        if (i == 0) rim.moveTo(p.x, p.y) else rim.lineTo(p.x, p.y)
    }
    rim.close()
    drawPath(rim, ink.copy(alpha = 0.07f))
    drawPath(rim, if (aligned) success else gold, style = Stroke(width = 3 * dp))
    // حلقة داخلية
    val inner = Path()
    for (i in 0..120) {
        val p = c3Project(ground(i * 3.0, 0.62), c, scale).o
        if (i == 0) inner.moveTo(p.x, p.y) else inner.lineTo(p.x, p.y)
    }
    inner.close()
    drawPath(inner, ink.copy(alpha = 0.25f), style = Stroke(width = 1 * dp))
    // التدريج
    for (a in 0 until 360 step 5) {
        val len = if (a % 90 == 0) 0.14 else if (a % 45 == 0) 0.10 else if (a % 15 == 0) 0.07 else 0.04
        val p1 = c3Project(ground(a.toDouble(), 1.0), c, scale).o
        val p2 = c3Project(ground(a.toDouble(), 1.0 - len), c, scale).o
        drawLine(
            if (a == 0) danger else ink.copy(alpha = if (a % 15 == 0) 0.8f else 0.4f), p1, p2,
            strokeWidth = (if (a % 45 == 0) 2.4f else 1.2f) * dp
        )
    }
    // الحروف
    for (i in 0 until 8) {
        val p = c3Project(ground(i * 45.0, 0.80), c, scale)
        compassText(paint, c3Letters[i], p.o.x, p.o.y + 6 * dp * p.k, (if (i % 2 == 0) 19 else 13).sp.toPx() * p.k, if (i == 0) danger else ink, i % 2 == 0)
    }

    // الأوتاد: ارتفاعها ∝ ارتفاع الجرم (للأعلى فوق الأفق وللأسفل تحته)
    val pins = listOf(
        C3Pin(ground(b.qibla, 1.0), 0.28, gold, false, "القبلة"),
        C3Pin(ground(b.sun.az, 1.0), 0.9 * sin(Math.toRadians(b.sun.alt)), Color(0xFFFFB300), b.sun.alt < 0, "الشمس"),
        C3Pin(ground(b.moon.az, 1.0), 0.9 * sin(Math.toRadians(b.moon.alt)), Color(0xFFCFD8DC), b.moon.alt < 0, "القمر")
    ).sortedBy { it.dir[2] }
    for (pin in pins) {
        val base = c3Project(pin.dir, c, scale)
        val tip = c3Project(c3Add(pin.dir, f.up, pin.height), c, scale)
        drawLine(pin.color.copy(alpha = 0.9f), base.o, tip.o, strokeWidth = 3 * dp)
        drawCircle(ink.copy(alpha = 0.35f), 3.5f * dp, base.o)
        val rr = 11 * dp * tip.k
        if (pin.hollow) drawCircle(pin.color, rr, tip.o, style = Stroke(2.2f * dp)) else drawCircle(pin.color, rr, tip.o)
        compassText(paint, pin.label, tip.o.x, tip.o.y - rr - 4 * dp, 11.sp.toPx(), ink, true)
    }
    drawCircle(ink, 3 * dp, c)

    // مؤشر ثابت لأعلى الهاتف
    val tipY = 10 * dp
    val pointer = Path().apply {
        moveTo(size.width / 2f, tipY + 16 * dp)
        lineTo(size.width / 2f - 9 * dp, tipY)
        lineTo(size.width / 2f + 9 * dp, tipY)
        close()
    }
    drawPath(pointer, if (aligned) success else danger)
    compassText(paint, if (hasSensor) "${norm360(heading).roundToInt() % 360}°  ${Fmt.direction(heading)}" else "لا يتوفر حساس", size.width / 2f, tipY + 40 * dp, 18.sp.toPx(), ink, true)

    // فقاعة الميل (مستوى): تتحرك نحو الجهة الأعلى
    val gc = Offset(size.width / 2f, size.height - 46 * dp)
    val gr = 30 * dp
    drawCircle(ink.copy(alpha = 0.08f), gr, gc)
    drawCircle(muted, gr, gc, style = Stroke(1.5f * dp))
    drawLine(muted.copy(alpha = 0.5f), Offset(gc.x - gr, gc.y), Offset(gc.x + gr, gc.y), strokeWidth = 1 * dp)
    drawLine(muted.copy(alpha = 0.5f), Offset(gc.x, gc.y - gr), Offset(gc.x, gc.y + gr), strokeWidth = 1 * dp)
    var bx = f.up[0].toFloat() * gr * 2.2f
    var by = -f.up[1].toFloat() * gr * 2.2f
    val bl = sqrt(bx * bx + by * by)
    val maxR = gr - 8 * dp
    if (bl > maxR) { bx = bx / bl * maxR; by = by / bl * maxR }
    val level = abs(pitch) < 2 && abs(roll) < 2
    drawCircle(if (level) success else gold, 8 * dp, Offset(gc.x + bx, gc.y + by))
    compassText(paint, "الميل ${Fmt.deg(pitch, 0)}", gc.x + gr + 50 * dp, gc.y + 5 * dp, 13.sp.toPx(), ink)
    compassText(paint, "الدوران ${Fmt.deg(roll, 0)}", gc.x - gr - 50 * dp, gc.y + 5 * dp, 13.sp.toPx(), ink)
}

/** بوصلة ثلاثية الأبعاد: قرص منظوري يميل مع الهاتف وأوتاد للقبلة والشمس والقمر */
@Composable
fun Compass3DScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val oState = rememberOrientation()
    val b = rememberCompassBodies()
    val aligned = compassEffects(oState, b)
    val noSensor by remember { derivedStateOf { !oState.value.available } }
    val lowAcc by remember { derivedStateOf { oState.value.available && oState.value.accuracy < 2 } }
    val paint = remember { newCompassPaint() }

    ScreenScaffold(
        title = "بوصلة ثلاثية الأبعاد", onBack = env.back, subtitle = "تتبّع ميلان الهاتف",
        actions = {
            ShareAction {
                "اتجاه القبلة: ${Fmt.deg(b.qibla)} (${Fmt.direction(b.qibla)})\nالشمس: سمت ${Fmt.deg(b.sun.az)} وارتفاع ${Fmt.deg(b.sun.alt)}\n" +
                    "القمر: سمت ${Fmt.deg(b.moon.az)} وارتفاع ${Fmt.deg(b.moon.alt)}"
            }
        }
    ) {
        ScreenList {
            item {
                Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).aspectRatio(0.95f)) {
                    val s = oState.value
                    val heading = if (s.available) s.azimuthTrue(b.decl) else 0.0
                    c3Draw(
                        heading, if (s.available) s.pitch else 0.0, if (s.available) s.roll else 0.0, s.available,
                        aligned.value && s.available, b, o.ink, o.muted, o.gold, o.danger, o.success, paint
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Pill("القبلة", o.gold)
                    Pill(if (b.sun.alt > 0) "الشمس ↑" else "الشمس ↓", Color(0xFFFFB300))
                    Pill(if (b.moon.alt > 0) "القمر ↑" else "القمر ↓", o.muted)
                }
            }
            if (noSensor) item {
                GlassCard(title = "لا يتوفر حساس اتجاه") {
                    Text("لا يحتوي هذا الجهاز على حساس مغناطيسي أو متجه دوران، فيُعرض القرص ثابتاً والشمال في الأعلى.", color = o.muted)
                }
            }
            if (lowAcc) item {
                GlassCard(title = "المعايرة مطلوبة") {
                    Text("دقة الحساس منخفضة: حرّك الهاتف على شكل 8 في الهواء عدة مرات.", color = o.warning)
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
            item { Note("القرص أفقي دائماً كما في الواقع ويظهر مائلاً حين تميل بالهاتف. وتد الشمس والقمر يرتفع للأعلى فوق الأفق وينزل للأسفل تحته (الطول يتناسب مع الارتفاع)، ووتد القبلة بطول ثابت. الفقاعة تتجه نحو الجهة الأعلى من الهاتف وتصبح خضراء عند الاستواء.") }
        }
    }
}
