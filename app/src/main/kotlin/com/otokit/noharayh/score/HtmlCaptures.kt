package com.otokit.noharayh.score

import java.io.InputStream
import java.time.Instant

enum class ScoreGame(val key: String) { MAIMAI("maimai"), CHUNITHM("chunithm") }

enum class HtmlPageKind(val suffix: String) {
    PLAYER("Player"), RECENT("Recent"), BASIC("Basic"), ADVANCED("Advanced"),
    EXPERT("Expert"), MASTER("Master"), REMASTER("ReMaster"), ULTIMA("Ultima"),
    UTAGE("Utage"), WORLDS_END("WorldsEnd"), LOGIN_DIAGNOSTIC("LoginDiagnostic"), FAILED_RESPONSE("FailedResponse");

    val isDiagnostic: Boolean get() = this in listOf(LOGIN_DIAGNOSTIC, FAILED_RESPONSE)
    val isDifficulty: Boolean get() = this !in listOf(PLAYER, RECENT) && !isDiagnostic

    internal val chartIndex: Int get() = when (this) {
        BASIC -> 0
        ADVANCED -> 1
        EXPERT -> 2
        MASTER -> 3
        REMASTER, ULTIMA -> 4
        UTAGE, WORLDS_END -> 5
        else -> error("Page does not represent a chart difficulty")
    }
}

enum class HtmlBatchStatus { RUNNING, COMPLETED, FAILED, CANCELLED, TIMED_OUT, INTERRUPTED }
enum class HtmlValidity { VALID, VALID_EMPTY_SCORES, UNRECOGNIZED, BUSINESS_ERROR }

/** Counts refer to actual numeric score fields, not just chart titles. */
data class HtmlPageEvidence(
    val validity: HtmlValidity,
    val chartCount: Int = 0,
    val scoreCount: Int = 0,
    val businessErrorCode: String? = null,
)

/** An unidentified batch is quarantined; it must not be merged into a user profile. */
data class HtmlBatchRef(val id: String, val game: ScoreGame, val startedAt: Instant)
data class HtmlPageRef(
    val batch: HtmlBatchRef,
    val kind: HtmlPageKind,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
    val capturedAt: Instant,
)
data class HtmlBatchRecord(
    val batch: HtmlBatchRef,
    val taskId: String,
    val status: HtmlBatchStatus,
    val expectedPages: List<HtmlPageKind>,
    val pages: List<HtmlPageRef>,
    val evidence: Map<HtmlPageKind, HtmlPageEvidence>,
    val fileStamp: String,
)

/** The score boundary owns paths and indexes. Consumers exchange stable references. */
interface HtmlCaptures {
    fun begin(game: ScoreGame, expected: List<HtmlPageKind>, taskId: String, startedAt: Instant = Instant.now()): HtmlBatchRef
    fun save(batch: HtmlBatchRef, kind: HtmlPageKind, source: InputStream, cancelled: () -> Boolean): HtmlPageRef
    fun open(page: HtmlPageRef): InputStream
    fun recordEvidence(page: HtmlPageRef, evidence: HtmlPageEvidence)
    fun list(game: ScoreGame): List<HtmlBatchRef>
    fun read(batch: HtmlBatchRef): HtmlBatchRecord
    fun finish(batch: HtmlBatchRef, status: HtmlBatchStatus)
    fun recoverInterrupted()
}
