package uz.taksi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Admin "To'lov" bo'limi: Daromad / To'ldirish / Yechish / Sozlamalar. */
@Composable
fun AdminPayments() {
    var sec by remember { mutableStateOf("rev") }
    Column(Modifier.fillMaxSize()) {
        Text("Moliya", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp))
        Segmented(
            listOf("rev" to "Daromad", "top" to "To'ldirish", "pay" to "Yechish", "set" to "Sozlama"),
            sec, Modifier.padding(16.dp),
        ) { sec = it }
        Box(Modifier.weight(1f)) {
            when (sec) {
                "rev" -> AdminRevenue()
                "top" -> AdminTopups()
                "pay" -> AdminPayouts()
                else -> AdminSettings()
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(tint).padding(14.dp)) {
        Text(label, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

@Composable
private fun AdminRevenue() {
    val d by rememberPoll("/admin/revenue")
    val r = d
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (r == null) { Text("Yuklanmoqda…", color = Muted); return@Column }
        val pct = r.optInt("percent")
        val today = r.optJSONObject("today")
        val total = r.optJSONObject("total")
        val days = r.optJSONArray("days")?.items() ?: emptyList()
        Text("Admin daromadi (komissiya $pct%)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Bugun", som(today?.optLong("commission") ?: 0L), Color(0xFFFFE9A8), Modifier.weight(1f))
            Stat("7 kun", som(days.sumOf { it.optLong("commission") }), Color(0xFFFFE9A8), Modifier.weight(1f))
            Stat("Jami", som(total?.optLong("commission") ?: 0L), Color(0xFFFFD36B), Modifier.weight(1f))
        }
        Text("Haydovchilar daromadi (komissiyasiz)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        fun net(o: JSONObject?) = (o?.optLong("gross") ?: 0L) - (o?.optLong("commission") ?: 0L)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Bugun", som(net(today)), Color(0xFFD9F5DF), Modifier.weight(1f))
            Stat("7 kun", som(days.sumOf { net(it) }), Color(0xFFD9F5DF), Modifier.weight(1f))
            Stat("Jami", som(net(total)), Color(0xFFBFE9C9), Modifier.weight(1f))
        }
        Panel {
            Text("Aylanma (safar narxlari)", fontSize = 13.sp, color = Muted)
            Text(som(total?.optLong("gross") ?: 0L) + "  ·  " + (total?.optInt("trips") ?: 0) + " safar", fontWeight = FontWeight.Bold)
        }
        Text("Oxirgi 7 kun", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Panel {
            days.reversed().forEach { x ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(x.optString("date").takeLast(5), Modifier.weight(1f), fontSize = 14.sp)
                    Text(x.optInt("trips").toString() + " safar", Modifier.weight(1f), fontSize = 13.sp, color = Muted)
                    Text(som(x.optLong("commission")), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
        Text("Hisob-kitob", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Panel {
            Line("Haydovchilar hisoblari yig'indisi", som(r.optLong("driverBalances")))
            Line("To'ldirilgan (jami)", som(r.optLong("topups")))
            Line("Yechib olingan (jami)", som(r.optLong("withdrawn")))
            Line("Berilgan bonuslar (jami)", som(r.optLong("bonusesGiven")))
            Line("Hisobi kam (liniyadan chiqarilgan)", r.optInt("lowBalance").toString() + " ta")
        }
        Spacer16()
    }
}

@Composable
private fun Line(a: String, b: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(a, Modifier.weight(1f), fontSize = 14.sp, color = Muted)
        Text(b, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Spacer16() = androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

@Composable
private fun AdminTopups() {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val d by rememberPoll("/admin/topups?status=pending", tick)
    var msg by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+998") }
    var amount by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Naqd pul qabul qilish (ofis)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Panel {
            Field(phone, { phone = it.take(13) }, "Haydovchi telefoni", keyboard = KeyboardType.Phone)
            Field(amount, { amount = it.filter(Char::isDigit).take(8) }, "Summa (so'm)", keyboard = KeyboardType.Number)
            BigButton("Hisobga qo'shish", enabled = phone.length >= 12 && (amount.toLongOrNull() ?: 0L) > 0) {
                scope.launch {
                    try {
                        io { Api.call("POST", "/admin/credit", JSONObject().put("phone", phone).put("amount", amount.toLong()).put("note", "Ofisda naqd to'ldirildi")) }
                        ok = "✓ " + som(amount.toLong()) + " qo'shildi"; msg = ""; amount = ""
                    } catch (e: Exception) { msg = e.message ?: "Xato"; ok = "" }
                }
            }
            if (ok.isNotEmpty()) Text(ok, color = Ok, fontWeight = FontWeight.SemiBold)
        }
        if (msg.isNotEmpty()) ErrorText(msg)
        Text("To'ldirish so'rovlari", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        val list = d?.optJSONArray("topups")?.items() ?: emptyList()
        if (list.isEmpty()) Panel { Text("Kutilayotgan so'rov yo'q", color = Muted) }
        list.forEach { t ->
            Panel {
                Text(t.optString("name").ifBlank { "Haydovchi" } + "  ·  " + t.optString("phone"), fontWeight = FontWeight.SemiBold)
                Text(som(t.optLong("amount")), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(t.optString("method") + "  ·  kod: " + t.optString("code"), fontSize = 13.sp, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("Tasdiqlash", Modifier.weight(1f)) {
                        scope.launch {
                            try { io { Api.call("POST", "/admin/topups/" + t.optInt("id"), JSONObject().put("action", "confirm")) }; msg = ""; tick++ }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                    BigButton("Rad etish", Modifier.weight(1f)) {
                        scope.launch {
                            try { io { Api.call("POST", "/admin/topups/" + t.optInt("id"), JSONObject().put("action", "reject")) }; msg = ""; tick++ }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                }
            }
        }
        Spacer16()
    }
}

@Composable
private fun AdminSettings() {
    val scope = rememberCoroutineScope()
    val d by rememberPoll("/admin/settings")
    val cfg = d
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (cfg == null) { Text("Yuklanmoqda…", color = Muted); return@Column }
        val key = cfg.toString()
        val rule = cfg.optJSONObject("balRule")
        val tc = cfg.optJSONObject("topupCfg")
        var comm by remember(key) { mutableStateOf(cfg.optDouble("commission", 10.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }) }
        var welcome by remember(key) { mutableStateOf(cfg.optLong("welcome").toString()) }
        var warn by remember(key) { mutableStateOf(rule?.optLong("warn", 5000)?.toString() ?: "5000") }
        var resume by remember(key) { mutableStateOf(rule?.optLong("resume", 10000)?.toString() ?: "10000") }
        var card by remember(key) { mutableStateOf(tc?.optString("card") ?: "") }
        var holder by remember(key) { mutableStateOf(tc?.optString("holder") ?: "") }
        var click by remember(key) { mutableStateOf(tc?.optString("click") ?: "") }
        var payme by remember(key) { mutableStateOf(tc?.optString("payme") ?: "") }
        var paynet by remember(key) { mutableStateOf(tc?.optString("paynet") ?: "") }
        var uzum by remember(key) { mutableStateOf(tc?.optString("uzum") ?: "") }
        val offs = tc?.optJSONArray("offices")?.items() ?: emptyList()
        var office by remember(key) { mutableStateOf(offs.joinToString("\n") { it.optString("name") + " | " + it.optString("address") + " | " + it.optString("phone") }) }

        Text("Komissiya va qoidalar", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Panel {
            Field(comm, { comm = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, "Komissiya, % (har safardan)", keyboard = KeyboardType.Decimal)
            Field(welcome, { welcome = it.filter(Char::isDigit).take(7) }, "Yangi haydovchiga bonus (so'm)", keyboard = KeyboardType.Number)
            Field(warn, { warn = it.filter(Char::isDigit).take(7) }, "Ogohlantirish va liniyadan chiqarish chegarasi", keyboard = KeyboardType.Number)
            Field(resume, { resume = it.filter(Char::isDigit).take(7) }, "Qayta ishlash uchun balans (dan oshsa)", keyboard = KeyboardType.Number)
        }
        Text("To'lov rekvizitlari", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Panel {
            Field(card, { card = it }, "Karta raqami")
            Field(holder, { holder = it }, "Karta egasi")
            Field(click, { click = it }, "Click (raqam/havola)")
            Field(payme, { payme = it }, "Payme (raqam/havola)")
            Field(paynet, { paynet = it }, "Paynet (raqam/havola)")
            Field(uzum, { uzum = it }, "Uzum Bank (raqam/havola)")
            androidx.compose.material3.OutlinedTextField(
                value = office, onValueChange = { office = it.take(1500) },
                label = { Text("Ofislar (har qatorda: nom | manzil | telefon)") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
                shape = RoundedCornerShape(16.dp),
            )
        }
        ErrorText(msg)
        BigButton("Saqlash") {
            scope.launch {
                try {
                    val arr = JSONArray()
                    office.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { ln ->
                        val p = ln.split("|").map { it.trim() }
                        arr.put(JSONObject().put("name", p.getOrElse(0) { "" }).put("address", p.getOrElse(1) { "" }).put("phone", p.getOrElse(2) { "" }))
                    }
                    io {
                        Api.call("POST", "/admin/settings", JSONObject()
                            .put("commission", comm.toDoubleOrNull() ?: 10.0)
                            .put("welcome", welcome.toLongOrNull() ?: 0L)
                            .put("balRule", JSONObject().put("enabled", true).put("warn", warn.toLongOrNull() ?: 5000L).put("resume", resume.toLongOrNull() ?: 10000L))
                            .put("topupCfg", JSONObject().put("card", card).put("holder", holder).put("click", click).put("payme", payme)
                                .put("paynet", paynet).put("uzum", uzum).put("offices", arr)))
                    }
                    msg = ""
                } catch (e: Exception) { msg = e.message ?: "Xato" }
            }
        }

        // Yangilik yuborish
        var nt by remember { mutableStateOf("") }
        var nb by remember { mutableStateOf("") }
        var nmsg by remember { mutableStateOf("") }
        Text("Haydovchilarga yangilik", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
        Panel {
            Field(nt, { nt = it.take(60) }, "Sarlavha")
            Field(nb, { nb = it.take(600) }, "Matn")
            if (nmsg.isNotEmpty()) Text(nmsg, color = if (nmsg.startsWith("✓")) Ok else Bad, fontSize = 13.sp)
            BigButton("Yuborish", enabled = nt.trim().length >= 3 && nb.trim().length >= 3) {
                scope.launch {
                    try {
                        io { Api.call("POST", "/admin/news", JSONObject().put("title", nt.trim()).put("text", nb.trim())) }
                        nt = ""; nb = ""; nmsg = "✓ Yuborildi"
                    } catch (e: Exception) { nmsg = e.message ?: "Xato" }
                }
            }
        }
        Spacer16()
    }
}
