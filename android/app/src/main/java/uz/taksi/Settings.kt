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
import androidx.compose.ui.unit.dp

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

    private var prefs: SharedPreferences? = null

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        lang = p.getString("lang", "uz") ?: "uz"
        // Birinchi ochilishda telefonning qorong'i rejimiga mos keladi
        val systemDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        dark = if (p.contains("dark")) p.getBoolean("dark", false) else systemDark
        sound = p.getString("sound", "on") ?: "on"
        fontScale = p.getFloat("font", 1.0f)
    }

    fun setLang(v: String) { lang = v; prefs?.edit()?.putString("lang", v)?.apply() }
    fun setDark(v: Boolean) { dark = v; prefs?.edit()?.putBoolean("dark", v)?.apply() }
    fun setSound(v: String) { sound = v; prefs?.edit()?.putString("sound", v)?.apply() }
    fun setFontScale(v: Float) { fontScale = v; prefs?.edit()?.putFloat("font", v)?.apply() }
}

val LANGS = listOf("uz" to "O'zbekcha", "ru" to "Русский", "en" to "English")

/** Kirish ekranlarida til tanlash qatori. */
@Composable
fun LangBar(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Segmented(LANGS, AppSettings.lang) { AppSettings.setLang(it) }
    }
}
