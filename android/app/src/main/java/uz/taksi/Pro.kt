package uz.taksi

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
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

// ───────────────────────── Ovozli navigatsiya ─────────────────────────

/** Telefonning matn o'qish (TTS) imkoniyati bilan burilish ko'rsatmalarini aytib beradi. */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var lastLang = ""

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            applyLang()
        }
    }

    private fun applyLang() {
        val t = tts ?: return
        if (!ready) return
        val loc = when (AppSettings.lang) { "ru" -> Locale("ru", "RU"); "en" -> Locale.US; else -> Locale("uz", "UZ") }
        var r = t.setLanguage(loc)
        // O'zbek tili telefonda bo'lmasa, rus tilida o'qiladi (lotin yozuvi ham tushunarli chiqadi)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = t.setLanguage(Locale("ru", "RU"))
        lastLang = AppSettings.lang
    }

    fun say(text: String) {
        if (AppSettings.voice != "on" || text.isBlank()) return
        val t = tts ?: return
        if (!ready) return
        if (lastLang != AppSettings.lang) applyLang()
        try { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav") } catch (e: Exception) { }
    }

    fun stop() { try { tts?.stop() } catch (e: Exception) { } }
}

private fun dayText(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

// ───────────────────────── Bonuslar (haydovchi) ─────────────────────────

@Composable
fun BonusPanel() {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try { data = io { Api.call("GET", "/driver/bonus") } } catch (e: Exception) { }
            delay(10000)
        }
    }
    val d = data ?: return
    val tiers = d.optJSONArray("tiers") ?: return
    if (tiers.length() == 0) return
    val today = d.optInt("today")
    Panel {
        Text("🎁 Bugungi bonuslar", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Bugun $today ta safar tugatdingiz", fontSize = 13.sp, color = Muted)
        for (i in 0 until tiers.length()) {
            val t = tiers.getJSONObject(i)
            val need = t.optInt("trips")
            val done = t.optBoolean("done")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (done) "✅" else "🎯", fontSize = 20.sp)
                Text("$need ta safar", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (done) Pill("+" + som(t.optLong("reward")), Color(0xFFE1F5E9), Color(0xFF14753F))
                else Text("+" + som(t.optLong("reward")) + "  ·  yana ${maxOf(0, need - today)} ta", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

// ───────────────────────── Balans va pul yechish (haydovchi) ─────────────────────────

@Composable
fun BalancePanel() {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        while (true) {
            try { data = io { Api.call("GET", "/driver/balance") } } catch (e: Exception) { }
            delay(10000)
        }
    }
    val d = data ?: return
    val balance = d.optLong("balance")
    val min = d.optLong("minWithdraw", 20000)

    if (dialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) dialog = false },
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
                            dialog = false; amount = ""; note = ""; tick++
                        } catch (e: Exception) { err = e.message ?: "Xato" }
                        busy = false
                    }
                }) { Text("Yuborish") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Orqaga") } },
        )
    }

    Panel {
        Text("💰 Balans", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(som(balance), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        BigButton("Pul yechish", enabled = balance >= min) { err = ""; dialog = true }
        if (balance < min) Text("Pul yechish uchun kamida " + som(min) + " kerak", fontSize = 12.sp, color = Muted)

        val ws = d.optJSONArray("withdrawals")
        if (ws != null && ws.length() > 0) {
            Text("So'rovlar", fontSize = 13.sp, color = Muted)
            for (i in 0 until minOf(ws.length(), 5)) {
                val w = ws.getJSONObject(i)
                val st = w.optString("status")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayText(w.optLong("at")) + "  ·  " + som(w.optLong("amount")), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    when (st) {
                        "paid" -> Pill("To'landi", Color(0xFFE1F5E9), Color(0xFF14753F))
                        "rejected" -> Pill("Rad etildi", Color(0xFFFCE4E4), Color(0xFFA32626))
                        else -> Pill("Kutilmoqda", Color(0xFFFFF1C2), Color(0xFF8A6100))
                    }
                }
            }
        }
        val led = d.optJSONArray("ledger")
        if (led != null && led.length() > 0) {
            Text("Oxirgi harakatlar", fontSize = 13.sp, color = Muted)
            for (i in 0 until minOf(led.length(), 8)) {
                val e = led.getJSONObject(i)
                val amt = e.optLong("amount")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.optString("note"), fontSize = 13.sp)
                        Text(dayText(e.optLong("t")), fontSize = 11.sp, color = Muted)
                    }
                    Text((if (amt >= 0) "+" else "−") + som(kotlin.math.abs(amt)), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (amt >= 0) Ok else Bad)
                }
            }
        }
    }
}

