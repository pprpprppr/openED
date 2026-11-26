package org.opened.app.core.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncRevisionMetadata(
    val revisionId: String,
    val providerName: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val description: String = ""
)
