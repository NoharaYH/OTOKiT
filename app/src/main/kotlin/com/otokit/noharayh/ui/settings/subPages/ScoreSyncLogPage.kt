package com.otokit.noharayh.ui.settings.subPages

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.otokit.noharayh.ui.kit.common.ContentScrollView
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.exportPreferences
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogRef
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogSummary
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogs
import com.otokit.noharayh.platform.DocumentZipExport
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsChoiceDimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialogAction
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordList
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsCompactButton
import com.otokit.noharayh.ui.kit.settings.SettingsHeaderActions
import com.otokit.noharayh.ui.settings.components.ExportPrompts
import org.json.JSONException
import java.io.IOException
import java.util.Date
import java.util.concurrent.Executors

/** 同步日志的内部记录／预览流程；只调用日志公开入口，不探测文件路径。 */
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
    private val list = ArchiveRecordList(context, context.getString(R.string.settings_archive_scroll), ::updateRangeSelection)
    private val status = ViewPrimitives.text(context, "", 14f, SettingsComponentColors.TEXT)
    private val previewText = ViewPrimitives.text(context, "", 13f, SettingsComponentColors.TEXT).apply {
        setTextIsSelectable(true)
        contentDescription = context.getString(R.string.settings_sync_logs_preview)
    }
    private val preview = ContentScrollView(context).apply {
        visibility = GONE
        addView(previewText, FrameLayout.LayoutParams(-1, -2))
    }
    private val viewAction = SettingsButton(context, context.getString(R.string.settings_sync_logs_view),
        SettingsButton.Kind.Action, onClick = ::showDetail)
    private val actions = PressFrontColumn(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val removeAction = addHeaderAction(R.string.settings_archive_delete, SettingsComponentColors.ERROR_TEXT, ::confirmDeletion)
    private val exportAction = addHeaderAction(R.string.settings_archive_export, SettingsComponentColors.SELECTED, ::confirmExport)
    private val cancelAction = addHeaderAction(R.string.settings_archive_cancel, SettingsComponentColors.TEXT) {
        selected.clear()
        list.clearRangeAnchor()
        renderSelection()
    }
    private val listAction = addHeaderAction(R.string.settings_sync_logs_list, SettingsComponentColors.TEXT) { closeDetail() }
    private val copyAction = addHeaderAction(R.string.settings_sync_logs_copy, SettingsComponentColors.SELECTED, ::copyDetail)
    private val idle = SettingsButton(context,
        context.getString(if (split) R.string.settings_archive_back else R.string.settings_sync_logs_refresh),
        SettingsButton.Kind.Action,
        trailingIcon = if (split) SettingsButton.ActionIcon.Back else null,
        contentHeight = dp(SettingsCardDimensions.HEADER_HEIGHT_DP), onClick = {
            if (split) onBack() else if (!busy) load()
        })
    private val header = SettingsHeaderActions(context, idle, actions)
    private val card: SettingsCard

    var showingDetail = false
        private set

    init {
        setPadding(if (split) 0 else margin, margin, margin, margin)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(viewAction, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        card = SettingsCard(context, R.drawable.ic_archive_record_24, R.string.settings_sync_logs,
            content = content, trailing = header, fillContent = true)
        addView(card, LayoutParams(-1, -1))
        status.visibility = GONE
        renderSelection()
    }

    private fun addHeaderAction(label: Int, color: Int, action: () -> Unit): SettingsCompactButton {
        val button = SettingsCompactButton(context, context.getString(label), color) { if (!busy) action() }
            .apply { deferClickUntilReleaseFeedback = true }
        actions.addView(button, LinearLayout.LayoutParams(button.minimumWidth, button.minimumHeight).apply {
            if (actions.childCount > 0) marginStart = dp(SettingsChoiceDimensions.GAP_DP)
        })
        return button
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
        list.rangeSelectionEnabled = !busy && !showingDetail
        list.setSelection(selected.mapNotNull(rows::get).toSet())
        card.setTitle(if (selected.isEmpty() || showingDetail) context.getString(R.string.settings_sync_logs)
            else context.getString(R.string.settings_archive_selected, selected.size))
        header.showActions(selected.isNotEmpty() || showingDetail)
        listOf(removeAction, exportAction, cancelAction).forEach { it.visibility = if (showingDetail) GONE else VISIBLE }
        listOf(listAction, copyAction).forEach { it.visibility = if (showingDetail) VISIBLE else GONE }
        removeAction.isEnabled = !busy && !writing
        exportAction.isEnabled = !busy && !writing
        cancelAction.isEnabled = !busy
        listAction.isEnabled = !busy
        copyAction.isEnabled = !busy
        viewAction.visibility = if (!showingDetail && selected.size == 1) VISIBLE else GONE
        viewAction.isEnabled = !busy
        idle.isEnabled = !busy
        list.visibility = if (showingDetail) GONE else VISIBLE
        preview.visibility = if (showingDetail) VISIBLE else GONE
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
                    list.removeRows(removed) {
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

    private fun showDetail() {
        val ref = selected.singleOrNull() ?: return
        busy = true
        renderSelection()
        worker.execute {
            val value = try { logs.preview(ref) }
            catch (_: IOException) { null }
            catch (_: JSONException) { null }
            catch (_: IllegalStateException) { null }
            main.post {
                if (!isAttachedToWindow) return@post
                busy = false
                if (value == null) notice(context.getString(R.string.settings_sync_logs_read_failed))
                else {
                    showingDetail = true
                    previewText.text = value.text
                    status.text = if (value.truncated) context.getString(R.string.settings_sync_logs_preview_truncated) else ""
                    status.visibility = if (value.truncated) VISIBLE else GONE
                    preview.scrollTo(0, 0)
                }
                renderSelection()
            }
        }
    }

    fun closeDetail(): Boolean {
        if (!showingDetail) return false
        showingDetail = false
        previewText.text = ""
        renderSelection()
        renderStatus()
        return true
    }

    private fun copyDetail() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_sync_logs), previewText.text))
        notice(context.getString(R.string.settings_sync_logs_copied))
    }

    private fun showDialog(next: MessageDialog) {
        dialog = next
        next.setOnDismissListener { if (dialog === next) dialog = null }
        next.showWithin(rootView.width)
    }

    private fun load() {
        if (busy || showingDetail) return
        busy = true
        status.setText(R.string.settings_archive_loading)
        status.visibility = VISIBLE
        renderSelection()
        worker.execute {
            val values = try { logs.list() }
            catch (_: IOException) { null }
            catch (_: IllegalStateException) { null }
            main.post {
                if (!isAttachedToWindow) return@post
                busy = false
                if (values == null) {
                    status.setText(R.string.settings_sync_logs_read_failed)
                    status.visibility = VISIBLE
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
                    list.setRows(rows.values.toList())
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
        status.visibility = if (label != null) VISIBLE else GONE
        if (label != null) status.setText(label)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!loaded) load() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && isAttachedToWindow && loaded && !busy && !showingDetail) load()
    }
    override fun onDetachedFromWindow() {
        dialog?.dismiss()
        dialog = null
        exporter.close()
        worker.shutdown()
        super.onDetachedFromWindow()
    }

    private fun notice(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