// ───────────────────────── Yo'qolgan narsa (yo'lovchi) ─────────────────────────

@Composable
fun LostItemDialog(orderId: Int, onClose: (sent: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose(false) },
        title = { Text("Narsa qoldirdingizmi?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nima qoldirganingizni yozing. Haydovchiga xabar boradi va u siz bilan bog'lanadi.", fontSize = 13.sp, color = Muted)
                Field(text, { text = it.take(120) }, "Masalan: qora ryukzak")
                if (err.isNotEmpty()) ErrorText(err)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (text.trim().length < 3) { err = "Kamida 3 ta belgi"; return@TextButton }
                busy = true; err = ""
                scope.launch {
                    try {
                        io { Api.call("POST", "/orders/$orderId/lost", JSONObject().put("text", text.trim())) }
                        onClose(true)
                    } catch (e: Exception) { err = e.message ?: "Xato"; busy = false }
                }
            }) { Text("Yuborish") }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text("Orqaga") } },
    )
}

// ───────────────────────── Admin: pul yechish va tariflar ─────────────────────────

@Composable
fun AdminPayouts() {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val wd by rememberPoll("/admin/withdrawals?status=pending", tick)
    val cfg by rememberPoll("/admin/bonus", tick)
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("To'lovlar", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (msg.isNotEmpty()) ErrorText(msg)

        val list = wd?.optJSONArray("withdrawals")
        Text("Pul yechish so'rovlari", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (list == null || list.length() == 0) Panel { Text("Kutilayotgan so'rov yo'q", color = Muted) }
        else for (i in 0 until list.length()) {
            val w = list.getJSONObject(i)
            Panel {
                Text(w.optString("name").ifBlank { "Haydovchi" } + "  ·  " + w.optString("phone"), fontWeight = FontWeight.SemiBold)
                Text(som(w.optLong("amount")), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(w.optString("note"), fontSize = 13.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("To'landi", Modifier.weight(1f)) {
                        scope.launch {
                            try { io { Api.call("POST", "/admin/withdrawals/" + w.optInt("id"), JSONObject().put("action", "paid")) }; msg = ""; tick++ }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                    BigButton("Rad etish", Modifier.weight(1f)) {
                        scope.launch {
                            try { io { Api.call("POST", "/admin/withdrawals/" + w.optInt("id"), JSONObject().put("action", "reject").put("note", "Rad etildi")) }; msg = ""; tick++ }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                }
            }
        }

        val bonus = cfg?.optJSONObject("bonus")?.optJSONArray("tiers")
        val ic = cfg?.optJSONObject("intercity")
        if (bonus != null && ic != null) {
            var t1 by remember(cfg.toString()) { mutableStateOf(bonus.optJSONObject(0)?.optInt("trips")?.toString() ?: "") }
            var r1 by remember(cfg.toString()) { mutableStateOf(bonus.optJSONObject(0)?.optLong("reward")?.toString() ?: "") }
            var t2 by remember(cfg.toString()) { mutableStateOf(bonus.optJSONObject(1)?.optInt("trips")?.toString() ?: "") }
            var r2 by remember(cfg.toString()) { mutableStateOf(bonus.optJSONObject(1)?.optLong("reward")?.toString() ?: "") }
            var pk by remember(cfg.toString()) { mutableStateOf(ic.optInt("perKm").toString()) }
            var mn by remember(cfg.toString()) { mutableStateOf(ic.optInt("minimum").toString()) }
            val rf = cfg?.optJSONObject("referral")
            var rt by remember(cfg.toString()) { mutableStateOf(rf?.optInt("trips")?.toString() ?: "3") }
            var rr by remember(cfg.toString()) { mutableStateOf(rf?.optLong("reward")?.toString() ?: "10000") }
            Text("Bonus va shaharlararo tarif", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Panel {
                Text("1-bonus", fontSize = 13.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(t1, { t1 = it.filter(Char::isDigit) }, "Safar soni", Modifier.weight(1f), KeyboardType.Number)
                    Field(r1, { r1 = it.filter(Char::isDigit) }, "Mukofot", Modifier.weight(1f), KeyboardType.Number)
                }
                Text("2-bonus", fontSize = 13.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(t2, { t2 = it.filter(Char::isDigit) }, "Safar soni", Modifier.weight(1f), KeyboardType.Number)
                    Field(r2, { r2 = it.filter(Char::isDigit) }, "Mukofot", Modifier.weight(1f), KeyboardType.Number)
                }
                Text("Shaharlararo", fontSize = 13.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(pk, { pk = it.filter(Char::isDigit) }, "1 km narxi", Modifier.weight(1f), KeyboardType.Number)
                    Field(mn, { mn = it.filter(Char::isDigit) }, "Eng kam narx", Modifier.weight(1f), KeyboardType.Number)
                }
                Text("Taklif bonusi (do'st necha safar qilsa, qancha beriladi)", fontSize = 13.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(rt, { rt = it.filter(Char::isDigit) }, "Safar soni", Modifier.weight(1f), KeyboardType.Number)
                    Field(rr, { rr = it.filter(Char::isDigit) }, "Mukofot", Modifier.weight(1f), KeyboardType.Number)
                }
                BigButton("Saqlash") {
                    scope.launch {
                        try {
                            val tiers = org.json.JSONArray()
                            if (t1.isNotEmpty() && r1.isNotEmpty()) tiers.put(JSONObject().put("trips", t1.toInt()).put("reward", r1.toLong()))
                            if (t2.isNotEmpty() && r2.isNotEmpty()) tiers.put(JSONObject().put("trips", t2.toInt()).put("reward", r2.toLong()))
                            io {
                                Api.call("POST", "/admin/bonus", JSONObject().put("tiers", tiers)
                                    .put("intercity", JSONObject().put("perKm", pk.toInt()).put("minimum", mn.toInt()))
                                    .put("referral", JSONObject().put("trips", (rt.toIntOrNull() ?: 3)).put("reward", (rr.toLongOrNull() ?: 0L))))
                            }
                            msg = "Saqlandi"; tick++
                        } catch (e: Exception) { msg = e.message ?: "Xato" }
                    }
                }
            }
        }
        AdminPromos()
    }
}

/** Shaharlararo buyurtma bo'lsa: shahar, o'rinlar va vaqtni ko'rsatadi. */
@Composable
fun IntercityLine(o: JSONObject) {
    if (o.optString("kind") != "intercity") return
    val whenTxt = o.optString("when")
    Pill("🛣 Shaharlararo: ${o.optString("toCity")} · ${o.optInt("seats", 1)} o'rin" + if (whenTxt.isNotEmpty()) " · $whenTxt" else "",
        Color(0xFFE0EEFF), Color(0xFF1456A8))
}

// ───────────────────────── Shaharlararo buyurtma (yo'lovchi) ─────────────────────────

/** Manzil tanlash oynasi: viloyat markazlari va tumanlar, qidiruv bilan. */
@Composable
private fun PlacePicker(places: List<JSONObject>, withMyLocation: Boolean, title: String, onPick: (JSONObject?) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = PageBg, contentColor = Fg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Text("←", fontSize = 26.sp) }
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp))
                }
                Field(q, { q = it }, "Qidirish: shahar yoki tuman")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (withMyLocation && q.isBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Chip)
                                .clickable { onPick(null) }.padding(horizontal = 14.dp, vertical = 14.dp),
                        ) { Text("📍 Mening joylashuvim", fontWeight = FontWeight.Bold) }
                    }
                    places.filter {
                        q.isBlank() || it.optString("name").contains(q.trim(), ignoreCase = true) || it.optString("region").contains(q.trim(), ignoreCase = true)
                    }.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Chip)
                                .clickable { onPick(c) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.optString("name"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(c.optString("region"), fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntercityOrder(pickup: Pair<Double, Double>?, pickupName: String, pay: String, onMsg: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var places by remember { mutableStateOf(listOf<JSONObject>()) }
    var fromSel by remember { mutableStateOf<JSONObject?>(null) }   // null = mening joylashuvim
    var toSel by remember { mutableStateOf<JSONObject?>(null) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var seats by remember { mutableStateOf("1") }
    var whenTxt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        try { places = io { Api.call("GET", "/intercity/cities").getJSONArray("cities").items() } } catch (e: Exception) { onMsg(e.message ?: "Xato") }
    }
    val fromLat = fromSel?.optDouble("lat") ?: pickup?.first
    val fromLng = fromSel?.optDouble("lng") ?: pickup?.second
    val fromName = fromSel?.optString("name") ?: pickupName.ifBlank { "Xaritadagi nuqta" }
    LaunchedEffect(fromLat, fromLng, toSel) {
        quote = null
        val t = toSel
        if (fromLat != null && fromLng != null && t != null) {
            try { quote = io { Api.call("GET", "/intercity/quote?fromLat=$fromLat&fromLng=$fromLng&toLat=${t.optDouble("lat")}&toLng=${t.optDouble("lng")}") } }
            catch (e: Exception) { onMsg(e.message ?: "Xato") }
        }
    }
    if (pickFrom) PlacePicker(places, true, "Qayerdan?", { fromSel = it; pickFrom = false }) { pickFrom = false }
    if (pickTo) PlacePicker(places, false, "Qayerga?", { toSel = it; pickTo = false }) { pickTo = false }

    Text("Jo'nash joyi (xohlagan shahar yoki tuman)", fontSize = 13.sp, color = Muted)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Chip).clickable { pickFrom = true }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (fromSel == null) "📍 Mening joylashuvim" else fromName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("›", fontSize = 20.sp, color = Muted)
    }
    Text("Borish joyi (xohlagan shahar yoki tuman)", fontSize = 13.sp, color = Muted)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (toSel != null) Yellow else Chip).clickable { pickTo = true }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(toSel?.optString("name") ?: "Shahar yoki tumanni tanlang", fontWeight = FontWeight.SemiBold, color = if (toSel != null) OnBrand else Fg, modifier = Modifier.weight(1f))
        Text("›", fontSize = 20.sp, color = if (toSel != null) OnBrand else Muted)
    }
    quote?.let { Text("${it.optInt("km")} km  ·  " + som(it.optLong("price")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
    Text("Necha kishi", fontSize = 13.sp, color = Muted)
    Segmented(listOf("1" to "1", "2" to "2", "3" to "3", "4" to "4"), seats) { seats = it }
    Field(whenTxt, { whenTxt = it.take(40) }, "Qachon? (masalan: ertaga 08:00)")
    Text("Narx butun yo'l uchun. Haydovchi buyurtmani qabul qilgach siz bilan bog'lanadi.", fontSize = 12.sp, color = Muted)
    BigButton("Shaharlararo buyurtma", enabled = !busy && toSel != null && fromLat != null && fromLng != null && quote != null) {
        val t = toSel ?: return@BigButton
        val la = fromLat ?: return@BigButton
        val ln = fromLng ?: return@BigButton
        scope.launch {
            busy = true
            try {
                io {
                    Api.call("POST", "/orders", JSONObject()
                        .put("kind", "intercity").put("city", t.optString("key")).put("seats", seats.toInt()).put("when", whenTxt.trim())
                        .put("from", fromName).put("to", t.optString("name"))
                        .put("fromLat", la).put("fromLng", ln)
                        .put("toLat", t.optDouble("lat")).put("toLng", t.optDouble("lng"))
                        .put("payMethod", pay))
                }
                onMsg(""); toSel = null
            } catch (e: Exception) { onMsg(e.message ?: "Xato") }
            busy = false
        }
    }
}

/** Yandex Pro uslubidagi qisqa kartochka: bugungi buyurtmalar, daromad va keyingi bonus. */
@Composable
fun DayCard(count: Int, sum: Long, priority: Int = 0) {
    var next by remember { mutableStateOf<JSONObject?>(null) }
    var today by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val d = io { Api.call("GET", "/driver/bonus") }
                today = d.optInt("today")
                next = d.optJSONArray("tiers")?.items()?.firstOrNull { !it.optBoolean("done") }
            } catch (e: Exception) { }
            delay(15000)
        }
    }
    var showPts by remember { mutableStateOf(false) }
    if (showPts) androidx.compose.ui.window.Dialog(
        onDismissRequest = { showPts = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = PageBg, contentColor = Fg) { PointsScreen { showPts = false } } }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { showPts = true }) {
                Text("Ustuvorlik ›", fontSize = 12.sp, color = Muted)
                Text(signed(priority), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text("$count ta buyurtma", fontSize = 12.sp, color = Muted)
                Text(som(sum), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        next?.let { t ->
            val need = t.optInt("trips")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Chip).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Yana ${maxOf(0, need - today)} ta buyurtma", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("$today/$need · Bugun", fontSize = 12.sp, color = Muted)
                }
                Pill("+" + som(t.optLong("reward")), Color(0xFFDDE6FF), Color(0xFF2747C9))
            }
        }
    }
}

