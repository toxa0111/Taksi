package uz.taksi

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Shahar nomi: manzil qidirilganda oxiriga qo'shiladi. O'zingizning shaharingizga almashtiring. */
const val CITY = "Toshkent"

/** Xarita boshlang'ich markazi (Toshkent). GPS topilguncha shu ko'rinadi. */
val DEFAULT_CENTER = Pair(41.311, 69.279)

object Api {
    /** Emulyatorda 10.0.2.2 — kompyuterning localhost'i. Haqiqiy telefonda server manzilini kiriting. */
    var base = "https://taksi-server.onrender.com"
    var token = ""

    fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 45000   // bepul server uxlab qolsa, uyg'onishiga 30-60 soniya ketadi
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        if (code !in 200..299) throw Exception(json.optString("error", "Xato $code"))
        return json
    }
}

suspend fun <T> io(f: () -> T): T = withContext(Dispatchers.IO) { f() }

fun JSONArray.items(): List<JSONObject> = List(length()) { getJSONObject(it) }

/** Ro'yxat mazmuni o'zgarganini tekshirish uchun: o'zgarmagan bo'lsa ekran qayta chizilmaydi. */
fun List<JSONObject>.sameAs(other: List<JSONObject>): Boolean =
    size == other.size && indices.all { this[it].toString() == other[it].toString() }

fun som(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed() + " so'm"

/** Manzil matnini koordinataga aylantiradi (qurilmadagi Geocoder orqali). */
fun geocode(ctx: Context, q: String): Pair<Double, Double>? {
    if (!Geocoder.isPresent()) return null
    return try {
        @Suppress("DEPRECATION")
        val r = Geocoder(ctx).getFromLocationName("$q, $CITY", 1)
        val a = r?.firstOrNull()
        if (a == null) null else Pair(a.latitude, a.longitude)
    } catch (e: Exception) { null }
}

/** Koordinatadan manzil matni. Topilmasa — "Xaritadagi nuqta". */
fun reverseGeocode(ctx: Context, lat: Double, lng: Double): String {
    val fallback = "Xaritadagi nuqta"
    if (!Geocoder.isPresent()) return fallback
    return try {
        @Suppress("DEPRECATION")
        val r = Geocoder(ctx).getFromLocation(lat, lng, 1)
        r?.firstOrNull()?.getAddressLine(0)?.take(80) ?: fallback
    } catch (e: Exception) { fallback }
}

val STATUS = mapOf(
    "searching" to "🔎 Haydovchi qidirilmoqda",
    "accepted" to "🚕 Haydovchi yo'lda",
    "arrived" to "📍 Haydovchi yetib keldi",
    "started" to "➡️ Safar davom etmoqda",
    "done" to "✅ Yakunlandi",
    "cancelled" to "❌ Bekor qilindi",
)
val ACTIVE = setOf("searching", "accepted", "arrived", "started")

fun payText(o: JSONObject): String {
    val m = if (o.optString("payMethod") == "card") "💳 Karta" else "💵 Naqd"
    return if (o.optString("payStatus") == "paid") "$m · to'langan" else m
}

/** JSON ichidagi {lat,lng} obyektini juftlikka aylantiradi. */
fun locOf(o: JSONObject, key: String): Pair<Double, Double>? {
    val l = o.optJSONObject(key) ?: return null
    return Pair(l.optDouble("lat"), l.optDouble("lng"))
}

const val REPO = "toxa0111/taksi"

fun currentVersionCode(ctx: Context): Int = try {
    val i = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    @Suppress("DEPRECATION") i.versionCode
} catch (e: Exception) { 0 }

/** Eng so'nggi release: Pair(versiya raqami, APK havolasi) yoki null */
suspend fun latestRelease(): Pair<Int, String>? = withContext(Dispatchers.IO) {
    try {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val n = j.getString("tag_name").removePrefix("build-").toInt()
        val assets = j.getJSONArray("assets")
        var url = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
        }
        if (url.isEmpty()) null else Pair(n, url)
    } catch (e: Exception) { null }
}
