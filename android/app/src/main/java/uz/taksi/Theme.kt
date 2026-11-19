package uz.taksi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────── Ranglar ─────────────────────────

// Brend: qora fon, tillo (asosiy amallar), kumush (ikkinchi darajali amallar), oyna (shisha) kartalar
val GoldHi = Color(0xFFF1DFA6)
val Gold = Color(0xFFD1AA4F)
val GoldLo = Color(0xFF8E6C25)
val SilverHi = Color(0xFFF2F3F6)
val Silver = Color(0xFFC9CAD1)
val SilverLo = Color(0xFF8F9099)
/** Jilolangan metall: yuqorida yorug' qirra, o'rtada chiziq, pastda chuqur tus. */
val GoldBrush: Brush get() = Brush.verticalGradient(0f to GoldHi, 0.48f to Color(0xFFD9B75A), 0.52f to Color(0xFFBF9A40), 1f to GoldLo)
val SilverBrush: Brush get() = Brush.verticalGradient(0f to Color(0xFFFFFFFF), 0.48f to Color(0xFFD3D4DA), 0.52f to Color(0xFFABACB4), 1f to SilverLo)
val Yellow: Color get() = Gold                 // asosiy rang (eski nom saqlandi)
val OnBrand = Color(0xFF2A1D00)                // tillo ustidagi matn
val Teal = Silver
val Ink = Color(0xFF111111)
val Ok = Color(0xFF3DD68C)
val Bad = Color(0xFFFF5A52)

// Yorug' / qorong'i rejimga qarab o'zgaradigan ranglar (AppSettings.dark o'zgarsa, ekran qayta chiziladi)
val PageBg: Color get() = if (AppSettings.dark) Color(0xFF17171B) else Color(0xFFFAF8F3)
val Card: Color get() = if (AppSettings.dark) Color(0xFF212126) else Color(0xFFFFFFFF)
val Chip: Color get() = if (AppSettings.dark) Color(0xFF2C2C33) else Color(0xFFEFEBE0)
val Muted: Color get() = if (AppSettings.dark) Color(0xFFAAAAB5) else Color(0xFF6E6A60)
val Hairline: Color get() = if (AppSettings.dark) Color(0xFF3B3B44) else Color(0xFFE5DFD0)
/** Oddiy matn rangi. */
val Fg: Color get() = if (AppSettings.dark) Color(0xFFF5F5F7) else Ink
/** Yumshoq tillo fon (eslatma kartalari). */
val Soft: Color get() = if (AppSettings.dark) Color(0xFF332C18) else Color(0xFFFBF1D3)

/** Ilova foni: ochiq grafit gradient va yuqoridan tillo yog'du. */
fun Modifier.appBackground(): Modifier = composed {
    if (AppSettings.dark) this
        .background(Brush.verticalGradient(listOf(Color(0xFF2A2A31), Color(0xFF18181D), Color(0xFF131317))))
        .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.20f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(160f, 0f), radius = 1100f))
    else this.background(PageBg)
}

/** Matn uchun jilolangan tillo / kumush gradientlar. */
val GoldTextBrush: Brush get() = Brush.horizontalGradient(listOf(Color(0xFFF6E7B0), Color(0xFFD9B75A), Color(0xFFB8913A)))
val SilverTextBrush: Brush get() = Brush.horizontalGradient(listOf(Color(0xFFF2F3F6), Color(0xFFC9CAD1), Color(0xFFA9AAB3)))

