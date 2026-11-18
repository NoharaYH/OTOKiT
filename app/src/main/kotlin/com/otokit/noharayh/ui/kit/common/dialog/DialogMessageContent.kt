package com.otokit.noharayh.ui.kit.common.dialog

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.otokit.noharayh.ui.constants.motion.ContainerMotion
import com.otokit.noharayh.ui.kit.common.AppTypography
import kotlin.math.roundToInt

/** 自然测量两份文案；动画高度不作为正文测量约束，主说明决定最小高度。 */
internal class DialogMessageContent(context: Context, primary: CharSequence, alternate: CharSequence?) : FrameLayout(context) {
    private val primaryView = message(primary)
    private val alternateView = alternate?.let(::message)
    var showingAlternate = false
        private set
    private var fromHeight = 0
    private var sizeProgress = 1f
    private val activeView get() = if (showingAlternate) alternateView!! else primaryView

    init {
        addView(primaryView, LayoutParams(-1, -2))
        alternateView?.let { addView(it, LayoutParams(-1, -2)); it.visibility = View.INVISIBLE }
    }

    fun select(alternate: Boolean) {
        fromHeight = height
        showingAlternate = alternate
        primaryView.visibility = if (alternate) View.INVISIBLE else View.VISIBLE
        alternateView?.visibility = if (alternate) View.VISIBLE else View.INVISIBLE
    }

    fun render(progress: Float, resize: Boolean) {
        activeView.alpha = progress
        activeView.translationY = -ContainerMotion.CONTENT_SHIFT_DP * resources.displayMetrics.density * (1f - progress)
        if (resize) { sizeProgress = progress; requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val baseline = primaryView.measuredHeight
        val target = maxOf(baseline, activeView.measuredHeight)
        val current = fromHeight + (target - fromHeight) * sizeProgress
        setMeasuredDimension(measuredWidth, maxOf(baseline, current.roundToInt()))
    }

    private fun message(value: CharSequence) = TextView(context).apply {
        text = value
        textSize = 16f
        setTextColor(0xFF616161.toInt())
        includeFontPadding = false
        setLineSpacing(4f * resources.displayMetrics.density, 1f)
        AppTypography.applyAppTypeface(this)
    }
}
