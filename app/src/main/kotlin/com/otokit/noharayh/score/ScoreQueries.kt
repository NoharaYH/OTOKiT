package com.otokit.noharayh.score

import java.time.Instant

data class ScorePage<T>(val items: List<T>, val nextOffset: Int?)
data class ScoreProfile(val game: ScoreGame, val id: String, val displayName: String?)
data class ScoreArchiveBatch(
    val ref: HtmlBatchRef,
    val status: HtmlBatchStatus,
    val htmlCount: Int,
    val htmlBytes: Long,
    val json: ScoreSnapshotRef?,
)
data class ScoreSnapshotRef(val batch: HtmlBatchRef, val id: String, val name: String, val bytes: Long)
data class ScoreArchiveDetail(val batch: ScoreArchiveBatch, val html: List<HtmlPageRef>)
data class ScoreTextPreview(val text: String, val truncated: Boolean)

enum class ScoreImportState { WAITING, PARTIAL, IMPORTED, FAILED }
data class ScoreImportProgress(val state: ScoreImportState, val profileId: String? = null)

/** Private local archive views. Run reads off the UI thread. */
interface ScoreArchiveQueries {
    fun list(game: ScoreGame, offset: Int = 0, limit: Int = 30): ScorePage<ScoreArchiveBatch>
    fun detail(batch: HtmlBatchRef): ScoreArchiveDetail
    fun previewHtml(page: HtmlPageRef): ScoreTextPreview
    fun previewJson(snapshot: ScoreSnapshotRef): ScoreTextPreview
    fun importProgress(batch: HtmlBatchRef): ScoreImportProgress
}

enum class ScoreRecordKind { BEST, RECENT }
data class ScoreFilter(
    val kind: ScoreRecordKind = ScoreRecordKind.BEST,
    val difficulty: HtmlPageKind? = null,
    val songId: Int? = null,
)

data class PersonalScore(
    val id: Long,
    val batchId: String,
    val capturedAt: Instant,
    val kind: ScoreRecordKind,
    val title: String,
    val difficulty: HtmlPageKind,
    val chartType: String,
    val songId: Int?,
    /** Percentage in units of 0.0001%. */
    val achievement: Int?,
    val score: Int?,
    val dxScore: Int?,
    val maxDxScore: Int?,
    val level: String?,
    val rank: String?,
    val combo: String?,
    val sync: String?,
    val special: String?,
    val playedAt: String?,
    val clear: String? = null,
    val worldsEndStar: Int? = null,
)

/** Committed observations, newest batch first. Queries are scoped to one confirmed profile. */
interface PersonalScoreQueries {
    fun profiles(game: ScoreGame): List<ScoreProfile>
    fun scores(profile: ScoreProfile, filter: ScoreFilter = ScoreFilter(), offset: Int = 0, limit: Int = 50): ScorePage<PersonalScore>
    fun scoreDetail(profile: ScoreProfile, id: Long): PersonalScore?
}
