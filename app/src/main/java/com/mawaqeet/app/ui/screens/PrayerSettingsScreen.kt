package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.HighLatRule
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerMethod
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassChip
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.theme.AppTheme

@Composable
private fun PrayerSettingsStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { if (value > min) onChange(value - 1) }) { Icon(Icons.Filled.Remove, "إنقاص", tint = o.ink) }
        Text(
            (if (value > 0) "+" else "") + "$value د", Modifier.width(64.dp), textAlign = TextAlign.Center,
            color = if (value == 0) o.muted else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { if (value < max) onChange(value + 1) }) { Icon(Icons.Filled.Add, "زيادة", tint = o.ink) }
    }
}

private fun prayerSettingsMethodInfo(m: PrayerMethod): String {
    val isha = if (m.ishaMinutes > 0) "العشاء بعد المغرب بـ ${m.ishaMinutes} دقيقة" else "العشاء ${Fmt.num(m.isha, 1)}°"
    return "الفجر ${Fmt.num(m.fajr, 1)}° • $isha"
}

/** إعدادات الصلاة: الطريقة والعصر والتعديلات والتنبيهات */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerSettingsScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val s = env.settings
    // يُعاد الحساب عند تغيّر اليوم أو أي إعداد فقط (الشاشة تُعاد تركيبها كل ثانية)
    val preview = remember(env.today, s) { env.prayers(env.today) }

    ScreenScaffold(title = "إعدادات الصلاة", onBack = env.back, subtitle = env.place.name) {
        ScreenList {
            item {
                GlassCard(title = "معاينة مواقيت اليوم", subtitle = "تتغير فوراً مع أي تعديل") {
                    Prayer.values().forEach { InfoRow(it.ar, env.time(preview[it]), emphasize = true) }
                }
            }

            item {
                GlassCard(title = "طريقة الحساب", subtitle = "زوايا الفجر والعشاء") {
                    PrayerMethod.values().forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { env.update { it.copy(method = m) } }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = s.method == m, onClick = { env.update { it.copy(method = m) } })
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(m.ar, color = o.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = if (s.method == m) FontWeight.Black else FontWeight.Medium)
                                Text(prayerSettingsMethodInfo(m), color = o.muted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(title = "وقت العصر") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassChip(selected = !s.hanafiAsr, onClick = { env.update { it.copy(hanafiAsr = false) } }, label = { Text("الشافعي والمالكي والحنبلي (مثل)") })
                        GlassChip(selected = s.hanafiAsr, onClick = { env.update { it.copy(hanafiAsr = true) } }, label = { Text("الحنفي (مثلان)") })
                    }
                }
            }

            item {
                GlassCard(title = "المناطق ذات خطوط العرض العالية", subtitle = "حين يغيب الفجر أو العشاء أو يطول الشفق") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HighLatRule.values().forEach { r ->
                            GlassChip(selected = s.highLat == r, onClick = { env.update { it.copy(highLat = r) } }, label = { Text(r.ar) })
                        }
                    }
                }
            }

            item {
                GlassCard(title = "تعديل يدوي بالدقائق", subtitle = "من −30 إلى +30 لكل وقت") {
                    Prayer.values().forEachIndexed { idx, p ->
                        PrayerSettingsStepper(p.ar, s.offsets.getOrElse(idx) { 0 }, -30, 30) { nv ->
                            env.update { st -> st.copy(offsets = st.offsets.mapIndexed { i, v -> if (i == idx) nv else v }) }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text("يُحسب الظهر بعد الزوال بدقيقة على الأقل.", color = o.muted, style = MaterialTheme.typography.labelMedium)
                }
            }

            item {
                GlassCard(title = "دقائق الاحتياط", subtitle = "كما تفعل التقاويم الرسمية") {
                    PrayerSettingsStepper("الظهر (بعد الزوال)", s.dhuhrSafety, 0, 15) { nv -> env.update { it.copy(dhuhrSafety = nv) } }
                    PrayerSettingsStepper("المغرب (بعد الغروب)", s.maghribSafety, 0, 15) { nv -> env.update { it.copy(maghribSafety = nv) } }
                }
            }

            item {
                GlassCard(title = "التنبيهات") {
                    Text("التنبيه قبل الوقت", color = o.muted, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        listOf(0, 5, 10, 15, 20, 30).forEach { m ->
                            GlassChip(selected = s.notifyBefore == m, onClick = { env.update { it.copy(notifyBefore = m) } }, label = { Text(if (m == 0) "في الوقت" else "$m د") })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("صوت التنبيه", Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = s.notifySound, onCheckedChange = { v -> env.update { it.copy(notifySound = v) } })
                    }
                }
            }
            item { Note("تفعيل التنبيه لكل صلاة يكون من شاشة المواقيت (أيقونة الجرس). تنبيه «الشروق» يذكّر بانتهاء وقت الفجر.") }
        }
    }
}
