package uz.taksi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Zamonaviy chiziqli ikonkalar (24x24 to'r). Kumush metall gradientda chiziladi. */
private class G(val s: Float) {
    val p = Path()
    private var cx = 0f
    private var cy = 0f
    fun m(x: Float, y: Float) { p.moveTo(x * s, y * s); cx = x; cy = y }
    fun l(x: Float, y: Float) { p.lineTo(x * s, y * s); cx = x; cy = y }
    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) { p.cubicTo(x1 * s, y1 * s, x2 * s, y2 * s, x * s, y * s); cx = x; cy = y }
    fun q(qx: Float, qy: Float, x: Float, y: Float) = c(cx + 2f / 3 * (qx - cx), cy + 2f / 3 * (qy - cy), x + 2f / 3 * (qx - x), y + 2f / 3 * (qy - y), x, y)
    fun z() = p.close()
    fun circle(x: Float, y: Float, r: Float) = p.addOval(Rect((x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s))
    fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float) = p.addRoundRect(RoundRect(l * s, t * s, r * s, b * s, CornerRadius(rad * s)))
}

private val ICON_OF = mapOf(
    "🧭" to "nav", "🚕" to "car", "🗓" to "route", "👛" to "wallet", "💰" to "wallet", "💬" to "chat", "👤" to "user",
    "📊" to "chart", "👥" to "users", "🪪" to "idcard", "💳" to "card", "⚙" to "gear", "🩺" to "pulse",
    "🔄" to "refresh", "📍" to "pin", "🚪" to "logout",
)

private fun build(kind: String, s: Float): Path {
    val g = G(s)
    when (kind) {
        "nav" -> { g.m(12f, 3.5f); g.l(19.5f, 20f); g.l(12f, 16f); g.l(4.5f, 20f); g.z() }
        "car" -> {
            g.m(6f, 11.5f); g.l(8f, 7f); g.l(16f, 7f); g.l(18f, 11.5f)
            g.rr(3.5f, 11.5f, 20.5f, 17.5f, 2.2f); g.circle(7.5f, 17.5f, 1.9f); g.circle(16.5f, 17.5f, 1.9f)
        }
        "route" -> {
            g.m(8.2f, 18f); g.l(14.5f, 18f); g.c(18.5f, 18f, 18.5f, 12f, 14.5f, 12f); g.l(9.5f, 12f)
            g.c(5.5f, 12f, 5.5f, 6f, 9.5f, 6f); g.l(15.8f, 6f); g.circle(6f, 18f, 2.2f); g.circle(18f, 6f, 2.2f)
        }
        "wallet" -> { g.rr(3f, 6f, 21f, 20f, 3.2f); g.m(3f, 10.5f); g.l(21f, 10.5f); g.circle(16.8f, 15.2f, 1.1f) }
        "chat" -> { g.rr(3f, 4f, 21f, 17f, 4.5f); g.m(8f, 17f); g.l(8f, 21f); g.l(12.5f, 17f) }
        "user" -> { g.circle(12f, 8f, 4f); g.m(4.5f, 21f); g.c(4.5f, 16.5f, 8f, 14.5f, 12f, 14.5f); g.c(16f, 14.5f, 19.5f, 16.5f, 19.5f, 21f) }
        "chart" -> { g.m(6f, 20f); g.l(6f, 12f); g.m(12f, 20f); g.l(12f, 5f); g.m(18f, 20f); g.l(18f, 9f) }
        "users" -> {
            g.circle(9f, 9f, 3f); g.m(3.5f, 20f); g.q(3.5f, 14.5f, 9f, 14.5f); g.q(14.5f, 14.5f, 14.5f, 20f)
            g.circle(17f, 8f, 2.3f); g.m(16.5f, 13.8f); g.q(20.8f, 14f, 20.8f, 19f)
        }
        "idcard" -> {
            g.rr(3f, 5f, 21f, 19f, 2.6f); g.circle(9f, 11f, 2f); g.m(5.8f, 16f); g.q(9f, 12.8f, 12.2f, 16f)
            g.m(14.5f, 10f); g.l(18f, 10f); g.m(14.5f, 13.5f); g.l(18f, 13.5f)
        }
        "card" -> { g.rr(3f, 6f, 21f, 18f, 2.6f); g.m(3f, 10.5f); g.l(21f, 10.5f); g.m(6.5f, 14.8f); g.l(10f, 14.8f) }
        "gear" -> {
            g.circle(12f, 12f, 3.2f); g.circle(12f, 12f, 6.2f)
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                g.m(12f + 6.2f * cos(a), 12f + 6.2f * sin(a)); g.l(12f + 8.8f * cos(a), 12f + 8.8f * sin(a))
            }
        }
        "pulse" -> { g.m(3f, 12f); g.l(8f, 12f); g.l(10f, 6f); g.l(14f, 18f); g.l(16f, 12f); g.l(21f, 12f) }
        "refresh" -> {
            g.p.arcTo(Rect(5f * s, 5f * s, 19f * s, 19f * s), -50f, 280f, true)
            g.m(18f, 3.6f); g.l(18f, 7.6f); g.l(14f, 7.6f)
        }
        "pin" -> {
            g.m(12f, 21f); g.c(8f, 16f, 5.8f, 13f, 5.8f, 9.5f); g.c(5.8f, 6f, 8.5f, 3.5f, 12f, 3.5f)
            g.c(15.5f, 3.5f, 18.2f, 6f, 18.2f, 9.5f); g.c(18.2f, 13f, 16f, 16f, 12f, 21f); g.circle(12f, 9.5f, 2.4f)
        }
        "logout" -> {
            g.m(10f, 4f); g.l(6f, 4f); g.q(4.5f, 4f, 4.5f, 5.5f); g.l(4.5f, 18.5f); g.q(4.5f, 20f, 6f, 20f); g.l(10f, 20f)
            g.m(9f, 12f); g.l(20f, 12f); g.m(16.5f, 8.5f); g.l(20f, 12f); g.l(16.5f, 15.5f)
        }
    }
    return g.p
}

/** Emoji o'rniga: kumush gradientli zamonaviy chiziqli ikonka (mos kelmasa emoji ko'rsatiladi). */
@Composable
fun SilverIcon(emoji: String, size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    val kind = ICON_OF[emoji.replace("️", "")]
    if (kind == null) { Text(emoji, fontSize = (size.value * 0.8f).sp, modifier = modifier); return }
    val brush: Brush = when {
        tint != null -> SolidColor(tint)
        AppSettings.dark -> SilverBrush
        else -> SolidColor(Color(0xFF55555E))
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        drawPath(build(kind, s), brush, style = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
