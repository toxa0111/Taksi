package uz.taksi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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

private fun dt(ms: Long) = SimpleDateFormat("d.MM, HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun Back(onBack: () -> Unit, title: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ChatRow(icon: String, tint: Color, title: String, sub: String = "", badge: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(to = 0.98f, onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, fontSize = 14.sp, color = Muted, maxLines = 1)
        }
        if (badge.isNotEmpty()) Text(badge, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(end = 6.dp))
        Text("›", fontSize = 24.sp, color = Muted)
    }
}

private class Story(val icon: String, val title: String, val body: String, val bg: Color)

/** Haydovchining "Xabarlar" bo'limi: Yandex Pro uslubida. */
@Composable
fun DriverChats() {
    var scr by remember { mutableStateOf("home") }
    var story by remember { mutableStateOf<Story?>(null) }
    var bal by remember { mutableStateOf<JSONObject?>(null) }
    var notes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var news by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick, scr) {
        while (scr == "home") {
            try { bal = io { Api.call("GET", "/driver/balance") } } catch (e: Exception) { }
            try { notes = io { Api.call("GET", "/notifications").getJSONArray("notes").items() } } catch (e: Exception) { }
            try { news = io { Api.call("GET", "/news").getJSONArray("news").items() } } catch (e: Exception) { }
            delay(12000)
        }
    }
    if (scr != "home") BackHandler { scr = "home" }
    val back = { scr = "home" }

    when (scr) {
        "ai" -> { AiChat(back, onOperator = { scr = "tickets" }); return }
        "topup" -> { TopUpScreen(back); return }
        "tickets" -> {
            Column(Modifier.fillMaxSize()) {
                Back(back, "Qo'llab-quvvatlash")
                Box(Modifier.weight(1f)) { SupportTab("driver") }
            }
            return
        }
        "notes" -> { NotesScreen(back, "Ogohlantirishlar", notes); return }
        "news" -> { NewsScreen(back, news); return }
        "bonus" -> {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Back(back, "Bonuslar")
                BonusPanel()
            }
            return
        }
        "ledger" -> { BalanceScreen(back, onTopUp = { scr = "topup" }); return }
    }

    val balance = bal?.optLong("balance") ?: 0L
    val warn = bal?.optLong("warn", 5000) ?: 5000L
    val locked = bal?.optBoolean("locked") ?: false
    val low = bal != null && (locked || balance <= warn + 5000)
    val pct = bal?.optInt("commission", 10) ?: 10

    story?.let { s ->
        AlertDialog(
            onDismissRequest = { story = null },
            title = { Text(s.title, fontWeight = FontWeight.Bold) },
            text = { Text(s.body) },
            confirmButton = { TextButton(onClick = { story = null }) { Text("Yopish") } },
        )
    }

    val stories = listOf(
        Story("💳", "Hisobni qanday to'ldiraman?",
            "Chatlar → TOXA ofisi → «Hisobni to'ldirish» ni bosing. Karta, Click, Payme, Paynet, Uzum yoki taksi ofisida naqd pul bilan to'ldirish mumkin. To'lovdan keyin hisob tasdiqlanishi bilan to'ladi.", Color(0xFFFFE9A8)),
        Story("🚖", "Nega buyurtmalar yo'q?",
            "Liniyada ekaningizni, hujjatlaringiz tasdiqlanganini va hisobingizda " + som(warn) + " dan ko'p mablag' borligini tekshiring. Xaritadagi qizil zonalarda buyurtmalar ko'proq bo'ladi.", Color(0xFFCDE8FF)),
        Story("💰", "Komissiya haqida",
            "Har bir tugagan safardan narxning $pct% i hisobingizdan yechiladi. Hisobda " + som(warn) + " qolsa ogohlantirish beriladi va liniyadan chiqarilasiz; hisob 10 000 so'mdan oshgach yana ishlay olasiz.", Color(0xFFFFD6D6)),
        Story("🎁", "Yangi haydovchi bonusi",
            "Yangi ulangan haydovchiga 20 000 so'm bonus beriladi. U komissiyaga sarflanadi, naqd yechib bo'lmaydi.", Color(0xFFD9F5DF)),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TabTitle("Xabarlar")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stories.forEach { s ->
                Box(
                    Modifier.size(width = 140.dp, height = 170.dp).clip(RoundedCornerShape(18.dp)).background(s.bg)
                        .clickable { story = s }.padding(12.dp),
                ) {
                    Text(s.icon, fontSize = 30.sp)
                    Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        ChatRow("🙋", Color(0xFFBFE9C9), "Yordam xizmati", "Claude yordamchi · 24/7") { scr = "ai" }
        ChatRow(
            "🏢", Color(0xFFFFD98A), "TOXA ofisi",
            if (low) "Balansni to'ldiring! (" + som(balance) + ")" else "Hisobni to'ldirish · " + som(balance),
            badge = if (low) "❗" else "",
        ) { scr = "topup" }
        ChatRow("⚠", Color(0xFFFFC9C9), "Ogohlantirishlar",
            notes.firstOrNull { it.optString("kind") != "news" }?.optString("title") ?: "Yangi ogohlantirish yo'q",
            badge = notes.firstOrNull()?.let { dt(it.optLong("t")) } ?: "") { scr = "notes" }
        ChatRow("📰", Color(0xFFC9D8FF), "TOXA yangiliklari", news.firstOrNull()?.optString("title") ?: "", badge = news.firstOrNull()?.let { dt(it.optLong("t")) } ?: "") { scr = "news" }
        ChatRow("🎯", Color(0xFFFFE08A), "Bonuslar", "Maqsadli bonus va taklif kodi") { scr = "bonus" }
        ChatRow("🎧", Color(0xFFD9C9FF), "Qo'llab-quvvatlash", "Operator bilan yozishma") { scr = "tickets" }
        ChatRow("🧾", Color(0xFFBFE9E9), "Hisob balansidagi o'zgarishlar", "Komissiya, to'ldirish, bonus") { scr = "ledger" }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NotesScreen(onBack: () -> Unit, title: String, notes: List<JSONObject>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Back(onBack, title)
        if (notes.isEmpty()) Text("Hozircha xabar yo'q", color = Muted)
        notes.forEach { n ->
            Panel {
                Row(Modifier.fillMaxWidth()) {
                    Text(n.optString("title"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(dt(n.optLong("t")), fontSize = 12.sp, color = Muted)
                }
                Text(n.optString("text"), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun NewsScreen(onBack: () -> Unit, news: List<JSONObject>) = NotesScreen(onBack, "TOXA yangiliklari", news)

/** Claude yordamchi (haydovchi va yo'lovchi uchun). */
@Composable
fun AiChat(onBack: () -> Unit, onOperator: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var enabled by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        try {
            val j = io { Api.call("GET", "/support/ai") }
            enabled = j.optBoolean("enabled", true)
            msgs = j.optJSONArray("messages")?.items() ?: emptyList()
        } catch (e: Exception) { }
    }
    LaunchedEffect(msgs.size, busy) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Back(onBack, "Yordam xizmati")
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Chip).padding(12.dp)) {
                Text(
                    "Salom! Men TOXA yordamchisiman (Claude). Komissiya, hisobni to'ldirish, buyurtmalar, bonuslar va ilovadan foydalanish bo'yicha savol bering." +
                        if (enabled) "" else " Hozircha tayyor javoblar rejimida ishlayman.",
                    fontSize = 14.sp,
                )
            }
            msgs.forEach { m ->
                val mine = m.optString("r") == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Yellow else Card).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text(m.optString("text"), fontSize = 15.sp, color = if (mine) Ink else Fg) }
                }
            }
            if (busy) Text("Yozmoqda…", fontSize = 13.sp, color = Muted)
            ErrorText(err)
            GhostButton("🎧 Operator bilan bog'lanish", onClick = onOperator)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Field(text, { text = it }, "Savolingiz") }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (busy || text.isBlank()) Chip else Yellow)
                    .clickable(enabled = !busy && text.trim().length >= 2) {
                        val q = text.trim()
                        text = ""; busy = true; err = ""
                        msgs = msgs + JSONObject().put("r", "user").put("text", q)
                        scope.launch {
                            try {
                                val j = io { Api.call("POST", "/support/ai", JSONObject().put("text", q)) }
                                msgs = j.optJSONArray("messages")?.items() ?: msgs
                            } catch (e: Exception) { err = e.message ?: "Xato" }
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("➤", fontSize = 22.sp, color = Ink) }
        }
    }
}

