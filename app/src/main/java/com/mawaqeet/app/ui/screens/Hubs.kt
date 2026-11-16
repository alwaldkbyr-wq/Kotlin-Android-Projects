package com.mawaqeet.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.TileSpec
import com.mawaqeet.app.ui.components.tileGrid

/** تبويب «السماء»: كل ما يخص الشمس والقمر والنجوم */
@Composable
fun SkyHubScreen() {
    val env = LocalEnv.current
    val go = env.navigate
    ScreenScaffold(title = "السماء", subtitle = "الشمس والقمر والنجوم", topLevel = true) {
        ScreenList {
            item { SectionTitle("الشمس والقمر") }
            tileGrid(listOf(
                TileSpec("الشروق والغروب", "الشمس والقمر والليل والنهار", Icons.Filled.WbTwilight) { go(Routes.SUN_MOON) },
                TileSpec("أطوار القمر", "الطور والعمر والإضاءة", Icons.Filled.Brightness2) { go(Routes.MOON) },
                TileSpec("رؤية الهلال", "معيار يالوب وعودة وخريطة الرؤية", Icons.Filled.NightsStay) { go(Routes.CRESCENT) },
                TileSpec("منازل القمر", "المنازل الثمانية والعشرون", Icons.Filled.Stars) { go(Routes.MANSIONS) },
                TileSpec("الكسوف والخسوف", "المواعيد والأنواع والمدد", Icons.Filled.Contrast) { go(Routes.ECLIPSES) },
                TileSpec("الفصول", "الاعتدالان والانقلابان", Icons.Filled.Thermostat) { go(Routes.SEASONS) }
            ))
            item { SectionTitle("النجوم والكواكب") }
            tileGrid(listOf(
                TileSpec("خريطة السماء", "الكواكب وألمع النجوم والمنازل", Icons.Filled.TravelExplore) { go(Routes.SKY_MAP) },
                TileSpec("الكرة السماوية", "عرض ثلاثي الأبعاد تفاعلي", Icons.Filled.Public) { go(Routes.SPHERE_3D) },
                TileSpec("الكوكبات", "خريطة الأبراج والكوكبات", Icons.Filled.AutoAwesome) { go(Routes.CONSTELLATIONS) },
                TileSpec("الكواكب", "الطلوع والغروب واللمعان", Icons.Filled.Circle) { go(Routes.PLANETS) }
            ))
            item { SectionTitle("رسوم بيانية") }
            tileGrid(listOf(
                TileSpec("الرسوم البيانية", "طول النهار ومواقيت السنة", Icons.Filled.ShowChart) { go(Routes.CHARTS) }
            ))
        }
    }
}

