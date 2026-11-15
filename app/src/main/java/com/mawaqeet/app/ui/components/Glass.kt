package com.mawaqeet.app.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalGlass
import com.mawaqeet.app.ui.theme.LocalMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * أسلوب «الزجاج السائل» (Liquid Glass):
 * خلفية مائية متحركة ببطء، وأسطح زجاجية شفافة بلمعة وحافة مضيئة،
 * وتموّجات كقطرة الماء عند اللمس، وانتقالات تطفو كسطح الماء.
 */

/** خلفية مائية: بقع لونية تتحرك ببطء شديد مع نقش هندسي خافت فوقها */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    val perf = LocalPerf.current
    // على الأجهزة الضعيفة يبقى الماء ساكناً: لا يُعاد رسم الخلفية ولا لمعان الأسطح باستمرار
    val motion = LocalMotion.current && perf.liveWater
    val o = AppTheme.ornament
    Box(modifier.fillMaxSize()) {
        // حركة الماء بطيئة جداً (دورة كل 36 ثانية)، فيكفي تحديثها نحو 12 مرة في الثانية
        // بدلاً من كل إطار: نفس الانسياب بصرياً مع استهلاك أقل بكثير للمعالج والبطارية
        val phase = remember { mutableFloatStateOf(0.8f) }
        if (motion) {
            LaunchedEffect(Unit) {
                val start = System.nanoTime()
                while (isActive) {
                    // ينتظر إطاراً فعلياً: يتوقف تلقائياً حين يكون التطبيق في الخلفية فلا يستهلك البطارية
                    withFrameNanos { }
                    val t = (System.nanoTime() - start) / 1_000_000_000.0
                    phase.floatValue = (0.8 + t / 36.0 * 2 * PI).rem(2 * PI).toFloat()
                    delay(perf.waterDelayMs)
                }
            }
        }
        Spacer(Modifier.fillMaxSize().graphicsLayer().drawBehind {
            drawWater(g.bgTop, g.bgBottom, g.blob1, g.blob2, g.blob3, phase.floatValue)
        })
        // مدارات خافتة (كأسطرلاب) ثابتة، ونجوم تتلألأ مع الطور
        Spacer(Modifier.fillMaxSize().graphicsLayer().orbitRings(o.gold.copy(alpha = 0.12f)))
        val stars = remember { cosmicStars(420) }
        val starColor = o.pattern
        Spacer(Modifier.fillMaxSize().drawBehind { drawCosmicStars(stars, starColor, phase.floatValue) })
        CompositionLocalProvider(LocalLightPhase provides phase) { content() }
    }
}

/** ماء ساكن (للنوافذ المنبثقة) */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStillWater(
    top: Color, bottom: Color, b1: Color, b2: Color, b3: Color
) = drawWater(top, bottom, b1, b2, b3, 0.8f)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWater(
    top: Color, bottom: Color, b1: Color, b2: Color, b3: Color, p: Float
) {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val w = size.width
    val h = size.height
    val m = size.maxDimension
    fun blob(c: Color, cx: Float, cy: Float, r: Float) =
        drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
    blob(b1, w * (0.85f + 0.10f * sin(p)), h * (0.10f + 0.06f * cos(p * 2)), m * 0.62f)
    blob(b2, w * (0.10f + 0.12f * cos(p)), h * (0.88f + 0.05f * sin(p * 2)), m * 0.66f)
    blob(b3, w * (0.45f + 0.18f * sin(p + 1.7f)), h * (0.50f + 0.10f * cos(p + 0.6f)), m * 0.40f)
}

/** طور الضوء المشترك (يتبع حركة الماء) حتى تتحرك انعكاسات كل الأسطح الزجاجية معاً */
val LocalLightPhase = staticCompositionLocalOf<FloatState?> { null }

