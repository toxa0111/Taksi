package uz.taksi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

/** Yangi buyurtma ringtoni: kod bilan hosil qilinadi (fayl kerak emas). 0.0 = pauza. */
class Ringtone2(
    val id: String, val name: String, val notes: DoubleArray, val noteSec: Double,
    val decay: Double, val harm: Double, val harm2: Double = 0.0,
)

val RINGTONES = listOf(
    Ringtone2("classic", "Klassik", doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 783.99, 659.25, 783.99, 1046.5), 0.2, 5.0, 0.35),
    Ringtone2("bell", "Qo'ng'iroq", doubleArrayOf(1318.5, 0.0, 1318.5, 0.0, 987.8, 1318.5, 0.0, 0.0), 0.24, 6.0, 0.5, 0.25),
    Ringtone2("signal", "Signal", doubleArrayOf(880.0, 0.0, 880.0, 0.0, 880.0, 0.0, 0.0, 0.0), 0.16, 3.0, 0.6, 0.4),
    Ringtone2("siren", "Sirena", doubleArrayOf(660.0, 880.0, 660.0, 880.0, 660.0, 880.0), 0.32, 1.5, 0.2),
    Ringtone2("marimba", "Marimba", doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 783.99, 659.25, 587.33), 0.17, 9.0, 0.15, 0.1),
    Ringtone2("crystal", "Kristall", doubleArrayOf(1568.0, 1975.5, 2349.3, 1975.5, 1568.0, 0.0, 1318.5, 0.0), 0.22, 7.0, 0.3),
    Ringtone2("drum", "Baraban", doubleArrayOf(196.0, 0.0, 196.0, 261.6, 0.0, 196.0, 0.0, 130.8), 0.2, 11.0, 0.7, 0.4),
    Ringtone2("waltz", "Vals", doubleArrayOf(392.0, 523.25, 659.25, 659.25, 523.25, 659.25, 784.0, 0.0), 0.28, 4.0, 0.3, 0.1),
    Ringtone2("fanfare", "Fanfara", doubleArrayOf(523.25, 523.25, 523.25, 784.0, 659.25, 784.0, 1046.5, 1046.5), 0.18, 3.5, 0.45, 0.2),
)

/** Sozlamalardagi "Ringtonlar" bo'limi: bosilsa tanlanadi va bir marta eshittiriladi. */
@Composable
fun RingtonePanel() {
    val ctx = LocalContext.current
    Panel {
        Text("Rington", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Yangi buyurtma kelganda eshitiladigan ovoz. Bosib tanlang.", fontSize = 11.sp, color = Muted)
        val all = listOf("default" to "Standart (telefon + musiqa)") + RINGTONES.map { it.id to it.name }
        all.forEach { (id, name) ->
            val sel = AppSettings.ringtone == id
            Row(
                Modifier.fillMaxWidth().clickable {
                    AppSettings.changeRingtone(id)
                    OfferSound.preview(ctx, if (id == "default") "classic" else id)
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
