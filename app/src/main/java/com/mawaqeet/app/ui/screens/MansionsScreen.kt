package com.mawaqeet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.astro.DAY_MS
import com.mawaqeet.app.astro.Mansions
import com.mawaqeet.app.astro.MIN_MS
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.norm360
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Busy
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.rememberComputed
import com.mawaqeet.app.ui.theme.AppTheme

/** منازل القمر الثمانية والعشرون والأنواء */
@Composable
fun MansionsScreen() {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val now = env.now
    val jde = jdeOf(jdOf(now))
    val (moonIdx, moonFrac) = Mansions.moonMansion(now)
    val sunIdx = Mansions.sunMansion(now)

    // متى يدخل القمر المنزلة التالية: بحث للأمام بخطوات صغيرة ثم تدقيق
    val nextEntry: Long? = remember(moonIdx) {
        val t0 = System.currentTimeMillis()
        var a = t0
        var found: Long? = null
        var i = 0
        while (i < 100 && found == null) {
            val b = a + 30 * MIN_MS
            if (Mansions.moonMansion(b).first != moonIdx) {
                found = RiseSetCalc.refine(a, b) { if (Mansions.moonMansion(it).first == moonIdx) -1.0 else 1.0 }
            }
            a = b; i++
        }
        found
    }

    val entries = rememberComputed(env.today) { Mansions.sunEntries(env.now) }

    fun shareText(): String = buildString {
        appendLine("منازل القمر — ${env.date(now)}")
        appendLine("القمر في منزلة: ${Mansions.names[moonIdx]} (${Fmt.num(moonFrac * 100, 0)}%)")
        appendLine("الشمس في منزلة: ${Mansions.names[sunIdx]}")
        if (nextEntry != null) appendLine("دخول القمر ${Mansions.names[(moonIdx + 1) % 28]}: ${env.dateTime(nextEntry)}")
    }

    ScreenScaffold(title = "منازل القمر", onBack = env.back, subtitle = "الأنواء والمنازل الثمانية والعشرون", actions = { ShareAction { shareText() } }) {
        ScreenList {
            item {
                GlassCard(title = "الآن") {
                    InfoRow("منزلة القمر", Mansions.names[moonIdx], emphasize = true)
                    InfoRow("نسبة قطع المنزلة", "${Fmt.num(moonFrac * 100, 0)}%")
                    InfoRow("منزلة الشمس (الطالع)", Mansions.names[sunIdx], emphasize = true)
                    if (nextEntry != null) {
                        InfoRow("المنزلة التالية للقمر", Mansions.names[(moonIdx + 1) % 28])
                        InfoRow("يدخلها", env.dateTime(nextEntry))
                        InfoRow("بعد", Fmt.span(nextEntry - now))
                    }
                }
            }

            item {
                GlassCard(title = "المنازل الثمانية والعشرون", subtitle = "بالطول البروجي (درجات)") {
                    for (i in 0 until 28) {
                        val from = Mansions.lonOf(i, jde)
                        val to = norm360(from + Mansions.SPAN)
                        val isMoon = i == moonIdx
                        val isSun = i == sunIdx
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isMoon || isSun) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${i + 1}", Modifier.width(26.dp), color = o.muted, style = MaterialTheme.typography.labelLarge)
                            Text(
                                Mansions.names[i], Modifier.weight(1f), color = if (isMoon || isSun) o.gold else o.ink,
                                style = MaterialTheme.typography.titleSmall, fontWeight = if (isMoon || isSun) FontWeight.Black else FontWeight.Medium
                            )
                            if (isMoon) { Pill("القمر ${Fmt.num(moonFrac * 100, 0)}%", MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                            if (isSun) { Pill("الشمس", o.warning); Spacer(Modifier.width(6.dp)) }
                            Text("${Fmt.num(from, 1)}° – ${Fmt.num(to, 1)}°", color = o.muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item {
                GlassCard(title = "الأنواء: دخول الشمس المنازل", subtitle = "خلال سنة من اليوم") {
                    val list = entries
                    if (list == null) Busy() else {
                        list.forEach { (idx, t) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Mansions.names[idx], color = if (idx == sunIdx) o.gold else o.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(env.hijri(env.dateOf(t)), color = o.muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(env.date(t), color = o.ink, style = MaterialTheme.typography.bodyMedium)
                                    val d = Fmt.daysUntil(now, t)
                                    Text(if (d <= 0L) "الآن تقريباً" else "بعد $d يوم", color = o.muted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Note("المنازل هنا محسوبة فلكياً من الطول البروجي، بدءاً من منزلة «الشرطان» قرب النجم β الحمل (Beta Arietis)، وتتساوى المنازل في الطول (12.857° لكل منزلة). قد تختلف التقاويم التقليدية للأنواء عن هذه التواريخ بعدة أيام بحسب المرجع المعتمد.")
            }
        }
    }
}
