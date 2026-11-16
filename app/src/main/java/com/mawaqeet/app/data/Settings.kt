package com.mawaqeet.app.data

import android.content.Context
import android.content.SharedPreferences
import com.mawaqeet.app.astro.HighLatRule
import com.mawaqeet.app.astro.HijriMethod
import com.mawaqeet.app.astro.Prayer
import com.mawaqeet.app.astro.PrayerMethod
import com.mawaqeet.app.astro.PrayerSettings
import com.mawaqeet.app.astro.SolarNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** موقع محفوظ */
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val heightM: Double = 0.0,
    /** موقع من GPS (يتحدّث تلقائياً) */
    val gps: Boolean = false
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

    fun toJson(): JSONObject = JSONObject().put("n", name).put("la", lat).put("lo", lon).put("z", zoneId).put("h", heightM).put("g", gps)

    companion object {
        fun fromJson(o: JSONObject) = Place(o.getString("n"), o.getDouble("la"), o.getDouble("lo"), o.optString("z", ZoneId.systemDefault().id), o.optDouble("h", 0.0), o.optBoolean("g", false))
        val DEFAULT = Place("مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh")
    }
}

/** كل إعدادات التطبيق */
data class AppSettings(
    // المظهر
    val dark: Boolean = true,
    val palette: Int = 0,
    val motion: Boolean = true,
    val perfMode: Int = 0,
    // العرض
    val h24: Boolean = true,
    val maghrebMonths: Boolean = true,
    val showSeconds: Boolean = false,
    // التقاويم
    val hijriMethod: HijriMethod = HijriMethod.UMM_AL_QURA,
    val hijriAdjust: Int = 0,
    val solarNames: SolarNames = SolarNames.ARABIC_ZODIAC,
    /** التقويم الرئيسي في شبكة الشهر: 0 ميلادي، 1 يولياني، 2 هجري، 3 هجري شمسي */
    val mainCalendar: Int = 2,
    // الصلاة
    val method: PrayerMethod = PrayerMethod.ALGERIA,
    val hanafiAsr: Boolean = false,
    val highLat: HighLatRule = HighLatRule.ANGLE_BASED,
    val offsets: List<Int> = List(6) { 0 },
    val dhuhrSafety: Int = 0,
    val maghribSafety: Int = 0,
    // التنبيهات: لكل صلاة (بترتيب Prayer) تفعيل التنبيه، وتنبيه قبل الوقت بالدقائق
    val notify: List<Boolean> = listOf(true, false, true, true, true, true),
    val notifyBefore: Int = 0,
    val notifySound: Boolean = true,
    // تذكيرات إضافية (كلها متوقفة افتراضياً)
    val remindFriday: Boolean = false,
    /** دقائق التذكير قبل الظهر يوم الجمعة */
    val fridayBefore: Int = 45,
    val remindQiyam: Boolean = false,
    val remindDuha: Boolean = false,
    val remindImsak: Boolean = false,
    // المواقع
    val places: List<Place> = listOf(Place.DEFAULT),
    val placeIndex: Int = 0
) {
    val place: Place get() = places.getOrElse(placeIndex) { places.firstOrNull() ?: Place.DEFAULT }

    val prayerSettings: PrayerSettings
        get() = PrayerSettings(method, hanafiAsr, highLat, offsets.toIntArray(), dhuhrSafety, maghribSafety, place.heightM)

    fun notifyFor(p: Prayer) = notify.getOrElse(p.ordinal) { false }
}

