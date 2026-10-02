package com.marblemd.app.model

import android.net.Uri
import java.util.UUID

enum class SaveState {
    SAVED,
    SAVING,
    UNSAVED,
    READ_ONLY,
    ERROR
}

data class MarkdownDocument(
    val title: String,
    val content: String,
    val uri: Uri? = null,
    val id: String = UUID.randomUUID().toString(),
    val writable: Boolean = false,
    val saveState: SaveState = if (uri == null) SaveState.UNSAVED else SaveState.READ_ONLY,
    val revision: Long = 0L,
    val hasUnsavedChanges: Boolean = uri == null
)
