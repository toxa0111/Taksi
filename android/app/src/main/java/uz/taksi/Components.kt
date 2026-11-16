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
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF3F4F6)), contentAlignment = Alignment.Center) {
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
        color = Color(0xFFFFF8E8),
        border = BorderStroke(1.dp, Color(0xFFF2E4B8)),
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
        color = Color(0xFFF8F9FB),
        border = BorderStroke(1.dp, Color(0xFFE8EBF0)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hisob ma'lumotlari", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            listOf("Ism" to name, "Telefon" to phone, "Hisob turi" to role).forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.first, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(item.second.ifBlank { "Ko'rsatilmagan" }, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Telefon raqamini o'zgartirish hozircha ilova ichida mavjud emas. Hisobga kirish SMS tasdiqlash orqali amalga oshiriladi.", fontSize = 12.sp, color = Muted)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRefresh),
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFFFFF3D1),
            ) {
                Text("↻  Ma'lumotlarni yangilash", Modifier.padding(13.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
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
                Text("Ko'rinish", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Rang rejimi", fontSize = 13.sp, color = Muted)
                Segmented(
                    listOf("light" to "Kunduzgi", "dark" to "Tungi"),
                    if (AppPreferences.darkMode) "dark" else "light",
                ) { AppPreferences.setDark(it == "dark") }
                Text("Matn o'lchami", fontSize = 13.sp, color = Muted)
                Segmented(
                    listOf("small" to "Kichik", "normal" to "O'rtacha", "large" to "Katta"),
                    when (AppPreferences.fontScale) { in 0.8f..0.95f -> "small"; in 1.05f..1.3f -> "large"; else -> "normal" },
                ) {
                    AppPreferences.setFont(when (it) { "small" -> 0.90f; "large" -> 1.15f; else -> 1f })
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ovoz va tebranish", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                PreferenceSwitch("Yangi buyurtma ovozi", AppPreferences.orderSound, AppPreferences::setOrderSound)
                PreferenceSwitch("Bildirishnoma ovozi", AppPreferences.notificationSound, AppPreferences::setNotificationSound)
                PreferenceSwitch("Tebranish", AppPreferences.vibration, AppPreferences::setVibration)
                Text("Eslatma: ovoz va tebranish tanlovlari saqlanadi. Ularni ishga tushiradigan buyurtma/bildirishnoma hodisalari ilovaga ulanganda qo'llanadi.", fontSize = 11.sp, color = Muted)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardBg, border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Maxfiylik va xavfsizlik", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("TOXA joylashuv ruxsatidan safar va haydovchi xaritasini ko'rsatish uchun foydalanadi. Ruxsatlarni Android sozlamalarida istalgan vaqtda o'zgartirishingiz mumkin.", fontSize = 12.sp, color = Muted)
                Surface(
                    Modifier.fillMaxWidth().clickable {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(13.dp), color = if (AppPreferences.darkMode) Color(0xFF303744) else Color(0xFFFFF3D1),
                ) {
                    Text("⚙  Ilova ruxsatlari va xavfsizlik", Modifier.padding(13.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
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
    val questions = listOf(
        "Buyurtmani qanday beraman?" to "Bosh sahifada qayerdan va qayerga manzillarini tanlang, narxni tekshiring va buyurtmani tasdiqlang.",
        "Buyurtmani bekor qilish mumkinmi?" to "Faol buyurtma oynasidagi bekor qilish tugmasidan foydalaning. Haydovchi safarni boshlab yuborganidan keyin bekor qilish cheklanishi mumkin.",
        "Narx qanday hisoblanadi?" to "Narx safar masofasi va tarif hisob-kitobiga ko'ra ilovada buyurtmadan oldin ko'rsatiladi.",
        "GPS ishlamasa nima qilaman?" to "Telefon sozlamalarida TOXA uchun joylashuv ruxsatini yoqing, GPSni faollashtiring va ochiq joyda qayta urinib ko'ring.",
        "Haydovchi buyurtmalarni ko'rmayapti" to "Haydovchi ilovasida Ishga chiqish holatini yoqing va internet hamda joylashuv ruxsatini tekshiring."
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        questions.forEachIndexed { index, qa ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = if (expanded == index) -1 else index },
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFFF8F9FB),
                border = BorderStroke(1.dp, Color(0xFFE8EBF0)),
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
