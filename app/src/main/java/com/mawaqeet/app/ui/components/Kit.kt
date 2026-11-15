package com.mawaqeet.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * عناصر مشتركة لكل الشاشات (فوق مكوّنات الزجاج السائل).
 */

/** لوحة رصد زجاجية: رأس بنقطة متوهجة وخط مداري، وزوايا كإطار منظار */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val o = AppTheme.ornament
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
            .dropIn()
            .glass(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowDot(accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = o.ink)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = o.muted)
                }
                trailing?.invoke()
                if (trailing == null && onClick != null) Text("‹", color = accent, fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            OrbitLine(accent)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** نقطة متوهجة صغيرة (نجم) */
@Composable
fun GlowDot(color: Color, size: androidx.compose.ui.unit.Dp = 10.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size + 8.dp)) {
        val c = center
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), c, this.size.minDimension / 2), this.size.minDimension / 2, c)
        drawCircle(color, size.toPx() / 3.2f, c)
    }
}

/** خط رفيع متلاشٍ يبدأ بنقطة (مدار) */
@Composable
fun OrbitLine(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val y = size.height / 2
        // في الواجهة العربية يبدأ الخط من اليمين
        drawLine(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.18f), color.copy(alpha = 0.6f))),
            androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx()
        )
        drawCircle(color, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), y))
    }
}

/** زوايا إطار كإطار عدسة المنظار */
fun Modifier.hudCorners(color: Color, len: androidx.compose.ui.unit.Dp = 14.dp, inset: androidx.compose.ui.unit.Dp = 7.dp): Modifier = drawWithContent {
    drawContent()
    val l = len.toPx(); val i = inset.toPx(); val sw = 1.4.dp.toPx()
    val w = size.width; val h = size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx * l, y), sw)
        drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy * l), sw)
    }
    corner(i, i, 1f, 1f); corner(w - i, i, -1f, 1f); corner(i, h - i, 1f, -1f); corner(w - i, h - i, -1f, -1f)
}

/** سطر «عنوان: قيمة» */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, emphasize: Boolean = false) {
    val o = AppTheme.ornament
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = o.muted, style = MaterialTheme.typography.bodyMedium)
        Text(
            value, color = if (valueColor == Color.Unspecified) o.ink else valueColor,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.Black else FontWeight.Bold, textAlign = TextAlign.End
        )
    }
}

/** قراءة رصد: وصف صغير فوق رقم كبير */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val o = AppTheme.ornament
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = o.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        Text(value, color = if (color == Color.Unspecified) o.ink else color, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MetricRow(vararg items: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (l, v) -> Metric(l, v, Modifier.weight(1f)) }
    }
}

/** عنوان قسم: نجمة صغيرة ثم النص ثم خط مداري */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = AppTheme.ornament.gold, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.ornament.ink)
        Spacer(Modifier.width(10.dp))
        OrbitLine(accent.copy(alpha = 0.7f), Modifier.weight(1f))
    }
}

/** ملاحظة صغيرة (تنبيه أو مصدر) */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall, color = AppTheme.ornament.muted
    )
}

/** متنقّل بين الأيام: سهم للسابق، التاريخ (ميلادي وهجري)، سهم للتالي، ونقرة على التاريخ تعيد اليوم */
@Composable
fun DayStepper(date: LocalDate, onChange: (LocalDate) -> Unit, stepDays: Long = 1, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).glass(RoundedCornerShape(24.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // في الواجهة العربية (RTL) يكون اليمين «السابق»
        IconButton(onClick = { onChange(date.minusDays(stepDays)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "السابق", tint = o.ink) }
        Column(
            Modifier.weight(1f).clickable { onChange(env.today) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${com.mawaqeet.app.astro.Calendars.weekDay(date)} ${env.gregorian(date)}", color = o.ink, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(env.hijri(date), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            if (date != env.today) Text("اضغط للعودة إلى اليوم", color = o.muted, fontSize = 11.sp)
        }
        IconButton(onClick = { onChange(date.plusDays(stepDays)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "التالي", tint = o.ink) }
    }
}

/** بلاطة ميزة: أيقونة داخل مدار، وعنوان ووصف، وسهم */
@Composable
fun FeatureTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier.padding(6.dp).dropIn().glass(RoundedCornerShape(22.dp)).clickable(onClick = onClick)
            .heightIn(min = 128.dp).padding(14.dp)
    ) {
        Box(
            Modifier.size(48.dp).drawBehind {
                val c = center; val r = size.minDimension / 2
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(primary.copy(alpha = 0.35f), Color.Transparent), c, r), r, c)
                drawCircle(primary.copy(alpha = 0.6f), r * 0.86f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                drawCircle(o.gold, 2.6.dp.toPx(), androidx.compose.ui.geometry.Offset(c.x + r * 0.61f, c.y - r * 0.61f))
            },
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(title, color = o.ink, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = o.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** شبكة بلاطات من عمودين داخل قائمة */
fun LazyListScope.tileGrid(items: List<TileSpec>) {
    items.chunked(2).forEach { row ->
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                row.forEach { t -> FeatureTile(t.title, t.subtitle, t.icon, Modifier.weight(1f), t.onClick) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

data class TileSpec(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

/** دائرة انتظار في وسط المساحة */
@Composable
fun Busy(modifier: Modifier = Modifier, text: String = "جارٍ الحساب…") {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(text, color = AppTheme.ornament.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * حساب ثقيل في الخلفية يُعاد كلما تغيّرت المفاتيح. يعيد null حتى ينتهي.
 * مثال: `val data = rememberComputed(date, env.settings) { Eclipses.between(a, b) }`
 */
@Composable
fun <T> rememberComputed(vararg keys: Any?, compute: suspend () -> T): T? {
    var value by remember(*keys) { mutableStateOf<T?>(null) }
    LaunchedEffect(*keys) {
        value = withContext(Dispatchers.Default) { compute() }
    }
    return value
}

/** قائمة الشاشة القياسية: تمرير عمودي مع مسافة سفلية لشريط التنقل */
@Composable
fun ScreenList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    // على الشاشات الكبيرة (التابلت والوضع الأفقي) يبقى المحتوى عموداً متوسطاً بعرض مريح للقراءة
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier.fillMaxSize().widthIn(max = MaxContentWidth),
            contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
            content = content
        )
    }
}

/** أقصى عرض للمحتوى المقروء على الشاشات الكبيرة */
val MaxContentWidth = 720.dp

/** زر مشاركة نص في شريط العنوان */
@Composable
fun ShareAction(text: () -> String) {
    val ctx = LocalContext.current
    IconButton(onClick = { Exporter.shareText(ctx, text()) }) { Icon(Icons.Filled.Share, "مشاركة") }
}

/** خانة صغيرة بقيمة نصية (للجداول البسيطة داخل البطاقات) */
@Composable
fun TableRowCells(cells: List<String>, header: Boolean = false, weights: List<Float>? = null) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c, Modifier.weight(weights?.getOrNull(i) ?: 1f).padding(horizontal = 2.dp),
                color = if (header) o.gold else o.ink, textAlign = TextAlign.Center, maxLines = 1,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.Black else FontWeight.Medium
            )
        }
    }
}

/** مسافة لشريط التنقل في أسفل الصفحات غير القائمة */
@Composable
fun BottomSpace() = Spacer(Modifier.navigationBarsPadding().height(100.dp).width(1.dp))
