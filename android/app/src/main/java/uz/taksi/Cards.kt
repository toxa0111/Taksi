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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun luhn(n: String): Boolean {
    var sum = 0
    n.reversed().forEachIndexed { i, c ->
        var d = c - '0'
        if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
        sum += d
    }
    return sum % 10 == 0
}

/** Yo'lovchining to'lov kartalari. Serverga faqat birinchi 6 va oxirgi 4 raqam yuboriladi. */
@Composable
fun CardsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var number by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        try { cards = io { Api.call("GET", "/cards").getJSONArray("cards").items() } } catch (e: Exception) { err = e.message ?: "Xato" }
    }
    suspend fun act(method: String, path: String) {
        try { cards = io { Api.call(method, path).getJSONArray("cards").items() }; err = "" }
        catch (e: Exception) { err = e.message ?: "Xato" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
            Spacer(Modifier.width(6.dp))
            Text("To'lov kartalari", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (cards.isEmpty()) Panel { Text("Hali karta qo'shilmagan", color = Muted) }
        cards.forEach { c ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFFFE9A8)), contentAlignment = Alignment.Center) { Text("💳", fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.optString("brand") + "  •••• " + c.optString("last4"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (c.optBoolean("main")) Text("Asosiy karta", fontSize = 12.sp, color = Ok)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!c.optBoolean("main")) GhostButton("Asosiy qilish", Modifier.weight(1f)) { scope.launch { act("POST", "/cards/" + c.optString("id")) } }
                    GhostButton("O'chirish", Modifier.weight(1f)) { scope.launch { act("DELETE", "/cards/" + c.optString("id")) } }
                }
            }
        }
        Text("Yangi karta qo'shish", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
        Panel {
            Field(
                number.chunked(4).joinToString(" "),
                { number = it.filter(Char::isDigit).take(16) },
                "Karta raqami (16 xona)", keyboard = KeyboardType.Number,
            )
            ErrorText(err)
            BigButton("Qo'shish", enabled = !busy && number.length == 16) {
                if (!luhn(number)) { err = "Karta raqami noto'g'ri"; return@BigButton }
                scope.launch {
                    busy = true
                    try {
                        val j = io { Api.call("POST", "/cards", JSONObject().put("bin", number.take(6)).put("last4", number.takeLast(4))) }
                        cards = j.getJSONArray("cards").items(); number = ""; err = ""
                    } catch (e: Exception) { err = e.message ?: "Xato" }
                    busy = false
                }
            }
        }
        Text(
            "Xavfsizlik: ilova faqat kartaning birinchi 6 va oxirgi 4 raqamini saqlaydi. To'liq raqam, muddat va CVV hech qayerda saqlanmaydi. " +
                "Karta orqali avtomatik to'lov to'lov tizimi (Click/Payme) ulangach ishga tushadi, hozircha to'lov naqd.",
            fontSize = 12.sp, color = Muted,
        )
    }
}
