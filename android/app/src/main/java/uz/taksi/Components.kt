package uz.taksi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pastdagi oq yumaloq "sheet": ichida kontent aylantiriladi, balandligi ekranning 62% idan oshmaydi. */
@Composable
fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = maxH),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = CardBg,
        shadowElevation = 16.dp,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Hairline),
            )
            content()
        }
    }
}

/** Xarita ustidagi suzuvchi oq tugma. */
@Composable
fun FloatingChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = CardBg, shadowElevation = 6.dp) {
        Box(
            Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

/** "Qayerdan / Qayerga" qatori: rangli nuqta, kichik yorliq va manzil. */
@Composable
fun AddressRow(dot: Color, label: String, value: String, isPlaceholder: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (AppPreferences.darkMode) Color(0xFF252B35) else Color(0xFFF8F9FB),
        border = BorderStroke(1.dp, Hairline),
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(
                value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (isPlaceholder) Muted else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        }
    }
}

/** Takliflar ro'yxatidagi qator. */
@Composable
fun SuggestionRow(icon: String, title: String, sub: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (AppPreferences.darkMode) Color(0xFF303744) else Color(0xFFF3F4F6)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tarif kartasi: narx va (≈ vaqt · km). */
@Composable
fun TariffRow(price: Long, mins: Int, km: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (AppPreferences.darkMode) Color(0xFF3A321F) else Color(0xFFFFF8E8),
        border = BorderStroke(1.dp, if (AppPreferences.darkMode) Color(0xFF66552B) else Color(0xFFF2E4B8)),
        shadowElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(Modifier.weight(1f)) {
            Text("Narx", fontSize = 12.sp, color = Muted)
            Text(som(price), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("≈ $mins daq", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.1f km", km), fontSize = 13.sp, color = Muted)
        }
        }
    }
}

/** Katta sarlavha satri (masalan, "Haydovchi ~5 daqiqada keladi"). */
@Composable
fun BigLine(text: String, sub: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Muted)
    }
}


/** Kabinet ichidagi hisob sozlamalari. Ma'lumotlar serverdan kelgan holicha ko'rsatiladi. */
@Composable
fun AccountSettingsContent(name: String, phone: String, role: String, onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Hairline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Hisob ma'lumotlari", "Данные аккаунта", "Account details"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            listOf(tr("Ism", "Имя", "Name") to name, tr("Telefon", "Телефон", "Phone") to phone, tr("Hisob turi", "Тип аккаунта", "Account type") to role).forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.first, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(item.second.ifBlank { tr("Ko'rsatilmagan", "Не указано", "Not provided") }, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(tr("Telefon raqamini o'zgartirish hozircha ilova ichida mavjud emas. Hisobga kirish SMS tasdiqlash orqali amalga oshiriladi.", "Изменение номера телефона пока недоступно в приложении. Вход выполняется по SMS-коду.", "Changing the phone number is not yet available in the app. Sign-in uses SMS verification."), fontSize = 12.sp, color = Muted)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRefresh),
                shape = RoundedCornerShape(13.dp),
                color = if (AppPreferences.darkMode) Color(0xFF3A321F) else Color(0xFFFFF3D1),
            ) {
                Text(tr("↻  Ma'lumotlarni yangilash", "↻  Обновить данные", "↻  Refresh details"), Modifier.padding(13.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }
    }
}


