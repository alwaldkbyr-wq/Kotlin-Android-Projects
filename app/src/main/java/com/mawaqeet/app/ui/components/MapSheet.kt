package com.mawaqeet.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.ui.theme.AppTheme

/**
 * صفحة خريطة: الخريطة تملأ الشاشة كلها، وفوقها لوحة سفلية زجاجية تُطوى وتُفتح بالنقر على مقبضها،
 * فلا تبدو الخريطة معلّقة وسط الصفحة.
 */
@Composable
fun MapSheet(
    mapContent: @Composable BoxScope.() -> Unit,
    items: LazyListScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val target = if (expanded) maxHeight * 0.62f else maxHeight * 0.34f
        val h by animateDpAsState(target, label = "sheet")
        Box(Modifier.fillMaxSize(), content = mapContent)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h)
                .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 26.dp, bottom = 24.dp)
            ) {
                items()
                item { Box(Modifier.navigationBarsPadding()) }
            }
            // المقبض
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(26.dp).clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(44.dp).height(5.dp).background(AppTheme.ornament.muted.copy(alpha = 0.6f), RoundedCornerShape(50)))
            }
        }
    }
}