// ───────────────────────── Promokod (yo'lovchi) ─────────────────────────

@Composable
fun PromoBox(price: Long, onApplied: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val a = applied
    if (a != null) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE1F5E9)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🎟 ${a.optString("code")} qo'llandi", fontWeight = FontWeight.Bold, color = Color(0xFF14753F))
                Text("Chegirma −" + som(a.optLong("discount")) + "  →  " + som(a.optLong("price")), fontSize = 13.sp, color = Color(0xFF14753F))
            }
            TextButton(onClick = { applied = null; code = ""; onApplied("") }) { Text("Bekor", color = Color(0xFF14753F)) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(16) }, "Promokod", Modifier.weight(1f))
            TextButton(enabled = !busy && code.length >= 3, onClick = {
                busy = true; err = ""
                scope.launch {
                    try {
                        val r = io { Api.call("POST", "/promo/check", JSONObject().put("code", code).put("price", price)) }
                        applied = r; onApplied(r.optString("code"))
                    } catch (e: Exception) { err = e.message ?: "Xato" }
                    busy = false
                }
            }) { Text("Qo'llash", fontWeight = FontWeight.Bold) }
        }
        if (err.isNotEmpty()) ErrorText(err)
    }
}

// ───────────────────────── Taklif kodi (haydovchi) ─────────────────────────

