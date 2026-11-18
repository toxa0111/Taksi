package uz.taksi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BarBlue = Color(0xFF3D5AFE)

private fun hm(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
private fun dm(ms: Long) = SimpleDateFormat("d.MM, HH:mm", Locale.getDefault()).format(Date(ms))
private fun dayNum(d: String) = d.takeLast(2).trimStart('0')
private fun dayShort(d: String) = dayNum(d) + "." + d.substring(5, 7)

/** Kunlik daromad ustunlari. big=true bo'lsa, summalar ham ko'rsatiladi va ustunni tanlash mumkin. */
@Composable
private fun DayBars(days: List<JSONObject>, selected: String, big: Boolean, onSelect: (String) -> Unit) {
    val max = (days.maxOfOrNull { it.optLong("sum") } ?: 0L).coerceAtLeast(1L)
    val h = if (big) 170.dp else 60.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (big) 6.dp else 5.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            val sum = d.optLong("sum")
            val sel = d.optString("date") == selected
            Column(Modifier.weight(1f).clickable(enabled = big) { onSelect(d.optString("date")) }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (big) Text(if (sum > 0) sum.toString() else "0", fontSize = 9.sp, color = if (sel) BarBlue else Muted, maxLines = 1)
                Box(Modifier.height(h), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height(maxOf(4.dp, h * (sum.toFloat() / max.toFloat())))
                            .clip(RoundedCornerShape(if (big) 12.dp else 8.dp))
                            .background(if (sel) BarBlue else Chip),
                    )
                }
                Text(dayNum(d.optString("date")), fontSize = if (big) 13.sp else 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) BarBlue else Muted)
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit, content: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun OrderRow(o: JSONObject) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) { Text("🚕", fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hm(o.optLong("t")), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(o.optString("to"), fontSize = 13.sp, color = Muted, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(som(o.optLong("price")), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (o.has("pay")) Text(if (o.optString("pay") == "cash") "💵 Naqd" else "💳 Karta", fontSize = 11.sp, color = Muted)
        }
    }
}

/** Pul qabul qilish: naqd va naqdsiz (karta) to'lovlar alohida. */
@Composable
private fun PayKinds(d: JSONObject?) {
    var kind by remember { mutableStateOf("cash") }
    val cash = d?.optLong("cash") ?: 0L
    val card = d?.optLong("card") ?: 0L
    val list = (d?.optJSONArray("orders")?.items() ?: emptyList()).filter { it.optString("pay") == kind }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Chip).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pul qabul qilish", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Segmented(listOf("cash" to "💵 Naqd pul", "card" to "💳 Naqdsiz"), kind) { kind = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(som(if (kind == "cash") cash else card), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Bugun · " + list.size + " ta safar", fontSize = 13.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (kind == "cash") "Naqd jami" else "Naqdsiz jami", fontSize = 11.sp, color = Muted)
                Text(som(cash + card), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            if (kind == "cash") "Yo'lovchidan naqd olingan pul. Komissiya hisobingizdan yechiladi."
            else "Karta orqali to'langan safarlar. Karta to'lovi to'lov tizimi ulangach to'liq ishlaydi.",
            fontSize = 12.sp, color = Muted,
        )
        if (list.isEmpty()) Text("Bu turdagi to'lov hali yo'q", fontSize = 13.sp, color = Muted)
        list.take(5).forEach { OrderRow(it) }
    }
}

/** Haydovchi "Pul" bo'limi (Yandex Pro uslubida): bosh sahifa, daromad va balans ekranlari. */
@Composable
fun DriverMoney(goSupport: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    var day by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    BackHandler(enabled = screen != "home") { screen = "home" }

    LaunchedEffect(day, tick) {
        while (true) {
            try { data = io { Api.call("GET", "/driver/earnings" + if (day.isNotEmpty()) "?day=$day" else "") } } catch (e: Exception) { }
            delay(10000)
        }
    }
    val d = data
    val days = d?.optJSONArray("days")?.items() ?: emptyList()
    val sel = d?.optString("day").orEmpty()
    val todayKey = days.lastOrNull()?.optString("date").orEmpty()
    val todaySum = days.lastOrNull()?.optLong("sum") ?: 0L

    when (screen) {
        "earn" -> EarnScreen(d, days, sel, onSelect = { day = it }, onBack = { screen = "home"; day = "" })
        "balance" -> BalanceScreen(onBack = { screen = "home" }, onTopUp = { screen = "topup" })
        "topup" -> TopUpScreen { screen = "balance" }
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pul", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF0A8A55)).clickable { goSupport() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("👋 Yordam xizmati", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
            Column(Modifier.fillMaxWidth().clickable { screen = "earn" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1.3f)) {
                        Text(som(todaySum), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Bugun", fontSize = 15.sp)
                    }
                    Box(Modifier.weight(1f)) { if (days.isNotEmpty()) DayBars(days.takeLast(7), todayKey, false) { } }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Chip).clickable { screen = "earn" }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Card), contentAlignment = Alignment.Center) { Text("🕘", fontSize = 22.sp) }
                Spacer(Modifier.width(14.dp))
                Text("Oxirgi buyurtmalar", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("›", fontSize = 24.sp, color = Muted)
            }
            BalanceCard { screen = "balance" }
            PayKinds(d)
            BonusPanel()
        }
    }
}

