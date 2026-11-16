package uz.taksi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val scope = rememberCoroutineScope()
    val me by rememberMyLocation()
    var centerTo by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var centerKey by remember { mutableStateOf(0) }
    var available by remember { mutableStateOf(listOf<JSONObject>()) }
    var mine by remember { mutableStateOf(listOf<JSONObject>()) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var selectedTab by remember { mutableStateOf("Bosh sahifa") }
    var online by remember { mutableStateOf(false) }
    var shiftSlider by remember { mutableStateOf(0f) }
    var shiftDragging by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    // Bosh ekran ixcham panel bilan ochiladi; xaritaga bosilganda vaqtincha faqat xarita qoladi.
    var sheetExpanded by remember { mutableStateOf(false) }
    var mapOnlyMode by remember { mutableStateOf(false) }
    var mapOnlyTap by remember { mutableStateOf(0) }
    var showOfflineConfirm by remember { mutableStateOf(false) }
    var previousAvailableIds by remember { mutableStateOf<Set<Int>?>(null) }

    LaunchedEffect(mapOnlyTap) {
        if (mapOnlyTap > 0) {
            mapOnlyMode = true
            delay(3500)
            mapOnlyMode = false
        }
    }

    val appContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(available, online) {
        val currentIds = available.map { it.optInt("id") }.toSet()
        val previousIds = previousAvailableIds
        if (previousIds != null && online && currentIds.any { it !in previousIds }) {
            if (AppPreferences.orderSound) {
                try {
                    val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                    tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 350)
                    delay(400)
                    tone.release()
                } catch (_: Exception) { }
            }
            if (AppPreferences.vibration) {
                try {
                    val vibrator = androidx.core.content.ContextCompat.getSystemService(appContext, android.os.Vibrator::class.java)
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(250L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) { }
            }
        }
        previousAvailableIds = currentIds
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
                val p = io { Api.call("GET", "/profile") }
                if (p.toString() != profile?.toString()) profile = p
                online = s.optBoolean("online", false)
                if (!shiftDragging) shiftSlider = if (online) 1f else 0f
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

    fun setAvailability(value: Boolean) {
        scope.launch {
            try {
                io { Api.call("POST", "/driver/availability", JSONObject().put("online", value)) }
                online = value
                shiftSlider = if (value) 1f else 0f
                if (value) {
                    // Ishga chiqqach haydovchi panelini yopib, xaritani asosiy ko'rinishda qoldiramiz.
                    selectedTab = "Bosh sahifa"
                    sheetExpanded = false
                    mapOnlyMode = false
                }
                msg = ""
            } catch (e: Exception) {
                shiftSlider = if (online) 1f else 0f
                msg = e.message ?: "Ish holatini o'zgartirib bo'lmadi"
            }
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
            Box(Modifier.fillMaxSize()) {
            MapBox(
                pins = pins,
                center = centerTo ?: pickup ?: DEFAULT_CENTER,
                modifier = Modifier.fillMaxSize(),
                route = route?.points,
                fitKey = fitKey,
                centerKey = centerKey,
                onMapTap = { mapOnlyTap++ },
            )
            if (!mapOnlyMode) {
                if (online) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showOfflineConfirm = true },
                        color = Color(0xFF20252D),
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("⏻", color = Color(0xFFFF6B6B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(tr("OFFLINE", "ОФЛАЙН", "OFFLINE"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    FloatingChip(
                        "TOXA  •  HAYDOVCHI",
                        Modifier.align(Alignment.TopStart).padding(12.dp),
                    ) {}
                }
                FloatingChip(tr("📍 Mening joyim", "📍 Моё местоположение", "📍 My location"), Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                    val m = me
                    if (m != null) { centerTo = m; centerKey++ }
                }
            }
            }

            if (!mapOnlyMode && sheetExpanded) Sheet(Modifier.align(Alignment.BottomCenter)) {
            if (selectedTab != "Bosh sahifa") {
                BigLine(when (selectedTab) { "Buyurtmalar" -> tr("Buyurtmalar", "Заказы", "Orders"); "Kabinet" -> tr("Kabinet", "Кабинет", "Account"); "Sozlamalar" -> tr("Sozlamalar", "Настройки", "Settings"); else -> tr("Qo'llab-quvvatlash", "Поддержка", "Support") }, tr("Kerakli bo'lim", "Нужный раздел", "Selected section"))
                when (selectedTab) {
                    "Buyurtmalar" -> {
                        stats?.let { data -> Panel(color = Ink) {
                            Text(tr("BUGUNGI DAROMAD", "ДОХОД ЗА СЕГОДНЯ", "TODAY EARNINGS"), fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
                            Text(som(data.optLong("todaySum")), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Yellow)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tr("Buyurtmalar: ", "Заказы: ", "Orders: ") + data.optInt("todayCount"), color = Color.White, fontSize = 12.sp)
                                Text(tr("Reyting: ", "Рейтинг: ", "Rating: ") + if (data.optInt("ratingCount") > 0) String.format("%.1f ★", data.optDouble("rating")) else "—", color = Color.White, fontSize = 12.sp)
                            }
                            Text(tr("Jami daromad: ", "Общий доход: ", "Total earnings: ") + som(data.optLong("totalSum")), color = Color.White, fontSize = 12.sp)
                        } } ?: Panel { Text(tr("Hisobot yuklanmoqda…", "Загрузка отчёта…", "Loading report…"), color = Muted) }
                        BigLine(tr("Buyurtmalar", "Заказы", "Orders"), tr("Faol va yangi buyurtmalar", "Активные и новые заказы", "Active and new orders"))
                        if (active != null) Panel { Text(tr("Sizda faol buyurtma mavjud.", "У вас есть активный заказ.", "You have an active order."), fontWeight = FontWeight.Bold) }
                        Text(tr("Yangi buyurtmalar: ", "Новые заказы: ", "New orders: ") + available.size, color = Muted)
                        available.forEach { order -> OrderCard(order) { BigButton(tr("Qabul qilish", "Принять", "Accept")) { post("/orders/" + order.optInt("id") + "/accept") } } }
                    }
                    "Kabinet" -> {
                        val driverName = profile?.optString("name").orEmpty().ifBlank { "Haydovchi" }
                        val driverPhone = profile?.optString("phone").orEmpty().ifBlank { "Telefon raqami mavjud emas" }
                        Panel(color = Ink) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(
                                    Modifier.size(58.dp).background(Yellow, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        driverName.firstOrNull()?.uppercase() ?: "H",
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Ink,
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(driverName, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Text(tr("TOXA haydovchisi", "Водитель TOXA", "TOXA driver"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.68f))
                                }
                                Pill(
                                    if (online) tr("ONLAYN", "ОНЛАЙН", "ONLINE") else tr("OFFLAYN", "ОФЛАЙН", "OFFLINE"),
                                    if (online) Color(0xFF173D2C) else Color(0xFF30343C),
                                    if (online) Color(0xFF7BE0A2) else Color.White.copy(alpha = 0.78f),
                                )
                            }
                        }
                        BigLine(tr("Shaxsiy ma'lumotlar", "Личные данные", "Personal details"), tr("Hisobingizga tegishli ma'lumotlar", "Данные вашего аккаунта", "Your account information"))
                        Panel {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Telefon raqami", "Номер телефона", "Phone number"), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(driverPhone, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Hisob turi", "Тип аккаунта", "Account type"), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(tr("Haydovchi", "Водитель", "Driver"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Ish holati", "Статус работы", "Work status"), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(if (online) tr("Ishda", "На линии", "Online") else tr("Dam olishda", "Не на линии", "Offline"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    color = if (online) Color(0xFF237A4B) else Muted)
                            }
                        }
                        LanguageSelector()
                        BigButton(tr("⚙  Sozlamalar", "⚙  Настройки", "⚙  Settings")) { selectedTab = "Sozlamalar" }
                        DangerButton(tr("Hisobdan chiqish", "Выйти из аккаунта", "Sign out")) { onLogout() }
                    }
                    "Sozlamalar" -> {
                        BigLine(tr("Sozlamalar", "Настройки", "Settings"), tr("Kabinet va hisob ma'lumotlari", "Кабинет и данные аккаунта", "Account and profile details"))
                        SettingsPreferencesContent()
                        AccountSettingsContent(
                            name = profile?.optString("name").orEmpty(),
                            phone = profile?.optString("phone").orEmpty(),
                            role = "Haydovchi",
                            onRefresh = {
                                scope.launch {
                                    try {
                                        profile = io { Api.call("GET", "/profile") }
                                        msg = "Hisob ma'lumotlari yangilandi"
                                    } catch (e: Exception) { msg = e.message ?: "Ma'lumotlarni yangilab bo'lmadi" }
                                }
                            },
                        )
                        GhostButton(tr("‹ Kabinetga qaytish", "‹ Вернуться в кабинет", "‹ Back to account")) { selectedTab = "Kabinet" }
                    }
                    "Qo'llab-quvvatlash" -> {
                        BigLine(tr("Qo'llab-quvvatlash", "Поддержка", "Support"), tr("TOXA yordam markazi", "Центр помощи TOXA", "TOXA Help Center"))
                        Text(tr("Ko'p uchraydigan savollarga javoblar", "Ответы на частые вопросы", "Frequently asked questions"), fontSize = 13.sp, color = Muted)
                        SupportFaq()
                        Text(tr("Bevosita operator bilan aloqa kanali hali sozlanmagan.", "Связь с оператором пока не настроена.", "Direct contact with support is not set up yet."), fontSize = 12.sp, color = Muted)
                    }
                }
            } else if (active != null && pickup != null && dropoff != null) {
                val isCash = active.optString("payMethod") == "cash"
                val (bg, fg) = statusColors(st)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill(statusText(st), bg, fg)
                    Box(Modifier.weight(1f))
                    Text(som(active.optLong("price")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                val what = if (st == "started") tr("Manzilgacha", "До пункта назначения", "To destination") else tr("Yo'lovchigacha", "До пассажира", "To passenger")
                if (st == "arrived") {
                    BigLine(tr("Yo'lovchini kutmoqdasiz", "Ожидайте пассажира", "Waiting for passenger"), tr("Yo'lovchi chiqqach, safarni boshlang", "Начните поездку, когда пассажир сядет в машину", "Start the trip when the passenger is in the car"))
                } else if (route != null) {
                    BigLine(tr("$what ${route.min} daq", "$what ${route.min} мин", "$what ${route.min} min"), String.format("%.1f km", route.km))
                } else {
                    BigLine("$what marshrut hisoblanmoqda…", if (me == null) tr("GPS kutilmoqda", "Ожидание GPS", "Waiting for GPS") else "")
                }

                Route(active.optString("from"), active.optString("to"))
                Text(
                    String.format("%.1f km  ·  %s", active.optDouble("km"), payText(active)),
                    fontSize = 13.sp, color = Muted,
                )
                val p = active.optJSONObject("passenger")
                if (p != null) PersonRow(tr("Yo'lovchi", "Пассажир", "Passenger"), p.optString("name"), p.optString("phone"))
                if (!isCash) Text(tr("Karta to'lovi provayderi ulanmagan.", "Платёжный провайдер для карт не подключён.", "Card payment provider is not connected."), fontSize = 12.sp, color = Muted)

                val next = mapOf(
                    "accepted" to ("arrived" to tr("Yetib keldim", "Я прибыл", "I have arrived")),
                    "arrived" to ("started" to tr("Safarni boshlash", "Начать поездку", "Start trip")),
                    "started" to ("done" to (if (isCash) tr("Yakunlash (naqd oldim)", "Завершить (получил наличные)", "Finish (cash received)") else tr("Yakunlash", "Завершить", "Finish"))),
                )[st]
                if (next != null) {
                    BigButton(next.second) { post("/orders/$id/status", JSONObject().put("status", next.first)) }
                }

                if (st == "accepted" || st == "arrived") {
                    DangerButton(tr("Buyurtmani rad etish", "Отклонить заказ", "Decline order")) { post("/orders/$id/cancel") }
                }
            } else {
                Panel(color = Ink) {
                    Row(Modifier.fillMaxWidth().clickable { sheetExpanded = false }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(46.dp).background(Color(0xFF292E37), CircleShape), contentAlignment = Alignment.Center) {
                            Text("T", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Yellow)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(tr("HAYDOVCHI PANELI", "ПАНЕЛЬ ВОДИТЕЛЯ", "DRIVER PANEL"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.58f), letterSpacing = 1.1.sp)
                            Text(if (online) tr("Yo'lda bo'lishga tayyorsiz", "Вы готовы принимать заказы", "You are ready for trips") else tr("Bugun ishni boshlang", "Начните работу сегодня", "Start working today"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Pill(if (online) tr("ONLAYN", "ОНЛАЙН", "ONLINE") else tr("OFFLAYN", "ОФЛАЙН", "OFFLINE"), if (online) Color(0xFF173D2C) else Color(0xFF30343C), if (online) Color(0xFF7BE0A2) else Color.White.copy(alpha = 0.78f))
                    }
                    Text(if (online) tr("Yangi buyurtmalarni qabul qilyapsiz", "Вы принимаете новые заказы", "You are receiving new orders") else tr("Buyurtmalarni ko'rish uchun ishga chiqing", "Выйдите на линию, чтобы видеть заказы", "Go online to see orders"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.68f))
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().padding(top = 2.dp).height(66.dp)
                            .background(Color(0xFF252A32), RoundedCornerShape(22.dp))
                            .pointerInput(online) {
                                var dragValue = shiftSlider
                                detectHorizontalDragGestures(
                                    onDragStart = { shiftDragging = true; dragValue = shiftSlider },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        dragValue = (dragValue + amount / size.width.toFloat()).coerceIn(0f, 1f)
                                        shiftSlider = dragValue
                                    },
                                    onDragEnd = { shiftDragging = false; setAvailability(dragValue >= 0.5f) },
                                    onDragCancel = { shiftDragging = false; shiftSlider = if (online) 1f else 0f },
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(Modifier.fillMaxWidth(shiftSlider.coerceAtLeast(0.16f)).height(66.dp)
                            .background(if (online) Color(0xFF237A4B) else Color(0xFF3A414D), RoundedCornerShape(22.dp)))
                        Text(
                            if (online) tr("CHAPGA SURING  •  ISHDAN CHIQISH", "СДВИНЬТЕ ВЛЕВО  •  ВЫЙТИ С ЛИНИИ", "SWIPE LEFT  •  GO OFFLINE") else tr("O'NGGA SURING  •  ISHGA CHIQISH", "СДВИНЬТЕ ВПРАВО  •  ВЫЙТИ НА ЛИНИЮ", "SWIPE RIGHT  •  GO ONLINE"),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 62.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 0.25.sp,
                        )
                        Box(Modifier.offset(x = (maxWidth - 62.dp) * shiftSlider).size(62.dp).padding(5.dp).background(Yellow, CircleShape), contentAlignment = Alignment.Center) {
                            Text("➜", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DriverQuickStat(tr("BUGUN", "СЕГОДНЯ", "TODAY"), stats?.let { som(it.optLong("todaySum")) } ?: "—", tr("Daromad", "Доход", "Earnings"), Modifier.weight(1f))
                    DriverQuickStat(tr("BUYURTMA", "ЗАКАЗЫ", "ORDERS"), stats?.optInt("todayCount")?.toString() ?: "—", tr("Bugun", "Сегодня", "Today"), Modifier.weight(1f))
                    DriverQuickStat(tr("REYTING", "РЕЙТИНГ", "RATING"), stats?.let { if (it.optInt("ratingCount") > 0) String.format("%.1f ★", it.optDouble("rating")) else "—" } ?: "—", tr("Baholash", "Оценка", "Rating"), Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth().clickable { sheetExpanded = false }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("Yangi buyurtmalar", "Новые заказы", "New orders"), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        Text(if (available.isEmpty()) tr("Yangi takliflar kutilmoqda", "Ожидаются новые предложения", "Waiting for new offers") else tr("Sizga ${available.size} ta taklif bor", "Для вас предложений: ${available.size}", "You have ${available.size} offers"), fontSize = 12.sp, color = Muted)
                    }
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF3CD)).padding(horizontal = 11.dp, vertical = 7.dp)) {
                        Text(tr("${available.size} ta", "${available.size} шт.", "${available.size}"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
                if (available.isEmpty()) {
                    Panel {
                        Box(Modifier.size(44.dp).background(Color(0xFFFFF5D8), CircleShape), contentAlignment = Alignment.Center) {
                            Text("⌛", fontSize = 22.sp)
                        }
                        Text(tr("Hozircha buyurtma yo'q", "Пока нет заказов", "No orders yet"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(tr("Onlayn holatda turing — yangi buyurtmalar shu yerda ko'rinadi.", "Оставайтесь онлайн — новые заказы появятся здесь.", "Stay online — new orders will appear here."), fontSize = 13.sp, color = Muted)
                    }
                }
                available.forEach { o ->
                    OrderCard(o) {
                        if (o.has("pickupKm")) {
                            Text(tr(String.format("Yo'lovchigacha: ~%.1f km", o.optDouble("pickupKm")), String.format("До пассажира: ~%.1f км", o.optDouble("pickupKm")), String.format("To passenger: ~%.1f km", o.optDouble("pickupKm"))), fontSize = 13.sp, color = Muted)
                        }
                        BigButton(tr("Qabul qilish", "Принять", "Accept")) { post("/orders/${o.optInt("id")}/accept") }
                    }
                }

                
            }
            ErrorText(msg)
            }
            else if (!online && !mapOnlyMode) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clickable { sheetExpanded = true },
                    shape = RoundedCornerShape(22.dp), color = Ink, shadowElevation = 12.dp,
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).background(Yellow, CircleShape), contentAlignment = Alignment.Center) {
                            Text("T", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Ink)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(tr("HAYDOVCHI PANELI", "ПАНЕЛЬ ВОДИТЕЛЯ", "DRIVER PANEL"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.65f))
                            Text("Yangi buyurtmalar: ${available.size} ta  ·  ${if (online) tr("ONLAYN", "ОНЛАЙН", "ONLINE") else tr("OFFLAYN", "ОФЛАЙН", "OFFLINE")}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("⌃", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Yellow)
                    }
                }
            }
        }
        // Pastki navigatsiya doim ko'rinadi; xaritaga bosish faqat haydovchi panelini yashiradi.
        DriverBottomNavigation(selectedTab = selectedTab, onSelect = {
            selectedTab = it
            // Online holatda Bosh sahifa tanlansa, xarita ochiq qoladi; boshqa bo'limlar panelni ochadi.
            sheetExpanded = it != "Bosh sahifa" || !online
            mapOnlyMode = false
        })
    }

    if (showOfflineConfirm) {
        AlertDialog(
            onDismissRequest = { showOfflineConfirm = false },
            title = { Text(tr("Ishdan chiqasizmi?", "Выйти из сети?", "Go offline?"), fontWeight = FontWeight.Bold) },
            text = { Text(tr("OFFLINE holatiga o'tsangiz, yangi buyurtmalar kelmaydi.", "После перехода в режим OFFLINE новые заказы поступать не будут.", "When you go OFFLINE, you will no longer receive new orders.")) },
            confirmButton = {
                TextButton(onClick = {
                    showOfflineConfirm = false
                    setAvailability(false)
                }) { Text(tr("Ha, ishdan chiqish", "Да, выйти", "Yes, go offline"), color = Color(0xFFC62828), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineConfirm = false }) { Text(tr("Bekor qilish", "Отмена", "Cancel")) }
            },
        )
    }
}


@Composable
private fun DriverQuickStat(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = CardBg, shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 0.55.sp, maxLines = 1)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1)
            Text(detail, fontSize = 10.sp, color = Muted, maxLines = 1)
        }
    }
}

@Composable
private fun DriverBottomNavigation(selectedTab: String, onSelect: (String) -> Unit) {
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
                    if (active) {
                        Box(Modifier.padding(top = 3.dp).size(width = 20.dp, height = 3.dp).background(Yellow, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}