@Composable
fun ReferralDialog(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { try { d = io { Api.call("GET", "/driver/referral") } } catch (e: Exception) { } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Do'stingizni taklif qiling") },
        text = {
            val j = d
            if (j == null) Text("Yuklanmoqda…", color = Muted)
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sizning kodingiz", fontSize = 12.sp, color = Muted)
                Text(j.optString("code"), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Text("Do'stingiz ro'yxatdan o'tishda shu kodni yozsin. U ${j.optInt("trips")} ta safar qilgach, sizga " + som(j.optLong("reward")) + " bonus tushadi.", fontSize = 13.sp, color = Muted)
                val inv = j.optJSONArray("invited")?.items() ?: emptyList()
                if (inv.isNotEmpty()) {
                    Text("Taklif qilganlaringiz", fontSize = 12.sp, color = Muted)
                    inv.forEach {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(it.optString("name"), modifier = Modifier.weight(1f))
                            Text(if (it.optBoolean("rewarded")) "✅ bonus berildi" else "${it.optInt("done")}/${j.optInt("trips")}", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val code = d?.optString("code").orEmpty()
                if (code.isNotEmpty()) {
                    val it = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "X taksi haydovchisi bo'ling! Ro'yxatdan o'tishda taklif kodini yozing: $code")
                    try { ctx.startActivity(android.content.Intent.createChooser(it, "Ulashish").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }
            }) { Text("Ulashish") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Orqaga") } },
    )
}

// ───────────────────────── Diagnostika (muddatlar) ─────────────────────────

private val DIAG = listOf("insurance" to "🛡 Sug'urta (OSAGO)", "inspection" to "🔧 Texnik ko'rik", "license" to "🪪 Haydovchilik guvohnomasi")

fun daysLeft(date: String): Int? = try {
    java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(date)).toInt()
} catch (e: Exception) { null }

/** Muddati yaqin (14 kun) yoki o'tgan hujjat bo'lsa, ogohlantirish matni. */
fun diagAlert(items: JSONObject?): String? {
    if (items == null) return null
    for ((k, label) in DIAG) {
        val left = daysLeft(items.optString(k)) ?: continue
        if (left < 0) return "⚠ " + label.drop(2).trim() + " muddati o'tgan"
        if (left <= 14) return "⚠ " + label.drop(2).trim() + ": $left kun qoldi"
    }
    return null
}

@Composable
fun DiagnosticsDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vals = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val items = io { Api.call("GET", "/driver/diagnostics") }.optJSONObject("items")
            DIAG.forEach { (k, _) -> vals[k] = items?.optString(k).orEmpty() }
        } catch (e: Exception) { }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Diagnostika") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Muddat tugash sanasini yozing (YYYY-OO-KK). Muddat yaqinlashsa, asosiy oynada eslatma chiqadi.", fontSize = 12.sp, color = Muted)
                DIAG.forEach { (k, label) ->
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Field(vals[k].orEmpty(), { vals[k] = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, "2027-01-31")
                    val left = daysLeft(vals[k].orEmpty())
                    if (left != null) {
                        val (bg, fg, t) = when {
                            left < 0 -> Triple(Color(0xFFFCE4E4), Color(0xFFA32626), "Muddati o'tgan")
                            left <= 30 -> Triple(Color(0xFFFFF1C2), Color(0xFF8A6100), "$left kun qoldi")
                            else -> Triple(Color(0xFFE1F5E9), Color(0xFF14753F), "$left kun qoldi")
                        }
                        Pill(t, bg, fg)
                    }
                }
                if (msg.isNotEmpty()) ErrorText(msg)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        val body = JSONObject()
                        DIAG.forEach { (k, _) -> body.put(k, vals[k].orEmpty()) }
                        io { Api.call("POST", "/driver/diagnostics", body) }
                        onClose()
                    } catch (e: Exception) { msg = e.message ?: "Xato" }
                }
            }) { Text("Saqlash") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Orqaga") } },
    )
}

