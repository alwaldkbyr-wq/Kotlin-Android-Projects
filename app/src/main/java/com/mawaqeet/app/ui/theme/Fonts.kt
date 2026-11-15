package com.mawaqeet.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mawaqeet.app.R

/**
 * خطوط «ثمانية» العشرة:
 * - display (ثمانية سيرف ديسبلاي): العناوين، والأرقام الكبيرة، والساعة، وأسماء الصلوات البارزة.
 * - text (ثمانية سيرف نص): النصوص، والقوائم، والأزرار، والملاحظات (مصمَّم للأحجام الصغيرة).
 */
object AppFonts {
    val display: FontFamily = FontFamily(
        Font(R.font.thmanyah_serif_display_light, FontWeight.Light),
        Font(R.font.thmanyah_serif_display_regular, FontWeight.Normal),
        Font(R.font.thmanyah_serif_display_medium, FontWeight.Medium),
        Font(R.font.thmanyah_serif_display_bold, FontWeight.SemiBold),
        Font(R.font.thmanyah_serif_display_bold, FontWeight.Bold),
        Font(R.font.thmanyah_serif_display_black, FontWeight.ExtraBold),
        Font(R.font.thmanyah_serif_display_black, FontWeight.Black)
    )
    val text: FontFamily = FontFamily(
        Font(R.font.thmanyah_serif_text_light, FontWeight.Light),
        Font(R.font.thmanyah_serif_text_regular, FontWeight.Normal),
        Font(R.font.thmanyah_serif_text_medium, FontWeight.Medium),
        Font(R.font.thmanyah_serif_text_bold, FontWeight.SemiBold),
        Font(R.font.thmanyah_serif_text_bold, FontWeight.Bold),
        Font(R.font.thmanyah_serif_text_black, FontWeight.ExtraBold),
        Font(R.font.thmanyah_serif_text_black, FontWeight.Black)
    )
    val body: FontFamily = text
}

/** خطوط «ثمانية» للرسم المباشر على Canvas (الأقراص والخرائط والبوصلة) — تُحمَّل مرة عند بدء التطبيق */
object CanvasFonts {
    @Volatile var text: android.graphics.Typeface = android.graphics.Typeface.DEFAULT
        private set
    @Volatile var display: android.graphics.Typeface = android.graphics.Typeface.DEFAULT_BOLD
        private set

    fun init(context: android.content.Context) {
        runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.thmanyah_serif_text_medium) }.getOrNull()?.let { text = it }
        runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.thmanyah_serif_display_bold) }.getOrNull()?.let { display = it }
    }
}
