package uz.taksi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("taksi", Context.MODE_PRIVATE)
        Api.token = prefs.getString("token", "") ?: ""
        Api.base = prefs.getString("base", Api.base) ?: Api.base
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App(prefs) } } }
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
    when (role) {
        "passenger" -> PassengerScreen(logout)
        "driver" -> DriverScreen(logout)
        else -> LoginScreen(prefs) { role = it }
    }
}

// ───────────────────────── Umumiy elementlar ─────────────────────────

@Composable
fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun Header(title: String, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onLogout) { Text("Chiqish") }
    }
}

@Composable
fun OrderCard(o: JSONObject, extra: @Composable ColumnScope.() -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(STATUS[o.optString("status")] ?: o.optString("status"), style = MaterialTheme.typography.titleMedium)
            Text("${o.optString("from")} → ${o.optString("to")}")
            Text(String.format("%.1f km · %s", o.optDouble("km"), som(o.optLong("price"))))
            Text(payText(o))
            extra()
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

// ───────────────────────── Kirish ─────────────────────────

@Composable
fun LoginScreen(prefs: SharedPreferences, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf(Api.base) }
    var phone by remember { mutableStateOf("+998") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("passenger") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🚕 Taksi", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = base, onValueChange = { base = it },
            label = { Text("Server manzili") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            label = { Text("Telefon raqam") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Ismingiz (birinchi marta kirganda)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Yo'lovchi", role == "passenger", Modifier.weight(1f)) { role = "passenger" }
            Choice("Haydovchi", role == "driver", Modifier.weight(1f)) { role = "driver" }
        }

        if (!sent) {
            Button(
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true; msg = ""
                        try {
                            Api.base = base.trim().trimEnd('/')
                            val r = io { Api.call("POST", "/auth/request", JSONObject().put("phone", phone.trim())) }
                            sent = true
                            // Test rejimida server kodni o'zi qaytaradi. Haqiqiy SMS ulanganda bu qator o'chadi.
                            if (r.has("devCode")) msg = "Test kodi: ${r.optString("devCode")}"
                        } catch (e: Exception) { msg = e.message ?: "Xato" }
                        busy = false
                    }
                },
            ) { Text("Kod olish") }
        } else {
            OutlinedTextField(
                value = code, onValueChange = { code = it },
                label = { Text("SMS kod") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
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
                },
            ) { Text("Kirish") }
            TextButton(onClick = { sent = false; code = ""; msg = "" }) { Text("Raqamni o'zgartirish") }
        }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
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
            try { orders = io { Api.call("GET", "/orders/mine").getJSONArray("orders").items() } } catch (e: Exception) { }
            delay(3000)
        }
    }

    val active = orders.firstOrNull { it.optString("status") in ACTIVE }
    val needPay = orders.firstOrNull {
        it.optString("status") == "done" && it.optString("payMethod") == "card" && it.optString("payStatus") != "paid"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header("🚕 Taksi chaqirish", onLogout)

        if (needPay != null) {
            OrderCard(needPay) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            try { io { Api.call("POST", "/orders/${needPay.optInt("id")}/pay", JSONObject()) }; msg = "" }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    },
                ) { Text("💳 Karta bilan to'lash") }
            }
        }

        if (active != null) {
            val pins = buildList {
                add(Pin(active.optDouble("fromLat"), active.optDouble("fromLng"), "Olinadigan joy", GREEN))
                add(Pin(active.optDouble("toLat"), active.optDouble("toLng"), "Borish manzili", RED))
                locOf(active, "driverLoc")?.let { add(Pin(it.first, it.second, "Haydovchi", ORANGE)) }
                me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
            }
            MapBox(
                pins,
                Pair(active.optDouble("fromLat"), active.optDouble("fromLng")),
                Modifier.height(300.dp),
            )
            Text("🟢 olinadigan joy · 🔴 manzil · 🟠 haydovchi · 🔵 men", style = MaterialTheme.typography.bodySmall)
            OrderCard(active) {
                val d = active.optJSONObject("driver")
                if (d != null) Text("Haydovchi: ${d.optString("name")} · ${d.optString("phone")}")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            try { io { Api.call("POST", "/orders/${active.optInt("id")}/cancel", JSONObject()) } }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    },
                ) { Text("Bekor qilish") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("🟢 Qayerdan", target == "from", Modifier.weight(1f)) { target = "from" }
                Choice("🔴 Qayerga", target == "to", Modifier.weight(1f)) { target = "to" }
            }
            Text(
                if (target == "from") "Xaritaga bosib olinadigan joyni belgilang" else "Xaritaga bosib borish manzilini belgilang",
                style = MaterialTheme.typography.bodySmall,
            )

            val pins = buildList {
                pickup?.let { add(Pin(it.first, it.second, "Qayerdan", GREEN)) }
                dest?.let { add(Pin(it.first, it.second, "Qayerga", RED)) }
                me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
            }
            MapBox(pins, focus ?: start ?: DEFAULT_CENTER, Modifier.height(300.dp)) { lat, lng ->
                if (target == "from") { pickup = Pair(lat, lng); target = "to" } else dest = Pair(lat, lng)
            }

            OutlinedButton(
                enabled = me != null, modifier = Modifier.fillMaxWidth(),
                onClick = { val m = me; if (m != null) { pickup = m; focus = m; target = "to" } },
            ) { Text("📍 Mening joyimdan olib ketish") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    label = { Text("Manzil bo'yicha qidirish") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = !busy && search.isNotBlank(),
                    onClick = {
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
                    },
                ) { Text("Topish") }
            }

            val q = quote
            val a = pickup
            val b = dest
            if (q != null && a != null && b != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(som(q.optLong("price")), style = MaterialTheme.typography.headlineMedium)
                        Text(String.format("Masofa: taxminan %.1f km", q.optDouble("km")))
                        Text("To'lov usuli", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("💵 Naqd", pay == "cash", Modifier.weight(1f)) { pay = "cash" }
                            Choice("💳 Karta", pay == "card", Modifier.weight(1f)) { pay = "card" }
                        }
                        if (pay == "card") Text("Karta bilan safar tugagach to'laysiz.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            enabled = !busy, modifier = Modifier.fillMaxWidth(),
                            onClick = {
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
                            },
                        ) { Text("Taksi chaqirish") }
                    }
                }
            } else {
                Text("Olinadigan joy va borish manzilini belgilang — narx shu yerda chiqadi.", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)

        val history = orders.filter { it.optString("status") !in ACTIVE && it !== needPay }
        if (history.isNotEmpty()) {
            Text("Oldingi safarlar", style = MaterialTheme.typography.titleMedium)
            history.forEach { OrderCard(it) }
        }
    }
}

