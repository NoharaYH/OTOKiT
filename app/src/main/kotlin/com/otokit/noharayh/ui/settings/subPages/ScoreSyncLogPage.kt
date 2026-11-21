package com.otokit.noharayh.ui.settings.subPages

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.FrameLayout
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.exportPreferences
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogRef
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogSummary
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogs
import com.otokit.noharayh.platform.DocumentZipExport
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialogAction
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordList
import com.otokit.noharayh.ui.kit.settings.SettingsRecordPage
import com.otokit.noharayh.ui.settings.components.ExportPrompts
import java.io.IOException
import java.util.Date
import java.util.concurrent.Executors

/** 第三级同步日志记录页；只调用日志公开入口，不探测文件路径。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ScoreSyncLogPage(
    context: Context,
    margin: Int,
    split: Boolean,
    private val logs: ScoreSyncLogs,
    onBack: () -> Unit,
) : FrameLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val exporter = DocumentZipExport(context)
    private val preferences = exportPreferences(context)
    private var dialog: MessageDialog? = null
    private var loaded = false
    private var busy = false
    private var summaries = emptyMap<ScoreSyncLogRef, ScoreSyncLogSummary>()
    private var rows = emptyMap<ScoreSyncLogRef, ArchiveRecordList.Row>()
    private val selected = linkedSetOf<ScoreSyncLogRef>()
    private val records = SettingsRecordPage(context, R.string.settings_sync_logs, margin, split,
        onRangeSelectionChanged = ::updateRangeSelection,
        onDelete = ::confirmDeletion,
        onExport = ::confirmExport,
        onCancel = { selected.clear(); renderSelection() },
        onBack = onBack)

    init {
        addView(records, LayoutParams(-1, -1))
        renderSelection()
    }

    private fun toggle(ref: ScoreSyncLogRef) {
        if (busy) return
        if (!selected.remove(ref)) selected.add(ref)
        renderSelection()
    }

    private fun updateRangeSelection(changed: Set<ArchiveRecordList.Row>, select: Boolean) {
        if (busy) return
        rows.forEach { (ref, row) -> if (row in changed) {
            if (select) selected.add(ref) else selected.remove(ref)
        } }
        renderSelection()
    }

    private fun renderSelection() {
        val writing = selected.any { summaries[it]?.writing == true }
        records.renderSelection(selected.mapNotNull(rows::get).toSet(), busy,
            canDelete = !writing, canExport = !writing)
    }

    private fun confirmDeletion() {
        if (selected.isEmpty() || selected.any { summaries[it]?.writing == true }) return
        val refs = selected.toSet()
        showDialog(MessageDialog(context, R.drawable.ic_warning_24,
            context.getString(R.string.settings_sync_logs_delete, refs.size),
            context.getString(R.string.settings_archive_delete_confirm), context.getString(R.string.settings_archive_nevermind)) {
            busy = true
            renderSelection()
            worker.execute {
                val result = logs.remove(refs)
                main.post {
                    if (!isAttachedToWindow) return@post
                    val removed = result.removed.mapNotNull(rows::get).toSet()
                    records.removeRows(removed) {
                        rows = rows - result.removed
                        summaries = summaries - result.removed
                        selected.removeAll(result.removed)
                        busy = false
                        renderSelection()
                        renderStatus()
                        if (result.skipped.isNotEmpty()) notice(context.getString(R.string.settings_sync_logs_delete_skipped,
                            result.removed.size, result.skipped.size))
                    }
                }
            }
        })
    }

    private fun confirmExport() {
        if (dialog != null || selected.isEmpty() || selected.any { summaries[it]?.writing == true }) return
        val refs = selected.toSet()
        ExportPrompts(context, preferences, ::showDialog, { isAttachedToWindow }).start(true) {
            val message = context.getString(R.string.settings_archive_export_message, refs.size, "logs")
            showDialog(MessageDialog(context, R.drawable.ic_archive_record_24, message,
                context.getString(R.string.settings_archive_export_open), context.getString(R.string.settings_archive_nevermind),
                accentColor = SettingsComponentColors.SELECTED,
                additionalAction = MessageDialogAction(context.getString(R.string.settings_archive_export_share),
                    SettingsComponentColors.SELECTED) { export(refs, true) }, cancelOnTouchOutside = false) {
                export(refs, false)
            })
        }
    }

    private fun export(refs: Set<ScoreSyncLogRef>, share: Boolean) {
        val redact = preferences.redactExports()
        val includeHtml = preferences.includeHtmlWithLogs()
        busy = true
        renderSelection()
        exporter.export("logs", "Log", write = { destination, done ->
            worker.execute { logs.export(refs, redact, includeHtml, destination, done) }
        }, written = { uri -> main.post {
            if (!isAttachedToWindow) return@post
            busy = false
            renderSelection()
            try { DocumentZipExport.open(context, uri, share) }
            catch (_: ActivityNotFoundException) { notice(context.getString(R.string.settings_archive_export_no_app)) }
        } }, stopped = { reason -> main.post {
            if (!isAttachedToWindow) return@post
            busy = false
            renderSelection()
            if (reason != null) notice(context.getString(R.string.settings_sync_logs_export_failed))
        } })
    }

    private fun showDialog(next: MessageDialog) {
        dialog = next
        next.setOnDismissListener { if (dialog === next) dialog = null }
        next.showWithin(rootView.width)
    }

    private fun load() {
        if (busy) return
        busy = true
        records.showStatus(R.string.settings_archive_loading)
        renderSelection()
        worker.execute {
            val values = try { logs.list() }
            catch (_: IOException) { null }
            catch (_: IllegalStateException) { null }
            main.post {
                if (!isAttachedToWindow) return@post
                busy = false
                if (values == null) {
                    records.showStatus(R.string.settings_sync_logs_read_failed)
                } else {
                    loaded = true
                    summaries = values.associateBy { it.ref }
                    val date = DateFormat.getDateFormat(context)
                    val time = DateFormat.getTimeFormat(context)
                    rows = values.associate { summary ->
                        val instant = Date(summary.ref.startedAt.toEpochMilli())
                        val game = context.getString(if (summary.ref.game == "maimai") R.string.settings_archive_mai
                            else R.string.settings_archive_chu)
                        val label = context.getString(R.string.settings_sync_logs_row, game,
                            "${date.format(instant)} ${time.format(instant)}", context.getString(statusLabel(summary)))
                        summary.ref to ArchiveRecordList.Row(label) { toggle(summary.ref) }
                    }
                    selected.retainAll(rows.keys)
                    records.setRows(rows.values.toList())
                    renderStatus()
                }
                renderSelection()
            }
        }
    }

    private fun statusLabel(value: ScoreSyncLogSummary): Int = when {
        value.recordingError != null -> R.string.settings_sync_logs_recording_error
        value.writing -> R.string.settings_sync_logs_running
        else -> when (value.status) {
            "COMPLETED" -> R.string.settings_sync_logs_completed
            "PARTIAL" -> R.string.settings_sync_logs_partial
            "CANCELLED" -> R.string.settings_sync_logs_cancelled
            "TIMED_OUT" -> R.string.settings_sync_logs_timed_out
            "INTERRUPTED" -> R.string.settings_sync_logs_interrupted
            else -> R.string.settings_sync_logs_failed
        }
    }

    private fun renderStatus() {
        val label = when {
            logs.recordingError != null -> R.string.settings_sync_logs_recording_error
            rows.isEmpty() -> R.string.settings_sync_logs_empty
            else -> null
        }
        records.showStatus(label)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!loaded) load() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && isAttachedToWindow && loaded && !busy) load()
    }
    override fun onDetachedFromWindow() {
        dialog?.dismiss()
        dialog = null
        exporter.close()
        worker.shutdown()
        super.onDetachedFromWindow()
    }

    private fun notice(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
