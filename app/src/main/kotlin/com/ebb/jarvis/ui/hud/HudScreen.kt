package com.ebb.jarvis.ui.hud

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ebb.jarvis.ui.Phase
import com.ebb.jarvis.ui.JarvisUiState
import com.ebb.jarvis.ui.theme.Jarvis
import kotlinx.coroutines.delay

@Composable
fun HudScreen(
    state: JarvisUiState,
    onMic: () -> Unit,
    onSubmit: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        StatusHeader(status = state.status)

        Spacer(modifier = Modifier.weight(0.6f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ArcReactor(
                mode = state.phase.toReactorMode(),
                amplitude = state.amplitude,
                modifier = Modifier
                    .size(236.dp)
                    .clickable(onClick = onMic),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = state.phase.caption(),
            style = MaterialTheme.typography.labelSmall,
            color = when (state.phase) {
                Phase.WORKING -> Jarvis.Amber
                Phase.LISTENING -> Jarvis.CyanBright
                else -> Jarvis.TextDim
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(0.4f))

        ConversationPanel(
            state = state,
            onClear = onClear,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        CommandBar(
            listening = state.phase == Phase.LISTENING,
            micEnabled = state.phase != Phase.WORKING,
            onMic = onMic,
            onSubmit = onSubmit,
            onOpenDrawer = onOpenDrawer,
            onOpenSettings = onOpenSettings,
        )

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ConversationPanel(
    state: JarvisUiState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nothingToShow = state.heard.isBlank() &&
        state.reply.isBlank() &&
        state.error == null &&
        state.actions.isEmpty()

    if (nothingToShow) {
        HoloPanel(modifier = modifier) {
            Text(
                text = "Tap the reactor and speak, or type below.",
                style = MaterialTheme.typography.bodyMedium,
                color = Jarvis.TextDim,
            )
        }
        return
    }

    HoloPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.heard.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionLabel("INPUT")
                    Text(
                        text = "CLEAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = Jarvis.CyanDim,
                        modifier = Modifier.clickable(onClick = onClear),
                    )
                }
                Text(
                    text = state.heard,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jarvis.TextDim,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            state.actions.forEach { note ->
                Text(
                    text = "› $note",
                    style = MaterialTheme.typography.labelSmall,
                    color = Jarvis.Amber,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.reply.isNotBlank()) {
                SectionLabel(text = "JARVIS", modifier = Modifier.padding(top = 12.dp))
                TypedText(
                    text = state.reply,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            state.error?.let { message ->
                SectionLabel(
                    text = "FAULT",
                    color = Jarvis.Danger,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jarvis.Danger,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** Reveals the finished reply character by character — the feel of a stream without one. */
@Composable
private fun TypedText(text: String, modifier: Modifier = Modifier) {
    var revealed by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        revealed = 0
        while (revealed < text.length) {
            delay(10L)
            revealed = (revealed + 2).coerceAtMost(text.length)
        }
    }
    Text(
        text = text.take(revealed),
        style = MaterialTheme.typography.bodyLarge,
        color = Jarvis.Text,
        modifier = modifier,
    )
}

@Composable
private fun CommandBar(
    listening: Boolean,
    micEnabled: Boolean,
    onMic: () -> Unit,
    onSubmit: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudIcon(
            icon = Icons.Filled.Apps,
            description = "App drawer",
            onClick = onOpenDrawer,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .background(Jarvis.Panel, RoundedCornerShape(3.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Jarvis.Text),
                cursorBrush = SolidColor(Jarvis.Cyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) {
                            onSubmit(draft)
                            draft = ""
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                  Box {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Type a command",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jarvis.TextDim.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                  }
                },
            )
        }

        if (draft.isNotBlank()) {
            HudIcon(
                icon = Icons.Filled.ArrowUpward,
                description = "Send",
                tint = Jarvis.CyanBright,
                onClick = {
                    onSubmit(draft)
                    draft = ""
                },
            )
        } else {
            HudIcon(
                icon = if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                description = if (listening) "Stop listening" else "Listen",
                tint = when {
                    !micEnabled -> Jarvis.CyanDim
                    listening -> Jarvis.CyanBright
                    else -> Jarvis.Cyan
                },
                onClick = { if (micEnabled) onMic() },
            )
        }

        HudIcon(
            icon = Icons.Filled.Settings,
            description = "Settings",
            tint = Jarvis.TextDim,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun HudIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Jarvis.Cyan,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Jarvis.Panel, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun Phase.toReactorMode(): ReactorMode = when (this) {
    Phase.BOOT -> ReactorMode.BOOTING
    Phase.IDLE -> ReactorMode.IDLE
    Phase.LISTENING -> ReactorMode.LISTENING
    Phase.WORKING -> ReactorMode.WORKING
    Phase.SPEAKING -> ReactorMode.SPEAKING
}

private fun Phase.caption(): String = when (this) {
    Phase.BOOT -> "INITIALISING"
    Phase.IDLE -> "STANDING BY"
    Phase.LISTENING -> "LISTENING"
    Phase.WORKING -> "WORKING"
    Phase.SPEAKING -> "SPEAKING"
}
