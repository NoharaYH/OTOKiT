package com.otokit.noharayh.ui.kit.settings

import android.animation.ValueAnimator
import androidx.core.animation.doOnEnd
import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import com.otokit.noharayh.ui.constants.dimensions.ArchiveRecordListDimensions as Dimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsButtonDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions
import com.otokit.noharayh.ui.constants.motion.ArchiveRecordListMotion as Motion
import com.otokit.noharayh.ui.constants.motion.SettingsChoiceMotion
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** 记录行跟随一个滚动位置在边缘交接，列表只挂载这一帧涉及的行。 */
internal class ArchiveRecordList(
    context: Context,
    scrollbarDescription: String,
    private val onRangeSelectionChanged: ((Set<Row>, Boolean) -> Unit)? = null,
) : FrameLayout(context) {
    class Row(val label: String, val onClick: () -> Unit)

    private data class RowFrame(
        val top: Float, val height: Float, val width: Float, val alpha: Float, val expansion: Float,
        val topRadius: Float, val bottomRadius: Float,
    )
    private class Swipe(val row: Row, val view: ArchiveRecordRow, val pointer: Int, val x: Float, val y: Float) {
        var rejected = false
    }
    private data class RangeAnchor(val row: Row, val deselect: Boolean)

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
    private val rowViews = mutableMapOf<Int, ArchiveRecordRow>()
    private var selected = emptySet<Row>()
    private var rangeAnchor: RangeAnchor? = null
    private val deselectAnchor get() = rangeAnchor?.takeIf { it.deselect }?.row
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val revealWidth = dp(context, Dimensions.RANGE_REVEAL_DP).toFloat()
    private val revealOvershoot = dp(context, Dimensions.RANGE_OVERSHOOT_DP).toFloat()
    private var candidateSwipe: Swipe? = null
    private var activeSwipe: Swipe? = null
    var rangeSelectionEnabled = true
        set(value) {
            field = value
            if (!value) cancelRangeGesture()
        }
    private val selectionInset = dp(context, Dimensions.SELECTION_INSET_DP).toFloat()
    private var removalAnimation: ValueAnimator? = null
    private var removing = emptySet<Row>()
    private var shrink = 0f
    private var previousFrames = emptyMap<Row, RowFrame>()
    private var refill = 1f
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
        cancelRangeGesture()
        rangeAnchor = null
        rows.removeAllViews()
        rowViews.clear()
        expansionFeedback = ListExpansionFeedback()
        items = records
        updateRange()
        scroll.scrollTo(0, 0)
    }

    fun setSelection(selection: Set<Row>) {
        val changed = (selected - selection) + (selection - selected)
        selected = selection
        if (rangeAnchor?.let { it.deselect && it.row !in selection } == true) rangeAnchor = null
        refreshHighlights(changed)
    }

    private fun refreshHighlights(changed: Set<Row>) {
        rowViews.forEach { (index, view) ->
            val item = items[index]
            if (item in changed) highlight(view.button, item, animate = true)
        }
    }

    fun clearRangeAnchor() {
        updateRangeAnchor(null)
        cancelRangeGesture()
    }

    private fun updateRangeAnchor(anchor: RangeAnchor?) {
        val previouslyMarked = deselectAnchor
        rangeAnchor = anchor
        if (previouslyMarked != deselectAnchor) {
            refreshHighlights(listOfNotNull(previouslyMarked, deselectAnchor).toSet())
        }
    }

    private fun highlight(button: SettingsButton, row: Row, animate: Boolean) {
        val marked = row === deselectAnchor
        val fill = if (marked) SettingsComponentColors.ARCHIVE_DESELECT_ANCHOR_FILL else SettingsComponentColors.ARCHIVE_SELECTED_FILL
        button.setHighlight(row in selected, selectionInset, fill, animate)
        if (marked) button.setContentColor(SettingsComponentColors.ARCHIVE_DESELECT_ANCHOR_TEXT)
    }

    fun removeRows(removed: Set<Row>, finished: () -> Unit) {
        if (removed.isEmpty()) {
            finished()
            return
        }
        scroll.fling(0)
        removeCallbacks(settle)
        cancelRangeGesture()
        if (rangeAnchor?.row in removed) updateRangeAnchor(null)
        removing = removed
        if (rowViews.keys.any { items[it] in removed }) {
            animateRemoval(Motion.REMOVE_SHRINK_MS, linear = true, { shrink = it; renderRows() }) {
                refillRows(removed, finished)
            }
        } else refillRows(removed, finished)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        removing.isNotEmpty() || super.dispatchTouchEvent(event)

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (activeSwipe != null) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                candidateSwipe = if (rangeSelectionEnabled && onRangeSelectionChanged != null) findSwipeRow(event) else null
            }
            MotionEvent.ACTION_MOVE -> {
                val swipe = candidateSwipe ?: return false
                if (swipe.rejected) return false
                val pointer = event.findPointerIndex(swipe.pointer)
                if (pointer < 0) return false
                val dx = event.getX(pointer) - swipe.x
                val dy = abs(event.getY(pointer) - swipe.y)
                if (dy >= touchSlop) {
                    swipe.rejected = true
                } else if (abs(dx) >= touchSlop) {
                    if (dy > abs(dx) * Motion.RANGE_SWIPE_MAX_SLOPE) {
                        swipe.rejected = true
                    } else {
                        candidateSwipe = null
                        activeSwipe = swipe
                        scroll.fling(0)
                        removeCallbacks(settle)
                        parent.requestDisallowInterceptTouchEvent(true)
                        swipe.view.beginReveal(first = matchingAnchor(swipe.row, deselect = swipe.row in selected) == null)
                        updateReveal(swipe, dx)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> candidateSwipe?.rejected = true
            MotionEvent.ACTION_UP -> {
                val rejected = candidateSwipe?.rejected == true
                candidateSwipe = null
                // Consume a diagonal that travelled past slop but never became a native scroll.
                return rejected
            }
            MotionEvent.ACTION_CANCEL -> candidateSwipe = null
        }
        return super.onInterceptTouchEvent(event)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility") // Row taps and horizontal range gestures are separate actions.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val swipe = activeSwipe ?: return super.onTouchEvent(event)
        val pointer = event.findPointerIndex(swipe.pointer)
        val action = event.actionMasked
        when {
            pointer < 0 || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN -> {
                cancelRangeGesture()
                scheduleSettle()
            }
            action == MotionEvent.ACTION_MOVE -> updateReveal(swipe, event.getX(pointer) - swipe.x)
            action == MotionEvent.ACTION_UP -> {
                val dx = event.getX(pointer) - swipe.x
                updateReveal(swipe, dx)
                cancelRangeGesture()
                if (dragDistance(dx) >= revealWidth) commitRangeSwipe(swipe.row)
                scheduleSettle()
            }
        }
        return true
    }

    private fun findSwipeRow(event: MotionEvent): Swipe? {
        val x = event.x - scroll.left - rows.left
        val y = event.y - scroll.top + scroll.scrollY - rows.top
        val hit = rowViews.entries.firstOrNull { (_, view) ->
            view.button.isClickable && view.button.isEnabled &&
                x >= view.left && x < view.right && y >= view.top && y < view.bottom
        } ?: return null
        return Swipe(items[hit.key], hit.value, event.getPointerId(0), event.x, event.y)
    }

    private fun dragDistance(dx: Float): Float = (abs(dx) - touchSlop).coerceAtLeast(0f)

    private fun updateReveal(swipe: Swipe, dx: Float) {
        val distance = dragDistance(dx)
        // Continuous at the knee, with progressively less movement per pixel beyond it.
        val shown = if (distance <= revealWidth) distance else {
            val extra = distance - revealWidth
            revealWidth + revealOvershoot * extra / (revealOvershoot + extra)
        }
        swipe.view.dragReveal(if (dx < 0f) -shown else shown)
    }

    private fun matchingAnchor(row: Row, deselect: Boolean): Row? =
        rangeAnchor?.takeIf { it.deselect == deselect && it.row !== row }?.row

    private fun commitRangeSwipe(row: Row) {
        val end = items.indexOf(row)
        if (end < 0) return
        val deselect = row in selected
        if (deselect && selected.size == 1) {
            updateRangeAnchor(null)
            onRangeSelectionChanged?.invoke(setOf(row), false)
            return
        }
        val anchor = matchingAnchor(row, deselect)
        val start = if (anchor != null) items.indexOf(anchor) else -1
        if (start < 0) {
            updateRangeAnchor(RangeAnchor(row, deselect))
            if (!deselect) onRangeSelectionChanged?.invoke(setOf(row), true)
        } else {
            updateRangeAnchor(null)
            val range = items.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
            onRangeSelectionChanged?.invoke(range, !deselect)
        }
    }

    private fun cancelRangeGesture() {
        candidateSwipe = null
        activeSwipe?.view?.releaseReveal()
        activeSwipe = null
    }

    private fun refillRows(removed: Set<Row>, finished: () -> Unit) {
        val oldPosition = scroll.scrollY
        val first = oldPosition / stride
        val start = (0 until minOf(first, items.size)).count { items[it] !in removed }
        previousFrames = rowViews.entries.mapNotNull { (index, view) ->
            val button = view.button
            val row = items[index]
            if (row in removed || view.height == 0) null else row to RowFrame(
                (view.top - oldPosition).toFloat(), view.height.toFloat(),
                view.width.toFloat() / rows.width.coerceAtLeast(1), view.alpha,
                ((view.height - stripHeight).toFloat() / (rowHeight - stripHeight)).coerceIn(0f, 1f)
                    .let { if (button.isClickable) 1f else it },
                button.topCornerRadius ?: inner, button.bottomCornerRadius ?: inner,
            )
        }.toMap()
        val retained = items - removed
        val oldViews = rowViews.toMap()
        rowViews.clear()
        var index = 0
        items.forEachIndexed { oldIndex, row ->
            val view = oldViews[oldIndex]
            if (row in removed) {
                if (view != null) rows.removeView(view)
            } else {
                if (view != null) rowViews[index] = view
                index++
            }
        }
        items = retained
        selected = selected - removed
        expansionFeedback = ListExpansionFeedback()
        updateRange()
        refill = 0f
        scroll.scrollTo(0, (start * stride + oldPosition % stride).coerceAtMost(maximum))
        animateRemoval(SettingsChoiceMotion.DURATION_MS, linear = false, { refill = it; renderRows() }) {
            previousFrames = emptyMap()
            removing = emptySet()
            shrink = 0f
            renderRows()
            finished()
        }
    }

    private fun animateRemoval(durationMs: Long, linear: Boolean, frame: (Float) -> Unit, finished: () -> Unit) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = if (linear) LinearInterpolator() else MotionInterpolators.of(SettingsChoiceMotion.CURVE)
            addUpdateListener { frame(it.animatedValue as Float) }
            doOnEnd {
                if (removalAnimation === it) {
                    removalAnimation = null
                    finished()
                }
            }
        }
        removalAnimation = animator
        animator.start()
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
        if (!touching && !draggingThumb && activeSwipe == null && removing.isEmpty()) postDelayed(settle, Motion.IDLE_SNAP_DELAY_MS)
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
        for (index in rowViews.keys.toList()) {
            if (index !in visible) {
                rows.removeView(rowViews.remove(index))
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
        val item = items[index]
        val view = rowViews.getOrPut(index) {
            ArchiveRecordRow(context, item.label, item.onClick).also {
                highlight(it.button, item, animate = false)
                rows.addView(it, if (expansion == 0f) 0 else rows.childCount)
                rows.bind(it.button)
            }
        }
        val button = view.button
        val previous = previousFrames[item]
        val visualTop = mix(previous?.top ?: (scroll.height - stripHeight).toFloat(), top, refill)
        val rowTop = visualTop.roundToInt()
        val h = (visualTop + mix(previous?.height ?: stripHeight.toFloat(), height, refill)).roundToInt() - rowTop
        val w = (rows.width * mix(previous?.width ?: narrowStrip, widthFraction, refill)).roundToInt()
        val left = (rows.width - w) / 2
        val y = scroll.scrollY + rowTop
        val complete = expansion >= 1f
        val removal = if (item in removing) shrink else 0f
        view.visibility = if (h > 0) VISIBLE else INVISIBLE
        view.alpha = mix(previous?.alpha ?: 0f, alpha, refill) * (1f - removal)
        // The short side reaches half size; both axes lose the same number of pixels per edge.
        button.removalInset = (minOf(w, h) / 4f - selectionInset / 2f).coerceAtLeast(0f) * removal
        view.setGroupCorners(mix(previous?.topRadius ?: stripInner, topRadius, refill),
            mix(previous?.bottomRadius ?: stripOuter, bottomRadius, refill))
        val visibleExpansion = mix(previous?.expansion ?: 0f, expansion, refill)
        button.setContentAlpha(((visibleExpansion - Motion.CONTENT_FADE_START) / (1f - Motion.CONTENT_FADE_START)).coerceIn(0f, 1f))
        button.isClickable = complete
        button.isFocusable = complete
        button.importantForAccessibility = if (complete) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        view.layout(left, y, left + w, y + h)
        return expansionFeedback.update(index, expansion, fullHeight > 0f && h >= (top + fullHeight).roundToInt() - rowTop)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(settle)
        cancelRangeGesture()
        val animator = removalAnimation
        removalAnimation = null
        animator?.cancel()
        removing = emptySet()
        previousFrames = emptyMap()
        shrink = 0f
        refill = 1f
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
