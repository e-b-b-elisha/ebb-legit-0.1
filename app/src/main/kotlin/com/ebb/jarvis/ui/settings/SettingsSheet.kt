package com.ebb.jarvis.ui.settings

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ebb.jarvis.ui.SettingsSnapshot
import com.ebb.jarvis.ui.hud.HoloPanel
import com.ebb.jarvis.ui.hud.SectionLabel
import com.ebb.jarvis.ui.theme.Jarvis

private val MODELS = listOf(
    "claude-opus-5" to "Opus 5 · most capable",
    "claude-sonnet-5" to "Sonnet 5 · balanced",
    "claude-haiku-4-5" to "Haiku 4.5 · cheapest",
)

private val EFFORTS = listOf("low", "medium", "high")

@Composable
fun SettingsSheet(
    settings: SettingsSnapshot,
    onApiKey: (String) -> Unit,
    onModel: (String) -> Unit,
    onEffort: (String) -> Unit,
    onOperatorName: (String) -> Unit,
    onSpeakReplies: (Boolean) -> Unit,
    onBootSequence: (Boolean) -> Unit,
    onWebSearch: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var keyDraft by remember { mutableStateOf("") }
    var nameDraft by remember(settings.operatorName) { mutableStateOf(settings.operatorName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Jarvis.Void.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "CONFIGURATION", modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close settings",
                tint = Jarvis.Cyan,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onClose),
            )
        }

        // --- credentials ----------------------------------------------------
        Block(title = "ANTHROPIC API KEY") {
            Text(
                text = if (settings.apiKeySet) {
                    "A key is stored. Paste a new one to replace it."
                } else {
                    "No key stored. JARVIS cannot reason until one is set."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (settings.apiKeySet) Jarvis.TextDim else Jarvis.Amber,
            )
            Field(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                placeholder = "sk-ant-...",
                masked = true,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Chip(
                    label = "SAVE KEY",
                    selected = keyDraft.isNotBlank(),
                    onClick = {
                        if (keyDraft.isNotBlank()) {
                            onApiKey(keyDraft)
                            keyDraft = ""
                        }
                    },
                )
                if (settings.apiKeySet) {
                    Chip(
                        label = "FORGET",
                        selected = false,
                        onClick = {
                            onApiKey("")
                            keyDraft = ""
                        },
                    )
                }
            }
            Text(
                text = "Held in the phone's encrypted keystore. Calls go straight from " +
                    "this device to api.anthropic.com — no intermediary server.",
                style = MaterialTheme.typography.labelSmall,
                color = Jarvis.TextDim,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // --- model ----------------------------------------------------------
        Block(title = "MODEL") {
            MODELS.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModel(id) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Radio(selected = settings.model == id)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (settings.model == id) Jarvis.Text else Jarvis.TextDim,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }

        Block(title = "EFFORT") {
            Text(
                text = "Low answers fastest. Raise it when you want the model to think " +
                    "before it speaks — at more tokens and more latency.",
                style = MaterialTheme.typography.labelSmall,
                color = Jarvis.TextDim,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EFFORTS.forEach { level ->
                    Chip(
                        label = level.uppercase(),
                        selected = settings.effort == level,
                        onClick = { onEffort(level) },
                    )
                }
            }
        }

        // --- behaviour ------------------------------------------------------
        Block(title = "BEHAVIOUR") {
            Text(
                text = "Address me as",
                style = MaterialTheme.typography.labelSmall,
                color = Jarvis.TextDim,
            )
            Field(
                value = nameDraft,
                onValueChange = {
                    nameDraft = it
                    onOperatorName(it)
                },
                placeholder = "Sir",
                modifier = Modifier.padding(top = 8.dp),
            )
            Toggle(
                label = "Speak replies aloud",
                checked = settings.speakReplies,
                onCheckedChange = onSpeakReplies,
            )
            Toggle(
                label = "Allow web search",
                checked = settings.webSearch,
                onCheckedChange = onWebSearch,
            )
            Toggle(
                label = "Boot sequence on cold start",
                checked = settings.bootSequence,
                onCheckedChange = onBootSequence,
            )
        }

        // --- system ---------------------------------------------------------
        Block(title = "SYSTEM") {
            Chip(
                label = "SET AS DEFAULT HOME",
                selected = true,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_HOME_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Text(
                text = "Android only lets you change the home app from its own settings.",
                style = MaterialTheme.typography.labelSmall,
                color = Jarvis.TextDim,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        SectionLabel(text = title, color = Jarvis.Cyan)
        HoloPanel(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column { content() }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    masked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Jarvis.Panel, RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Jarvis.Text),
            cursorBrush = SolidColor(Jarvis.Cyan),
            visualTransformation = if (masked) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jarvis.TextDim.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Jarvis.Cyan.copy(alpha = 0.18f) else Jarvis.Panel,
                RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Jarvis.CyanBright else Jarvis.TextDim,
        )
    }
}

@Composable
private fun Radio(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(
                if (selected) Jarvis.Cyan else Jarvis.Panel,
                RoundedCornerShape(7.dp),
            ),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Jarvis.Text,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Jarvis.Void,
                checkedTrackColor = Jarvis.Cyan,
                uncheckedThumbColor = Jarvis.TextDim,
                uncheckedTrackColor = Jarvis.Panel,
            ),
        )
    }
}
