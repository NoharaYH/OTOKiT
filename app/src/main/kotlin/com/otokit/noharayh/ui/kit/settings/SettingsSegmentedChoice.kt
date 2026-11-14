package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import androidx.core.graphics.ColorUtils
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.ui.animation.settings.SettingsChoiceTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.SettingsChoiceDimensions as Dimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.motion.SettingsChoiceMotion
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
    private var disabledProgress = 0f
    private val textColors = mutableListOf<Int>()
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
        textColors.clear()
        transition.stop()
        this.onSelected = onSelected
        this.selectedIndex = selectedIndex

        labels.forEachIndexed { index, label ->
            val text = TextView(context).apply {
                text = label
                textSize = Dimensions.TEXT_SP
                maxLines = 1
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(if (index == selectedIndex) ACTIVE_TEXT else INACTIVE_TEXT)
                AppTypography.applyAppTypeface(this, bold = true)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            text.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            val buttonHeight = dp(Dimensions.HEIGHT_DP)
            val textPadding = ((buttonHeight - text.measuredHeight) / 2).coerceAtLeast(0)
            val buttonWidth = text.measuredWidth + textPadding * 2
            val surface = PressSurface(context).apply {
                configureSingleChoiceSemantics(index == selectedIndex, column = index)
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
            textColors += if (index == selectedIndex) ACTIVE_TEXT else INACTIVE_TEXT
            buttons += surface
            this.labels += text
            addView(
                surface,
                LayoutParams(buttonWidth, buttonHeight).apply {
                    if (index > 0) marginStart = dp(Dimensions.GAP_DP)
                },
            )
        }
    }

    fun setControlsEnabled(enabled: Boolean) {
        buttons.forEach { it.isEnabled = enabled }
    }

    fun setDisabledProgress(progress: Float) {
        disabledProgress = progress.coerceIn(0f, 1f)
        buttons.forEachIndexed { index, button ->
            button.fillColor = ColorUtils.blendARGB(INACTIVE_FILL, 0xFFFAFAFA.toInt(), disabledProgress)
            labels[index].setTextColor(displayColor(textColors[index]))
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
        // 语义立即跟随确认值；圆角/文字动画只负责视觉过渡。
        buttons.forEachIndexed { itemIndex, button -> button.isSelected = itemIndex == index }
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
        val fromColor = textColors[index]
        val toColor = if (toCapsule) ACTIVE_TEXT else INACTIVE_TEXT
        transition.play(index, fromRadius, toRadius, fromColor, toColor) { radius, color ->
            surface.baseRadius = radius
            textColors[index] = color
            text.setTextColor(displayColor(color))
            surface.invalidate()
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
            1, buttons.size, false, AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE)
    }

    private fun applyState(index: Int, capsule: Float) {
        val surface = buttons.getOrNull(index) ?: return
        val text = labels.getOrNull(index) ?: return
        surface.baseRadius = radiusFor(capsule > 0.5f)
        textColors[index] = if (capsule > 0.5f) ACTIVE_TEXT else INACTIVE_TEXT
        text.setTextColor(displayColor(textColors[index]))
        surface.invalidate()
    }

    private fun displayColor(color: Int): Int =
        ColorUtils.blendARGB(color, android.graphics.Color.WHITE,
            SettingsChoiceMotion.DISABLED_BLEND * disabledProgress)

    private fun radiusFor(capsule: Boolean): Float = dp(
        if (capsule) Dimensions.RADIUS_DP else SettingsCardDimensions.CONTENT_CORNER_DP,
    ).toFloat()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val ACTIVE_TEXT: Int = 0xFF2196F3.toInt()
        val INACTIVE_TEXT: Int = 0xFF616161.toInt()
        const val INACTIVE_FILL: Int = 0xFFF5F5F5.toInt()
    }
}
