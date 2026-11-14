package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.animation.common.ContinuousCorners
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.ui.animation.settings.ThemeDropdownArrowRunner
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion
import com.otokit.noharayh.ui.constants.motion.PressHaloSpec
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 胶囊触发框；描边和反馈层共享按压几何，不叠加固定尺寸外框。 */
@android.annotation.SuppressLint("ViewConstructor") // 仅由代码提供标签并创建。
internal class LabeledDropdownField(context: Context, label: String) : PressSurface(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF757575.toInt()
        style = Paint.Style.STROKE
        strokeWidth = Dimensions.FIELD_STROKE_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val outline = RectF()
    private val outlinePath = Path()
    private val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val panelFillPath = Path()
    private var outlineBottomRadius = 0f
    private var outlineMaximumBottomRadius = 0f
    override val surfaceFillInset: Float
        get() = stroke.strokeWidth / 2f
    private fun valueText() = TextView(context).apply {
        textSize = 14f
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(0xFF323232.toInt())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        AppTypography.applyAppTypeface(this)
    }
    private val valueView = valueText()
    private val nextValueView = valueText().apply { alpha = 0f }
    private var pendingValue: String? = null
    private val arrow = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_right_24)
        setColorFilter(0xFF757575.toInt())
        rotation = 90f
    }
    private val labelView = TextView(context).apply {
        text = label
        textSize = 11f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(0xFF757575.toInt())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        AppTypography.applyAppTypeface(this)
    }
    private val prefixView = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelView.textSize)
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
        typeface = labelView.typeface
        setTextColor(labelView.currentTextColor)
    }
    private var prefixProgress = 0f
    private var drawingAlpha = 255
    /** 标题及外环的绘制外溢范围，供自身合成层和宿主过渡裁剪共同使用。 */
    val drawingOverflow: Float
        get() = maxOf(baseRadius, labelView.height.toFloat(), prefixView.height.toFloat())

    // View 默认的透明度合成层只覆盖自身矩形，会裁掉伸出顶部的浮动标题。
    // 由组件合成整个绘制结果，保留标题和外环，同时避免子元素分别淡化导致叠色。
    override fun onSetAlpha(alpha: Int): Boolean {
        drawingAlpha = alpha
        invalidate()
        return true
    }

    override fun draw(canvas: Canvas) {
        if (drawingAlpha == 255) {
            super.draw(canvas)
            return
        }
        val overflow = drawingOverflow
        val layer = canvas.saveLayerAlpha(-overflow, -overflow,
            width + overflow, height + panelHeight + overflow, drawingAlpha)
        super.draw(canvas)
        canvas.restoreToCount(layer)
    }

    fun configureLabelPrefix(prefix: String) {
        prefixView.text = prefix
    }

    fun setLabelPrefixProgress(progress: Float) {
        prefixProgress = progress.coerceIn(0f, 1f)
        prefixView.alpha = prefixProgress
        labelView.translationX = prefixView.paint.measureText(prefixView.text.toString()) * prefixProgress
        updateDescription()
        invalidate()
    }

    private val arrowRunner = ThemeDropdownArrowRunner(arrow)
    private var panelHeight = 0f
    private var panelGap = 0f
    private var panelExpansion = 0f
    private var menuBottomRadius = 0f

    init {
        fillColor = Color.WHITE
        outerHaloEnabled = true
        baseRadius = dp(Dimensions.FIELD_RADIUS_DP).toFloat()
        clipChildren = false
        clipToPadding = false
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Dimensions.FIELD_TEXT_INSET_DP), 0, dp(Dimensions.FIELD_END_INSET_DP), 0)
            addView(FrameLayout(context).apply {
                addView(valueView, LayoutParams(-1, -1))
                addView(nextValueView, LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(arrow, LinearLayout.LayoutParams(dp(Dimensions.FIELD_ARROW_DP), dp(Dimensions.FIELD_ARROW_DP)))
        }
        addView(row, LayoutParams(-1, dp(Dimensions.FIELD_HEIGHT_DP)))
        addView(prefixView, LayoutParams(-2, -2).apply {
            leftMargin = dp(Dimensions.FIELD_TEXT_INSET_DP)
            rightMargin = dp(Dimensions.FIELD_TEXT_INSET_DP)
        })
        addView(labelView, LayoutParams(-2, -2).apply {
            leftMargin = dp(Dimensions.FIELD_TEXT_INSET_DP)
            rightMargin = dp(Dimensions.FIELD_TEXT_INSET_DP)
        })
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        labelView.translationY = -labelView.height / 2f + stroke.strokeWidth / 2f
        prefixView.translationY = labelView.translationY
        setLabelPrefixProgress(prefixProgress)
    }

    private fun updateOutlineGeometry(cornerRadius: Float, inset: Float): Float {
        val half = stroke.strokeWidth / 2f
        outline.set(inset + half, inset + half, width - inset - half, height + panelHeight - inset - half)
        val radius = (cornerRadius - half).coerceAtLeast(0f)
        // 菜单与框的左右、底部留白相同。半径之差等于边界之差，圆心才能重合。
        val clearance = dp(Dimensions.MENU_PADDING_DP) - inset - half
        val expandedRadius = (menuBottomRadius + clearance).coerceAtLeast(0f)
        outlineBottomRadius = (radius + (expandedRadius - radius) * panelExpansion)
            .coerceIn(0f, minOf(outline.width() / 2f, outline.height() - radius).coerceAtLeast(0f))
        val maximumRadius = Dimensions.MENU_LARGE_RADIUS_DP * resources.displayMetrics.density + clearance
        outlineMaximumBottomRadius = (radius + (maximumRadius - radius) * panelExpansion)
            .coerceIn(0f, minOf(outline.width() / 2f, outline.height() - radius).coerceAtLeast(0f))
        return radius
    }

    /** 与描边共用断口；下端以最大圆角的切点为界，不随最后一项的压缩状态漂移。 */
    private fun topCornerLimit() = minOf(outline.width(), height - outline.top * 2f).coerceAtLeast(0f) / 2f

    private fun sideGap(radius: Float): Pair<Float, Float> {
        val haloBottom = height + (PressHaloSpec.PEAK_OUTSET_DP + Dimensions.FIELD_STROKE_DP) * resources.displayMetrics.density
        return themePanelSideGap(outline.top + ContinuousCorners.extent(radius, topCornerLimit()), outline.bottom,
            ContinuousCorners.extent(outlineMaximumBottomRadius, minOf(outline.width(), outline.height()) / 2f), haloBottom, panelGap,
            (Dimensions.FIELD_HEIGHT_DP + Dimensions.SCROLLBAR_END_GAP_DP * 2f) * resources.displayMetrics.density)
    }

    fun scrollbarTrack(): Pair<Float, Float> = sideGap(updateOutlineGeometry(baseRadius, 0f))

    override fun drawUnderlay(canvas: Canvas, cornerRadius: Float, inset: Float) {
        if (panelHeight <= 0f) return
        val radius = updateOutlineGeometry(cornerRadius, inset)
        ContinuousCorners.setPath(panelFillPath, outline, radius, outlineBottomRadius, topCornerLimit())
        // 位于外环之上、标题内部涟漪之下；白底随描边延展，盖住原胶囊的下半圈。
        canvas.drawPath(panelFillPath, panelFill)
    }

    override fun drawOutline(canvas: Canvas, cornerRadius: Float, inset: Float) {
        val radius = updateOutlineGeometry(cornerRadius, inset)
        val bottomRadius = outlineBottomRadius
        val limit = minOf(outline.width(), outline.height()) / 2f
        val topLimit = topCornerLimit()
        val topExtent = ContinuousCorners.extent(radius, topLimit)
        val bottomExtent = ContinuousCorners.extent(bottomRadius, limit)
        val gapStart = (labelView.left - dp(Dimensions.LABEL_GAP_DP)).toFloat().coerceIn(outline.left + topExtent, outline.right - topExtent)
        val gapEnd = (labelView.right + labelView.translationX + dp(Dimensions.LABEL_GAP_DP)).coerceIn(gapStart, outline.right - topExtent)
        labelView.translationY = outline.top - labelView.height / 2f
        prefixView.translationY = outline.top - prefixView.height / 2f
        // 描边在标签两侧主动断开，圆形笔帽收尾；文字不再用白色矩形覆盖描边。
        outlinePath.rewind()
        outlinePath.moveTo(gapEnd, outline.top)
        outlinePath.lineTo(outline.right - topExtent, outline.top)
        ContinuousCorners.corner(outlinePath, outline.right, outline.top, radius, topLimit, 0)
        // 上段越过标题外环的峰值范围，断口不与黑圈下缘相交。
        val (upperEnd, lowerStart) = sideGap(radius)
        outlinePath.lineTo(outline.right, upperEnd)
        if (lowerStart > upperEnd) outlinePath.moveTo(outline.right, lowerStart)
        outlinePath.lineTo(outline.right, outline.bottom - bottomExtent)
        ContinuousCorners.corner(outlinePath, outline.right, outline.bottom, bottomRadius, limit, 1)
        outlinePath.lineTo(outline.left + bottomExtent, outline.bottom)
        ContinuousCorners.corner(outlinePath, outline.left, outline.bottom, bottomRadius, limit, 2)
        outlinePath.lineTo(outline.left, outline.top + topExtent)
        ContinuousCorners.corner(outlinePath, outline.left, outline.top, radius, topLimit, 3)
        outlinePath.lineTo(gapStart, outline.top)
        canvas.drawPath(outlinePath, stroke)
    }

    fun setValue(title: String) {
        pendingValue = null
        valueView.text = title
        valueView.alpha = 1f
        valueView.translationY = 0f
        nextValueView.alpha = 0f
        updateDescription()
    }

    fun prepareValueTransition(title: String): Boolean {
        if (valueView.text.toString() == title) return false
        pendingValue = title
        nextValueView.text = title
        return true
    }

    fun renderValueTransition(progress: Float) {
        val title = pendingValue ?: return
        val t = progress.coerceIn(0f, 1f)
        if (t >= 1f) { setValue(title); return }
        val shift = ThemeSelectionMotion.FIELD_TRANSLATION_DP * resources.displayMetrics.density
        valueView.alpha = 1f - t
        valueView.translationY = -shift * t
        nextValueView.alpha = t
        nextValueView.translationY = -shift * (1f - t)
    }

    private fun updateDescription() {
        val prefix = if (prefixProgress >= 0.5f) prefixView.text else ""
        contentDescription = "$prefix${labelView.text}：${pendingValue ?: valueView.text}"
    }

    fun setControlsEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun setDisabledProgress(progress: Float) {
        val t = progress.coerceIn(0f, 1f)
        stroke.color = ColorUtils.blendARGB(0xFF757575.toInt(), 0xFFCCCCCC.toInt(), t)
        val color = ColorUtils.blendARGB(0xFF323232.toInt(), 0xFFB3B3B3.toInt(), t)
        valueView.setTextColor(color)
        nextValueView.setTextColor(color)
        labelView.setTextColor(stroke.color)
        prefixView.setTextColor(stroke.color)
        arrow.imageAlpha = (255 - 158 * t).toInt()
        invalidate()
    }

    fun renderPanel(extraHeight: Float, gap: Float, expansion: Float = 0f, bottomRadius: Float = 0f) {
        panelHeight = extraHeight
        panelGap = gap
        panelExpansion = expansion
        menuBottomRadius = bottomRadius
        invalidate()
    }

    fun setExpanded(expanded: Boolean) { arrowRunner.render(expanded) }
    fun previewExpanded(progress: Float) { arrowRunner.preview(progress) }

    override fun onDetachedFromWindow() {
        arrowRunner.reset()
        super.onDetachedFromWindow()
    }


    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}

/** 胶囊闭合时两段圆弧相接；分别计算的端点可能相差一个浮点单位，按相接处理。 */
internal fun themePanelSideEnd(topArcEnd: Float, bottomArcStart: Float, preferredEnd: Float): Float =
    preferredEnd.coerceIn(topArcEnd, bottomArcStart.coerceAtLeast(topArcEnd))

internal fun themePanelSideGap(topArcEnd: Float, bottom: Float, maximumRadius: Float,
    preferredEnd: Float, progress: Float, minimumGap: Float = 0f): Pair<Float, Float> {
    val upper = themePanelSideEnd(topArcEnd, bottom - maximumRadius, preferredEnd)
    val lower = (bottom - maximumRadius).coerceAtLeast(upper)
    val middle = (upper + lower) / 2f
    // 够放下一段完整滑块及两端留白才直接切开，端点不再从中点向两边退缩。
    return if (progress > 0f && lower - upper >= minimumGap) upper to lower else middle to middle
}
