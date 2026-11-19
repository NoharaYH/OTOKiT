package com.otokit.noharayh.ui.kit.pageScoreSync

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import androidx.core.graphics.withTranslation
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.PressHalo
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 涟漪以图标为中心，点击与系统触觉沿用完整的按钮热区。 */
internal class ScoreSyncCopyButton(context: Context, private val glyphSize: Int, onCopy: () -> Unit) : PressSurface(context) {
    private val iconHalo = PressHalo(this, color = Color.BLACK)

    init {
        contentDescription = context.getString(R.string.sync_copy_log)
        fillColor = Color.TRANSPARENT
        whiteMaxAlpha = 0f
        innerRippleEnabled = false
        pressInset = 0f
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_content_copy_24)
            setColorFilter(Color.WHITE)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(glyphSize, glyphSize, Gravity.CENTER))
        setOnClickListener { onCopy() }
    }

    override fun onPressStateChanged(pressed: Boolean) {
        super.onPressStateChanged(pressed)
        if (pressed) iconHalo.press() else iconHalo.release()
    }

    override fun drawUnderlay(canvas: Canvas, cornerRadius: Float, inset: Float) {
        canvas.withTranslation((width - glyphSize) / 2f, (height - glyphSize) / 2f) {
            iconHalo.draw(this, glyphSize, glyphSize, 0f, glyphSize / 2f)
        }
    }

    override fun onDetachedFromWindow() {
        iconHalo.reset()
        super.onDetachedFromWindow()
    }
}
