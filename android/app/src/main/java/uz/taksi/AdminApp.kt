package uz.taksi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

// ───────────────────────── Kirish va qobiq ─────────────────────────

/** TOXA Admin: alohida ilova (uz.taksi.admin). Faqat serverda ADMIN_PHONES ro'yxatidagi raqamlar kira oladi. */
@Composable
fun AdminApp(prefs: SharedPreferences) {
    var role by remember { mutableStateOf(prefs.getString("role", "") ?: "") }
    val logout = {
        prefs.edit().remove("token").remove("role").remove("name").remove("phone").apply()
        Api.token = ""
        role = ""
    }
    androidx.compose.runtime.SideEffect { Api.onUnauthorized = { android.os.Handler(android.os.Looper.getMainLooper()).post { if (role.isNotEmpty()) logout() } } }
    UpdateDialog(tag = "toxa-admin", appName = "TOXA Admin")
    if (role == "admin") AdminShell(prefs, logout) else AdminLogin(prefs) { role = it }
}

@Composable
fun AdminLogin(prefs: SharedPreferences, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("+998") }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().appBackground().verticalScroll(rememberScrollState())) {
        LangBar()
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Yellow)
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Column {
                Image(
                    painter = painterResource(R.drawable.toxa_logo),
                    contentDescription = "TOXA",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text("TOXA ADMIN", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = OnBrand, letterSpacing = 3.sp)
                Text("Boshqaruv paneli", fontSize = 15.sp, color = OnBrand.copy(alpha = 0.85f))
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel {
                Text(if (sent) "Tasdiqlash kodi" else "Admin kirishi", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Field(phone, { phone = it }, "Telefon raqam", keyboard = KeyboardType.Phone)
                Field(name, { name = it }, "Ismingiz (birinchi marta kirganda)")
                if (!sent) {
                    BigButton("Kod olish", enabled = !busy) {
                        scope.launch {
                            busy = true; msg = ""
                            try {
                                val r = io { Api.call("POST", "/auth/request", JSONObject().put("phone", phone.trim())) }
                                sent = true
                                devCode = if (r.has("devCode")) r.optString("devCode") else ""
                            } catch (e: Exception) { msg = e.message ?: "Xato" }
                            busy = false
                        }
                    }
                } else {
                    if (devCode.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Soft).padding(12.dp)) {
                            Text("Test kodi: $devCode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Field(code, { code = it }, "SMS kod", keyboard = KeyboardType.Number)
                    BigButton("Kirish", enabled = !busy) {
                        scope.launch {
                            busy = true
                            try {
                                val body = JSONObject()
                                    .put("phone", phone.trim()).put("code", code.trim())
                                    .put("name", name.trim()).put("admin", true)
                                val r = io { Api.call("POST", "/auth/verify", body) }
                                val u = r.getJSONObject("user")
                                if (u.getString("role") != "admin") throw Exception("Bu hisob admin emas")
                                Api.token = r.getString("token")
                                prefs.edit()
                                    .putString("token", Api.token).putString("role", "admin")
                                    .putString("base", Api.base)
                                    .putString("name", u.optString("name")).putString("phone", u.optString("phone")).apply()
                                onDone("admin")
                            } catch (e: Exception) { msg = e.message ?: "Xato" }
                            busy = false
                        }
                    }
                    TextButton(onClick = { sent = false; code = ""; msg = ""; devCode = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Raqamni o'zgartirish", color = Muted)
                    }
                }
                if (busy) Text("Server javob kutilmoqda… (birinchi marta 30–60 soniya ketishi mumkin)", fontSize = 12.sp, color = Muted)
                ErrorText(msg)
            }
        }
    }
}

private val ADMIN_TABS = listOf("📊" to "Umumiy", "🚕" to "Buyurtma", "👥" to "Odamlar", "💬" to "Murojaat", "🪪" to "Hujjat", "💳" to "To'lov", "⚙" to "Profil")

@Composable
fun AdminShell(prefs: SharedPreferences, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().appBackground()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> AdminOverview()
                1 -> AdminOrders()
                2 -> AdminUsers()
                3 -> AdminTickets()
                4 -> AdminDocs()
                5 -> AdminPayments()
                else -> AdminProfile(prefs, onLogout)
            }
        }
        BottomTabs(ADMIN_TABS, tab) { tab = it }
    }
}

