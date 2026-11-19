package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import com.otokit.noharayh.ui.constants.dimensions.ArchiveRecordListDimensions as Dimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsButtonDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions
import com.otokit.noharayh.ui.constants.motion.ArchiveRecordListMotion as Motion
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import kotlin.math.roundToInt

/** 记录行跟随一个滚动位置在边缘交接，列表只挂载这一帧涉及的行。 */
internal class ArchiveRecordList(
    context: Context,
    scrollbarDescription: String,
) : FrameLayout(context) {
    data class Row(val label: String, val onClick: () -> Unit)

    private val rowHeight = dp(context, SettingsButtonDimensions.HEIGHT_DP)
    private val gap = dp(context, SettingsGroupDimensions.GAP_DP)
    private val stripHeight = dp(context, Dimensions.STRIP_HEIGHT_DP)
    private val narrowStrip = Dimensions.STRIP_ENTRY_WIDTH_FRACTION
    private val stride = rowHeight + gap
    private val edgeSpace = stripHeight + gap
    private val inner = dp(context, SettingsGroupDimensions.INNER_RADIUS_DP).toFloat()
    private val outer = dp(context, SettingsGroupDimensions.OUTER_RADIUS_DP).toFloat()
    private val stripInner = minOf(inner, stripHeight / 2f)
    private val stripOuter = minOf(outer, stripHeight / 2f)
    private val trackWidth = (ThemeSelectionDimensions.SCROLLBAR_WIDTH_DP * resources.displayMetrics.density
        + 2 * dp(context, Dimensions.SCROLLBAR_TRACK_INSET_DP)).roundToInt()
    private var items = emptyList<Row>()
    private val buttons = mutableMapOf<Int, SettingsButton>()
    private var slots = 1
    private var maximum = 0
    private var touching = false
    private var draggingThumb = false
    private var scrollFeedbackActive = false
    private var expansionFeedback = ListExpansionFeedback()

    private val rows = object : PressFrontColumn(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), scroll.height + maximum)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            renderRows()
        }
    }
    private val settle: Runnable = Runnable {
        if (!touching && !draggingThumb) {
            val target = ((scroll.scrollY.toFloat() / stride).roundToInt() * stride).coerceAtMost(maximum)
            if (scroll.scrollY != target) scroll.smoothScrollTo(0, target)
            else scrollFeedbackActive = false
        }
    }
    private val scroll: ScrollView = object : ScrollView(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    this@ArchiveRecordList.removeCallbacks(settle)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    scheduleSettle()
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }
    private val track = View(context).apply {
        background = ContinuousCornerDrawable(SettingsComponentColors.ARCHIVE_SCROLLBAR_TRACK, trackWidth / 2f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = INVISIBLE
    }
    private val scrollbar = ThemeScrollbar(context, {
        draggingThumb = true
        scroll.fling(0)
        removeCallbacks(settle)
    }, { fraction ->
        scroll.scrollTo(0, (fraction * maximum).roundToInt())
    }, {
        draggingThumb = false
        scheduleSettle()
    }, trackEndInsetDp = Dimensions.SCROLLBAR_TRACK_INSET_DP.toFloat())
        .apply { contentDescription = scrollbarDescription }

    init {
        clipChildren = false
        clipToPadding = false
        val hitWidth = dp(context, ThemeSelectionDimensions.SCROLLBAR_HIT_WIDTH_DP)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.addView(rows, FrameLayout.LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, -1))
        addView(track, LayoutParams(trackWidth, -1, Gravity.END))
        addView(scrollbar, LayoutParams(hitWidth, -1, Gravity.END).apply {
            // 热区围绕灰底居中，正文只给可见轨道留宽。
            marginEnd = (trackWidth - hitWidth) / 2
        })
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) updateRange()
        }
        scroll.setOnScrollChangeListener { _, _, y, _, oldY ->
            if (y != oldY && (touching || draggingThumb)) scrollFeedbackActive = true
            renderRows(playFeedback = y != oldY && scrollFeedbackActive)
            scheduleSettle()
        }
    }

    fun setRows(records: List<Row>) {
        rows.removeAllViews()
        buttons.clear()
        expansionFeedback = ListExpansionFeedback()
        items = records
        updateRange()
        scroll.scrollTo(0, 0)
    }

    private fun updateRange() {
        scrollFeedbackActive = false
        slots = ((scroll.height - 2 * edgeSpace + gap) / stride).coerceAtLeast(1)
        maximum = (items.size - slots).coerceAtLeast(0) * stride
        track.visibility = if (maximum > 0) VISIBLE else INVISIBLE
        val rowEndMargin = if (maximum > 0) trackWidth + gap else 0
        val params = scroll.layoutParams as LayoutParams
        if (params.marginEnd != rowEndMargin) {
            params.marginEnd = rowEndMargin
            scroll.layoutParams = params
        }
        rows.requestLayout()
    }

    private fun scheduleSettle() {
        removeCallbacks(settle)
        if (!touching && !draggingThumb) postDelayed(settle, Motion.IDLE_SNAP_DELAY_MS)
    }

    private fun renderRows(playFeedback: Boolean = false) {
        val position = scroll.scrollY.coerceIn(0, maximum)
        val first = position / stride
        val p = (position % stride).toFloat() / stride
        val last = minOf(first + slots - 1, items.lastIndex)
        val incoming = last + 1
        val next = incoming + 1
        val topSpace = if (first == 0) 0 else edgeSpace
        val advancing = p > 0f
        val hasIncoming = incoming < items.size
        val hasNext = next < items.size
        val currentRowHeight = fittedRowHeight(topSpace, if (hasIncoming) edgeSpace else 0)
        val nextRowHeight = fittedRowHeight(edgeSpace, if (hasNext) edgeSpace else 0)
        val visible = (maxOf(0, first - 1)..minOf(if (advancing) next else incoming, items.lastIndex))
        for (index in buttons.keys.toList()) {
            if (index !in visible) {
                rows.removeView(buttons.remove(index))
                expansionFeedback.forget(index)
            }
        }
        val movingTop = topSpace * (1f - p)
        val movingHeight = mix(currentRowHeight, stripHeight.toFloat(), p)
        val middleHeight = mix(currentRowHeight, nextRowHeight, p)
        if (first > 0) {
            val retiringHeight = stripHeight * (1f - p)
            place(first - 1, movingTop - edgeGap(1f - p) - retiringHeight, retiringHeight, stripOuter, stripInner,
                expansion = 0f, widthFraction = mix(1f, narrowStrip, p), alpha = 1f - p)
        }
        var expanded = false
        for (index in first..last) {
            val firstRow = index == first
            val top = if (firstRow) movingTop else movingTop + movingHeight + gap + (index - first - 1) * (middleHeight + gap)
            val topRadius = if (firstRow) mix(if (first == 0) outer else inner, stripOuter, p) else inner
            val bottomRadius = if (index == last && !hasIncoming) outer
                else if (firstRow) mix(inner, stripInner, p) else inner
            expanded = place(index, top, if (firstRow) movingHeight else middleHeight, topRadius, bottomRadius,
                expansion = if (firstRow) 1f - p else 1f,
                fullHeight = if (firstRow) currentRowHeight else middleHeight) || expanded
        }
        if (hasIncoming) {
            val incomingTop = movingTop + movingHeight + gap + (last - first) * (middleHeight + gap)
            val incomingHeight = mix(stripHeight.toFloat(), nextRowHeight, p)
            if (advancing && hasNext) {
                place(next, incomingTop + incomingHeight + edgeGap(p), stripHeight * p,
                    stripInner, stripOuter, expansion = 0f, widthFraction = mix(narrowStrip, 1f, p), alpha = p)
            }
            expanded = place(incoming, incomingTop, incomingHeight, mix(stripInner, inner, p),
                mix(stripOuter, if (hasNext) inner else outer, p), expansion = p, fullHeight = nextRowHeight) || expanded
        }
        scrollbar.render(0f, scroll.height.toFloat(), if (maximum > 0) 1f else 0f,
            if (maximum > 0) position.toFloat() / maximum else 0f,
            canScroll = maximum > 0, enabled = maximum > 0, progress = 1f, retracting = false)
        if (expanded && playFeedback && isAttachedToWindow && isShown && hasWindowFocus()) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun place(index: Int, top: Float, height: Float, topRadius: Float, bottomRadius: Float,
        expansion: Float, widthFraction: Float = 1f, alpha: Float = 1f, fullHeight: Float = 0f): Boolean {
        val button = buttons.getOrPut(index) {
            val item = items[index]
            SettingsButton(context, item.label, SettingsButton.Kind.Action,
                contentHeight = LayoutParams.MATCH_PARENT, onClick = item.onClick).also {
                rows.addView(it, if (expansion == 0f) 0 else rows.childCount)
            }
        }
        val rowTop = top.roundToInt()
        val h = (top + height).roundToInt() - rowTop
        val w = (rows.width * widthFraction).roundToInt()
        val left = (rows.width - w) / 2
        val y = scroll.scrollY + rowTop
        val complete = expansion >= 1f
        button.visibility = if (h > 0) VISIBLE else INVISIBLE
        button.alpha = alpha
        button.topCornerRadius = topRadius
        button.bottomCornerRadius = bottomRadius
        button.setContentAlpha(((expansion - Motion.CONTENT_FADE_START) / (1f - Motion.CONTENT_FADE_START)).coerceIn(0f, 1f))
        button.isClickable = complete
        button.isFocusable = complete
        button.importantForAccessibility = if (complete) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        button.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        button.layout(left, y, left + w, y + h)
        return expansionFeedback.update(index, expansion, fullHeight > 0f && h >= (top + fullHeight).roundToInt() - rowTop)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(settle)
        touching = false
        draggingThumb = false
        scrollFeedbackActive = false
        super.onDetachedFromWindow()
    }

    private fun fittedRowHeight(topSpace: Int, bottomSpace: Int): Float {
        if (items.size < slots) return rowHeight.toFloat()
        val total = scroll.height - topSpace - bottomSpace - (slots - 1) * gap
        return (total.toFloat() / slots).coerceAtLeast(0f)
    }

    private fun mix(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    private fun edgeGap(p: Float): Float = mix(-stripHeight / 2f, gap.toFloat(), p)
}
