package uz.taksi

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TABS = listOf("🚕" to "Safar", "💰" to "Pul", "💬" to "Yordam", "👤" to "Kabinet")

/**
 * Asosiy qobiq: safar ekrani doim ostida turadi (tanlangan manzil va faol buyurtma yo'qolmaydi),
 * boshqa bo'limlar uning ustidan ochiladi.
 */
@Composable
fun Shell(role: String, prefs: SharedPreferences, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    Column(Modifier.fillMaxSize().background(PageBg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (role == "driver") DriverScreen(onLogout) else PassengerScreen(onLogout)
            if (tab != 0) {
                Box(Modifier.fillMaxSize().background(PageBg).pointerInput(Unit) { detectTapGestures { } }) {
                    when (tab) {
                        1 -> MoneyTab(role)
                        2 -> SupportTab(role)
                        else -> CabinetTab(role, prefs, onLogout)
                    }
                }
            }
        }
        BottomTabs(TABS, tab) { tab = it }
    }
}

/** Pastki panel: (belgi, yozuv) juftliklari. */
@Composable
fun BottomTabs(tabs: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = Color.White, shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            tabs.forEachIndexed { i, (icon, label) ->
                val sel = selected == i
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(i) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) Yellow else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text(icon, fontSize = 18.sp) }
                    Text(
                        label, fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) Ink else Muted,
                    )
                }
            }
        }
    }
}
