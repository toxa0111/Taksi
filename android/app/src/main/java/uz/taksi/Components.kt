package uz.taksi

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
        color = Color.White,
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
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 6.dp) {
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF3F4F6))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF8E1)).padding(16.dp),
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

/** Katta sarlavha satri (masalan, "Haydovchi ~5 daqiqada keladi"). */
@Composable
fun BigLine(text: String, sub: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Muted)
    }
}
