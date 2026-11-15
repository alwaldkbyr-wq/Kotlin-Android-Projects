package com.mawaqeet.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.R
import com.mawaqeet.app.ui.components.orbitRings
import com.mawaqeet.app.ui.components.drawCosmicStars
import com.mawaqeet.app.ui.theme.AppFonts
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/** شاشة البداية: البسملة، وشعار الساهور (هلال ومدار)، والاسم — ثم تتلاشى */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        val a = async { fade.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        a.await()
        delay(2600)
        out.animateTo(0f, tween(450))
        onDone()
    }
    // تتبع ألوان الثيم المختار (ليلي أو نهاري)
    val o = com.mawaqeet.app.ui.theme.AppTheme.ornament
    val g = com.mawaqeet.app.ui.theme.AppTheme.glass
    val gold = o.gold
    val stars = remember { com.mawaqeet.app.ui.components.cosmicStars(260) }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = out.value }
            .background(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom)))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(g.blob1, Color.Transparent), Offset(size.width * 0.85f, size.height * 0.15f), size.maxDimension * 0.6f), size.maxDimension * 0.6f, Offset(size.width * 0.85f, size.height * 0.15f))
                drawCircle(Brush.radialGradient(listOf(g.blob2, Color.Transparent), Offset(size.width * 0.1f, size.height * 0.9f), size.maxDimension * 0.6f), size.maxDimension * 0.6f, Offset(size.width * 0.1f, size.height * 0.9f))
            }
            .orbitRings(gold.copy(alpha = 0.12f))
            .drawBehind { drawCosmicStars(stars, o.pattern, 0.8f) }
            // يمنع اللمس من الوصول إلى ما تحتها
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painterResource(R.drawable.bismillah), contentDescription = "بسم الله الرحمن الرحيم",
                modifier = Modifier.fillMaxWidth(0.82f).graphicsLayer { alpha = fade.value; translationY = (1f - fade.value) * -20f },
                contentScale = ContentScale.FillWidth,
                colorFilter = ColorFilter.tint(gold)
            )
            Spacer(Modifier.height(56.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), "الساهور", Modifier.size(170.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                "الساهور", color = o.ink, fontFamily = AppFonts.display, fontWeight = FontWeight.Black,
                fontSize = 44.sp, textAlign = TextAlign.Center, modifier = Modifier
            )
            Text(
                "تقويم فلكي — من أسماء القمر", color = o.muted, fontFamily = AppFonts.text,
                fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Designed by", color = o.muted, fontFamily = AppFonts.text, fontSize = 12.sp, letterSpacing = 2.sp)
            Image(
                painterResource(R.drawable.signature), contentDescription = "حارث تلاتيت",
                modifier = Modifier.padding(top = 2.dp).height(52.dp),
                contentScale = ContentScale.FillHeight,
                colorFilter = ColorFilter.tint(gold)
            )
        }
    }
}
