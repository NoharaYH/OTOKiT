package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.settings.ThemeDropdownArrowRunner
import com.otokit.noharayh.ui.constants.dimensions.LabeledFieldDimensions
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.motion.PressHaloSpec
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion
import com.otokit.noharayh.ui.constants.settings.LabeledFieldColors
import com.otokit.noharayh.ui.kit.common.AppTypography

/** 主题下拉入口；公共标签表面不承担菜单、箭头或主题选值过渡。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class LabeledDropdownField(context: Context, label: String) : LabeledFieldSurface(context, label) {
    private fun valueText() = TextView(context).apply {
        textSize = LabeledFieldDimensions.TEXT_SP
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(LabeledFieldColors.TEXT)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        AppTypography.applyAppTypeface(this)
    }
    private val valueView = valueText()
    private val nextValueView = valueText().apply { alpha = 0f }
    private var pendingValue: String? = null
    private val arrow = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_right_24)
        setColorFilter(LabeledFieldColors.STROKE)
        rotation = 90f
    }
    private val prefixView = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelView.textSize)
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
        typeface = labelView.typeface
        setTextColor(labelView.currentTextColor)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var prefixProgress = 0f
    private val arrowRunner = ThemeDropdownArrowRunner(arrow)
    private var panelHeight = 0f
    private var panelGap = 0f
    private var panelExpansion = 0f
    private val collapsedRadius = dp(Dimensions.FIELD_COLLAPSED_RADIUS_DP).toFloat()
    private var corners = ThemePanelCorners(collapsedRadius, collapsedRadius, collapsedRadius)
    private var outlineMaximumBottomRadius = 0f
    private val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val panelFillPath = Path()
    override val outlineExtraHeight get() = panelHeight
    val panelBottomCornerRadius: Float get() {
        if (width == 0) return corners.bottom
        updateOutlineGeometry(baseRadius, 0f)
        return outlineBottomRadius + stroke.strokeWidth / 2f
    }

    init {
        setFieldContent(FrameLayout(context).apply {
            addView(valueView, LayoutParams(-1, -1))
            addView(nextValueView, LayoutParams(-1, -1))
        })
        setFieldTrailing(arrow, dp(Dimensions.FIELD_ARROW_DP))
        addView(prefixView, indexOfChild(labelView), LayoutParams(-2, -2).apply {
            leftMargin = dp(Dimensions.FIELD_TEXT_INSET_DP)
            rightMargin = dp(Dimensions.FIELD_TEXT_INSET_DP)
        })
    }

    fun configureLabelPrefix(prefix: String) { prefixView.text = prefix }

    fun setLabelPrefixProgress(progress: Float) {
        prefixProgress = progress.coerceIn(0f, 1f)
        prefixView.alpha = prefixProgress
        labelView.translationX = prefixView.paint.measureText(prefixView.text.toString()) * prefixProgress
        updateDescription()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        setLabelPrefixProgress(prefixProgress)
    }

    override fun positionFloatingLabel(outlineTop: Float) {
        super.positionFloatingLabel(outlineTop)
        prefixView.translationY = labelView.translationY
    }

    override fun updateOutlineGeometry(cornerRadius: Float, inset: Float): Float {
        val radius = super.updateOutlineGeometry(cornerRadius, inset)
        val half = stroke.strokeWidth / 2f
        val adjustment = (cornerRadius - baseRadius) * (1f - panelExpansion) - inset * panelExpansion
        outlineBottomRadius = (corners.bottom + adjustment - half)
            .coerceIn(0f, minOf(outline.width() / 2f, outline.height() - radius).coerceAtLeast(0f))
        outlineMaximumBottomRadius = (corners.maximumBottom + adjustment - half)
            .coerceIn(0f, minOf(outline.width() / 2f, outline.height() - radius).coerceAtLeast(0f))
        return radius
    }

    override fun outlineSideGap(radius: Float): Pair<Float, Float> {
        val haloBottom = height + (PressHaloSpec.PEAK_OUTSET_DP + Dimensions.FIELD_STROKE_DP) * resources.displayMetrics.density
        return themePanelSideGap(outline.top + continuousPath.verticalExtent(radius, outline), outline.bottom,
            continuousPath.verticalExtent(outlineMaximumBottomRadius, outline), haloBottom, panelGap,
            (Dimensions.FIELD_HEIGHT_DP + Dimensions.SCROLLBAR_END_GAP_DP * 2f) * resources.displayMetrics.density)
    }

    fun scrollbarTrack(): Pair<Float, Float> = outlineSideGap(updateOutlineGeometry(baseRadius, 0f))

    override fun drawUnderlay(canvas: Canvas, cornerRadius: Float, inset: Float) {
        if (panelHeight <= 0f) return
        val radius = updateOutlineGeometry(cornerRadius, inset)
        continuousPath.setPath(panelFillPath, outline, radius, outlineBottomRadius)
        canvas.drawPath(panelFillPath, panelFill)
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

    override fun renderDisabledContent(progress: Float, textColor: Int) {
        valueView.setTextColor(textColor)
        nextValueView.setTextColor(textColor)
        prefixView.setTextColor(stroke.color)
        arrow.imageAlpha = (255 - 158 * progress).toInt()
    }

    fun renderPanel(extraHeight: Float, gap: Float, expansion: Float = 0f, bottomRadius: Float = 0f) {
        panelHeight = extraHeight
        panelGap = gap
        panelExpansion = expansion
        corners = ThemePanelCorners.resolve(expansion, collapsedRadius, dp(Dimensions.FIELD_RADIUS_DP).toFloat(),
            bottomRadius, Dimensions.MENU_LARGE_RADIUS_DP * resources.displayMetrics.density,
            dp(Dimensions.MENU_PADDING_DP).toFloat())
        baseRadius = corners.top
        invalidate()
    }

    fun setExpanded(expanded: Boolean) { arrowRunner.render(expanded) }
    fun previewExpanded(progress: Float) { arrowRunner.preview(progress) }

    override fun onDetachedFromWindow() { arrowRunner.reset(); super.onDetachedFromWindow() }
}

internal fun themePanelSideEnd(topArcEnd: Float, bottomArcStart: Float, preferredEnd: Float): Float =
    preferredEnd.coerceIn(topArcEnd, bottomArcStart.coerceAtLeast(topArcEnd))

internal fun themePanelSideGap(topArcEnd: Float, bottom: Float, maximumRadius: Float,
    preferredEnd: Float, progress: Float, minimumGap: Float = 0f): Pair<Float, Float> {
    val upper = themePanelSideEnd(topArcEnd, bottom - maximumRadius, preferredEnd)
    val lower = (bottom - maximumRadius).coerceAtLeast(upper)
    val middle = (upper + lower) / 2f
    return if (progress > 0f && lower - upper >= minimumGap) upper to lower else middle to middle
}
