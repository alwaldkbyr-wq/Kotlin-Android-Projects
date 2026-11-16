package com.mawaqeet.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.WorshipStore
import com.mawaqeet.app.ui.components.ChipsSelector
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.glass
import com.mawaqeet.app.ui.theme.AppFonts
import com.mawaqeet.app.ui.theme.AppTheme
import java.util.Locale

private class TsStep(val text: String, val target: Int)
private class TsPreset(val key: String, val label: String, val steps: List<TsStep>)

private const val TS_TAHLIL = "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير"

private val TS_PRESETS = listOf(
    TsPreset("after", "تسبيح ما بعد الصلاة", listOf(
        TsStep("سبحان الله", 33), TsStep("الحمد لله", 33), TsStep("الله أكبر", 33), TsStep(TS_TAHLIL, 1)
    )),
    TsPreset("after34", "ما بعد الصلاة (33-33-34)", listOf(
        TsStep("سبحان الله", 33), TsStep("الحمد لله", 33), TsStep("الله أكبر", 34)
    )),
    TsPreset("istighfar", "أستغفر الله", listOf(TsStep("أستغفر الله", 100))),
    TsPreset("hawqala", "لا حول ولا قوة إلا بالله", listOf(TsStep("لا حول ولا قوة إلا بالله", 100))),
    TsPreset("salawat", "الصلاة على النبي", listOf(TsStep("اللهم صلِّ على محمد", 100))),
    TsPreset("subhan", "سبحان الله وبحمده", listOf(TsStep("سبحان الله وبحمده", 100)))
)

private fun tsVibrate(ctx: Context, ms: Long) {
    runCatching {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** المسبحة الإلكترونية */
@Composable
fun TasbihScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = WorshipStore.get(ctx)
    store.version

    // إبقاء الشاشة مضاءة
    val view = LocalView.current
    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    var presetKey by rememberSaveable { mutableStateOf("after") }
    var customText by rememberSaveable { mutableStateOf("") }
    var customTarget by rememberSaveable { mutableStateOf("100") }
    var pos by rememberSaveable { mutableIntStateOf(0) }

    val steps: List<TsStep> = if (presetKey == "custom") {
        listOf(TsStep(customText.ifBlank { "ذكر مخصّص" }, (customTarget.toIntOrNull() ?: 33).coerceIn(1, 100000)))
    } else TS_PRESETS.first { it.key == presetKey }.steps
    val total = steps.sumOf { it.target }
    if (pos > total) pos = 0

    // تحديد الخطوة الحالية والعدد داخلها
    var stepIndex = 0
    var inStep = pos
    while (stepIndex < steps.size - 1 && inStep >= steps[stepIndex].target) { inStep -= steps[stepIndex].target; stepIndex++ }
    val done = pos >= total
    val step = steps[stepIndex]
    val shown = if (done) step.target else inStep

    fun tap() {
        var p = pos
        if (p >= total) p = 0 // بدء جولة جديدة
        p++
        pos = p
        store.tasbihAdd(env.today, 1)
        // انتهاء خطوة أو الهدف كله: اهتزاز أقوى
        var acc = 0
        var boundary = false
        for (s in steps) { acc += s.target; if (p == acc) boundary = true }
        if (p == total) tsVibrate(ctx, 220)
        else if (boundary) tsVibrate(ctx, 90)
        else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    ScreenScaffold(title = "المسبحة", onBack = env.back, subtitle = "عدّاد ذكر بلمسة") {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                ChipsSelector(
                    options = TS_PRESETS.associate { it.key to it.label } + ("custom" to "ذكر مخصّص"),
                    selected = presetKey,
                    onSelect = { presetKey = it; pos = 0 }
                )
                if (presetKey == "custom") {
                    Spacer(Modifier.height(8.dp))
                    Field("نص الذكر", customText, { customText = it; pos = 0 }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Field("العدد المستهدف", customTarget, { customTarget = it.filter(Char::isDigit).take(6); pos = 0 }, Modifier.fillMaxWidth(), number = true)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    step.text, Modifier.fillMaxWidth(), color = o.gold, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
                if (steps.size > 1) {
                    Text(
                        "المرحلة ${stepIndex + 1} من ${steps.size} • المجموع $pos/$total",
                        Modifier.fillMaxWidth(), color = o.muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else pos.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTonalButton(
                        onClick = { if (pos > 0) { pos--; store.tasbihAdd(env.today, -1) } },
                        Modifier.weight(1f), nav = true, enabled = pos > 0
                    ) { Text("تراجع (−1)") }
                    GlassOutlinedButton(onClick = { pos = 0 }, Modifier.weight(1f), nav = true) { Text("تصفير") }
                }
                Spacer(Modifier.height(10.dp))
                MetricRow(
                    "إجمالي اليوم" to String.format(Locale.US, "%,d", store.tasbihToday(env.today)),
                    "الإجمالي الكلي" to String.format(Locale.US, "%,d", store.tasbihLifetime())
                )
                Spacer(Modifier.height(8.dp))
            }
            // النصف السفلي كله منطقة لمس
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                    .glass(RoundedCornerShape(32.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    .clickable { tap() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        shown.toString(), color = o.ink, fontFamily = AppFonts.display, fontSize = 110.sp,
                        maxLines = 1, textAlign = TextAlign.Center
                    )
                    Text(
                        if (done) "اكتمل العدد — اللمسة التالية تبدأ جولة جديدة" else "من ${step.target}",
                        color = o.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
