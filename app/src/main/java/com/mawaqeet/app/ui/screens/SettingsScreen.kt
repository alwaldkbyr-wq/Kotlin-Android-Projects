package com.mawaqeet.app.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.CalendarKind
import com.mawaqeet.app.astro.Calendars
import com.mawaqeet.app.astro.HijriMethod
import com.mawaqeet.app.astro.SolarNames
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.Routes
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.theme.AppPalette
import com.mawaqeet.app.ui.theme.AppTheme

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, enabled: Boolean = true, note: String? = null, onChange: (Boolean) -> Unit) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) o.ink else o.muted, style = MaterialTheme.typography.bodyLarge)
            if (note != null) Text(note, color = o.muted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text, Modifier.padding(top = 8.dp, bottom = 4.dp), color = AppTheme.ornament.muted, style = MaterialTheme.typography.labelLarge)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val o = AppTheme.ornament
    val s = env.settings
    val palette = AppPalette.of(s.palette)
    val forced = palette.forcedDark

    val canExact = if (Build.VERSION.SDK_INT >= 31) {
        runCatching { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() }.getOrDefault(true)
    } else true
    val version = remember(ctx) { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0" }

    ScreenScaffold(title = "الإعدادات", onBack = env.back) {
        ScreenList {
            // ───── المظهر ─────
            item {
                GlassCard(title = "المظهر") {
                    SettingsSwitchRow(
                        "الوضع الداكن", forced ?: s.dark, enabled = forced == null,
                        note = if (forced != null) "هذا الثيم يفرض ${if (forced) "الوضع الداكن" else "الوضع الفاتح"}" else null
                    ) { v -> env.update { it.copy(dark = v) } }
                    SettingsLabel("الثيم")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppPalette.entries.forEach { p ->
                            GlassChip(selected = p.key == s.palette, onClick = { env.update { it.copy(palette = p.key) } }, label = { Text(p.label, maxLines = 1, softWrap = false) })
                        }
                    }
                    Text(
                        "أيقونة التطبيق تتغير لتطابق الثيم بعد الخروج من التطبيق (قد تحتاج الشاشة الرئيسية لحظات لتحديثها).",
                        color = AppTheme.ornament.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    SettingsSwitchRow("الحركة والتأثيرات", s.motion) { v -> env.update { it.copy(motion = v) } }
                    SettingsLabel("وضع الأداء")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "تلقائي", 1 to "جودة كاملة", 2 to "خفيف").forEach { (k, l) ->
                            GlassChip(selected = s.perfMode == k, onClick = { env.update { it.copy(perfMode = k) } }, label = { Text(l) })
                        }
                    }
                }
            }

            // ───── العرض ─────
            item {
                GlassCard(title = "العرض") {
                    SettingsSwitchRow("نظام 24 ساعة", s.h24, note = "مثال: ${Fmt.time(env.now, env.zone, s.h24)}") { v -> env.update { it.copy(h24 = v) } }
                    SettingsSwitchRow(
                        "أسماء الأشهر المغاربية", s.maghrebMonths,
                        note = if (s.maghrebMonths) "جانفي، فيفري، أفريل، ماي، جوان…" else "يناير، فبراير، أبريل، مايو، يونيو…"
                    ) { v -> env.update { it.copy(maghrebMonths = v) } }
                    SettingsSwitchRow("إظهار الثواني", s.showSeconds) { v -> env.update { it.copy(showSeconds = v) } }
                }
            }

            // ───── التقاويم ─────
            item {
                GlassCard(title = "التقاويم") {
                    SettingsLabel("طريقة حساب الهجري")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HijriMethod.entries.forEach { m ->
                            GlassChip(selected = s.hijriMethod == m, onClick = { env.update { it.copy(hijriMethod = m) } }, label = { Text(m.ar) })
                        }
                    }
                    SettingsLabel("تصحيح التاريخ الهجري (أيام)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { env.update { it.copy(hijriAdjust = (it.hijriAdjust - 1).coerceIn(-2, 2)) } }, enabled = s.hijriAdjust > -2) {
                            Icon(Icons.Filled.Remove, "إنقاص", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            (if (s.hijriAdjust > 0) "+" else "") + s.hijriAdjust,
                            Modifier.width(48.dp), color = o.ink, style = MaterialTheme.typography.titleLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick = { env.update { it.copy(hijriAdjust = (it.hijriAdjust + 1).coerceIn(-2, 2)) } }, enabled = s.hijriAdjust < 2) {
                            Icon(Icons.Filled.Add, "زيادة", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("اليوم: ${env.hijri(env.today)}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    SettingsLabel("أسماء أشهر الهجري الشمسي")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SolarNames.entries.forEach { n ->
                            GlassChip(selected = s.solarNames == n, onClick = { env.update { it.copy(solarNames = n) } }, label = { Text(n.ar, maxLines = 1, softWrap = false) })
                        }
                    }
                    Text(
                        "مثال: " + s.solarNames.months.take(3).joinToString("، ") + " … • اليوم: " + env.solarHijri(env.today),
                        Modifier.padding(top = 6.dp), color = o.muted, style = MaterialTheme.typography.labelMedium
                    )
                    SettingsLabel("التقويم الرئيسي في شبكة الشهر")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "ميلادي", 1 to "يولياني", 2 to "هجري", 3 to "هجري شمسي").forEach { (k, l) ->
                            GlassChip(selected = s.mainCalendar == k, onClick = { env.update { it.copy(mainCalendar = k) } }, label = { Text(l) })
                        }
                    }
                }
            }

            // ───── الصلاة والتنبيهات ─────
            item {
                val enabledCount = s.notify.count { it }
                GlassCard(title = "الصلاة والتنبيهات") {
                    InfoRow("طريقة الحساب", s.method.ar)
                    InfoRow("التنبيهات", if (enabledCount == 0) "متوقفة" else "$enabledCount من 6" + if (s.notifyBefore > 0) " • قبل ${s.notifyBefore} د" else "")
                    InfoRow("الصوت", if (s.notifySound) "صوت الإشعار" else "صامت")
                    Spacer(Modifier.height(8.dp))
                    GlassOutlinedButton(onClick = { env.navigate(Routes.PRAYER_SETTINGS) }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Text("إعدادات الصلاة والتنبيهات")
                    }
                    if (!canExact) {
                        Spacer(Modifier.height(8.dp))
                        Text("لضمان وصول التنبيه في وقته بالضبط، اسمح للتطبيق بالمنبّهات الدقيقة.", color = o.muted, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        GlassTonalButton(
                            onClick = {
                                runCatching {
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        ctx.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), nav = true
                        ) { Text("السماح بالمنبّهات الدقيقة") }
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassTonalButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(), nav = true
                    ) {
                        Icon(Icons.Filled.Notifications, null, Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إعدادات إشعارات التطبيق")
                    }
                }
            }

            // ───── الموقع ─────
            item {
                GlassCard(title = "الموقع") {
                    InfoRow("المكان", env.place.name, emphasize = true)
                    InfoRow("الإحداثيات", Fmt.latLon(env.lat, env.lon))
                    InfoRow("المنطقة الزمنية", env.place.zoneId)
                    Spacer(Modifier.height(8.dp))
                    GlassOutlinedButton(onClick = { env.navigate(Routes.LOCATIONS) }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Icon(Icons.Filled.Place, null, Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إدارة المواقع")
                    }
                }
            }

            // ───── الودجات ─────
            item {
                GlassCard(title = "الودجات") {
                    Text(
                        "يتوفر ثلاثة ودجات للشاشة الرئيسية: «التاريخ» و«الصلاة القادمة» (مع عدّ تنازلي حي) و«القمر».\n" +
                            "لإضافتها: اضغط مطولاً على مساحة فارغة في الشاشة الرئيسية، ثم اختر «الودجات»، وابحث عن «الساهور» واسحب الودجت الذي تريده.",
                        color = o.ink, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ───── عن التطبيق ─────
            item {
                GlassCard(title = "عن التطبيق") {
                    InfoRow("الإصدار", version)
                    Text(
                        "«مواقيت» تقويم فلكي وإسلامي: مواقيت الصلاة، التقاويم الهجري والميلادي والهجري الشمسي، أطوار القمر والمنازل، الشمس والكسوف والخسوف، القبلة والبوصلة وخرائط السماء.",
                        Modifier.padding(top = 4.dp), color = o.ink, style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "لا يجمع التطبيق أي بيانات، ويعمل كاملاً دون إنترنت: كل الحسابات والخرائط على هاتفك",
                        Modifier.padding(top = 8.dp), color = o.muted, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassOutlinedButton(onClick = { env.navigate(Routes.DEVICE) }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Text("معلومات الجهاز والحساسات")
                    }
                }
            }
        }
    }
}