@Composable
private fun BalanceCard(onClick: () -> Unit) {
    var bal by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try { bal = io { Api.call("GET", "/driver/balance") }.optLong("balance") } catch (e: Exception) { }
            delay(10000)
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Chip).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hisob balansi", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(bal?.let { som(it) } ?: "…", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("Pul yechish va harakatlar tarixi uchun bosing", fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun EarnScreen(d: JSONObject?, days: List<JSONObject>, sel: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("cmp") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackRow(onBack) {
            Segmented(listOf("cmp" to "Taqqoslash", "det" to "Tafsilotlar"), tab) { tab = it }
        }
        val total = d?.optLong("total") ?: 0L
        val isToday = days.isNotEmpty() && days.last().optString("date") == sel
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isToday) "Bugun" else if (sel.isNotEmpty()) dayShort(sel) else "", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(som(total), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        }
        val cash = d?.optLong("cash") ?: 0L
        val card = d?.optLong("card") ?: 0L
        val bonus = d?.optLong("bonus") ?: 0L
        if (tab == "cmp") {
            if (days.isNotEmpty()) DayBars(days, sel, true, onSelect)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip2("Naqd pul bilan · " + som(cash), Modifier.weight(1f))
                Chip2("Bonuslar · " + som(bonus), Modifier.weight(1f))
            }
            if (card > 0) Chip2("Karta bilan · " + som(card), Modifier)
            val fee = kotlin.math.abs(d?.optLong("commission") ?: 0L)
            if (fee > 0) Chip2("Komissiya (" + (d?.optInt("commissionPercent", 10) ?: 10) + "%) · −" + som(fee), Modifier)
        } else {
            val sum = (cash + card + bonus).coerceAtLeast(1L)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Chip).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BreakdownRow("Naqd pulda", cash, sum, BarBlue.copy(alpha = 0.25f))
                BreakdownRow("Bonuslar", bonus, sum, BarBlue.copy(alpha = 0.25f))
                if (card > 0) BreakdownRow("Karta", card, sum, BarBlue.copy(alpha = 0.25f))
                BreakdownRow("Daromad", total, sum, Ink)
            }
        }
        val orders = d?.optJSONArray("orders")?.items() ?: emptyList()
        Text(if (isToday) "Bugun" else dayShort(sel), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (orders.isEmpty()) Text("Bu kunda safarlar yo'q", color = Muted)
        orders.forEach { OrderRow(it) }
    }
}

@Composable
private fun Chip2(text: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(Card).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BreakdownRow(label: String, value: Long, sum: Long, color: Color) {
    val frac = (value.toFloat() / sum.toFloat()).coerceIn(0f, 1f)
    val dark = color == Ink
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (dark) Color(0xFFE0E0E0) else Color.Transparent)) {
            Box(Modifier.fillMaxWidth(maxOf(frac, 0.04f)).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(color))
            Text(label, fontSize = 14.sp, color = if (dark) Color.White else Fg, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        }
        Text(value.toString(), fontSize = 16.sp, modifier = Modifier.width(96.dp).padding(start = 10.dp))
    }
}

