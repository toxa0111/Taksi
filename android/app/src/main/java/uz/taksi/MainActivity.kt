package uz.taksi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("taksi", Context.MODE_PRIVATE)
        Api.token = prefs.getString("token", "") ?: ""
        Api.base = prefs.getString("base", Api.base) ?: Api.base
        // Holat paneli sariq, belgilari qora
                window.statusBarColor = 0xFFFFC21A.toInt()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent { TaksiTheme { Surface(Modifier.fillMaxSize(), color = PageBg) { App(prefs) } } }
    }
}

@Composable
fun App(prefs: SharedPreferences) {
    var role by remember { mutableStateOf(prefs.getString("role", "") ?: "") }
    val logout = {
        prefs.edit().remove("token").remove("role").apply()
        Api.token = ""
        role = ""
    }
    UpdateDialog()
    when (role) {
        "passenger" -> PassengerScreen(logout)
        "driver" -> DriverScreen(logout)
        else -> LoginScreen(prefs) { role = it }
    }
}

// ───────────────────────── Umumiy elementlar ─────────────────────────

/** Buyurtma kartasi: holat, narx, yo'nalish, to'lov usuli. */
@Composable
fun OrderCard(o: JSONObject, extra: @Composable ColumnScope.() -> Unit = {}) {
    val st = o.optString("status")
    val (bg, fg) = statusColors(st)
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(STATUS[st] ?: st, bg, fg)
            Spacer(Modifier.weight(1f))
            Text(som(o.optLong("price")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Route(o.optString("from"), o.optString("to"))
        Text(
            String.format("%.1f km  ·  %s", o.optDouble("km"), payText(o)),
            fontSize = 13.sp, color = Muted,
        )
        extra()
    }
}

/** Ism va telefon, yoniga "Qo'ng'iroq" tugmasi. */
@Composable
fun PersonRow(role: String, name: String, phone: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEDEEF1)), contentAlignment = Alignment.Center) {
            Text("👤", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { role }, fontWeight = FontWeight.Bold)
            Text("$role · $phone", fontSize = 12.sp, color = Muted)
        }
        DarkButton("📞 Qo'ng'iroq") {
            try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (e: Exception) { }
        }
    }
}

/** Telefondagi navigator ilovasida (Google Maps, Yandex, 2GIS…) nuqtani ochadi. */
fun openNavigator(ctx: Context, lat: Double, lng: Double, label: String) {
    try {
        val uri = Uri.parse("geo:0,0?q=$lat,$lng(${Uri.encode(label)})")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) { }
}

/** Joylashuvni serverga yuborib turadi (boshqa tomon xaritada ko'radi). */
@Composable
fun ShareLocation(me: Pair<Double, Double>?) {
    LaunchedEffect(me) {
        if (me != null) {
            try { io { Api.call("POST", "/location", JSONObject().put("lat", me.first).put("lng", me.second)) } } catch (e: Exception) { }
        }
    }
}

/** Yumaloq burchakli xarita. */
@Composable
fun RoundedMap(
    pins: List<Pin>,
    center: Pair<Double, Double>,
    height: Int,
    onTap: ((Double, Double) -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(22.dp))) {
        MapBox(pins, center, Modifier.fillMaxSize(), onTap)
    }
}

@Composable
fun ErrorText(msg: String) {
    if (msg.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFCE4E4)).padding(12.dp)) {
            Text(msg, color = Color(0xFFA32626), fontSize = 13.sp)
        }
    }
}

// ───────────────────────── Kirish ─────────────────────────

