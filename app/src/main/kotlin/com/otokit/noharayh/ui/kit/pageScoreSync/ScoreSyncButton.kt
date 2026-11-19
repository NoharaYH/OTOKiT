package com.otokit.noharayh.ui.kit.pageScoreSync

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.motion.ContainerMotion
import com.otokit.noharayh.ui.constants.dimensions.SettingsButtonDimensions
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.configureSingleChoiceSemantics

internal class ScoreSyncButton(context: Context, label: String, private val choice: Boolean, onClick: () -> Unit) : PressSurface(context) {
    private val caption = ViewPrimitives.text(context, label, SettingsButtonDimensions.TEXT_SP, Color.BLACK, true)
    private data class Colors(val fill: Int, val text: Int)
    private var target: Colors? = null
    private val transition = ContentTransition(Colors(fillColor, caption.currentTextColor), ContainerMotion.CONTENT,
        interpolate = { from, to, progress ->
            Colors(ColorUtils.blendARGB(from.fill, to.fill, progress), ColorUtils.blendARGB(from.text, to.text, progress))
        },
        onFrame = { colors ->
            fillColor = colors.fill
            caption.setTextColor(colors.text)
        },
    )
    init {
        outerHaloEnabled = true
        baseRadius = ViewPrimitives.dp(context, 12).toFloat()
        contentDescription = label
        caption.gravity = Gravity.CENTER
        caption.maxLines = 1
        caption.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(caption, LayoutParams(-1, -1))
        if (choice) configureSingleChoiceSemantics(false)
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun render(accent: Int, lit: Boolean, available: Boolean, interactive: Boolean) {
        isSelected = choice && lit
        isEnabled = available && interactive
        val color = when {
            !available -> 0xff929297.toInt()
            lit -> ColorUtils.setAlphaComponent(accent, 255)
            else -> ColorUtils.blendARGB(Color.WHITE, accent, .12f)
        }
        val ink = if (available) ColorUtils.setAlphaComponent(accent, 255) else color
        val colors = Colors(color, labelColor(color, ink))
        if (colors == target) return
        val animate = target != null && isShown && isLaidOut
        target = colors
        transition.play(colors, animate)
    }

    private fun labelColor(background: Int, ink: Int): Int {
        if (ColorUtils.calculateContrast(Color.WHITE, background) >= 4.5) return Color.WHITE
        if (ColorUtils.calculateContrast(ink, background) >= 4.5) return ink
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(ink, hsl)
        var darker: Int
        do {
            hsl[2] = (hsl[2] - .04f).coerceAtLeast(0f)
            darker = ColorUtils.HSLToColor(hsl)
        } while (ColorUtils.calculateContrast(darker, background) < 4.5)
        return darker
    }

    override fun onDetachedFromWindow() {
        transition.finish()
        super.onDetachedFromWindow()
    }
}
