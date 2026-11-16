package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.DayStepper
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.TileSpec
import com.mawaqeet.app.ui.components.tileGrid
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme

/** تبويب «الصلاة»: مواقيت اليوم والعدّ التنازلي والتنبيهات */
@Composable
fun PrayerScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val s = env.settings
    var date by remember { mutableStateOf(env.today) }
    val isToday = date == env.today
    val now = env.now

    val day = remember(date, s) { env.prayers(date) }
    val nextDay = remember(date, s) { env.prayers(date.plusDays(1)) }
    val prevDay = remember(date, s) { env.prayers(date.minusDays(1)) }

    val events = Prayer.values().mapNotNull { p -> day[p]?.let { p to it } }.sortedBy { it.second }
    val nextEv: Pair<Prayer, Long>? = events.firstOrNull { it.second > now }
        ?: nextDay[Prayer.FAJR]?.let { Prayer.FAJR to it }
    val lastEv: Pair<Prayer, Long>? = events.lastOrNull { it.second <= now }
        ?: prevDay[Prayer.ISHA]?.let { Prayer.ISHA to it }
    val nextInTable = isToday && nextEv != null && events.contains(nextEv)
    val currentPrayer: Prayer? = if (isToday && lastEv != null && lastEv.first != Prayer.SUNRISE && events.contains(lastEv)) lastEv.first else null

    // معلومات إضافية: منتصف الليل وثلث الليل الأخير والضحى
    val maghrib = day[Prayer.MAGHRIB]
    val fajrNext = nextDay[Prayer.FAJR]
    val night = if (maghrib != null && fajrNext != null && fajrNext > maghrib) fajrNext - maghrib else null
    val midnight = if (night != null && maghrib != null) maghrib + night / 2 else null
    val lastThird = if (night != null && maghrib != null) maghrib + night * 2 / 3 else null
    val duha = day[Prayer.SUNRISE]?.let { it + 15 * MIN_MS }

    fun shareText(): String = buildString {
        appendLine("مواقيت الصلاة — ${env.place.name}")
        appendLine("${env.gregorian(date)} • ${env.hijri(date)}")
        appendLine("(${s.method.ar})")
        Prayer.values().forEach { appendLine("${it.ar}: ${env.time(day[it])}") }
    }

    ScreenScaffold(title = "مواقيت الصلاة", subtitle = env.place.name, topLevel = true, actions = { ShareAction { shareText() } }) {
        ScreenList {
            item { DayStepper(date, { date = it }) }

            if (isToday && nextEv != null) {
                item {
                    GlassCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الصلاة القادمة", color = o.muted, style = MaterialTheme.typography.labelLarge)
                            Text(nextEv.first.ar, color = o.gold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text(
                                Fmt.countdown(nextEv.second - now), color = o.ink, fontSize = 40.sp,
                                fontWeight = FontWeight.Black, textAlign = TextAlign.Center
                            )
                            Text("عند ${env.time(nextEv.second)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                            if (lastEv != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "مضى على ${lastEv.first.ar}: ${Fmt.span(now - lastEv.second)}",
                                    color = o.muted, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(title = "المواقيت", subtitle = if (s.notifyBefore > 0) "تنبيه قبل الوقت بـ ${s.notifyBefore} دقيقة" else null) {
                    Prayer.values().forEachIndexed { idx, p ->
                        val isNext = nextInTable && nextEv?.first == p
                        val isCur = currentPrayer == p
                        val on = s.notify.getOrElse(idx) { false }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.ar, color = if (isNext) o.gold else o.ink,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = if (isNext) FontWeight.Black else FontWeight.Bold
                                )
                                if (p == Prayer.SUNRISE) Text("التنبيه = انتهاء وقت الفجر", color = o.muted, fontSize = 11.sp)
                            }
                            if (isNext) { Pill("التالية", MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                            if (isCur) { Pill("الآن", o.success); Spacer(Modifier.width(8.dp)) }
                            Text(
                                env.time(day[p]), color = if (isNext) o.gold else o.ink,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black
                            )
                            IconButton(onClick = {
                                env.update { st -> st.copy(notify = st.notify.mapIndexed { i, b -> if (i == idx) !b else b }) }
                            }) {
                                Icon(
                                    if (on) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                                    if (on) "إيقاف التنبيه" else "تفعيل التنبيه",
                                    tint = if (on) MaterialTheme.colorScheme.primary else o.muted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(title = "أوقات إضافية (تقديرية)", subtitle = "محسوبة من المواقيت أعلاه") {
                    InfoRow("منتصف الليل (بين المغرب والفجر)", env.time(midnight))
                    InfoRow("بداية الثلث الأخير من الليل", env.time(lastThird))
                    InfoRow("بداية وقت الضحى (الشروق + 15 د)", env.time(duha))
                    if (night != null) InfoRow("طول الليل الشرعي", Fmt.span(night))
                }
            }

            item {
                GlassCard(title = "طريقة الحساب") {
                    InfoRow("الطريقة", s.method.ar)
                    InfoRow("المذهب في العصر", if (s.hanafiAsr) "الحنفي (مثلان)" else "الجمهور (مثل واحد)")
                    Spacer(Modifier.height(6.dp))
                    GlassOutlinedButton(onClick = { env.navigate(Routes.PRAYER_SETTINGS) }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Icon(Icons.Filled.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إعدادات الصلاة والتنبيهات")
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassOutlinedButton(onClick = { env.navigate(Routes.TABLES) }, modifier = Modifier.weight(1f), nav = true) {
                        Icon(Icons.Filled.TableChart, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("الجدول الشهري")
                    }
                    GlassOutlinedButton(onClick = { env.navigate(Routes.COMPASS_2D) }, modifier = Modifier.weight(1f), nav = true) {
                        Icon(Icons.Filled.Explore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("القبلة")
                    }
                }
            }
            item { SectionTitle("أدوات الصلاة") }
            tileGrid(listOf(
                TileSpec("تفصيل الأوقات", "الكراهة وأجزاء الليل", Icons.Filled.Schedule) { env.navigate(Routes.PRAYER_PARTS) },
                TileSpec("سجل الصلوات", "المتابعة والقضاء", Icons.Filled.CheckCircle) { env.navigate(Routes.PRAYER_LOG) },
                TileSpec("المسبحة", "أذكار بعد الصلاة", Icons.Filled.TouchApp) { env.navigate(Routes.TASBIH) },
                TileSpec("القبلة بالشمس", "دون بوصلة", Icons.Filled.WbSunny) { env.navigate(Routes.QIBLA_SUN) },
                TileSpec("صلاة المسافر", "القصر ومواقيت الوجهة", Icons.Filled.Luggage) { env.navigate(Routes.TRAVEL_PRAYER) },
                TileSpec("عدة مدن", "مقارنة المواقيت", Icons.Filled.Public) { env.navigate(Routes.MULTI_CITY) }
            ))
            item { Note("الأوقات محسوبة فلكياً للموقع المحدّد وقد تختلف دقائق قليلة عن التقاويم الرسمية المحلية؛ يمكن ضبط الفروق من إعدادات الصلاة.") }
        }
    }
}