@Composable
fun LoginScreen(prefs: SharedPreferences, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf(Api.base) }
    var showServer by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("+998") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("passenger") }
    var code by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(PageBg).verticalScroll(rememberScrollState())) {
        // Sariq sarlavha
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Yellow)
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Column {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                    Text("🚕", fontSize = 32.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Taksi", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("Shahar ichida tez va qulay safar", fontSize = 15.sp, color = Ink.copy(alpha = 0.75f))
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel {
                Text(
                    if (sent) "Tasdiqlash kodi" else "Kirish",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Segmented(
                    listOf("passenger" to "🧍 Yo'lovchi", "driver" to "🚖 Haydovchi"),
                    role,
                ) { role = it }
                Field(phone, { phone = it }, "Telefon raqam", keyboard = KeyboardType.Phone)
                Field(name, { name = it }, "Ismingiz (birinchi marta kirganda)")

                if (!sent) {
                    BigButton("Kod olish", enabled = !busy) {
                        scope.launch {
                            busy = true; msg = ""
                            try {
                                Api.base = base.trim().trimEnd('/')
                                val r = io { Api.call("POST", "/auth/request", JSONObject().put("phone", phone.trim())) }
                                sent = true
                                // Test rejimida server kodni o'zi qaytaradi. Haqiqiy SMS ulanganda bu o'chadi.
                                devCode = if (r.has("devCode")) r.optString("devCode") else ""
                            } catch (e: Exception) { msg = e.message ?: "Xato" }
                            busy = false
                        }
                    }
                } else {
                    if (devCode.isNotEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFFF1C2)).padding(12.dp),
                        ) {
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
                                    .put("name", name.trim()).put("role", role)
                                val r = io { Api.call("POST", "/auth/verify", body) }
                                val u = r.getJSONObject("user")
                                Api.token = r.getString("token")
                                prefs.edit()
                                    .putString("token", Api.token).putString("role", u.getString("role"))
                                    .putString("base", Api.base).apply()
                                onDone(u.getString("role"))
                            } catch (e: Exception) { msg = e.message ?: "Xato" }
                            busy = false
                        }
                    }
                    TextButton(
                        onClick = { sent = false; code = ""; msg = ""; devCode = "" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Raqamni o'zgartirish", color = Muted) }
                }
                if (busy) Text("Server javob kutilmoqda… (birinchi marta 30–60 soniya ketishi mumkin)", fontSize = 12.sp, color = Muted)
                ErrorText(msg)
            }

            // Server manzili: odatda o'zgartirish shart emas
            TextButton(onClick = { showServer = !showServer }, modifier = Modifier.fillMaxWidth()) {
                Text("⚙ Server sozlamalari", color = Muted, fontSize = 13.sp)
            }
            if (showServer) Field(base, { base = it }, "Server manzili", keyboard = KeyboardType.Uri)
        }
    }
}

// ───────────────────────── Yo'lovchi ─────────────────────────

