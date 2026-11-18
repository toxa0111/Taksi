package uz.taksi

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Daraja: bajarilgan safarlar soniga qarab. */
fun levelOf(trips: Int): String = when {
    trips >= 500 -> "Master"
    trips >= 200 -> "Ekspert"
    trips >= 50 -> "Tajribali"
    else -> "Yangi"
}

/** Ustuvorlik: reyting va safarlar soni asosida hisoblanadi (0 dan boshlanadi). */
fun priorityOf(rating: Double, ratingCount: Int, trips: Int, penalty: Int = 0): Int {
    val r = if (ratingCount > 0) ((rating - 4.0).coerceAtLeast(0.0) * 40.0) else 0.0
    return (r + minOf(trips, 200) / 4.0).toInt() - penalty
}

/** Ustuvorlik: serverdan kelgan tayyor qiymat (bo'lmasa eski formula). */
fun prioOf(st: org.json.JSONObject?): Int =
    if (st != null && st.has("priority")) st.optInt("priority")
    else priorityOf(st?.optDouble("rating") ?: 0.0, st?.optInt("ratingCount") ?: 0, st?.optInt("totalCount") ?: 0, st?.optInt("penalty") ?: 0)

fun signed(n: Int) = if (n >= 0) "+$n" else n.toString()

@Composable
private fun StatTile(value: String, label: String, icon: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Chip).height(118.dp).padding(14.dp)) {
        Column {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, fontSize = 13.sp, color = Muted)
        }
        Text(icon, fontSize = 40.sp, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun MenuRow(icon: String, title: String, value: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bounceClick(to = 0.98f, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(end = 6.dp))
        Text("›", fontSize = 24.sp, color = Muted)
    }
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card)) { content() }
}

