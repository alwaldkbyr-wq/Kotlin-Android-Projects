package com.mawaqeet.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.LiquidBackground
import com.mawaqeet.app.ui.components.LiquidPage
import com.mawaqeet.app.ui.components.glass
import com.mawaqeet.app.ui.screens.*
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.LocalMotion
import kotlin.math.PI
import kotlin.math.sin

/** مسارات الشاشات */
object Routes {
    // التبويبات الرئيسية
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val PRAYER = "prayer"
    const val SKY = "sky"
    const val TOOLS = "tools"

    // التقاويم والوقت
    const val CONVERTER = "converter"
    const val DATES = "dates"
    const val AGE = "age"
    const val SEASONS = "seasons"
    const val TABLES = "tables"

    // الشمس والقمر والصلاة
    const val PRAYER_SETTINGS = "prayer_settings"
    const val SUN_MOON = "sun_moon"
    const val MOON = "moon"
    const val MANSIONS = "mansions"
    const val CRESCENT = "crescent"
    const val ECLIPSES = "eclipses"
    const val CHARTS = "charts"

    // الخرائط والبوصلة
    const val DAYNIGHT_MAP = "daynight_map"
    const val TIMEZONE_MAP = "timezone_map"
    const val MAGNETIC_MAP = "magnetic_map"
    const val QIBLA_MAP = "qibla_map"
    const val COMPASS_2D = "compass_2d"
    const val COMPASS_3D = "compass_3d"

    // السماء
    const val SKY_MAP = "sky_map"
    const val CONSTELLATIONS = "constellations"
    const val SPHERE_3D = "sphere_3d"
    const val PLANETS = "planets"

    // النظام
    const val SETTINGS = "settings"
    const val LOCATIONS = "locations"
    const val DEVICE = "device"
    const val NAVIGATOR = "navigator"
    const val EMERGENCY = "emergency"
    const val NATURAL_NAV = "natural_nav"
    const val WATER_HEAT = "water_heat"
    const val BAROMETER = "barometer"
    const val SURVIVAL_GUIDE = "survival_guide"
    const val PRAYER_PARTS = "prayer_parts"
    const val PRAYER_LOG = "prayer_log"
    const val TASBIH = "tasbih"
    const val QIBLA_SUN = "qibla_sun"
    const val TRAVEL_PRAYER = "travel_prayer"
    const val MULTI_CITY = "multi_city"

    val tabs = listOf(HOME, CALENDAR, PRAYER, SKY, TOOLS)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun ScreenFor(route: String) {
    when (route) {
        Routes.HOME -> HomeScreen()
        Routes.CALENDAR -> CalendarScreen()
        Routes.PRAYER -> PrayerScreen()
        Routes.SKY -> SkyHubScreen()
        Routes.TOOLS -> ToolsHubScreen()
        Routes.CONVERTER -> ConverterScreen()
        Routes.DATES -> DatesScreen()
        Routes.AGE -> AgeScreen()
        Routes.SEASONS -> SeasonsScreen()
        Routes.TABLES -> TablesScreen()
        Routes.PRAYER_SETTINGS -> PrayerSettingsScreen()
        Routes.SUN_MOON -> SunMoonScreen()
        Routes.MOON -> MoonScreen()
        Routes.MANSIONS -> MansionsScreen()
        Routes.CRESCENT -> CrescentScreen()
        Routes.ECLIPSES -> EclipsesScreen()
        Routes.CHARTS -> ChartsScreen()
        Routes.DAYNIGHT_MAP -> DayNightMapScreen()
        Routes.TIMEZONE_MAP -> TimeZoneMapScreen()
        Routes.MAGNETIC_MAP -> MagneticMapScreen()
        Routes.QIBLA_MAP -> QiblaMapScreen()
        Routes.COMPASS_2D -> Compass2DScreen()
        Routes.COMPASS_3D -> Compass3DScreen()
        Routes.SKY_MAP -> SkyMapScreen()
        Routes.CONSTELLATIONS -> ConstellationsScreen()
        Routes.SPHERE_3D -> Sphere3DScreen()
        Routes.PLANETS -> PlanetsScreen()
        Routes.SETTINGS -> SettingsScreen()
        Routes.LOCATIONS -> LocationsScreen()
        Routes.DEVICE -> DeviceInfoScreen()
        Routes.NAVIGATOR -> NavigatorScreen()
        Routes.EMERGENCY -> EmergencyScreen()
        Routes.NATURAL_NAV -> NaturalNavScreen()
        Routes.WATER_HEAT -> WaterHeatScreen()
        Routes.BAROMETER -> BarometerScreen()
        Routes.SURVIVAL_GUIDE -> SurvivalGuideScreen()
        Routes.PRAYER_PARTS -> PrayerPartsScreen()
        Routes.PRAYER_LOG -> PrayerLogScreen()
        Routes.TASBIH -> TasbihScreen()
        Routes.QIBLA_SUN -> QiblaSunScreen()
        Routes.TRAVEL_PRAYER -> TravelPrayerScreen()
        Routes.MULTI_CITY -> MultiCityPrayerScreen()
        else -> HomeScreen()
    }
}

/**
 * جذر الواجهة: خلفية مائية، وصفحة بانتقال مائي، وشريط تبويبات زجاجي عائم في الصفحات الرئيسية.
 * [stack] مكدّس المسارات (آخره الصفحة الحالية)، و[onTab] لاختيار تبويب.
 */
@Composable
fun AppRoot(stack: List<String>, onTab: (String) -> Unit) {
    val env = LocalEnv.current
    val motion = LocalMotion.current
    val route = stack.lastOrNull() ?: Routes.HOME
    val isTop = route in Routes.tabs
    BackHandler(enabled = stack.size > 1 || route != Routes.HOME) {
        if (stack.size > 1) env.back() else onTab(Routes.HOME)
    }
    val tabs = listOf(
        Tab(Routes.HOME, "الرئيسية", Icons.Filled.Home),
        Tab(Routes.CALENDAR, "التقويم", Icons.Filled.CalendarMonth),
        Tab(Routes.PRAYER, "الصلاة", Icons.Filled.Mosque),
        Tab(Routes.SKY, "السماء", Icons.Filled.AutoAwesome),
        Tab(Routes.TOOLS, "الأدوات", Icons.Filled.Widgets)
    )
    // يحفظ حالة كل صفحة (موضع التمرير والاختيارات) فتعود كما تركتها عند الرجوع
    val saver = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val liveKeys = stack.mapIndexed { i, r -> "$i:$r" }.toSet()
    val seenKeys = remember { mutableSetOf<String>() }
    androidx.compose.runtime.SideEffect {
        (seenKeys - liveKeys).filterNot { it.startsWith("0:") }.forEach { saver.removeState(it) }
        seenKeys.retainAll(liveKeys); seenKeys.addAll(liveKeys)
    }
    LiquidBackground {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = stack.size to route,
                transitionSpec = {
                    if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else {
                        val forward = targetState.first >= initialState.first
                        val dir = if (forward) -1 else 1
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 5 * dir } + fadeIn(tween(300)) + scaleIn(tween(380), initialScale = 0.96f)) togetherWith
                            (slideOutHorizontally(tween(320)) { -it / 6 * dir } + fadeOut(tween(220)))
                    }
                },
                label = "page"
            ) { (n, r) ->
                saver.SaveableStateProvider("${n - 1}:$r") { LiquidPage { ScreenFor(r) } }
            }
            if (isTop) Box(Modifier.align(Alignment.BottomCenter)) { LiquidBottomBar(tabs, route, onTab) }
        }
    }
}

