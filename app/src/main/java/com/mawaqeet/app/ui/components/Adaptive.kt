package com.mawaqeet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** فئة عرض النافذة: هاتف، أو تابلت صغير/هاتف أفقي، أو شاشة كبيرة */
enum class WindowClass { Compact, Medium, Expanded }

val WindowClass.isWide: Boolean get() = this != WindowClass.Compact

val LocalWindowClass = compositionLocalOf { WindowClass.Compact }

@Composable
fun rememberWindowClass(): WindowClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> WindowClass.Expanded
        w >= 600 -> WindowClass.Medium
        else -> WindowClass.Compact
    }
}

/**
 * قائمة تتكيف مع الشاشة: عمود واحد على الهاتف، وعدة أعمدة على التابلت والوضع الأفقي.
 * العناصر العريضة (العناوين والفلاتر) تُضاف بـ [fullItem].
 */
@Composable
fun AdaptiveList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    minCell: Dp = 380.dp,
    content: LazyGridScope.() -> Unit
) {
    val wide = LocalWindowClass.current.isWide
    LazyVerticalGrid(
        columns = if (wide) GridCells.Adaptive(minCell) else GridCells.Fixed(1),
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** عنصر يمتد على كامل عرض الشبكة */
fun LazyGridScope.fullItem(key: Any? = null, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}
