package com.mawaqeet.app.ui.screens

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.data.NavTarget
import com.mawaqeet.app.data.TrackPoint
import com.mawaqeet.app.data.Waypoint
import com.mawaqeet.app.data.WaypointKinds
import com.mawaqeet.app.data.WaypointStore
import com.mawaqeet.app.ui.components.BigButton
import com.mawaqeet.app.ui.components.ChipsSelector
import com.mawaqeet.app.ui.components.ConfirmDialog
import com.mawaqeet.app.ui.components.Exporter
import com.mawaqeet.app.ui.components.Field
import com.mawaqeet.app.ui.components.GlassAlertDialog
import com.mawaqeet.app.ui.components.GlassButton
import com.mawaqeet.app.ui.components.GlassOutlinedButton
import com.mawaqeet.app.ui.components.GlassTonalButton
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.GpsFix
import com.mawaqeet.app.ui.components.Geo
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.Pill
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.components.magneticDeclination
import com.mawaqeet.app.ui.components.rememberGpsFix
import com.mawaqeet.app.ui.components.rememberOrientation
import com.mawaqeet.app.ui.theme.AppTheme
import com.mawaqeet.app.ui.theme.CanvasFonts
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/* ───────────────────────── مساعدات ───────────────────────── */

private fun navNum(s: String): Double? {
    val sb = StringBuilder()
    for (ch in s.trim()) {
        when {
            ch in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
            ch in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
            ch == ',' || ch == '،' || ch == '٫' -> sb.append('.')
            else -> sb.append(ch)
        }
    }
    return sb.toString().toDoubleOrNull()
}

private fun navNorm180(d: Double): Double {
    var x = d % 360.0
    if (x > 180) x -= 360
    if (x <= -180) x += 360
    return x
}

private fun navCoordsText(title: String, lat: Double, lon: Double, alt: Double?): String {
    val us = Locale.US
    return buildString {
        appendLine(title)
        appendLine("عشري: ${String.format(us, "%.6f, %.6f", lat, lon)}")
        appendLine("DMS: ${Geo.dms(lat, true)}  ${Geo.dms(lon, false)}")
        appendLine("UTM: ${Geo.utm(lat, lon)}")
        if (alt != null) appendLine("الارتفاع: ${alt.toInt()} م")
        append("geo:${String.format(us, "%.6f,%.6f", lat, lon)}?q=${String.format(us, "%.6f,%.6f", lat, lon)}")
    }
}

private fun navVibrate(ctx: Context) {
    try {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150, 100, 300), -1))
    } catch (e: Exception) { /* لا اهتزاز */ }
}

private fun navTrackDistance(t: List<TrackPoint>): Double {
    var s = 0.0
    for (i in 1 until t.size) s += Geo.distanceM(t[i - 1].lat, t[i - 1].lon, t[i].lat, t[i].lon)
    return s
}

private fun navEta(distM: Double, speedMs: Double): String = Fmt.span((distM / speedMs * 1000).toLong())

