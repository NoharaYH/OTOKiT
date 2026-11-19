package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.otokit.noharayh.R
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreArchiveBatch
import com.otokit.noharayh.score.ScoreArchiveQueries
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.score.ScoreTextPreview
import com.otokit.noharayh.ui.chrome.TitleText
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.settings.ExpandableContent
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsGroup
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import com.otokit.noharayh.ui.kit.settings.ThemeScrollbar
import java.io.IOException
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

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
    private val date = DateFormat.getDateFormat(context)
    private val time = DateFormat.getTimeFormat(context)
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        addView(list, LayoutParams(-1, -2))
    }
    private val scrollbar = ThemeScrollbar(context, {}, { fraction ->
        scroll.scrollTo(0, (fraction * scrollRange()).roundToInt())
    }, {}).apply {
        contentDescription = context.getString(R.string.settings_archive_scroll)
    }
    private val status = ViewPrimitives.text(context, "", 14f, SettingsComponentColors.TEXT)
    private val more = SettingsPageViews.action(context, context.getString(R.string.settings_archive_more)) { load() }
    private val offsets = ScoreGame.entries.associateWith { 0 }.toMutableMap()
    private var worker: ExecutorService? = null
    private var reading = false
    private var dialog: AlertDialog? = null

    init {
        setPadding(if (split) 0 else margin, margin, margin, margin)
        val viewport = FrameLayout(context).apply {
            val hitWidth = dp(ThemeSelectionDimensions.SCROLLBAR_HIT_WIDTH_DP)
            addView(scroll, LayoutParams(-1, -1).apply { marginEnd = hitWidth / 2 + dp(8) })
            addView(scrollbar, LayoutParams(hitWidth, -1, Gravity.END))
        }
        val back = if (split) SettingsButton(context, context.getString(R.string.settings_archive_back),
            SettingsButton.Kind.Action, trailingIcon = SettingsButton.ActionIcon.Back,
            contentHeight = dp(SettingsCardDimensions.HEADER_HEIGHT_DP), onClick = onBack) else null
        addView(SettingsCard(context, R.drawable.ic_archive_record_24, kind.title.chinese,
            content = viewport, trailing = back, fillContent = true),
            LayoutParams(-1, -1))
        list.addView(status, LinearLayout.LayoutParams(-1, -2))
        list.addView(more, SettingsPageViews.gap(context))
        more.visibility = GONE
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> renderScrollbar() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> renderScrollbar() }
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> renderScrollbar() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        worker = Executors.newSingleThreadExecutor()
        if (list.childCount == 2 && offsets.values.all { it == 0 }) load()
        else renderScrollbar()
    }

    override fun onDetachedFromWindow() {
        dialog?.dismiss()
        worker?.shutdownNow()
        worker = null
        reading = false
        super.onDetachedFromWindow()
    }

    private fun load() {
        val pending = offsets.toMap()
        read({
            pending.map { (game, offset) -> game to queries.list(game, offset, limit = 8) }
        }) { pages ->
            if (pages == null) {
                status.setText(R.string.settings_archive_read_error)
            } else {
                for ((game, result) in pages) {
                    result.nextOffset?.let { offsets[game] = it } ?: offsets.remove(game)
                }
                val batches = pages.flatMap { it.second.items }.sortedByDescending { it.ref.startedAt }
                for (batch in batches) addBatch(batch)
                status.setText(R.string.settings_archive_empty)
                status.visibility = if (list.childCount == 2 && offsets.isEmpty()) VISIBLE else GONE
            }
            more.visibility = if (offsets.isEmpty()) GONE else VISIBLE
        }
        if (list.childCount == 2) {
            status.visibility = VISIBLE
            status.setText(R.string.settings_archive_loading)
        }
    }

    private fun addBatch(batch: ScoreArchiveBatch) {
        val started = Date(batch.ref.startedAt.toEpochMilli())
        val game = context.getString(if (batch.ref.game == ScoreGame.MAIMAI) R.string.settings_archive_mai else R.string.settings_archive_chu)
        val label = context.getString(R.string.settings_archive_group, game, "${date.format(started)} ${time.format(started)}")
        val item = when (kind) {
            ScoreArchiveKind.Json -> batch.json?.let { snapshot ->
                SettingsPageViews.action(context, label) { preview(snapshot.name) { queries.previewJson(snapshot) } }
            }
            ScoreArchiveKind.Html -> if (batch.htmlCount == 0) null else htmlBatch(batch, label)
        } ?: return
        list.addView(item, list.childCount - 1, SettingsPageViews.gap(context))
    }

    private fun htmlBatch(batch: ScoreArchiveBatch, label: String): LinearLayout {
        val slot = ExpandableContent(context).apply { setExpanded(false, animate = false) }
        var expanded = false
        var loaded = false
        val button = SettingsPageViews.action(context, context.getString(R.string.settings_archive_html_batch, label, batch.htmlCount)) {
            if (reading) return@action
            expanded = !expanded
            if (!loaded && expanded) {
                loadHtmlBatch(batch, slot) { loaded = it; expanded = it }
            } else {
                slot.setExpanded(expanded)
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(button, LayoutParams(-1, -2))
            addView(slot, LayoutParams(-1, -2))
        }
    }

    private fun loadHtmlBatch(batch: ScoreArchiveBatch, slot: ExpandableContent, onLoaded: (Boolean) -> Unit) =
        read({ queries.detail(batch.ref) }) { detail ->
        onLoaded(detail != null)
        if (detail == null) return@read showError()
        val pages = detail.html.sortedBy {
            if (it.kind.isDifficulty) it.kind.chartIndex else if (it.kind == HtmlPageKind.RECENT) 6 else 7
        }
        val group = SettingsGroup(context).apply {
            setPadding(dp(12), dp(4), 0, 0)
            for (page in pages) {
                val label = when (page.kind) {
                    HtmlPageKind.PLAYER -> context.getString(R.string.settings_archive_player)
                    HtmlPageKind.RECENT -> context.getString(R.string.settings_archive_recent)
                    HtmlPageKind.WORLDS_END -> "WORLD'S END"
                    HtmlPageKind.UTAGE -> context.getString(R.string.settings_archive_utage)
                    HtmlPageKind.REMASTER -> "Re:MASTER"
                    else -> page.kind.suffix
                }
                addView(SettingsPageViews.action(context, label) { preview(page.fileName) { queries.previewHtml(page) } },
                    LinearLayout.LayoutParams(-1, -2))
            }
        }
        slot.setContent(group)
        slot.setExpanded(true)
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
        more.isEnabled = false
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
                    more.isEnabled = true
                    show(result)
                }
            }
        }
    }

    private fun renderScrollbar() {
        val range = scrollRange()
        scrollbar.render(0f, scroll.height.toFloat(), if (range > 0) 1f else 0f,
            if (range > 0) scroll.scrollY.toFloat() / range else 0f,
            canScroll = range > 0, enabled = range > 0, progress = 1f, retracting = false)
    }

    private fun scrollRange() = (list.height - scroll.height).coerceAtLeast(0)
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
