package uz.taksi

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Foydalanuvchi sozlamalari: til, yorug'/qorong'i rejim, ovoz va shrift o'lchami. Telefonda saqlanadi. */
object AppSettings {
    var lang by mutableStateOf("uz")          // uz / ru / en
        private set
    var dark by mutableStateOf(false)
        private set
    var sound by mutableStateOf("on")         // on / vibrate / off
        private set
    var fontScale by mutableStateOf(1.0f)
        private set
    var voice by mutableStateOf("on")         // on / off  (ovozli navigatsiya)
        private set

    private var prefs: SharedPreferences? = null

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        lang = p.getString("lang", "uz") ?: "uz"
        // Birinchi ochilishda telefonning qorong'i rejimiga mos keladi
        val systemDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        dark = p.getBoolean("dark", true)   // brend: sukut bo'yicha qora fon
        sound = p.getString("sound", "on") ?: "on"
        fontScale = p.getFloat("font", 1.0f)
        voice = p.getString("voice", "on") ?: "on"
    }

    fun changeLang(v: String) { lang = v; prefs?.edit()?.putString("lang", v)?.apply() }
    fun changeDark(v: Boolean) { dark = v; prefs?.edit()?.putBoolean("dark", v)?.apply() }
    fun changeSound(v: String) { sound = v; prefs?.edit()?.putString("sound", v)?.apply() }
    fun changeVoice(v: String) { voice = v; prefs?.edit()?.putString("voice", v)?.apply() }
    fun changeFont(v: Float) { fontScale = v; prefs?.edit()?.putFloat("font", v)?.apply() }
}

val LANGS = listOf("uz" to "O'zbekcha", "ru" to "Русский", "en" to "English")

/** Kirish ekranlarida til tanlash qatori. */
@Composable
fun LangBar(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Segmented(LANGS, AppSettings.lang) { AppSettings.changeLang(it) }
    }
}

/** Til, rejim, ovoz va shrift sozlamalari paneli (foydalanuvchi va admin ilovada bir xil). */
@Composable
fun AppPrefsPanel() {
    Panel {
            Text("Sozlamalar", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Til", fontSize = 13.sp, color = Muted)
            Segmented(LANGS, AppSettings.lang) { AppSettings.changeLang(it) }
            Text("Ko'rinish", fontSize = 13.sp, color = Muted)
            Segmented(listOf("light" to "☀ Yorug'", "dark" to "🌙 Qorong'i"), if (AppSettings.dark) "dark" else "light") {
                AppSettings.changeDark(it == "dark")
            }
            Text("Ovoz", fontSize = 13.sp, color = Muted)
            Segmented(listOf("on" to "🔔 Ovozli", "vibrate" to "📳 Tebranish", "off" to "🔕 Ovozsiz"), AppSettings.sound) {
                AppSettings.changeSound(it)
            }
            Text("Yangi buyurtma ovozi va tebranishi", fontSize = 11.sp, color = Muted)
            Text("Ovozli navigatsiya", fontSize = 13.sp, color = Muted)
            Segmented(listOf("on" to "🗣 Yoqilgan", "off" to "🔇 O'chiq"), AppSettings.voice) { AppSettings.changeVoice(it) }
            Text("Shrift o'lchami", fontSize = 13.sp, color = Muted)
            Segmented(
                listOf("0.85" to "Kichik", "1.0" to "O'rta", "1.2" to "Katta"),
                when { AppSettings.fontScale < 0.95f -> "0.85"; AppSettings.fontScale > 1.1f -> "1.2"; else -> "1.0" },
            ) { AppSettings.changeFont(it.toFloat()) }
            Text("Matn o'lchami butun ilovada o'zgaradi", fontSize = 11.sp, color = Muted)
        }
}

/** Ilova ochiq (oldingi planda) yoki yo'qligini bildiradi: fonda bildirishnomalar faqat yopiq paytda chiqadi. */
object AppState {
    @Volatile var foreground = false
}