/* ───────────────────────── الشاشة ───────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigatorScreen() {
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary

    WaypointStore.init(ctx)
    val version = WaypointStore.version // قراءة في الجسم لإعادة التركيب
    val points = remember(version) { WaypointStore.load(ctx) }
    val track = remember(version) { WaypointStore.loadTrack(ctx) }
    val recording = WaypointStore.recording
    val target = WaypointStore.target

    val fixState = rememberGpsFix(2000L)
    val fix: GpsFix? = fixState.value
    val orient = rememberOrientation().value
    val decl = remember(fix?.lat?.let { (it * 10).toInt() }, fix?.lon?.let { (it * 10).toInt() }) {
        if (fix != null) magneticDeclination(fix.lat, fix.lon) else magneticDeclination(env.lat, env.lon)
    }
    val heading: Double? = if (orient.available) orient.azimuthTrue(decl) else null

    var saveDialog by remember { mutableStateOf(false) }
    var manualDialog by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var clearTrackAsk by remember { mutableStateOf(false) }

    // إبقاء الشاشة مضاءة أثناء التسجيل
    DisposableEffect(recording) {
        view.keepScreenOn = recording
        onDispose { view.keepScreenOn = false }
    }

    // تسجيل الأثر: كل 20 م أو كل 30 ثانية مع حركة
    LaunchedEffect(fix, recording) {
        val f = fix
        if (recording && f != null && f.accuracy <= 60f) {
            val last = WaypointStore.lastTrackPoint(ctx)
            val now = System.currentTimeMillis()
            if (last == null) {
                WaypointStore.appendTrack(ctx, TrackPoint(f.lat, f.lon, f.altitude, now))
            } else {
                val d = Geo.distanceM(last.lat, last.lon, f.lat, f.lon)
                if (d >= 20.0 || (now - last.time >= 30_000L && d >= 5.0)) {
                    WaypointStore.appendTrack(ctx, TrackPoint(f.lat, f.lon, f.altitude, now))
                }
            }
        }
    }

    // المسافة والاتجاه إلى الهدف
    val distT = if (fix != null && target != null) Geo.distanceM(fix.lat, fix.lon, target.lat, target.lon) else null
    val brgT = if (fix != null && target != null) Geo.bearing(fix.lat, fix.lon, target.lat, target.lon) else null
    val arrived = distT != null && distT <= 20.0
    LaunchedEffect(arrived, target) {
        if (arrived) navVibrate(ctx)
    }

    ScreenScaffold(
        title = "الملاحة والعودة", onBack = env.back, subtitle = "GPS دون إنترنت",
        actions = {
            ShareAction {
                if (fix != null) navCoordsText("موقعي الحالي", fix.lat, fix.lon, fix.altitude) else "لا يتوفر موقع حالياً"
            }
        }
    ) {
        ScreenList {
            // ───── الموقع الحالي ─────
            item {
                if (fix == null) {
                    GlassCard(title = "الموقع الحالي") {
                        Text(
                            "جارٍ انتظار إشارة الأقمار الصناعية… اخرج إلى مكان مكشوف",
                            color = o.ink, style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        BigButton("تفعيل تحديد الموقع (GPS)", Icons.Filled.MyLocation, nav = true) { env.requestGps() }
                    }
                } else {
                    val q = when {
                        fix.accuracy < 10f -> "ممتازة" to o.success
                        fix.accuracy < 30f -> "جيدة" to o.warning
                        else -> "ضعيفة" to o.danger
                    }
                    GlassCard(
                        title = "الموقع الحالي",
                        trailing = { Pill("الدقة: ${q.first}", q.second) }
                    ) {
                        InfoRow("عشري", String.format(Locale.US, "%.6f, %.6f", fix.lat, fix.lon), emphasize = true)
                        InfoRow("DMS", "${Geo.dms(fix.lat, true)}  ${Geo.dms(fix.lon, false)}")
                        InfoRow("UTM", Geo.utm(fix.lat, fix.lon))
                        Spacer(Modifier.height(6.dp))
                        MetricRow(
                            "الارتفاع" to (fix.altitude?.let { "${it.toInt()} م" } ?: "--"),
                            "الدقة" to "±${fix.accuracy.toInt()} م",
                            "السرعة" to (fix.speed?.let { String.format(Locale.US, "%.1f كم/س", it * 3.6) } ?: "--")
                        )
                        Spacer(Modifier.height(10.dp))
                        BigButton("حفظ هذا الموقع", Icons.Filled.MyLocation, nav = true) { saveDialog = true }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassTonalButton(
                                onClick = { WaypointStore.add(ctx, "سيارتي", fix.lat, fix.lon, fix.altitude, "car") },
                                modifier = Modifier.weight(1f), nav = true
                            ) { Text("🚗 سيارتي هنا") }
                            GlassTonalButton(
                                onClick = { WaypointStore.add(ctx, "مخيمي", fix.lat, fix.lon, fix.altitude, "camp") },
                                modifier = Modifier.weight(1f), nav = true
                            ) { Text("⛺ مخيمي هنا") }
                        }
                        if (fix.accuracy >= 30f) {
                            Spacer(Modifier.height(6.dp))
                            Text("الدقة ضعيفة: ابتعد عن المباني والجبال وانتظر لحظات.", color = o.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ───── بطاقة التوجيه إلى الهدف ─────
            if (target != null) item {
                val tgt = target
                GlassCard(
                    title = "التوجيه إلى: ${tgt.name}",
                    trailing = { TextButton(onClick = { WaypointStore.target = null }) { Text("إلغاء الهدف") } }
                ) {
                    if (fix == null || distT == null || brgT == null) {
                        Text("بانتظار الموقع لحساب الاتجاه…", color = o.muted)
                    } else {
                        val relAngle = brgT - (heading ?: 0.0)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            NavArrowDial(
                                relAngle = relAngle, heading = heading ?: 0.0, arrived = arrived,
                                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
                            )
                        }
                        if (arrived) {
                            Text(
                                "وصلت إلى ${tgt.name}", color = o.success, style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        MetricRow(
                            "المسافة" to Geo.formatDistance(distT),
                            "الاتجاه" to Fmt.deg(brgT, 0),
                            "الجهة" to Fmt.direction(brgT)
                        )
                        if (heading == null) {
                            Text(
                                "حساس البوصلة غير متوفر: القرص ثابت والشمال للأعلى. سر باتجاه ${Fmt.deg(brgT, 0)} (${Fmt.direction(brgT)}).",
                                color = o.warning, style = MaterialTheme.typography.bodySmall
                            )
                        } else if (orient.accuracy < 2) {
                            Text("دقة البوصلة منخفضة: حرّك الهاتف على شكل 8 لمعايرتها.", color = o.warning, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        InfoRow("مشياً (4 كم/س)", navEta(distT, 4.0 / 3.6))
                        InfoRow("بالسيارة (40 كم/س)", navEta(distT, 40.0 / 3.6))
                        val sp = fix.speed?.toDouble()
                        if (sp != null && sp > 0.5) {
                            InfoRow("بسرعتك الحالية", navEta(distT, sp), valueColor = primary)
                        }
                        val course = fix.bearing?.toDouble()
                        if (course != null && sp != null && sp > 0.5) {
                            val diff = navNorm180(brgT - course)
                            val side = if (abs(diff) < 3) "على الاتجاه الصحيح" else if (diff > 0) "انعطف يميناً ${abs(diff).toInt()}°" else "انعطف يساراً ${abs(diff).toInt()}°"
                            InfoRow("انحرافك عن الاتجاه", "${abs(diff).toInt()}° — $side", valueColor = if (abs(diff) < 15) o.success else o.warning)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "في الأرض المفتوحة اختر معلماً بعيداً في نفس الاتجاه وسر نحوه، ثم أعد التوجيه.",
                            color = o.muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ───── النقاط المحفوظة ─────
            item {
                GlassCard(title = "النقاط المحفوظة", subtitle = "${points.size} نقطة") {
                    if (points.isEmpty()) {
                        Text("لا توجد نقاط بعد. احفظ مكان سيارتك أو مخيمك قبل الابتعاد عنه.", color = o.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    val ordered = if (fix != null) points.sortedBy { Geo.distanceM(fix.lat, fix.lon, it.lat, it.lon) } else points.sortedByDescending { it.time }
                    ordered.forEach { w ->
                        NavPointRow(
                            w, fix,
                            onGo = { WaypointStore.target = NavTarget(w.name, w.lat, w.lon, w.kind) },
                            onShare = { Exporter.shareText(ctx, navCoordsText("${w.name} (${WaypointKinds.label(w.kind)})", w.lat, w.lon, w.alt)) },
                            onDelete = { deleteId = w.id }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassOutlinedButton(onClick = { manualDialog = true }, modifier = Modifier.fillMaxWidth(), nav = true) {
                        Text("إدخال هدف يدوياً (إحداثيات أو اتجاه ومسافة)")
                    }
                }
            }

            // ───── أثر العودة ─────
            item {
                val dist = navTrackDistance(track)
                val dur = if (track.size >= 2) track.last().time - track.first().time else 0L
                GlassCard(
                    title = "أثر العودة", subtitle = "يسجّل مسارك لتعود منه",
                    trailing = { if (recording) Pill("● يسجّل", o.danger) }
                ) {
                    if (!recording) {
                        BigButton("بدء تسجيل المسار", Icons.Filled.Navigation, nav = true) { WaypointStore.setRecording(ctx, true) }
                    } else {
                        BigButton("إيقاف التسجيل", Icons.Filled.Navigation, outlined = true, nav = true) { WaypointStore.setRecording(ctx, false) }
                        Text("تبقى الشاشة مضاءة أثناء التسجيل. يُحفظ موضع كل 20 م تقريباً.", color = o.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    MetricRow("المسافة" to Geo.formatDistance(dist), "المدة" to Fmt.span(dur), "النقاط" to "${track.size}")
                    if (track.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            NavTrackPlot(track, points, fix, target, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f))
                        }
                        Text("الشمال للأعلى · نقطة خضراء = البداية · نقطة ملونة = موقعك", color = o.muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                onClick = {
                                    WaypointStore.startPoint(ctx)?.let { WaypointStore.target = NavTarget("نقطة البداية", it.lat, it.lon, "point") }
                                },
                                modifier = Modifier.weight(1f), nav = true
                            ) { Text("العودة إلى البداية") }
                            GlassOutlinedButton(onClick = { clearTrackAsk = true }, modifier = Modifier.weight(1f), nav = true) { Text("مسح المسار") }
                        }
                    }
                }
            }

            // ───── ملاحظات ─────
            item {
                GlassCard(title = "نصائح للصحراء") {
                    Text(
                        "• نظام GPS يعمل دون إنترنت ودون تغطية شبكة، فهو يستقبل الأقمار الصناعية مباشرة.\n" +
                            "• أبقِ الهاتف في مكان مكشوف للسماء ولا تضعه داخل حقيبة مغلقة أو تحت سقف.\n" +
                            "• سجّل موقع سيارتك ومخيمك قبل أن تبتعد عنهما، وشغّل أثر العودة عند المشي.\n" +
                            "• GPS يستهلك البطارية: فعّل وضع الطيران (يبقى GPS يعمل) واخفض سطوع الشاشة لتوفيرها.\n" +
                            "• خذ معك شاحناً متنقلاً، وأخبر أحداً بمسارك ووقت عودتك.",
                        color = o.ink, style = MaterialTheme.typography.bodyMedium
                    )
                }
                Note("الاتجاه على السهم يعتمد على بوصلة الهاتف؛ ابتعد عن المعادن والمغناطيس لدقة أفضل.")
            }
        }
    }

    // ───── نوافذ ─────
    if (saveDialog && fix != null) {
        var name by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("point") }
        GlassAlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("حفظ هذا الموقع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(String.format(Locale.US, "%.6f, %.6f", fix.lat, fix.lon), color = o.muted)
                    Field("الاسم", name, { name = it })
                    ChipsSelector(WaypointKinds.labels.mapValues { WaypointKinds.icon(it.key) + " " + it.value }, kind) { kind = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    WaypointStore.add(ctx, name, fix.lat, fix.lon, fix.altitude, kind)
                    saveDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("إلغاء") } }
        )
    }

    deleteId?.let { id ->
        ConfirmDialog("حذف النقطة", "هل تريد حذف هذه النقطة؟", "حذف", onDismiss = { deleteId = null }) {
            val w = points.firstOrNull { it.id == id }
            val t = WaypointStore.target
            if (w != null && t != null && t.lat == w.lat && t.lon == w.lon) WaypointStore.target = null
            WaypointStore.delete(ctx, id)
        }
    }

    if (clearTrackAsk) {
        ConfirmDialog("مسح المسار", "سيُحذف أثر العودة المسجّل كله. متابعة؟", "مسح", onDismiss = { clearTrackAsk = false }) {
            WaypointStore.clearTrack(ctx)
        }
    }

    if (manualDialog) {
        var mode by remember { mutableStateOf("coords") }
        var name by remember { mutableStateOf("") }
        var latS by remember { mutableStateOf("") }
        var lonS by remember { mutableStateOf("") }
        var brgS by remember { mutableStateOf("") }
        var distS by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        GlassAlertDialog(
            onDismissRequest = { manualDialog = false },
            title = { Text("هدف يدوي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipsSelector(linkedMapOf("coords" to "إحداثيات", "bd" to "اتجاه ومسافة"), mode) { mode = it; err = null }
                    Field("الاسم (مثلاً: بئر، قرية)", name, { name = it })
                    if (mode == "coords") {
                        Field("خط العرض (عشري)", latS, { latS = it }, number = true)
                        Field("خط الطول (عشري)", lonS, { lonS = it }, number = true)
                    } else {
                        Text(
                            if (fix == null) "يتطلب موقعاً حالياً من GPS." else "من موقعك الحالي: الاتجاه بالدرجات من الشمال (0–360) والمسافة بالكيلومتر.",
                            color = o.muted, style = MaterialTheme.typography.bodySmall
                        )
                        Field("الاتجاه (درجات)", brgS, { brgS = it }, number = true)
                        Field("المسافة (كم)", distS, { distS = it }, number = true)
                    }
                    err?.let { Text(it, color = o.danger, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var lat: Double? = null
                    var lon: Double? = null
                    if (mode == "coords") {
                        lat = navNum(latS); lon = navNum(lonS)
                        if (lat == null || lon == null || abs(lat) > 90 || abs(lon) > 180) { err = "إحداثيات غير صالحة"; return@TextButton }
                    } else {
                        val b = navNum(brgS); val d = navNum(distS)
                        if (fix == null) { err = "لا يتوفر موقعك الحالي بعد"; return@TextButton }
                        if (b == null || d == null || d <= 0) { err = "أدخل اتجاهاً ومسافة صحيحين"; return@TextButton }
                        val p = Geo.destination(fix.lat, fix.lon, ((b % 360) + 360) % 360, d * 1000.0)
                        lat = p.first; lon = p.second
                    }
                    val w = WaypointStore.add(ctx, name.ifBlank { "هدف يدوي" }, lat, lon, null, "point")
                    WaypointStore.target = NavTarget(w.name, w.lat, w.lon, w.kind)
                    manualDialog = false
                }) { Text("اعتماد") }
            },
            dismissButton = { TextButton(onClick = { manualDialog = false }) { Text("إلغاء") } }
        )
    }
}

/* ───────────────────────── صف نقطة محفوظة ───────────────────────── */

