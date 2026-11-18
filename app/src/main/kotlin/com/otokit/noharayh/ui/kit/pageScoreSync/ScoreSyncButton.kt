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
        val colors = Colors(color, when {
            !available -> 0xffe0e0e4.toInt()
            choice && !lit -> accent
            ColorUtils.calculateContrast(Color.WHITE, color) > ColorUtils.calculateContrast(Color.BLACK, color) -> Color.WHITE
            else -> 0xff17171a.toInt()
        })
        if (colors == target) return
        val animate = target != null && isShown && isLaidOut
        target = colors
        transition.play(colors, animate)
    }

    override fun onDetachedFromWindow() {
        transition.finish()
        super.onDetachedFromWindow()
    }
}
