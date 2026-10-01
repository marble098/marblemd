package com.marblemd.app.model

/** A pending "restore the reading position" request for one document. */
data class ScrollTarget(
    val documentId: String,
    val blockIndex: Int
)
