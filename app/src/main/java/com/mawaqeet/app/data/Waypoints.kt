package com.mawaqeet.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** نقطة محفوظة (مخيم، سيارة، ماء…) */
data class Waypoint(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val time: Long,
    /** camp | car | water | danger | point */
    val kind: String
)

/** نقطة من أثر المسار */
data class TrackPoint(val lat: Double, val lon: Double, val alt: Double?, val time: Long)

/** الهدف الحالي للملاحة */
data class NavTarget(val name: String, val lat: Double, val lon: Double, val kind: String = "point")

object WaypointKinds {
    val labels: Map<String, String> = linkedMapOf(
        "camp" to "مخيم", "car" to "سيارة", "water" to "ماء", "danger" to "خطر", "point" to "نقطة"
    )
    fun label(kind: String) = labels[kind] ?: "نقطة"
    fun icon(kind: String) = when (kind) {
        "camp" -> "⛺"; "car" -> "🚗"; "water" -> "💧"; "danger" -> "⚠️"; else -> "📍"
    }
}

/** تخزين النقاط وأثر المسار في SharedPreferences، دون إنترنت */
object WaypointStore {
    private const val PREFS = "waypoints"
    private const val KEY_POINTS = "points"
    private const val KEY_TRACK = "track"
    private const val KEY_REC = "recording"
    private const val MAX_TRACK = 6000

    /** يزيد عند أي تغيير ليُعاد رسم الشاشات (اقرأه في جسم الدالة المركّبة) */
    var version by mutableIntStateOf(0)
        private set

    /** الهدف الحالي (يبقى عند التنقل بين الشاشات) */
    var target by mutableStateOf<NavTarget?>(null)

    var recording by mutableStateOf(false)
        private set

    private var inited = false
    private var trackCache: MutableList<TrackPoint>? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        recording = prefs(ctx).getBoolean(KEY_REC, false)
    }

    fun setRecording(ctx: Context, on: Boolean) {
        recording = on
        prefs(ctx).edit().putBoolean(KEY_REC, on).apply()
    }

    // ───── النقاط ─────
    fun load(ctx: Context): List<Waypoint> {
        val s = prefs(ctx).getString(KEY_POINTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    Waypoint(
                        id = o.getLong("id"), name = o.optString("name", ""),
                        lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                        alt = if (o.has("alt") && !o.isNull("alt")) o.getDouble("alt") else null,
                        time = o.optLong("time", 0L), kind = o.optString("kind", "point")
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun save(ctx: Context, list: List<Waypoint>) {
        val arr = JSONArray()
        list.forEach { w ->
            val o = JSONObject()
            o.put("id", w.id); o.put("name", w.name); o.put("lat", w.lat); o.put("lon", w.lon)
            if (w.alt != null) o.put("alt", w.alt)
            o.put("time", w.time); o.put("kind", w.kind)
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_POINTS, arr.toString()).apply()
        version++
    }

    fun add(ctx: Context, name: String, lat: Double, lon: Double, alt: Double?, kind: String): Waypoint {
        var id = System.currentTimeMillis()
        val cur = load(ctx)
        while (cur.any { it.id == id }) id++
        val w = Waypoint(id, name.trim().ifEmpty { WaypointKinds.label(kind) }, lat, lon, alt, System.currentTimeMillis(), kind)
        save(ctx, cur + w)
        return w
    }

    fun delete(ctx: Context, id: Long) {
        save(ctx, load(ctx).filter { it.id != id })
    }

    // ───── أثر المسار (صيغة مضغوطة: lat,lon,alt,time في كل سطر) ─────
    private fun ensureTrack(ctx: Context): MutableList<TrackPoint> {
        trackCache?.let { return it }
        val out = ArrayList<TrackPoint>()
        val s = prefs(ctx).getString(KEY_TRACK, "") ?: ""
        s.lineSequence().forEach { line ->
            try {
                val p = line.split(',')
                if (p.size >= 4) out.add(TrackPoint(p[0].toDouble(), p[1].toDouble(), p[2].toDoubleOrNull(), p[3].toLong()))
            } catch (e: Exception) { /* تجاهل السطر التالف */ }
        }
        trackCache = out
        return out
    }

    private fun enc(p: TrackPoint) = String.format(Locale.US, "%.6f,%.6f,%s,%d", p.lat, p.lon, p.alt?.let { String.format(Locale.US, "%.1f", it) } ?: "", p.time)

    fun loadTrack(ctx: Context): List<TrackPoint> = ensureTrack(ctx).toList()

    fun appendTrack(ctx: Context, p: TrackPoint) {
        val t = ensureTrack(ctx)
        t.add(p)
        if (t.size > MAX_TRACK) {
            // احتفظ بأول نقطة (البداية) ثم أحدث النقاط
            val keep = listOf(t.first()) + t.takeLast(MAX_TRACK - 1000)
            t.clear(); t.addAll(keep)
            prefs(ctx).edit().putString(KEY_TRACK, t.joinToString("\n") { enc(it) }).apply()
        } else {
            val old = prefs(ctx).getString(KEY_TRACK, "") ?: ""
            prefs(ctx).edit().putString(KEY_TRACK, if (old.isEmpty()) enc(p) else old + "\n" + enc(p)).apply()
        }
        version++
    }

    fun clearTrack(ctx: Context) {
        trackCache = ArrayList()
        prefs(ctx).edit().remove(KEY_TRACK).apply()
        version++
    }

    fun startPoint(ctx: Context): TrackPoint? = ensureTrack(ctx).firstOrNull()
    fun lastTrackPoint(ctx: Context): TrackPoint? = ensureTrack(ctx).lastOrNull()
}
