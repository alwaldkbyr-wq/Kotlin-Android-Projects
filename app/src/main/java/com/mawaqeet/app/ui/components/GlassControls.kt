package com.mawaqeet.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Check
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalGlass

/*
 * عناصر تحكم بأسلوب «الزجاج السائل» تحل محل أزرار وشرائح ونوافذ Material
 * بنفس طريقة الاستدعاء تقريباً، حتى تتوحد الهوية في كل الشاشات والنوافذ.
 */

@Composable
private fun GlassButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    contentPadding: PaddingValues,
    surface: Modifier,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .defaultMinSize(minHeight = 46.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .then(surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = contentColor)) { content() }
        }
    }
}

private val DefaultPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)

/** زر أساسي: زجاج ملوّن بلون الهوية */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** يبقى ظاهراً لحساب المشاهدة (تنقّل أو إعداد شخصي)، وإلا يختفي */
    nav: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = DefaultPadding,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalReadOnly.current && !nav) return
    val primary = MaterialTheme.colorScheme.primary
    GlassButtonBase(
        onClick, modifier, enabled, shape, contentPadding,
        Modifier.glass(shape, tint = primary, rim = Color.White.copy(alpha = 0.5f), shadow = false),
        MaterialTheme.colorScheme.onPrimary, content
    )
}

/** زر ثانوي: زجاج شفاف بحافة ملوّنة */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** يبقى ظاهراً لحساب المشاهدة (تنقّل أو إعداد شخصي)، وإلا يختفي */
    nav: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = DefaultPadding,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalReadOnly.current && !nav) return
    GlassButtonBase(
        onClick, modifier, enabled, shape, contentPadding,
        Modifier.glass(shape, rim = accent.copy(alpha = 0.55f), shadow = false),
        accent, content
    )
}

/** زر هادئ: زجاج بلمسة لونية خفيفة */
@Composable
fun GlassTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** يبقى ظاهراً لحساب المشاهدة (تنقّل أو إعداد شخصي)، وإلا يختفي */
    nav: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = DefaultPadding,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalReadOnly.current && !nav) return
    val primary = MaterialTheme.colorScheme.primary
    GlassButtonBase(
        onClick, modifier, enabled, shape, contentPadding,
        Modifier.glass(shape, shadow = false).drawBehind { drawRect(primary.copy(alpha = 0.10f)) },
        primary, content
    )
}

/** شريحة اختيار زجاجية: المختارة تمتلئ بلون الهوية كقطرة */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else AppTheme.ornament.ink
    Row(
        modifier
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (selected) Modifier.glass(shape, tint = primary, rim = Color.White.copy(alpha = 0.5f), shadow = false)
                else Modifier.glass(shape, shadow = false)
            )
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = fg)) {
                if (selected && leadingIcon == null) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                }
                leadingIcon?.let { it(); Spacer(Modifier.size(6.dp)) }
                label()
            }
        }
    }
}

/**
 * سطح النوافذ المنبثقة: ماء ساكن تحت زجاج، بحافة مضيئة.
 * أكثر عتامة من البطاقات حتى يبقى النص واضحاً فوق تعتيم الخلفية.
 */
@Composable
fun GlassDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit
) {
    val g = LocalGlass.current
    Column(
        modifier
            .clip(shape)
            .drawBehind { drawStillWater(g.bgTop, g.bgBottom, g.blob1, g.blob2, g.blob3) }
            .glass(shape, shadow = false),
        content = content
    )
}

/** عرض مناسب للنوافذ: كامل تقريباً على الهاتف، ومحدود على الشاشات الكبيرة */
@Composable
fun dialogWidth(): Modifier {
    val w = LocalConfiguration.current.screenWidthDp
    return if (w >= 600) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier.fillMaxWidth(0.94f)
}

/** أقصى ارتفاع للنافذة حتى لا يُقص محتواها في الوضع الأفقي (يُمرَّر داخلها بدلاً من ذلك) */
@Composable
fun dialogMaxHeight(fraction: Float = 0.9f): Dp = (LocalConfiguration.current.screenHeightDp * fraction).dp

/**
 * بديل AlertDialog بأسلوب زجاجي، بنفس المعاملات.
 * النص قابل للتمرير دائماً فلا يختفي جزء منه على الشاشات القصيرة أو في الوضع الأفقي.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false
        )
    ) {
        GlassDialogSurface(
            modifier.then(dialogWidth()).heightIn(max = dialogMaxHeight()).imePadding()
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)) {
                if (icon != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CompositionLocalProvider(LocalContentColor provides AppTheme.ornament.gold) { icon() }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (title != null) {
                    ProvideTextStyle(
                        MaterialTheme.typography.titleLarge.copy(color = AppTheme.ornament.ink, textAlign = TextAlign.Center)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { title() }
                    }
                    OrnamentDivider(Modifier.padding(horizontal = 30.dp, vertical = 6.dp))
                }
                if (text != null) {
                    // النص يُمرَّر هنا؛ لا تضع داخله verticalScroll أو LazyColumn (التمرير المتداخل يوقف التطبيق)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                        CompositionLocalProvider(LocalContentColor provides AppTheme.ornament.ink) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = AppTheme.ornament.ink)) { text() }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
