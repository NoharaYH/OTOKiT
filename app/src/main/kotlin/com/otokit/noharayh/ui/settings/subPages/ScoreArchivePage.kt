package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.otokit.noharayh.R
import com.otokit.noharayh.score.ScoreArchiveBatch
import com.otokit.noharayh.score.ScoreArchiveQueries
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.score.ScoreTextPreview
import com.otokit.noharayh.ui.chrome.TitleText
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordList
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import java.io.IOException
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class ScoreArchiveKind(val title: TitleText) {
    Html(TitleText(R.string.title_settings_html_records, R.string.settings_html_records)),
    Json(TitleText(R.string.title_settings_json_records, R.string.settings_json_records)),
}

/** 第三级存档查看页，通过 score 的只读入口按需读取文件预览。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ScoreArchivePage(
    context: Context,
    private val kind: ScoreArchiveKind,
    margin: Int,
    split: Boolean,
    private val queries: ScoreArchiveQueries,
    onBack: () -> Unit,
) : FrameLayout(context) {
    private val list = ArchiveRecordList(context, context.getString(R.string.settings_archive_scroll))
    private val status = ViewPrimitives.text(context, "", 14f, SettingsComponentColors.TEXT)
    private var worker: ExecutorService? = null
    private var reading = false
    private var loaded = false
    private var dialog: AlertDialog? = null

    init {
        setPadding(if (split) 0 else margin, margin, margin, margin)
        val viewport = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val back = if (split) SettingsButton(context, context.getString(R.string.settings_archive_back),
            SettingsButton.Kind.Action, trailingIcon = SettingsButton.ActionIcon.Back,
            contentHeight = dp(SettingsCardDimensions.HEADER_HEIGHT_DP), onClick = onBack) else null
        addView(SettingsCard(context, R.drawable.ic_archive_record_24, kind.title.chinese,
            content = viewport, trailing = back, fillContent = true),
            LayoutParams(-1, -1))
        status.visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        worker = Executors.newSingleThreadExecutor()
        if (!loaded) load()
    }

    override fun onDetachedFromWindow() {
        dialog?.dismiss()
        worker?.shutdownNow()
        worker = null
        reading = false
        super.onDetachedFromWindow()
    }

    private fun load() {
        status.setText(R.string.settings_archive_loading)
        status.visibility = VISIBLE
        read(::queryRows) { records ->
            if (records == null) {
                status.setText(R.string.settings_archive_read_error)
            } else {
                loaded = true
                status.setText(R.string.settings_archive_empty)
                status.visibility = if (records.isEmpty()) VISIBLE else GONE
                list.setRows(records)
            }
        }
    }

    private fun queryRows(): List<ArchiveRecordList.Row> {
        val date = DateFormat.getDateFormat(context)
        val time = DateFormat.getTimeFormat(context)
        return queries.listBatches().mapNotNull { batch ->
            val started = Date(batch.ref.startedAt.toEpochMilli())
            recordRow(batch, "${date.format(started)} ${time.format(started)}")
        }
    }

    private fun recordRow(batch: ScoreArchiveBatch, timestamp: String): ArchiveRecordList.Row? {
        val game = context.getString(if (batch.ref.game == ScoreGame.MAIMAI) R.string.settings_archive_mai else R.string.settings_archive_chu)
        val label = context.getString(R.string.settings_archive_group, game, timestamp)
        return when (kind) {
            ScoreArchiveKind.Json -> batch.json?.let { snapshot ->
                ArchiveRecordList.Row(label) { preview(snapshot.name) { queries.previewJson(snapshot) } }
            }
            ScoreArchiveKind.Html -> if (batch.htmlCount == 0) null else {
                ArchiveRecordList.Row(context.getString(R.string.settings_archive_html_batch, label, batch.htmlCount)) {}
            }
        }
    }

    private fun preview(name: String, load: () -> ScoreTextPreview) = read(load) { preview ->
        if (preview == null) return@read showError()
        showPreview(name, TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            text = if (preview.truncated) context.getString(R.string.settings_archive_preview_truncated) + "\n\n" + preview.text else preview.text
            setTextIsSelectable(true)
        })
    }

    private fun showPreview(title: String, content: android.view.View) {
        dialog = AlertDialog.Builder(context).setTitle(title)
            .setView(ScrollView(context).apply {
                setPadding(dp(20), dp(8), dp(20), dp(8))
                addView(content, LayoutParams(-1, -2))
            })
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showError() {
        dialog = AlertDialog.Builder(context).setMessage(R.string.settings_archive_read_error)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun <T : Any> read(load: () -> T, show: (T?) -> Unit) {
        val executor = worker ?: return
        if (reading) return
        reading = true
        executor.execute {
            val result = try {
                load()
            } catch (error: IOException) {
                Log.w("ScoreArchivePage", "Archive read failed", error)
                null
            } catch (error: IllegalStateException) {
                Log.w("ScoreArchivePage", "Archive validation failed", error)
                null
            }
            post {
                if (worker === executor) {
                    reading = false
                    show(result)
                }
            }
        }
    }

    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
