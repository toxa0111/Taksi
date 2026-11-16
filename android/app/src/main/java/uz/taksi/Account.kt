package uz.taksi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────────────────── Umumiy ─────────────────────────

/** Mening buyurtmalarim (mavjud /orders/mine). Birinchi yuklanguncha null. */
@Composable
fun rememberMyOrders(): State<List<JSONObject>?> {
    val s = remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val n = io { Api.call("GET", "/orders/mine").getJSONArray("orders").items() }
                val cur = s.value
                if (cur == null || !n.sameAs(cur)) s.value = n
            } catch (e: Exception) { }
            delay(8000)
        }
    }
    return s
}

private fun dateText(o: JSONObject): String {
    val t = o.optLong("doneAt", o.optLong("createdAt"))
    return SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault()).format(Date(t))
}

private fun sumPrice(l: List<JSONObject>): Long = l.sumOf { it.optLong("price") }

@Composable
fun TabTitle(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
fun LightStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = Muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── Pul ─────────────────────────

@Composable
fun MoneyTab(role: String) {
    val scope = rememberCoroutineScope()
    val orders by rememberMyOrders()
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    val isDriver = role == "driver"

    if (isDriver) {
        LaunchedEffect(Unit) {
            while (true) {
                try { stats = io { Api.call("GET", "/stats") } } catch (e: Exception) { }
                delay(8000)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Pul")
        val list = orders
        if (list == null) {
            Text("Yuklanmoqda…", color = Muted, modifier = Modifier.padding(4.dp))
        } else {
            val done = list.filter { it.optString("status") == "done" }
            val cash = done.filter { it.optString("payMethod") == "cash" }
            val card = done.filter { it.optString("payMethod") == "card" }

            if (isDriver) {
                val s = stats
                Panel(color = Ink) {
                    Text("Bugungi daromad", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Text(som(s?.optLong("todaySum") ?: 0L), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatItem("Safarlar", "${s?.optInt("todayCount") ?: 0} ta", Modifier.weight(1f))
                        StatItem("Naqd", som(s?.optLong("todayCash") ?: 0L), Modifier.weight(1f))
                        StatItem("Karta", som(s?.optLong("todayCard") ?: 0L), Modifier.weight(1f))
                    }
                }
                Panel {
                    Text("Jami daromad", fontSize = 12.sp, color = Muted)
                    Text(som(s?.optLong("totalSum") ?: 0L), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${s?.optInt("totalCount") ?: 0} ta safar", fontSize = 13.sp, color = Muted)
                    Text("Hozir barcha to'lovlar naqd: pulni yo'lovchidan o'zingiz olasiz.", fontSize = 12.sp, color = Muted)
                }
            } else {
                Panel(color = Ink) {
                    Text("Jami sarflangan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Text(som(sumPrice(done)), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatItem("Safarlar", "${done.size} ta", Modifier.weight(1f))
                        StatItem("Naqd", som(sumPrice(cash)), Modifier.weight(1f))
                        StatItem("Karta", som(sumPrice(card)), Modifier.weight(1f))
                    }
                }
                val unpaid = done.filter { it.optString("payMethod") == "card" && it.optString("payStatus") != "paid" }
                unpaid.forEach { o ->
                    Panel(color = Soft) {
                        Text("To'lanmagan safar", fontWeight = FontWeight.Bold)
                        Text(o.optString("to"), fontSize = 13.sp, color = Muted, maxLines = 1)
                        BigButton("💳 ${som(o.optLong("price"))} to'lash") {
                            scope.launch {
                                try { io { Api.call("POST", "/orders/${o.optInt("id")}/pay", JSONObject()) }; msg = "" }
                                catch (e: Exception) { msg = e.message ?: "Xato" }
                            }
                        }
                    }
                }
                Text("Hozir to'lov naqd: haydovchiga beriladi. Karta to'lovi tez orada.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
            }

            ErrorText(msg)

            val hist = list.filter { it.optString("status") in setOf("done", "cancelled") }
            Text("Safarlar tarixi", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            if (hist.isEmpty()) {
                Panel { Text("Hali safarlar yo'q", color = Muted) }
            }
            hist.forEach { o ->
                val cancelled = o.optString("status") == "cancelled"
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dateText(o), fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                        if (cancelled) Pill("Bekor qilingan", Color(0xFFFCE4E4), Color(0xFFA32626))
                        else Text(som(o.optLong("price")), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Route(o.optString("from"), o.optString("to"))
                    if (!cancelled) Text(String.format("%.1f km  ·  %s", o.optDouble("km"), payText(o)), fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ───────────────────────── Shaxsiy kabinet ─────────────────────────

@Composable
fun CabinetTab(role: String, prefs: SharedPreferences, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val orders by rememberMyOrders()
    var info by remember { mutableStateOf("") }
    var installUrl by remember { mutableStateOf<String?>(null) }
    installUrl?.let { InstallDialog(it, "TOXA") { installUrl = null } }
    val isDriver = role == "driver"

    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        try { profile = io { Api.call("GET", "/profile") } } catch (e: Exception) { }
        if (isDriver) { try { stats = io { Api.call("GET", "/stats") } } catch (e: Exception) { } }
    }
    val name = (profile?.optString("name") ?: "").ifBlank { prefs.getString("name", "") ?: "" }
    val phone = (profile?.optString("phone") ?: "").ifBlank { prefs.getString("phone", "") ?: "" }
    val done = orders?.filter { it.optString("status") == "done" } ?: emptyList()
    val cancelled = orders?.count { it.optString("status") == "cancelled" } ?: 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Kabinet")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase().ifBlank { "👤" }, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name.ifBlank { "Foydalanuvchi" }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(phone.ifBlank { "Telefon ko'rsatilmagan" }, fontSize = 14.sp, color = Muted)
                    Pill(if (isDriver) "🚖 Haydovchi" else "🧍 Yo'lovchi", Color(0xFFFFF1C2), Color(0xFF8A6100))
                }
            }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightStat("Safarlar", "${done.size} ta", Modifier.weight(1f))
                LightStat(if (isDriver) "Daromad" else "Sarflangan", som(sumPrice(done)), Modifier.weight(1.4f))
                if (isDriver) {
                    val rc = stats?.optInt("ratingCount") ?: 0
                    LightStat("Reyting", if (rc > 0) String.format("%.1f ★", stats?.optDouble("rating") ?: 0.0) else "—", Modifier.weight(1f))
                } else {
                    LightStat("Bekor", "$cancelled ta", Modifier.weight(1f))
                }
            }
            Text("So'nggi 30 ta buyurtma bo'yicha", fontSize = 11.sp, color = Muted)
        }

        if (isDriver) DriverDocsPanel()
        AppPrefsPanel()
        Panel {
            Text("Ilova", fontWeight = FontWeight.Bold)
            Text("Versiya: ${versionName(ctx)}", fontSize = 13.sp, color = Muted)
            GhostButton("Yangilanishni tekshirish") {
                scope.launch {
                    info = "Tekshirilmoqda…"
                    try {
                        val r = latestRelease()
                        if (r != null && r.first > currentVersionCode(ctx)) {
                            info = "Yangi versiya bor"
                            installUrl = r.second
                        } else info = "Yangi versiya topilmadi"
                    } catch (e: Exception) {
                        info = "Yangilanish tekshirilmadi: ${e.message ?: "internet yoki server xatosi"}"
                    }
                }
            }
            if (info.isNotEmpty()) Text(info, fontSize = 12.sp, color = Muted)
            GhostButton("Joylashuv ruxsatlari") {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { }
            }
        }
        DangerButton("Hisobdan chiqish", onClick = onLogout)
    }
}

private fun versionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
} catch (e: Exception) { "-" }

// ───────────────────────── Qo'llab-quvvatlash ─────────────────────────

@Composable
private fun FaqItem(q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
            .clickable { open = !open }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", fontSize = 20.sp, color = Muted)
        }
        if (open) Text(a, fontSize = 14.sp, color = Muted)
    }
}

fun dateOf(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
fun SupportTab(role: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDriver = role == "driver"
    var text by remember { mutableStateOf("") }
    var tickets by remember { mutableStateOf<List<JSONObject>?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        try { tickets = io { Api.call("GET", "/support/mine").getJSONArray("tickets").items() } } catch (e: Exception) { }
    }
    LaunchedEffect(Unit) {
        while (true) { refresh(); delay(8000) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Yordam")
        Panel {
            Text("Savol yoki muammo yozing", fontWeight = FontWeight.Bold)
            Text("Xabaringiz qo'llab-quvvatlash xizmatiga boradi, javob shu yerda chiqadi.", fontSize = 13.sp, color = Muted)
            Field(text, { text = it }, "Xabaringiz")
            BigButton("Yuborish", enabled = !busy && text.trim().length >= 3) {
                scope.launch {
                    busy = true
                    try {
                        io { Api.call("POST", "/support", JSONObject().put("text", text.trim())) }
                        text = ""; msg = ""
                        refresh()
                    } catch (e: Exception) { msg = e.message ?: "Xato" }
                    busy = false
                }
            }
            ErrorText(msg)
            if (SUPPORT_PHONE.isNotBlank()) {
                GhostButton("📞 $SUPPORT_PHONE") {
                    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))) } catch (e: Exception) { }
                }
            }
            if (SUPPORT_TELEGRAM.isNotBlank()) {
                GhostButton("✈️ Telegram: @$SUPPORT_TELEGRAM") {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$SUPPORT_TELEGRAM"))) } catch (e: Exception) { }
                }
            }
        }

        val tl = tickets
        if (tl != null && tl.isNotEmpty()) {
            Text("Murojaatlarim", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            tl.forEach { t ->
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dateOf(t.optLong("createdAt")), fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f))
                        if (t.has("reply")) Pill("Javob keldi", Color(0xFFE1F5E9), Color(0xFF14753F))
                        else Pill("Javob kutilmoqda", Color(0xFFFFF1C2), Color(0xFF8A6100))
                    }
                    Text(t.optString("text"), fontSize = 14.sp)
                    if (t.has("reply")) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Chip).padding(12.dp)) {
                            Text("Javob: " + t.optString("reply"), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Text("Ko'p so'raladigan savollar", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        FaqItem(
            "SMS kod kelmasa nima qilaman?",
            "Kod kelmasa, 1 daqiqadan keyin qayta so'rang. Server uxlab qolgan bo'lsa, birinchi so'rov 30–60 soniya olishi mumkin.",
        )
        FaqItem(
            "Narx qanday hisoblanadi?",
            "Minimal narx 8 000 so'm. Narx 5 000 so'm + har kilometr uchun 2 500 so'm, 500 so'mgacha yaxlitlanadi. Kilometr yo'l bo'yicha hisoblanadi.",
        )
        if (!isDriver) {
            FaqItem(
                "Buyurtmani qanday bekor qilaman?",
                "Haydovchi qidirilayotganda yoki yo'lda bo'lsa, safar ekranida “Buyurtmani bekor qilish” tugmasi bor. Safar boshlangach bekor qilib bo'lmaydi.",
            )
            FaqItem(
                "Qanday to'layman?",
                "Hozir to'lov naqd: safar tugagach pulni haydovchiga berasiz. Karta to'lovi tez orada ulanadi.",
            )
            FaqItem(
                "Manzilni qanday tanlayman?",
                "“Qayerga” qatorini bosing va yozing, yoki “Xaritadan tanlash” ni bosib xaritaga tegining. Oldingi manzillaringiz ham taklif qilinadi.",
            )
            FaqItem(
                "Haydovchini qanday baholayman?",
                "Safar tugagach Safar bo'limida yulduzchalar chiqadi. Baho haydovchi reytingiga qo'shiladi.",
            )
        } else {
            FaqItem(
                "Navigatsiya qanday ishlaydi?",
                "Buyurtmani qabul qilsangiz, xaritada marshrut va burilish ko'rsatmalari ilovaning o'zida chiqadi. Boshqa ilova o'rnatish shart emas. Ilovadan chiqib ketsangiz ham buyurtma saqlanib turadi.",
            )
            FaqItem(
                "Buyurtma qanday keladi?",
                "Safar bo'limida “Ishga chiqish” tugmasini suring. Onlayn bo'lsangiz, yangi buyurtma tushganda telefon tebranadi va ovoz chiqadi.",
            )
            FaqItem(
                "Naqd to'lovda nima qilaman?",
                "Pulni yo'lovchidan o'zingiz olasiz va “Yakunlash” ni bosasiz.",
            )
            FaqItem(
                "Buyurtmani rad qilsam nima bo'ladi?",
                "Yo'lovchiga yetib kelguncha rad etishingiz mumkin: buyurtma boshqa haydovchilarga qayta ko'rsatiladi.",
            )
        }
        FaqItem(
            "Xarita yoki joylashuv ishlamayapti",
            "Telefonda GPS ni yoqing va ilovaga joylashuv ruxsatini bering (Kabinet → Joylashuv ruxsatlari). Internet aloqasini ham tekshiring.",
        )
        Spacer(Modifier.height(8.dp))
    }
}
