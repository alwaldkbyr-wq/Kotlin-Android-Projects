package com.mawaqeet.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.mawaqeet.app.data.AppSettings
import com.mawaqeet.app.ui.theme.AppPalette

/**
 * أيقونة التطبيق تتبع الثيم: لكل ثيم اسم بديل (activity-alias) بأيقونته، ويُفعَّل واحد فقط.
 * يُطبَّق التغيير عند مغادرة التطبيق حتى لا تُعيد الشاشة الرئيسية للهاتف تحميله أثناء الاستعمال.
 */
object AppIcon {
    private val all = listOf("Space", "Moonlight", "Nebula", "Aurora", "Mars", "Galaxy", "Dawn")

    fun aliasFor(s: AppSettings): String = when (AppPalette.of(s.palette)) {
        AppPalette.DEFAULT -> if (s.dark) "Space" else "Moonlight"
        AppPalette.NEBULA -> "Nebula"
        AppPalette.AURORA -> "Aurora"
        AppPalette.MARS -> "Mars"
        AppPalette.GALAXY -> "Galaxy"
        AppPalette.MOONLIGHT -> "Moonlight"
        AppPalette.DAWN -> "Dawn"
    }

    fun apply(context: Context, s: AppSettings) {
        val pm = context.packageManager
        val want = aliasFor(s)
        fun cn(a: String) = ComponentName(context.packageName, "com.mawaqeet.app.Icon$a")
        runCatching {
            val wantCn = cn(want)
            val enabled = pm.getComponentEnabledSetting(wantCn)
            val isDefaultOn = want == "Space" && enabled == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || isDefaultOn) {
                // المطلوب مفعّل؛ نتأكد فقط أن غيره معطّل
                all.filter { it != want }.forEach { a ->
                    val st = pm.getComponentEnabledSetting(cn(a))
                    if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) pm.setComponentEnabledSetting(cn(a), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
                return
            }
            // فعّل الجديد أولاً ثم عطّل البقية (لا تبقى الشاشة بلا أيقونة)
            pm.setComponentEnabledSetting(wantCn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            all.filter { it != want }.forEach { a ->
                pm.setComponentEnabledSetting(cn(a), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