/**
 * سطح زجاجي سائل:
 * تعبئة شفافة متدرجة + نقطة لمعان تنساب ببطء مع حركة الماء + خط لمعان مائل كانعكاس على الزجاج +
 * حافة ضوئية ساطعة في الأعلى + انكسار ملوّن خفيف في الأسفل.
 * [tint] يلوّن الزجاج بلون خفيف (مثل الزجاج الملوّن في النوافذ).
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    rim: Color = Color.Unspecified,
    shadow: Boolean = true
): Modifier = composed {
    val g = LocalGlass.current
    val tinted = tint != Color.Unspecified
    if (!LocalPerf.current.richGlass) {
        // نسخة خفيفة للأجهزة الضعيفة: تعبئة متدرجة وحافة مضيئة فقط (دون لمعان متحرك ولا ظلال)
        val top = if (!tinted) g.fill else androidx.compose.ui.graphics.lerp(tint, Color.White, 0.10f).copy(alpha = 1f)
        val bottom = if (!tinted) g.fillLow else androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.18f).copy(alpha = 1f)
        val rimC = if (rim != Color.Unspecified) rim else g.rim.copy(alpha = g.rim.alpha * 0.8f)
        return@composed this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, rimC, shape)
    }
    val light = LocalLightPhase.current
    // الأزرار الملوّنة: لون مصمت واضح (فاتح قليلاً في الأعلى وأعمق في الأسفل) بدل لون شبه شفاف باهت
    val fillTop = if (!tinted) g.fill else androidx.compose.ui.graphics.lerp(tint, Color.White, 0.10f).copy(alpha = 1f)
    val fillBottom = if (!tinted) g.fillLow else androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.18f).copy(alpha = 1f)
    val rimBrush = if (rim != Color.Unspecified) Brush.linearGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.5f), rim))
    else Brush.linearGradient(listOf(g.rim, g.rimSoft, g.rimSoft, g.rim.copy(alpha = g.rim.alpha * 0.55f)))
    // فوق اللون: لمعان بنفس الشكل لكن أخف حتى لا يغسل اللون ويجعله باهتاً
    val gloss = if (tinted) g.gloss.copy(alpha = g.gloss.alpha * 0.9f) else g.gloss
    val sheen = if (tinted) g.sheen.copy(alpha = g.sheen.alpha * 0.4f) else g.sheen
    this
        .then(if (!shadow) Modifier else Modifier.drawBehind {
            // ظل مائي ناعم أسفل السطح (بدلاً من ظل النظام الذي يظهر عبر الزجاج)
            val o = 6.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.Transparent, g.shadow), startY = size.height * 0.4f, endY = size.height + o),
                topLeft = Offset(o, o), size = androidx.compose.ui.geometry.Size(size.width - 2 * o, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(28.dp.toPx(), size.height / 2f))
            )
        })
        .clip(shape)
        .drawBehind {
            val w = size.width
            val h = size.height
            val ph = light?.floatValue ?: 0.8f
            drawRect(Brush.verticalGradient(listOf(fillTop, fillBottom)))
            // توهّج سديمي خافت ينساب ببطء داخل السطح
            drawRect(
                Brush.radialGradient(
                    listOf(sheen, sheen.copy(alpha = 0f)),
                    center = Offset(w * (0.75f + 0.18f * sin(ph)), -h * 0.25f),
                    radius = size.maxDimension * 0.8f
                )
            )
            // ضوء نجمي من الزاوية السفلى
            drawRect(
                Brush.radialGradient(
                    listOf(g.caustic.copy(alpha = g.caustic.alpha * 3f), Color.Transparent),
                    center = Offset(w * (0.08f + 0.05f * cos(ph)), h * 1.05f),
                    radius = size.maxDimension * 0.55f
                )
            )
        }
        // حافة رفيعة متدرجة: مضيئة في الأعلى وتخفت نحو الأسفل
        .border(1.dp, Brush.verticalGradient(listOf(g.edgeLight.copy(alpha = g.edgeLight.alpha * 0.9f), rimBrushColor(g, rim), g.rimSoft)), shape)
}

private fun rimBrushColor(g: com.mawaqeet.app.ui.theme.Glass, rim: Color) = if (rim != Color.Unspecified) rim.copy(alpha = rim.alpha * 0.6f) else g.rim.copy(alpha = g.rim.alpha * 0.5f)

/** تموّج كقطرة ماء عند اللمس: حلقتان تتسعان وتتلاشيان من نقطة اللمس */
data class WaterIndication(val color: Color, val rich: Boolean = true) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = WaterNode(interactionSource, color, rich)
}

private class Drop(val at: Offset) { val p = Animatable(0f) }

