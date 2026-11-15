package com.mawaqeet.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.ui.theme.LocalMotion
import kotlinx.coroutines.delay

/** مفتاح يعيد تشغيل حركة إسقاط البطاقات عند تغيّره (مثلاً عند تبديل التبويب) */
val LocalDropTrigger = compositionLocalOf<Any?> { Unit }

/**
 * حركة إسقاط: تسقط البطاقة من الأعلى بنطّة خفيفة، وتتأخر كل بطاقة بحسب موضعها
 * على الشاشة فيظهر التتابع من الأعلى إلى الأسفل.
 * البطاقات التي تظهر لاحقاً بالتمرير تظهر فوراً دون حركة.
 */
fun Modifier.dropIn(trigger: Any? = Unit): Modifier = composed {
    // الأجهزة الضعيفة: تظهر البطاقات مباشرة دون قياس موضع ولا طبقة رسم لكل بطاقة
    if (!LocalPerf.current.dropIn) return@composed this
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dropPx = with(density) { 30.dp.toPx() }
    var top by remember { mutableFloatStateOf(-1f) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(trigger, motion, top) {
        if (!motion || top > screenH) {
            p.snapTo(1f)
        } else if (top >= 0f) {
            p.snapTo(0f)
            delay((top / 6f).toLong().coerceIn(0L, 380L))
            p.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 170f))
        }
    }
    // ضمان الظهور حتى لو لم يصل قياس الموضع لأي سبب
    LaunchedEffect(Unit) {
        delay(900)
        if (top < 0f) p.snapTo(1f)
    }
    this
        .onGloballyPositioned { if (top < 0f) top = it.positionInWindow().y.coerceAtLeast(0f) }
        .graphicsLayer {
            val v = p.value
            // تطفو البطاقة من الأسفل كفقاعة تصعد إلى سطح الماء
            alpha = (v * 2.2f).coerceIn(0f, 1f)
            translationY = (1f - v) * dropPx
            val s = 0.95f + 0.05f * v
            scaleX = s
            scaleY = s
        }
}
