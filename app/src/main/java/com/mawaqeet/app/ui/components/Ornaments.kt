package com.mawaqeet.app.ui.components

import com.mawaqeet.app.ui.theme.readableOn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.ui.theme.AppFonts
import com.mawaqeet.app.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ======================= الأشكال الهندسية =======================

/** نجمة ثمانية (خاتم سليمان / ربع الحزب) */
fun star8Path(center: Offset, r: Float): Path {
    val inner = r * 0.7654f
    val p = Path()
    for (i in 0 until 16) {
        val ang = (PI / 8.0 * i - PI / 2.0)
        val rad = if (i % 2 == 0) r else inner
        val x = center.x + (rad * cos(ang)).toFloat()
        val y = center.y + (rad * sin(ang)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

fun DrawScope.drawStar8(center: Offset, r: Float, color: Color, strokeWidth: Float? = null) {
    val p = star8Path(center, r)
    if (strokeWidth == null) drawPath(p, color) else drawPath(p, color, style = Stroke(strokeWidth))
}

/** شعار فلكي: هلال داخل مدار مائل مع كوكب صغير */
fun DrawScope.drawRosette(center: Offset, r: Float, color: Color, sw: Float) {
    // المدار
    drawOval(color.copy(alpha = 0.55f), topLeft = Offset(center.x - r, center.y - r * 0.38f), size = Size(r * 2f, r * 0.76f), style = Stroke(sw))
    // الهلال: قرص مضيء ناقص قرص مزاح
    val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r * 0.62f)) }
    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + r * 0.30f, center.y - r * 0.24f), r * 0.52f)) }
    drawPath(Path.combine(PathOperation.Difference, moon, cut), color)
    // كوكب على المدار
    drawCircle(color, r * 0.11f, Offset(center.x - r * 0.92f, center.y + r * 0.12f))
}

/** شكل القوس المدبّب (المحراب) */
class ArchShape(private val springline: Float = 0.42f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val w = size.width
        val h = size.height
        val s = h * springline
        val p = Path().apply {
            moveTo(0f, h)
            lineTo(0f, s)
            cubicTo(0f, s * 0.45f, w * 0.30f, s * 0.12f, w / 2f, 0f)
            cubicTo(w * 0.70f, s * 0.12f, w, s * 0.45f, w, s)
            lineTo(w, h)
            close()
        }
        return Outline.Generic(p)
    }
}

/** حقل نجوم ثابت (نقاط بأحجام وسطوع مختلفة) — يُرسم مرة ويُخزَّن */
fun Modifier.islamicPattern(color: Color, cell: Dp = 34.dp, strokeWidth: Float = 1.1f): Modifier = starField(color)

fun Modifier.starField(color: Color, density: Float = 1f): Modifier = drawBehind {
    val rnd = java.util.Random(7L)
    val n = (size.width * size.height / 5200f * density).toInt().coerceIn(60, 900)
    repeat(n) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        val m = rnd.nextFloat()
        val r = (0.5f + m * m * 1.6f).dp.toPx() * 0.6f
        val a = (0.15f + m * 0.75f) * color.alpha
        drawCircle(color.copy(alpha = a), r, Offset(x, y))
        if (m > 0.97f) {
            // وميض صليبي لألمع النجوم
            val l = r * 4f
            drawLine(color.copy(alpha = a * 0.6f), Offset(x - l, y), Offset(x + l, y), 0.6f)
            drawLine(color.copy(alpha = a * 0.6f), Offset(x, y - l), Offset(x, y + l), 0.6f)
        }
    }
}

/** شريط زخرفي بنجوم ذهبية على خلفية كحلية (يستخدم في أعلى وأسفل البطاقات الكبيرة) */
@Composable
fun StarBand(modifier: Modifier = Modifier, height: Dp = 30.dp, bg: Color = AppTheme.ornament.band, color: Color = AppTheme.ornament.gold) {
    Canvas(modifier.fillMaxWidth().height(height).background(bg)) {
        val h = size.height
        val r = h * 0.36f
        val step = h * 1.1f
        var x = step / 2f
        while (x < size.width + step) {
            drawRosette(Offset(x, h / 2f), r, color, 1.2.dp.toPx())
            // معيّن بين الوردات
            val dx = x + step / 2f
            val q = r * 0.32f
            drawPath(Path().apply { moveTo(dx, h / 2f - q); lineTo(dx + q, h / 2f); lineTo(dx, h / 2f + q); lineTo(dx - q, h / 2f); close() }, color)
            x += step
        }
    }
}

