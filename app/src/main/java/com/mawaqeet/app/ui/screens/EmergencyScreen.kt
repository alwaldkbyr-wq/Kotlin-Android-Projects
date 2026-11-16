package com.mawaqeet.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.BatteryManager
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.Exporter
import com.mawaqeet.app.ui.components.Geo
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.rememberGpsFix
import com.mawaqeet.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

/* ───────────── أدوات داخلية ───────────── */

private tailrec fun Context.emFindActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.emFindActivity()
    else -> null
}

/** التحكم في مصباح الكاميرا (لا يحتاج إذن الكاميرا من أندرويد 6) */
private class EmTorch(ctx: Context) {
    private val cm: CameraManager? = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    val id: String? = runCatching {
        val m = cm ?: return@runCatching null
        val ids = m.cameraIdList.filter { m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        ids.firstOrNull { m.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            ?: ids.firstOrNull()
    }.getOrNull()

    fun set(on: Boolean) {
        val i = id ?: return
        runCatching { cm?.setTorchMode(i, on) }
    }
}

/** نمط SOS بالمورس: (مضاء؟ ، مدة بالميلي ثانية): نقطة 200 وشرطة 600 وفاصل حرف 600 وفاصل كلمة 1400 */
private val EM_SOS: List<Pair<Boolean, Long>> = buildList {
    fun letter(d: Long) { repeat(3) { add(true to d); add(false to 200L) }; add(false to 400L) }
    letter(200L); letter(600L); letter(200L)
    add(false to 800L)
}

private fun emToneBuffer(sampleRate: Int, freq: Double): ShortArray {
    val out = ArrayList<Short>()
    for ((on, ms) in EM_SOS) {
        val n = (sampleRate * ms / 1000L).toInt()
        for (k in 0 until n) {
            if (!on) { out.add(0); continue }
            val fade = minOf(1.0, minOf(k, n - 1 - k) / (sampleRate * 0.005))
            out.add((Math.sin(2.0 * Math.PI * freq * k / sampleRate) * 30000.0 * fade).toInt().toShort())
        }
    }
    return out.toShortArray()
}

private fun emBatteryPercent(ctx: Context): Int? {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level < 0 || scale <= 0) null else level * 100 / scale
}

private fun emDial(ctx: Context, number: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "لا يوجد تطبيق اتصال", Toast.LENGTH_SHORT).show()
    }
}

/* ───────────── الشاشة ───────────── */

