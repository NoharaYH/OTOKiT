package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.motion.ContainerMotion

/** Switches the trailing content in a card header without moving the card or its rows. */
internal class SettingsHeaderActions(context: Context, private val idle: View?, private val active: View) :
    FrameLayout(context) {
    private var showing = false
    private val shift = ContainerMotion.CONTENT_SHIFT_DP * resources.displayMetrics.density / 2f
    private val transition = ContentTransition(0f, ContainerMotion.CONTENT, { a, b, t -> a + (b - a) * t }, ::render)

    init {
        clipChildren = false
        clipToPadding = false
        listOfNotNull(idle, active).forEach { addView(it, LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL)) }
        render(0f)
    }

    fun showActions(show: Boolean) {
        if (showing == show) return
        showing = show
        transition.play(if (show) 1f else 0f, animate = isAttachedToWindow)
    }

    private fun render(progress: Float) {
        active.alpha = ((progress - .2f) / .8f).coerceIn(0f, 1f)
        active.translationX = (1f - progress) * shift
        active.visibility = if (progress > 0f) VISIBLE else GONE
        active.importantForAccessibility = if (showing) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        idle?.apply {
            alpha = (1f - progress / .55f).coerceIn(0f, 1f)
            visibility = if (progress < .55f) VISIBLE else GONE
            importantForAccessibility = if (showing) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    override fun onDetachedFromWindow() {
        transition.finish()
        super.onDetachedFromWindow()
    }
}
