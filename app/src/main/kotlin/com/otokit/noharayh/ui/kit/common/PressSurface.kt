package com.otokit.noharayh.ui.kit.common

import com.otokit.noharayh.ui.constants.dimensions.PressSurfaceDimensions
import com.otokit.noharayh.ui.constants.colors.PressSurfaceColors
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.PressHalo
import com.otokit.noharayh.ui.constants.motion.PressDeformationSpec
import com.otokit.noharayh.ui.constants.motion.PressOverlaySpec
import com.otokit.noharayh.ui.constants.motion.TouchIndicatorSpec
import com.otokit.noharayh.ui.animation.common.TouchIndicator
import com.otokit.noharayh.ui.animation.common.PressProgress
import com.otokit.noharayh.ui.animation.common.PressGeometry
import kotlin.math.roundToInt

/**
 * 按压反馈容器，基于用户提供的 PressSurface。
 *
 * 按下时弹簧驱动等距内收、圆角与白色覆盖；黑圆先弹性扩张，再回落到静止直径并跟手。
 * 黑圆始终裁切在按钮圆角内，所以按钮本身要足够大（当前示例 260×160dp）才能完整容纳 120dp 的黑圆。
 * 内容由 FrameLayout 在遮罩上方绘制，不随遮罩变色。
 *
 * 所有半径、内收量与直径均使用 px，由调用方从 dp 换算；时长使用 ms，
 * 透明度与进度使用 0..1。绘制属性修改后自动 invalidate，不改变测量尺寸。
 * rippleMaxDiameter / rippleExpandMs / maxRipples 配置作用于后续按压，
 * 不重启已经运行的弹簧或重新排队已有任务。属性须在 UI 线程更新。
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

    var fillColor: Int = PressSurfaceColors.FILL
        set(value) {
            field = value
            fillPaint.color = value
            invalidate()
        }
    /** 静止圆角半径（px），绘制时按当前可用尺寸约束。 */
    var baseRadius = PressSurfaceDimensions.RADIUS_DP * dp
        set(value) { if (field != value) { field = value; invalidate() } }
    /** 上下圆角半径（px）；为空时沿用 baseRadius。 */
    var topCornerRadius: Float? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var bottomCornerRadius: Float? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    /** 每边按压内收量（px）。 */
    var pressInset = PressDeformationSpec.INSET_DP * dp
        set(value) { if (field != value) { field = value; invalidate() } }
    /** 可生长容器的底端形状由展开进度约束；外环仍使用头行的按压几何。 */
    var bottomCornerPressFraction = 1f
        set(value) { if (field != value) { field = value; invalidate() } }
    /** 可只保留外扩反馈；按压收缩、触觉和点击分发不受影响。 */
    var innerRippleEnabled = true
        set(value) { if (field != value) { field = value; invalidate() } }
    /** 白色反馈层的峰值不透明度（0..1）。 */
    var whiteMaxAlpha = PressOverlaySpec.WHITE_ALPHA
        set(value) { if (field != value) { field = value; invalidate() } }
    /** 涟漪跟随时间常数（ms），应大于零。 */
    var followTauMs = TouchIndicatorSpec.FOLLOW_TAU_MS
        set(value) { if (field != value) { field = value; invalidate() } }
    var maxRipples = TouchIndicatorSpec.MAX_RIPPLES

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
    var rippleMaxDiameter = TouchIndicatorSpec.MAX_DIAMETER_DP * dp

    /** 回落后保持的直径（106dp ≈ 16.8mm），约为峰值的 88%。 */
    var rippleRestDiameter = TouchIndicatorSpec.REST_DIAMETER_DP * dp
        set(value) { if (field != value) { field = value; invalidate() } }

    /** 扩张到最大直径后回落到静止直径的时间；点按要快，不能等。 */
    var rippleExpandMs = TouchIndicatorSpec.EXPAND_DURATION_MS

    private val rippleMaxScale: Float
        get() = rippleMaxDiameter / rippleRestDiameter.coerceAtLeast(1f)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(TouchIndicatorSpec.BLACK_ALPHA, 0, 0, 0)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val shapeRect = RectF()
    private val shapePath = Path()
    private val shapeCorners = FloatArray(8)

    private val press = PressProgress { invalidate() }

    private val ripples = ArrayList<TouchIndicator>()
    private var held: TouchIndicator? = null
    private var lastFrame = 0L
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val interaction = PressInteraction(this)

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    private fun now() = AnimationUtils.currentAnimationTimeMillis()

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

    @android.annotation.SuppressLint("ClickableViewAccessibility") // PressInteraction.click 统一调用本 View 的 performClick。
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 一次已接受的点击正在完成反馈，不让第二次按下覆盖或重复派发它。
        if (interaction.deferredClick) {
            return true
        }
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
                val ripple = TouchIndicator(this, time, event.x, event.y, rippleMaxScale, rippleExpandMs)
                ripples += ripple
                held = ripple
                activePointerId = event.getPointerId(0)
                isPressed = true
                onPressStateChanged(true)
                press.animateTo(1f)
                ripple.start()
                interaction.press()
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
                    interaction.click(deferClickUntilReleaseFeedback, releaseHapticEnabled)
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
        press.animateTo(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val time = now()
        val delta = if (lastFrame == 0L) 0L else (time - lastFrame).coerceIn(0L, 50L)
        lastFrame = time

        val geometry = PressGeometry.resolve(width.toFloat(), height.toFloat(), pressFeedbackHeight,
            press.value, pressInset, baseRadius, topCornerRadius, bottomCornerRadius,
            bottomCornerPressFraction, surfaceFillInset)
        val boundedProgress = geometry.progress
        val inset = geometry.inset
        val radius = geometry.radius
        val top = geometry.top
        val bottom = geometry.bottom
        val haloBottom = geometry.haloBottom
        val fillInset = geometry.fillInset
        shapeRect.set(inset, inset, width - inset, height - inset)

        // 外环半径要补上按压内收量，才能和按钮保持同心；否则外圈两端会变成圆角矩形，
        // 看起来像被一个更大的方框限位。
        // 外环收尾必须回到实际填充边界；带内缩填充的描边组件否则会残留半圈黑边。
        // 与主题触发框相同：先画原头行外环，再让生长后的白底盖住下半圈。
        halo?.draw(canvas, width, pressFeedbackHeight.roundToInt().coerceIn(0, height), inset + fillInset,
            (top - fillInset).coerceAtLeast(0f), (haloBottom - fillInset).coerceAtLeast(0f))
        drawUnderlay(canvas, radius, inset)

        ripples.removeAll { it.isDead() }
        ripples.forEach { it.follow(delta, followTauMs) }

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
        interaction.cancelDeferredClick()
        halo?.reset()
        press.reset()
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
        if (!hasWindowFocus && interaction.deferredClick) interaction.cancelDeferredClick()
    }
}
