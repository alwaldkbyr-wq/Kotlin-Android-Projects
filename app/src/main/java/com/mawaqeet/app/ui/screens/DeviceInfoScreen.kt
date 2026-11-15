package com.mawaqeet.app.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Display
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.Note
import com.mawaqeet.app.ui.components.ScreenList
import com.mawaqeet.app.ui.components.ScreenScaffold
import com.mawaqeet.app.ui.components.SectionTitle
import com.mawaqeet.app.ui.components.ShareAction
import com.mawaqeet.app.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

private data class DevSection(val title: String, val rows: List<Pair<String, String>>)
private data class DevSensor(val name: String, val vendor: String, val type: String, val power: String, val resolution: String)
private class DevReport(val sections: List<DevSection>, val sensors: List<DevSensor>, val used: List<Pair<String, Boolean>>)

private fun devGb(bytes: Long) = String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)

private fun devBuild(ctx: Context): DevReport {
    val sections = ArrayList<DevSection>()
    sections += DevSection(
        "الجهاز", listOf(
            "الشركة المصنّعة" to Build.MANUFACTURER, "الطراز" to Build.MODEL, "العلامة" to Build.BRAND, "الاسم الكودي" to Build.DEVICE,
            "إصدار أندرويد" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    )

    val dm = ctx.resources.displayMetrics
    val refresh = runCatching {
        (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY).refreshRate
    }.getOrNull()
    sections += DevSection(
        "الشاشة", listOf(
            "الدقة (بكسل)" to "${dm.widthPixels} × ${dm.heightPixels}",
            "الأبعاد (dp)" to String.format(Locale.US, "%.0f × %.0f", dm.widthPixels / dm.density, dm.heightPixels / dm.density),
            "الكثافة" to "${dm.densityDpi} dpi (×${String.format(Locale.US, "%.2f", dm.density)})",
            "معدّل التحديث" to (refresh?.let { String.format(Locale.US, "%.0f Hz", it) } ?: "--")
        )
    )

    val mi = ActivityManager.MemoryInfo()
    runCatching { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi) }
    val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    sections += DevSection(
        "الذاكرة والتخزين", listOf(
            "الذاكرة الكلية (RAM)" to devGb(mi.totalMem),
            "الذاكرة المتاحة" to devGb(mi.availMem),
            "التخزين الكلي" to (st?.let { devGb(it.blockCountLong * it.blockSizeLong) } ?: "--"),
            "التخزين المتاح" to (st?.let { devGb(it.availableBlocksLong * it.blockSizeLong) } ?: "--")
        )
    )

    val bi = runCatching { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = runCatching { (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)
    val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    sections += DevSection("البطارية", listOf("المستوى" to (if (level >= 0) "$level%" else "--"), "الشحن" to (if (charging) "قيد الشحن" else "غير موصول")))

    sections += DevSection(
        "المعالج", listOf(
            "عدد الأنوية" to Runtime.getRuntime().availableProcessors().toString(),
            "البنية (ABI)" to Build.SUPPORTED_ABIS.joinToString("، ")
        )
    )

    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val list = runCatching { sm.getSensorList(Sensor.TYPE_ALL) }.getOrDefault(emptyList())
    val sensors = list.map {
        DevSensor(
            it.name, it.vendor, it.stringType ?: it.type.toString(),
            String.format(Locale.US, "%.2f mA", it.power), String.format(Locale.US, "%.4f", it.resolution)
        )
    }
    val used = listOf(
        "متجه الدوران (Rotation Vector)" to (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null),
        "المغناطيسي (Magnetometer)" to (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null),
        "مقياس التسارع (Accelerometer)" to (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null),
        "الجيروسكوب (Gyroscope)" to (sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)
    )

    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    fun prov(p: String) = if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) "مفعّل" else "متوقف"
    sections += DevSection(
        "مزوّدات الموقع", listOf(
            "GPS" to prov(LocationManager.GPS_PROVIDER), "الشبكة" to prov(LocationManager.NETWORK_PROVIDER), "السلبي (Passive)" to prov(LocationManager.PASSIVE_PROVIDER)
        )
    )

    val zone = ZoneId.systemDefault()
    sections += DevSection(
        "الوقت واللغة", listOf(
            "المنطقة الزمنية" to TimeZone.getDefault().id,
            "فرق التوقيت الحالي" to zone.rules.getOffset(Instant.now()).toString(),
            "اللغة" to Locale.getDefault().toString()
        )
    )
    return DevReport(sections, sensors, used)
}

private fun devText(r: DevReport): String = buildString {
    appendLine("معلومات الجهاز — الساهور")
    r.sections.forEach { s ->
        appendLine()
        appendLine("【${s.title}】")
        s.rows.forEach { appendLine("${it.first}: ${it.second}") }
    }
    appendLine()
    appendLine("【الحساسات التي يستعملها التطبيق】")
    r.used.forEach { appendLine("${it.first}: ${if (it.second) "متوفر" else "غير متوفر"}") }
    appendLine()
    appendLine("【كل الحساسات (${r.sensors.size})】")
    r.sensors.forEach { appendLine("• ${it.name} | ${it.vendor} | ${it.type} | ${it.power} | دقة ${it.resolution}") }
}

@Composable
fun DeviceInfoScreen() {
    val env = LocalEnv.current
    val ctx = LocalContext.current
    val o = AppTheme.ornament
    val report = remember { devBuild(ctx) }

    ScreenScaffold(
        title = "معلومات الجهاز", onBack = env.back, subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
        actions = { ShareAction { devText(report) } }
    ) {
        ScreenList {
            report.sections.forEach { sec ->
                item {
                    GlassCard(title = sec.title) { sec.rows.forEach { (k, v) -> InfoRow(k, v) } }
                }
            }
            item {
                GlassCard(title = "الحساسات التي يستعملها التطبيق", subtitle = "البوصلة والاتجاهات ثلاثية الأبعاد") {
                    report.used.forEach { (k, ok) ->
                        InfoRow(k, if (ok) "متوفر" else "غير متوفر", valueColor = if (ok) o.success else o.danger)
                    }
                    if (report.used.none { it.second }) Text("لا توجد حساسات اتجاه؛ ستعمل البوصلة بالاتجاه المحسوب فقط.", color = o.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            item { SectionTitle("كل الحساسات (${report.sensors.size})") }
            items(report.sensors) { s ->
                GlassCard {
                    Text(s.name, color = o.ink, style = MaterialTheme.typography.titleSmall)
                    Text(s.vendor, color = o.muted, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("النوع", color = o.muted, style = MaterialTheme.typography.labelSmall); Text(s.type.substringAfterLast('.'), color = o.ink, style = MaterialTheme.typography.bodySmall) }
                        Column(Modifier.weight(1f)) { Text("الطاقة", color = o.muted, style = MaterialTheme.typography.labelSmall); Text(s.power, color = o.ink, style = MaterialTheme.typography.bodySmall) }
                        Column(Modifier.weight(1f)) { Text("الدقة", color = o.muted, style = MaterialTheme.typography.labelSmall); Text(s.resolution, color = o.ink, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Note("تُقرأ هذه المعلومات من جهازك مباشرة ولا تُرسل إلى أي مكان؛ زر المشاركة يرسل التقرير بإرادتك فقط.") }
        }
    }
}
