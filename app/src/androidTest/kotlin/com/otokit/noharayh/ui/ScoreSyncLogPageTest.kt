package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.view.allViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogRef
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogs
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordRow
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.settings.subPages.ScoreSyncLogPage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ScoreSyncLogPageTest : UiComponentTestHost() {
    private lateinit var root: File
    private lateinit var logs: ScoreSyncLogs
    private lateinit var completed: ScoreSyncLogRef
    private lateinit var running: ScoreSyncLogRef
    private var page: ScoreSyncLogPage? = null

    @Before fun seedIsolatedLogs() {
        root = Files.createTempDirectory(activity.cacheDir.toPath(), "sync-log-ui-").toFile()
        logs = ScoreSyncLogs(root, "https://example.invalid/repo", "ui-test")
        val start = Instant.parse("2026-11-20T10:20:30Z")
        logs.start("completed", "maimai", start)!!.apply {
            event("TEST", "fixture-complete-message")
            finish("COMPLETED")
        }
        logs.start("running", "maimai", start.plusSeconds(1))!!.event("TEST", "fixture-running-message")
        completed = logs.list().single { !it.writing }.ref
        running = logs.list().single { it.writing }.ref
    }

    @After fun removeIsolatedLogs() {
        main { page?.let(host::removeView) }
        logs.find(running.taskId)?.finish("CANCELLED")
        assertTrue(root.deleteRecursively())
    }

    @Test fun selectionKeepsWritingLogsAndOnlyOffersRecordActions() {
        lateinit var mounted: ScoreSyncLogPage
        main { mounted = ScoreSyncLogPage(activity, 20, false, logs) {}; page = mounted }
        mount(mounted, activity.resources.displayMetrics.widthPixels - 160, -1)
        awaitMain { recordRows(mounted).size == 2 }
        val runningLabel = activity.getString(R.string.settings_sync_logs_running)
        val completedLabel = activity.getString(R.string.settings_sync_logs_completed)
        main {
            assertEquals(recordRows(mounted).map { it.button }.toSet(),
                mounted.allViews.filterIsInstance<SettingsButton>().toSet())
            recordRows(mounted).single { it.button.contentDescription.toString().contains(runningLabel) }.button.performClick()
            assertFalse(action(mounted, R.string.settings_archive_delete).isEnabled)
            assertFalse(action(mounted, R.string.settings_archive_export).isEnabled)
            action(mounted, R.string.settings_archive_delete).performClick()
            assertNull(dialog(mounted))
            recordRows(mounted).single { it.button.contentDescription.toString().contains(completedLabel) }.button.performClick()
            assertFalse(action(mounted, R.string.settings_archive_delete).isEnabled)
        }
        awaitMain {
            mounted.allViews.filterIsInstance<TextView>().any {
                it.text.toString() == activity.getString(R.string.settings_archive_selected, 2)
            }
        }
        main {
            recordRows(mounted).single { it.button.contentDescription.toString().contains(runningLabel) }.button.performClick()
            assertTrue(action(mounted, R.string.settings_archive_delete).isEnabled)
            assertTrue(action(mounted, R.string.settings_archive_export).isEnabled)
            assertEquals(2, recordRows(mounted).size)
            assertEquals(recordRows(mounted).map { it.button }.toSet(),
                mounted.allViews.filterIsInstance<SettingsButton>().toSet())
            assertFalse(mounted.allViews.filterIsInstance<TextView>().any { it.text.contains("fixture-complete-message") })
            action(mounted, R.string.settings_archive_delete).performClick()
        }
        val confirmation = dialog(mounted)!!
        main {
            action(confirmation.window!!.decorView, R.string.settings_archive_delete_confirm).performClick()
        }
        awaitMain { recordRows(mounted).size == 1 && !action(mounted, R.string.settings_archive_cancel).isShown }
        assertEquals(setOf(running), logs.list().map { it.ref }.toSet())
        assertFalse(logs.list().any { it.ref == completed })
    }

    @Test fun splitLayoutReturnsFromTheListAndRestoresBackAfterCancellingSelection() {
        lateinit var mounted: ScoreSyncLogPage
        var backs = 0
        main { mounted = ScoreSyncLogPage(activity, 20, true, logs) { backs++ }; page = mounted }
        mount(mounted, activity.resources.displayMetrics.widthPixels - 160, -1)
        awaitMain { recordRows(mounted).size == 2 }
        main {
            action(mounted, R.string.settings_archive_back).performClick()
            assertEquals(1, backs)
            recordRows(mounted).first().button.performClick()
        }
        awaitMain { !action(mounted, R.string.settings_archive_back).isShown }
        main { action(mounted, R.string.settings_archive_cancel).performClick() }
        awaitMain { action(mounted, R.string.settings_archive_back).isShown }
        main {
            assertEquals(2, recordRows(mounted).size)
            assertEquals(recordRows(mounted).map { it.button }.toSet() + action(mounted, R.string.settings_archive_back),
                mounted.allViews.filterIsInstance<SettingsButton>().toSet())
        }
    }

    private fun recordRows(view: View) = view.allViews.filterIsInstance<ArchiveRecordRow>().toList()
    private fun action(view: View, label: Int) = view.allViews.single {
        it.contentDescription == activity.getString(label)
    }
    private fun dialog(view: ScoreSyncLogPage): MessageDialog? = ScoreSyncLogPage::class.java
        .getDeclaredField("dialog").apply { isAccessible = true }.get(view) as MessageDialog?
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 4000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("The expected sync log UI state did not arrive")
    }
}
