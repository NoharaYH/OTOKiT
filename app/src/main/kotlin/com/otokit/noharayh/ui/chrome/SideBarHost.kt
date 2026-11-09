package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.customview.widget.ViewDragHelper
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.window.PixelRect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 抽屉共同父容器的运行部分：内容场景之上叠遮罩与面板。
 *
 * 只接收应用交互矩形、选中 destination 与点击意图；开合是临时呈现，不写偏好或 Bundle。
 * 关闭态从交互区域左侧 22% 起手右滑打开；打开态从面板左滑关闭。一次手势只归一个所有者。
 */
class SideBarHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val scrim = SideBarScrimView(context)
    private val sideBar = SideBar(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    private val helper = ViewDragHelper.create(this, 1f, DragCallback())

    private var interactionLeftPx = 0
    private var interactionTopPx = 0
    private var interactionRightPx = 0
    private var interactionBottomPx = 0
    private var drawerWidthPx = 0

    private var progress = 0f
    private var lastNotifiedOpen = false

    private var dragEnabled = false
    private var downEvent: MotionEvent? = null
    private var downX = 0f
    private var downY = 0f
    private var edgeCandidate = false
    private var claimed = false

    var onNavigate: ((Destination) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    /** 开合变化通知，宿主据此同步返回消费与输入阻断。 */
    var onOpenChanged: ((Boolean) -> Unit)? = null

    init {
        addView(
            scrim,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            sideBar,
            LayoutParams(dp(308f), LayoutParams.MATCH_PARENT),
        )
        scrim.visibility = GONE
        sideBar.visibility = GONE
        scrim.setOnClickListener { close() }
        sideBar.onNavigate = { destination -> onNavigate?.invoke(destination) }
        sideBar.onSettings = { onSettings?.invoke() }
    }

    val isOpen: Boolean
        get() = progress >= 0.999f

    val isVisible: Boolean
        get() = progress > 0f

    /**
     * XML 的内容场景在构造后才加入；这里把它压回底层，保证遮罩与面板始终在内容之上。
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        bringChildToFront(scrim)
        bringChildToFront(sideBar)
    }

    /** 应用交互矩形为根局部坐标；抽屉宽度取 308dp 与 92% 交互宽度的较小值。 */
    fun setMetrics(interaction: PixelRect, widthPx: Int) {
        interactionLeftPx = interaction.left
        interactionTopPx = interaction.top
        interactionRightPx = interaction.right
        interactionBottomPx = interaction.bottom
        drawerWidthPx = widthPx
            .coerceAtMost(interaction.width)
            .coerceAtLeast(0)
        sideBar.setPanelWidth(drawerWidthPx)
        applyLayout()
    }

    /** 仅 Compact 且未打开设置时允许边缘手势。 */
    fun setDragEnabled(enabled: Boolean) {
        dragEnabled = enabled
        if (!enabled) cancelDrag()
    }

    fun setSelected(destination: Destination) {
        sideBar.setSelected(destination)
    }

    fun open() {
        if (drawerWidthPx <= 0) return
        setProgress(1f)
    }

    fun close() {
        setProgress(0f)
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun release() {
        dragEnabled = false
        cancelDrag()
        sideBar.dispose()
        onNavigate = null
        onSettings = null
        onOpenChanged = null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!dragEnabled || drawerWidthPx <= 0) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downEvent?.recycle()
                downEvent = MotionEvent.obtain(ev)
                downX = ev.x
                downY = ev.y
                claimed = false
                edgeCandidate = isDownEligible(ev.x, ev.y)
            }

            MotionEvent.ACTION_MOVE -> {
                if (claimed) return true
                val event = downEvent ?: return false
                if (!edgeCandidate) return false

                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) <= touchSlop || abs(dx) <= abs(dy)) return false
                if (!isVisible && dx <= 0f) return false
                if (isVisible && dx >= 0f) return false

                claimed = true
                preparePanelForDrag()
                helper.processTouchEvent(event)
                helper.captureChildView(sideBar, ev.getPointerId(0))
                helper.processTouchEvent(ev)
                notifyOpenState(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> clearDown()
        }
        return false
    }

    // 容器只做拖动仲裁；点击语义属于胶囊与遮罩，这里不产生点击事件。
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!claimed) return super.onTouchEvent(ev)

        helper.processTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                claimed = false
                clearDown()
            }
        }
        return true
    }

    override fun computeScroll() {
        if (helper.continueSettling(true)) {
            postInvalidateOnAnimation()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun isDownEligible(x: Float, y: Float): Boolean {
        if (x < interactionLeftPx || x > interactionRightPx) return false
        if (y < interactionTopPx || y > interactionBottomPx) return false
        if (isVisible) {
            // 打开态只从面板范围起手，遮罩区域保留点击关闭。
            return x <= interactionLeftPx + drawerWidthPx
        }
        val edge = interactionLeftPx + (interactionRightPx - interactionLeftPx) * EDGE_START_RATIO
        return x <= edge
    }

    /** 关闭态第一次拖动前面板是 GONE；先按关闭位置摆好再交给 helper 捕获。 */
    private fun preparePanelForDrag() {
        if (progress > 0f) return
        val closedLeft = interactionLeftPx - drawerWidthPx
        sideBar.layout(
            closedLeft,
            interactionTopPx,
            closedLeft + drawerWidthPx,
            interactionBottomPx,
        )
        scrim.visibility = VISIBLE
        scrim.setProgress(0f)
        sideBar.visibility = VISIBLE
    }

    private fun setProgress(value: Float) {
        val bounded = value.coerceIn(0f, 1f)
        if (bounded == progress) return
        progress = bounded
        scrim.setProgress(progress)
        applyLayout()
        updateVisibility()
        notifyOpenState(progress > 0f)
    }

    private fun applyLayout() {
        val width = (interactionRightPx - interactionLeftPx).coerceAtLeast(0)
        val height = (interactionBottomPx - interactionTopPx).coerceAtLeast(0)
        if (width <= 0 || height <= 0) return

        scrim.layoutParams = LayoutParams(width, height).apply {
            leftMargin = interactionLeftPx
            topMargin = interactionTopPx
        }

        val visibleWidth = (drawerWidthPx * progress).roundToInt()
        sideBar.layoutParams = LayoutParams(drawerWidthPx, height).apply {
            leftMargin = interactionLeftPx - drawerWidthPx + visibleWidth
            topMargin = interactionTopPx
        }
    }

    private fun updateVisibility() {
        val visible = progress > 0f
        scrim.visibility = if (visible) VISIBLE else GONE
        sideBar.visibility = if (visible) VISIBLE else GONE
    }

    private fun notifyOpenState(open: Boolean) {
        if (lastNotifiedOpen == open) return
        lastNotifiedOpen = open
        onOpenChanged?.invoke(open)
    }

    private fun cancelDrag() {
        if (claimed) {
            claimed = false
            helper.cancel()
        }
        clearDown()
    }

    private fun clearDown() {
        downEvent?.recycle()
        downEvent = null
        edgeCandidate = false
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private inner class DragCallback : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean = child === sideBar

        override fun getViewHorizontalDragRange(child: View): Int = drawerWidthPx

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            val closedLeft = interactionLeftPx - drawerWidthPx
            return left.coerceIn(closedLeft, interactionLeftPx)
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int,
        ) {
            if (drawerWidthPx <= 0) return
            val closedLeft = interactionLeftPx - drawerWidthPx
            val value = ((left - closedLeft).toFloat() / drawerWidthPx).coerceIn(0f, 1f)
            progress = value
            scrim.setProgress(value)
            updateVisibility()
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val open = when {
                xvel > minFlingVelocity -> true
                xvel < -minFlingVelocity -> false
                else -> progress > 0.5f
            }
            settleTo(open)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_IDLE && !claimed) {
                normalizeAfterSettle()
            }
        }
    }

    private fun settleTo(open: Boolean) {
        val target = if (open) interactionLeftPx else interactionLeftPx - drawerWidthPx
        if (helper.smoothSlideViewTo(sideBar, target, interactionTopPx)) {
            postInvalidateOnAnimation()
        } else {
            normalizeAfterSettle()
        }
    }

    /** settle 结束后用稳定进度写回布局参数，避免依赖拖动偏移。 */
    private fun normalizeAfterSettle() {
        val open = progress > 0.5f
        progress = if (open) 1f else 0f
        scrim.setProgress(progress)
        applyLayout()
        updateVisibility()
        notifyOpenState(progress > 0f)
    }

    private companion object {
        /** 关闭态起手带占交互区域宽度的比例。 */
        const val EDGE_START_RATIO = 0.22f
    }
}
