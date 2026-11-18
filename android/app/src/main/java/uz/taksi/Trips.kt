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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Haydovchining yakunlangan safarlari tarixi (profildan bir bosishda ochiladi). */
@Composable
fun TripsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val list by rememberMyOrders()
    val done = (list ?: emptyList()).filter { it.optString("status") == "done" }.sortedByDescending { it.optLong("doneAt", it.optLong("createdAt")) }
    val fmt = SimpleDateFormat("d.MM.yyyy, HH:mm", Locale.getDefault())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
            Spacer(Modifier.width(6.dp))
            Text("Safarlar", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(18.dp)) {
            Text("Jami ${done.size} ta safar", fontSize = 14.sp, color = Muted)
            Text(som(done.sumOf { it.optLong("price") }), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
        }
        if (list == null) Text("Yuklanmoqda…", color = Muted)
        else if (done.isEmpty()) Text("Hali yakunlangan safar yo'q", color = Muted)
        done.forEach { o ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Date(o.optLong("doneAt", o.optLong("createdAt")))), fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(som(o.optLong("price")), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(o.optString("from"), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                Text("→ " + o.optString("to"), fontSize = 14.sp, color = Muted, maxLines = 1)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (o.optString("payMethod") == "cash") "💵 Naqd" else "💳 Karta", fontSize = 12.sp, color = Muted)
                    if (o.has("rating")) Text("★ " + o.optInt("rating"), fontSize = 12.sp, color = Yellow)
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