// ───────────────────────── Haydovchi ─────────────────────────

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
                mine = io { Api.call("GET", "/orders/mine").getJSONArray("orders").items() }
                available = io { Api.call("GET", "/orders/available").getJSONArray("orders").items() }
                stats = io { Api.call("GET", "/stats") }
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header("🚖 Haydovchi", onLogout)

        stats?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bugun: ${it.optInt("todayCount")} ta safar · ${som(it.optLong("todaySum"))}")
                    Text("Naqd: ${som(it.optLong("todayCash"))} · Karta: ${som(it.optLong("todayCard"))}")
                    Text("Jami: ${it.optInt("totalCount")} ta safar · ${som(it.optLong("totalSum"))}")
                }
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
            MapBox(pins, pickup, Modifier.height(300.dp))
            Text("🟢 olish joyi · 🔴 manzil · 🟠 yo'lovchi · 🔵 men", style = MaterialTheme.typography.bodySmall)

            OrderCard(active) {
                val p = active.optJSONObject("passenger")
                if (p != null) Text("Yo'lovchi: ${p.optString("name")} · ${p.optString("phone")}")
                if (!isCash) Text("Karta: yo'lovchi safar tugagach ilovada to'laydi", style = MaterialTheme.typography.bodySmall)

                val goTo = if (st == "started") dropoff else pickup
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openNavigator(ctx, goTo.first, goTo.second, if (st == "started") "Manzil" else "Yo'lovchi") },
                ) { Text("🧭 Navigatorda ochish") }

                val next = mapOf(
                    "accepted" to ("arrived" to "Yetib keldim"),
                    "arrived" to ("started" to "Safarni boshlash"),
                    "started" to ("done" to (if (isCash) "Yakunlash (naqd oldim)" else "Yakunlash")),
                )[st]
                if (next != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { post("/orders/$id/status", JSONObject().put("status", next.first)) },
                    ) { Text(next.second) }
                }
                if (st == "accepted" || st == "arrived") {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { post("/orders/$id/cancel") },
                    ) { Text("Rad etish") }
                }
            }
        } else {
            val pins = buildList {
                available.forEach { add(Pin(it.optDouble("fromLat"), it.optDouble("fromLng"), "Buyurtma #${it.optInt("id")}", GREEN)) }
                me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
            }
            MapBox(pins, start ?: DEFAULT_CENTER, Modifier.height(260.dp))
            Text("🟢 yo'lovchilar · 🔵 men", style = MaterialTheme.typography.bodySmall)

            Text("Yangi buyurtmalar", style = MaterialTheme.typography.titleMedium)
            if (available.isEmpty()) Text("Hozircha buyurtma yo'q…")
            available.forEach { o ->
                OrderCard(o) {
                    if (o.has("pickupKm")) Text(String.format("Yo'lovchigacha: ~%.1f km", o.optDouble("pickupKm")))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { post("/orders/${o.optInt("id")}/accept") },
                    ) { Text("Qabul qilish") }
                }
            }
        }

        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)
    }
}
