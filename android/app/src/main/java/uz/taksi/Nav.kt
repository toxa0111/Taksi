package uz.taksi

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

// ───────────────────────── Umumiy ─────────────────────────

private const val UA = "TaksiAndroid/1.0 (https://github.com/toxa0111/Taksi)"

private fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000
    c.readTimeout = 15000
    c.setRequestProperty("User-Agent", UA)
    c.setRequestProperty("Accept", "application/json")
    val code = c.responseCode
    if (code !in 200..299) throw Exception("HTTP $code")
    return c.inputStream.bufferedReader().use { it.readText() }
}

/** Koordinatani qisqa kalitga aylantiradi (yaqin nuqtalar bir xil kalit beradi). */
fun keyOf(p: Pair<Double, Double>, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f,%.${decimals}f", p.first, p.second)

// ───────────────────────── Marshrut (OSRM) ─────────────────────────

/** points — chiziq nuqtalari (lat, lng), km — masofa, min — davomiylik (daqiqa). */
class RouteInfo(
    val points: List<Pair<Double, Double>>,
    val km: Double,
    val min: Int,
    val toKey: String = "",
    val steps: List<NavStep> = emptyList(),
) {
    fun withKey(k: String) = RouteInfo(points, km, min, k, steps)
}

/** Ichki navigatsiya qadami: manevr joyi (lat, lng), ko'rsatma matni va belgisi. */
class NavStep(val lat: Double, val lng: Double, val arrow: String, val text: String, val meters: Double)

private fun stepArrow(type: String, mod: String): String = when {
    type == "arrive" -> "⚑"
    type == "depart" -> "↑"
    type == "roundabout" || type == "rotary" || type == "roundabout turn" -> "⟳"
    mod == "left" -> "←"
    mod == "right" -> "→"
    mod == "slight left" -> "↖"
    mod == "slight right" -> "↗"
    mod == "sharp left" -> "↰"
    mod == "sharp right" -> "↱"
    mod == "uturn" -> "↶"
    else -> "↑"
}

private fun stepText(type: String, mod: String, name: String, exit: Int): String {
    val base = when {
        type == "arrive" -> "Manzilga yetdingiz"
        type == "depart" -> "Yo'lga chiqing"
        type == "roundabout" || type == "rotary" ->
            if (exit > 0) "Aylanada $exit-chiqishdan chiqing" else "Aylanadan chiqing"
        mod == "left" -> "Chapga buriling"
        mod == "right" -> "O'ngga buriling"
        mod == "slight left" -> "Chapga biroz buriling"
        mod == "slight right" -> "O'ngga biroz buriling"
        mod == "sharp left" -> "Chapga keskin buriling"
        mod == "sharp right" -> "O'ngga keskin buriling"
        mod == "uturn" -> "Orqaga qayriling"
        type == "merge" -> "Yo'lga qo'shiling"
        else -> "To'g'ri yuring"
    }
    return if (name.isNotBlank() && type != "arrive") "$base — $name" else base
}

/** Ikki nuqta orasidagi masofa (metr). */
fun meters(a: Pair<Double, Double>, bLat: Double, bLng: Double): Double {
    val r = FloatArray(1)
    android.location.Location.distanceBetween(a.first, a.second, bLat, bLng, r)
    return r[0].toDouble()
}

object Osrm {
    fun route(a: Pair<Double, Double>, b: Pair<Double, Double>): RouteInfo? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${a.second},${a.first};${b.second},${b.first}?overview=full&geometries=geojson&steps=true"
            val j = JSONObject(httpGet(url))
            if (j.optString("code") != "Ok") return null
            val r = j.getJSONArray("routes").getJSONObject(0)
            val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
            val n = coords.length()
            if (n < 2) return null
            val step = maxOf(1, n / 400)
            val pts = ArrayList<Pair<Double, Double>>()
            var i = 0
            while (i < n) {
                val c = coords.getJSONArray(i)
                pts.add(Pair(c.getDouble(1), c.getDouble(0)))
                i += step
            }
            val last = coords.getJSONArray(n - 1)
            pts.add(Pair(last.getDouble(1), last.getDouble(0)))
            val km = r.getDouble("distance") / 1000.0
            val min = maxOf(1, Math.ceil(r.getDouble("duration") / 60.0).toInt())
            val steps = ArrayList<NavStep>()
            try {
                val legs = r.getJSONArray("legs")
                for (li in 0 until legs.length()) {
                    val st = legs.getJSONObject(li).getJSONArray("steps")
                    for (si in 0 until st.length()) {
                        val o = st.getJSONObject(si)
                        val m = o.getJSONObject("maneuver")
                        val loc = m.getJSONArray("location")
                        val type = m.optString("type")
                        val mod = m.optString("modifier")
                        steps.add(
                            NavStep(
                                loc.getDouble(1), loc.getDouble(0), stepArrow(type, mod),
                                stepText(type, mod, o.optString("name"), m.optInt("exit", 0)),
                                o.optDouble("distance", 0.0),
                            )
                        )
                    }
                }
            } catch (e: Exception) { steps.clear() }
            RouteInfo(pts, km, min, "", steps)
        } catch (e: Exception) { null }
    }
}

