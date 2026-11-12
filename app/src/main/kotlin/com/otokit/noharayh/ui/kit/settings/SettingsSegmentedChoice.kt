package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import androidx.core.graphics.ColorUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.ui.animation.settings.SettingsChoiceTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface

/**
 * 三态分段选择：自身持有三个 PressSurface，不随页面重建。
 *
 * 选中/取消只动画 baseRadius 与文字颜色；按下时的内收、ripple 和 halo 仍由
 * PressSurface 负责，因此不会出现松手后先弹回矩形再硬切成胶囊。
 */
internal class SettingsSegmentedChoice(context: Context) : LinearLayout(context) {

    private val buttons = mutableListOf<PressSurface>()
    private val labels = mutableListOf<TextView>()
    private val transition = SettingsChoiceTransitionRunner()
    private var selectedIndex = -1
    private var controlsEnabled = true
    private var onSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    fun setItems(
        labels: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        removeAllViews()
        buttons.clear()
        this.labels.clear()
        transition.stop()
        this.onSelected = onSelected
        this.selectedIndex = selectedIndex

        labels.forEachIndexed { index, label ->
            val text = TextView(context).apply {
                text = label
                textSize = 12f
                maxLines = 1
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(if (index == selectedIndex) ACTIVE_TEXT else INACTIVE_TEXT)
                AppTypography.applyAppTypeface(this, bold = true)
            }
            text.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            val buttonHeight = dp(Dimensions.CHOICE_HEIGHT_DP)
            val textPadding = ((buttonHeight - text.measuredHeight) / 2).coerceAtLeast(0)
            val buttonWidth = text.measuredWidth + textPadding * 2
            val surface = PressSurface(context).apply {
                outerHaloEnabled = true
                deferClickUntilReleaseFeedback = false
                fillColor = INACTIVE_FILL
                baseRadius = radiusFor(index == selectedIndex)
                contentDescription = label
                setPadding(textPadding, textPadding, textPadding, textPadding)
                addView(text, FrameLayout.LayoutParams(-1, -1))
                setOnClickListener {
                    if (this@SettingsSegmentedChoice.selectedIndex == index) return@setOnClickListener
                    this@SettingsSegmentedChoice.onSelected?.invoke(index)
                }
            }
            buttons += surface
            this.labels += text
            addView(
                surface,
                LayoutParams(buttonWidth, buttonHeight).apply {
                    if (index > 0) marginStart = dp(Dimensions.CHOICE_GAP_DP)
                },
            )
        }
    }

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
        buttons.forEachIndexed { index, button ->
            button.isEnabled = enabled
            button.fillColor = if (enabled) INACTIVE_FILL else 0xFFFAFAFA.toInt()
            labels[index].setTextColor(displayColor(if (index == selectedIndex) ACTIVE_TEXT else INACTIVE_TEXT))
        }
    }

    override fun onDetachedFromWindow() {
        transition.stop()
        buttons.indices.forEach { applyState(it, if (it == selectedIndex) 1f else 0f) }
        super.onDetachedFromWindow()
    }

    fun setSelection(index: Int, animate: Boolean = true) {
        if (index == selectedIndex || index !in buttons.indices) return
        val previous = selectedIndex
        selectedIndex = index
        if (!animate) {
            transition.stop(previous)
            transition.stop(index)
            applyState(previous, 0f)
            applyState(index, 1f)
            return
        }
        animateState(previous, toCapsule = false)
        animateState(index, toCapsule = true)
    }

    private fun animateState(index: Int, toCapsule: Boolean) {
        val surface = buttons.getOrNull(index) ?: return
        val text = labels.getOrNull(index) ?: return
        val fromRadius = surface.baseRadius
        val toRadius = radiusFor(toCapsule)
        val fromColor = (text.currentTextColor)
        val toColor = if (toCapsule) ACTIVE_TEXT else INACTIVE_TEXT
        transition.play(index, fromRadius, toRadius, fromColor, toColor) { radius, color ->
            surface.baseRadius = radius
            text.setTextColor(displayColor(color))
            surface.invalidate()
        }
    }

    private fun applyState(index: Int, capsule: Float) {
        val surface = buttons.getOrNull(index) ?: return
        val text = labels.getOrNull(index) ?: return
        surface.baseRadius = radiusFor(capsule > 0.5f)
        text.setTextColor(displayColor(if (capsule > 0.5f) ACTIVE_TEXT else INACTIVE_TEXT))
        surface.invalidate()
    }

    private fun displayColor(color: Int): Int =
        if (controlsEnabled) color else ColorUtils.blendARGB(color, android.graphics.Color.WHITE, 0.62f)

    private fun radiusFor(capsule: Boolean): Float = dp(
        if (capsule) Dimensions.CHOICE_RADIUS_DP else SettingsCardDimensions.CONTENT_CORNER_DP,
    ).toFloat()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val ACTIVE_TEXT: Int = 0xFF2196F3.toInt()
        val INACTIVE_TEXT: Int = 0xFF616161.toInt()
        const val INACTIVE_FILL: Int = 0xFFF5F5F5.toInt()
    }
}
