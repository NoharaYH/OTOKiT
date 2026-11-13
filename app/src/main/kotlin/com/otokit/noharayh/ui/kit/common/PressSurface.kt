package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.animation.common.PressHalo
import com.otokit.noharayh.ui.kit.common.PressSurfaceSpec
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 按压反馈容器，基于用户提供的 PressSurface。
 *
 * 按下时弹簧驱动等距内收、圆角与白色覆盖；黑圆先弹性扩张，再回落到静止直径并跟手。
 * 黑圆始终裁切在按钮圆角内，所以按钮本身要足够大（当前示例 260×160dp）才能完整容纳 120dp 的黑圆。
 * 内容由 FrameLayout 在遮罩上方绘制，不随遮罩变色。
 *
 * ## 外溢外环的宿主约束
 * 启用 [outerHaloEnabled] 时，[PressHalo] 会绘制到本 View 的边界之外。
 * Android 父容器默认 `clipChildren=true`，会把子 View 的绘制裁回自身边界，
 * 表现为“外环只能扩到按钮原来的方形区域”。
 *
 * 因此**直接父容器必须同时设置**：
 * ```kotlin
 * clipChildren = false
 * clipToPadding = false
 * ```
 * 本组件不会反向修改父容器，避免跨层副作用；调用方在自己的容器上显式声明。
 * 现有示例：`GamePlaceholderPanel`、`SettingsHome`、`AppSettingsPage` 的选项容器。
 */
