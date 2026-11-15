package com.mawaqeet.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.mawaqeet.app.ui.theme.LocalMotion
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mawaqeet.app.ui.theme.AppFonts
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.LocalDate

/** ترويسة الصفحة: عنوان كبير حرّ بلا إطار، وفوقه سطر صغير مضيء، وزر رجوع دائري زجاجي */
@Composable
fun IslamicTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val o = AppTheme.ornament
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(44.dp).glass(CircleShape, shadow = false).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = o.ink) }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                if (subtitle != null) Text(
                    subtitle, color = accent, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    title, color = o.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
                )
            }
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides accent) { actions() }
            }
        }
        OrbitLine(accent, Modifier.padding(top = 8.dp, bottom = 4.dp))
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    topLevel: Boolean = false,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    /** المحتوى (خريطة أو لوحة رسم) يملأ الشاشة كلها خلف شريط العنوان العائم */
    immersive: Boolean = false,
    /** ملء الشاشة: يختفي شريط العنوان وأشرطة النظام */
    fullscreen: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    if (immersive || fullscreen) {
        HideSystemBars(fullscreen)
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            if (!fullscreen) {
                val bg = AppTheme.glass.bgTop
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.92f), bg.copy(alpha = 0.6f), Color.Transparent)))
                        .padding(bottom = 18.dp)
                ) { IslamicTopBar(title, subtitle, onBack, actions) }
            }
        }
        return
    }
    Scaffold(
        topBar = { IslamicTopBar(title, subtitle, onBack, actions) },
        floatingActionButton = fab,
        containerColor = Color.Transparent,
        contentWindowInsets = if (topLevel) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
        content = { pad -> Box(Modifier.fillMaxSize().padding(pad)) { content(PaddingValues(0.dp)) } }
    )
}

/** ارتفاع شريط العنوان العائم تقريباً (لإزاحة العناصر العلوية في الصفحات الغامرة) */
val TopBarSpace = 104.dp

/** إخفاء شريطي الحالة والتنقل للنظام (ملء الشاشة) ثم إعادتهما عند الخروج */
@Composable
fun HideSystemBars(hidden: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (ctl != null) {
            if (hidden) {
                ctl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else ctl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
}

/** زر ملء الشاشة / الخروج منه */
@Composable
fun FullscreenToggle(full: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (full) androidx.compose.material.icons.Icons.Filled.FullscreenExit else androidx.compose.material.icons.Icons.Filled.Fullscreen,
            if (full) "الخروج من ملء الشاشة" else "ملء الشاشة"
        )
    }
}

/** زر رئيسي كبير من زجاج ملوّن (مثل «إضافة حلقة»)، والمحدَّد زجاج شفاف بحافة ملوّنة */
@Composable
fun BigButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
    /** يبقى ظاهراً لحساب المشاهدة (تنقّل أو إعداد شخصي)؛ وإلا تختفي أزرار الإجراء عنه */
    nav: Boolean = false,
    onClick: () -> Unit
) {
    if (LocalReadOnly.current && !nav) return
    val shape = MaterialTheme.shapes.large
    val primary = MaterialTheme.colorScheme.primary
    val fg = if (outlined) primary else MaterialTheme.colorScheme.onPrimary
    Row(
        modifier.fillMaxWidth().height(if (outlined) 62.dp else 64.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (outlined) Modifier.glass(shape, rim = primary.copy(alpha = 0.85f))
                else Modifier.glass(shape, tint = primary, rim = Color.White.copy(alpha = 0.55f))
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) { Spacer(Modifier.width(10.dp)); Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp)) }
    }
}

/** حساب «مشاهدة فقط»: تختفي أزرار الإضافة والحفظ والحذف */
val LocalReadOnly = staticCompositionLocalOf { false }

/** نقرة تفتح نموذج التعديل: لا شيء لحساب المشاهدة (فلا تبدو البطاقة قابلة للنقر) */
@Composable
fun editClick(action: () -> Unit): (() -> Unit)? = if (LocalReadOnly.current) null else action