private class WaterNode(private val source: InteractionSource, private val color: Color, private val rich: Boolean) :
    Modifier.Node(), DrawModifierNode {
    private val drops = mutableStateListOf<Drop>()
    private val press = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> {
                        launch { press.animateTo(1f, tween(120)) }
                        // الأجهزة الضعيفة: إضاءة الضغط فقط دون حلقات التموّج
                        if (!rich) return@collect
                        val d = Drop(i.pressPosition)
                        drops += d
                        launch {
                            d.p.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
                            drops.remove(d)
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        launch { press.animateTo(0f, tween(420)) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        clipRect {
            val pr = press.value
            if (pr > 0f) drawRect(color.copy(alpha = 0.08f * pr))
            val maxR = size.maxDimension * 1.05f
            for (d in drops) {
                val p = d.p.value
                val fade = 1f - p
                val r = maxR * p
                drawCircle(color.copy(alpha = 0.10f * fade), r, d.at)
                drawCircle(color.copy(alpha = 0.40f * fade), r, d.at, style = Stroke(width = 1.dp.toPx() + 5.dp.toPx() * fade))
                drawCircle(color.copy(alpha = 0.22f * fade), r * 0.6f, d.at, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

/**
 * غلاف الصفحة أثناء الانتقال: تطفو الصفحة الجديدة من ضباب خفيف كأنها تخرج من الماء
 * (التمويه متاح من أندرويد 12 فما فوق، وما دونه يكتفي بالتلاشي والتكبير).
 */
@Composable
fun AnimatedContentScope.LiquidPage(content: @Composable () -> Unit) {
    val motion = LocalMotion.current && LocalPerf.current.blurPages
    if (!motion || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { content(); return }
    val blur by transition.animateFloat(
        transitionSpec = { tween(420, easing = FastOutSlowInEasing) }, label = "liquidBlur"
    ) { if (it == EnterExitState.Visible) 0f else 16f }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val px = blur.dp.toPx()
            renderEffect = if (px > 0.5f) BlurEffect(px, px, TileMode.Decal) else null
        }
    ) { content() }
}


/* ───────────────────────── عناصر الخلفية الكونية ───────────────────────── */

class CosmicStar(val x: Float, val y: Float, val r: Float, val a: Float, val tw: Float, val ph: Float)

/** نجوم بمواقع نسبية ثابتة (0..1) وأحجام وسرعات تلألؤ مختلفة */
fun cosmicStars(n: Int): List<CosmicStar> {
    val rnd = java.util.Random(11L)
    return List(n) {
        val m = rnd.nextFloat()
        CosmicStar(rnd.nextFloat(), rnd.nextFloat(), 0.35f + m * m * m * 1.9f, 0.18f + m * 0.8f, 0.4f + rnd.nextFloat() * 2.2f, rnd.nextFloat() * 6.28f)
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCosmicStars(stars: List<CosmicStar>, color: Color, phase: Float) {
    val dp = density
    for (s in stars) {
        val tw = 0.65f + 0.35f * sin(phase * 6f * s.tw + s.ph)
        val a = (s.a * tw * color.alpha).coerceIn(0f, 1f)
        val c = Offset(s.x * size.width, s.y * size.height)
        drawCircle(color.copy(alpha = a), s.r * dp * 0.75f, c)
        if (s.r > 1.9f) {
            // هالة ووميض صليبي للنجوم الأسطع
            drawCircle(color.copy(alpha = a * 0.18f), s.r * dp * 2.6f, c)
            val l = s.r * dp * 3.2f
            drawLine(color.copy(alpha = a * 0.5f), Offset(c.x - l, c.y), Offset(c.x + l, c.y), 0.7f)
            drawLine(color.copy(alpha = a * 0.5f), Offset(c.x, c.y - l), Offset(c.x, c.y + l), 0.7f)
        }
    }
}

/** مدارات متحدة المركز مائلة (أسطرلاب خافت) مع نقاط كواكب صغيرة عليها */
fun Modifier.orbitRings(color: Color): Modifier = drawBehind {
    val c = Offset(size.width * 1.05f, size.height * 0.02f)
    val base = size.maxDimension
    val sw = 1.dp.toPx()
    listOf(0.35f, 0.55f, 0.78f, 1.05f).forEachIndexed { i, k ->
        val r = base * k
        drawCircle(color.copy(alpha = color.alpha * (1f - i * 0.18f)), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        val ang = (2.3 + i * 0.42)
        val p = Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat())
        drawCircle(color.copy(alpha = (color.alpha * 3.2f).coerceAtMost(1f)), (2.5f + i).dp.toPx() * 0.8f, p)
    }
    // دائرة سفلية معاكسة
    val c2 = Offset(-size.width * 0.15f, size.height * 1.02f)
    drawCircle(color.copy(alpha = color.alpha * 0.7f), base * 0.42f, c2, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
}

