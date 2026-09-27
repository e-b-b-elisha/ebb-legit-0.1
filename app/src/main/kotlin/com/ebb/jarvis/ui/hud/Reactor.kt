package com.ebb.jarvis.ui.hud

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.ebb.jarvis.ui.theme.Jarvis
import kotlin.math.cos
import kotlin.math.sin

/** What the reactor is telling you at a glance. */
enum class ReactorMode { BOOTING, IDLE, LISTENING, WORKING, SPEAKING }

/**
 * The arc reactor. Concentric rings on opposing spins, a tick ring, and a core whose
 * glow tracks microphone amplitude while listening.
 *
 * Every layer is stroked geometry rather than a bitmap, so it stays sharp at any size
 * and needs no blur support (Modifier.blur is API 31+; this runs on 26).
 */
@Composable
fun ArcReactor(
    mode: ReactorMode,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "reactor")

    val outerSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
        ),
        label = "outerSpin",
    )
    val midSpin by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
        ),
        label = "midSpin",
    )
    val fastSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
        ),
        label = "fastSpin",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val accent = when (mode) {
        ReactorMode.BOOTING -> Jarvis.CyanDim
        ReactorMode.IDLE -> Jarvis.Cyan
        ReactorMode.LISTENING -> Jarvis.CyanBright
        ReactorMode.WORKING -> Jarvis.Amber
        ReactorMode.SPEAKING -> Jarvis.Cyan
    }

    // Core swell: amplitude drives it while listening, otherwise it just breathes.
    val targetCore = when (mode) {
        ReactorMode.LISTENING -> 0.55f + amplitude * 0.45f
        ReactorMode.WORKING -> 0.75f
        ReactorMode.SPEAKING -> 0.8f
        ReactorMode.IDLE -> 0.45f
        ReactorMode.BOOTING -> 0.2f
    }
    val core by animateFloatAsState(
        targetValue = targetCore,
        animationSpec = tween(durationMillis = 140),
        label = "core",
    )

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)

            // Outer casing: full ring plus four heavy arc segments.
            glowRing(centre, radius * 0.96f, accent.copy(alpha = 0.22f), radius * 0.012f)
            rotate(degrees = outerSpin, pivot = centre) {
                repeat(4) { index ->
                    drawArc(
                        color = accent.copy(alpha = 0.85f),
                        startAngle = index * 90f + 8f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius * 0.96f, centre.y - radius * 0.96f),
                        size = Size(radius * 1.92f, radius * 1.92f),
                        style = Stroke(width = radius * 0.035f),
                    )
                }
            }

            // Tick ring: 60 marks, every fifth one long.
            rotate(degrees = -midSpin * 0.25f, pivot = centre) {
                repeat(60) { index ->
                    val long = index % 5 == 0
                    val angle = Math.toRadians((index * 6).toDouble())
                    val inner = radius * if (long) 0.78f else 0.82f
                    val outer = radius * 0.86f
                    drawLine(
                        color = accent.copy(alpha = if (long) 0.7f else 0.3f),
                        start = Offset(
                            centre.x + (cos(angle) * inner).toFloat(),
                            centre.y + (sin(angle) * inner).toFloat(),
                        ),
                        end = Offset(
                            centre.x + (cos(angle) * outer).toFloat(),
                            centre.y + (sin(angle) * outer).toFloat(),
                        ),
                        strokeWidth = radius * if (long) 0.016f else 0.008f,
                    )
                }
            }

            // Mid ring, counter-rotating, dashed.
            rotate(degrees = midSpin, pivot = centre) {
                drawCircle(
                    color = accent.copy(alpha = 0.55f),
                    radius = radius * 0.66f,
                    center = centre,
                    style = Stroke(
                        width = radius * 0.02f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(radius * 0.09f, radius * 0.05f),
                        ),
                    ),
                )
            }

            // Inner race: three arcs on a fast spin, quickened further while working.
            rotate(
                degrees = if (mode == ReactorMode.WORKING) fastSpin else midSpin * 1.6f,
                pivot = centre,
            ) {
                repeat(3) { index ->
                    drawArc(
                        color = accent.copy(alpha = 0.9f),
                        startAngle = index * 120f,
                        sweepAngle = 64f,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius * 0.5f, centre.y - radius * 0.5f),
                        size = Size(radius, radius),
                        style = Stroke(width = radius * 0.028f),
                    )
                }
            }

            // Core: radial bloom plus a hard centre, scaled by breath and amplitude.
            val coreRadius = radius * 0.3f * core * breathe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Jarvis.CyanBright.copy(alpha = 0.95f),
                        accent.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                    center = centre,
                    radius = (coreRadius * 3.2f).coerceAtLeast(1f),
                ),
                radius = coreRadius * 3.2f,
                center = centre,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = coreRadius * 0.55f,
                center = centre,
            )
        }
    }
}

/** A ring drawn several times at falling alpha — a blur you can afford on API 26. */
private fun DrawScope.glowRing(centre: Offset, radius: Float, color: Color, width: Float) {
    repeat(4) { step ->
        drawCircle(
            color = color.copy(alpha = color.alpha / (step + 1)),
            radius = radius + step * width * 2.4f,
            center = centre,
            style = Stroke(width = width * (step + 1)),
        )
    }
}