/**
 * Marshrutni so'raydi. Faqat boshlanish/tugash nuqtasi sezilarli o'zgarganda qayta so'raydi
 * (fromDecimals=3 taxminan 110 m, 4 taxminan 11 m). Xato bo'lsa, 3 marta urinadi.
 */
@Composable
fun rememberRoute(from: Pair<Double, Double>?, to: Pair<Double, Double>?, fromDecimals: Int = 4): RouteInfo? {
    val fk = if (from == null) "" else keyOf(from, fromDecimals)
    val tk = if (to == null) "" else keyOf(to, 4)
    var result by remember { mutableStateOf<RouteInfo?>(null) }
    LaunchedEffect(fk, tk) {
        if (from == null || to == null) {
            result = null
        } else {
            var attempt = 0
            while (attempt < 3) {
                val r = io { Osrm.route(from, to) }
                if (r != null) { result = r.withKey(tk); break }
                attempt++
                delay(2500)
            }
        }
    }
    val r = result
    return if (r != null && tk.isNotEmpty() && r.toKey == tk) r else null
}

// ───────────────────────── Manzil qidirish (Nominatim) ─────────────────────────

class Place(val name: String, val sub: String, val lat: Double, val lng: Double)

object Nominatim {
    private var last = 0L

    /** Nominatim qoidasi: soniyasiga 1 tadan ko'p so'rov yo'q. */
    @Synchronized
    private fun throttle() {
        val wait = last + 1100L - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        last = System.currentTimeMillis()
    }

    private fun get(url: String): String {
        throttle()
        return httpGet(url)
    }

    /** Toshkent atrofidan qidiradi. Xato bo'lsa — bo'sh ro'yxat. */
    fun search(q: String): List<Place> {
        return try {
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=uz" +
                "&accept-language=uz,ru,en&viewbox=69.10,41.45,69.45,41.20&q=" + URLEncoder.encode(q, "UTF-8")
            val arr = org.json.JSONArray(get(url))
            val out = ArrayList<Place>()
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val full = o.optString("display_name")
                if (!seen.add(full)) continue
                val parts = full.split(",").map { it.trim() }
                val nm = o.optString("name").ifBlank { parts.firstOrNull() ?: full }
                val sub = parts.drop(1).take(3).joinToString(", ")
                out.add(Place(nm, sub, o.getString("lat").toDouble(), o.getString("lon").toDouble()))
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    /** Nuqtadan manzil nomi. Topilmasa null. */
    fun reverse(lat: Double, lng: Double): String? {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=uz,ru,en" +
                "&lat=" + String.format(Locale.US, "%.6f", lat) + "&lon=" + String.format(Locale.US, "%.6f", lng)
            val j = JSONObject(get(url))
            val ad = j.optJSONObject("address")
            val nm = j.optString("name")
            var road = ad?.optString("road") ?: ""
            if (road.isBlank()) road = ad?.optString("pedestrian") ?: ""
            val num = ad?.optString("house_number") ?: ""
            val street = (road + " " + num).trim()
            val full = j.optString("display_name").split(",").map { it.trim() }.take(2).joinToString(", ")
            val s = when {
                nm.isNotBlank() && street.isNotBlank() && nm != road -> "$nm, $street"
                nm.isNotBlank() -> nm
                street.isNotBlank() -> street
                else -> full
            }
            if (s.isBlank()) null else s.take(80)
        } catch (e: Exception) { null }
    }
}

/** Yangi buyurtma: qisqa tebranish va standart bildirishnoma ovozi. */
@Suppress("DEPRECATION")
fun alertNewOrder(ctx: Context) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (v != null) {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(450)
        }
    } catch (e: Exception) { }
    try {
        RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    } catch (e: Exception) { }
}
