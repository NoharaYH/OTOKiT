package com.otokit.noharayh.ui.shell.background

import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSnapshotPublisherTest {
    private val mai = BackgroundSnapshot(GameLine.Mai, 0f, BackgroundPhase.Idle)
    private val chu = BackgroundSnapshot(GameLine.Chu, 1f, BackgroundPhase.Idle)
    private fun at(progress: Float) = BackgroundSnapshot(GameLine.Mai, progress, BackgroundPhase.Dragging)

    @Test
    fun firstSubscriptionReadsRestoredChuSynchronously() {
        val publisher = BackgroundSnapshotPublisher(chu)
        val received = mutableListOf<BackgroundSnapshot>()

        publisher.subscribe(received::add)

        assertEquals(listOf(chu), received)
    }

    @Test
    fun unsubscribeDuringDeliveryStopsRemainingCopiedListener() {
        val publisher = BackgroundSnapshotPublisher(mai)
        var second: AutoCloseable? = null
        publisher.subscribe { if (it != mai) second?.close() }
        val received = mutableListOf<BackgroundSnapshot>()
        second = publisher.subscribe(received::add)

        publisher.publish(chu)
        second?.close()

        assertEquals(listOf(mai), received)
    }

    @Test
    fun disposeDuringDeliveryStopsLaterListenersAndFutureSubscriptions() {
        val publisher = BackgroundSnapshotPublisher(mai)
        publisher.subscribe { if (it != mai) publisher.dispose() }
        val received = mutableListOf<BackgroundSnapshot>()
        publisher.subscribe(received::add)

        publisher.publish(chu)
        publisher.publish(at(0.5f))
        publisher.subscribe(received::add).close()
        publisher.dispose()

        assertEquals(listOf(mai), received)
    }

    @Test
    fun identicalCallbacksHaveIndependentIdempotentHandles() {
        val publisher = BackgroundSnapshotPublisher(mai)
        val received = mutableListOf<BackgroundSnapshot>()
        val listener: (BackgroundSnapshot) -> Unit = { received.add(it) }
        val first = publisher.subscribe(listener)
        val second = publisher.subscribe(listener)
        received.clear()
        first.close()
        first.close()

        publisher.publish(chu)
        publisher.publish(chu)
        second.close()
        publisher.publish(mai)

        assertEquals(listOf(chu), received)
    }

    @Test
    fun nestedPublishingPreservesEachImmutableEventForExistingListeners() {
        val publisher = BackgroundSnapshotPublisher(mai)
        val firstEvent = at(0.2f)
        val secondEvent = at(0.6f)
        val received = mutableListOf<BackgroundSnapshot>()
        publisher.subscribe { if (it == firstEvent) publisher.publish(secondEvent) }
        publisher.subscribe(received::add)
        received.clear()

        publisher.publish(firstEvent)

        assertEquals(listOf(firstEvent, secondEvent), received)
        assertEquals(secondEvent, publisher.current)
    }

    @Test
    fun subscriptionDuringNestedPublishingSkipsQueuedEventsOlderThanInitialValue() {
        val publisher = BackgroundSnapshotPublisher(mai)
        val firstEvent = at(0.2f)
        val secondEvent = at(0.6f)
        val newestEvent = at(0.8f)
        val received = mutableListOf<BackgroundSnapshot>()
        publisher.subscribe {
            if (it == firstEvent) {
                publisher.publish(secondEvent)
                publisher.publish(newestEvent)
                publisher.subscribe(received::add)
            }
        }

        publisher.publish(firstEvent)
        publisher.publish(chu)

        assertEquals(listOf(newestEvent, chu), received)
    }
}
