package uz.taksi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Pair<Double, Double>? ni ekran aylanganda ham saqlab turish uchun. */
private val PairSaver = listSaver<Pair<Double, Double>?, Double>(
    save = { if (it == null) emptyList() else listOf(it.first, it.second) },
    restore = { if (it.size == 2) Pair(it[0], it[1]) else null },
)

private fun rk(p: Pair<Double, Double>?): String = if (p == null) "" else keyOf(p, 4)

/** Yandex Go uslubi: tepada katta xarita, pastda oq "sheet". */
@Composable
fun PassengerScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me by rememberMyLocation()

    var pickup by rememberSaveable(stateSaver = PairSaver) { mutableStateOf<Pair<Double, Double>?>(null) }
    var pickupName by rememberSaveable { mutableStateOf("") }
    var dest by rememberSaveable(stateSaver = PairSaver) { mutableStateOf<Pair<Double, Double>?>(null) }
    var destName by rememberSaveable { mutableStateOf("") }

    var field by remember { mutableStateOf("") }        // "" | "from" | "to": qaysi manzil tahrirlanmoqda
    var mapPick by remember { mutableStateOf(false) }   // xaritaga bosib tanlash rejimi
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(listOf<Place>()) }
    var searchedFor by remember { mutableStateOf("") }

    var centerTo by rememberSaveable(stateSaver = PairSaver) { mutableStateOf<Pair<Double, Double>?>(null) }
    var centerKey by remember { mutableStateOf(0) }

    var pay by rememberSaveable { mutableStateOf("cash") }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var quoteKey by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf(listOf<JSONObject>()) }
    var msg by remember { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf("Bosh sahifa") }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }

    ShareLocation(me)

    // GPS birinchi marta topilganda: "Qayerdan" avtomatik to'ladi
    LaunchedEffect(me != null) {
        val m = me
        if (m != null && pickup == null) {
            pickup = m
            pickupName = "Mening joyim"
            centerTo = m
            centerKey++
            val n = io { Nominatim.reverse(m.first, m.second) }
            if (n != null && pickup == m) pickupName = n
        }
    }

    // Manzil qidirish: ~700 ms kutib, so'ng so'raydi (Nominatim talabi)
    LaunchedEffect(query, field) {
        val q = query.trim()
        found = emptyList()
        searchedFor = ""
        if (field == "" || q.length < 3) return@LaunchedEffect
        delay(700)
        val r = io { Nominatim.search(q) }
        found = r
        searchedFor = q
    }

    LaunchedEffect(Unit) {
        try { profile = io { Api.call("GET", "/profile") } } catch (_: Exception) { }
    }

    // Buyurtmalar: har 3 soniyada
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

    // Safar davomida boshqa ilovaga (masalan, Navigator) o'tsangiz ham joylashuv yuborilib turadi
    TripLocationService(
        running = active != null && active.optString("status") != "searching",
        text = "Safar davom etmoqda — joylashuv yuborilmoqda",
    )

    // ---- Marshrut ----
    val aSt = active?.optString("status") ?: ""
    val aId = active?.optInt("id") ?: 0
    val aFrom = active?.let { Pair(it.optDouble("fromLat"), it.optDouble("fromLng")) }
    val aTo = active?.let { Pair(it.optDouble("toLat"), it.optDouble("toLng")) }
    val dLoc = active?.let { locOf(it, "driverLoc") }

    val routeFrom: Pair<Double, Double>? = when {
        active == null -> pickup
        aSt == "searching" -> aFrom
        aSt == "accepted" -> dLoc
        aSt == "started" -> dLoc ?: me
        else -> null
    }
    val routeTo: Pair<Double, Double>? = when {
        active == null -> dest
        aSt == "searching" || aSt == "started" -> aTo
        aSt == "accepted" -> aFrom
        else -> null
    }
    val route = rememberRoute(routeFrom, routeTo, if (active == null) 4 else 3)
    val fitKey: Any = if (active == null) "trip:${rk(pickup)}:${rk(dest)}" else "order:$aId:$aSt"

    // ---- Narx: serverdan (km — OSRM'dan, server tekshiradi) ----
    val pk = rk(pickup)
    val dk = rk(dest)
    val routeKm = route?.km
    LaunchedEffect(pk, dk, routeKm, active == null) {
        val a = pickup
        val b = dest
        if (active == null && a != null && b != null) {
            try {
                var path = "/estimate?flat=${a.first}&flng=${a.second}&tlat=${b.first}&tlng=${b.second}"
                if (routeKm != null) path += "&km=" + String.format(java.util.Locale.US, "%.2f", routeKm)
                val q = io { Api.call("GET", path) }
                quote = q
                quoteKey = "$pk|$dk"
            } catch (e: Exception) { msg = e.message ?: "Xato" }
        }
    }

    // ---- Tanlash yordamchilari ----
    fun choose(p: Pair<Double, Double>, name: String) {
        if (field == "from") { pickup = p; pickupName = name } else { dest = p; destName = name }
        field = ""; mapPick = false; query = ""; found = emptyList()
        centerTo = p; centerKey++
    }

    val onMapPick: (Double, Double) -> Unit = { lat, lng ->
        val f = field
        val p = Pair(lat, lng)
        choose(p, "Xaritadagi nuqta")
        scope.launch {
            val n = io { Nominatim.reverse(lat, lng) }
            if (n != null) {
                if (f == "from" && pickup == p) pickupName = n
                if (f == "to" && dest == p) destName = n
            }
        }
    }

    val pins = buildList {
        if (active == null) {
            pickup?.let { add(Pin(it.first, it.second, "Qayerdan", GREEN)) }
            dest?.let { add(Pin(it.first, it.second, "Qayerga", RED)) }
        } else {
            aFrom?.let { add(Pin(it.first, it.second, "Olinadigan joy", GREEN)) }
            aTo?.let { add(Pin(it.first, it.second, "Borish manzili", RED)) }
            dLoc?.let { add(Pin(it.first, it.second, "Haydovchi", ORANGE)) }
        }
        me?.let { add(Pin(it.first, it.second, "Men", BLUE)) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapBox(
                pins = pins,
                center = centerTo ?: aFrom ?: DEFAULT_CENTER,
                modifier = Modifier.fillMaxSize(),
                onTap = if (active == null && mapPick) onMapPick else null,
                route = route?.points,
                fitKey = fitKey,
                centerKey = centerKey,
                onMapTap = { /* Pastki navigatsiya xarita ustida ham doim ko'rinadi. */ },
            )
            FloatingChip("✕ Chiqish", Modifier.align(Alignment.TopStart).padding(12.dp), onLogout)
            FloatingChip(tr("📍 Mening joyim", "📍 Моё местоположение", "📍 My location"), Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                val m = me
                if (m != null) { centerTo = m; centerKey++ }
            }
        }

        Sheet {
            if (selectedTab == "Buyurtmalar") {
                BigLine("Buyurtmalarim", "Safarlaringiz tarixi va holati")
                if (orders.isEmpty()) {
                    Panel {
                        Text(tr("Hozircha buyurtmalar yo'q", "Пока нет заказов", "No orders yet"), fontWeight = FontWeight.Bold)
                        Text(tr("Birinchi safaringizni rejalashtiring.", "Запланируйте свою первую поездку.", "Plan your first trip."), fontSize = 13.sp, color = Muted)
                    }
                } else {
                    orders.sortedByDescending { it.optLong("createdAt") }.forEach { order ->
                        OrderCard(order)
                    }
                }
            } else if (selectedTab == "Kabinet") {
                val passengerName = profile?.optString("name").orEmpty().ifBlank { "Yo'lovchi" }
                val passengerPhone = profile?.optString("phone").orEmpty().ifBlank { "Telefon raqami mavjud emas" }
                Panel(color = Ink) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(58.dp).background(Yellow, CircleShape), contentAlignment = Alignment.Center) {
                            Text(passengerName.firstOrNull()?.uppercase() ?: "Y", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(passengerName, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("TOXA yo'lovchisi", fontSize = 12.sp, color = Color.White.copy(alpha = 0.68f))
                        }
                    }
                }
                BigLine("Shaxsiy ma'lumotlar", "Hisobingizga tegishli ma'lumotlar")
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Telefon raqami", "Номер телефона", "Phone number"), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(passengerPhone, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Hisob turi", "Тип аккаунта", "Account type"), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Yo'lovchi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                LanguageSelector()
                BigButton(tr("⚙  Sozlamalar", "⚙  Настройки", "⚙  Settings")) { selectedTab = "Sozlamalar" }
                DangerButton("Hisobdan chiqish") { onLogout() }
            } else if (selectedTab == "Sozlamalar") {
                BigLine(tr("Sozlamalar", "Настройки", "Settings"), tr("Kabinet va hisob ma'lumotlari", "Кабинет и данные аккаунта", "Account and profile details"))
                SettingsPreferencesContent()
                AccountSettingsContent(
                    name = profile?.optString("name").orEmpty(),
                    phone = profile?.optString("phone").orEmpty(),
                    role = "Yo'lovchi",
                    onRefresh = {
                        scope.launch {
                            try {
                                profile = io { Api.call("GET", "/profile") }
                                msg = "Hisob ma'lumotlari yangilandi"
                            } catch (e: Exception) { msg = e.message ?: "Ma'lumotlarni yangilab bo'lmadi" }
                        }
                    },
                )
                GhostButton("‹ Kabinetga qaytish") { selectedTab = "Kabinet" }
            } else if (selectedTab == "Qo'llab-quvvatlash") {
                BigLine(tr("Qo'llab-quvvatlash", "Поддержка", "Support"), tr("TOXA yordam markazi", "Центр помощи TOXA", "TOXA Help Center"))
                Text(tr("Ko'p uchraydigan savollarga javoblar", "Ответы на частые вопросы", "Frequently asked questions"), fontSize = 13.sp, color = Muted)
                SupportFaq()
                Text(tr("Bevosita operator bilan aloqa kanali hali sozlanmagan.", "Связь с оператором пока не настроена.", "Direct contact with support is not set up yet."), fontSize = 12.sp, color = Muted)
            } else if (active != null) {
                // ───── Faol buyurtma ─────
                val (bg, fg) = statusColors(aSt)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill(statusText(aSt), bg, fg)
                    Box(Modifier.weight(1f))
                    Text(som(active.optLong("price")), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                }
                val mins = route?.min
                when (aSt) {
                    "searching" -> BigLine(
                        "Haydovchi qidirilmoqda…",
                        if (mins != null) "Safar ≈ $mins daq · ${String.format("%.1f", active.optDouble("km"))} km" else "",
                    )
                    "accepted" -> BigLine(
                        if (mins != null) "Haydovchi ~$mins daqiqada keladi" else "Haydovchi yo'lda",
                        if (dLoc == null) "Haydovchi joylashuvi kutilmoqda…" else "",
                    )
                    "arrived" -> BigLine("Haydovchi sizni kutmoqda", "Mashinaga o'tiring")
                    "started" -> BigLine(
                        if (mins != null) "Manzilga ~$mins daqiqa" else "Safar davom etmoqda",
                        "Yaxshi yo'l!",
                    )
                }
                Route(active.optString("from"), active.optString("to"))
                Text(
                    String.format("%.1f km  ·  %s", active.optDouble("km"), payText(active)),
                    fontSize = 13.sp, color = Muted,
                )
                val d = active.optJSONObject("driver")
                if (d != null) PersonRow("Haydovchi", d.optString("name"), d.optString("phone"))
                if (aSt != "started") {
                    DangerButton("Buyurtmani bekor qilish") {
                        scope.launch {
                            try { io { Api.call("POST", "/orders/$aId/cancel", JSONObject()) } }
                            catch (e: Exception) { msg = e.message ?: "Xato" }
                        }
                    }
                }
            } else if (field != "") {
                // ───── Manzil tanlash ─────
                val isFrom = field == "from"
                if (mapPick) {
                    BigLine(
                        if (isFrom) "Olinadigan joyni belgilang" else "Borish manzilini belgilang",
                        "Xaritaga bosing",
                    )
                    GhostButton("Qidiruvga qaytish") { mapPick = false }
                } else {
                    val fr = remember { FocusRequester() }
                    LaunchedEffect(field) {
                        delay(150)
                        try { fr.requestFocus() } catch (e: Exception) { }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(query, { query = it }, if (isFrom) "Qayerdan" else "Qayerga", Modifier.weight(1f).focusRequester(fr))
                        TextButton(onClick = { field = ""; query = ""; found = emptyList() }) { Text("Bekor", color = Muted) }
                    }
                    val m = me
                    if (isFrom && m != null) {
                        SuggestionRow("📍", "Mening joyim", "GPS bo'yicha") { choose(m, "Mening joyim") }
                    }
                    SuggestionRow("🗺", "Xaritadan tanlash", "Xaritaga bosib belgilang") { mapPick = true }

                    val q = query.trim()
                    if (q.length < 3) {
                        val recent = orders.filter { it.optString("status") == "done" }.distinctBy { it.optString("to") }.take(4)
                        recent.forEach { o ->
                            SuggestionRow("🕘", o.optString("to"), "Oldingi safar") {
                                choose(Pair(o.optDouble("toLat"), o.optDouble("toLng")), o.optString("to"))
                            }
                        }
                    } else if (searchedFor != q) {
                        Text("Qidirilmoqda…", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(4.dp))
                    } else if (found.isEmpty()) {
                        Text("Topilmadi. Boshqacha yozing yoki xaritadan tanlang.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(4.dp))
                    } else {
                        found.forEach { p -> SuggestionRow("📍", p.name, p.sub) { choose(Pair(p.lat, p.lng), p.name) } }
                    }
                }
            } else {
                // ───── Buyurtma tuzish ─────
                if (needPay != null) {
                    BigLine(
                        tr("Safar yakunlandi", "Поездка завершена", "Trip completed"),
                        tr("To'lov: ${som(needPay.optLong("price"))}", "К оплате: ${som(needPay.optLong("price"))}", "Amount due: ${som(needPay.optLong("price"))}"),
                    )
                    Text(
                        tr("Karta to'lovi provayderi hali ulanmagan. Iltimos, hozircha naqd to'lovni tanlang.", "Платёжный провайдер для карт ещё не подключён. Пока выберите оплату наличными.", "Card payments are not connected yet. Please choose cash payment for now."),
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Safaringizni rejalashtiring",
                        fontSize = 21.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        color = Ink,
                    )
                    Text(
                        "Qayerdan va qayerga borishingizni tanlang",
                        fontSize = 13.sp,
                        color = Muted,
                    )
                }

                AddressRow(Ok, "Qayerdan", pickupName.ifBlank { "Joylashuv aniqlanmoqda…" }, pickupName.isBlank()) {
                    field = "from"; query = ""; mapPick = false
                }
                AddressRow(Bad, "Qayerga", destName.ifBlank { "Qayerga boramiz?" }, destName.isBlank()) {
                    field = "to"; query = ""; mapPick = false
                }

                val a = pickup
                val b = dest
                if (a != null && b != null) {
                    val q = quote?.takeIf { quoteKey == "$pk|$dk" }
                    if (q == null) {
                        Text("Narx hisoblanmoqda…", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(4.dp))
                    } else {
                        val km = q.optDouble("km")
                        val mins = route?.min ?: maxOf(1, Math.round(km * 2.5).toInt())
                        TariffRow(q.optLong("price"), mins, km)
                        Segmented(listOf("cash" to tr("💵 Naqd", "💵 Наличные", "💵 Cash")), "cash") { pay = "cash" }
                        Text(tr("Karta orqali to'lov hozircha mavjud emas.", "Оплата картой пока недоступна.", "Card payment is not available yet."), fontSize = 12.sp, color = Muted)
                        BigButton("Taksi chaqirish", enabled = !busy) {
                            scope.launch {
                                busy = true
                                try {
                                    val body = JSONObject()
                                        .put("from", pickupName.ifBlank { "Xaritadagi nuqta" })
                                        .put("to", destName.ifBlank { "Xaritadagi nuqta" })
                                        .put("fromLat", a.first).put("fromLng", a.second)
                                        .put("toLat", b.first).put("toLng", b.second)
                                        .put("payMethod", "cash")
                                    if (routeKm != null) body.put("km", routeKm)
                                    io { Api.call("POST", "/orders", body) }
                                    dest = null; destName = ""; msg = ""
                                } catch (e: Exception) { msg = e.message ?: "Xato" }
                                busy = false
                            }
                        }
                    }
                } else if (b == null) {
                    val recent = orders.filter { it.optString("status") == "done" }.distinctBy { it.optString("to") }.take(3)
                    if (recent.isNotEmpty()) {
                        Text("Oldingi manzillar", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 4.dp))
                        recent.forEach { o ->
                            SuggestionRow("🕘", o.optString("to"), "Oldingi safar") {
                                dest = Pair(o.optDouble("toLat"), o.optDouble("toLng"))
                                destName = o.optString("to")
                                centerTo = dest; centerKey++
                            }
                        }
                    }
                }
            }
            ErrorText(msg)
        }
        PassengerBottomNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
    }
}

@Composable
private fun PassengerBottomNavigation(selectedTab: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("Bosh sahifa", "⌂", tr("Bosh sahifa", "Главная", "Home")),
        Triple("Buyurtmalar", "▤", tr("Buyurtmalar", "Заказы", "Orders")),
        Triple("Kabinet", "♙", tr("Kabinet", "Кабинет", "Account")),
        Triple("Qo'llab-quvvatlash", "◉", tr("Yordam", "Помощь", "Support")),
    )
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = CardBg,
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val active = selectedTab == item.first || (selectedTab == "Sozlamalar" && item.first == "Kabinet")
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) { if (AppPreferences.darkMode) Color(0xFF4A3A0A) else Color(0xFFFFF5D8) } else Color.Transparent)
                        .clickable { onSelect(item.first) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(item.second, fontSize = 23.sp, color = if (active) Ink else Muted, fontWeight = FontWeight.Bold)
                    Text(item.third, fontSize = 10.sp, color = if (active) Ink else Muted, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    if (active) Box(Modifier.padding(top = 3.dp).size(width = 20.dp, height = 3.dp).background(Yellow, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
