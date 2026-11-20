package com.otokit.noharayh.logged.scoresync

import java.io.InputStream
import java.time.Instant

internal data class ScoreSyncLogRef(val taskId: String, val game: String, val startedAt: Instant)

internal data class ScoreSyncLogSummary(
    val ref: ScoreSyncLogRef,
    val status: String,
    val writing: Boolean,
    val bytes: Long,
    val batchId: String?,
    val fileStamp: String,
    val recordingError: String? = null,
)

internal data class ScoreSyncLogRemoval(val removed: Set<ScoreSyncLogRef>, val skipped: Set<ScoreSyncLogRef>)
internal data class ScoreSyncLogPreview(val text: String, val truncated: Boolean)
internal data class ScoreSyncLogAttachment(val name: String, val open: () -> InputStream)
