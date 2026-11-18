package uz.taksi

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.DisposableEffect
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
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
    var online by remember { mutableStateOf(false) }
    var shiftSlider by remember { mutableStateOf(0f) }
    var shiftDragging by remember { mutableStateOf(false) }
    var confirmOffline by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var demand by remember { mutableStateOf(listOf<JSONObject>()) }
    LaunchedEffect(Unit) { Voice.init(ctx) }
    var verified by remember { mutableStateOf(true) }
    var diagWarn by remember { mutableStateOf<String?>(null) }
    var lowWarn by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try { verified = io { Api.call("GET", "/driver/docs") }.optBoolean("verified", true) } catch (e: Exception) { }
            try { diagWarn = diagAlert(io { Api.call("GET", "/driver/diagnostics") }.optJSONObject("items")) } catch (e: Exception) { }
            try {
                val b = io { Api.call("GET", "/driver/balance") }
                val bl = b.optLong("balance"); val wn = b.optLong("warn", 5000); val rs = b.optLong("resume", 10000)
                lowWarn = if (b.optBoolean("locked")) "⛔ Hisobingiz kam (" + som(bl) + "). Liniyaga chiqish uchun hisobni " + som(rs) + " dan oshirib to'ldiring: Pul → To'ldirish."
                else if (bl <= wn + 3000) "⚠ Hisobingizda " + som(bl) + " qoldi. " + som(wn) + " ga tushsa liniyadan chiqarilasiz. Hisobni to'ldiring."
                else null
            } catch (e: Exception) { }
            delay(30000)
        }
    }

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
                online = s.optBoolean("online", false)
                if (!shiftDragging) shiftSlider = if (online) 1f else 0f
            } catch (e: Exception) { }
            delay(3000)
        }
    }

    LaunchedEffect(online) {
        while (online) {
            try { demand = io { Api.call("GET", "/driver/demand").getJSONArray("zones").items() } } catch (e: Exception) { }
            delay(20000)
        }
        demand = emptyList()
    }

    fun post(path: String, body: JSONObject = JSONObject()) {
        scope.launch {
            try { io { Api.call("POST", path, body) }; msg = "" }
            catch (e: Exception) { msg = e.message ?: "Xato" }
        }
    }

    fun setAvailability(value: Boolean) {
        scope.launch {
            try {
                io { Api.call("POST", "/driver/availability", JSONObject().put("online", value)) }
                online = value
                shiftSlider = if (value) 1f else 0f
                msg = ""
            } catch (e: Exception) {
                shiftSlider = if (online) 1f else 0f
                msg = e.message ?: "Ish holatini o'zgartirib bo'lmadi"
            }
        }
    }

    // Yangi buyurtma tushganda tebranish va signal
    var lastCount by remember { mutableStateOf(0) }
    LaunchedEffect(available.size) {
        if (available.size > lastCount && online) alertNewOrder(ctx)
        lastCount = available.size
    }

    val active = mine.firstOrNull { it.optString("status") in ACTIVE }
    val st = active?.optString("status") ?: ""

    // ---- 10 soniyalik buyurtma taklifi (3 km ichida olinmasa -7 ball) ----
    var offerId by remember { mutableStateOf(0) }
    var offerEnd by remember { mutableStateOf(0L) }
    var offerOrder by remember { mutableStateOf<JSONObject?>(null) }
    var offerNow by remember { mutableStateOf(0L) }
    val dismissed = remember { mutableStateListOf<Int>() }
    fun closeOffer() { OfferSound.stop(); offerId = 0; offerOrder = null }
    LaunchedEffect(available, online, active) {
        if (offerId == 0 && online && active == null) {
            val c = available.firstOrNull { it.optBoolean("offer") && it.optLong("offerLeftMs") > 800 && it.optInt("id") !in dismissed }
            if (c != null) {
                offerId = c.optInt("id"); offerOrder = c
                offerNow = SystemClock.elapsedRealtime(); offerEnd = offerNow + c.optLong("offerLeftMs")
                OfferSound.start(ctx)
            }
        } else if (offerId != 0 && (active != null || !online || available.none { it.optInt("id") == offerId })) closeOffer()
    }
    LaunchedEffect(offerId) {
        while (offerId != 0) {
            offerNow = SystemClock.elapsedRealtime()
            if (offerNow >= offerEnd) {
                dismissed.add(offerId); closeOffer()
                msg = "⏱ Vaqt tugadi: buyurtma olinmadi, 7 ball ayiriladi"
                break
            }
            delay(33)
        }
    }
    DisposableEffect(Unit) { onDispose { OfferSound.stop() } }
    val id = active?.optInt("id") ?: 0
    val pickup = active?.let { Pair(it.optDouble("fromLat"), it.optDouble("fromLng")) }
    val dropoff = active?.let { Pair(it.optDouble("toLat"), it.optDouble("toLng")) }

    // Navigatorga o'tganingizda ham joylashuvingiz yo'lovchiga ko'rinib turadi
    TripLocationService(
        running = active != null || online,
        text = if (active != null) "Faol buyurtma — joylashuv yuborilmoqda" else "Onlayn — yangi buyurtmalar kutilmoqda",
        role = "driver",
    )

    // Marshrut: o'zidan yo'lovchigacha, safar boshlangach manzilgacha. Faqat ~110 m siljiganda qayta so'raladi.
    val routeTo: Pair<Double, Double>? = when (st) {
        "accepted", "arrived" -> pickup
        "started" -> dropoff
        else -> null
    }
    val route = rememberRoute(if (routeTo != null) me else null, routeTo, 3)
    // Ichki navigatsiya: keyingi burilish. Marshrut yangilanganda qaytadan hisoblanadi.
    val steps = route?.steps ?: emptyList()
    var stepIdx by remember(route) { mutableStateOf(if (steps.size > 1) 1 else 0) }
    LaunchedEffect(me, route) {
        val m = me
        if (m != null) {
            while (stepIdx < steps.size - 1 && meters(m, steps[stepIdx].lat, steps[stepIdx].lng) < 30.0) stepIdx++
        }
    }
    val nextStep = steps.getOrNull(stepIdx)
    val nearNext = nextStep != null && me?.let { meters(it, nextStep.lat, nextStep.lng) < 180.0 } == true
    LaunchedEffect(stepIdx, nearNext, route) {
        val ns = nextStep
        if (ns != null && route != null && active != null) {
            val d = me?.let { meters(it, ns.lat, ns.lng) } ?: ns.meters.toDouble()
            val dText = if (d >= 1000) String.format("%.1f km", d / 1000.0) else "${(d / 10).toInt() * 10} m"
            val tx = translate(ns.text, AppSettings.lang)
            val lead = when (AppSettings.lang) { "ru" -> "Через $dText, "; "en" -> "In $dText, "; else -> "$dText dan keyin, " }
            Voice.say(if (nearNext) tx else lead + tx)
        }
    }
    var follow by remember { mutableStateOf(true) }
    val navActive = active != null && route != null && me != null
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

    if (confirmCancel != 0) {
        AlertDialog(
            onDismissRequest = { confirmCancel = 0 },
            shape = RoundedCornerShape(24.dp),
            containerColor = Card,
            title = { Text("Buyurtmani bekor qilasizmi?", fontWeight = FontWeight.Bold) },
            text = { Text("Qabul qilingan buyurtmani bekor qilsangiz, 4 ball ayiriladi va yangi buyurtmalar kechroq ko'rinadi.") },
            confirmButton = {
                TextButton(onClick = {
                    val cid = confirmCancel; confirmCancel = 0
                    scope.launch {
                        try {
                            val r = io { Api.call("POST", "/orders/$cid/cancel") }
                            msg = if (r.optInt("penalty") > 0) "Bekor qilindi: −" + r.optInt("penalty") + " ball" else ""
                        } catch (e: Exception) { msg = e.message ?: "Xato" }
                    }
                }) { Text("Ha, bekor qilaman", color = Bad, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = 0 }) { Text("Yo'q", color = Muted) } },
        )
    }

    if (confirmOffline) {
        AlertDialog(
            onDismissRequest = { confirmOffline = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Card,
            title = { Text("Ishdan chiqasizmi?", fontWeight = FontWeight.Bold) },
            text = { Text("Offlayn bo'lsangiz, yangi buyurtmalar kelmaydi.") },
            confirmButton = {
                TextButton(onClick = { confirmOffline = false; setAvailability(false) }) {
                    Text("Ha, chiqaman", color = Bad, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOffline = false }) { Text("Yo'q", color = Muted) } },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapBox(
                pins = pins,
                center = if (follow && navActive) me!! else (centerTo ?: pickup ?: DEFAULT_CENTER),
                modifier = Modifier.fillMaxSize(),
                route = route?.points,
                fitKey = if (follow && navActive) null else fitKey,
                centerKey = centerKey,
                zoom = if (follow && navActive) 17.0f else 15.0f,
                zones = if (active == null) demand.map {
                    val f = it.optLong("from")
                    Zone(it.optDouble("lat"), it.optDouble("lng"), 650f,
                        if (f > 0) "🔒 " + som(f).replace(" so'm", "") + " so'mdan" else "${it.optInt("count")} ta buyurtma")
                } else emptyList(),
            )
            if (navActive && nextStep != null) {
                val dist = me?.let { meters(it, nextStep.lat, nextStep.lng) } ?: 0.0
                Row(
                    Modifier.align(Alignment.TopCenter).padding(start = 12.dp, end = 12.dp, top = 56.dp)
                        .fillMaxWidth()
                        .background(Ink, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(nextStep.arrow, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Yellow)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (dist >= 1000) String.format("%.1f km", dist / 1000.0) else "${(dist / 10).toInt() * 10} m",
                            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        )
                        Text(nextStep.text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 2)
                    }
                }
            }
            val warnText = if (lowWarn != null) lowWarn else if (!verified) "⚠ Hujjatlaringiz tasdiqlanmagan. Kabinet → Hujjatlar bo'limidan yuboring." else diagWarn
            if (warnText != null && !navActive) {
                Text(
                    warnText,
                    Modifier.align(Alignment.TopCenter).padding(start = 12.dp, end = 12.dp, top = 64.dp).fillMaxWidth()
                        .background(Color(0xFFFFF1C2), RoundedCornerShape(14.dp)).padding(10.dp),
                    fontSize = 12.sp, color = Color(0xFF8A6100), fontWeight = FontWeight.SemiBold,
                )
            }
            if (!navActive) {
                Column(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 14.dp, end = 80.dp)
                    .clickable { if (online) confirmOffline = true }) {
                    Text(if (online) "Liniyada" else "Liniyada emas", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp))
                    Text(if (online) "Buyurtmalar qidirilmoqda" else "Buyurtmalarni qidirish o'chirilgan", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp))
                }
            } else FloatingChip(
                if (online) "🟢 ONLAYN" else "⚪ OFFLAYN",
                Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                if (online) confirmOffline = true
            }
            FloatingChip(
                if (navActive && follow) "🧭 Kuzatish: yoqiq" else if (navActive) "🧭 Kuzatishni yoqish" else "📍 Mening joyim",
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                val m = me
                if (navActive) follow = !follow
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
                IntercityLine(active)
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

                if (st == "accepted" || st == "arrived") {
                    DangerButton("Buyurtmani bekor qilish (−4 ball)") { confirmCancel = id }
                }
                if (st == "started" && available.isNotEmpty()) {
                    Text("Keyingi buyurtma (oldindan band qilish)", fontSize = 13.sp, color = Muted)
                    available.take(3).forEach { o ->
                        OrderCard(o) {
                            IntercityLine(o)
                            if (o.optBoolean("reservedByMe")) {
                                Pill("✅ Band qilindi — safardan keyin avtomatik boshlanadi", Color(0xFFE1F5E9), Color(0xFF14753F))
                                DangerButton("Bandni bekor qilish") { post("/orders/${o.optInt("id")}/unreserve") }
                            } else {
                                BigButton("Keyingisiga band qilish") { post("/orders/${o.optInt("id")}/reserve") }
                            }
                        }
                    }
                }
            } else {
                if (!online) {
                    DayCard(stats?.optInt("todayCount") ?: 0, stats?.optLong("todaySum") ?: 0L,
                        prioOf(stats))
                    Panel(color = Ink) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("HAYDOVCHI HOLATI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.62f), letterSpacing = 1.sp)
                                Text(if (online) "Ishdasiz" else "Hozir ishda emassiz", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (online) Color(0xFF7BE0A2) else Color.White)
                            }
                            Pill(if (online) "ONLAYN" else "OFFLAYN", if (online) Color(0xFF173D2C) else Color(0xFF30343C), if (online) Color(0xFF7BE0A2) else Color.White.copy(alpha = 0.75f))
                        }
                        Text(if (online) "Ishdan chiqish uchun chapga suring" else "Ishga chiqish uchun o\u0027ngga suring", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        // Yandex Pro uslubidagi keng, qalin surish yo'lagi
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().padding(top = 4.dp)
                                .height(64.dp)
                                .background(Color(0xFF252A32), RoundedCornerShape(32.dp))
                                .pointerInput(online) {
                                    var dragValue = shiftSlider
                                    detectHorizontalDragGestures(
                                        onDragStart = { shiftDragging = true; dragValue = shiftSlider },
                                        onHorizontalDrag = { change, amount ->
                                            change.consume()
                                            dragValue = (dragValue + amount / size.width.toFloat()).coerceIn(0f, 1f)
                                            shiftSlider = dragValue
                                        },
                                        onDragEnd = {
                                            shiftDragging = false
                                            setAvailability(dragValue >= 0.5f)
                                        },
                                        onDragCancel = {
                                            shiftDragging = false
                                            shiftSlider = if (online) 1f else 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(Modifier.fillMaxWidth(shiftSlider.coerceAtLeast(0.16f)).height(64.dp)
                                .background(if (online) Color(0xFF237A4B) else Color(0xFF4A505B), RoundedCornerShape(32.dp)))
                            Text(
                                if (online) "◀  ISHDAN CHIQISH UCHUN SURING" else "ISHGA CHIQISH UCHUN SURING  ▶",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
                                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = 0.3.sp,
                            )
                            Box(
                                Modifier.offset(x = (maxWidth - 58.dp) * shiftSlider)
                                    .size(58.dp).padding(5.dp)
                                    .background(Yellow, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("➜", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink)
                            }
                        }
                    }
                }



                if (online) {
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
                    if (demand.isNotEmpty()) {
                        Panel {
                            Text("🔥 Talab yuqori joylar", fontWeight = FontWeight.Bold)
                            demand.take(3).forEach { z ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { centerTo = Pair(z.optDouble("lat"), z.optDouble("lng")); centerKey++; follow = false },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("📍 ${z.optInt("count")} ta buyurtma", modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    Text(
                                        me?.let { String.format("%.1f km", meters(it, z.optDouble("lat"), z.optDouble("lng")) / 1000.0) } ?: "",
                                        fontSize = 12.sp, color = Muted,
                                    )
                                }
                            }
                            Text("Xaritada to'q sariq belgilar — talab zonalari", fontSize = 11.sp, color = Muted)
                        }
                    }
                    available.forEach { o ->
                        OrderCard(o) {
                            IntercityLine(o)
                            if (o.has("pickupKm")) {
                                Text(String.format("Yo'lovchigacha: ~%.1f km", o.optDouble("pickupKm")), fontSize = 13.sp, color = Muted)
                            }
                            BigButton("Qabul qilish") { post("/orders/${o.optInt("id")}/accept") }
                        }
                    }
                }
            }
            ErrorText(msg)
        }
    }
    offerOrder?.let { oo ->
        OfferCard(
            oo, (offerEnd - offerNow).coerceAtLeast(0L),
            onAccept = { val id = oo.optInt("id"); dismissed.add(id); closeOffer(); post("/orders/$id/accept") },
            onDecline = {
                val id = oo.optInt("id"); dismissed.add(id); closeOffer()
                scope.launch {
                    try {
                        val r = io { Api.call("POST", "/orders/$id/decline") }
                        msg = if (r.optInt("penalty") > 0) "Rad etildi: −" + r.optInt("penalty") + " ball" else ""
                    } catch (e: Exception) { msg = e.message ?: "Xato" }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
}
