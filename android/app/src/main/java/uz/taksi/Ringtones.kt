package uz.taksi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Yangi buyurtma ringtoni: res/raw/rt_<id>.ogg (tools/make_ringtones.py bilan sintez qilingan, takrorlanuvchi sikl). */
class Ringtone2(val id: String, val name: String)

val RINGTONES = listOf(
    Ringtone2("neon", "Neon"), Ringtone2("aurora", "Aurora"), Ringtone2("puls", "Puls"), Ringtone2("drift", "Drift"),
    Ringtone2("trap", "Trap"), Ringtone2("velvet", "Velvet"), Ringtone2("lift", "Lift"), Ringtone2("kristall", "Kristall"),
)

/** Sozlamalardagi "Ringtonlar" bo'limi: bosilsa tanlanadi va bir marta eshittiriladi. */
@Composable
fun RingtonePanel() {
    val ctx = LocalContext.current
    Panel {
        Text("Rington", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Yangi buyurtma kelganda eshitiladigan ovoz. Bosib tanlang.", fontSize = 11.sp, color = Muted)
        val all = listOf("default" to "Telefon ovozi") + RINGTONES.map { it.id to it.name }
        val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    // Faylni ilova ichiga ko'chiramiz: keyin ruxsat yoki joy o'zgarsa ham ishlayveradi
                    var nm = "rington"
                    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) nm = it.getString(0) ?: nm }
                    val f = java.io.File(ctx.filesDir, "custom_ringtone")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    AppSettings.changeRingtoneName(nm)
                    AppSettings.changeRingtoneUri(f.absolutePath)
                    AppSettings.changeRingtone("custom")
                    OfferSound.preview(ctx, "custom")
                } catch (e: Exception) { }
            }
        }
        val customName = AppSettings.ringtoneName.ifBlank { null }
        Row(
            Modifier.fillMaxWidth().clickable { pick.launch(arrayOf("audio/*")) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (AppSettings.ringtone == "custom") "◉" else "○", fontSize = 20.sp, color = if (AppSettings.ringtone == "custom") Yellow else Muted)
            Column {
                Text("Telefondan musiqa yuklash", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(customName ?: "O'zingizdagi istalgan qo'shiqni qo'ying", fontSize = 12.sp, color = Muted, maxLines = 1)
            }
        }
        all.forEach { (id, name) ->
            val sel = AppSettings.ringtone == id
            Row(
                Modifier.fillMaxWidth().clickable {
                    AppSettings.changeRingtone(id)
                    OfferSound.preview(ctx, id)
                }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (sel) "◉" else "○", fontSize = 20.sp, color = if (sel) Yellow else Muted)
                Text(name, fontSize = 16.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
        if (AppSettings.sound != "on") Text("Ovoz o'chiq: rington eshitilmaydi (Ovoz bo'limida yoqing).", fontSize = 11.sp, color = Muted)
    }
}
