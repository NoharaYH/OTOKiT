package com.otokit.noharayh.transfer.net

import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException

class NetReadRetryTest {
    @Test fun transportFailureRetriesBoundedGetAndReportsEachRetry() {
        var calls = 0
        var retries = 0
        val waits = mutableListOf<Long>()
        val result = NetReadRetry({ false }, { retries++ }, { waits.add(it) }).run(true) {
            if (calls++ < 2) throw EOFException()
            "complete"
        }
        assertEquals("complete", result)
        assertEquals(3, calls)
        assertEquals(2, retries)
        assertEquals(listOf(500L, 1000L), waits)
    }

    @Test fun postHttpRejectionAndCancellationAreNotReplayed() {
        listOf(false to false, true to true).forEach { (safe, cancelled) ->
            var calls = 0
            assertThrows(EOFException::class.java) {
                NetReadRetry({ cancelled }, { fail("Unexpected retry") }, {}).run(safe) { calls++; throw EOFException() }
            }
            assertEquals(1, calls)
        }
        assertThrows(NetHttpException::class.java) {
            NetReadRetry({ false }, { fail("HTTP rejection must not retry") }, {}).run(true) { throw NetHttpException(429) }
        }
    }

    @Test fun repeatedReadFailureStopsAfterThreeAttempts() {
        var attempts = 0
        assertThrows(EOFException::class.java) {
            NetReadRetry({ false }, {}, {}).run(true) { attempts++; throw EOFException() }
        }
        assertEquals(3, attempts)
    }
}