@Composable
private fun NavPointRow(w: Waypoint, fix: GpsFix?, onGo: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val o = AppTheme.ornament
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(WaypointKinds.icon(w.kind), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(w.name, color = o.ink, style = MaterialTheme.typography.titleSmall)
            val sub = if (fix != null) {
                val d = Geo.distanceM(fix.lat, fix.lon, w.lat, w.lon)
                val b = Geo.bearing(fix.lat, fix.lon, w.lat, w.lon)
                "${Geo.formatDistance(d)} · ${Fmt.deg(b, 0)} ${Fmt.direction(b)}"
            } else String.format(Locale.US, "%.5f, %.5f", w.lat, w.lon)
            Text(sub, color = o.muted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onGo) { Icon(Icons.Filled.Navigation, "انتقل إليه", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "مشاركة", tint = o.muted) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "حذف", tint = o.danger) }
    }
}

/* ───────────────────────── قرص السهم ───────────────────────── */

@Composable
private fun NavArrowDial(relAngle: Double, heading: Double, arrived: Boolean, modifier: Modifier = Modifier) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    val arrowColor = if (arrived) o.success else primary
    val ink = o.ink.toArgb()
    val muted = o.muted
    val gold = o.gold
    val paint = remember {
        Paint().apply { typeface = CanvasFonts.text; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 6.dp.toPx()
        drawCircle(muted.copy(alpha = 0.35f), r, c, style = Stroke(2.dp.toPx()))
        drawCircle(muted.copy(alpha = 0.15f), r * 0.55f, c, style = Stroke(1.dp.toPx()))
        // الدرجات تدور مع اتجاه الهاتف: الشمال الحقيقي
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - heading)
            val len = if (i % 3 == 0) 10.dp.toPx() else 5.dp.toPx()
            val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
            drawLine(
                muted.copy(alpha = 0.7f),
                Offset(c.x + dx * r, c.y + dy * r), Offset(c.x + dx * (r - len), c.y + dy * (r - len)),
                strokeWidth = 2.dp.toPx()
            )
        }
        paint.textSize = 15.dp.toPx()
        val labels = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        for ((deg, t) in labels) {
            val a = Math.toRadians(deg - heading)
            val rr = r - 24.dp.toPx()
            paint.color = if (deg == 0) gold.toArgb() else ink
            drawContext.canvas.nativeCanvas.drawText(
                t, c.x + sin(a).toFloat() * rr, c.y - cos(a).toFloat() * rr + paint.textSize * 0.35f, paint
            )
        }
        // السهم نحو الهدف
        rotate(relAngle.toFloat(), c) {
            val path = Path().apply {
                moveTo(c.x, c.y - r * 0.68f)
                lineTo(c.x + r * 0.2f, c.y + r * 0.22f)
                lineTo(c.x, c.y + r * 0.08f)
                lineTo(c.x - r * 0.2f, c.y + r * 0.22f)
                close()
            }
            drawPath(path, arrowColor)
            drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(1.5.dp.toPx()))
        }
        drawCircle(o.ink, 4.dp.toPx(), c)
    }
}

