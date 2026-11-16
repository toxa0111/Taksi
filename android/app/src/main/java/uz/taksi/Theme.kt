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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── Ranglar ─────────────────────────

val Yellow = Color(0xFFFFC21A)
val Ink: Color get() = if (AppPreferences.darkMode) Color(0xFFF3F4F6) else Color(0xFF16181D)
val PageBg: Color get() = if (AppPreferences.darkMode) Color(0xFF111318) else Color(0xFFF4F5F7)
val Muted: Color get() = if (AppPreferences.darkMode) Color(0xFFADB3BE) else Color(0xFF6B7280)
val CardBg: Color get() = if (AppPreferences.darkMode) Color(0xFF1D222B) else Color.White
val Ok = Color(0xFF1F9D55)
val Bad = Color(0xFFD64545)
val Hairline: Color get() = if (AppPreferences.darkMode) Color(0xFF343B46) else Color(0xFFE3E5E9)

private val LightScheme = lightColorScheme(
    primary = Yellow, onPrimary = Color(0xFF16181D),
    secondary = Color(0xFF16181D), onSecondary = Color.White,
    background = Color(0xFFF4F5F7), onBackground = Color(0xFF16181D),
    surface = Color.White, onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFEDEEF1), onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE3E5E9), error = Bad,
    primaryContainer = Color(0xFFFFF1C2), onPrimaryContainer = Color(0xFF16181D),
)
private val DarkScheme = androidx.compose.material3.darkColorScheme(
    primary = Yellow, onPrimary = Color(0xFF16181D),
    secondary = Color(0xFFE5E7EB), onSecondary = Color(0xFF16181D),
    background = Color(0xFF111318), onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF1D222B), onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF2A303A), onSurfaceVariant = Color(0xFFADB3BE),
    outline = Color(0xFF343B46), error = Color(0xFFFF7777),
    primaryContainer = Color(0xFF4A3A0A), onPrimaryContainer = Color(0xFFFFE7A0),
)

object AppPreferences {
    var darkMode by mutableStateOf(false)
    var fontScale by mutableStateOf(1f)
    var orderSound by mutableStateOf(true)
    var notificationSound by mutableStateOf(true)
    var vibration by mutableStateOf(true)
    private var save: ((String, Any) -> Unit)? = null

    fun load(prefs: android.content.SharedPreferences) {
        darkMode = prefs.getBoolean("pref_dark_mode", false)
        fontScale = prefs.getFloat("pref_font_scale", 1f)
        orderSound = prefs.getBoolean("pref_order_sound", true)
        notificationSound = prefs.getBoolean("pref_notification_sound", true)
        vibration = prefs.getBoolean("pref_vibration", true)
        save = { key, value ->
            val editor = prefs.edit()
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                else -> editor
            }.apply()
        }
    }
    fun setDark(value: Boolean) { darkMode = value; save?.invoke("pref_dark_mode", value) }
    fun setFont(value: Float) { fontScale = value; save?.invoke("pref_font_scale", value) }
    fun setOrderSound(value: Boolean) { orderSound = value; save?.invoke("pref_order_sound", value) }
    fun setNotificationSound(value: Boolean) { notificationSound = value; save?.invoke("pref_notification_sound", value) }
    fun setVibration(value: Boolean) { vibration = value; save?.invoke("pref_vibration", value) }
}

@Composable
fun TaksiTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides density.copy(fontScale = density.fontScale * AppPreferences.fontScale)) {
        MaterialTheme(colorScheme = if (AppPreferences.darkMode) DarkScheme else LightScheme, content = content)
    }
}

// ───────────────────────── Tugmalar ─────────────────────────

/** Asosiy (sariq) katta tugma. */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Yellow, contentColor = Ink,
            disabledContainerColor = Color(0xFFE5E7EB), disabledContentColor = Muted,
        ),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** Qora tugma (ikkinchi darajali asosiy amal). */
@Composable
fun DarkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink, contentColor = Color.White,
            disabledContainerColor = Color(0xFFE5E7EB), disabledContentColor = Muted,
        ),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

/** Ramkali tugma. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** Qizil matnli tugma (bekor qilish kabi). */
@Composable
fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(46.dp)) {
        Text(text, color = Bad, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────────────────── Maydonlar va kartalar ─────────────────────────

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink, unfocusedBorderColor = Hairline,
            focusedLabelColor = Ink, cursorColor = Ink,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
        ),
    )
}

/** Oq yumaloq karta. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = BorderStroke(1.dp, if (color == Color.White) Color(0xFFE9EBEF) else color.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Tanlov (segment) tugmalari: (kalit, yozuv) juftliklari. */
@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAECEF)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (sel) Color.White else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) Ink else Muted,
                )
            }
        }
    }
}

/** Holat yorlig'i. */
@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun statusColors(s: String): Pair<Color, Color> = when (s) {
    "searching" -> Color(0xFFFFF1C2) to Color(0xFF8A6100)
    "accepted", "arrived" -> Color(0xFFE0EEFF) to Color(0xFF1456A8)
    "started", "done" -> Color(0xFFE1F5E9) to Color(0xFF14753F)
    "cancelled" -> Color(0xFFFCE4E4) to Color(0xFFA32626)
    else -> Color(0xFFEDEEF1) to Muted
}

/** "Qayerdan → Qayerga" ko'rinishi: yashil va qizil nuqta, oralig'ida chiziq. */
@Composable
fun Route(from: String, to: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Ok))
            Box(Modifier.width(2.dp).height(24.dp).background(Hairline))
            Box(Modifier.size(12.dp).clip(CircleShape).background(Bad))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(from, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(to, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Xarita ostidagi rang izohi. */
@Composable
fun Legend(vararg items: Pair<Int, String>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c)))
                Text(t, fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

/** Ekran tepasidagi panel: logotip, sarlavha va "Chiqish". */
@Composable
fun TopBar(title: String, subtitle: String, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
            Text("T", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        TextButton(onClick = onLogout) { Text("Chiqish", color = Muted) }
    }
}
