package com.otokit.noharayh.ui.kit.pageScoreSync

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.SettingsButtonDimensions
import com.otokit.noharayh.ui.constants.dimensions.ScoreSyncDimensions
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

internal enum class SyncTarget(val label: Int) {
    Fish(R.string.sync_target_fish), Both(R.string.sync_target_both), Snow(R.string.sync_target_snow),
}

internal class ScoreSyncPanel(
    context: Context,
    private val grid: DifficultyGrid,
    onMode: (SyncTarget) -> Unit,
    onStart: () -> Unit,
    onCopy: (CharSequence) -> Unit,
) : FrameLayout(context) {
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
    private val space = dp(16)
    private val panelFill = 0xccffffff.toInt()
    private val buttonHeight = dp(SettingsButtonDimensions.HEIGHT_DP)
    private val row = LinearLayout(context)
    private val form = PressFrontColumn(context)
    private val scroll = PanelScroll(context)
    private val modes = SyncTarget.entries.associateWith { mode ->
        ScoreSyncButton(context, context.getString(mode.label), true) { onMode(mode) }
    }
    private val start = ScoreSyncButton(context, context.getString(R.string.sync_start), false, onStart)
    private val log = FrameLayout(context)
    private val message = ViewPrimitives.text(context, "", 14f, 0xffd2d2d7.toInt())
    private val copy = ImageButton(context).apply {
        contentDescription = context.getString(R.string.sync_copy_log)
        setImageResource(R.drawable.ic_content_copy_24)
        setColorFilter(Color.WHITE)
        background = RippleDrawable(ColorStateList.valueOf(0x40ffffff), null, null)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        setOnClickListener { onCopy(message.text) }
    }
    private var wide: Boolean? = null

    init {
        addView(row, LayoutParams(-1, -1, Gravity.CENTER))
        form.setPadding(space, space, space, space)
        form.background = ViewPrimitives.shape(context, panelFill, 24)
        val tabs = PressFrontColumn(context).apply { orientation = LinearLayout.HORIZONTAL }
        for ((mode, button) in modes) {
            tabs.addView(button, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
                if (mode != SyncTarget.Fish) marginStart = dp(8)
            })
        }
        form.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        form.addView(ViewPrimitives.text(context, context.getString(R.string.sync_difficulty),
            18f, 0xff252529.toInt(), true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = space })
        form.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        form.addView(start, LinearLayout.LayoutParams(-1, buttonHeight).apply { topMargin = space })
        scroll.isVerticalScrollBarEnabled = false
        scroll.isFillViewport = true
        scroll.addView(form, LayoutParams(-1, -2))
        val corner = dp(18)
        val hitSize = dp(48)
        log.background = ViewPrimitives.shape(context, 0xf529292e.toInt(), 18)
        log.minimumHeight = dp(132)
        val logContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(corner, corner, corner, corner)
        }
        log.addView(logContent, LayoutParams(-1, -1))
        val title = ViewPrimitives.text(context, context.getString(R.string.sync_log), 16f, Color.WHITE, true)
        logContent.addView(title, LinearLayout.LayoutParams(-1, -2).apply { marginEnd = hitSize })
        // 标题先独立排版。图标按字高对齐，透明热区不参与标题测量。
        val glyphSize = title.textSize.toInt().coerceAtMost(hitSize)
        val iconInset = (hitSize - glyphSize) / 2
        copy.setPadding(iconInset, iconInset, iconInset, iconInset)
        log.addView(copy, LayoutParams(hitSize, hitSize, Gravity.TOP or Gravity.END).apply {
            marginEnd = corner - iconInset
        })
        title.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            copy.translationY = (top + bottom - hitSize) / 2f
        }
        val logBody = PanelScroll(context).apply {
            addView(message, LayoutParams(-1, -2))
        }
        logContent.addView(logBody, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        message.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setWide(false)
    }

    fun setWide(value: Boolean) {
        if (wide == value) return
        wide = value
        val margin = dp(if (value) ScoreSyncDimensions.WIDE_MIN_MARGIN_DP else ScoreSyncDimensions.COMPACT_MARGIN_DP)
        setPadding(margin, margin, margin, margin)
        grid.setWide(value)
        (log.parent as? ViewGroup)?.removeView(log)
        row.removeAllViews()
        if (value) {
            row.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            row.addView(log, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = space })
        } else {
            form.addView(log, LinearLayout.LayoutParams(-1, log.minimumHeight, 1f).apply { topMargin = space })
            row.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val params = row.layoutParams
        if (wide == true) {
            val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
            params.width = minOf(availableWidth, dp(ScoreSyncDimensions.WIDE_WIDTH_DP))
            params.height = minOf(availableHeight, dp(ScoreSyncDimensions.WIDE_HEIGHT_DP))
        } else {
            params.width = LayoutParams.MATCH_PARENT
            params.height = LayoutParams.MATCH_PARENT
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun render(target: SyncTarget, bothAvailable: Boolean, selected: Set<Int>, startAvailable: Boolean,
               interactive: Boolean, accent: Int, status: String) {
        importantForAccessibility = if (interactive) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        modes.forEach { (mode, button) ->
            button.render(accent, target == mode, mode != SyncTarget.Both || bothAvailable, interactive)
        }
        grid.render(selected, interactive)
        start.render(accent, startAvailable, startAvailable, interactive)
        copy.isEnabled = interactive
        if (message.text != status) message.text = status
    }
}

/** 空白处交给背景的音游翻页；内容超出视口时正常纵向滚动。 */
private class PanelScroll(context: Context) : ScrollView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !canScrollVertically(-1) && !canScrollVertically(1)) return false
        return super.onTouchEvent(event)
    }
}