/** شريط تنقل زجاجي عائم: عدسة تنزلق إلى التبويب المختار بنابض مائي */
@Composable
private fun LiquidBottomBar(tabs: List<Tab>, current: String, onSelect: (String) -> Unit) {
    val o = AppTheme.ornament
    val g = AppTheme.glass
    val primary = MaterialTheme.colorScheme.primary
    val motion = LocalMotion.current
    val sel = tabs.indexOfFirst { it.route == current }.coerceAtLeast(0)
    val barShape = RoundedCornerShape(30.dp)
    val lensShape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxWidth().glass(barShape).padding(5.dp)) {
            val w = maxWidth / tabs.size.coerceAtLeast(1)
            val x by animateDpAsState(w * sel, if (motion) spring(dampingRatio = 0.6f, stiffness = 320f) else snap(), label = "lens")
            val squish = remember { Animatable(1f) }
            LaunchedEffect(sel) {
                if (motion) { squish.snapTo(0f); squish.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            }
            // كوكب متوهّج ينزلق خلف التبويب المختار
            Box(
                Modifier.matchParentSize().wrapContentWidth(Alignment.Start).offset(x = x).width(w)
                    .graphicsLayer {
                        val k = sin(PI * squish.value).toFloat()
                        scaleX = 1f + 0.22f * k
                        scaleY = 1f - 0.08f * k
                    }
                    .drawBehind {
                        val c = Offset(size.width / 2f, size.height * 0.36f)
                        val r = minOf(size.width, size.height) * 0.62f
                        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.42f), primary.copy(alpha = 0.10f), Color.Transparent), c, r), r, c)
                        drawCircle(primary.copy(alpha = 0.55f), r * 0.52f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        drawCircle(o.gold, 2.dp.toPx(), Offset(c.x + r * 0.37f, c.y - r * 0.37f))
                    }
            )
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, t ->
                    val on = i == sel
                    val tint by animateColorAsState(if (on) primary else o.muted, tween(350), label = "tabTint")
                    val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "tabLift")
                    Column(
                        Modifier.weight(1f).clip(lensShape).clickable { onSelect(t.route) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            t.icon, t.label, tint = tint,
                            modifier = Modifier.size(23.dp).graphicsLayer {
                                val s = 1f + 0.1f * lift
                                scaleX = s; scaleY = s
                                translationY = -2.dp.toPx() * lift
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.label, fontSize = 10.5.sp, maxLines = 1, textAlign = TextAlign.Center,
                            color = if (on) o.ink else o.muted, style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