/** Oyna uslubi: yarim shaffof porloq sirt va nozik tillo-kumush hoshiya. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp)): Modifier = composed {
    val dark = AppSettings.dark
    this.clip(shape)
        .background(if (dark) Color(0xFF1D1D22) else Color.White)
        .background(Brush.linearGradient(if (dark) listOf(Color(0x30FFFFFF), Color(0x0AFFFFFF)) else listOf(Color(0x14D4AF37), Color(0x00FFFFFF))))
        .border(1.4.dp, Brush.linearGradient(if (dark) listOf(Gold.copy(alpha = 0.9f), Color.White.copy(alpha = 0.10f), Silver.copy(alpha = 0.55f)) else listOf(Gold.copy(alpha = 0.45f), Hairline, Hairline)), shape)
}

private val DarkScheme = darkColorScheme(
    primary = Gold, onPrimary = OnBrand,
    secondary = Silver, onSecondary = Color.Black,
    background = Color(0xFF17171B), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF212126), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF2C2C33), onSurfaceVariant = Color(0xFFAAAAB5),
    outline = Color(0xFF3B3B44), error = Bad,
)

private val Scheme = lightColorScheme(
    primary = Gold, onPrimary = OnBrand,
    secondary = Color(0xFF5A5A62), onSecondary = Color.White,
    background = Color(0xFFFAF8F3), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEFEBE0), onSurfaceVariant = Color(0xFF6E6A60),
    outline = Color(0xFFE5DFD0), error = Bad,
    primaryContainer = Color(0xFFFBF1D3), onPrimaryContainer = Ink,
)

@Composable
fun TaksiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (AppSettings.dark) DarkScheme else Scheme, content = content)
}

// ───────────────────────── Tugmalar ─────────────────────────


// ───────────────────────── Jonli (animatsiyali) bosish ─────────────────────────

/** Bosilganda yumshoq "cho'kadi" va qo'yib yuborilganda prujinadek qaytadi. */
@Composable
fun pressScale(src: MutableInteractionSource, enabled: Boolean = true, to: Float = 0.95f): Float {
    val pressed by src.collectIsPressedAsState()
    return animateFloatAsState(
        if (pressed && enabled) to else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "press",
    ).value
}

/** Har qanday qator/kartaga: bosilganda kichrayadi, tebranish (haptic) beradi. */
fun Modifier.bounceClick(enabled: Boolean = true, to: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val sc = pressScale(src, enabled, to)
    val haptic = LocalHapticFeedback.current
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = ripple(), enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Asosiy (sariq) katta tugma: gradient, yumshoq soya, bosilganda cho'kadi va qaytadi. */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc = pressScale(src, enabled, 0.96f)
    val elev by animateDpAsState(if (!enabled) 0.dp else if (pressed) 1.dp else 8.dp, label = "elev")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val fill = if (enabled) GoldBrush
    else Brush.verticalGradient(listOf(Chip, Chip))
    Box(
        modifier.fillMaxWidth().height(54.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .shadow(elev, shape, ambientColor = Gold, spotColor = Color(0xFF7A5A18))
            .clip(shape).background(fill)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (enabled) 0.55f else 0f), Color.Transparent)), shape)
            .clickable(interactionSource = src, indication = ripple(color = OnBrand), enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (enabled) OnBrand else Muted) }
}

/** Qora tugma (ikkinchi darajali asosiy amal). */
@Composable
fun DarkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val sc = pressScale(src, enabled, 0.96f)
    Button(
        onClick = onClick, enabled = enabled, interactionSource = src,
        modifier = modifier.height(54.dp).graphicsLayer { scaleX = sc; scaleY = sc },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Silver, contentColor = Color(0xFF1B1B20),
            disabledContainerColor = Chip, disabledContentColor = Muted,
        ),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

/** Ramkali tugma. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val sc = pressScale(src, enabled, 0.96f)
    OutlinedButton(
        onClick = onClick, enabled = enabled, interactionSource = src,
        modifier = modifier.fillMaxWidth().height(50.dp).graphicsLayer { scaleX = sc; scaleY = sc },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (AppSettings.dark) Color(0x14FFFFFF) else Color.Transparent, contentColor = if (AppSettings.dark) SilverHi else Color(0xFF3A3A40)),
        border = BorderStroke(1.4.dp, SilverBrush),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

/** Qizil matnli tugma (bekor qilish kabi). */
@Composable
fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val sc = pressScale(src, true, 0.96f)
    TextButton(onClick = onClick, interactionSource = src, modifier = modifier.fillMaxWidth().height(46.dp).graphicsLayer { scaleX = sc; scaleY = sc }) {
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
            focusedBorderColor = Fg, unfocusedBorderColor = Hairline,
            focusedLabelColor = Fg, cursorColor = Fg,
            focusedContainerColor = Card, unfocusedContainerColor = Card,
        ),
    )
}

/** Oq yumaloq karta. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (color == Card) {
        Column(modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    } else Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Chip).border(1.dp, SilverBrush, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .then(if (sel) Modifier.background(GoldBrush) else Modifier)
                    .bounceClick(to = 0.95f) { onSelect(key) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) OnBrand else Muted,
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
            Text("T", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = OnBrand)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        TextButton(onClick = onLogout) { Text("Chiqish", color = Muted) }
    }
}
