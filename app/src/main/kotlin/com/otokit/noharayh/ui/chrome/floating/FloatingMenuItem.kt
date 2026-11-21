package com.otokit.noharayh.ui.chrome.floating

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp

/** 浮动菜单的一行，尺寸、选择色和触摸反馈由本版独立管理。 */
internal class FloatingMenuItem(context: Context, label: Int, subtitle: Int, image: Int,
    private val accent: Int, action: () -> Unit) : PressSurface(context) {
    private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val glyph = ImageView(context)
    private val english = line(subtitle, 11f)
    private val chinese = line(label, 20f)
    private var fraction = 0f
    private var animation: ValueAnimator? = null

    init {
        baseRadius = dp(context, 28).toFloat()
        outerHaloEnabled = true
        innerRippleEnabled = false
        contentDescription = context.getString(label)
        glyph.background = circle
        glyph.setImageResource(image)
        val inset = dp(context, 8)
        glyph.setPadding(inset, inset, inset, inset)
        addView(glyph, LayoutParams(dp(context, 38), dp(context, 38), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply {
            leftMargin = inset
        })
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            AppTypography.applyTitleBarRogSans(english)
            AppTypography.applyAppTypeface(chinese, bold = true)
            addView(english)
            addView(chinese, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 2) })
        }
        addView(text, LayoutParams(-1, -1).apply { leftMargin = dp(context, 56); rightMargin = dp(context, 12) })
        paintSelection(0f)
        setOnClickListener { action() }
    }

    private fun line(res: Int, size: Float) = TextView(context).apply {
        setText(res)
        textSize = size
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    override fun setSelected(selected: Boolean) {
        if (selected == isSelected) return
        super.setSelected(selected)
        animation?.cancel()
        val target = if (selected) 1f else 0f
        if (!isAttachedToWindow || !ValueAnimator.areAnimatorsEnabled()) paintSelection(target)
        else animation = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 180
            addUpdateListener { paintSelection(it.animatedValue as Float) }
            start()
        }
    }

    private fun paintSelection(p: Float) {
        fraction = p
        val ink = ColorUtils.blendARGB(accent, Color.WHITE, p)
        fillColor = ColorUtils.blendARGB(Color.WHITE, accent, p)
        circle.setColor(ink)
        glyph.imageTintList = ColorStateList.valueOf(fillColor)
        english.setTextColor(ink)
        chinese.setTextColor(ink)
    }

    override fun onDetachedFromWindow() {
        animation?.cancel()
        paintSelection(if (isSelected) 1f else 0f)
        super.onDetachedFromWindow()
    }
}
