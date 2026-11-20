package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.content.ActivityNotFoundException
import android.os.Handler
import android.os.Looper
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.exportPreferences
import com.otokit.noharayh.platform.ScoreArchiveExport
import com.otokit.noharayh.platform.directoryName
import com.otokit.noharayh.score.HtmlBatchRef
import com.otokit.noharayh.score.ScoreArchiveActions
import com.otokit.noharayh.score.ScoreArchiveBatch
import com.otokit.noharayh.score.ScoreArchiveFormat
import com.otokit.noharayh.score.ScoreArchiveQueries
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.ui.chrome.TitleText
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsChoiceDimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialogAction
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordList
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsCompactButton
import com.otokit.noharayh.ui.kit.settings.SettingsHeaderActions
import java.io.IOException
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class ScoreArchiveKind(val title: TitleText, val format: ScoreArchiveFormat) {
    Html(TitleText(R.string.title_settings_html_records, R.string.settings_html_records), ScoreArchiveFormat.Html),
    Json(TitleText(R.string.title_settings_json_records, R.string.settings_json_records), ScoreArchiveFormat.Json),
}

/** 第三级存档页，消费记录摘要，并把选中的批次交给存档操作入口。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ScoreArchivePage(
    context: Context,
    private val kind: ScoreArchiveKind,
    margin: Int,
    split: Boolean,
    private val queries: ScoreArchiveQueries,
    private val actions: ScoreArchiveActions,
    onBack: () -> Unit,
) : FrameLayout(context) {
    private val list = ArchiveRecordList(context, context.getString(R.string.settings_archive_scroll), ::updateRangeSelection)
    private val status = ViewPrimitives.text(context, "", 14f, SettingsComponentColors.TEXT)
    private var worker: ExecutorService? = null
    private var loaded = false
    private val main = Handler(Looper.getMainLooper())
    private val exporter = ScoreArchiveExport(context, actions)
    private val exportPreferences = exportPreferences(context)
    private var records = emptyMap<HtmlBatchRef, ArchiveRecordList.Row>()
    private val selected = linkedSetOf<HtmlBatchRef>()
    private var busy = false
    private var dialog: MessageDialog? = null
    private val headerActions = PressFrontColumn(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val back = if (split) SettingsButton(context, context.getString(R.string.settings_archive_back),
        SettingsButton.Kind.Action, trailingIcon = SettingsButton.ActionIcon.Back,
        contentHeight = dp(SettingsCardDimensions.HEADER_HEIGHT_DP), onClick = onBack) else null
    private val header = SettingsHeaderActions(context, back, headerActions)
    private val card: SettingsCard

    init {
        setPadding(if (split) 0 else margin, margin, margin, margin)
        val viewport = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        addHeaderAction(R.string.settings_archive_delete, SettingsComponentColors.ERROR_TEXT,
            deferClick = true, action = ::confirmDeletion)
        addHeaderAction(R.string.settings_archive_export, SettingsComponentColors.SELECTED,
            deferClick = true, action = ::confirmExport)
        addHeaderAction(R.string.settings_archive_cancel, SettingsComponentColors.TEXT) {
            selected.clear()
            list.clearRangeAnchor()
            renderSelection()
        }
        card = SettingsCard(context, R.drawable.ic_archive_record_24, kind.title.chinese,
            content = viewport, trailing = header, fillContent = true)
        addView(card, LayoutParams(-1, -1))
        status.visibility = GONE
    }

    private fun addHeaderAction(label: Int, color: Int, deferClick: Boolean = false, action: () -> Unit) {
        val button = SettingsCompactButton(context, context.getString(label), color) {
            if (!busy && selected.isNotEmpty()) action()
        }.apply { deferClickUntilReleaseFeedback = deferClick }
        headerActions.addView(button, LinearLayout.LayoutParams(button.minimumWidth, button.minimumHeight).apply {
            if (headerActions.childCount > 0) marginStart = dp(SettingsChoiceDimensions.GAP_DP)
        })
    }

    private fun toggle(batch: HtmlBatchRef) {
        if (busy) return
        if (!selected.remove(batch)) selected.add(batch)
        renderSelection()
    }

    private fun updateRangeSelection(rows: Set<ArchiveRecordList.Row>, select: Boolean) {
        if (busy) return
        records.forEach { (batch, row) ->
            if (row in rows) {
                if (select) selected.add(batch) else selected.remove(batch)
            }
        }
        renderSelection()
    }

    private fun renderSelection() {
        list.rangeSelectionEnabled = !busy
        list.setSelection(selected.mapNotNull(records::get).toSet())
        card.setTitle(if (selected.isEmpty()) context.getString(kind.title.chinese)
            else context.getString(R.string.settings_archive_selected, selected.size))
        header.showActions(selected.isNotEmpty())
        for (index in 0 until headerActions.childCount) headerActions.getChildAt(index).isEnabled = !busy
    }

    private fun confirmDeletion() {
        val batches = selected.toSet()
        val message = if (kind == ScoreArchiveKind.Html) R.string.settings_archive_delete_html
            else R.string.settings_archive_delete_json
        showDialog(MessageDialog(context, R.drawable.ic_warning_24, context.getString(message, batches.size),
            context.getString(R.string.settings_archive_delete_confirm), context.getString(R.string.settings_archive_nevermind)) {
            busy = true
            renderSelection()
            actions.remove(kind.format, batches) { result ->
                main.post {
                    if (!isAttachedToWindow) return@post
                    val removedRows = result.removed.mapNotNull(records::get).toSet()
                    list.removeRows(removedRows) {
                        records = records - result.removed
                        selected.removeAll(result.removed)
                        busy = false
                        renderSelection()
                        status.visibility = if (records.isEmpty()) VISIBLE else GONE
                        val skipped = batches.size - result.removed.size
                        if (skipped > 0 || result.cleanupPending > 0) {
                            notice(context.getString(R.string.settings_archive_delete_result,
                                result.removed.size, skipped, result.cleanupPending))
                        }
                    }
                }
            }
        })
    }

    private fun confirmExport() {
        if (dialog != null) return
        val batches = selected.toSet()
        if (kind == ScoreArchiveKind.Html && !exportPreferences.exportWarningAcknowledged()) {
            val warning = SpannableString(context.getText(R.string.settings_export_warning)).apply {
                getSpans(0, length, Annotation::class.java).filter {
                    it.key == "color" && it.value == "warning"
                }.forEach {
                    setSpan(ForegroundColorSpan(SettingsComponentColors.WARNING), getSpanStart(it), getSpanEnd(it),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            showDialog(MessageDialog(context, R.drawable.ic_error_40, warning,
                context.getString(R.string.settings_export_warning_enable), null,
                additionalAction = MessageDialogAction(context.getString(R.string.settings_export_warning_disable),
                    SettingsComponentColors.TEXT) { chooseExportRedaction(batches, false) },
                cancelOnTouchOutside = false) {
                chooseExportRedaction(batches, true)
            })
        } else showExportChoices(batches)
    }

    private fun chooseExportRedaction(batches: Set<HtmlBatchRef>, enabled: Boolean) {
        if (!isAttachedToWindow) return
        exportPreferences.setRedactExports(enabled)
        exportPreferences.acknowledgeExportWarning()
        showExportChoices(batches)
    }

    private fun showExportChoices(batches: Set<HtmlBatchRef>) {
        val message = context.getString(R.string.settings_archive_export_message, batches.size, kind.format.directoryName)
        showDialog(MessageDialog(context, R.drawable.ic_archive_record_24, message,
            context.getString(R.string.settings_archive_export_open), context.getString(R.string.settings_archive_nevermind),
            accentColor = SettingsComponentColors.SELECTED,
            additionalAction = MessageDialogAction(context.getString(R.string.settings_archive_export_share),
                SettingsComponentColors.SELECTED) { export(batches, share = true) },
            cancelOnTouchOutside = false) {
            export(batches, share = false)
        })
    }

    private fun export(batches: Set<HtmlBatchRef>, share: Boolean) {
        busy = true
        renderSelection()
        exporter.export(kind.format, batches,
            redact = kind == ScoreArchiveKind.Html && exportPreferences.redactExports(), written = { file ->
            main.post {
                if (!isAttachedToWindow) return@post
                busy = false
                renderSelection()
                try {
                    ScoreArchiveExport.open(context, file, share)
                } catch (_: ActivityNotFoundException) {
                    notice(context.getString(R.string.settings_archive_export_no_app))
                }
            }
        }, stopped = { reason ->
            main.post {
                if (!isAttachedToWindow) return@post
                busy = false
                renderSelection()
                reason?.let { notice(context.getString(it)) }
            }
        })
    }

    private fun showDialog(next: MessageDialog) {
        dialog = next
        next.setOnDismissListener { if (dialog === next) dialog = null }
        next.showWithin(rootView.width)
    }

    private fun notice(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!loaded) load()
    }

    override fun onDetachedFromWindow() {
        worker?.shutdownNow()
        worker = null
        dialog?.dismiss()
        dialog = null
        exporter.close()
        if (busy) {
            busy = false
            loaded = false
        }
        super.onDetachedFromWindow()
    }

    private fun load() {
        status.setText(R.string.settings_archive_loading)
        status.visibility = VISIBLE
        val executor = Executors.newSingleThreadExecutor()
        worker = executor
        executor.execute {
            val loadedRecords = try {
                queryRows()
            } catch (error: IOException) {
                Log.w("ScoreArchivePage", "Archive read failed", error)
                null
            } catch (error: IllegalStateException) {
                Log.w("ScoreArchivePage", "Archive validation failed", error)
                null
            }
            post {
                if (worker !== executor) return@post
                worker = null
                if (loadedRecords == null) {
                    status.setText(R.string.settings_archive_read_error)
                } else {
                    loaded = true
                    records = loadedRecords
                    selected.retainAll(records.keys)
                    status.setText(R.string.settings_archive_empty)
                    status.visibility = if (records.isEmpty()) VISIBLE else GONE
                    list.setRows(records.values.toList())
                    renderSelection()
                }
            }
        }
        executor.shutdown()
    }

    private fun queryRows(): Map<HtmlBatchRef, ArchiveRecordList.Row> {
        val date = DateFormat.getDateFormat(context)
        val time = DateFormat.getTimeFormat(context)
        return queries.listBatches().mapNotNull { batch ->
            val started = Date(batch.ref.startedAt.toEpochMilli())
            recordRow(batch, "${date.format(started)} ${time.format(started)}")?.let { batch.ref to it }
        }.toMap()
    }

    private fun recordRow(batch: ScoreArchiveBatch, timestamp: String): ArchiveRecordList.Row? {
        val game = context.getString(if (batch.ref.game == ScoreGame.MAIMAI) R.string.settings_archive_mai else R.string.settings_archive_chu)
        val label = context.getString(R.string.settings_archive_group, game, timestamp)
        val summary = when (kind) {
            ScoreArchiveKind.Json -> if (batch.json != null) label else return null
            ScoreArchiveKind.Html -> {
                if (batch.htmlCount == 0) return null
                context.getString(R.string.settings_archive_html_batch, label, batch.htmlCount)
            }
        }
        return ArchiveRecordList.Row(summary) { toggle(batch.ref) }
    }

    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