// ───────────────────────── Admin: promokodlar ─────────────────────────

@Composable
fun AdminPromos() {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val data by rememberPoll("/admin/promos", tick)
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("percent") }
    var value by remember { mutableStateOf("") }
    var maxUses by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Text("Promokodlar", fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Panel {
        Field(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(16) }, "Kod (masalan X10)")
        Segmented(listOf("percent" to "% foiz", "amount" to "So'm"), type) { type = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(value, { value = it.filter(Char::isDigit) }, if (type == "percent") "Foiz (1-50)" else "Summa", Modifier.weight(1f), KeyboardType.Number)
            Field(maxUses, { maxUses = it.filter(Char::isDigit) }, "Necha marta", Modifier.weight(1f), KeyboardType.Number)
            Field(days, { days = it.filter(Char::isDigit) }, "Necha kun", Modifier.weight(1f), KeyboardType.Number)
        }
        if (msg.isNotEmpty()) ErrorText(msg)
        BigButton("Promokod yaratish") {
            scope.launch {
                try {
                    io {
                        Api.call("POST", "/admin/promos", JSONObject().put("code", code).put("type", type)
                            .put("value", value.toLongOrNull() ?: 0).put("maxUses", maxUses.toLongOrNull() ?: 0).put("days", days.toLongOrNull() ?: 0))
                    }
                    code = ""; value = ""; maxUses = ""; days = ""; msg = ""; tick++
                } catch (e: Exception) { msg = e.message ?: "Xato" }
            }
        }
    }
    data?.optJSONArray("promos")?.items()?.forEach { p ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.optString("code"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text((if (p.optString("type") == "percent") "${p.optInt("value")}%" else som(p.optLong("value"))) +
                        "  ·  ishlatilgan ${p.optInt("used")}" + if (p.optInt("maxUses") > 0) "/${p.optInt("maxUses")}" else "", fontSize = 12.sp, color = Muted)
                }
                val on = p.optBoolean("active")
                TextButton(onClick = {
                    scope.launch {
                        try { io { Api.call("POST", "/admin/promos/" + p.optString("code"), JSONObject().put("active", !on)) }; tick++ } catch (e: Exception) { }
                    }
                }) { Text(if (on) "O'chirish" else "Yoqish") }
            }
        }
    }
}