/** Haydovchi kabineti (Yandex Pro uslubida). */
@Composable
fun DriverProfile(prefs: SharedPreferences, onLogout: () -> Unit, goSupport: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var docs by remember { mutableStateOf<JSONObject?>(null) }
    var photo by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var info by remember { mutableStateOf("") }
    var installUrl by remember { mutableStateOf<String?>(null) }
    var showPrefs by remember { mutableStateOf(false) }
    var showRef by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf<JSONObject?>(null) }
    installUrl?.let { InstallDialog(it, "TOXA") { installUrl = null } }
    var showPoints by remember { mutableStateOf(false) }
    var showRating by remember { mutableStateOf(false) }
    if (showPoints) { PointsScreen { showPoints = false }; return }
    if (showRating) { RatingScreen { showRating = false }; return }

    LaunchedEffect(Unit) {
        try { profile = io { Api.call("GET", "/profile") } } catch (e: Exception) { }
        try { stats = io { Api.call("GET", "/stats") } } catch (e: Exception) { }
        try { docs = io { Api.call("GET", "/driver/docs") } } catch (e: Exception) { }
        try { diag = io { Api.call("GET", "/driver/diagnostics") }.optJSONObject("items") } catch (e: Exception) { }
        val id = profile?.optString("id").orEmpty()
        if (id.isNotEmpty()) {
            photo = try {
                io { val b = Api.bytes("/docs/$id/face"); BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
            } catch (e: Exception) { null }
        }
    }
    val name = (profile?.optString("name") ?: "").ifBlank { prefs.getString("name", "") ?: "" }
    val rating = stats?.optDouble("rating") ?: 0.0
    val rc = stats?.optInt("ratingCount") ?: 0
    val total = stats?.optInt("totalCount") ?: 0
    val plate = docs?.optString("plate").orEmpty()
    val verified = docs?.optBoolean("verified") == true

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
                val ph = photo
                if (ph != null) Image(ph, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(name.take(1).uppercase().ifBlank { "👤" }, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Haydovchi" }, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text((profile?.optString("phone") ?: "").ifBlank { prefs.getString("phone", "") ?: "" }, fontSize = 13.sp, color = Muted)
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Yellow), contentAlignment = Alignment.Center) { Text("🚖", fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Text("Haydovchi", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (verified) Pill("✅ Tasdiqlangan", Color(0xFFE1F5E9), Color(0xFF14753F))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(if (rc > 0) String.format("%.2f", rating) else "—", "Reyting", "⭐", Modifier.weight(1f).clickable { showRating = true })
                StatTile(levelOf(total), "Daraja", "🏅", Modifier.weight(1f))
                StatTile(signed(prioOf(stats)), "Ustuvorlik", "🟡", Modifier.weight(1f).clickable { showPoints = true })
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip)) {
                MenuRow("🌟", "Reyting va baholar", if (rc > 0) String.format("%.2f", rating) else "—") { showRating = true }
                MenuRow("⭐", "Ustuvorlik va ballar", signed(prioOf(stats))) { showPoints = true }
                MenuRow("🚕", "Safarlar", "$total ta") { }
                MenuRow("💳", "To'lov", "Naqd pul") { }
            }
        }

        Text("Mening transportim", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                Text(docs?.optString("car").orEmpty().ifBlank { "Mening avtomobilim" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    plate.ifBlank { "Raqam kiritilmagan" },
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                )
            }
            Text("🚗", fontSize = 64.sp, modifier = Modifier.align(Alignment.CenterEnd))
        }

        MenuGroup {
            DriverDocsEntryRow()
            MenuRow("🩺", "Diagnostika", diagAlert(diag)?.let { "⚠" } ?: "") { showDiag = true }
            MenuRow("👥", "Do'stingizni taklif qiling") { showRef = true }
        }
        if (showRef) ReferralDialog { showRef = false }
        if (showDiag) DiagnosticsDialog { showDiag = false; scope.launch { try { diag = io { Api.call("GET", "/driver/diagnostics") }.optJSONObject("items") } catch (e: Exception) { } } }
        MenuGroup {
            MenuRow("💬", "Yordam", "") { goSupport() }
            MenuRow("⚙️", "Sozlamalar", "") { showPrefs = !showPrefs }
        }
        if (showPrefs) AppPrefsPanel()
        MenuGroup {
            MenuRow("🔄", "Yangilanishni tekshirish", info) {
                scope.launch {
                    info = "Tekshirilmoqda…"
                    try {
                        val r = latestRelease()
                        if (r != null && r.first > currentVersionCode(ctx)) { info = "Yangi versiya bor"; installUrl = r.second }
                        else info = "Yangi versiya topilmadi"
                    } catch (e: Exception) { info = "Tekshirilmadi" }
                }
            }
            MenuRow("📍", "Joylashuv ruxsatlari") {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { }
            }
            MenuRow("🚪", "Chiqish") { onLogout() }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp)) {
            Text("Ilova versiyasi", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(versionName(ctx), fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Haydovchi uchun "Shaharlararo" bo'limi: faqat shaharlararo buyurtmalar. */
@Composable
fun DriverIntercityTab() {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) {
        while (true) {
            try {
                list = io { Api.call("GET", "/orders/available").getJSONArray("orders").items() }
                    .filter { it.optString("kind") == "intercity" }
            } catch (e: Exception) { }
            delay(6000)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Shaharlararo")
        if (msg.isNotEmpty()) ErrorText(msg)
        val l = list
        if (l == null) Text("Yuklanmoqda…", color = Muted)
        else if (l.isEmpty()) Panel { Text("Hozircha shaharlararo buyurtma yo'q", color = Muted) }
        else l.forEach { o ->
            OrderCard(o) {
                IntercityLine(o)
                BigButton("Qabul qilish") {
                    scope.launch {
                        try { io { Api.call("POST", "/orders/${o.optInt("id")}/accept", JSONObject()) }; msg = ""; tick++ }
                        catch (e: Exception) { msg = e.message ?: "Xato" }
                    }
                }
            }
        }
    }
}
