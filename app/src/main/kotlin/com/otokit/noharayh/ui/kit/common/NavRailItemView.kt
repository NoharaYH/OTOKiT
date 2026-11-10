package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions

/**
 * 宽屏 Rail 的单个条目：图标圆 + 中文标签的普通 View 组合。
 *
 * 只接收文字、图标、颜色、selected 与点击回调，不感知 Destination 或导航状态。
 * 按用户规则不加阴影；按压只做有界缩放，不引第三套外环。
 */
class NavRailItemView(context: Context) : FrameLayout(context) {

    private val iconCircle = FrameLayout(context)
    private val iconView = AppCompatImageView(context)
    private val label = TextView(context)
    private val circleDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private var accentColor: Int = Color.GRAY
    private var navSelected: Boolean = false

    init {
        isClickable = true
        isFocusable = true
        background = null

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        iconCircle.layoutParams = LinearLayout.LayoutParams(
            size(ShellDimensions.padNavRailIconCircle),
            size(ShellDimensions.padNavRailIconCircle),
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        iconCircle.background = circleDrawable
        iconView.layoutParams = LayoutParams(
            size(ShellDimensions.padNavRailIconInner),
            size(ShellDimensions.padNavRailIconInner),
            Gravity.CENTER,
        )
        iconCircle.addView(iconView)
        column.addView(iconCircle)

        label.apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(ShellDimensions.padNavRailLabelTextSize),
            )
            AppTypography.applyAppTypeface(this, bold = true)
            gravity = Gravity.CENTER
        }
        column.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = (4 * resources.displayMetrics.density).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        addView(
            column,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        setPadding(0, dp(6), 0, dp(6))
    }

    /** 功能项：图标圆 + 中文标签。 */
    fun bind(labelText: CharSequence, iconRes: Int, accentColor: Int, selected: Boolean) {
        this.accentColor = accentColor
        navSelected = selected
        super.setSelected(selected)
        label.visibility = View.VISIBLE
        label.text = labelText
        iconView.setImageResource(iconRes)
        iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        circleDrawable.setColor(accentColor)
        applyLabelColor()
        contentDescription = labelText
    }

    /** 设置入口：灰色图标圆，无文字。 */
    fun bindSettings(iconRes: Int, circleColor: Int, description: CharSequence) {
        this.accentColor = circleColor
        navSelected = false
        super.setSelected(false)
        label.visibility = View.GONE
        iconView.setImageResource(iconRes)
        iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        circleDrawable.setColor(circleColor)
        contentDescription = description
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        if (navSelected == selected) return
        navSelected = selected
        applyLabelColor()
    }

    private fun applyLabelColor() {
        val alpha = if (navSelected) 255 else (255 * 0.65f).toInt()
        label.setTextColor(
            Color.argb(
                alpha,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor),
            ),
        )
    }

    private fun size(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
                return true
            }

            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                return true
            }
        }
        return true
    }
}