@Composable
fun BalanceScreen(onBack: () -> Unit, onTopUp: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        while (true) {
            try { data = io { Api.call("GET", "/driver/balance") } } catch (e: Exception) { }
            delay(10000)
        }
    }
    val d = data
    val balance = d?.optLong("balance") ?: 0L
    val min = d?.optLong("minWithdraw", 20000) ?: 20000L
    val wdAble = d?.optLong("withdrawable", balance) ?: balance
    val locked = d?.optBoolean("locked") ?: false
    val warn = d?.optLong("warn", 5000) ?: 5000L
    val resume = d?.optLong("resume", 10000) ?: 10000L
    if (dialog) WithdrawDialog(wdAble, min) { sent -> dialog = false; if (sent) tick++ }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackRow(onBack)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Balans", fontSize = 18.sp)
            Text(som(balance), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (locked || balance <= warn) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFCE4E4)).padding(12.dp)) {
                Text(
                    if (locked) "Hisobingiz kam: liniyaga chiqish yopilgan. Hisobni " + som(resume) + " dan oshirib to'ldiring."
                    else "Hisobingizda " + som(balance) + " qoldi. " + som(warn) + " ga tushsa liniyadan chiqarilasiz.",
                    fontSize = 13.sp, color = Color(0xFFA32626), fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally)) {
            Column(Modifier.clickable(onClick = onTopUp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) { Text("+", fontSize = 32.sp, color = Ink) }
                Text("To'ldirish", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Column(Modifier.clickable(enabled = wdAble >= min) { dialog = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) { Text("↑", fontSize = 28.sp) }
                Text("Chiqarish", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (wdAble < balance) Text("Yechish mumkin: " + som(wdAble) + " (bonus qismi faqat komissiyaga sarflanadi)", fontSize = 12.sp, color = Muted, modifier = Modifier.fillMaxWidth())
        else if (wdAble < min) Text("Pul chiqarish uchun kamida " + som(min) + " kerak", fontSize = 12.sp, color = Muted, modifier = Modifier.fillMaxWidth())
        Text("Har bir safardan " + (d?.optInt("commission", 10) ?: 10) + "% komissiya yechiladi", fontSize = 12.sp, color = Muted, modifier = Modifier.fillMaxWidth())

        val ws = d?.optJSONArray("withdrawals")?.items() ?: emptyList()
        if (ws.isNotEmpty()) {
            Text("So'rovlar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            ws.take(5).forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dm(w.optLong("at")) + "  ·  " + som(w.optLong("amount")), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    when (w.optString("status")) {
                        "paid" -> Pill("To'landi", Color(0xFFE1F5E9), Color(0xFF14753F))
                        "rejected" -> Pill("Rad etildi", Color(0xFFFCE4E4), Color(0xFFA32626))
                        else -> Pill("Kutilmoqda", Color(0xFFFFF1C2), Color(0xFF8A6100))
                    }
                }
            }
        }

        Text("Tranzaksiyalar tarixi", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        val led = d?.optJSONArray("ledger")?.items() ?: emptyList()
        if (led.isEmpty()) Text("Hali harakatlar yo'q", color = Muted)
        led.take(if (all) 30 else 6).forEach { e ->
            val amt = e.optLong("amount")
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) {
                    Text(when (e.optString("type")) {
                        "withdraw" -> "↑"; "commission" -> "％"; "topup" -> "＋"; "welcome" -> "🎉"; else -> "🎁"
                    }, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.optString("note"), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(dm(e.optLong("t")), fontSize = 12.sp, color = Muted)
                }
                Text((if (amt >= 0) "+" else "−") + som(kotlin.math.abs(amt)), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (amt >= 0) Ok else Bad)
            }
        }
        if (!all && led.size > 6) GhostButton("Hammasini ochish") { all = true }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WithdrawDialog(balance: Long, min: Long, onClose: (sent: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose(false) },
        title = { Text("Pul yechish") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mavjud: " + som(balance) + "  ·  kamida " + som(min), fontSize = 13.sp, color = Muted)
                Field(amount, { amount = it.filter { c -> c.isDigit() }.take(9) }, "Summa (so'm)", keyboard = KeyboardType.Number)
                Field(note, { note = it.take(80) }, "Karta raqami yoki izoh")
                if (err.isNotEmpty()) ErrorText(err)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val a = amount.toLongOrNull() ?: 0L
                if (a < min) { err = "Kamida " + som(min); return@TextButton }
                if (a > balance) { err = "Balans yetarli emas"; return@TextButton }
                if (note.trim().length < 4) { err = "Karta raqami yoki izohni yozing"; return@TextButton }
                busy = true; err = ""
                scope.launch {
                    try {
                        io { Api.call("POST", "/driver/withdraw", JSONObject().put("amount", a).put("note", note.trim())) }
                        onClose(true)
                    } catch (e: Exception) { err = e.message ?: "Xato"; busy = false }
                }
            }) { Text("Yuborish") }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text("Orqaga") } },
    )
}
