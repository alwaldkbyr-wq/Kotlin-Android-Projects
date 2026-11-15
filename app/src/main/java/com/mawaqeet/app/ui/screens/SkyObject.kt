package com.mawaqeet.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mawaqeet.app.astro.AU_KM
import com.mawaqeet.app.astro.Body
import com.mawaqeet.app.astro.Ephemeris
import com.mawaqeet.app.astro.Phases
import com.mawaqeet.app.astro.Planets
import com.mawaqeet.app.astro.RiseSetCalc
import com.mawaqeet.app.astro.Star
import com.mawaqeet.app.astro.StarCatalog
import com.mawaqeet.app.astro.eqToHor
import com.mawaqeet.app.astro.jdOf
import com.mawaqeet.app.astro.jdeOf
import com.mawaqeet.app.astro.lst
import com.mawaqeet.app.astro.refraction
import com.mawaqeet.app.data.Fmt
import com.mawaqeet.app.data.LocalEnv
import com.mawaqeet.app.ui.components.GlassCard
import com.mawaqeet.app.ui.components.InfoRow
import com.mawaqeet.app.ui.components.MetricRow
import com.mawaqeet.app.ui.theme.AppTheme
import java.util.Locale

/** أبعاد ألمع النجوم بالسنوات الضوئية (قيم مرجعية تقريبية) */
private val starDistanceLy = mapOf(
    "Sirius" to 8.6, "Canopus" to 310.0, "Rigil Kentaurus" to 4.37, "Arcturus" to 36.7, "Vega" to 25.0,
    "Capella" to 42.9, "Rigel" to 860.0, "Procyon" to 11.5, "Achernar" to 139.0, "Betelgeuse" to 550.0,
    "Hadar" to 390.0, "Altair" to 16.7, "Acrux" to 320.0, "Aldebaran" to 65.0, "Antares" to 550.0,
    "Spica" to 250.0, "Pollux" to 34.0, "Fomalhaut" to 25.0, "Deneb" to 2600.0, "Mimosa" to 280.0,
    "Regulus" to 79.0, "Adhara" to 430.0, "Castor" to 51.0, "Shaula" to 570.0, "Gacrux" to 88.0,
    "Bellatrix" to 250.0, "Elnath" to 134.0, "Miaplacidus" to 113.0, "Alnilam" to 2000.0, "Alnair" to 101.0,
    "Alnitak" to 1260.0, "Alioth" to 81.0, "Dubhe" to 123.0, "Mirfak" to 510.0, "Wezen" to 1600.0,
    "Sargas" to 300.0, "Kaus Australis" to 143.0, "Avior" to 610.0, "Alkaid" to 104.0, "Menkalinan" to 81.0,
    "Atria" to 415.0, "Alhena" to 109.0, "Peacock" to 180.0, "Mirzam" to 500.0, "Alphard" to 177.0,
    "Polaris" to 433.0, "Hamal" to 66.0, "Algieba" to 130.0, "Diphda" to 96.0, "Nunki" to 228.0,
    "Menkent" to 59.0, "Mirach" to 197.0, "Alpheratz" to 97.0, "Rasalhague" to 49.0, "Kochab" to 131.0,
    "Saiph" to 650.0, "Denebola" to 36.0, "Algol" to 90.0, "Aspidiske" to 690.0, "Suhail" to 545.0,
    "Alphecca" to 75.0, "Mizar" to 83.0, "Sadr" to 1800.0, "Schedar" to 228.0, "Eltanin" to 154.0,
    "Mintaka" to 1200.0, "Caph" to 55.0, "Merak" to 79.0, "Izar" to 202.0, "Enif" to 690.0,
    "Scheat" to 196.0, "Markab" to 133.0, "Menkar" to 220.0, "Phecda" to 84.0, "Unukalhai" to 74.0,
    "Zubenelgenubi" to 77.0, "Zubeneschamali" to 185.0, "Vindemiatrix" to 110.0, "Sabik" to 88.0,
    "Alderamin" to 49.0, "Mira" to 300.0, "Thuban" to 303.0, "Alcyone" to 440.0, "Algenib" to 390.0,
    "Ankaa" to 85.0, "Acamar" to 161.0, "Gienah" to 154.0, "Kraz" to 140.0, "Algorab" to 87.0,
    "Rasalgethi" to 360.0, "Kornephoros" to 139.0, "Sheratan" to 59.0, "Tarazed" to 395.0, "Albireo" to 430.0
)

/** الكوكبة التي يمر بها خط نجم (أقرب نقطة من خطوطها) */
private fun constellationOf(s: Star): String? = StarCatalog.constellations.firstOrNull { c ->
    c.lines.any { l -> (0 until l.size / 2).any { k -> kotlin.math.abs(l[2 * k] - s.ra) < 0.15 && kotlin.math.abs(l[2 * k + 1] - s.dec) < 0.15 } }
}?.ar

