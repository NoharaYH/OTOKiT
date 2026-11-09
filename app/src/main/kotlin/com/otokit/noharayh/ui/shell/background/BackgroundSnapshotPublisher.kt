package com.otokit.noharayh.ui.shell.background

/** 背景快照的局部订阅：独立句柄、有序重入，派发中解绑/销毁立即生效。 */
internal class BackgroundSnapshotPublisher(initial: BackgroundSnapshot) {
    var current: BackgroundSnapshot = initial
        private set

    private class Subscription(
        val listener: (BackgroundSnapshot) -> Unit,
        var lastRevision: Long,
    )

    private data class Event(val revision: Long, val snapshot: BackgroundSnapshot)

    private val listeners = linkedMapOf<Long, Subscription>()
    private val pending = ArrayDeque<Event>()
    private var nextId = 0L
    private var revision = 0L
    private var dispatching = false
    private var disposed = false

    fun subscribe(listener: (BackgroundSnapshot) -> Unit): AutoCloseable {
        if (disposed) return AutoCloseable { }
        val id = nextId++
        listeners[id] = Subscription(listener, revision)
        try {
            listener(current)
        } catch (failure: Throwable) {
            listeners.remove(id)
            throw failure
        }
        return AutoCloseable { listeners.remove(id) }
    }

    fun publish(next: BackgroundSnapshot) {
        if (disposed || next == current) return
        current = next
        pending.addLast(Event(++revision, next))
        if (dispatching) return

        dispatching = true
        try {
            while (!disposed && pending.isNotEmpty()) {
                val event = pending.removeFirst()
                for ((id, subscription) in listeners.toList()) {
                    if (disposed) break
                    if (listeners[id] === subscription && event.revision > subscription.lastRevision) {
                        subscription.lastRevision = event.revision
                        subscription.listener(event.snapshot)
                    }
                }
            }
        } finally {
            dispatching = false
        }
    }

    fun dispose() {
        disposed = true
        listeners.clear()
        pending.clear()
    }
}
