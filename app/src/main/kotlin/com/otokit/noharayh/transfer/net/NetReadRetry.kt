package com.otokit.noharayh.transfer.net

import java.io.IOException

/** Only replay-safe NET GET reads may retry. OAuth login and POST never use this policy. */
internal class NetReadRetry(
    private val cancelled: () -> Boolean,
    private val onRetry: () -> Unit,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun <T> run(replaySafe: Boolean, operation: () -> T): T {
        var retries = 0
        while (true) {
            try { return operation() }
            catch (error: IOException) {
                if (!replaySafe || error is NetHttpException || cancelled() || Thread.currentThread().isInterrupted || retries == 2) throw error
                retries++
                onRetry()
                pause(500L * retries)
            }
        }
    }
}
