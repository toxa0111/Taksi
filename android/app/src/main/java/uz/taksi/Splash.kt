package uz.taksi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private var splashDone = false   // jarayon boshiga bir marta

/** Ilova ochilganda brend animatsiyasi: yorug'lik halqasi, X logosi o'sib chiqadi, tillo yaltiroq o'tadi, so'ng yo'qoladi. */
@Composable
fun BrandSplash(content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(!splashDone) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (show) SplashLayer { splashDone = true; show = false }
    }
}

@Composable
private fun SplashLayer(onEnd: () -> Unit) {
    val appear = remember { Animatable(0f) }    // logo paydo bo'lishi
    val ring = remember { Animatable(0f) }      // halqa kengayishi
    val shine = remember { Animatable(0f) }     // yaltiroq o'tishi
    val tag = remember { Animatable(0f) }       // yozuv
    val fade = remember { Animatable(1f) }      // chiqish
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { ring.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
            launch { appear.animateTo(1f, tween(750, easing = CubicBezierEasing(0.2f, 1.25f, 0.4f, 1f))) }
            launch { kotlinx.coroutines.delay(650); shine.animateTo(1f, tween(900, easing = LinearEasing)) }
            launch { kotlinx.coroutines.delay(900); tag.animateTo(1f, tween(600)) }
        }
        kotlinx.coroutines.delay(250)
        fade.animateTo(0f, tween(420))
        onEnd()
    }
    Box(
        Modifier.fillMaxSize().alpha(fade.value).appBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 20.dp.toPx())
            val r = size.minDimension * (0.15f + 0.55f * ring.value)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66D9AE4B).copy(alpha = 0.45f * (1f - ring.value * 0.6f)), Color.Transparent), c, r),
                radius = r, center = c,
            )
            drawCircle(Color(0xFFF1DFA6).copy(alpha = 0.5f * (1f - ring.value)), radius = r * 0.8f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Image(
                painter = painterResource(R.drawable.toxa_logo),
                contentDescription = null,
                modifier = Modifier.size(170.dp)
                    .graphicsLayer {
                        val s = 0.55f + 0.45f * appear.value
                        scaleX = s; scaleY = s; rotationZ = (1f - appear.value) * -25f
                        alpha = appear.value.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val w = size.width
                        val x = -w * 0.6f + shine.value * w * 2.2f
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                                start = Offset(x, 0f), end = Offset(x + w * 0.5f, size.height * 0.4f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
            )
            Text(
                "TOSHKENT TAKSI", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp,
                color = Color(0xFFD9AE4B), modifier = Modifier.alpha(tag.value),
            )
        }
    }
}
