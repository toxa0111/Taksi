package uz.taksi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Baho sabablari (teglar): kalit server bilan bir xil. */
val TAGS_POS = listOf(
    "care" to "Haydovchi g'amxo'rligi", "polite" to "Haydovchining xushmuomalaligi", "style" to "Haydash uslubi yoqimli",
    "music" to "Yoqimli musiqa", "clean" to "Saloni toza", "chat" to "Qiziqarli suhbat",
)
val TAGS_NEG = listOf(
    "rude" to "Qo'pol muomala", "dirty" to "Salon iflos", "unsafe" to "Xavfli haydash",
    "late" to "Kech qoldi", "route" to "Yo'ldan adashdi", "loud" to "Musiqa baland",
)

private fun starColor(n: Int) = when {
    n >= 5 -> Color(0xFF1FBF55)
    n == 4 -> Color(0xFFFFC21A)
    else -> Color(0xFFFF7A5A)
}

@Composable
private fun StarCard(stars: Int, count: Int, tags: List<Pair<String, Int>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                (1..5).forEach { i -> Text("★", fontSize = 26.sp, color = if (i <= stars) starColor(stars) else Color(0x33808080)) }
            }
            Text(count.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        tags.forEach { (label, n) ->
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(n.toString(), fontSize = 15.sp)
            }
        }
    }
}

/** Yandex Pro uslubidagi "Reyting" ekrani: oxirgi baholar, yulduzlar bo'yicha taqsimot va sabablar. */
@Composable
fun RatingScreen(onBack: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var scope by remember { mutableStateOf("all") }
    BackHandler(onBack = onBack)
    LaunchedEffect(scope) { try { d = io { Api.call("GET", "/driver/ratings?scope=$scope") } } catch (e: Exception) { } }
    val x = d
    val st = x?.optJSONObject("stars")
    val tg = x?.optJSONObject("tags")
    fun star(n: Int) = st?.optInt(n.toString()) ?: 0
    fun tag(k: String) = tg?.optInt(k) ?: 0
    val fmt = SimpleDateFormat("d.MM", Locale.getDefault())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
            Spacer(Modifier.width(6.dp))
            Text("Reyting", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        val rating = x?.optDouble("rating") ?: 0.0
        val considered = x?.optInt("considered") ?: 0
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (considered > 0) String.format("%.2f", rating) else "—", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
            Text("Reyting oxirgi ${x?.optInt("window") ?: 150} ta bahoning o'rtachasi", fontSize = 13.sp, color = Muted)
        }
        Segmented(listOf("all" to "Hisobdagi baholar", "day" to "Oxirgi 24 soat"), scope) { scope = it }
        if (x != null) Text("Yangilandi: " + SimpleDateFormat("d.MM, HH:mm", Locale.getDefault()).format(Date(x.optLong("updated"))), fontSize = 12.sp, color = Muted)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reytingda hisobga olinadigan oxirgi baholar", fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(considered.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
            val seq = x?.optJSONArray("seq")
            if (seq != null && seq.length() > 0) {
                val n = seq.length()
                Canvas(Modifier.fillMaxWidth().padding(top = 12.dp).height(10.dp)) {
                    val w = size.width / n
                    for (i in 0 until n) drawRect(starColor(seq.optInt(i)), Offset(i * w, 0f), Size(w + 0.5f, size.height))
                }
                val f = x.optLong("fromT"); val t = x.optLong("toT")
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(fmt.format(Date(f)), fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(fmt.format(Date(t)), fontSize = 12.sp, color = Muted)
                }
            } else Text("Hali baho yo'q", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }

        StarCard(5, star(5), TAGS_POS.map { it.second to tag(it.first) }.filter { it.second > 0 }.sortedByDescending { it.second })
        StarCard(4, star(4), emptyList())
        StarCard(3, star(3), emptyList())
        StarCard(2, star(2), emptyList())
        StarCard(1, star(1), emptyList())

        val neg = TAGS_NEG.map { it.second to tag(it.first) }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (neg.isNotEmpty()) {
            Text("Shikoyat sabablari", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                neg.forEach { (label, n) -> Row(Modifier.fillMaxWidth()) { Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text(n.toString(), fontSize = 15.sp, color = Bad) } }
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip).padding(14.dp)) {
            Text("Reyting yo'lovchilar bergan oxirgi 150 ta bahodan hisoblanadi. 4,75 va 4,85 dan yuqori reyting ustuvorlik ballini oshiradi.", fontSize = 13.sp)
        }
        Spacer(Modifier.size(16.dp))
    }
}
