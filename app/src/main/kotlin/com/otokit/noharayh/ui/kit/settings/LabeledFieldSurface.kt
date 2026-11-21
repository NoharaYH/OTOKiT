package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.ui.animation.common.ContinuousRoundedPath
import com.otokit.noharayh.ui.constants.dimensions.LabeledFieldDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.LabeledFieldColors as Colors
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 浮动标签、描边及按压表面；内容和可选尾部由成品组件提供，不持有菜单或业务。 */
@android.annotation.SuppressLint("ViewConstructor")
internal open class LabeledFieldSurface(context: Context, label: String,
    private val contentTouchFeedback: Boolean = false,
) : PressSurface(context) {
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Colors.STROKE
        style = Paint.Style.STROKE
        strokeWidth = Dimensions.STROKE_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    protected val continuousPath = ContinuousRoundedPath()
    protected val outline = RectF()
    private val outlinePath = Path()
    protected var outlineBottomRadius = 0f
    protected open val outlineExtraHeight: Float get() = 0f
    protected val labelView = TextView(context).apply {
        text = label
        textSize = Dimensions.LABEL_SP
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(Colors.STROKE)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        AppTypography.applyAppTypeface(this)
    }
    private val contentSlot = FrameLayout(context)
    private val trailingSlot = FrameLayout(context).apply { visibility = GONE }
    private val row = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(Dimensions.HEIGHT_DP)
        clipChildren = false
        clipToPadding = false
    }
    private var drawingAlpha = 255
    override val surfaceFillInset: Float get() = stroke.strokeWidth / 2f
    val floatingLabelOutset: Float get() = ((labelView.measuredHeight - stroke.strokeWidth) / 2f).coerceAtLeast(0f)
    val floatingLabelContentInset: Float get() = (labelView.measuredHeight + stroke.strokeWidth) / 2f
    val drawingOverflow: Float get() = maxOf(dp(Dimensions.FEEDBACK_RADIUS_DP).toFloat(), labelView.height.toFloat())

    init {
        fillColor = Colors.SURFACE
        outerHaloEnabled = true
        baseRadius = dp(Dimensions.COLLAPSED_RADIUS_DP).toFloat()
        clipChildren = false
        clipToPadding = false
        row.addView(contentSlot, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(trailingSlot, LinearLayout.LayoutParams(-2, -1))
        addView(row, LayoutParams(-1, -2))
        addView(labelView, LayoutParams(-2, -2).apply {
            leftMargin = dp(Dimensions.TEXT_INSET_DP)
            rightMargin = dp(Dimensions.TEXT_INSET_DP)
        })
        if (contentTouchFeedback) {
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    protected fun setFieldContent(view: View, inset: Boolean = true) {
        row.setPadding(if (inset) dp(Dimensions.TEXT_INSET_DP) else 0, 0,
            if (inset) dp(Dimensions.END_INSET_DP) else 0, 0)
        contentSlot.removeAllViews()
        contentSlot.addView(view, LayoutParams(-1, -1))
    }

    protected fun setFieldTrailing(view: View, width: Int) {
        trailingSlot.removeAllViews()
        trailingSlot.addView(view, LayoutParams(width, width, Gravity.CENTER))
        trailingSlot.visibility = VISIBLE
    }

    override fun onSetAlpha(alpha: Int): Boolean {
        if (drawingAlpha != alpha) {
            drawingAlpha = alpha
            invalidate()
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        if (drawingAlpha == 255) { super.draw(canvas); return }
        val overflow = drawingOverflow
        val saved = canvas.saveLayerAlpha(-overflow, -overflow,
            width + overflow, height + outlineExtraHeight + overflow, drawingAlpha)
        super.draw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        positionFloatingLabel(stroke.strokeWidth / 2f)
    }

    protected open fun positionFloatingLabel(outlineTop: Float) {
        labelView.translationY = outlineTop - labelView.height / 2f
    }

    protected open fun updateOutlineGeometry(cornerRadius: Float, inset: Float): Float {
        val half = stroke.strokeWidth / 2f
        outline.set(inset + half, inset + half, width - inset - half, height + outlineExtraHeight - inset - half)
        val radius = (cornerRadius - half).coerceAtLeast(0f)
        outlineBottomRadius = radius
        return radius
    }

    /** 可选右侧描边断口，仅接收几何，不决定它的用途。 */
    protected open fun outlineSideGap(radius: Float): Pair<Float, Float>? = null

    override fun buildSurfacePath(path: Path, bounds: RectF, top: Float, bottom: Float) =
        continuousPath.setPath(path, bounds, top, bottom)

    override fun drawOutline(canvas: Canvas, cornerRadius: Float, inset: Float) {
        val radius = updateOutlineGeometry(cornerRadius, inset)
        val bottomRadius = outlineBottomRadius
        val topExtent = continuousPath.horizontalExtent(radius, outline)
        val topVerticalExtent = continuousPath.verticalExtent(radius, outline)
        val bottomExtent = continuousPath.horizontalExtent(bottomRadius, outline)
        val bottomVerticalExtent = continuousPath.verticalExtent(bottomRadius, outline)
        val gapStart = (labelView.left - dp(Dimensions.LABEL_GAP_DP)).toFloat()
        val gapEnd = labelView.right + labelView.translationX + dp(Dimensions.LABEL_GAP_DP)
        positionFloatingLabel(outline.top)
        outlinePath.rewind()
        outlinePath.moveTo(outline.left + topExtent, outline.top)
        outlinePath.lineTo(outline.right - topExtent, outline.top)
        continuousPath.corner(outlinePath, outline, radius, 0)
        outlineSideGap(radius)?.let { (upperEnd, lowerStart) ->
            outlinePath.lineTo(outline.right, upperEnd)
            if (lowerStart > upperEnd) outlinePath.moveTo(outline.right, lowerStart)
        }
        outlinePath.lineTo(outline.right, outline.bottom - bottomVerticalExtent)
        continuousPath.corner(outlinePath, outline, bottomRadius, 1)
        outlinePath.lineTo(outline.left + bottomExtent, outline.bottom)
        continuousPath.corner(outlinePath, outline, bottomRadius, 2)
        outlinePath.lineTo(outline.left, outline.top + topVerticalExtent)
        continuousPath.corner(outlinePath, outline, radius, 3)
        val saved = canvas.save()
        canvas.clipOutRect(gapStart, outline.top - labelView.height,
            gapEnd, outline.top + labelView.height / 2f + stroke.strokeWidth)
        canvas.drawPath(outlinePath, stroke)
        canvas.restoreToCount(saved)
    }

    fun setControlsEnabled(enabled: Boolean) { isEnabled = enabled }

    open fun setDisabledProgress(progress: Float) {
        val t = progress.coerceIn(0f, 1f)
        stroke.color = ColorUtils.blendARGB(Colors.STROKE, Colors.DISABLED_STROKE, t)
        labelView.setTextColor(stroke.color)
        renderDisabledContent(t, ColorUtils.blendARGB(Colors.TEXT, Colors.DISABLED_TEXT, t))
        invalidate()
    }

    protected open fun renderDisabledContent(progress: Float, textColor: Int) = Unit

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!contentTouchFeedback) return super.dispatchTouchEvent(event)
        // 被动观察一次反馈；原事件继续交给原生编辑器，不拦截选区、长按或 IME。
        val handled = super.dispatchTouchEvent(event)
        val feedback = super.onTouchEvent(event)
        return handled || feedback
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (contentTouchFeedback) false else super.onTouchEvent(event)

    // 编辑模式的真实点击及无障碍事件由 EditText 发出，表面只保留通用释放反馈。
    override fun performClick(): Boolean = if (contentTouchFeedback) true else super.performClick()
    protected fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