/** Haydovchi hisobini to'ldirish. */
@Composable
fun TopUpScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var method by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<JSONObject?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(tick) {
        try { data = io { Api.call("GET", "/driver/topup") } } catch (e: Exception) { err = e.message ?: "Xato" }
    }
    val d = data
    val methods = d?.optJSONArray("methods")?.items() ?: emptyList()
    val offices = d?.optJSONArray("offices")?.items()?.map {
        it.optString("name") + (if (it.optString("address").isNotBlank()) " — " + it.optString("address") else "") +
            (if (it.optString("phone").isNotBlank()) " · " + it.optString("phone") else "")
    } ?: emptyList()
    val min = d?.optLong("min", 5000) ?: 5000L

    done?.let { r ->
        AlertDialog(
            onDismissRequest = { done = null },
            title = { Text("So'rov yuborildi", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Summa: " + som(r.optLong("amount")) + "\nKod: " + r.optString("code") +
                        "\n\nTo'lov izohiga shu kodni yozing (naqd bo'lsa ofisda ayting). To'lov tasdiqlangach hisobingiz avtomatik to'ldiriladi.",
                )
            },
            confirmButton = { TextButton(onClick = { done = null }) { Text("Tushunarli") } },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Back(onBack, "Hisobni to'ldirish")
        if (d == null) Text("Yuklanmoqda…", color = Muted)
        if (d != null && methods.isEmpty()) {
            Panel { Text("To'lov usullari hali sozlanmagan. Operatorga murojaat qiling.", color = Muted) }
        }
        if (methods.isNotEmpty()) {
            Text("To'lov usuli", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            methods.forEach { m ->
                val sel = method == m.optString("key")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (sel) Color(0xFFFFF1C2) else Card)
                        .clickable { method = m.optString("key") }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (sel) "●" else "○", fontSize = 20.sp, color = Ink)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(m.optString("name"), fontWeight = FontWeight.SemiBold, color = if (sel) Ink else Fg)
                        Text(m.optString("info"), fontSize = 13.sp, color = if (sel) Ink else Muted)
                    }
                }
            }
            if (method == "cash" && offices.isNotEmpty()) {
                Panel {
                    Text("Taksi ofislari", fontWeight = FontWeight.Bold)
                    offices.forEach { Text("📍 $it", fontSize = 14.sp) }
                }
            }
            Text("Summa", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10000, 20000, 50000, 100000, 200000).forEach { v ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (amount == v.toString()) Yellow else Chip)
                            .clickable { amount = v.toString() }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(som(v.toLong()), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (amount == v.toString()) Ink else Fg) }
                }
            }
            Field(amount, { amount = it.filter(Char::isDigit).take(8) }, "Boshqa summa (so'm)", keyboard = KeyboardType.Number)
            ErrorText(err)
            BigButton(
                "So'rov yuborish",
                enabled = !busy && method.isNotEmpty() && (amount.toLongOrNull() ?: 0L) >= min,
            ) {
                scope.launch {
                    busy = true
                    try {
                        val j = io { Api.call("POST", "/driver/topup", JSONObject().put("method", method).put("amount", amount.toLong())) }
                        done = j.optJSONObject("request") ?: j
                        amount = ""; err = ""; tick++
                    } catch (e: Exception) { err = e.message ?: "Xato" }
                    busy = false
                }
            }
            Text("Eng kam summa: " + som(min), fontSize = 12.sp, color = Muted)
        }

        val reqs = d?.optJSONArray("requests")?.items() ?: emptyList()
        if (reqs.isNotEmpty()) {
            Text("So'rovlarim", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
            reqs.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(som(r.optLong("amount")) + "  ·  " + r.optString("code"), fontWeight = FontWeight.Medium)
                        Text(dt(r.optLong("at")), fontSize = 12.sp, color = Muted)
                    }
                    when (r.optString("status")) {
                        "confirmed" -> Pill("To'ldirildi", Color(0xFFE1F5E9), Color(0xFF14753F))
                        "rejected" -> Pill("Rad etildi", Color(0xFFFCE4E4), Color(0xFFA32626))
                        else -> Pill("Kutilmoqda", Color(0xFFFFF1C2), Color(0xFF8A6100))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
