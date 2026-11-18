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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val ITEM_TITLE = mapOf(
    "orders" to "Qabul qilingan buyurtmalar", "carYear" to "Avtomobilning yili", "license" to "Litsenziya kartasi",
    "brand" to "Mashina brendi", "r475" to "Reyting 4,75 dan yuqori", "r485" to "Reyting 4,85 dan yuqori",
)

@Composable
private fun Gauge(value: Int, max: Int, color: Color) {
    Box(Modifier.fillMaxWidth().size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(210.dp)) {
            val w = 22.dp.toPx(); val pad = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(Color(0x22000000), 150f, 240f, false, Offset(pad, pad), sz, style = Stroke(w, cap = StrokeCap.Round))
            val frac = if (max > 0) (value.coerceIn(0, max).toFloat() / max) else 0f
            if (frac > 0f) drawArc(color, 150f, 240f * frac, false, Offset(pad, pad), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            Text("$max dan", fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun PointItem(title: String, pts: String, sub: String, hint: String, got: Boolean) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (got) Color(0xFFFFD60A) else Color(0x22000000)), contentAlignment = Alignment.Center) {
                Text(if (got) "✓" else "🔒", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(pts, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Muted)
            }
            Text("  ›", fontSize = 22.sp, color = Muted)
        }
        if (open) Text(hint, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 52.dp, top = 6.dp))
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
        val items = x?.optJSONArray("items")?.items() ?: emptyList()
        val mx = x?.optInt("max") ?: 0
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Gauge(pr, mx, if (pr >= 0) green else red)
            Text(if ((x?.optInt("delaySec") ?: 0) > 0) "Buyurtmalar kechroq ko'rinadi" else "Buyurtmalarni odatiy qidirish", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            if (x != null) Text("Ballar: " + signed(x.optInt("base")) + (if (pen > 0) "   ·   Jarimalar: −$pen" else ""), fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (pen > 0) Color(0xFFFFE3E3) else Chip).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    Text(if (pen > 0) "−$pen" else "0", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = if (pen > 0) red else Muted)
                }
                Text(if (pen > 0) "Jarima ballari" else "Muammo yo'q", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFE1F5E9)).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    Text((x?.optInt("trips") ?: 0).toString(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = green)
                }
                Text("Yakunlangan safar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val delay = x?.optInt("delaySec") ?: 0
        if (delay > 0) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF1C2)).padding(12.dp)) {
                Text("Ballingiz kam: yangi buyurtmalar sizga $delay soniya kechroq ko'rinadi. Safarlarni yakunlab ballni tiklang.", fontSize = 13.sp, color = Ink)
            }
        }

        val locked = items.filter { !it.optBoolean("got") }
        if (locked.isNotEmpty()) {
            val gain = locked.sumOf { it.optInt("pts") }
            Text("Darajangizni ${pr + gain} ga oshiring", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth()) {
                locked.forEach { PointItem(ITEM_TITLE[it.optString("k")] ?: it.optString("k"), "+" + it.optInt("pts"), "", it.optString("hint"), false) }
            }
        }
        val got = items.filter { it.optBoolean("got") }
        if (got.isNotEmpty()) {
            Text("Olinganlar", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth()) {
                got.forEach { PointItem(ITEM_TITLE[it.optString("k")] ?: it.optString("k"), "+" + it.optInt("pts"), it.optString("sub"), it.optString("hint"), true) }
            }
        }

        Text("Ball qanday ortadi", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip).padding(horizontal = 14.dp, vertical = 8.dp)) {
            RuleRow("+1", green, "Har 4 ta safar", "200 safargacha hisoblanadi (eng ko'pi +50 ball).")
            RuleRow("+15", green, "Reyting 4,75 va 4,85", "Har bir chegara uchun +15 ball.")
            RuleRow("+15", green, "Mashina yili va guvohnoma", "2015-yildan yangi mashina va tasdiqlangan guvohnoma.")
            RuleRow("+18", green, "Mashina brendi", "Zamonaviy marka bo'lsa.")
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
