package com.marblemd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    document: MarkdownDocument,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    onOpen: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onDirectionChange: (DirectionMode) -> Unit
) {
    var directionMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "MarbleMD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Open Markdown")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { directionMenu = true }) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Text direction")
                        }
                        DropdownMenu(expanded = directionMenu, onDismissRequest = { directionMenu = false }) {
                            DirectionMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        onDirectionChange(mode)
                                        directionMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onFontSizeChange((fontSizeSp - 1f).coerceAtLeast(12f)) },
                        enabled = fontSizeSp > 12f
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Smaller text")
                    }
                    Text("${fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelLarge)
                    FilledTonalIconButton(
                        onClick = { onFontSizeChange((fontSizeSp + 1f).coerceAtMost(34f)) },
                        enabled = fontSizeSp < 34f
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Larger text")
                    }
                    Spacer(Modifier.weight(1f))
                    AssistChip(
                        onClick = { directionMenu = true },
                        label = { Text(directionMode.label) },
                        leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) }
                    )
                }
            }
            HorizontalDivider()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownText(
                    markdown = document.content,
                    fontSizeSp = fontSizeSp,
                    directionMode = directionMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
