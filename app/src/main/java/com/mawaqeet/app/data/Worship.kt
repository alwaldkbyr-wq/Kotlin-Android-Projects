package com.mawaqeet.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** حالة أداء الصلاة */
object PrayerState {
    const val NONE = 0
    const val ON_TIME = 1
    const val JAMAA = 2
    const val LATE = 3
    const val MISSED = 4
    val labels = listOf("غير مسجّل", "في وقتها", "في جماعة", "متأخرة", "فائتة")
    fun prayed(s: Int) = s == ON_TIME || s == JAMAA || s == LATE
}

/**
 * حفظ سجل الصلوات وعدّاد القضاء والمسبحة في SharedPreferences "worship".
 * الصلوات الخمس بالترتيب: الفجر، الظهر، العصر، المغرب، العشاء.
 * اقرأ [version] داخل الدالة المركّبة ليُعاد الرسم عند أي تغيير.
 */
class WorshipStore private constructor(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("worship", Context.MODE_PRIVATE)

    var version by mutableIntStateOf(0)
        private set

    private fun bump() { version++ }

    // ───── السجل اليومي ─────
    private fun logKey(d: LocalDate) = "log_$d"
    private fun addedKey(d: LocalDate) = "qadaAdded_$d"

    fun states(d: LocalDate): IntArray {
        val s = sp.getString(logKey(d), null)
        return IntArray(5) { i -> s?.getOrNull(i)?.digitToIntOrNull()?.coerceIn(0, 4) ?: 0 }
    }

    fun state(d: LocalDate, i: Int): Int = states(d)[i]

    fun setState(d: LocalDate, i: Int, state: Int) {
        val a = states(d)
        a[i] = state.coerceIn(0, 4)
        val str = a.joinToString("")
        if (str == "00000") sp.edit().remove(logKey(d)).apply() else sp.edit().putString(logKey(d), str).apply()
        bump()
    }

    fun allPrayed(d: LocalDate): Boolean = states(d).all { PrayerState.prayed(it) }

    /** هل أُضيفت الفائتة (d, i) إلى القضاء سابقاً */
    fun qadaAdded(d: LocalDate, i: Int): Boolean = sp.getString(addedKey(d), "")?.contains(i.toString()) == true

    /** تضيف صلاة فائتة من السجل إلى عدّاد القضاء مرة واحدة */
    fun addMissedToQada(d: LocalDate, i: Int) {
        if (qadaAdded(d, i)) return
        val cur = sp.getString(addedKey(d), "") ?: ""
        sp.edit().putString(addedKey(d), cur + i).putInt("qada_$i", qada(i) + 1).apply()
        bump()
    }

    /** عدد الأيام المتتالية التي أُدّيت فيها الصلوات الخمس (يُحتسب اليوم إن اكتمل، وإلا يبدأ من أمس) */
    fun streak(today: LocalDate): Int {
        var d = if (allPrayed(today)) today else today.minusDays(1)
        var n = 0
        while (n < 3650 && allPrayed(d)) { n++; d = d.minusDays(1) }
        return n
    }

    // ───── القضاء ─────
    fun qada(i: Int): Int = sp.getInt("qada_$i", 0)
    fun qadaTotal(): Int = (0 until 5).sumOf { qada(it) }

    fun setQada(i: Int, v: Int) { sp.edit().putInt("qada_$i", v.coerceAtLeast(0)).apply(); bump() }
    fun addQada(i: Int, delta: Int) = setQada(i, qada(i) + delta)
    fun addQadaAll(each: Int) {
        val e = sp.edit()
        for (i in 0 until 5) e.putInt("qada_$i", (qada(i) + each).coerceAtLeast(0))
        e.apply(); bump()
    }

    // ───── المسبحة ─────
    fun tasbihLifetime(): Long = sp.getLong("tasbih_total", 0L)
    fun tasbihToday(today: LocalDate): Int = sp.getInt("tasbih_day_$today", 0)

    fun tasbihAdd(today: LocalDate, delta: Int) {
        val day = (tasbihToday(today) + delta).coerceAtLeast(0)
        val total = (tasbihLifetime() + delta).coerceAtLeast(0L)
        sp.edit().putInt("tasbih_day_$today", day).putLong("tasbih_total", total).apply()
        bump()
    }

    companion object {
        @Volatile private var inst: WorshipStore? = null
        fun get(context: Context): WorshipStore =
            inst ?: synchronized(this) { inst ?: WorshipStore(context.applicationContext).also { inst = it } }
    }
}
