package com.marblemd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marblemd.app.library.MarkdownTemplate
import com.marblemd.app.library.RecentDocument
import com.marblemd.app.model.MarkdownDocument
import java.text.DateFormat
import java.util.Date

/**
 * The app library: what is open right now plus everything the user read before,
 * each recent remembering where they stopped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySheet(
    documents: List<MarkdownDocument>,
    activeId: String?,
    recents: List<RecentDocument>,
    onDismiss: () -> Unit,
    onSelectDocument: (String) -> Unit,
    onCloseDocument: (String) -> Unit,
    onOpenFiles: () -> Unit,
    onNewFile: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onRemoveRecent: (RecentDocument) -> Unit,
    onClearRecents: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                text = "Your open tabs and the Markdown you read before — continue exactly where you stopped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(onClick = onNewFile) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New file")
                }
                FilledTonalButton(onClick = onOpenFiles) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open files")
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (documents.isNotEmpty()) {
                    item { SectionHeader("Open tabs", "${documents.size}") }
                    items(documents, key = { it.id }) { document ->
                        ListItem(
                            modifier = Modifier.clickable { onSelectDocument(document.id) },
                            leadingContent = {
                                Icon(
                                    if (document.id == activeId) Icons.Outlined.CheckCircle
                                    else Icons.Outlined.Description,
                                    contentDescription = null
                                )
                            },
                            headlineContent = {
                                Text(
                                    document.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = { Text(document.saveState.compactLabel()) },
                            trailingContent = {
                                IconButton(onClick = { onCloseDocument(document.id) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Close tab")
                                }
                            }
                        )
                    }
                }

                if (recents.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("Recent Markdown", "${recents.size}")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onClearRecents) { Text("Clear") }
                        }
                    }
                    items(recents, key = { it.uri }) { recent ->
                        RecentRow(
                            recent = recent,
                            onOpen = { onOpenRecent(recent) },
                            onRemove = { onRemoveRecent(recent) }
                        )
                    }
                }

                if (documents.isEmpty() && recents.isEmpty()) {
                    item {
                        Text(
                            text = "Nothing here yet. Open a .md file or start a new one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String) {
    Row(
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun RecentRow(
    recent: RecentDocument,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
        headlineContent = {
            Text(recent.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    text = buildString {
                        append(formatWhen(recent.lastOpenedAt))
                        append(" • ")
                        append(recent.charCount.formatCount())
                        if (recent.scrollIndex > 0) append(" • position saved")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recent.snippet.isNotBlank()) {
                    Text(
                        text = recent.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove from recents")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplateSheet(
    onDismiss: () -> Unit,
    onSelect: (MarkdownTemplate) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "New Markdown file",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Text(
            text = "Start from a template. You can save it anywhere on your device afterwards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
            items(MarkdownTemplate.entries) { template ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(template) }
                        .padding(horizontal = 8.dp),
                    leadingContent = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = template.emoji,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    },
                    headlineContent = { Text(template.label) },
                    supportingContent = { Text(template.description) }
                )
            }
        }
    }
}

internal fun formatWhen(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

internal fun Int.formatCount(): String = when {
    this >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM chars", this / 1_000_000f)
    this >= 1_000 -> String.format(java.util.Locale.US, "%.1fK chars", this / 1_000f)
    else -> "$this chars"
}
