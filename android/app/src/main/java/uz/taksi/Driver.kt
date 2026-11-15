package uz.taksi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Haydovchi ekrani: tepada xarita, pastda sheet. */
@Composable
fun DriverScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by rememberMyLocation()
    var centerTo by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var centerKey by remember { mutableStateOf(0) }
    var available by remember { mutableStateOf(listOf<JSONObject>()) }
    var mine by remember { mutableStateOf(listOf<JSONObject>()) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }

    ShareLocation(me)
    LaunchedEffect(me != null) {
        val m = me
        if (m != null && centerTo == null) { centerTo = m; centerKey++ }
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
    val st = active?.optString("status") ?: ""
    val id = active?.optInt("id") ?: 0
    val pickup = active?.let { Pair(it.optDouble("fromLat"), it.optDouble("fromLng")) }
    val dropoff = active?.let { Pair(it.optDouble("toLat"), it.optDouble("toLng")) }

    // Navigatorga o'tganingizda ham joylashuvingiz yo'lovchiga ko'rinib turadi
    TripLocationService(running = active != null, text = "Faol buyurtma — joylashuv yuborilmoqda")

    // Marshrut: o'zidan yo'lovchigacha, safar boshlangach manzilgacha. Faqat ~110 m siljiganda qayta so'raladi.
    val routeTo: Pair<Double, Double>? = when (st) {
        "accepted", "arrived" -> pickup
        "started" -> dropoff
        else -> null
    }
    val route = rememberRoute(if (routeTo != null) me else null, routeTo, 3)
    val fitKey: Any = if (active == null) "idle" else "order:$id:${if (st == "started") "trip" else "pickup"}"

    val pins = buildList {
        if (active != null) {
            pickup?.let { add(Pin(it.first, it.second, "Yo'lovchini olish joyi", GREEN)) }
            dropoff?.let { add(Pin(it.first, it.second, "Borish manzili", RED)) }
            locOf(active, "passengerLoc")?.let { add(Pin(it.first, it.second, "Yo'lovchi (hozir)", ORANGE)) }
        } else {
            available.forEach { add(Pin(it.optDouble("fromLat"), it.optDouble("fromLng"), "Buyurtma #${it.optInt("id")}", GREEN)) }
        }
        me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapBox(
                pins = pins,
                center = centerTo ?: pickup ?: DEFAULT_CENTER,
                modifier = Modifier.fillMaxSize(),
                route = route?.points,
                fitKey = fitKey,
                centerKey = centerKey,
            )
            FloatingChip(
                "TOXA  •  HAYDOVCHI",
                Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {}
            FloatingChip("Chiqish  ✕", Modifier.align(Alignment.TopEnd).padding(12.dp), onLogout)
            FloatingChip("📍 Mening joyim", Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                val m = me
                if (m != null) { centerTo = m; centerKey++ }
            }
        }

        Sheet {
            if (active != null && pickup != null && dropoff != null) {
                val isCash = active.optString("payMethod") == "cash"
                val (bg, fg) = statusColors(st)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill(STATUS[st] ?: st, bg, fg)
                    Box(Modifier.weight(1f))
                    Text(som(active.optLong("price")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                val goTo = if (st == "started") dropoff else pickup
                val what = if (st == "started") "Manzilgacha" else "Yo'lovchigacha"
                if (st == "arrived") {
                    BigLine("Yo'lovchini kutmoqdasiz", "Yo'lovchi chiqqach, safarni boshlang")
                } else if (route != null) {
                    BigLine("$what ${route.min} daq", String.format("%.1f km", route.km))
                } else {
                    BigLine("$what marshrut hisoblanmoqda…", if (me == null) "GPS kutilmoqda" else "")
                }

                Route(active.optString("from"), active.optString("to"))
                Text(
                    String.format("%.1f km  ·  %s", active.optDouble("km"), payText(active)),
                    fontSize = 13.sp, color = Muted,
                )
                val p = active.optJSONObject("passenger")
                if (p != null) PersonRow("Yo'lovchi", p.optString("name"), p.optString("phone"))
                if (!isCash) Text("Karta: yo'lovchi safar tugagach ilovada to'laydi", fontSize = 12.sp, color = Muted)

                val next = mapOf(
                    "accepted" to ("arrived" to "Yetib keldim"),
                    "arrived" to ("started" to "Safarni boshlash"),
                    "started" to ("done" to (if (isCash) "Yakunlash (naqd oldim)" else "Yakunlash")),
                )[st]
                if (next != null) {
                    BigButton(next.second) { post("/orders/$id/status", JSONObject().put("status", next.first)) }
                }

                val label = if (st == "started") "Manzil" else "Yo'lovchi"
                DarkButton("🧭 Yandex Navigator", Modifier.fillMaxWidth()) {
                    openYandexNavigator(ctx, me, goTo, label)
                }
                GhostButton("🗺 Boshqa xarita") { openOtherMap(ctx, goTo, label) }

                if (st == "accepted" || st == "arrived") {
                    DangerButton("Buyurtmani rad etish") { post("/orders/$id/cancel") }
                }
            } else {
                stats?.let {
                    Panel(color = Ink) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("BUGUNGI NATIJA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.65f), letterSpacing = 1.sp)
                            Box(Modifier.weight(1f))
                            Pill("HISOBOT", Color(0xFF30343C), Yellow)
                        }
                        Text("Bugungi daromad", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(som(it.optLong("todaySum")), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
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

                BigLine(
                    "Yangi buyurtmalar",
                    if (available.isEmpty()) "Kutilmoqda" else "${available.size} ta mavjud",
                )
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
        }
    }
}
