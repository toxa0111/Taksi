package uz.taksi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun RuleRow(sign: String, color: Color, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(sign, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, fontSize = 13.sp, color = Muted)
        }
    }
}

/** Yandex Pro uslubidagi "Ustuvorlik" ekrani: ball qoidalari va o'zgarishlar tarixi. */
@Composable
fun PointsScreen(onBack: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { try { d = io { Api.call("GET", "/driver/points") } } catch (e: Exception) { } }
    val x = d
    val pr = x?.optInt("priority") ?: 0
    val pen = x?.optInt("penalty") ?: 0
    val r = x?.optJSONObject("rules")
    val km = r?.optInt("offerKm", 3) ?: 3
    val sec = r?.optInt("offerSec", 10) ?: 10
    val op = r?.optInt("offerPenalty", 7) ?: 7
    val cp = r?.optInt("cancelPenalty", 4) ?: 4
    val green = Color(0xFF14A05A); val red = Color(0xFFD33A3A)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
            Spacer(Modifier.width(6.dp))
            Text("Ustuvorlik", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(signed(pr), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = if (pr >= 0) green else red)
            Text("ball", fontSize = 14.sp, color = Muted)
            if (x != null) Text(
                "Reyting va safarlardan: " + signed(x.optInt("base")) + (if (pen > 0) "   ·   Jarimalar: −$pen" else ""),
                fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
            )
        }
        val delay = x?.optInt("delaySec") ?: 0
        if (delay > 0) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF1C2)).padding(12.dp)) {
                Text("Ballingiz kam: yangi buyurtmalar sizga $delay soniya kechroq ko'rinadi. Safarlarni yakunlab ballni tiklang.", fontSize = 13.sp, color = Ink)
            }
        }

        Text("Ball qanday ortadi", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip).padding(horizontal = 14.dp, vertical = 8.dp)) {
            RuleRow("+1", green, "Har 4 ta safar", "200 safargacha hisoblanadi (eng ko'pi +50 ball).")
            RuleRow("+4", green, "Reyting 4.0 dan yuqori", "Har 0.1 reyting uchun +4 ball.")
            RuleRow("+1", green, "Yakunlangan safar", "Har bir tugagan safar jarimadan 1 ball qaytaradi.")
        }
        Text("Ball qanday kamayadi", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip).padding(horizontal = 14.dp, vertical = 8.dp)) {
            RuleRow("−$op", red, "Buyurtmani olmaslik", "$km km ichidagi buyurtmani $sec soniyada qabul qilmasangiz yoki rad etsangiz.")
            RuleRow("−$cp", red, "Qabul qilib bekor qilish", "Qabul qilingan buyurtmani bekor qilsangiz.")
        }
        Text("Ustuvorlik nimaga ta'sir qiladi", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip).padding(14.dp)) {
            Text("Ustuvorlik yuqori haydovchilarga yangi buyurtmalar birinchi bo'lib ko'rinadi. Har $op ball jarima buyurtmalarni 3 soniyaga kechiktiradi (eng ko'pi 12 soniya).", fontSize = 13.sp)
        }

        val log = x?.optJSONArray("log")?.items() ?: emptyList()
        Text("Ball tarixi", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        if (log.isEmpty()) Text("Hali o'zgarish bo'lmagan", color = Muted)
        log.forEach { e ->
            val dl = e.optInt("d")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.optString("why"), fontSize = 14.sp)
                    Text(SimpleDateFormat("d.MM, HH:mm", Locale.getDefault()).format(Date(e.optLong("t"))), fontSize = 11.sp, color = Muted)
                }
                Text(signed(dl), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (dl >= 0) green else red)
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
