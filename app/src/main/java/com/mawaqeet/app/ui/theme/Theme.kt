package com.mawaqeet.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ألوان أساسية */
object Brand {
    val Navy = Color(0xFF17406A)
    val NavyDark = Color(0xFF0B2440)
    val Teal = Color(0xFF2E7480)
    val TealLight = Color(0xFF5E9EA0)
    val Gold = Color(0xFFCFB881)
    val GoldLight = Color(0xFFEADCB3)
    val GoldDeep = Color(0xFF9C7F43)
    val Parchment = Color(0xFFFAF7F0)
    val ParchmentDeep = Color(0xFFEFE8D8)
    val Ink = Color(0xFF1A2633)
    val Ruby = Color(0xFFA8323E)
    val NightBg = Color(0xFF09141F)
    val NightSurface = Color(0xFF111F2D)
}

/** ألوان إضافية للزخرفة تتبع الوضع الفاتح/الداكن */
@Immutable
data class Ornament(
    val gold: Color,
    val goldSoft: Color,
    val headerTop: Color,
    val headerBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val pattern: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val info: Color,
    val ink: Color,
    val muted: Color,
    val chip: Color,
    val onChip: Color,
    val band: Color
)

private val LightOrnament = Ornament(
    gold = Color(0xFFA86A12), goldSoft = Color(0xFFF1D7A6),
    headerTop = Color(0xFF1B2250), headerBottom = Color(0xFF3B4CCA),
    card = Color(0xFFFFFFFF), cardBorder = Color(0xFFD5DBEE),
    pattern = Color(0xFF3B4CCA).copy(alpha = 0.10f),
    success = Color(0xFF1E8A5A), danger = Color(0xFFC0392B), warning = Color(0xFFB7791F), info = Color(0xFF2A6FB0),
    ink = Color(0xFF151A33), muted = Color(0xFF5B6382), chip = Color(0xFFE8ECF8).copy(alpha = 0.9f), onChip = Color(0xFF1F2547),
    band = Color(0xFF1B2250)
)

private val DarkOrnament = Ornament(
    gold = Color(0xFFF5C26B), goldSoft = Color(0xFFFFE3AE),
    headerTop = Color(0xFF050814), headerBottom = Color(0xFF151D44),
    card = Color(0xFF141B38), cardBorder = Color(0xFF8FA8FF).copy(alpha = 0.22f),
    pattern = Color(0xFFFFFFFF).copy(alpha = 0.7f),
    success = Color(0xFF6EE7B7), danger = Color(0xFFFF8A80), warning = Color(0xFFFFC46B), info = Color(0xFF7CC8FF),
    ink = Color(0xFFEEF1FF), muted = Color(0xFFA9B1D6), chip = Color(0xFF222B52).copy(alpha = 0.92f), onChip = Color(0xFFDCE3FF),
    band = Color(0xFF0B1030)
)

val LocalOrnament = staticCompositionLocalOf { LightOrnament }

/** ألوان «الزجاج السائل»: خلفية مائية وأسطح زجاجية شفافة */
@Immutable
data class Glass(
    val bgTop: Color,
    val bgBottom: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
    val fill: Color,
    val fillLow: Color,
    val rim: Color,
    val rimSoft: Color,
    val sheen: Color,
    val shadow: Color,
    val lens: Color,
    val ripple: Color,
    val gloss: Color,      // خط اللمعان المائل
    val edgeLight: Color,  // الحافة الضوئية العليا
    val caustic: Color     // الانكسار الملوّن عند الحافة السفلى
)

// الزجاج هنا «مُعتَّم» بدرجة كافية حتى لا يختلط النص بما خلفه
private val LightGlass = Glass(
    bgTop = Color(0xFFF2F4FB), bgBottom = Color(0xFFDCE2F3),
    blob1 = Color(0xFF8C9BFF).copy(alpha = 0.40f),
    blob2 = Color(0xFF7FD6F5).copy(alpha = 0.32f),
    blob3 = Color(0xFFFFC98A).copy(alpha = 0.30f),
    fill = Color.White.copy(alpha = 0.90f), fillLow = Color(0xFFF4F6FD).copy(alpha = 0.86f),
    rim = Color.White.copy(alpha = 0.95f), rimSoft = Color(0xFFB8C2E6).copy(alpha = 0.45f),
    sheen = Color.White.copy(alpha = 0.40f), shadow = Color(0xFF1B2250).copy(alpha = 0.12f),
    lens = Color(0xFF3B4CCA).copy(alpha = 0.14f), ripple = Color(0xFF3B4CCA),
    gloss = Color.White.copy(alpha = 0.18f), edgeLight = Color.White,
    caustic = Color(0xFF3B4CCA).copy(alpha = 0.05f)
)