@Composable
fun EmergencyScreen() {
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val o = AppTheme.ornament
    val fix = rememberGpsFix(2000L).value

    // القراءة الحية، وإلا الموقع المحفوظ في التطبيق
    val lat = fix?.lat ?: env.lat
    val lon = fix?.lon ?: env.lon
    val accText = if (fix != null) "±${fix.accuracy.toInt()} م" else "غير معروفة"
    val timeText = if (fix != null) env.dateTime(fix.time) else "موقع محفوظ في التطبيق"

    val coordsLine = String.format(Locale.US, "%.6f, %.6f", lat, lon)
    val dmsLine = "${Geo.dms(lat, true)}  ${Geo.dms(lon, false)}"
    val message = "أحتاج مساعدة. موقعي: $coordsLine ($dmsLine) — الدقة $accText — الوقت: $timeText"

    // وضعية المصباح: 0 مطفأ، 1 SOS، 2 ضوء ثابت
    var torchMode by remember { mutableIntStateOf(0) }
    var strobe by remember { mutableStateOf(false) }
    var strobeWhite by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf(false) }
    val torch = remember { EmTorch(ctx) }
    val anyActive = torchMode != 0 || strobe || sound

    LaunchedEffect(torchMode) {
        when (torchMode) {
            1 -> try {
                while (true) {
                    for ((on, ms) in EM_SOS) { torch.set(on); delay(ms) }
                }
            } finally { torch.set(false) }
            2 -> try {
                torch.set(true); awaitCancellation()
            } finally { torch.set(false) }
        }
    }
    LaunchedEffect(strobe) {
        if (strobe) {
            var w = true
            while (true) { strobeWhite = w; w = !w; delay(250) }
        }
    }
    LaunchedEffect(sound) {
        if (sound) withContext(Dispatchers.Default) {
            val rate = 44100
            val buf = emToneBuffer(rate, 3000.0)
            var track: AudioTrack? = null
            try {
                val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(min, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                while (isActive) {
                    var off = 0
                    while (off < buf.size && isActive) {
                        val len = minOf(2205, buf.size - off)
                        track.write(buf, off, len)
                        off += len
                    }
                }
            } catch (e: Exception) {
                // الجهاز لا يدعم التشغيل
            } finally {
                runCatching { track?.pause(); track?.flush(); track?.release() }
            }
        }
    }
    // سطوع كامل وإبقاء الشاشة مضاءة أثناء الإشارات
    DisposableEffect(anyActive, strobe) {
        val act = ctx.emFindActivity()
        val w = act?.window
        if (w != null && anyActive) {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (strobe) {
                val lp = w.attributes; lp.screenBrightness = 1f; w.attributes = lp
            }
        }
        onDispose {
            if (w != null) {
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val lp = w.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = lp
            }
        }
    }
    DisposableEffect(Unit) { onDispose { torch.set(false) } }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(title = "الطوارئ", onBack = env.back, subtitle = "موقعك وإشارات الاستغاثة دون إنترنت") {
            ScreenList {
                // ───── الموقع ─────
                item {
                    GlassCard(title = "موقعي الآن", subtitle = if (fix != null) "من الأقمار الصناعية" else "لم تصل قراءة GPS بعد") {
                        Text(coordsLine, color = o.ink, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(dmsLine, color = o.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        InfoRow("UTM", Geo.utm(lat, lon))
                        InfoRow("الدقة", accText)
                        InfoRow("وقت القراءة", timeText)
                        if (fix?.altitude != null) InfoRow("الارتفاع", "${fix.altitude.toInt()} م")
                        if (fix == null) {
                            Spacer(Modifier.height(6.dp))
                            Text("جارٍ انتظار إشارة الأقمار الصناعية… اخرج إلى مكان مكشوف", color = o.warning, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            GlassButton(onClick = { env.requestGps() }, nav = true, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("تفعيل تحديد الموقع")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassButton(
                            onClick = {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", message).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(ctx, "لا يوجد تطبيق رسائل", Toast.LENGTH_SHORT).show()
                                }
                            },
                            nav = true, modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Filled.Sms, null); Spacer(Modifier.width(8.dp)); Text("إرسال موقعي برسالة SMS") }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassTonalButton(onClick = { Exporter.shareText(ctx, message) }, nav = true, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("مشاركة")
                            }
                            GlassTonalButton(
                                onClick = {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("location", message))
                                    Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
                                },
                                nav = true, modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("نسخ") }
                        }
                        Note("الرسالة النصية SMS تصل بوجود أي شبكة جوال، ولو لم يتوفر إنترنت.")
                    }
                }

                // ───── أرقام الطوارئ ─────
                item {
                    GlassCard(title = "أرقام الطوارئ في الجزائر", subtitle = "اضغط على الرقم لفتح الاتصال") {
                        val services = listOf(
                            "الحماية المدنية" to listOf("14", "1021"),
                            "الدرك الوطني" to listOf("1055"),
                            "الأمن الوطني" to listOf("17", "1548"),
                            "الغابات" to listOf("1070")
                        )
                        services.forEach { (name, nums) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), color = o.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                nums.forEach { n ->
                                    GlassTonalButton(onClick = { emDial(ctx, n) }, nav = true, modifier = Modifier.padding(start = 6.dp)) {
                                        Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(n, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("الرقم الدولي", color = o.ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("يعمل في كثير من الدول", color = o.muted, style = MaterialTheme.typography.labelMedium)
                            }
                            GlassTonalButton(onClick = { emDial(ctx, "112") }, nav = true) {
                                Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("112", fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Note("اتصل حتى لو ظهرت «مكالمات الطوارئ فقط»؛ فقد تعمل المكالمة عبر أي شبكة متاحة.")
                    }
                }

                // ───── الإشارات الضوئية والصوتية ─────
                item {
                    GlassCard(title = "إشارات الاستغاثة", subtitle = "SOS ضوئية وصوتية") {
                        if (torch.id == null) Text("لا يتوفر مصباح (فلاش) في هذا الجهاز؛ استخدم وميض الشاشة.", color = o.warning, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmToggle("SOS بالمصباح", Icons.Filled.FlashOn, torchMode == 1, Modifier.weight(1f)) { torchMode = if (torchMode == 1) 0 else 1 }
                            EmToggle("ضوء ثابت", Icons.Filled.Highlight, torchMode == 2, Modifier.weight(1f)) { torchMode = if (torchMode == 2) 0 else 2 }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmToggle("وميض الشاشة", Icons.Filled.Brightness7, strobe, Modifier.weight(1f)) { strobe = !strobe }
                            EmToggle("صافرة SOS", Icons.Filled.VolumeUp, sound, Modifier.weight(1f)) { sound = !sound }
                        }
                        Note("نمط SOS بالمورس: ثلاث نقاط، ثلاث شرطات، ثلاث نقاط (··· −−− ···). ارفع مستوى صوت الوسائط للصافرة (نغمة 3 كيلوهرتز تُسمع من بعيد). كل الإشارات تتوقف عند مغادرة الشاشة.")
                    }
                }

                // ───── البطارية ─────
                item {
                    val pct = emBatteryPercent(ctx)
                    GlassCard(title = "البطارية", subtitle = "حافظ عليها: هاتفك وسيلة إنقاذك") {
                        InfoRow("مستوى الشحن", if (pct != null) "$pct%" else "--", valueColor = when {
                            pct == null -> Color.Unspecified
                            pct < 20 -> o.danger
                            pct < 50 -> o.warning
                            else -> o.success
                        }, emphasize = true)
                        Text("وميض الشاشة والصافرة والمصباح تستهلك الشحن بسرعة؛ استعملها عند سماع محرك أو رؤية طائرة أو مركبة فقط، ولا تشغّلها باستمرار.", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        EmBullet("فعّل وضع الطيران: يبقى GPS يعمل ويوفّر شحن البحث عن الشبكة (شغّله مؤقتاً لإرسال الرسائل).")
                        EmBullet("خفّض سطوع الشاشة وأغلق التطبيقات والبلوتوث والمزامنة.")
                        EmBullet("أطفئ الهاتف عند عدم الحاجة، وشغّله كل ساعة أو ساعتين للتحقق من الشبكة.")
                        EmBullet("أبقِ الهاتف دافئاً قريباً من جسمك ليلاً؛ البرد يُفرغ البطارية.")
                    }
                }

                // ───── الإرشادات ─────
                item { SectionTitle("إرشادات الاستغاثة") }
                item {
                    GlassCard(title = "المرآة العاكسة", subtitle = "ترى من مسافة تتجاوز 10 كم في الشمس") {
                        EmBullet("استعمل مرآة أو سطحاً لامعاً (غطاء علبة، شاشة هاتف مطفأة، قرص مضغوط).")
                        EmBullet("طريقة حرف V: ارفع يدك وافتح إصبعين على شكل V أمام الهدف (طائرة أو مركبة).")
                        EmBullet("أمسك المرآة قرب وجهك وحرّكها حتى يقع انعكاس الشمس على إصبعي V، ثم أمِل المرآة قليلاً ليمرّ الضوء بينهما نحو الهدف.")
                        EmBullet("حرّك الانعكاس ببطء ذهاباً وإياباً على الهدف، ولا تتوقف حتى لو لم تظن أنهم رأوك.")
                    }
                }
                item {
                    GlassCard(title = "إشارات الأرض إلى الجو", subtitle = "تُصنع بأحجار أو ثياب أو حفر، بحجم 3 أمتار فأكثر") {
                        InfoRow("SOS", "استغاثة عامة")
                        InfoRow("V", "نحتاج مساعدة")
                        InfoRow("X", "نحتاج مساعدة طبية")
                        InfoRow("سهم →", "نتجه في هذا الاتجاه")
                        Note("اختر لوناً يتباين مع الأرض (داكن على الرمل)، وضعها في مكان مكشوف مفتوح، ومدّدها على امتداد خط مستقيم.")
                    }
                }
                item {
                    GlassCard(title = "قاعدة الثلاثة", subtitle = "إشارة الاستغاثة الدولية") {
                        Text("ثلاثة من أي شيء تعني خطراً: ثلاث صفارات، ثلاث وميضات ضوء، ثلاث نيران، ثلاث طلقات، ثلاثة أكوام من الحجارة، كلها بفواصل متساوية. كرّرها بعد دقيقة.", color = o.ink, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item { Note("المرجع: إرشادات البقاء العامة. هذه الشاشة للمساعدة ولا تغني عن الاتصال بجهات الإنقاذ.") }
            }
        }

        // ───── وميض الشاشة بملء الشاشة ─────
        if (strobe) {
            Box(
                Modifier.fillMaxSize().zIndex(10f)
                    .background(if (strobeWhite) Color.White else Color(0xFFD50000))
                    .clickable { strobe = false },
                contentAlignment = Alignment.Center
            ) {
                Text("اضغط في أي مكان للإيقاف", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.alpha(0.6f))
            }
        }
    }
}

@Composable
private fun EmToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (on) {
        GlassButton(onClick = onClick, nav = true, modifier = modifier) {
            Icon(icon, null); Spacer(Modifier.width(6.dp)); Text("إيقاف: $label", fontWeight = FontWeight.Bold)
        }
    } else {
        GlassOutlinedButton(onClick = onClick, nav = true, modifier = modifier) {
            Icon(icon, null); Spacer(Modifier.width(6.dp)); Text(label)
        }
    }
}

@Composable
private fun EmBullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("•", color = AppTheme.ornament.gold, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = AppTheme.ornament.ink, style = MaterialTheme.typography.bodyMedium)
    }
}