/** تبويب «الأدوات»: الخرائط والبوصلة والتصدير والإعدادات */
@Composable
fun ToolsHubScreen() {
    val env = LocalEnv.current
    val go = env.navigate
    ScreenScaffold(title = "الأدوات", subtitle = "خرائط وبوصلة وأدوات", topLevel = true) {
        ScreenList {
            item { SectionTitle("الصلاة والوقت") }
            tileGrid(listOf(
                TileSpec("تفصيل الأوقات", "الكراهة، أجزاء الليل، الساعات الزمانية", Icons.Filled.Schedule) { go(Routes.PRAYER_PARTS) },
                TileSpec("سجل الصلوات والقضاء", "المتابعة اليومية وحاسبة الفوائت", Icons.Filled.CheckCircle) { go(Routes.PRAYER_LOG) },
                TileSpec("المسبحة", "أذكار ما بعد الصلاة وعداد حر", Icons.Filled.TouchApp) { go(Routes.TASBIH) },
                TileSpec("القبلة بالشمس والظل", "دون بوصلة، ورصد الشمس فوق الكعبة", Icons.Filled.WbSunny) { go(Routes.QIBLA_SUN) },
                TileSpec("صلاة المسافر", "المسافة والقصر ومواقيت الوجهة", Icons.Filled.Luggage) { go(Routes.TRAVEL_PRAYER) },
                TileSpec("المواقيت في عدة مدن", "مقارنة الأوقات بين المدن", Icons.Filled.Public) { go(Routes.MULTI_CITY) }
            ))
            item { SectionTitle("الرحلات والصحراء (دون إنترنت)") }
            tileGrid(listOf(
                TileSpec("الملاحة والعودة", "حفظ الأماكن والرجوع إليها", Icons.Filled.MyLocation) { go(Routes.NAVIGATOR) },
                TileSpec("الطوارئ", "موقعي، SOS بالكشاف، أرقام النجدة", Icons.Filled.Sos) { go(Routes.EMERGENCY) },
                TileSpec("الاتجاه بالشمس والنجوم", "الشمال دون بوصلة", Icons.Filled.WbSunny) { go(Routes.NATURAL_NAV) },
                TileSpec("الماء والحرارة", "حاجة الماء وأوقات المشي الآمنة", Icons.Filled.WaterDrop) { go(Routes.WATER_HEAT) },
                TileSpec("الضغط والارتفاع", "البارومتر وتحذير العواصف", Icons.Filled.Speed) { go(Routes.BAROMETER) },
                TileSpec("دليل الرحلات", "إرشادات البر والصحراء", Icons.Filled.MenuBook) { go(Routes.SURVIVAL_GUIDE) }
            ))
            item { SectionTitle("القبلة والبوصلة") }
            tileGrid(listOf(
                TileSpec("البوصلة", "الشمس والقمر والقبلة", Icons.Filled.Explore) { go(Routes.COMPASS_2D) },
                TileSpec("بوصلة ثلاثية الأبعاد", "تتبع ميلان الهاتف", Icons.Filled.ViewInAr) { go(Routes.COMPASS_3D) },
                TileSpec("القبلة على الخريطة", "خط الاتجاه والمسافة", Icons.Filled.Mosque) { go(Routes.QIBLA_MAP) }
            ))
            item { SectionTitle("الخرائط") }
            tileGrid(listOf(
                TileSpec("الليل والنهار", "خط الظل والشفق الآن", Icons.Filled.DarkMode) { go(Routes.DAYNIGHT_MAP) },
                TileSpec("المناطق الزمنية", "خريطة التوقيت وساعات العالم", Icons.Filled.Schedule) { go(Routes.TIMEZONE_MAP) },
                TileSpec("المجال المغناطيسي", "الانحراف المغناطيسي حول العالم", Icons.Filled.Navigation) { go(Routes.MAGNETIC_MAP) }
            ))
            item { SectionTitle("التقاويم والجداول") }
            tileGrid(listOf(
                TileSpec("التواريخ", "اليوم في كل التقاويم واليوم اليولياني", Icons.Filled.Event) { go(Routes.DATES) },
                TileSpec("تحويل التاريخ", "بين التقاويم الأربعة", Icons.Filled.SwapHoriz) { go(Routes.CONVERTER) },
                TileSpec("حساب العمر", "بالميلادي والهجري", Icons.Filled.Cake) { go(Routes.AGE) },
                TileSpec("الجداول والتصدير", "PDF وCSV والطباعة والمشاركة", Icons.Filled.TableChart) { go(Routes.TABLES) }
            ))
            item { SectionTitle("التطبيق") }
            tileGrid(listOf(
                TileSpec("المواقع", "GPS والمدن المحفوظة", Icons.Filled.Place) { go(Routes.LOCATIONS) },
                TileSpec("الإعدادات", "المظهر والتقاويم والتنبيهات", Icons.Filled.Settings) { go(Routes.SETTINGS) },
                TileSpec("معلومات الجهاز", "الحساسات والشاشة والنظام", Icons.Filled.PhoneAndroid) { go(Routes.DEVICE) }
            ))
        }
    }
}
