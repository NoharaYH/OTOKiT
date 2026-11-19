package com.otokit.noharayh.transfer

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.HtmlPageKind

data class UploadTarget(val provider: OAuthProvider, val subject: String)
enum class UploadOutcome {
    SENDING, ACCEPTED, UPDATED, NO_CHANGES, NO_RECORDS, REJECTED, UNKNOWN,
    AUTH_REQUIRED, AUTH_UNAVAILABLE, PERMISSION_DENIED, RATE_LIMITED, UNSUPPORTED, BLOCKED,
}
data class UploadReceipt(val outcome: UploadOutcome, val httpStatus: Int? = null, val records: Int? = null) {
    val accepted get() = outcome in setOf(UploadOutcome.ACCEPTED, UploadOutcome.UPDATED, UploadOutcome.NO_CHANGES, UploadOutcome.NO_RECORDS)
}
data class UploadPageResult(val provider: OAuthProvider, val page: HtmlPageKind, val receipt: UploadReceipt)
