package com.otokit.noharayh.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ScoreTokenRecordingTest {
    @Test fun queuedTokenWorkKeepsRecordingUntilWorkerExitsAfterCallerInterruption() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "score-token-recording-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(preferenceName, mode)
        }
        val auth = OAuthConnections(context)
        val worker = OAuthConnections::class.java.getDeclaredField("worker").apply { isAccessible = true }
            .get(auth) as ExecutorService
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val retained = CountDownLatch(1)
        val released = CountDownLatch(1)
        val active = AtomicInteger()
        val callerFailure = AtomicReference<Throwable>()
        val caller = Thread {
            try {
                auth.scoreAccessToken(OAuthProvider.LXNS, "fixture-not-an-account", "write_player", null, retain = {
                    active.incrementAndGet(); retained.countDown()
                    Closeable { active.decrementAndGet(); released.countDown() }
                })
            } catch (failure: Throwable) { callerFailure.set(failure) }
        }
        try {
            worker.submit { entered.countDown(); proceed.await(5, TimeUnit.SECONDS) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            caller.start()
            assertTrue(retained.await(5, TimeUnit.SECONDS))
            caller.interrupt()
            caller.join(5000)
            assertFalse(caller.isAlive)
            assertTrue(callerFailure.get() is InterruptedException)
            assertEquals(1, active.get())
            assertEquals(1L, released.count)
            proceed.countDown()
            assertTrue(released.await(5, TimeUnit.SECONDS))
            assertEquals(0, active.get())
        } finally {
            proceed.countDown()
            caller.interrupt()
            caller.join(5000)
            worker.shutdown()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
            base.deleteSharedPreferences(preferenceName)
        }
    }
}