/** Haydovchi uchun: reyting, bugungi buyurtmalar va keyingi bonusgacha qolgan buyurtmalar. */
@Composable
fun ProgressCard(stats: JSONObject?) {
    var bonus by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try { bonus = io { Api.call("GET", "/driver/bonus") } } catch (e: Exception) { }
            delay(15000)
        }
    }
    val rating = stats?.optDouble("rating") ?: 0.0
    val rc = stats?.optInt("ratingCount") ?: 0
    val today = bonus?.optInt("today") ?: (stats?.optInt("todayCount") ?: 0)
    val tiers = bonus?.optJSONArray("tiers")?.items() ?: emptyList()
    val next = tiers.firstOrNull { !it.optBoolean("done") }
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (rc > 0) String.format("★ %.2f", rating) else "★ —", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Reyting", fontSize = 12.sp, color = Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("$today ta", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Bugungi buyurtma", fontSize = 12.sp, color = Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("${stats?.optInt("totalCount") ?: 0} ta", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Jami safar", fontSize = 12.sp, color = Muted)
            }
        }
        if (next != null) {
            val need = next.optInt("trips")
            val left = maxOf(0, need - today)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎁 Yana $left ta buyurtma — bonus", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("+" + som(next.optLong("reward")), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Gold)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Chip)) {
                    Box(Modifier.fillMaxWidth((today.toFloat() / need).coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(50)).background(GoldBrush))
                }
                Text("$today/$need · bugun", fontSize = 12.sp, color = Muted)
            }
        } else if (tiers.isNotEmpty()) {
            Text("🎉 Bugungi barcha bonuslar olindi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