private val DarkGlass = Glass(
    bgTop = Color(0xFF04060F), bgBottom = Color(0xFF0A1030),
    blob1 = Color(0xFF4B3BC4).copy(alpha = 0.42f),
    blob2 = Color(0xFF0E7490).copy(alpha = 0.36f),
    blob3 = Color(0xFF9D3B8A).copy(alpha = 0.26f),
    fill = Color(0xFF1A2247).copy(alpha = 0.86f), fillLow = Color(0xFF111735).copy(alpha = 0.90f),
    rim = Color(0xFFB4C3FF).copy(alpha = 0.28f), rimSoft = Color.White.copy(alpha = 0.05f),
    sheen = Color(0xFF9FB4FF).copy(alpha = 0.10f), shadow = Color.Black.copy(alpha = 0.35f),
    lens = Color(0xFF7CC8FF).copy(alpha = 0.16f), ripple = Color(0xFF7CC8FF),
    gloss = Color.White.copy(alpha = 0.05f), edgeLight = Color(0xFFCFD8FF).copy(alpha = 0.40f),
    caustic = Color(0xFF7CC8FF).copy(alpha = 0.05f)
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B4CCA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF101A5C),
    secondary = Brand.GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E7B8),
    onSecondaryContainer = Color(0xFF3A2E00),
    tertiary = Brand.Teal,
    tertiaryContainer = Color(0xFFCDEDEF),
    error = Brand.Ruby,
    errorContainer = Color(0xFFF6DADC),
    onErrorContainer = Color(0xFF5C0F17),
    background = Color(0xFFEEF1FA),
    onBackground = Color(0xFF151A33),
    surface = Color(0xFFEEF1FA),
    onSurface = Color(0xFF151A33),
    surfaceVariant = Color(0xFFEDE5D5),
    onSurfaceVariant = Color(0xFF6E685C),
    surfaceContainer = Color(0xFFF5F0E4),
    surfaceContainerHigh = Color(0xFFF0EADB),
    surfaceContainerHighest = Color(0xFFEAE2D0),
    surfaceContainerLow = Color(0xFFFCFAF5),
    outline = Color(0xFFB9A983),
    outlineVariant = Color(0xFFE2D8BF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC8FF),
    onPrimary = Color(0xFF04122A),
    primaryContainer = Color(0xFF1E2A5E),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFF5C26B),
    onSecondary = Color(0xFF2B2100),
    secondaryContainer = Color(0xFF4A3B00),
    onSecondaryContainer = Color(0xFFEADCB3),
    tertiary = Color(0xFF8AC9CF),
    tertiaryContainer = Color(0xFF1E4F57),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF6E1A22),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF070B1C),
    onBackground = Color(0xFFEEF1FF),
    surface = Color(0xFF070B1C),
    onSurface = Color(0xFFEEF1FF),
    surfaceVariant = Color(0xFF22343D),
    onSurfaceVariant = Color(0xFFC3BBA6),
    surfaceContainer = Color(0xFF14232C),
    surfaceContainerHigh = Color(0xFF192B35),
    surfaceContainerHighest = Color(0xFF1F323D),
    surfaceContainerLow = Color(0xFF101D25),
    outline = Color(0xFF8C7F5A),
    outlineVariant = Color(0xFF34464F)
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.f(family: androidx.compose.ui.text.font.FontFamily) = copy(fontFamily = family)
    val d = AppFonts.display
    val t = AppFonts.text
    return Typography(
        displayLarge = base.displayLarge.f(d).copy(fontWeight = FontWeight.Black),
        displayMedium = base.displayMedium.f(d).copy(fontWeight = FontWeight.Black),
        displaySmall = base.displaySmall.f(d).copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.f(d).copy(fontWeight = FontWeight.Black, fontSize = 34.sp),
        headlineMedium = base.headlineMedium.f(d).copy(fontWeight = FontWeight.Black, fontSize = 30.sp),
        headlineSmall = base.headlineSmall.f(d).copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.f(d).copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.f(d).copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
        titleSmall = base.titleSmall.f(d).copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodyLarge = base.bodyLarge.f(t).copy(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 27.sp),
        bodyMedium = base.bodyMedium.f(t).copy(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
        bodySmall = base.bodySmall.f(t).copy(fontWeight = FontWeight.Light, fontSize = 13.5.sp, lineHeight = 21.sp),
        labelLarge = base.labelLarge.f(t).copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelMedium = base.labelMedium.f(t).copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = base.labelSmall.f(t).copy(fontWeight = FontWeight.Medium, fontSize = 12.sp)
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** هل الوضع الليلي مفعّل؟ */
val LocalDark = staticCompositionLocalOf { false }

/** هل الحركات مفعّلة؟ (إعداد من المستخدم) */
val LocalMotion = staticCompositionLocalOf { true }

/**
 * ثيمات فلكية: الافتراضي (الفضاء العميق ليلاً / ضوء القمر نهاراً حسب الإعداد) وثيمات تحدد إضاءتها بنفسها.
 */
enum class AppPalette(val key: Int, val label: String, val forcedDark: Boolean?) {
    DEFAULT(0, "الفضاء العميق", null),
    NEBULA(1, "السديم البنفسجي", true),
    AURORA(2, "الشفق القطبي", true),
    MARS(3, "غروب المريخ", true),
    GALAXY(4, "المجرة", true),
    MOONLIGHT(5, "ضوء القمر", false),
    DAWN(6, "الفجر", false);

    companion object {
        fun of(key: Int) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** مجموعة ألوان ثيم كاملة */
private data class ThemeSet(val ornament: Ornament, val glass: Glass, val colors: androidx.compose.material3.ColorScheme)

private fun mix(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
private val Ink0 = Color(0xFF111111)

/** ثيم فاتح من لونين: خلفية فاتحة [bg] ولون رئيسي [primary]، مع زجاج سائل وزخارف ذهبية */
private fun lightSet(bg: Color, base: Color, gold: Color = Color(0xFFB8935A)): ThemeSet {
    // اللون الفاتح جداً (كالتركوازي) يُعمَّق قليلاً حتى يبقى النص الأبيض على الأزرار واضحاً
    val primary = if (base.luminance() > 0.3f) mix(base, Color.Black, 0.22f) else base
    val ink = mix(primary, Ink0, 0.62f)
    val muted = mix(ink, bg, 0.42f)
    val header = mix(primary, Color.Black, 0.35f)
    return ThemeSet(
        LightOrnament.copy(
            gold = gold, goldSoft = mix(gold, Color.White, 0.55f),
            headerTop = header, headerBottom = primary,
            card = mix(bg, Color.White, 0.55f), cardBorder = mix(bg, primary, 0.20f),
            pattern = primary.copy(alpha = 0.07f), info = mix(primary, Ink0, 0.15f),
            ink = ink, muted = muted,
            chip = mix(bg, primary, 0.08f).copy(alpha = 0.72f), onChip = ink, band = header
        ),
        LightGlass.copy(
            bgTop = mix(bg, Color.White, 0.40f), bgBottom = bg,
            blob1 = primary.copy(alpha = 0.22f),
            blob2 = mix(primary, Color.White, 0.45f).copy(alpha = 0.26f),
            blob3 = gold.copy(alpha = 0.26f),
            shadow = primary.copy(alpha = 0.10f), ripple = primary, lens = primary.copy(alpha = 0.14f),
            caustic = primary.copy(alpha = 0.05f)
        ),
        LightColors.copy(
            primary = primary, onPrimary = Color.White,
            primaryContainer = mix(primary, Color.White, 0.80f), onPrimaryContainer = mix(primary, Ink0, 0.6f),
            tertiary = primary, tertiaryContainer = mix(primary, Color.White, 0.85f),
            background = bg, onBackground = ink, surface = bg, onSurface = ink,
            surfaceVariant = mix(bg, primary, 0.08f), onSurfaceVariant = muted,
            surfaceContainer = mix(bg, Color.White, 0.30f), surfaceContainerHigh = mix(bg, primary, 0.04f),
            surfaceContainerHighest = mix(bg, primary, 0.08f), surfaceContainerLow = mix(bg, Color.White, 0.60f),
            outline = mix(bg, primary, 0.45f), outlineVariant = mix(bg, primary, 0.18f)
        )
    )
}

/** ثيم داكن من لونين: خلفية داكنة [bg] ولون مميز [accent] (ذهبي غالباً) */
private fun darkSet(bg: Color, accent: Color, nebula1: Color = accent, nebula2: Color = accent): ThemeSet {
    val ink = mix(accent, Color.White, 0.78f)
    val muted = mix(ink, bg, 0.35f)
    // اللون الرئيسي هو الذهبي نفسه، ونص الأزرار يُختار داكناً أو فاتحاً حسب وضوحه (fixContrast)
    val primary = accent
    return ThemeSet(
        DarkOrnament.copy(
            gold = accent, goldSoft = mix(accent, Color.White, 0.5f),
            headerTop = mix(bg, Color.Black, 0.4f), headerBottom = mix(bg, accent, 0.18f),
            card = mix(bg, Color.White, 0.07f), cardBorder = accent.copy(alpha = 0.30f),
            pattern = accent.copy(alpha = 0.06f), info = accent,
            ink = ink, muted = muted,
            chip = mix(bg, Color.White, 0.12f).copy(alpha = 0.62f), onChip = mix(accent, Color.White, 0.5f),
            band = mix(bg, Color.Black, 0.2f)
        ),
        DarkGlass.copy(
            bgTop = mix(bg, Color.Black, 0.25f), bgBottom = bg,
            blob1 = nebula1.copy(alpha = 0.40f),
            blob2 = nebula2.copy(alpha = 0.30f),
            blob3 = accent.copy(alpha = 0.16f),
            fill = mix(bg, Color.White, 0.10f).copy(alpha = 0.86f), fillLow = mix(bg, Color.White, 0.05f).copy(alpha = 0.90f),
            rim = accent.copy(alpha = 0.28f), lens = accent.copy(alpha = 0.16f),
            ripple = accent, caustic = accent.copy(alpha = 0.05f)
        ),
        DarkColors.copy(
            primary = primary, onPrimary = Color.White,
            primaryContainer = mix(bg, accent, 0.25f), onPrimaryContainer = mix(accent, Color.White, 0.7f),
            secondary = accent, tertiary = accent, tertiaryContainer = mix(bg, accent, 0.25f),
            background = bg, onBackground = ink, surface = bg, onSurface = ink,
            surfaceVariant = mix(bg, Color.White, 0.08f), onSurfaceVariant = muted,
            surfaceContainer = mix(bg, Color.White, 0.04f), surfaceContainerHigh = mix(bg, Color.White, 0.07f),
            surfaceContainerHighest = mix(bg, Color.White, 0.10f), surfaceContainerLow = mix(bg, Color.Black, 0.10f),
            outline = mix(bg, accent, 0.5f), outlineVariant = mix(bg, Color.White, 0.15f)
        )
    )
}

private val NebulaSet = darkSet(Color(0xFF0D0820), Color(0xFFC59BFF), Color(0xFF7A3FD1), Color(0xFFD14FA0))
private val AuroraSet = darkSet(Color(0xFF03141A), Color(0xFF4BE3B0), Color(0xFF0F8F7A), Color(0xFF3B6CD1))
private val MarsSet = darkSet(Color(0xFF170A08), Color(0xFFFF9A6B), Color(0xFFB2412A), Color(0xFF7A3B8F))
private val GalaxySet = darkSet(Color(0xFF07070C), Color(0xFFE8D9A8), Color(0xFF3A3F8F), Color(0xFF8F6A3A))
private val MoonlightSet = lightSet(Color(0xFFEDEFF5), Color(0xFF3B4CCA), Color(0xFF8A6A1C))
private val DawnSet = lightSet(Color(0xFFFFF3EA), Color(0xFFC2572B), Color(0xFF9A5B12))

/** نسبة التباين بين لونين (معيار WCAG: 4.5 للنص العادي، 7 للنص الأساسي المريح) */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** لون النص المناسب فوق خلفية ملوّنة: أبيض أو داكن، أيهما أوضح */
fun readableOn(c: Color): Color {
    val darkText = Color(0xFF141414)
    return if (contrastRatio(Color.White, c) >= contrastRatio(darkText, c)) Color.White else darkText
}

/** يقرّب اللون من الأبيض (في الثيم الداكن) أو الأسود (في الفاتح) حتى يبلغ التباين المطلوب مع كل الخلفيات */
private fun readable(fg: Color, bgs: List<Color>, min: Float, dark: Boolean): Color {
    val target = if (dark) Color.White else Color(0xFF0D0D0D)
    val base = fg.copy(alpha = 1f)
    var c = base
    var t = 0f
    while (t < 1f && bgs.any { contrastRatio(c, it) < min }) {
        t += 0.04f
        c = mix(base, target, t)
    }
    return c.copy(alpha = fg.alpha)
}

/**
 * يضمن وضوح كل النصوص في الثيم: النص الأساسي تباينه 7 على الأقل مع الخلفية والبطاقات،
 * والنص الثانوي 4.5، والألوان الدلالية (ذهبي، نجاح، خطر…) 3.5، ونص الأزرار يُختار تلقائياً.
 */
private fun fixContrast(set: ThemeSet, dark: Boolean): ThemeSet {
    val o = set.ornament
    val g = set.glass
    val c = set.colors
    // البطاقات زجاج نصف شفاف فوق الخلفية المائية: تُقدَّر ألوانها الفعلية
    val bgs = listOf(
        c.background, g.bgTop, g.bgBottom,
        mix(g.bgBottom, g.fill.copy(alpha = 1f), g.fill.alpha),
        mix(g.bgTop, g.fill.copy(alpha = 1f), g.fill.alpha),
        o.card
    )
    fun r(x: Color, min: Float) = readable(x, bgs, min, dark)
    val ink = r(o.ink, 7f)
    val muted = r(o.muted, 4.5f)
    val chipBg = mix(c.background, o.chip.copy(alpha = 1f), o.chip.alpha)
    val ornament = o.copy(
        ink = ink, muted = muted,
        gold = r(o.gold, 3f), info = r(o.info, 3.5f),
        success = r(o.success, 3.5f), danger = r(o.danger, 3.5f), warning = r(o.warning, 3.5f),
        onChip = readable(o.onChip, listOf(chipBg), 4.5f, dark)
    )
    val primary = r(c.primary, 3.5f)
    val secondary = r(c.secondary, 3.5f)
    val colors = c.copy(
        primary = primary, onPrimary = readableOn(primary),
        secondary = secondary, onSecondary = readableOn(secondary),
        onPrimaryContainer = readable(c.onPrimaryContainer, listOf(c.primaryContainer), 4.5f, dark),
        onSecondaryContainer = readable(c.onSecondaryContainer, listOf(c.secondaryContainer), 4.5f, dark),
        error = r(c.error, 3.5f),
        onBackground = ink, onSurface = ink, onSurfaceVariant = muted
    )
    return ThemeSet(ornament, g, colors)
}

@Composable
fun AppTheme(dark: Boolean, palette: AppPalette = AppPalette.DEFAULT, content: @Composable () -> Unit) {
    val raw = when (palette) {
        AppPalette.NEBULA -> NebulaSet
        AppPalette.AURORA -> AuroraSet
        AppPalette.MARS -> MarsSet
        AppPalette.GALAXY -> GalaxySet
        AppPalette.MOONLIGHT -> MoonlightSet
        AppPalette.DAWN -> DawnSet
        AppPalette.DEFAULT -> if (dark) ThemeSet(DarkOrnament, DarkGlass, DarkColors) else ThemeSet(LightOrnament, LightGlass, LightColors)
    }
    val set = androidx.compose.runtime.remember(palette, dark) { fixContrast(raw, dark) }
    val ornament = set.ornament
    val glass = set.glass
    val colors = set.colors
    CompositionLocalProvider(
        LocalOrnament provides ornament,
        LocalGlass provides glass,
        LocalDark provides dark
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography(),
            shapes = AppShapes
        ) {
            // تموّج قطرة الماء لكل العناصر القابلة للنقر
            val ripple = glass.ripple
            CompositionLocalProvider(
                androidx.compose.foundation.LocalIndication provides
                    com.mawaqeet.app.ui.components.WaterIndication(ripple, com.mawaqeet.app.ui.components.LocalPerf.current.richRipple),
                content = content
            )
        }
    }
}

object AppTheme {
    val ornament: Ornament
        @Composable get() = LocalOrnament.current
    val glass: Glass
        @Composable get() = LocalGlass.current
}
