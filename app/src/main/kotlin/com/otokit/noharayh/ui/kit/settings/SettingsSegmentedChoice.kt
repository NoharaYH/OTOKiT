package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.configureSingleChoiceSemantics
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
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

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
            val color = if (index == selectedIndex) Colors.SELECTED else Colors.TEXT
            val surface = SettingsCompactButton(context, label, color) {
                if (this.selectedIndex != index) this.onSelected?.invoke(index)
            }.apply {
                configureSingleChoiceSemantics(index == selectedIndex, column = index)
                baseRadius = radiusFor(index == selectedIndex)
            }
            textColors += color
            buttons += surface
            this.labels += surface.textView
            addView(
                surface,
                LayoutParams(surface.minimumWidth, surface.minimumHeight).apply {
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
            button.fillColor = ColorUtils.blendARGB(Colors.CONTENT, Colors.DISABLED_FILL, disabledProgress)
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
        val toColor = if (toCapsule) Colors.SELECTED else Colors.TEXT
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
        textColors[index] = if (capsule > 0.5f) Colors.SELECTED else Colors.TEXT
        text.setTextColor(displayColor(textColors[index]))
        surface.invalidate()
    }

    private fun displayColor(color: Int): Int =
        ColorUtils.blendARGB(color, Colors.SURFACE,
            Colors.DISABLED_TEXT_BLEND * disabledProgress)

    private fun radiusFor(capsule: Boolean): Float = dp(
        if (capsule) Dimensions.RADIUS_DP else Dimensions.UNSELECTED_RADIUS_DP,
    ).toFloat()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

}

internal class SettingsCompactButton(context: Context, label: String, color: Int, onClick: () -> Unit) :
    PressSurface(context) {
    val textView = TextView(context).apply {
        text = label
        textSize = Dimensions.TEXT_SP
        maxLines = 1
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(color)
        AppTypography.applyAppTypeface(this, bold = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        minimumHeight = ViewPrimitives.dp(context, Dimensions.HEIGHT_DP)
        val padding = ((minimumHeight - textView.measuredHeight) / 2).coerceAtLeast(0)
        minimumWidth = textView.measuredWidth + padding * 2
        outerHaloEnabled = true
        deferClickUntilReleaseFeedback = false
        fillColor = Colors.CONTENT
        baseRadius = ViewPrimitives.dp(context, Dimensions.UNSELECTED_RADIUS_DP).toFloat()
        contentDescription = label
        setPadding(padding, padding, padding, padding)
        addView(textView, FrameLayout.LayoutParams(-1, -1))
        setOnClickListener { onClick() }
    }
}
