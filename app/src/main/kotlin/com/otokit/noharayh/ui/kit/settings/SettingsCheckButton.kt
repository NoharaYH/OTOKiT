package com.otokit.noharayh.ui.kit.settings

import android.animation.ArgbEvaluator
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.dimensions.SettingsButtonDimensions as Dimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsCheckButtonDimensions
import com.otokit.noharayh.ui.constants.motion.SettingsCheckMotion as Motion
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

/** 容器内检查按钮。页面只提交确认状态和文案，按钮持有图文、底色和动画。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class SettingsCheckButton(context: Context, label: String, onClick: () -> Unit) : PressSurface(context) {
    enum class State { READY, CHECKING, SUCCESS, FAILURE, WARNING }

    private data class Presentation(val state: State, val label: String, @DrawableRes val icon: Int? = null)

    private val icon = ImageView(context)
    private val text = ViewPrimitives.text(context, label, Dimensions.TEXT_SP, Colors.TEXT)
    private val evaluator = ArgbEvaluator()
    private var target = Presentation(State.READY, label)
    private var displayed: Presentation? = null
    private val spin = ContentTransition(0f, Motion.ROTATION, ::lerp, ::showRotation)
    private val swap = ContentTransition(0f, Motion.SWAP, ::lerp, ::showSwap)

    init {
        outerHaloEnabled = true
        baseRadius = dp(Dimensions.RADIUS_DP).toFloat()
        minimumHeight = dp(Dimensions.HEIGHT_DP)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val inset = dp(Dimensions.INSET_DP)
            val vertical = dp(SettingsCheckButtonDimensions.VERTICAL_INSET_DP)
            setPadding(inset, vertical, inset, vertical)
            addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            addView(icon, LinearLayout.LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)).apply {
                marginStart = dp(Dimensions.ICON_GAP_DP)
            })
        }, LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        setOnClickListener { onClick() }
        settle()
    }

    fun render(state: State, label: String, @DrawableRes icon: Int? = null) {
        val next = Presentation(state, label, icon)
        if (next == target) return
        target = next
        when {
            state == State.CHECKING -> {
                swap.stop()
                settle()
                spin.play(Motion.ROTATION_DEGREES, isAttachedToWindow, from = 0f)
            }
            displayed?.state == State.CHECKING && state != State.READY -> {
                if (!spin.isRunning) startSwap()
            }
            else -> {
                spin.stop()
                swap.stop()
                settle()
            }
        }
    }

    private fun showRotation(degrees: Float) {
        icon.rotation = degrees
        if (degrees == Motion.ROTATION_DEGREES && !spin.isRunning && target.state != State.CHECKING) startSwap()
    }

    private fun startSwap() = swap.play(1f, isAttachedToWindow, from = 0f)

    private fun showSwap(progress: Float) {
        if (progress >= 1f) {
            settle()
            return
        }
        val entering = progress >= 0.5f
        if (entering) showContent()
        val visibility = if (entering) (progress - 0.5f) * 2f else 1f - progress * 2f
        transformContent(visibility)
        fillColor = evaluator.evaluate(if (entering) visibility else 0f,
            Colors.CONTENT, backgroundFor(target.state)) as Int
    }

    private fun settle() {
        showContent()
        transformContent(1f)
        fillColor = backgroundFor(target.state)
        isEnabled = target.state != State.CHECKING
        contentDescription = target.label
    }

    private fun showContent() {
        if (displayed == target) return
        displayed = target
        text.text = target.label
        val (drawable, ink) = when (target.state) {
            State.SUCCESS -> R.drawable.ic_check_circle_24 to Colors.SUCCESS_TEXT
            State.FAILURE -> R.drawable.ic_cancel_24 to Colors.ERROR_TEXT
            State.WARNING -> R.drawable.ic_warning_24 to Colors.CAUTION_TEXT
            else -> R.drawable.ic_update_24 to Colors.TEXT
        }
        icon.rotation = 0f
        icon.setImageResource(target.icon ?: drawable)
        icon.setColorFilter(ink)
        text.setTextColor(ink)
    }

    private fun transformContent(visibility: Float) {
        val scale = lerp(Motion.SWAP_SCALE, 1f, visibility)
        icon.alpha = visibility
        icon.scaleX = scale
        icon.scaleY = scale
        text.pivotX = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) text.width.toFloat() else 0f
        text.alpha = visibility
        text.scaleX = scale
        text.scaleY = scale
    }

    private fun backgroundFor(state: State) = when (state) {
        State.SUCCESS -> Colors.SUCCESS_CONTENT
        State.FAILURE -> Colors.ERROR_CONTENT
        State.WARNING -> Colors.CAUTION_CONTENT
        else -> Colors.CONTENT
    }

    override fun onDetachedFromWindow() {
        spin.stop()
        swap.stop()
        settle()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
    private fun lerp(from: Float, to: Float, progress: Float) = from + (to - from) * progress
}