/* ───────────────────────── رسم الأثر ───────────────────────── */

@Composable
private fun NavTrackPlot(track: List<TrackPoint>, points: List<Waypoint>, fix: GpsFix?, target: NavTarget?, modifier: Modifier = Modifier) {
    val o = AppTheme.ornament
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        // مجموعة النقاط التي تحدد الإطار: الأثر وموقعي والهدف
        val pts = ArrayList<Pair<Double, Double>>()
        track.forEach { pts.add(it.lat to it.lon) }
        if (fix != null) pts.add(fix.lat to fix.lon)
        if (target != null) pts.add(target.lat to target.lon)
        if (pts.isEmpty()) return@Canvas
        val lat0 = pts.map { it.first }.average()
        val lon0 = pts.map { it.second }.average()
        val kx = 111_320.0 * cos(Math.toRadians(lat0))
        val ky = 110_574.0
        fun mx(lon: Double) = (lon - lon0) * kx
        fun my(lat: Double) = (lat - lat0) * ky
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        pts.forEach {
            val x = mx(it.second); val y = my(it.first)
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        val span = max(max(maxX - minX, maxY - minY), 60.0)
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        val pad = 22.dp.toPx()
        val scale = (size.minDimension - 2 * pad) / span
        fun px(lon: Double) = (size.width / 2 + (mx(lon) - cx) * scale).toFloat()
        fun py(lat: Double) = (size.height / 2 - (my(lat) - cy) * scale).toFloat()

        drawRect(o.muted.copy(alpha = 0.08f))
        drawRect(o.muted.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
        // سهم الشمال
        drawLine(o.gold, Offset(size.width - 16.dp.toPx(), 32.dp.toPx()), Offset(size.width - 16.dp.toPx(), 14.dp.toPx()), strokeWidth = 2.dp.toPx())
        drawCircle(o.gold, 3.dp.toPx(), Offset(size.width - 16.dp.toPx(), 12.dp.toPx()))

        if (track.size >= 2) {
            val path = Path()
            track.forEachIndexed { i, t ->
                val x = px(t.lon); val y = py(t.lat)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, primary.copy(alpha = 0.85f), style = Stroke(2.5.dp.toPx()))
        }
        // النقاط المحفوظة ضمن الإطار
        points.forEach { w ->
            val x = px(w.lon); val y = py(w.lat)
            if (x in 0f..size.width && y in 0f..size.height) {
                drawCircle(o.gold, 4.dp.toPx(), Offset(x, y))
                drawCircle(Color.Black.copy(alpha = 0.4f), 4.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
            }
        }
        if (target != null) {
            val p = Offset(px(target.lon), py(target.lat))
            drawCircle(o.danger, 8.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
            drawCircle(o.danger, 2.5.dp.toPx(), p)
        }
        track.firstOrNull()?.let { s ->
            val p = Offset(px(s.lon), py(s.lat))
            drawCircle(o.success, 7.dp.toPx(), p)
            drawCircle(Color.Black.copy(alpha = 0.35f), 7.dp.toPx(), p, style = Stroke(1.dp.toPx()))
        }
        if (fix != null) {
            val p = Offset(px(fix.lon), py(fix.lat))
            drawCircle(primary.copy(alpha = 0.25f), 12.dp.toPx(), p)
            drawCircle(primary, 6.dp.toPx(), p)
            drawCircle(Color.White, 6.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
        }
    }
}
