package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.customview.widget.ViewDragHelper
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.window.PixelRect
import kotlin.math.abs
import kotlin.math.roundToInt

/** 抽屉共同父容器；仅管理自身呈现和手势，导航/输入策略由宿主连接。 */
class SideBarHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val scrim = SideBarScrimView(context)
    private val sideBar = SideBar(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val motion = DrawerMotionState()
    private val helper = ViewDragHelper.create(this, 1f, DragCallback())

    private var interaction = PixelRect.Zero
    private var drawerWidthPx = 0
    private var dragEnabled = false
    private var resumed = true
    private var disposed = false
    private var stoppingHelper = false
    private var lastNotifiedBlocked = false
    private var downEvent: MotionEvent? = null
    private var downX = 0f
    private var downY = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var edgeCandidate = false

    var onNavigate: ((Destination) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    /** 包含拖动、双向收敛和旧触摸流隔离，不只是面板是否可见。 */
    var onInputBlockedChanged: ((Boolean) -> Unit)? = null

    val blocksUnderlyingInput: Boolean get() = !disposed && motion.blocksInput
    private val interactionAllowed: Boolean
        get() = !disposed && resumed && dragEnabled && !interaction.isEmpty && drawerWidthPx > 0
    private val closedLeft: Int get() = interaction.left - drawerWidthPx

    init {
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(sideBar, LayoutParams(
            resources.getDimensionPixelSize(ShellDimensions.shellSideNavWidth), LayoutParams.MATCH_PARENT,
        ))
        sideBar.visibility = INVISIBLE
        sideBar.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        sideBar.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        scrim.setOnClickListener { close() }
        sideBar.onNavigate = { if (interactionAllowed) onNavigate?.invoke(it) }
        sideBar.onSettings = { if (interactionAllowed) onSettings?.invoke() }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        bringChildToFront(scrim)
        bringChildToFront(sideBar)
    }

    fun setMetrics(rect: PixelRect, widthPx: Int) {
        if (disposed) return
        val width = widthPx.coerceIn(0, rect.width.coerceAtLeast(0))
        if (interaction == rect && drawerWidthPx == width) return
        close(immediate = true)
        interaction = rect
        drawerWidthPx = width
        sideBar.setPanelWidth(width)
        applyLayout()
    }

    fun setDragEnabled(enabled: Boolean) {
        if (disposed || dragEnabled == enabled) return
        dragEnabled = enabled
        if (!enabled) close(immediate = true)
    }

    fun setResumed(value: Boolean) {
        if (disposed || resumed == value) return
        resumed = value
        if (!value) {
            close(immediate = true)
            finishStream()
        }
    }

    /** 仅操作自己的面板；跨模块焦点恢复由 shell 协调。 */
    fun requestPanelFocus(): Boolean =
        blocksUnderlyingInput && interactionAllowed && sideBar.requestNavigationFocus()

    fun setSelected(destination: Destination) {
        if (!disposed) sideBar.setSelected(destination)
    }

    fun open() {
        if (interactionAllowed) animateTo(true)
    }

    fun close(immediate: Boolean = false) {
        if (disposed) return
        if (immediate) {
            stopHelper()
            motion.forceClose()
            clearDown()
            sideBar.cancelPressFeedback()
            positionPanel()
            updateBlocking()
        } else if (motion.blocksInput) {
            animateTo(false)
        }
    }

    fun toggle() {
        if (motion.targetOpen ?: (motion.progress > 0f)) close() else open()
    }

    fun release() {
        if (disposed) return
        close(immediate = true)
        finishStream()
        disposed = true
        onNavigate = null
        onSettings = null
        onInputBlockedChanged = null
        scrim.setOnClickListener(null)
        sideBar.dispose()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (motion.dragging) cancelCapturedDrag()
            finishStream()
            downEvent = MotionEvent.obtain(event)
            downX = event.x
            downY = event.y
            pointerId = event.getPointerId(0)
            edgeCandidate = interactionAllowed && isDownEligible(event.x, event.y)
        }
        val terminal = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        if (event.actionMasked == MotionEvent.ACTION_CANCEL && motion.dragging) {
            cancelCapturedDrag()
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_UP
        ) {
            edgeCandidate = false
            if (motion.dragging) cancelCapturedDrag()
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            if (terminal) finishStream()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (motion.discardUntilEnd || motion.dragging) return true
        if (!interactionAllowed || !edgeCandidate || event.actionMasked != MotionEvent.ACTION_MOVE) {
            return false
        }
        val index = event.findPointerIndex(pointerId)
        if (index < 0) {
            edgeCandidate = false
            return false
        }
        val dx = event.getX(index) - downX
        val dy = event.getY(index) - downY
        if (abs(dx) <= touchSlop && abs(dy) <= touchSlop) return false
        if (abs(dx) <= abs(dy) ||
            (motion.progress == 0f && dx <= 0f) || (motion.progress > 0f && dx >= 0f)
        ) {
            edgeCandidate = false // 本轮已判给其他方向，不在中途抢回。
            return false
        }
        val down = downEvent ?: return false
        stopHelper()
        if (!motion.beginDrag()) return false
        positionPanel()
        updateBlocking() // 先同步关闭背景选择门禁，再由框架取消旧触摸目标。
        helper.processTouchEvent(down)
        helper.captureChildView(sideBar, pointerId)
        // 当前 MOVE 只在 onTouchEvent 处理一次。
        return true
    }

    @SuppressLint("ClickableViewAccessibility") // 这里只处理拖动；点击属于胶囊和遮罩。
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (motion.discardUntilEnd) return true
        if (!motion.dragging) return super.onTouchEvent(event)
        helper.processTouchEvent(event)
        return true
    }

    override fun computeScroll() {
        if (!disposed && helper.continueSettling(true)) postInvalidateOnAnimation()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 背景归位等操作可能重新布局；始终从当前进度恢复，而非旧的关闭态 margin。
        positionPanel()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child !== sideBar) return super.drawChild(canvas, child, drawingTime)
        val saved = canvas.save()
        return try {
            canvas.clipRect(interaction.left, interaction.top, interaction.right, interaction.bottom)
            super.drawChild(canvas, child, drawingTime)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun isDownEligible(x: Float, y: Float): Boolean {
        if (x < interaction.left || x >= interaction.right ||
            y < interaction.top || y >= interaction.bottom
        ) return false
        return if (motion.progress > 0f) {
            x < interaction.left + drawerWidthPx
        } else {
            x < interaction.left + interaction.width * EDGE_START_RATIO
        }
    }

    private fun animateTo(open: Boolean) {
        stopHelper()
        motion.request(open)
        clearDown()
        startSettle(open, released = false)
    }

    private fun cancelCapturedDrag() {
        stopHelper()
        motion.cancelDrag()
        clearDown()
        startSettle(motion.targetOpen == true, released = false)
    }

    private fun startSettle(open: Boolean, released: Boolean) {
        positionPanel()
        updateBlocking()
        val left = if (open) interaction.left else closedLeft
        val moving = if (released) {
            helper.settleCapturedViewAt(left, interaction.top)
        } else {
            helper.smoothSlideViewTo(sideBar, left, interaction.top)
        }
        if (moving) postInvalidateOnAnimation() else finishSettle()
    }

    private fun finishSettle() {
        motion.finishSettle()
        positionPanel()
        updateBlocking()
    }

    /** abort 可同步回调旧终点，期间禁止它覆盖当前进度或发布过期 Idle。 */
    private fun stopHelper() {
        stoppingHelper = true
        try {
            helper.abort()
        } finally {
            stoppingHelper = false
        }
        positionPanel()
    }

    private fun finishStream() {
        clearDown()
        motion.endStream()
        updateBlocking()
    }

    private fun clearDown() {
        downEvent?.recycle()
        downEvent = null
        pointerId = MotionEvent.INVALID_POINTER_ID
        edgeCandidate = false
    }

    @SuppressLint("RtlHardcoded") // 与 shell 提供的物理窗口坐标保持一致。
    private fun applyLayout() {
        val panelHeight = interaction.height.coerceAtLeast(0)
        if (interaction.isEmpty) return
        // 遮罩延伸到窗口底部：手势导航区域透明，不能在这里截断黑渐变。
        val scrimHeight = (height - interaction.top).coerceAtLeast(panelHeight)
        scrim.layoutParams = LayoutParams(
            interaction.width.coerceAtLeast(0), scrimHeight, Gravity.TOP or Gravity.LEFT,
        ).apply {
            leftMargin = interaction.left
            topMargin = interaction.top
        }
        sideBar.layoutParams = LayoutParams(drawerWidthPx, panelHeight, Gravity.TOP or Gravity.LEFT).apply {
            leftMargin = closedLeft
            topMargin = interaction.top
        }
        sideBar.visibility = if (interaction.isEmpty || drawerWidthPx == 0) INVISIBLE else VISIBLE
        positionPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!disposed) applyLayout()
    }

    private fun positionPanel() {
        if (drawerWidthPx <= 0) return
        val left = closedLeft + (drawerWidthPx * motion.progress).roundToInt()
        sideBar.offsetLeftAndRight(left - sideBar.left)
        sideBar.offsetTopAndBottom(interaction.top - sideBar.top)
        scrim.setProgress(motion.progress)
    }

    private fun updateBlocking() {
        val blocked = blocksUnderlyingInput
        scrim.setInputBlocked(blocked)
        sideBar.importantForAccessibility = if (blocked) IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        sideBar.descendantFocusability = if (blocked) FOCUS_AFTER_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
        sideBar.isFocusableInTouchMode = blocked
        sideBar.isFocusable = blocked
        if (!blocked && sideBar.hasFocus()) sideBar.clearFocus()
        if (lastNotifiedBlocked == blocked) return
        lastNotifiedBlocked = blocked
        onInputBlockedChanged?.invoke(blocked)
    }

    private inner class DragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean =
            !disposed && !stoppingHelper && motion.dragging && child === sideBar

        override fun getViewHorizontalDragRange(child: View): Int = drawerWidthPx

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int =
            left.coerceIn(closedLeft, interaction.left)

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = interaction.top

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            if (disposed || stoppingHelper || drawerWidthPx <= 0) return
            motion.moveTo((left - closedLeft).toFloat() / drawerWidthPx)
            scrim.setProgress(motion.progress)
            updateBlocking()
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            if (disposed || stoppingHelper || !motion.dragging) return
            val open = when {
                xvel > minFlingVelocity -> true
                xvel < -minFlingVelocity -> false
                else -> motion.progress >= 0.5f
            }
            motion.releaseTo(open)
            startSettle(open, released = true)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (disposed || stoppingHelper) return
            if (state == ViewDragHelper.STATE_IDLE && !motion.dragging) finishSettle()
        }
    }

    private companion object {
        const val EDGE_START_RATIO = 0.22f
    }
}
