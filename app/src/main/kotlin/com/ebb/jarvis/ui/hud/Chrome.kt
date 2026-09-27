package com.ebb.jarvis.ui.hud

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ebb.jarvis.core.system.SystemStatus
import com.ebb.jarvis.ui.theme.Jarvis

/**
 * A glass panel with corner brackets. The brackets are what make a rounded
 * rectangle read as instrumentation rather than a card.
 */
@Composable
fun HoloPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(Jarvis.Panel, RoundedCornerShape(4.dp))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                val arm = 14.dp.toPx()
                val edge = Jarvis.PanelEdge

                // Thin full outline.
                drawRect(
                    color = edge.copy(alpha = 0.35f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )

                // Brighter L-brackets at each corner.
                val bright = Jarvis.Cyan.copy(alpha = 0.85f)
                val w = size.width
                val h = size.height
                listOf(
                    // top-left
                    Offset(0f, 0f) to listOf(Offset(arm, 0f), Offset(0f, arm)),
                    // top-right
                    Offset(w, 0f) to listOf(Offset(w - arm, 0f), Offset(w, arm)),
                    // bottom-left
                    Offset(0f, h) to listOf(Offset(arm, h), Offset(0f, h - arm)),
                    // bottom-right
                    Offset(w, h) to listOf(Offset(w - arm, h), Offset(w, h - arm)),
                ).forEach { (corner, arms) ->
                    arms.forEach { end ->
                        drawLine(bright, corner, end, strokeWidth = stroke * 1.6f)
                    }
                }
            }
            .padding(14.dp),
        content = content,
    )
}

/** A moving scan band and a vignette, laid over everything. Purely atmosphere. */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = LinearEasing),
        ),
        label = "sweep",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()

                // Horizontal scan band.
                val bandTop = size.height * sweep
                val bandHeight = size.height * 0.16f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Jarvis.Cyan.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        startY = bandTop,
                        endY = bandTop + bandHeight,
                    ),
                    topLeft = Offset(0f, bandTop),
                    size = androidx.compose.ui.geometry.Size(size.width, bandHeight),
                )

                // Edge vignette so the panel content sits inside a lens.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Jarvis.Void.copy(alpha = 0.75f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.72f,
                    ),
                )
            },
    )
}

/** Small all-caps label with tracking, used to head every block on the HUD. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Jarvis.TextDim) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/** Clock, date, and the three readouts that matter on a phone. */
@Composable
fun StatusHeader(status: SystemStatus, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = status.clock,
                style = MaterialTheme.typography.displayLarge,
                color = Jarvis.Text,
            )
            Column(horizontalAlignment = Alignment.End) {
                SectionLabel(status.date)
                SectionLabel(
                    text = status.network,
                    color = if (status.network == "OFFLINE") Jarvis.Danger else Jarvis.Cyan,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Readout(
                label = "PWR",
                value = if (status.batteryPercent > 0) "${status.batteryPercent}%" else "--",
                fraction = status.batteryPercent / 100f,
                tint = when {
                    status.charging -> Jarvis.CyanBright
                    status.batteryPercent <= 15 -> Jarvis.Danger
                    status.batteryPercent <= 30 -> Jarvis.Amber
                    else -> Jarvis.Cyan
                },
                modifier = Modifier.weight(1f),
            )
            Readout(
                label = "MEM",
                value = "${status.memoryUsedPercent}%",
                fraction = status.memoryUsedPercent / 100f,
                tint = if (status.memoryUsedPercent > 85) Jarvis.Amber else Jarvis.Cyan,
                modifier = Modifier.weight(1f),
            )
            Readout(
                label = "DISK",
                value = "%.0fG".format(status.storageFreeGb),
                fraction = (status.storageFreeGb / 256.0).coerceIn(0.0, 1.0).toFloat(),
                tint = Jarvis.Cyan,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Readout(
    label: String,
    value: String,
    fraction: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(label)
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
        // Segmented bar: 16 cells lit in proportion.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(4.dp)
                .drawBehind {
                    val cells = 16
                    val gap = size.width * 0.02f
                    val cellWidth = (size.width - gap * (cells - 1)) / cells
                    val lit = (fraction.coerceIn(0f, 1f) * cells).toInt()
                    repeat(cells) { index ->
                        drawRect(
                            color = if (index < lit) tint else tint.copy(alpha = 0.16f),
                            topLeft = Offset(index * (cellWidth + gap), 0f),
                            size = androidx.compose.ui.geometry.Size(cellWidth, size.height),
                        )
                    }
                },
        )
    }
}
