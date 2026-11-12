package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.theme.ThemeOption

/** 胶囊触发框；描边和反馈层共享按压几何，不叠加固定尺寸外框。 */
@android.annotation.SuppressLint("ViewConstructor") // 仅由代码提供标签并创建。
internal class LabeledDropdownField(context: Context, label: String) : PressSurface(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF757575.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val outline = RectF()
    private val outlinePath = Path()
    private val arc = RectF()
    override val surfaceFillInset: Float
        get() = stroke.strokeWidth / 2f
    private val valueView = TextView(context).apply {
        textSize = 14f
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(0xFF323232.toInt())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        AppTypography.applyAppTypeface(this)
    }
    private val arrow = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_right_24)
        setColorFilter(0xFF757575.toInt())
        rotation = 90f
    }
    private val labelView = TextView(context).apply {
        text = label
        textSize = 11f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(0xFF757575.toInt())
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        AppTypography.applyAppTypeface(this)
    }
    private var menu: ThemeDropdownMenu? = null
    private var pressedAt = 0L

    init {
        fillColor = Color.WHITE
        outerHaloEnabled = true
        releaseHapticEnabled = false // 菜单展开统一给一次轻触觉。
        baseRadius = dp(22).toFloat()
        clipChildren = false
        clipToPadding = false
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26), 0, dp(12), 0)
            addView(valueView, LinearLayout.LayoutParams(0, -1, 1f))
            addView(arrow, LinearLayout.LayoutParams(dp(20), dp(20)))
        }
        addView(row, LayoutParams(-1, dp(44)))
        addView(labelView, LayoutParams(-2, -2).apply {
            leftMargin = dp(26)
            rightMargin = dp(26)
        })
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        labelView.translationY = -labelView.height / 2f + stroke.strokeWidth / 2f
    }

    override fun onPressStateChanged(pressed: Boolean) {
        super.onPressStateChanged(pressed)
        if (pressed) pressedAt = SystemClock.uptimeMillis()
    }

    override fun drawOutline(canvas: Canvas, cornerRadius: Float, inset: Float) {
        val half = stroke.strokeWidth / 2f
        outline.set(inset + half, inset + half, width - inset - half, height - inset - half)
        val radius = (cornerRadius - half).coerceAtLeast(0f)
        val gapStart = (labelView.left - dp(4)).toFloat().coerceIn(outline.left + radius, outline.right - radius)
        val gapEnd = (labelView.right + dp(4)).toFloat().coerceIn(gapStart, outline.right - radius)
        labelView.translationY = outline.top - labelView.height / 2f
        // 描边在标签两侧主动断开，圆形笔帽收尾；文字不再用白色矩形覆盖描边。
        outlinePath.rewind()
        outlinePath.moveTo(gapEnd, outline.top)
        outlinePath.lineTo(outline.right - radius, outline.top)
        arc.set(outline.right - radius * 2, outline.top, outline.right, outline.top + radius * 2)
        outlinePath.arcTo(arc, -90f, 90f)
        outlinePath.lineTo(outline.right, outline.bottom - radius)
        arc.set(outline.right - radius * 2, outline.bottom - radius * 2, outline.right, outline.bottom)
        outlinePath.arcTo(arc, 0f, 90f)
        outlinePath.lineTo(outline.left + radius, outline.bottom)
        arc.set(outline.left, outline.bottom - radius * 2, outline.left + radius * 2, outline.bottom)
        outlinePath.arcTo(arc, 90f, 90f)
        outlinePath.lineTo(outline.left, outline.top + radius)
        arc.set(outline.left, outline.top, outline.left + radius * 2, outline.top + radius * 2)
        outlinePath.arcTo(arc, 180f, 90f)
        outlinePath.lineTo(gapStart, outline.top)
        canvas.drawPath(outlinePath, stroke)
    }

    fun setValue(title: String) {
        valueView.text = title
        contentDescription = "${labelView.text}：$title"
    }

    fun setControlsEnabled(enabled: Boolean) {
        if (!enabled) dismissMenu()
        isEnabled = enabled
        stroke.color = if (enabled) 0xFF757575.toInt() else 0xFFCCCCCC.toInt()
        valueView.setTextColor(if (enabled) 0xFF323232.toInt() else 0xFFB3B3B3.toInt())
        labelView.setTextColor(stroke.color)
        arrow.imageAlpha = if (enabled) 255 else 97
        invalidate()
    }

    fun toggleMenu(options: List<ThemeOption>, onSelected: (ThemeOption) -> Unit) {
        if (!isEnabled) return
        if (menu != null) {
            menu?.dismiss()
            return
        }
        setExpanded(true)
        val openingHapticDelay = (120L - (SystemClock.uptimeMillis() - pressedAt)).coerceAtLeast(0L)
        menu = ThemeDropdownMenu.show(this, options, openingHapticDelay, { option ->
            onSelected(option)
        }) {
            menu = null
            setExpanded(false)
        }
    }

    fun dismissMenu() {
        menu?.dismiss(immediate = true)
        menu = null
    }

    override fun onDetachedFromWindow() {
        dismissMenu()
        arrow.animate().cancel()
        arrow.rotation = 90f
        super.onDetachedFromWindow()
    }

    private fun setExpanded(expanded: Boolean) {
        arrow.animate().rotation(if (expanded) 270f else 90f).setDuration(220L).start()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
