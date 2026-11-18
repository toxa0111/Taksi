package uz.taksi

import android.content.Context
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
    /** Server "Qaytadan kiring" (401) desa chaqiriladi: ilova o'zi chiqib, kirish ekranini ko'rsatadi. */
    var onUnauthorized: (() -> Unit)? = null

    /** Rasm (yoki boshqa fayl) baytlarini oladi, masalan admin uchun hujjat rasmi. */
    fun bytes(path: String): ByteArray {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 45000
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        if (c.responseCode !in 200..299) throw Exception("Rasm topilmadi")
        return c.inputStream.use { it.readBytes() }
    }

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
        if (code == 401 && token.isNotEmpty() && !path.startsWith("/auth")) {
            onUnauthorized?.invoke()
            throw Exception("Sessiya tugadi. Qaytadan kiring")
        }
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

/**
 * Eng so'nggi release: Pair(versiya raqami, APK havolasi) yoki null.
 * tag == null: foydalanuvchi ilovasi (releases/latest, tag "build-N").
 * tag berilsa (masalan "toxa-admin"): shu tegdagi release, versiya raqami nomining oxiridan olinadi.
 */
suspend fun latestRelease(tag: String? = null): Pair<Int, String>? {
    // Avval o'z serverimiz (GitHub cheklovlari tegmaydi), bo'lmasa to'g'ridan-to'g'ri GitHub
    try {
        val r = withContext(Dispatchers.IO) { Api.call("GET", "/app/release" + if (tag != null) "?tag=$tag" else "") }
        val v = r.optInt("version"); val u = r.optString("url")
        if (v > 0 && u.startsWith("https://")) return Pair(v, u)
    } catch (e: Exception) { }
    return latestReleaseGithub(tag)
}

suspend fun latestReleaseGithub(tag: String? = null): Pair<Int, String>? = withContext(Dispatchers.IO) {
    val path = if (tag == null) "releases/latest" else "releases/tags/$tag"
    val endpoint = "https://api.github.com/repos/$REPO/$path"
    val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
        requestMethod = "GET"
        useCaches = false
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "TOXA-Android-Updater")
    }
    try {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = try { JSONObject(response).optString("message").ifBlank { "HTTP $code" } }
                         catch (_: Exception) { "HTTP $code" }
            throw Exception("GitHub yangilanish serveri: $detail (HTTP $code)")
        }
        val j = JSONObject(response)
        val version = if (tag == null) {
            j.getString("tag_name").removePrefix("build-").toIntOrNull()
        } else {
            Regex("(\\d+)\\s*$").find(j.optString("name"))?.groupValues?.get(1)?.toIntOrNull()
        } ?: throw Exception("Release versiya raqami noto'g'ri")
        val assets = j.optJSONArray("assets") ?: throw Exception("APK ro'yxati topilmadi")
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val assetName = asset.optString("name")
            if (if (tag == "toxa-admin") assetName == "toxa-admin.apk" else assetName.endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                if (apkUrl != null) break
            }
        }
        apkUrl?.let { Pair(version, it) } ?: throw Exception("Release ichida APK fayli topilmadi")
    } finally {
        c.disconnect()
    }
}

/** Qo'llab-quvvatlash aloqa ma'lumotlari. Bo'sh bo'lsa, tegishli tugma ko'rsatilmaydi. */
const val SUPPORT_PHONE = "+998973869030"      // masalan: "+998901234567"
const val SUPPORT_TELEGRAM = "jackpoooot"   // masalan: "toxa_support" (@ belgisiz)

/** Karta to'lovi provayderi ulangunga qadar o'chiq (server ham shunday rad etadi). */
const val CARD_ENABLED = false