@Composable
fun PassengerScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by rememberMyLocation()
    var start by remember { mutableStateOf<Pair<Double, Double>?>(null) }   // GPS birinchi topilgan joy
    var focus by remember { mutableStateOf<Pair<Double, Double>?>(null) }   // xarita markaziga o'tkazish
    var pickup by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var dest by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var target by remember { mutableStateOf("from") }
    var pay by remember { mutableStateOf("cash") }
    var search by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var orders by remember { mutableStateOf(listOf<JSONObject>()) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    ShareLocation(me)
    LaunchedEffect(me) {
        val m = me
        if (m != null && start == null) {
            start = m
            if (pickup == null) pickup = m           // odatda yo'lovchi turgan joydan chaqiradi
        }
    }
    LaunchedEffect(pickup, dest) {
        val a = pickup
        val b = dest
        quote = null
        if (a != null && b != null) {
            try {
                quote = io { Api.call("GET", "/estimate?flat=${a.first}&flng=${a.second}&tlat=${b.first}&tlng=${b.second}") }
            } catch (e: Exception) { msg = e.message ?: "Xato" }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val n = io { Api.call("GET", "/orders/mine").getJSONArray("orders").items() }
                if (!n.sameAs(orders)) orders = n
            } catch (e: Exception) { }
            delay(3000)
        }
    }

    val active = orders.firstOrNull { it.optString("status") in ACTIVE }
    val needPay = orders.firstOrNull {
        it.optString("status") == "done" && it.optString("payMethod") == "card" && it.optString("payStatus") != "paid"
    }

    Column(Modifier.fillMaxSize().background(PageBg)) {
        TopBar("Taksi chaqirish", if (active != null) "Safaringiz davom etmoqda" else "Qayerga boramiz?", onLogout)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (needPay != null) {
                OrderCard(needPay) {
                    BigButton("💳 Karta bilan to'lash") {
                        scope.launch {
                            try { io { Api.call("POST", "/orders/${needPay.optInt("id")}/pay", JSONObject()) }; msg = "" }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                }
            }

            if (active != null) {
                val pins = buildList {
                    add(Pin(active.optDouble("fromLat"), active.optDouble("fromLng"), "Olinadigan joy", GREEN))
                    add(Pin(active.optDouble("toLat"), active.optDouble("toLng"), "Borish manzili", RED))
                    locOf(active, "driverLoc")?.let { add(Pin(it.first, it.second, "Haydovchi", ORANGE)) }
                    me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
                }
                RoundedMap(pins, Pair(active.optDouble("fromLat"), active.optDouble("fromLng")), 320)
                Legend(GREEN to "Olish joyi", RED to "Manzil", ORANGE to "Haydovchi", BLUE to "Men")
                OrderCard(active) {
                    val d = active.optJSONObject("driver")
                    if (d != null) PersonRow("Haydovchi", d.optString("name"), d.optString("phone"))
                    DangerButton("Buyurtmani bekor qilish") {
                        scope.launch {
                            try { io { Api.call("POST", "/orders/${active.optInt("id")}/cancel", JSONObject()) } }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                }
            } else {
                val pins = buildList {
                    pickup?.let { add(Pin(it.first, it.second, "Qayerdan", GREEN)) }
                    dest?.let { add(Pin(it.first, it.second, "Qayerga", RED)) }
                    me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
                }
                RoundedMap(pins, focus ?: start ?: DEFAULT_CENTER, 320) { lat, lng ->
                    if (target == "from") { pickup = Pair(lat, lng); target = "to" } else dest = Pair(lat, lng)
                }
                Legend(GREEN to "Qayerdan", RED to "Qayerga", BLUE to "Men")

                Panel {
                    Segmented(listOf("from" to "🟢 Qayerdan", "to" to "🔴 Qayerga"), target) { target = it }
                    Route(
                        if (pickup == null) "Olinadigan joyni belgilang" else "Olinadigan joy belgilandi ✓",
                        if (dest == null) "Borish manzilini belgilang" else "Borish manzili belgilandi ✓",
                    )
                    Text(
                        if (target == "from") "Xaritaga bosib olinadigan joyni belgilang" else "Xaritaga bosib borish manzilini belgilang",
                        fontSize = 12.sp, color = Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(search, { search = it }, "Manzil bo'yicha qidirish", Modifier.weight(1f))
                        DarkButton("Topish", enabled = !busy && search.isNotBlank()) {
                            scope.launch {
                                busy = true; msg = ""
                                val r = io { geocode(ctx, search) }
                                if (r == null) {
                                    msg = "Manzil topilmadi. Xaritaga bosib belgilang"
                                } else {
                                    if (target == "from") { pickup = r; target = "to" } else dest = r
                                    focus = r
                                }
                                busy = false
                            }
                        }
                    }
                    GhostButton("📍 Mening joyimdan olib ketish", enabled = me != null) {
                        val m = me
                        if (m != null) { pickup = m; focus = m; target = "to" }
                    }
                }

                val q = quote
                val a = pickup
                val b = dest
                if (q != null && a != null && b != null) {
                    Panel {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Safar narxi", fontSize = 12.sp, color = Muted)
                                Text(som(q.optLong("price")), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(String.format("~%.1f km", q.optDouble("km")), color = Muted, fontWeight = FontWeight.SemiBold)
                        }
                        Text("To'lov usuli", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Segmented(listOf("cash" to "💵 Naqd", "card" to "💳 Karta"), pay) { pay = it }
                        if (pay == "card") Text("Karta bilan safar tugagach to'laysiz.", fontSize = 12.sp, color = Muted)
                        BigButton("Taksi chaqirish", enabled = !busy) {
                            scope.launch {
                                busy = true
                                try {
                                    val fromName = io { reverseGeocode(ctx, a.first, a.second) }
                                    val toName = io { reverseGeocode(ctx, b.first, b.second) }
                                    val body = JSONObject()
                                        .put("from", fromName).put("to", toName)
                                        .put("fromLat", a.first).put("fromLng", a.second)
                                        .put("toLat", b.first).put("toLng", b.second)
                                        .put("payMethod", pay)
                                    io { Api.call("POST", "/orders", body) }
                                    dest = null; target = "to"; msg = ""
                                } catch (e: Exception) { msg = e.message ?: "Xato" }
                                busy = false
                            }
                        }
                    }
                } else {
                    Text(
                        "Olinadigan joy va borish manzilini belgilang — narx shu yerda chiqadi.",
                        fontSize = 13.sp, color = Muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            ErrorText(msg)

            val history = orders.filter { it.optString("status") !in ACTIVE && it !== needPay }
            if (history.isNotEmpty()) {
                SectionTitle("Oldingi safarlar")
                history.forEach { OrderCard(it) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ───────────────────────── Haydovchi ─────────────────────────

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun DriverScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by rememberMyLocation()
    var start by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var available by remember { mutableStateOf(listOf<JSONObject>()) }
    var mine by remember { mutableStateOf(listOf<JSONObject>()) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }

    ShareLocation(me)
    LaunchedEffect(me) {
        val m = me
        if (m != null && start == null) start = m
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val m = io { Api.call("GET", "/orders/mine").getJSONArray("orders").items() }
                if (!m.sameAs(mine)) mine = m
                val a = io { Api.call("GET", "/orders/available").getJSONArray("orders").items() }
                if (!a.sameAs(available)) available = a
                val s = io { Api.call("GET", "/stats") }
                if (s.toString() != stats?.toString()) stats = s
            } catch (e: Exception) { }
            delay(3000)
        }
    }

    fun post(path: String, body: JSONObject = JSONObject()) {
        scope.launch {
            try { io { Api.call("POST", path, body) }; msg = "" }
            catch (e: Exception) { msg = e.message ?: "Xato" }
        }
    }

    val active = mine.firstOrNull { it.optString("status") in ACTIVE }

    Column(Modifier.fillMaxSize().background(PageBg)) {
        TopBar("Haydovchi", if (active != null) "Faol buyurtma" else "Buyurtmalarni kutmoqda", onLogout)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stats?.let {
                Panel(color = Ink) {
                    Text("Bugungi daromad", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    Text(som(it.optLong("todaySum")), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatItem("Safarlar", "${it.optInt("todayCount")} ta", Modifier.weight(1f))
                        StatItem("Naqd", som(it.optLong("todayCash")), Modifier.weight(1f))
                        StatItem("Karta", som(it.optLong("todayCard")), Modifier.weight(1f))
                    }
                    Text(
                        "Jami: ${it.optInt("totalCount")} ta safar · ${som(it.optLong("totalSum"))}",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            if (active != null) {
                val id = active.optInt("id")
                val st = active.optString("status")
                val isCash = active.optString("payMethod") == "cash"
                val pickup = Pair(active.optDouble("fromLat"), active.optDouble("fromLng"))
                val dropoff = Pair(active.optDouble("toLat"), active.optDouble("toLng"))

                val pins = buildList {
                    add(Pin(pickup.first, pickup.second, "Yo'lovchini olish joyi", GREEN))
                    add(Pin(dropoff.first, dropoff.second, "Borish manzili", RED))
                    locOf(active, "passengerLoc")?.let { add(Pin(it.first, it.second, "Yo'lovchi (hozir)", ORANGE)) }
                    me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
                }
                RoundedMap(pins, pickup, 300)
                Legend(GREEN to "Olish joyi", RED to "Manzil", ORANGE to "Yo'lovchi", BLUE to "Men")

                OrderCard(active) {
                    val p = active.optJSONObject("passenger")
                    if (p != null) PersonRow("Yo'lovchi", p.optString("name"), p.optString("phone"))
                    if (!isCash) Text("Karta: yo'lovchi safar tugagach ilovada to'laydi", fontSize = 12.sp, color = Muted)

                    val goTo = if (st == "started") dropoff else pickup
                    GhostButton("🧭 Navigatorda ochish") {
                        openNavigator(ctx, goTo.first, goTo.second, if (st == "started") "Manzil" else "Yo'lovchi")
                    }

                    val next = mapOf(
                        "accepted" to ("arrived" to "Yetib keldim"),
                        "arrived" to ("started" to "Safarni boshlash"),
                        "started" to ("done" to (if (isCash) "Yakunlash (naqd oldim)" else "Yakunlash")),
                    )[st]
                    if (next != null) {
                        BigButton(next.second) { post("/orders/$id/status", JSONObject().put("status", next.first)) }
                    }
                    if (st == "accepted" || st == "arrived") {
                        DangerButton("Buyurtmani rad etish") { post("/orders/$id/cancel") }
                    }
                }
            } else {
                val pins = buildList {
                    available.forEach { add(Pin(it.optDouble("fromLat"), it.optDouble("fromLng"), "Buyurtma #${it.optInt("id")}", GREEN)) }
                    me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
                }
                RoundedMap(pins, start ?: DEFAULT_CENTER, 260)
                Legend(GREEN to "Yo'lovchilar", BLUE to "Men")

                SectionTitle("Yangi buyurtmalar")
                if (available.isEmpty()) {
                    Panel {
                        Text("⏳", fontSize = 28.sp)
                        Text("Hozircha buyurtma yo'q", fontWeight = FontWeight.Bold)
                        Text("Yangi buyurtmalar shu yerda avtomatik paydo bo'ladi.", fontSize = 13.sp, color = Muted)
                    }
                }
                available.forEach { o ->
                    OrderCard(o) {
                        if (o.has("pickupKm")) {
                            Text(String.format("Yo'lovchigacha: ~%.1f km", o.optDouble("pickupKm")), fontSize = 13.sp, color = Muted)
                        }
                        BigButton("Qabul qilish") { post("/orders/${o.optInt("id")}/accept") }
                    }
                }
            }

            ErrorText(msg)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ───────────────────────── Yangilanish ─────────────────────────

@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(Unit) {
        val r = latestRelease()
        if (r != null && r.first > currentVersionCode(ctx)) update = r
    }
    val u = update ?: return
    AlertDialog(
        onDismissRequest = { update = null },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = { Text("Yangi versiya bor", fontWeight = FontWeight.Bold) },
        text = { Text("Taksi ilovasining yangi versiyasi chiqdi. Yuklangach, faylni bosib o'rnating.") },
        confirmButton = {
            BigButton("Yangilash", Modifier.width(150.dp)) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.second)))
                update = null
            }
        },
        dismissButton = { TextButton(onClick = { update = null }) { Text("Keyinroq", color = Muted) } },
    )
}