/** Ilova ko'rinishi, bildirishnoma afzalliklari va maxfiylik boshqaruvi. */
@Composable
fun SettingsPreferencesContent() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Ko'rinish", "Внешний вид", "Appearance"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(tr("Rang rejimi", "Цветовая тема", "Color theme"), fontSize = 13.sp, color = Muted)
                Segmented(
                    listOf("light" to tr("Kunduzgi", "Светлая", "Light"), "dark" to tr("Tungi", "Тёмная", "Dark")),
                    if (AppPreferences.darkMode) "dark" else "light",
                ) { AppPreferences.setDark(it == "dark") }
                Text(tr("Matn o'lchami", "Размер текста", "Text size"), fontSize = 13.sp, color = Muted)
                Segmented(
                    listOf("small" to tr("Kichik", "Маленький", "Small"), "normal" to tr("O'rtacha", "Средний", "Medium"), "large" to tr("Katta", "Большой", "Large")),
                    when (AppPreferences.fontScale) { in 0.8f..0.95f -> "small"; in 1.05f..1.3f -> "large"; else -> "normal" },
                ) {
                    AppPreferences.setFont(when (it) { "small" -> 0.90f; "large" -> 1.15f; else -> 1f })
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Ovoz va tebranish", "Звук и вибрация", "Sound and vibration"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                PreferenceSwitch(tr("Yangi buyurtma ovozi", "Звук нового заказа", "New order sound"), AppPreferences.orderSound, AppPreferences::updateOrderSound)
                PreferenceSwitch(tr("Bildirishnoma ovozi", "Звук уведомлений", "Notification sound"), AppPreferences.notificationSound, AppPreferences::updateNotificationSound)
                PreferenceSwitch(tr("Tebranish", "Вибрация", "Vibration"), AppPreferences.vibration, AppPreferences::updateVibration)
                Text(tr("Yangi buyurtma ovozi va tebranishi yangi buyurtma kelganda ishlaydi. Bildirishnoma ovozi alohida bildirishnomalar ulanganda ishlaydi.", "Звук нового заказа и вибрация срабатывают при поступлении нового заказа. Звук уведомлений заработает после подключения системных уведомлений.", "New-order sound and vibration activate when a new order arrives. Notification sound will work when system notifications are connected."), fontSize = 11.sp, color = Muted)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Maxfiylik va xavfsizlik", "Конфиденциальность и безопасность", "Privacy and security"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(tr("TOXA joylashuv ruxsatidan safar va haydovchi xaritasini ko'rsatish uchun foydalanadi. Ruxsatlarni Android sozlamalarida istalgan vaqtda o'zgartirishingiz mumkin.", "TOXA использует геолокацию для поездок и отображения водителя на карте. Разрешения можно изменить в настройках Android.", "TOXA uses location to manage trips and show the driver on the map. You can change permissions in Android settings at any time."), fontSize = 12.sp, color = Muted)
                Surface(
                    Modifier.fillMaxWidth().clickable {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(13.dp), color = if (AppPreferences.darkMode) Color(0xFF303744) else Color(0xFFFFF3D1),
                ) {
                    Text(tr("⚙  Ilova ruxsatlari va xavfsizlik", "⚙  Разрешения и безопасность", "⚙  App permissions and security"), Modifier.padding(13.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, color = Ink)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


/** Aloqa kanali serverda sozlanmagani uchun hozircha mustaqil ishlaydigan FAQ ko'rsatiladi. */
@Composable
fun SupportFaq() {
    var expanded by remember { mutableStateOf(-1) }
    val questions = when (AppLanguage.code) {
        "ru" -> listOf(
            "Как заказать такси?" to "На главной странице выберите адреса отправления и назначения, проверьте цену и подтвердите заказ.",
            "Можно отменить заказ?" to "Используйте кнопку отмены в активном заказе. После начала поездки отмена может быть ограничена.",
            "Как рассчитывается цена?" to "Цена рассчитывается по расстоянию и тарифу и показывается до подтверждения заказа.",
            "Что делать, если не работает GPS?" to "Разрешите доступ к геолокации для TOXA в настройках телефона, включите GPS и попробуйте снова на открытом месте.",
            "Водитель не видит заказы" to "Водителю нужно выйти на линию и проверить интернет и разрешение на геолокацию."
        )
        "en" -> listOf(
            "How do I order a taxi?" to "On the home screen, choose pickup and destination, review the fare, and confirm the order.",
            "Can I cancel an order?" to "Use the cancel button in the active order. Cancellation may be restricted after the trip starts.",
            "How is the fare calculated?" to "The fare is calculated from the trip distance and tariff and shown before you confirm.",
            "What if GPS does not work?" to "Allow TOXA to access location in phone settings, turn on GPS, and try again outdoors.",
            "The driver cannot see orders" to "The driver should go online and check the internet connection and location permission."
        )
        else -> listOf(
            "Buyurtmani qanday beraman?" to "Bosh sahifada qayerdan va qayerga manzillarini tanlang, narxni tekshiring va buyurtmani tasdiqlang.",
            "Buyurtmani bekor qilish mumkinmi?" to "Faol buyurtma oynasidagi bekor qilish tugmasidan foydalaning. Haydovchi safarni boshlab yuborganidan keyin bekor qilish cheklanishi mumkin.",
            "Narx qanday hisoblanadi?" to "Narx safar masofasi va tarif hisob-kitobiga ko'ra ilovada buyurtmadan oldin ko'rsatiladi.",
            "GPS ishlamasa nima qilaman?" to "Telefon sozlamalarida TOXA uchun joylashuv ruxsatini yoqing, GPSni faollashtiring va ochiq joyda qayta urinib ko'ring.",
            "Haydovchi buyurtmalarni ko'rmayapti" to "Haydovchi ilovasida Ishga chiqish holatini yoqing va internet hamda joylashuv ruxsatini tekshiring."
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEachIndexed { index, qa ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = if (expanded == index) -1 else index },
                shape = RoundedCornerShape(15.dp),
                color = CardBg,
                border = BorderStroke(1.dp, Hairline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(qa.first, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(if (expanded == index) "−" else "+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    if (expanded == index) Text(qa.second, fontSize = 13.sp, color = Muted)
                }
            }
        }
    }
}
