package uz.taksi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

const val OFFER_TOTAL_MS = 10_000L
private val OfferBlue = Color(0xFF2F6BFF)

/** Yangi buyurtma: SMS kabi signal + qisqa musiqa (10 soniya davomida takrorlanadi) + tebranish. */
@Suppress("DEPRECATION")
object OfferSound {
    private var track: AudioTrack? = null
    private var tone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private var vib: Vibrator? = null

    // Quvnoq arpeggio: C5 E5 G5 C6 G5 E5 G5 C6
    private val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 783.99, 659.25, 783.99, 1046.5)

    private fun melody(rate: Int): ShortArray {
        val per = (rate * 0.2).toInt()
        val out = ShortArray(per * notes.size)
        notes.forEachIndexed { n, f ->
            for (i in 0 until per) {
                val t = i.toDouble() / rate
                val env = exp(-5.0 * t) * (if (i < 200) i / 200.0 else 1.0)
                val v = (sin(2 * PI * f * t) + 0.35 * sin(4 * PI * f * t)) * env * 0.38
                out[n * per + i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    fun start(ctx: Context) {
        stop()
        try {
            vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pat = longArrayOf(0, 350, 250, 350, 250, 350, 250, 350, 250, 350, 250, 350, 250, 350, 250, 350)
            if (Build.VERSION.SDK_INT >= 26) vib?.vibrate(VibrationEffect.createWaveform(pat, -1)) else vib?.vibrate(pat, -1)
        } catch (e: Exception) { }
        if (AppSettings.sound != "on") return
        try {
            tone = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            tone?.play()
        } catch (e: Exception) { }
        handler.postDelayed({
            try {
                val rate = 22050
                val pcm = melody(rate)
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC).build()
                t.write(pcm, 0, pcm.size)
                t.setLoopPoints(0, pcm.size, -1)
                t.play()
                track = t
            } catch (e: Exception) { }
        }, 900)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        try { track?.stop(); track?.release() } catch (e: Exception) { }
        track = null
        try { tone?.stop() } catch (e: Exception) { }
        tone = null
        try { vib?.cancel() } catch (e: Exception) { }
    }
}

/** Buyurtma taklifi: ko'k chiziq o'ngdan chapga qarab 10 soniyada tugaydi. */
@Composable
fun OfferCard(o: JSONObject, leftMs: Long, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val frac = (leftMs.toFloat() / OFFER_TOTAL_MS).coerceIn(0f, 1f)
    val sec = ((leftMs + 999) / 1000).toInt().coerceAtLeast(0)
    val near = o.has("pickupKm") && o.optDouble("pickupKm") <= 3.0
    Column(
        modifier.fillMaxWidth().padding(12.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Chip)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6FA0FF), OfferBlue)))
                    .align(Alignment.CenterStart),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔔 Yangi buyurtma!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("$sec s", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = OfferBlue)
        }
        Text(som(o.optLong("price")), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("📍 " + o.optString("from"), fontSize = 15.sp, maxLines = 2)
        Text("🏁 " + o.optString("to"), fontSize = 15.sp, color = Muted, maxLines = 2)
        if (o.has("pickupKm")) Text(String.format("Yo'lovchigacha ~%.1f km", o.optDouble("pickupKm")), fontSize = 13.sp, color = Muted)
        BigButton("Qabul qilish", onClick = onAccept)
        DangerButton(if (near) "Rad etish (−7 ball)" else "Rad etish", onClick = onDecline)
        if (near) Text("3 km ichidagi buyurtmani olmasangiz, 7 ball ayiriladi va yangi buyurtmalar kechroq ko'rinadi.", fontSize = 11.sp, color = Muted)
    }
}
