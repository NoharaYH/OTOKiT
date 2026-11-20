package com.otokit.noharayh.ui

import android.content.SharedPreferences
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.widget.TextView
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.exportPreferences
import com.otokit.noharayh.score.*
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordRow
import com.otokit.noharayh.ui.settings.subPages.ScoreArchiveKind
import com.otokit.noharayh.ui.settings.subPages.ScoreArchivePage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.OutputStream
import java.time.Instant

class HtmlExportFlowTest : UiComponentTestHost() {
    private lateinit var storage: SharedPreferences
    private var original = emptyMap<String, Boolean?>()

    @Before fun clearExportPreferences() {
        storage = activity.getSharedPreferences("export_preferences", 0)
        original = listOf("redact_exports", "export_warning_acknowledged").associateWith {
            if (storage.contains(it)) storage.getBoolean(it, false) else null
        }
        storage.edit().apply { original.keys.forEach { remove(it) } }.commit()
    }

    @After fun restoreExportPreferences() {
        storage.edit().apply {
            original.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) }
        }.commit()
    }

    @Test fun firstHtmlExportShowsStyledWarningThenChoicesAndRemembersConfirmation() {
        exportPreferences(activity).setRedactExports(false)
        var page = openPage(ScoreArchiveKind.Html)
        requestExport(page)
        val warning = currentDialog(page)
        main {
            assertFalse(exportPreferences(activity).exportWarningAcknowledged())
            assertFalse(exportPreferences(activity).redactExports())
            assertEquals("开启脱敏处理", activity.getString(R.string.settings_export_warning_enable))
            assertEquals("关闭脱敏处理", activity.getString(R.string.settings_export_warning_disable))
            for (label in listOf(R.string.settings_export_warning_enable, R.string.settings_export_warning_disable)) {
                assertTrue(warning.window!!.decorView.allViews.any { it.contentDescription == activity.getString(label) })
            }
            assertFalse(warning.window!!.decorView.allViews.any {
                it.contentDescription == activity.getString(R.string.settings_archive_nevermind)
            })
            val text = warning.window!!.decorView.allViews.filterIsInstance<TextView>()
                .single { it.text.toString() == WARNING }.text as Spanned
            val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            assertEquals(setOf("账号的敏感数据", "脱敏处理"), spans.map {
                assertEquals(SettingsComponentColors.WARNING, it.foregroundColor)
                text.subSequence(text.getSpanStart(it), text.getSpanEnd(it)).toString()
            }.toSet())
            assertEquals(2, spans.size)
        }
        click(warning, R.string.settings_export_warning_enable)
        main {
            assertFalse(warning.isShowing)
            assertTrue(exportPreferences(activity).redactExports())
            assertTrue(exportPreferences(activity).exportWarningAcknowledged())
            assertChoices(currentDialog(page))
        }
        click(currentDialog(page), R.string.settings_archive_nevermind)
        main { host.removeView(page) }
        page = openPage(ScoreArchiveKind.Html)
        requestExport(page)
        main {
            assertTrue(exportPreferences(activity).redactExports())
            assertTrue(exportPreferences(activity).exportWarningAcknowledged())
            assertChoices(currentDialog(page))
        }
    }

    @Test fun choosingUnredactedExportPersistsTheChoiceAndAcknowledgesWarning() {
        assertTrue(exportPreferences(activity).redactExports())
        var page = openPage(ScoreArchiveKind.Html)
        requestExport(page)
        val warning = currentDialog(page)
        click(warning, R.string.settings_export_warning_disable)
        main {
            assertFalse(warning.isShowing)
            assertFalse(exportPreferences(activity).redactExports())
            assertTrue(exportPreferences(activity).exportWarningAcknowledged())
            assertChoices(currentDialog(page))
        }
        click(currentDialog(page), R.string.settings_archive_nevermind)
        main { host.removeView(page) }
        page = openPage(ScoreArchiveKind.Html)
        requestExport(page)
        main {
            assertFalse(exportPreferences(activity).redactExports())
            assertTrue(exportPreferences(activity).exportWarningAcknowledged())
            assertChoices(currentDialog(page))
        }
    }

    @Test fun backingOutOfWarningDoesNotChangeRedactionOrAcknowledgeIt() {
        val page = openPage(ScoreArchiveKind.Html)
        requestExport(page)
        val warning = currentDialog(page)
        awaitMain { warning.window!!.decorView.hasWindowFocus() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
        main {
            assertFalse(warning.isShowing)
            assertTrue(exportPreferences(activity).redactExports())
            assertFalse(exportPreferences(activity).exportWarningAcknowledged())
        }
        requestExport(page, selectRecord = false)
        main {
            assertTrue(currentDialog(page).window!!.decorView.allViews.filterIsInstance<TextView>()
                .any { it.text.toString() == WARNING })
            assertTrue(exportPreferences(activity).redactExports())
            assertFalse(exportPreferences(activity).exportWarningAcknowledged())
        }
    }

    @Test fun jsonExportSkipsWarningWithoutAcknowledgingHtmlExport() {
        val page = openPage(ScoreArchiveKind.Json)
        requestExport(page)
        main {
            assertChoices(currentDialog(page))
            assertFalse(exportPreferences(activity).exportWarningAcknowledged())
        }
    }

    private fun openPage(kind: ScoreArchiveKind): ScoreArchivePage {
        val batch = HtmlBatchRef("html-export-flow-test", ScoreGame.MAIMAI, Instant.parse("2026-11-20T00:00:00Z"))
        val summary = ScoreArchiveBatch(batch, HtmlBatchStatus.COMPLETED, 1, 64,
            ScoreSnapshotRef(batch, "fixture", "fixture.json", 32))
        val queries = object : ScoreArchiveQueries {
            override fun listBatches() = listOf(summary)
            override fun detail(batch: HtmlBatchRef): ScoreArchiveDetail = error("Unexpected detail query")
            override fun previewHtml(page: HtmlPageRef): ScoreTextPreview = error("Unexpected HTML preview")
            override fun previewJson(snapshot: ScoreSnapshotRef): ScoreTextPreview = error("Unexpected JSON preview")
            override fun importProgress(batch: HtmlBatchRef): ScoreImportProgress = error("Unexpected import query")
        }
        val actions = object : ScoreArchiveActions {
            override fun remove(format: ScoreArchiveFormat, batches: Set<HtmlBatchRef>, done: (ScoreArchiveRemoval) -> Unit) {
                error("The export dialog must not delete records")
            }
            override fun export(format: ScoreArchiveFormat, batches: Set<HtmlBatchRef>, redact: Boolean,
                destination: ((OutputStream) -> Unit) -> Unit, done: (Boolean) -> Unit) {
                error("Choosing an export method must remain an explicit action")
            }
        }
        lateinit var page: ScoreArchivePage
        main { page = ScoreArchivePage(activity, kind, 20, false, queries, actions) {} }
        mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
        awaitMain { page.allViews.filterIsInstance<ArchiveRecordRow>().any() }
        return page
    }

    private fun requestExport(page: ScoreArchivePage, selectRecord: Boolean = true) {
        if (selectRecord) main { page.allViews.filterIsInstance<ArchiveRecordRow>().single().button.performClick() }
        val label = activity.getString(R.string.settings_archive_export)
        awaitMain { page.allViews.single { it.contentDescription == label }.isShown }
        main { page.allViews.single { it.contentDescription == label }.performClick() }
        instrumentation.waitForIdleSync()
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Archive UI did not become ready")
    }

    private fun currentDialog(page: ScoreArchivePage): MessageDialog =
        ScoreArchivePage::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(page) as MessageDialog

    private fun click(dialog: MessageDialog, label: Int) {
        main { dialog.window!!.decorView.allViews.single { it.contentDescription == activity.getString(label) }.performClick() }
        instrumentation.waitForIdleSync()
    }

    private fun assertChoices(dialog: MessageDialog) {
        assertTrue(dialog.isShowing)
        val views = dialog.window!!.decorView.allViews.toList()
        for (label in listOf(R.string.settings_archive_export_open, R.string.settings_archive_export_share,
            R.string.settings_archive_nevermind)) {
            assertTrue(views.any { it.contentDescription == activity.getString(label) })
        }
        assertFalse(views.filterIsInstance<TextView>().any { it.text.toString() == WARNING })
    }

    private companion object {
        const val WARNING = "请注意！导出的内容涉及您账号的敏感数据。如不希望被不法分子利用，请选择对导出内容进行脱敏处理！\n\n该选项后续可在数据与同步设置-导出内容脱敏进行开关。"
    }
}