/**
 * بطاقة معلومات أي جرم مختار في خريطة السماء أو الكرة السماوية:
 * الارتفاع والسمت، والطلوع والعبور والغروب، واللمعان، والبعد عن الأرض، وزمن وصول ضوئه.
 */
@Composable
fun SkyObjectCard(body: Body?, star: Star?, millis: Long, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val o = AppTheme.ornament
    val dayStart = env.dayStart(env.dateOf(millis))
    val info = remember(body, star, millis / 60_000L, env.lat, env.lon) {
        val jd = jdOf(millis); val jde = jdeOf(jd)
        if (body != null) {
            val h = Ephemeris.horizontal(body, millis, env.lat, env.lon, true, env.place.heightM)
            val eq = Ephemeris.equatorial(body, jde)
            val rs = RiseSetCalc.compute(body, dayStart, env.lat, env.lon, heightM = env.place.heightM)
            val mag = when (body) { Body.SUN -> -26.7; Body.MOON -> -12.7 + 2.5 * (1 - Phases.state(millis).illumination); else -> Planets.magnitude(body, jde) }
            SkyObjInfo(h.alt, h.az, rs.rise, rs.transit, rs.set, mag, eq.distKm, null, eq.ra, eq.dec,
                if (rs.alwaysUp) "لا يغيب اليوم" else if (rs.alwaysDown) "لا يطلع اليوم" else null)
        } else {
            val s = star!!
            val p = SkyMath.precess(s.ra, s.dec, jde)
            val h0 = eqToHor(p.ra, p.dec, env.lat, lst(jd, env.lon))
            val st = SkyMath.starTimes(p.ra, p.dec, dayStart, env.lat, env.lon)
            SkyObjInfo(h0.alt + refraction(h0.alt), h0.az, st.rise, st.transit, st.set, s.mag, null, starDistanceLy[s.latin], p.ra, p.dec,
                if (st.circumpolar) "نجم دائم الظهور في موقعك" else if (st.neverRises) "لا يظهر في موقعك" else null)
        }
    }
    val title = body?.ar ?: star!!.ar
    val sub = when {
        body == Body.SUN -> "نجمنا"
        body == Body.MOON -> "تابع الأرض • ${Phases.phaseName(Phases.state(millis).phaseAngle)}"
        body != null -> "كوكب"
        else -> listOfNotNull(star!!.latin, constellationOf(star)).joinToString(" • ")
    }
    GlassCard(
        modifier = modifier, title = title, subtitle = sub,
        trailing = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق", tint = o.muted) } }
    ) {
        MetricRow("الارتفاع" to Fmt.deg(info.alt), "السمت" to Fmt.deg(info.az, 0), "اللمعان" to Fmt.num(info.mag, 1))
        MetricRow("الطلوع" to env.time(info.rise), "العبور" to env.time(info.transit), "الغروب" to env.time(info.set))
        when {
            info.distKm != null && body == Body.MOON ->
                InfoRow("البعد عن الأرض", String.format(Locale.US, "%,d كم", info.distKm.toLong()))
            info.distKm != null -> {
                InfoRow("البعد عن الأرض", "${Fmt.num(info.distKm / AU_KM, 3)} و.ف (${String.format(Locale.US, "%,.0f", info.distKm / 1e6)} مليون كم)")
                InfoRow("يصلنا ضوؤه بعد", Fmt.span((info.distKm / 299_792.458 * 1000).toLong()))
            }
            info.ly != null -> {
                InfoRow("البعد عن الأرض", "${Fmt.num(info.ly, if (info.ly < 20) 1 else 0)} سنة ضوئية")
                InfoRow("الضوء الذي تراه انطلق منه قبل", "${Fmt.num(info.ly, if (info.ly < 20) 1 else 0)} سنة")
            }
            else -> InfoRow("البعد عن الأرض", "غير متوفر")
        }
        InfoRow("المطلع المستقيم / الميل", "${Fmt.num(info.ra / 15.0, 2)} س / ${Fmt.deg(info.dec)}")
        InfoRow(
            "الحالة", (if (info.alt > 0) "فوق الأفق" else "تحت الأفق") + " • " + Fmt.direction(info.az) + (info.note?.let { " • $it" } ?: ""),
            valueColor = if (info.alt > 0) o.success else o.muted
        )
    }
}

private class SkyObjInfo(
    val alt: Double, val az: Double, val rise: Long?, val transit: Long?, val set: Long?,
    val mag: Double, val distKm: Double?, val ly: Double?, val ra: Double, val dec: Double, val note: String?
)
