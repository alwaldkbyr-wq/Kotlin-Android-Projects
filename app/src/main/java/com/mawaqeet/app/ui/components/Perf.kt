package com.mawaqeet.app.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File

/*
 * التكيّف مع قوة الجهاز: يُقدَّر مستوى الهاتف (ضعيف / متوسط / قوي) من الذاكرة وعدد الأنوية وسرعتها
 * وإصدار أندرويد، وتُخفَّف المؤثرات الثقيلة على الأجهزة الضعيفة فقط. الأجهزة القوية تبقى بكامل المؤثرات.
 * ويستطيع المستخدم فرض «جودة كاملة» أو «خفيف» من الإعدادات.
 */

enum class PerfTier(val label: String) { LOW("ضعيف"), MID("متوسط"), HIGH("قوي") }

/** ما يُفعَّل من المؤثرات */
@Immutable
data class PerfConfig(
    /** الخلفية المائية المتحركة (ومعها حركة اللمعان على كل الأسطح الزجاجية) */
    val liveWater: Boolean,
    /** الفاصل بين تحديثات حركة الماء (أكبر = أخف) */
    val waterDelayMs: Long,
    /** لمعان الزجاج وانعكاساته وظلاله */
    val richGlass: Boolean,
    /** تمويه الصفحات أثناء الانتقال (أندرويد 12+) */
    val blurPages: Boolean,
    /** تموّج قطرة الماء عند اللمس (وإلا إضاءة خفيفة فقط) */
    val richRipple: Boolean,
    /** حركة إسقاط البطاقات */
    val dropIn: Boolean,
    /** انتقالات الصفحات المائية الكاملة (وإلا تلاشٍ سريع) */
    val richTransitions: Boolean
) {
    companion object {
        val HIGH = PerfConfig(true, 80, true, true, true, true, true)
        val MID = PerfConfig(true, 160, true, false, true, true, true)
        val LOW = PerfConfig(false, 1000, false, false, false, false, false)

        /** mode: 0 تلقائي حسب الجهاز، 1 جودة كاملة، 2 خفيف */
        fun of(mode: Int, tier: PerfTier) = when (mode) {
            1 -> HIGH
            2 -> LOW
            else -> when (tier) { PerfTier.HIGH -> HIGH; PerfTier.MID -> MID; PerfTier.LOW -> LOW }
        }
    }
}

val LocalPerf = staticCompositionLocalOf { PerfConfig.HIGH }

object DevicePerf {
    @Volatile private var cached: PerfTier? = null

    fun tier(context: Context): PerfTier = cached ?: detect(context).also { cached = it }

    private fun maxCpuMhz(): Int = runCatching {
        File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }.orEmpty()
            .mapNotNull { File(it, "cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toIntOrNull() }
            .maxOrNull()?.div(1000) ?: 0
    }.getOrDefault(0)

    private fun detect(context: Context): PerfTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return PerfTier.MID
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramGb = mi.totalMem / (1024.0 * 1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val mhz = maxCpuMhz()   // 0 إن تعذرت القراءة
        val sdk = Build.VERSION.SDK_INT
        return when {
            am.isLowRamDevice || ramGb < 2.6 || cores <= 4 || sdk < Build.VERSION_CODES.P || (mhz in 1..1800) -> PerfTier.LOW
            ramGb < 5.5 || cores < 8 || sdk < Build.VERSION_CODES.R || (mhz in 1..2300) -> PerfTier.MID
            else -> PerfTier.HIGH
        }
    }
}
