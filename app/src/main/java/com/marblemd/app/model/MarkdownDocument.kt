package com.marblemd.app.model

import android.net.Uri

data class MarkdownDocument(
    val title: String,
    val content: String,
    val uri: Uri? = null
)
