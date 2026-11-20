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

/** Zamonaviy sintezator ringtonlar: kod bilan hosil qilinadi (fayl kerak emas). 16 qadamlik sikl, takrorlanadi. */
class Ev(val step: Int, val kind: Char, val note: Int, val len: Int = 2)   // kind: p pluck, b bell, s sub-bass, k kick, h hat, c chord-pad

class Ringtone2(val id: String, val name: String, val bpm: Int, val events: List<Ev>, val echo: Double = 0.38) {
    fun render(rate: Int): ShortArray {
        val step = 60.0 / bpm / 4.0
        val n = (step * 16 * rate).toInt()
        val buf = DoubleArray(n)
        fun hz(m: Int) = 440.0 * Math.pow(2.0, (m - 69) / 12.0)
        var seed = 12345L
        for (e in events) {
            val start = (e.step * step * rate).toInt()
            val f = hz(e.note)
            val len = when (e.kind) { 'k' -> 0.30; 'h' -> 0.06; 'c' -> e.len * step + 0.25; else -> e.len * step + 0.45 }
            val cnt = (len * rate).toInt()
            for (i in 0 until cnt) {
                val t = i.toDouble() / rate
                val att = if (t < 0.004) t / 0.004 else 1.0
                val v: Double = when (e.kind) {
                    'p' -> {
                        var a = 0.0
                        for (h in 1..7) a += Math.sin(2 * Math.PI * f * h * t) / h * Math.exp(-h * 1.1 * t) +
                                Math.sin(2 * Math.PI * f * 1.004 * h * t) / h * Math.exp(-h * 1.1 * t)
                        a * 0.30 * Math.exp(-4.5 * t)
                    }
                    'b' -> Math.sin(2 * Math.PI * f * t + 2.2 * Math.exp(-6 * t) * Math.sin(2 * Math.PI * f * 3.5 * t)) * Math.exp(-4.2 * t) * 0.5
                    's' -> (Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(4 * Math.PI * f * t)) * Math.exp(-2.6 * t) * 0.75
                    'k' -> Math.sin(2 * Math.PI * (45.0 * t + 105.0 * (1 - Math.exp(-28 * t)) / 28.0)) * Math.exp(-11 * t) * 0.95
                    'h' -> { seed = seed * 6364136223846793005L + 1442695040888963407L; (((seed shr 33).toInt() and 0xFFFF) / 32768.0 - 1.0) * Math.exp(-70 * t) * 0.16 }
                    else -> {   // yumshoq pad akkord
                        val r = if (t > e.len * step) Math.exp(-9 * (t - e.len * step)) else 1.0
                        val a = Math.min(1.0, t / 0.05) * r
                        var x = 0.0
                        for (d in doubleArrayOf(0.996, 1.0, 1.004)) for (h in 1..4) x += Math.sin(2 * Math.PI * f * d * h * t) / (h * h)
                        x * 0.12 * a
                    }
                }
                val idx = (start + i) % n
                buf[idx] += v * att
            }
        }
        // exo (echo): ikki o'tishda, sikl uzluksiz bo'lishi uchun aylana bo'ylab
        val d = (step * 3 * rate).toInt()
        repeat(2) { for (i in 0 until n) buf[i] += echo * buf[(i - d + n) % n] }
        var peak = 0.0
        for (x in buf) if (Math.abs(x) > peak) peak = Math.abs(x)
        val g = if (peak > 0) 0.85 / peak else 1.0
        val out = ShortArray(n)
        for (i in 0 until n) out[i] = (Math.tanh(buf[i] * g * 1.2) * 0.9 * Short.MAX_VALUE).toInt().toShort()
        return out
    }
}

private fun arp(notes: List<Int>, kind: Char, from: Int = 0, every: Int = 1, len: Int = 1) =
    (from until 16 step every).mapIndexed { i, st -> Ev(st, kind, notes[i % notes.size], len) }
private fun kicks(vararg st: Int) = st.map { Ev(it, 'k', 0) }
private fun hats(vararg st: Int) = st.map { Ev(it, 'h', 0) }
private fun bass(vararg p: Pair<Int, Int>) = p.map { Ev(it.first, 's', it.second, 3) }

val RINGTONES = listOf(
    Ringtone2("neon", "Neon", 118,
        arp(listOf(69, 72, 76, 81, 76, 72), 'p', 0, 1) + kicks(0, 4, 8, 12) + hats(2, 6, 10, 14) + bass(0 to 33, 6 to 33, 10 to 36)),
    Ringtone2("pulse", "Puls", 124,
        arp(listOf(64, 67, 71, 76), 'p', 0, 2) + arp(listOf(88, 83), 'b', 1, 4) + kicks(0, 3, 8, 11) + hats(4, 12) + bass(0 to 28, 8 to 31)),
    Ringtone2("glass", "Shisha", 100,
        arp(listOf(88, 91, 95, 100, 95, 91), 'b', 0, 2) + bass(0 to 36, 8 to 38) + hats(4, 12)),
    Ringtone2("drive", "Drayv", 128,
        arp(listOf(57, 60, 64, 60), 'p', 0, 1) + kicks(0, 4, 8, 12) + hats(2, 6, 10, 14) + bass(0 to 33, 2 to 33, 8 to 36, 10 to 36)),
    Ringtone2("aurora", "Aurora", 92,
        listOf(Ev(0, 'c', 60, 8), Ev(0, 'c', 64, 8), Ev(0, 'c', 67, 8), Ev(8, 'c', 62, 8), Ev(8, 'c', 65, 8), Ev(8, 'c', 69, 8)) +
            arp(listOf(84, 88, 91, 96), 'b', 0, 4) + bass(0 to 36, 8 to 38)),
    Ringtone2("orbit", "Orbita", 110,
        arp(listOf(72, 79, 84, 79, 76, 79), 'p', 0, 1) + arp(listOf(96), 'b', 0, 8) + kicks(0, 6, 8, 14) + bass(0 to 36, 6 to 36, 8 to 40, 14 to 40)),
    Ringtone2("vibe", "Vayb", 98,
        arp(listOf(67, 70, 74, 79, 74, 70), 'p', 0, 2) + arp(listOf(91, 86), 'b', 3, 6) + kicks(0, 6, 10) + hats(4, 8, 12, 15) + bass(0 to 31, 6 to 31, 10 to 34)),
    Ringtone2("lift", "Lift", 132,
        arp(listOf(60, 64, 67, 72, 67, 64, 60, 55), 'p', 0, 1) + kicks(0, 4, 8, 12) + hats(2, 6, 10, 14) + bass(0 to 36, 4 to 36, 8 to 43, 12 to 43)),
)

/** Sozlamalardagi "Ringtonlar" bo'limi: bosilsa tanlanadi va bir marta eshittiriladi. */
@Composable
fun RingtonePanel() {
    val ctx = LocalContext.current
    Panel {
        Text("Rington", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Yangi buyurtma kelganda eshitiladigan ovoz. Bosib tanlang.", fontSize = 11.sp, color = Muted)
        val all = listOf("default" to "Telefon ovozi") + RINGTONES.map { it.id to it.name }
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