// ───────────────────────── Umumiy yordamchilar ─────────────────────────

/** Admin API'dan har 5 soniyada ma'lumot oladi. path yoki tick o'zgarsa darhol qayta yuklaydi. */
@Composable
fun rememberPoll(path: String, tick: Int = 0): State<JSONObject?> {
    val s = remember(path) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(path, tick) {
        while (true) {
            try {
                val j = io { Api.call("GET", path) }
                if (j.toString() != s.value?.toString()) s.value = j
            } catch (e: Exception) { }
            delay(5000)
        }
    }
    return s
}

@Composable
private fun PhoneLine(label: String, name: String, phone: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (name.isBlank()) label else "$label: $name", fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (phone.isNotBlank()) {
            TextButton(onClick = {
                try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (e: Exception) { }
            }) { Text("📞 $phone", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Confirm(title: String, text: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        shape = RoundedCornerShape(24.dp),
        containerColor = Card,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(yes, color = Bad, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Yo'q", color = Muted) } },
    )
}

private val ORDER_FILTERS = listOf("active" to "Faol", "done" to "Tugagan", "cancelled" to "Bekor", "all" to "Hammasi")
private val STATUS_LABEL = mapOf(
    "searching" to "Qidirilmoqda", "accepted" to "Qabul qilingan", "arrived" to "Yetib kelgan",
    "started" to "Safarda", "done" to "Yakunlangan", "cancelled" to "Bekor qilingan",
)

// ───────────────────────── Umumiy ko'rsatkichlar ─────────────────────────

@Composable
fun AdminOverview() {
    val ov by rememberPoll("/admin/overview")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Umumiy")
        val d = ov
        if (d == null) {
            Text("Yuklanmoqda…", color = Muted, modifier = Modifier.padding(4.dp))
        } else {
            val today = d.getJSONObject("today")
            val total = d.getJSONObject("total")
            val users = d.getJSONObject("users")
            val orders = d.getJSONObject("orders")
            Panel(color = Ink) {
                Text("Bugungi tushum", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Text(som(today.optLong("sum")), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatItem("Safarlar", "${today.optInt("count")} ta", Modifier.weight(1f))
                    StatItem("Naqd", som(today.optLong("cash")), Modifier.weight(1f))
                    StatItem("Karta", som(today.optLong("card")), Modifier.weight(1f))
                }
                Text(
                    "Jami: ${total.optInt("count")} ta safar · ${som(total.optLong("sum"))}",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                )
            }
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LightStat("Faol buyurtmalar", "${orders.optInt("active")}", Modifier.weight(1f))
                    LightStat("Onlayn haydovchi", "${users.optInt("onlineDrivers")} / ${users.optInt("drivers")}", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LightStat("Yo'lovchilar", "${users.optInt("passengers")}", Modifier.weight(1f))
                    LightStat("Bloklangan", "${users.optInt("blocked")}", Modifier.weight(1f))
                }
            }
            Panel {
                Text("Buyurtmalar holati", fontWeight = FontWeight.Bold)
                val by = orders.optJSONObject("byStatus")
                STATUS_LABEL.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
                        Text("${by?.optInt(k) ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Jami: ${orders.optInt("total")} ta", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

// ───────────────────────── Buyurtmalar ─────────────────────────

@Composable
fun AdminOrders() {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("active") }
    var tick by remember { mutableStateOf(0) }
    var cancelId by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val path = if (filter == "all") "/admin/orders?limit=50" else "/admin/orders?status=$filter&limit=50"
    val data by rememberPoll(path, tick)

    if (cancelId != 0) {
        Confirm(
            "Buyurtma #$cancelId", "Bu buyurtmani bekor qilasizmi? Yo'lovchi va haydovchiga bekor qilingani ko'rinadi.",
            "Ha, bekor qilish",
            onYes = {
                val id = cancelId
                cancelId = 0
                scope.launch {
                    try { io { Api.call("POST", "/admin/orders/$id/cancel", JSONObject()) }; msg = ""; tick++ }
                    catch (e: Exception) { msg = e.message ?: "Xato" }
                }
            },
            onNo = { cancelId = 0 },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Buyurtmalar")
        Segmented(ORDER_FILTERS, filter) { filter = it }
        ErrorText(msg)
        val list = data?.getJSONArray("orders")?.items()
        if (list == null) {
            Text("Yuklanmoqda…", color = Muted, modifier = Modifier.padding(4.dp))
        } else if (list.isEmpty()) {
            Panel { Text("Buyurtma yo'q", color = Muted) }
        }
        list?.forEach { o ->
            val st = o.optString("status")
            val (bg, fg) = statusColors(st)
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill(STATUS_LABEL[st] ?: st, bg, fg)
                    Box(Modifier.weight(1f))
                    Text(som(o.optLong("price")), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("#${o.optInt("id")} · ${dateOf(o.optLong("createdAt"))}", fontSize = 12.sp, color = Muted)
                Route(o.optString("from"), o.optString("to"))
                Text(String.format("%.1f km  ·  %s", o.optDouble("km"), payText(o)), fontSize = 12.sp, color = Muted)
                val p = o.optJSONObject("passenger")
                val dr = o.optJSONObject("driver")
                PhoneLine("Yo'lovchi", p?.optString("name") ?: "", p?.optString("phone") ?: "")
                if (dr != null) PhoneLine("Haydovchi", dr.optString("name"), dr.optString("phone"))
                if (st in ACTIVE) DangerButton("Buyurtmani bekor qilish") { cancelId = o.optInt("id") }
            }
        }
    }
}

// ───────────────────────── Foydalanuvchilar ─────────────────────────

private val USER_FILTERS = listOf("all" to "Hammasi", "driver" to "Haydovchi", "passenger" to "Yo'lovchi")

@Composable
fun AdminUsers() {
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var dq by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(query) { delay(500); dq = query.trim() }
    val path = "/admin/users?role=" + (if (role == "all") "" else role) + "&q=" + URLEncoder.encode(dq, "UTF-8")
    val data by rememberPoll(path, tick)

    target?.let { u ->
        val block = !u.optBoolean("blocked")
        Confirm(
            if (block) "Bloklash" else "Blokdan chiqarish",
            (if (block) "${u.optString("name")} hisobini bloklaysizmi? U ilovaga kira olmaydi." else "${u.optString("name")} hisobini qayta faollashtirasizmi?"),
            if (block) "Ha, bloklash" else "Ha, chiqarish",
            onYes = {
                target = null
                scope.launch {
                    try {
                        io { Api.call("POST", "/admin/users/${u.optString("id")}/block", JSONObject().put("blocked", block)) }
                        msg = ""; tick++
                    } catch (e: Exception) { msg = e.message ?: "Xato" }
                }
            },
            onNo = { target = null },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Odamlar")
        Segmented(USER_FILTERS, role) { role = it }
        Field(query, { query = it }, "Ism yoki telefon bo'yicha qidirish")
        ErrorText(msg)
        val list = data?.getJSONArray("users")?.items()
        if (list == null) {
            Text("Yuklanmoqda…", color = Muted, modifier = Modifier.padding(4.dp))
        } else if (list.isEmpty()) {
            Panel { Text("Hech kim topilmadi", color = Muted) }
        }
        list?.forEach { u ->
            val blocked = u.optBoolean("blocked")
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.optString("name").ifBlank { "—" }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(u.optString("phone"), fontSize = 13.sp, color = Muted)
                    }
                    if (blocked) Pill("Bloklangan", Color(0xFFFCE4E4), Color(0xFFA32626))
                    else if (u.optString("role") == "driver") {
                        if (u.optBoolean("online")) Pill("Onlayn", Color(0xFFE1F5E9), Color(0xFF14753F))
                        else Pill("Offlayn", Color(0xFFEDEEF1), Muted)
                    }
                }
                val rc = u.optInt("ratingCount")
                Text(
                    (if (u.optString("role") == "driver") "Haydovchi" else "Yo'lovchi") +
                        " · ${u.optInt("trips")} ta safar" +
                        (if (rc > 0) String.format(" · %.1f ★ (%d)", u.optDouble("rating"), rc) else ""),
                    fontSize = 12.sp, color = Muted,
                )
                if (blocked) GhostButton("Blokdan chiqarish") { target = u }
                else DangerButton("Bloklash") { target = u }
            }
        }
    }
}

// ───────────────────────── Murojaatlar ─────────────────────────

private val TICKET_FILTERS = listOf("open" to "Javob kutayotgan", "closed" to "Javob berilgan")

@Composable
private fun TicketCard(t: JSONObject, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.optString("name").ifBlank { "—" } + " · " + (if (t.optString("role") == "driver") "haydovchi" else "yo'lovchi"),
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text(dateOf(t.optLong("createdAt")), fontSize = 11.sp, color = Muted)
        }
        PhoneLine("Telefon", "", t.optString("phone"))
        Text(t.optString("text"), fontSize = 15.sp)
        if (t.has("reply")) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Chip).padding(12.dp)) {
                Text("Javob: " + t.optString("reply"), fontSize = 14.sp)
            }
        } else {
            Field(reply, { reply = it }, "Javobingiz")
            BigButton("Javob yuborish", enabled = !busy && reply.trim().length >= 2) {
                scope.launch {
                    busy = true
                    try {
                        io { Api.call("POST", "/admin/tickets/${t.optInt("id")}/reply", JSONObject().put("text", reply.trim())) }
                        msg = ""; onSent()
                    } catch (e: Exception) { msg = e.message ?: "Xato" }
                    busy = false
                }
            }
            ErrorText(msg)
        }
    }
}

@Composable
fun AdminTickets() {
    var filter by remember { mutableStateOf("open") }
    var tick by remember { mutableStateOf(0) }
    val data by rememberPoll("/admin/tickets?status=$filter", tick)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Murojaatlar")
        Segmented(TICKET_FILTERS, filter) { filter = it }
        val list = data?.getJSONArray("tickets")?.items()
        if (list == null) {
            Text("Yuklanmoqda…", color = Muted, modifier = Modifier.padding(4.dp))
        } else if (list.isEmpty()) {
            Panel { Text(if (filter == "open") "Yangi murojaat yo'q" else "Javob berilgan murojaat yo'q", color = Muted) }
        }
        list?.forEach { t -> TicketCard(t) { tick++ } }
    }
}