/** حفظ الإعدادات في SharedPreferences ونشرها كـ StateFlow */
class SettingsStore private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("mawaqeet", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _flow
    val value: AppSettings get() = _flow.value

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_flow.value)
        _flow.value = n
        save(n)
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, def: E): E =
        runCatching { enumValueOf<E>(name ?: def.name) }.getOrDefault(def)

    private fun load(): AppSettings {
        val d = AppSettings()
        val places = runCatching {
            val arr = JSONArray(sp.getString("places", null) ?: return@runCatching d.places)
            (0 until arr.length()).map { Place.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(d.places).ifEmpty { d.places }
        fun ints(key: String, n: Int, def: List<Int>) = sp.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == n } ?: def
        fun bools(key: String, n: Int, def: List<Boolean>) = sp.getString(key, null)?.split(',')?.map { it == "1" }?.takeIf { it.size == n } ?: def
        return AppSettings(
            dark = sp.getBoolean("dark", d.dark),
            palette = sp.getInt("palette", d.palette),
            motion = sp.getBoolean("motion", d.motion),
            perfMode = sp.getInt("perf", d.perfMode),
            h24 = sp.getBoolean("h24", d.h24),
            maghrebMonths = sp.getBoolean("maghreb", d.maghrebMonths),
            showSeconds = sp.getBoolean("secs", d.showSeconds),
            hijriMethod = enumOf(sp.getString("hijriMethod", null), d.hijriMethod),
            hijriAdjust = sp.getInt("hijriAdjust", d.hijriAdjust),
            solarNames = enumOf(sp.getString("solarNames", null), d.solarNames),
            mainCalendar = sp.getInt("mainCal", d.mainCalendar),
            method = enumOf(sp.getString("method", null), d.method),
            hanafiAsr = sp.getBoolean("hanafi", d.hanafiAsr),
            highLat = enumOf(sp.getString("highLat", null), d.highLat),
            offsets = ints("offsets", 6, d.offsets),
            dhuhrSafety = sp.getInt("dhuhrSafety", d.dhuhrSafety),
            maghribSafety = sp.getInt("maghribSafety", d.maghribSafety),
            notify = bools("notify", 6, d.notify),
            notifyBefore = sp.getInt("notifyBefore", d.notifyBefore),
            notifySound = sp.getBoolean("notifySound", d.notifySound),
            remindFriday = sp.getBoolean("remindFriday", d.remindFriday),
            fridayBefore = sp.getInt("fridayBefore", d.fridayBefore),
            remindQiyam = sp.getBoolean("remindQiyam", d.remindQiyam),
            remindDuha = sp.getBoolean("remindDuha", d.remindDuha),
            remindImsak = sp.getBoolean("remindImsak", d.remindImsak),
            places = places,
            placeIndex = sp.getInt("placeIndex", 0).coerceIn(0, places.size - 1)
        )
    }

    private fun save(s: AppSettings) {
        sp.edit()
            .putBoolean("dark", s.dark).putInt("palette", s.palette).putBoolean("motion", s.motion).putInt("perf", s.perfMode)
            .putBoolean("h24", s.h24).putBoolean("maghreb", s.maghrebMonths).putBoolean("secs", s.showSeconds)
            .putString("hijriMethod", s.hijriMethod.name).putInt("hijriAdjust", s.hijriAdjust)
            .putString("solarNames", s.solarNames.name).putInt("mainCal", s.mainCalendar)
            .putString("method", s.method.name).putBoolean("hanafi", s.hanafiAsr).putString("highLat", s.highLat.name)
            .putString("offsets", s.offsets.joinToString(",")).putInt("dhuhrSafety", s.dhuhrSafety).putInt("maghribSafety", s.maghribSafety)
            .putString("notify", s.notify.joinToString(",") { if (it) "1" else "0" }).putInt("notifyBefore", s.notifyBefore)
            .putBoolean("notifySound", s.notifySound)
            .putBoolean("remindFriday", s.remindFriday).putInt("fridayBefore", s.fridayBefore)
            .putBoolean("remindQiyam", s.remindQiyam).putBoolean("remindDuha", s.remindDuha).putBoolean("remindImsak", s.remindImsak)
            .putString("places", JSONArray().also { a -> s.places.forEach { a.put(it.toJson()) } }.toString())
            .putInt("placeIndex", s.placeIndex)
            .apply()
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context.applicationContext).also { instance = it } }
    }
}
