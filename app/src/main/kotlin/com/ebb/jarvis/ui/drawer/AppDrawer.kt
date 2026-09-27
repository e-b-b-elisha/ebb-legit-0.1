package com.ebb.jarvis.ui.drawer

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ebb.jarvis.core.apps.AppEntry
import com.ebb.jarvis.ui.hud.SectionLabel
import com.ebb.jarvis.ui.theme.Jarvis

/**
 * The drawer. A filter field and a four-wide grid — no folders, no pages, because the
 * fastest path to an app here is saying its name, and this is the fallback.
 */
@Composable
fun AppDrawerSheet(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("") }
    val needle = filter.trim().lowercase()
    val visible = remember(apps, needle) {
        if (needle.isEmpty()) apps else apps.filter { it.searchKey.contains(needle) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Jarvis.Void.copy(alpha = 0.97f))
            .padding(horizontal = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = "${apps.size} APPLICATIONS",
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Reload app index",
                tint = Jarvis.TextDim,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onRefresh),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close drawer",
                tint = Jarvis.Cyan,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onClose),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(Jarvis.Panel, RoundedCornerShape(3.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Jarvis.Text),
                cursorBrush = SolidColor(Jarvis.Cyan),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (filter.isEmpty()) {
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Jarvis.TextDim.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                },
            )
        }

        if (visible.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (apps.isEmpty()) {
                        "App index empty. Tap reload."
                    } else {
                        "Nothing matches \"$filter\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Jarvis.TextDim,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 16.dp,
                bottom = 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = visible, key = { it.component.flattenToString() }) { entry ->
                AppTile(entry = entry, onClick = { onLaunch(entry) })
            }
        }
    }
}

@Composable
private fun AppTile(entry: AppEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = entry.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = entry.label,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Jarvis.Panel, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Jarvis.Cyan,
                )
            }
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = Jarvis.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
