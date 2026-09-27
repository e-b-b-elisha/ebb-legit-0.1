package com.ebb.jarvis.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ebb.jarvis.ui.theme.Jarvis
import kotlinx.coroutines.delay

private val BOOT_LINES = listOf(
    "COLD START",
    "KERNEL LINK ............ OK",
    "SENSOR BUS ............. OK",
    "APP INDEX .............. OK",
    "VOICE INTERFACE ........ OK",
    "REASONING UPLINK ....... OK",
    "ALL SYSTEMS NOMINAL",
)

/**
 * Cold-start theatre: seven lines, then out of the way. Skippable by tapping,
 * and switched off entirely from settings for anyone who has to unlock the phone
 * forty times a day.
 */
@Composable
fun BootSequence(operatorName: String, onFinished: () -> Unit) {
    var shown by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        BOOT_LINES.indices.forEach { index ->
            delay(if (index == 0) 140L else 190L)
            shown = index + 1
        }
        delay(520L)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        BOOT_LINES.take(shown).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.endsWith("OK")) Jarvis.Cyan else Jarvis.TextDim,
            )
        }
        if (shown >= BOOT_LINES.size) {
            Text(
                text = "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Good to see you, $operatorName.",
                style = MaterialTheme.typography.bodyLarge,
                color = Jarvis.CyanBright,
            )
        }
    }
}
