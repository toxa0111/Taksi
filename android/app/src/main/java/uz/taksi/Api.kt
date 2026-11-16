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

/** GitHub Releases ichidan eng yangi build APK sini topadi (stable va test release'lar). */
suspend fun latestRelease(): Pair<Int, String>? = withContext(Dispatchers.IO) {
    try {
        val c = URL("https://api.github.com/repos/$REPO/releases?per_page=30").openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 20000
        c.useCaches = false
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TOXA-Android-App")
        c.setRequestProperty("Cache-Control", "no-cache")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        val code = c.responseCode
        if (code !in 200..299) {
            android.util.Log.w("TOXA_UPDATE", "GitHub release tekshiruvi HTTP $code; zaxira Atom feed tekshiriladi")
            throw java.io.IOException("GitHub HTTP $code")
        }
        val releases = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        c.disconnect()
        var bestVersion = -1
        var bestUrl = ""
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            val tag = release.optString("tag_name")
            if (!tag.startsWith("build-")) continue
            val version = tag.removePrefix("build-").toIntOrNull() ?: continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                // Faqat foydalanuvchi ilovasi APK sini tanlaymiz; admin APK yangilanish sifatida olinmasin.
                if (name.equals("taksi.apk", ignoreCase = true) && downloadUrl.startsWith("https://") && version > bestVersion) {
                    bestVersion = version
                    bestUrl = downloadUrl
                }
            }
        }
        if (bestVersion > 0 && bestUrl.isNotBlank()) Pair(bestVersion, bestUrl) else null
    } catch (e: Exception) {
        android.util.Log.w("TOXA_UPDATE", "GitHub API tekshiruvi bajarilmadi, Atom feed zaxira usuli ishga tushadi", e)
        try {
            // GitHub API rate-limit yoki vaqtinchalik xatoda releases Atom feed ishlatiladi.
            val feed = URL("https://github.com/$REPO/releases.atom").openConnection() as HttpURLConnection
            feed.connectTimeout = 15000
            feed.readTimeout = 15000
            feed.useCaches = false
            feed.setRequestProperty("User-Agent", "TOXA-Android-App")
            val xml = feed.inputStream.bufferedReader().use { it.readText() }
            feed.disconnect()
            val versions = Regex("""releases/tag/(build-([0-9]+))""")
                .findAll(xml)
                .mapNotNull { match ->
                    val version = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    version to match.groupValues[1]
                }
                .toList()
            val newest = versions.maxByOrNull { it.first }
            newest?.let { it.first to "https://github.com/$REPO/releases/download/${it.second}/taksi.apk" }
        } catch (fallbackError: Exception) {
            android.util.Log.w("TOXA_UPDATE", "Atom feed orqali ham tekshirib bo'lmadi", fallbackError)
            null
        }
    }
}