open class PressSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val dp = resources.displayMetrics.density

    var fillColor: Int = 0xFF6750A4.toInt()
        set(value) {
            field = value
            fillPaint.color = value
            invalidate()
        }
    var baseRadius = 16f * dp
    /** 分组选项可分别描述上下圆角；为空时沿用统一 baseRadius。 */
    var topCornerRadius: Float? = null
    var bottomCornerRadius: Float? = null
    var pressInset = PressSurfaceSpec.INSET_DP * dp
    /** 可生长容器的底端形状由展开进度约束；外环仍使用头行的按压几何。 */
    var bottomCornerPressFraction = 1f
    /** 可只保留外扩反馈；按压收缩、触觉和点击分发不受影响。 */
    var innerRippleEnabled = true
    var whiteMaxAlpha = PressSurfaceSpec.WHITE_ALPHA
    var followTauMs = 30f
    var maxRipples = PressSurfaceSpec.MAX_RIPPLES

    /** 离开窗口的动作可等待释放反馈；不含外链或页面等业务语义。 */
    var deferClickUntilReleaseFeedback = false
    /** 自带展开反馈的组件可关闭释放震动，避免两次触觉叠加。 */
    var releaseHapticEnabled = true

    private val pressStateListeners = mutableListOf<(Boolean) -> Unit>()

    /** 供容器观察按压事实（例如把按压行提到最前）；返回注销函数。 */
    fun addOnPressStateChangedListener(listener: (Boolean) -> Unit): () -> Unit {
        if (listener !in pressStateListeners) pressStateListeners += listener
        return { pressStateListeners -= listener }
    }

    /**
     * 是否绘制通用黑色外溢层；内容区按钮与设置卡片共用，标题/胶囊另有定位方式。
     *
     * 启用前请确认直接父容器已设置 `clipChildren=false`、`clipToPadding=false`；
     * 否则外环会被裁回按钮自身边界。组件不替调用方修改父级裁剪。
     */
    var outerHaloEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                if (halo == null) halo = PressHalo(this)
            } else {
                halo?.reset()
                halo = null
            }
            invalidate()
        }

    private var halo: PressHalo? = null

    /**
     * 黑圆最大直径。
     *
     * 120dp ≈ 19mm，略大于指腹遮挡宽度；与按钮宽度无关，
     * 由按钮圆角裁切，因此按钮本身要足够大才能完整容纳。
     */
    var rippleMaxDiameter = 120f * dp

    /** 回落后保持的直径（106dp ≈ 16.8mm），约为峰值的 88%。 */
    var rippleRestDiameter = 106f * dp

    /** 扩张到最大直径后回落到静止直径的时间；点按要快，不能等。 */
    var rippleExpandMs = PressSurfaceSpec.EXPAND_DURATION_MS

    private val rippleMaxScale: Float
        get() = rippleMaxDiameter / rippleRestDiameter.coerceAtLeast(1f)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(PressSurfaceSpec.BLACK_ALPHA, 0, 0, 0)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val shapeRect = RectF()
    private val shapePath = Path()
    private val shapeCorners = FloatArray(8)

    /**
     * 按压进度弹簧（0 = 静止，1 = 完全按下）。
     *
     * 0.85 阻尼 / 1000 刚度：按下干脆、松手不弹。可见幅度只有 [pressInset] 的 3dp，
     * 过冲会被绘制时的夹取吃掉，所以这里刻意不做额外弹跳。
     */
    private val pressHolder = FloatValueHolder(0f)
    private val press = SpringAnimation(pressHolder).apply {
        setSpring(SpringForce(0f).setDampingRatio(PressSurfaceSpec.PRESS_DAMPING)
            .setStiffness(PressSurfaceSpec.PRESS_STIFFNESS))
        setMinimumVisibleChange(PressSurfaceSpec.MIN_VISIBLE_CHANGE)
        addUpdateListener { _, _, _ -> invalidate() }
    }

    /**
     * 单个黑圆实例。
     *
     * 扩张使用 Material3 expressive fast spatial 弹簧（dampingRatio 0.8 / stiffness 1600，
     * 峰值约 55ms），到 [rippleExpandMs] 后回落到静止直径；松手后再按剩余展示时间收缩消失。
     */
    private inner class Ripple(val bornAt: Long, startX: Float, startY: Float) {

        var x = startX
        var y = startY
        var targetX = startX
        var targetY = startY

        private var released = false

        val expansion = FloatValueHolder(0f)
        val spring = SpringAnimation(expansion).apply {
            setSpring(
                SpringForce(rippleMaxScale).apply {
                    dampingRatio = PressSurfaceSpec.EXPAND_DAMPING
                    stiffness = PressSurfaceSpec.EXPAND_STIFFNESS
                },
            )
            setMinimumVisibleChange(PressSurfaceSpec.MIN_VISIBLE_CHANGE)
            addUpdateListener { _, _, _ -> invalidate() }
        }

        private val retractToRest = Runnable {
            if (released) return@Runnable
            // 回落稍慢、阻尼更高，避免在静止尺寸上反复弹动。
            // 运行中保留同一个 SpringForce，避免新实例丢失 start() 初始化的停止阈值。
            spring.spring.setDampingRatio(PressSurfaceSpec.REST_DAMPING)
                .setStiffness(PressSurfaceSpec.REST_STIFFNESS)
            spring.animateToFinalPosition(1f)
        }

        private val shrink = Runnable {
            if (!released) return@Runnable
            spring.spring.setDampingRatio(PressSurfaceSpec.RELEASE_DAMPING)
                .setStiffness(PressSurfaceSpec.RELEASE_STIFFNESS)
            spring.animateToFinalPosition(0f)
        }

        fun start() {
            spring.animateToFinalPosition(rippleMaxScale)
            postDelayed(retractToRest, rippleExpandMs)
        }

        fun release() {
            if (released) return
            released = true
            val age = now() - bornAt
            postDelayed(shrink, (rippleExpandMs - age).coerceAtLeast(0L))
        }

        fun cancel() {
            removeCallbacks(retractToRest)
            removeCallbacks(shrink)
            spring.cancel()
        }

        fun isDead(): Boolean = released && !spring.isRunning && expansion.value <= 0.01f
    }

    private val ripples = ArrayList<Ripple>()
    private var held: Ripple? = null
    private var lastFrame = 0L
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    /** 按下触觉的开始时间与手势序号；用于把抬起触觉排到按下播放结束之后。 */
    private var pressHapticAt = 0L
    private var gestureSeq = 0
    private var scheduledHapticSeq = -1
    private var deferredClick = false
    private val releaseHapticRunnable = Runnable {
        val valid = scheduledHapticSeq == gestureSeq
        scheduledHapticSeq = -1
        if (valid) deliverReleaseFeedback()
    }
    private val deferredClickRunnable = Runnable {
        val dispatch = deferredClick && canDeliverFeedback()
        deferredClick = false
        if (dispatch) performClick()
    }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    private fun now() = AnimationUtils.currentAnimationTimeMillis()
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** 可选装饰只接收按压事实，不接管点击、触觉或内部涟漪。 */
    protected open fun onPressStateChanged(pressed: Boolean) {
        if (pressed) halo?.press() else halo?.release()
        pressStateListeners.forEach { it(pressed) }
    }

    /** 底层装饰位于填充和内容下面；默认不绘制。 */
    protected open fun drawUnderlay(canvas: Canvas, cornerRadius: Float, inset: Float) = Unit

    /** 与底形共享按压几何的描边，绘制在反馈层之上、文字之下。 */
    protected open fun drawOutline(canvas: Canvas, cornerRadius: Float, inset: Float) = Unit

    /** 填充与内部反馈相对外形额外内缩的距离，带描边的控件可对齐到描边中心。 */
    protected open val surfaceFillInset: Float = 0f
    /** 白底可随内容生长，按压圆角与外环的高度仍可固定为原始头行。 */
    protected open val pressFeedbackHeight: Float get() = height.toFloat()

    private companion object {
        /** 按下与抬起触觉之间的最小间隔，避免抬起被 HAL 以 ignored_for_ongoing 丢弃。 */
        const val MIN_HAPTIC_GAP_MS = PressSurfaceSpec.MIN_HAPTIC_GAP_MS
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 一次已接受的点击正在完成反馈，不让第二次按下覆盖或重复派发它。
        if (deferredClick) return true
        if (!isEnabled || !isClickable) {
            release()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 上一个手势没收到 UP/CANCEL 又来了新的 DOWN：先把旧涟漪释放，避免永久停在按钮上。
                held?.release()
                while (ripples.size >= maxRipples.coerceAtLeast(1)) {
                    ripples.removeAt(0).cancel()
                }
                val time = now()
                lastFrame = time
                val ripple = Ripple(time, event.x, event.y)
                ripples += ripple
                held = ripple
                activePointerId = event.getPointerId(0)
                isPressed = true
                onPressStateChanged(true)
                press.animateToFinalPosition(1f)
                ripple.start()
                gestureSeq++
                removeCallbacks(releaseHapticRunnable)
                pressHapticAt = SystemClock.uptimeMillis()
                tickDown()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val ripple = held ?: return true
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    release()
                    return true
                }
                ripple.targetX = event.getX(index)
                ripple.targetY = event.getY(index)
                if (!inside(ripple.targetX, ripple.targetY)) release()
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 原按下手指先离开时取消，不能由另一根手指的抬起来补成点击。
                if (event.getPointerId(event.actionIndex) == activePointerId) release()
            }
            MotionEvent.ACTION_UP -> {
                val ripple = held
                if (ripple != null) {
                    ripple.targetX = event.x
                    ripple.targetY = event.y
                }
                val clicked = ripple != null && inside(event.x, event.y)
                release()
                if (clicked) {
                    if (deferClickUntilReleaseFeedback && releaseHapticEnabled) {
                        deferredClick = true
                        requestReleaseHaptic()
                    } else {
                        // 在回调替换页面/禁用按钮之前，交给当前窗口完成已接受点击的反馈。
                        if (releaseHapticEnabled) requestAcceptedReleaseHaptic()
                        performClick()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    private fun inside(x: Float, y: Float) = x in 0f..width.toFloat() && y in 0f..height.toFloat()

    private fun release() {
        isPressed = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        val ripple = held ?: return
        held = null
        onPressStateChanged(false)
        ripple.release()
        press.animateToFinalPosition(0f)
        invalidate()
    }

    /**
     * 按下触觉。使用系统键盘点击常量：比 SEGMENT_FREQUENT_TICK 更明显，
     * 且仍由 ROM 按自家马达调校。
     */
    private fun tickDown() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun tickUp(target: View = this): Boolean =
        target.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
            else HapticFeedbackConstants.CLOCK_TICK,
        )

    private fun canDeliverFeedback(): Boolean =
        isAttachedToWindow && hasWindowFocus() && isShown && isEnabled && isClickable

    /** 点击已经验收：源 View 可因重绘、动画或站内导航被禁用/替换，窗口仍可交付抬手触觉。 */
    private fun requestAcceptedReleaseHaptic() {
        if (!isHapticFeedbackEnabled) return
        val window = rootView
        val sequence = gestureSeq
        window.postDelayed({
            if (sequence == gestureSeq && window.isAttachedToWindow && window.hasWindowFocus() && window.isShown) {
                tickUp(window)
            }
        }, releaseHapticDelay())
    }

    private fun releaseHapticDelay(): Long =
        (MIN_HAPTIC_GAP_MS - (SystemClock.uptimeMillis() - pressHapticAt)).coerceAtLeast(0L)

    private fun deliverReleaseFeedback() {
        if (!canDeliverFeedback()) {
            cancelDeferredClick()
            return
        }
        if (!deferredClick) {
            tickUp()
            return
        }
        // 触觉关闭或系统拒绝时不额外等待；受理后留出短暂播放时间再离开窗口。
        val accepted = tickUp()
        if (accepted) postDelayed(deferredClickRunnable, PressSurfaceSpec.RELEASE_FEEDBACK_SETTLE_MS)
        else deferredClickRunnable.run()
    }

    private fun cancelDeferredClick() {
        deferredClick = false
        scheduledHapticSeq = -1
        removeCallbacks(releaseHapticRunnable)
        removeCallbacks(deferredClickRunnable)
    }

    /**
     * 抬起触觉。
     *
     * 快速点按时，按下震动（本机 VIRTUAL_KEY 约 80–90ms）还在播放，OEM 的 HAL 会把紧接着的
     * 抬起震动标记为 `ignored_for_ongoing` 丢掉。这里按“距按下至少 120ms”补齐等待，
     * 并用手势序号丢弃过期回调，避免延迟到下一次点按才响。
     */
    private fun requestReleaseHaptic() {
        val delay = releaseHapticDelay()
        if (delay == 0L) {
            deliverReleaseFeedback()
            return
        }
        scheduledHapticSeq = gestureSeq
        removeCallbacks(releaseHapticRunnable)
        postDelayed(releaseHapticRunnable, delay)
    }

    override fun onDraw(canvas: Canvas) {
        val time = now()
        val delta = if (lastFrame == 0L) 0L else (time - lastFrame).coerceIn(0L, 50L)
        lastFrame = time

        val progress = pressHolder.value
        val boundedProgress = progress.coerceIn(0f, 1f)
        val inset = max(progress, 0f) * pressInset
        shapeRect.set(inset, inset, width - inset, height - inset)
        val cap = min(shapeRect.width(), shapeRect.height()) / 2f
        val pressedCap = min(cap, (pressFeedbackHeight - inset * 2f).coerceAtLeast(0f) / 2f)
        // 固定 dp 等距内收；按下圆角取内收后短边的一半，不受静止圆角大小影响。
        val radius = lerp(baseRadius.coerceAtMost(cap), pressedCap, boundedProgress)
            .coerceAtMost(cap)
        val top = lerp((topCornerRadius ?: baseRadius).coerceAtMost(cap), pressedCap, boundedProgress)
        val haloBottom = lerp((bottomCornerRadius ?: baseRadius).coerceAtMost(pressedCap), pressedCap, boundedProgress)
        val bottom = lerp((bottomCornerRadius ?: baseRadius).coerceAtMost(cap), pressedCap,
            boundedProgress * bottomCornerPressFraction.coerceIn(0f, 1f))
        val fillInset = surfaceFillInset.coerceIn(0f, cap)

        // 外环半径要补上按压内收量，才能和按钮保持同心；否则外圈两端会变成圆角矩形，
        // 看起来像被一个更大的方框限位。
        // 外环收尾必须回到实际填充边界；带内缩填充的描边组件否则会残留半圈黑边。
        // 与主题触发框相同：先画原头行外环，再让生长后的白底盖住下半圈。
        halo?.draw(canvas, width, pressFeedbackHeight.roundToInt().coerceIn(0, height), inset + fillInset,
            (top - fillInset).coerceAtLeast(0f), (haloBottom - fillInset).coerceAtLeast(0f))
        drawUnderlay(canvas, radius, inset)

        val follow = 1f - exp(-delta / followTauMs)
        ripples.removeAll { it.isDead() }
        for (ripple in ripples) {
            ripple.x += (ripple.targetX - ripple.x) * follow
            ripple.y += (ripple.targetY - ripple.y) * follow
        }

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        shapeRect.inset(fillInset, fillInset)
        shapeCorners.fill((top - fillInset).coerceAtLeast(0f), 0, 4)
        shapeCorners.fill((bottom - fillInset).coerceAtLeast(0f), 4, 8)
        shapePath.reset()
        shapePath.addRoundRect(shapeRect, shapeCorners, Path.Direction.CW)
        canvas.drawPath(shapePath, fillPaint)
        whitePaint.alpha = (whiteMaxAlpha * boundedProgress * 255f).roundToInt()
        if (whitePaint.alpha > 0) canvas.drawRect(shapeRect, whitePaint)

        // 黑圆裁切在按钮圆角之内，按固定 dp 尺寸弹性扩张；按钮需足够大才能完整容纳。
        val restRadius = rippleRestDiameter / 2f
        if (innerRippleEnabled) for (ripple in ripples) {
            val radiusNow = restRadius * ripple.expansion.value
            if (radiusNow > 0.5f) canvas.drawCircle(ripple.x, ripple.y, radiusNow, ripplePaint)
        }
        canvas.restoreToCount(layer)
        drawOutline(canvas, radius, inset)
        if (ripples.isNotEmpty()) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        cancelDeferredClick()
        halo?.reset()
        press.cancel()
        pressHolder.value = 0f
        ripples.forEach { it.cancel() }
        ripples.clear()
        held = null
        lastFrame = 0L
        activePointerId = MotionEvent.INVALID_POINTER_ID
        isPressed = false
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus && deferredClick) cancelDeferredClick()
    }
}