/** شريط أقواس صغيرة */
@Composable
fun ArchBand(modifier: Modifier = Modifier, color: Color = AppTheme.ornament.gold, height: Dp = 10.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val h = size.height
        val n = (size.width / (h * 2.4f)).toInt().coerceAtLeast(6)
        val w = size.width / n
        val p = Path()
        p.moveTo(0f, 0f)
        for (i in 0 until n) {
            val x0 = i * w
            p.cubicTo(x0 + w * 0.05f, h * 0.95f, x0 + w * 0.45f, h * 0.85f, x0 + w / 2f, h)
            p.cubicTo(x0 + w * 0.55f, h * 0.85f, x0 + w * 0.95f, h * 0.95f, x0 + w, 0f)
        }
        drawPath(p, color, style = Stroke(1.4.dp.toPx()))
    }
}

/** فاصل فلكي: خط متلاشٍ يمر بكوكب صغير ومداره */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier, color: Color = AppTheme.ornament.gold, hole: Color = Color.Unspecified) {
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val cy = size.height / 2f
        val cx = size.width / 2f
        val r = size.height * 0.22f
        val sw = 1.dp.toPx()
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent)),
            Offset(0f, cy), Offset(size.width, cy), sw
        )
        drawOval(color.copy(alpha = 0.7f), topLeft = Offset(cx - r * 3f, cy - r * 1.1f), size = Size(r * 6f, r * 2.2f), style = Stroke(sw))
        drawCircle(color, r, Offset(cx, cy))
        drawCircle(color, r * 0.4f, Offset(cx + r * 2.6f, cy - r * 0.55f))
    }
}

/** وردة صغيرة كأيقونة */
@Composable
fun Rosette(size: Dp = 28.dp, color: Color = AppTheme.ornament.gold) {
    Canvas(Modifier.size(size)) {
        drawRosette(Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension / 2f * 0.95f, color, 1.4.dp.toPx())
    }
}

/** عنوان قسم مزخرف */
@Composable
fun OrnateTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = AppTheme.ornament.ink, modifier = Modifier.padding(horizontal = 4.dp))
        OrnamentDivider(Modifier.padding(vertical = 4.dp))
    }
}

/** شريط جانبي رفيع متدرّج (حافة البطاقات) */
@Composable
fun PatternStrip(color: Color = MaterialTheme.colorScheme.primary, width: Dp = 5.dp) {
    Box(Modifier.width(width).fillMaxHeight().background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))))
}

/** بطاقة زجاجية بحواف دائرية وحافة مضيئة، مع شريط زخرفي اختياري */
@Composable
fun OrnateCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = AppTheme.ornament.card,
    accent: Color = AppTheme.ornament.cardBorder,
    strip: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier
            .dropIn(LocalDropTrigger.current)
            // زجاج سائل: شفاف مع لمعة وحافة مضيئة (لون مخصص إن مُرّر)
            .glass(
                shape,
                tint = if (container == AppTheme.ornament.card) Color.Unspecified else container,
                rim = if (accent == AppTheme.ornament.cardBorder) Color.Unspecified else accent
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .height(IntrinsicSize.Min)
    ) {
        if (strip != null) PatternStrip(strip)
        Column(Modifier.weight(1f).padding(contentPadding), content = content)
    }
}

/** قرص ملوّن بحلقة مدارية يحمل أيقونة أو رقماً */
@Composable
fun StarBadge(
    size: Dp = 46.dp,
    fill: Color = MaterialTheme.colorScheme.primary,
    ring: Color = AppTheme.ornament.gold,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier.size(size).drawBehind {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            drawCircle(ring, r, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(fill, r - 4.dp.toPx(), c)
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun Avatar(name: String, size: Dp = 48.dp, fill: Color = MaterialTheme.colorScheme.primary) {
    StarBadge(size = size, fill = fill) {
        Text(
            name.trim().firstOrNull()?.toString() ?: "؟",
            color = readableOn(fill), fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.38f).sp, fontFamily = AppFonts.body
        )
    }
}

@Composable
fun NumberBadge(n: Int, size: Dp = 52.dp) {
    StarBadge(size = size) {
        Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = (size.value * 0.36f).sp, fontFamily = AppFonts.body)
    }
}

/** شارة نصية صغيرة ملوّنة */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.body,
        // سطر واحد دائماً: إن ضاق المكان تُختصر بـ «…» بدل أن تنكسر حرفاً حرفاً وتمتد البطاقة
        maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}

/** شريحة معلومات بيج داخل البطاقات */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, value: String? = null) {
    val o = AppTheme.ornament
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(o.chip).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = o.onChip, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = o.onChip, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = o.onChip, style = MaterialTheme.typography.titleMedium)
    }
}

/** سهم الانتقال في نهاية البطاقة */
@Composable
fun Chevron() {
    Icon(Icons.Filled.KeyboardArrowLeft, null, tint = AppTheme.ornament.ink.copy(alpha = 0.7f), modifier = Modifier.size(30.dp))
}

/** نجمة ثمانية تحمل أيقونة (لبطاقات السجلات) */
@Composable
fun AvatarIcon(icon: ImageVector, size: Dp = 44.dp) {
    StarBadge(size = size) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.5f)) }
}