// ───────────────────────── Profil ─────────────────────────

@Composable
fun AdminProfile(prefs: SharedPreferences, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("") }
    var installUrl by remember { mutableStateOf<String?>(null) }
    var showTariffs by remember { mutableStateOf(false) }
    installUrl?.let { InstallDialog(it, "TOXA Admin") { installUrl = null } }

    if (showTariffs) {
        AdminTariffs(onBack = { showTariffs = false })
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Sozlamalar")
        Panel {
            Text("Admin profili", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(prefs.getString("name", "").orEmpty().ifBlank { "Admin" }, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(prefs.getString("phone", "").orEmpty(), fontSize = 14.sp, color = Muted)
            Pill("🛡 Administrator", Color(0xFFFFF1C2), Color(0xFF8A6100))
        }
        AppPrefsPanel()
        Panel {
            Text("Tariflarni boshqarish", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Minimal narx, boshlang‘ich to‘lov va har kilometr narxini o‘zgartiring. Yangi buyurtmalarga darhol qo‘llanadi.", fontSize = 13.sp, color = Muted)
            BigButton("Tariflarni ochish") { showTariffs = true }
        }
        Panel {
            Text("Tizim ma'lumotlari", fontWeight = FontWeight.Bold)
            Text("Ilova versiyasi: ${adminVersion(ctx)}", fontSize = 13.sp, color = Muted)
            Text("Server: ${Api.base}", fontSize = 12.sp, color = Muted)
            GhostButton("Yangilanishni tekshirish") {
                scope.launch {
                    info = "Tekshirilmoqda…"
                    try {
                        val r = latestRelease("toxa-admin")
                        if (r != null && r.first > currentVersionCode(ctx)) {
                            info = "Yangi versiya ${r.first} topildi"
                            installUrl = r.second
                        } else {
                            info = "Ilova eng so'nggi versiyada (${adminVersion(ctx)})"
                        }
                    } catch (e: Exception) {
                        info = "Yangilanish tekshirilmadi: ${e.message ?: "internet yoki server xatosi"}"
                    }
                }
            }
            if (info.isNotEmpty()) Text(info, fontSize = 12.sp, color = Muted)
        }
        Panel {
            Text("Xavfsizlik", fontWeight = FontWeight.Bold)
            Text("Admin huquqlari server tomonidan tekshiriladi. Tarif va boshqaruv amallari faqat admin hisobida ishlaydi.", fontSize = 13.sp, color = Muted)
            DangerButton("Hisobdan chiqish", onClick = onLogout)
        }
    }
}

@Composable
private fun AdminTariffs(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val data by rememberPoll("/admin/tariffs", tick)
    var base by remember { mutableStateOf("") }
    var perKm by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(data) {
        val t = data?.optJSONObject("tariffs")
        if (t != null && !initialized) {
            base = t.optLong("base", 5000).toString()
            perKm = t.optLong("perKm", 2500).toString()
            minimum = t.optLong("minimum", 8000).toString()
            initialized = true
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Orqaga", color = Ink) }
            Text("Tariflar", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Panel {
            Text("Standart tarif", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Narxlar so‘mda kiritiladi. O‘zgarish faqat keyingi yangi buyurtmalarga ta’sir qiladi.", fontSize = 13.sp, color = Muted)
            Field(base, { base = it.filter(Char::isDigit) }, "Boshlang‘ich narx (so‘m)", keyboard = KeyboardType.Number)
            Field(perKm, { perKm = it.filter(Char::isDigit) }, "1 km narxi (so‘m)", keyboard = KeyboardType.Number)
            Field(minimum, { minimum = it.filter(Char::isDigit) }, "Minimal safar narxi (so‘m)", keyboard = KeyboardType.Number)
            if (initialized) {
                val km = 5.0
                val preview = maxOf(minimum.toLongOrNull() ?: 0L,
                    (((base.toLongOrNull() ?: 0L) + km * (perKm.toLongOrNull() ?: 0L)) / 500.0).toLong() * 500L)
                Text("Misol: 5 km safar ≈ ${som(preview)}", fontSize = 13.sp, color = Muted)
            }
            ErrorText(if (error) message else "")
            if (!error && message.isNotBlank()) Text(message, color = Color(0xFF14753F), fontSize = 13.sp)
            BigButton("Tariflarni saqlash", enabled = initialized && !busy &&
                (base.toLongOrNull() ?: -1) >= 0 && (perKm.toLongOrNull() ?: 0) >= 100 &&
                (minimum.toLongOrNull() ?: 0) >= 1000) {
                scope.launch {
                    busy = true; message = ""; error = false
                    try {
                        val body = JSONObject()
                            .put("base", base.toLong())
                            .put("perKm", perKm.toLong())
                            .put("minimum", minimum.toLong())
                        io { Api.call("POST", "/admin/tariffs", body) }
                        message = "Tariflar saqlandi. Yangi buyurtmalarda qo‘llanadi."
                        tick++
                    } catch (e: Exception) {
                        message = e.message ?: "Saqlashda xato"; error = true
                    }
                    busy = false
                }
            }
            if (busy) Text("Saqlanmoqda…", fontSize = 12.sp, color = Muted)
        }
        Panel {
            Text("Hisoblash qoidasi", fontWeight = FontWeight.Bold)
            Text("Safar narxi = boshlang‘ich narx + masofa × 1 km narxi. Yakuniy summa 500 so‘mga yaxlitlanadi va minimal narxdan past bo‘lmaydi.", fontSize = 13.sp, color = Muted)
        }
    }
}

private fun adminVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
} catch (e: Exception) { "-" }
