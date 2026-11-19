package com.otokit.noharayh.ui.kit.settings

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.ArchiveRecordListDimensions as Dimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.constants.motion.PressDeformationSpec
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** The viewport keeps its size while the record button and its trailing or leading icon share its width. */
internal class ArchiveRecordRow(context: Context, label: String, onClick: () -> Unit) : FrameLayout(context) {
    val button = SettingsButton(context, label, SettingsButton.Kind.Action,
        trailingIcon = null, contentHeight = LayoutParams.MATCH_PARENT, onClick = onClick)
    private val gap = dp(context, SettingsGroupDimensions.GAP_DP)
    private val iconSize = dp(context, Dimensions.RANGE_ICON_DP)
    private val chip = ContinuousCornerDrawable(Colors.CONTENT, 0f)
    private val icon = ImageView(context).apply {
        background = chip
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(Colors.TEXT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }
    private var reveal = 0f
    private var leading = true
    private val springPosition = FloatValueHolder()
    private val rebound = SpringAnimation(springPosition).apply {
        spring = SpringForce(0f)
            .setDampingRatio(PressDeformationSpec.PRESS_DAMPING)
            .setStiffness(PressDeformationSpec.PRESS_STIFFNESS)
        setMinValue(0f)
        addUpdateListener { _, value, _ -> renderReveal(value) }
        addEndListener { _, cancelled, _, _ -> if (!cancelled) renderReveal(0f) }
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(icon, LayoutParams(0, LayoutParams.MATCH_PARENT))
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun beginReveal(first: Boolean) {
        rebound.cancel()
        icon.setImageResource(if (first) R.drawable.ic_first_page_24 else R.drawable.ic_last_page_24)
    }

    fun setGroupCorners(top: Float, bottom: Float) {
        if (button.topCornerRadius == top && button.bottomCornerRadius == bottom) return
        button.topCornerRadius = top
        button.bottomCornerRadius = bottom
        chip.setRadii(top, bottom)
    }

    fun dragReveal(offset: Float) {
        leading = offset >= 0f
        renderReveal(abs(offset))
    }

    fun releaseReveal() {
        if (reveal == 0f || !ValueAnimator.areAnimatorsEnabled()) renderReveal(0f)
        else rebound.setStartValue(reveal).setStartVelocity(0f).animateToFinalPosition(0f)
    }

    private fun renderReveal(distance: Float) {
        reveal = distance
        if (isLaidOut) layoutSurfaces()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        layoutSurfaces()
    }

    private fun layoutSurfaces() {
        val offset = reveal.roundToInt().coerceIn(0, (width - iconSize).coerceAtLeast(0))
        val chipWidth = (offset - minOf(gap, offset / 3)).coerceAtLeast(0)
        val iconLeft = if (leading) 0 else width - chipWidth
        val buttonLeft = if (leading) offset else 0
        icon.visibility = if (chipWidth > 0) VISIBLE else GONE
        icon.imageAlpha = (255f * (chipWidth.toFloat() / iconSize).coerceAtMost(1f)).roundToInt()
        place(icon, iconLeft, chipWidth)
        place(button, buttonLeft, width - offset)
    }

    private fun place(view: View, left: Int, width: Int) {
        if (view.measuredWidth != width || view.measuredHeight != height) {
            view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
        view.layout(left, 0, left + width, height)
    }

    override fun onDetachedFromWindow() {
        rebound.cancel()
        reveal = 0f
        super.onDetachedFromWindow()
    }
}
