package com.otokit.noharayh.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PageContent

/**
 * 设置占位的展示入口：透明底 + 居中白色占位框。
 *
 * 不再用整屏白色底板盖住背景；输入隔离仍由 shell 的 overlay 负责。
 */
object SettingsPlaceholder {

    fun create(context: Context, textColor: Int): PageContent {
        val container = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.TRANSPARENT)
        }
        val frame = SettingsFrame(context)
        val text = placeholderText(context, R.string.placeholder_settings, textColor)
        frame.addView(
            text,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        container.addView(
            frame,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return PageContent(container, text::setTextColor)
    }

    private fun placeholderText(context: Context, textRes: Int, textColor: Int): TextView =
        TextView(context).apply {
            setText(textRes)
            gravity = Gravity.CENTER
            setTextAppearance(android.R.style.TextAppearance_Material_Large)
            AppTypography.applyAppTypeface(this)
            // 颜色放在 TextAppearance 与字体之后，避免被前者覆盖。
            setTextColor(textColor)
        }

    /** 与六占位一致的白色半透明大圆角框；只画框，不铺满整屏。 */
    private class SettingsFrame(context: Context) : FrameLayout(context) {

        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(PANEL_ALPHA, 255, 255, 255)
        }
        private val rect = RectF()

        init {
            setWillNotDraw(false)
            clipChildren = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width * PANEL_WIDTH_FRACTION
            val height = height * PANEL_HEIGHT_FRACTION
            val left = (this.width - width) / 2f
            val top = (this.height - height) / 2f
            rect.set(left, top, left + width, top + height)
            canvas.drawRoundRect(rect, 24f * density, 24f * density, paint)
        }

        private companion object {
            const val PANEL_ALPHA = 204
            const val PANEL_WIDTH_FRACTION = 0.90f
            const val PANEL_HEIGHT_FRACTION = 0.70f
        }
    }
}