@Composable
fun AddFab(text: String = "إضافة", onClick: () -> Unit) {
    if (LocalReadOnly.current) return
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(androidx.compose.material.icons.Icons.Filled.Add, null) },
        text = { Text(text, style = MaterialTheme.typography.titleSmall) }
    )
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Rosette(64.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center, color = AppTheme.ornament.muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "تأكيد",
    danger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** نافذة نموذج إدخال قابلة للتمرير */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // نافذة زجاجية: عرض محدود على الشاشات الكبيرة وارتفاع لا يتجاوز الشاشة (يُمرَّر المحتوى داخلها)
        GlassDialogSurface(dialogWidth().heightIn(max = dialogMaxHeight()).imePadding()) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = AppTheme.ornament.ink,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                OrnamentDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (readOnly) {
                        // حساب المشاهدة يرى التفاصيل فقط
                        Text("عرض فقط", style = MaterialTheme.typography.labelLarge, color = AppTheme.ornament.muted)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("إغلاق") }
                    } else {
                        if (onDelete != null) {
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("إلغاء") }
                        GlassButton(onClick = onSave, enabled = saveEnabled, shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(48.dp)) { Text("حفظ", style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog("تأكيد الحذف", "هل تريد حذف هذا السجل نهائياً؟", "حذف", onDismiss = { confirmDelete = false }) {
            onDelete()
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    number: Boolean = false,
    phone: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppTheme.glass.fill, unfocusedContainerColor = AppTheme.glass.fill,
            unfocusedBorderColor = AppTheme.ornament.cardBorder
        ),
        keyboardOptions = when {
            number -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
            phone -> KeyboardOptions(keyboardType = KeyboardType.Phone)
            else -> KeyboardOptions.Default
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.ornament.ink)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsSelector(options: Map<String, String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, v) ->
            GlassChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(v, maxLines = 1, softWrap = false) })
        }
    }
}

@Composable
fun <T> SelectField(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        GlassOutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), nav = true) {
            Text(
                "$label: " + (options.firstOrNull { it.first == selected }?.second ?: "اختر"),
                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("لا توجد خيارات") }, onClick = { open = false })
            options.forEach { (v, t) ->
                DropdownMenuItem(text = { Text(t) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

/** حقل اختيار طالب مرتبط بقاعدة بيانات الطلاب */

/** بطاقة إحصاء: وردة ذهبية ثم رقم كبير ثم وصف */
@Composable
fun StatTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppTheme.ornament.ink) {
    OrnateCard(modifier, contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppTheme.ornament.gold, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = tint, maxLines = 1)
            Text(title, style = MaterialTheme.typography.labelMedium, color = AppTheme.ornament.muted, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/** بطاقة سجل في القوائم */
@Composable
fun RecordCard(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null
) {
    val accent = badgeTone(badgeColor)
    OrnateCard(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onClick,
        strip = if (badge != null) accent else MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.ornament.ink)
                if (badge != null) {
                    Spacer(Modifier.height(4.dp))
                    Pill(badge, accent)
                }
                if (below != null) below()
                lines.filter { it.isNotBlank() }.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.ornament.muted)
                }
            }
            // عرض أقصى للأزرار الجانبية حتى لا تضغط النص والأزرار تحت الاسم
            if (trailing != null) Row(Modifier.widthIn(max = 170.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
            else if (onClick != null) Chevron()
        }
    }
}

/** يحوّل ألوان الحاويات الفاتحة إلى لون مميز للشارة */
@Composable
private fun badgeTone(c: Color): Color {
    val cs = MaterialTheme.colorScheme
    val o = AppTheme.ornament
    return when (c) {
        cs.errorContainer -> o.danger
        cs.primaryContainer -> o.success
        cs.surfaceVariant -> cs.outline
        cs.secondaryContainer -> o.gold
        cs.tertiaryContainer -> o.info
        else -> c
    }
}


/** شريط تبويبات زجاجي: قطرة ملوّنة تنزلق إلى التبويب المختار وتنضغط كالماء أثناء حركتها */
@Composable
fun OrnateTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val motion = LocalMotion.current
    val pillShape = MaterialTheme.shapes.medium
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .glass(MaterialTheme.shapes.large, shadow = false)
            .padding(5.dp)
    ) {
        val w = maxWidth / tabs.size.coerceAtLeast(1)
        val x by animateDpAsState(
            w * selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
            if (motion) spring(dampingRatio = 0.62f, stiffness = 420f) else snap(),
            label = "tabDrop"
        )
        val squish = remember { Animatable(1f) }
        LaunchedEffect(selected) {
            if (motion) { squish.snapTo(0f); squish.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        }
        Box(
            Modifier.matchParentSize().wrapContentWidth(Alignment.Start).offset(x = x).width(w)
                .graphicsLayer {
                    val k = sin(PI * squish.value).toFloat()
                    scaleX = 1f + 0.18f * k
                    scaleY = 1f - 0.10f * k
                }
                .clip(pillShape)
                .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.82f), primary)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f))), pillShape)
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                val c by animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimary else o.onChip, tween(350), label = "tabText")
                Box(
                    Modifier.weight(1f).clip(pillShape)
                        .clickable { onSelect(i) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t, color = c, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

// ---------- ترتيب السجلات ----------
/** 0 التاريخ (الأحدث أولاً)، 1 التاريخ (الأقدم أولاً)، 2 وقت الإضافة (الأحدث)، 3 وقت الإضافة (الأقدم)، 4 اسم الطالب */
