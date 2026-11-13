package com.otokit.noharayh.ui.kit.common.toggle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.ui.animation.common.SwitchTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.GroupedSwitchDimensions as Dimensions
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import kotlin.math.roundToInt

/** 分组开关行。只呈现确认值、发切换意图；整行共用 PressSurface 的触摸与反馈。 */
@android.annotation.SuppressLint("ViewConstructor")
open class GroupedSwitchRow(
    context: Context,
    label: CharSequence,
    groupStart: Boolean,
    groupEnd: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelIconRes: Int? = null,
    labelIconTint: Int = 0xFFE57373.toInt(),
    labelColor: Int = 0xFF616161.toInt(),
) : PressSurface(context), Checkable {
    private var checkedValue = checked
    private val indicator = SwitchIndicator(context, checked)

    init {
        fillColor = Color.WHITE
        outerHaloEnabled = true
        topCornerRadius = dp(if (groupStart) Dimensions.OUTER_RADIUS_DP.toFloat() else Dimensions.INNER_RADIUS_DP)
        bottomCornerRadius = dp(if (groupEnd) Dimensions.OUTER_RADIUS_DP.toFloat() else Dimensions.INNER_RADIUS_DP)
        contentDescription = label
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Dimensions.ROW_HEIGHT_DP.toFloat()).roundToInt()
            setPadding(dp(Dimensions.TEXT_INSET_DP.toFloat()).roundToInt(), 0,
                dp(Dimensions.TEXT_INSET_DP.toFloat()).roundToInt(), 0)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val labelGroup = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        labelGroup.addView(TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(labelColor)
            includeFontPadding = false
            AppTypography.applyAppTypeface(this, bold = true)
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        if (labelIconRes != null) labelGroup.addView(ImageView(context).apply {
            setImageResource(labelIconRes)
            setColorFilter(labelIconTint)
        }, LinearLayout.LayoutParams(dp(18f).roundToInt(), dp(18f).roundToInt()).apply {
            marginStart = dp(6f).roundToInt()
        })
        row.addView(labelGroup, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(Dimensions.TEXT_INSET_DP.toFloat()).roundToInt()
        })
        row.addView(indicator, LinearLayout.LayoutParams(
            dp(Dimensions.TRACK_WIDTH_DP).roundToInt(), dp(Dimensions.TRACK_HEIGHT_DP).roundToInt()))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setOnClickListener { onCheckedChange(!checkedValue) }
    }

    override fun isChecked(): Boolean = checkedValue
    override fun toggle() { performClick() }
    override fun setChecked(checked: Boolean) {
        if (checkedValue == checked) return
        checkedValue = checked
        indicator.render(checked)
        refreshDrawableState()
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checkedValue
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

private class SwitchIndicator(context: Context, checked: Boolean) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var target = if (checked) 1f else 0f
    private var progress = target
    private val transition = SwitchTransitionRunner { progress = it; invalidate() }

    fun render(checked: Boolean) {
        target = if (checked) 1f else 0f
        if (isAttachedToWindow) transition.play(progress, target)
        else { progress = target; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = Dimensions.TRACK_STROKE_DP * density
        rect.set(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.blendARGB(0xFFE0E0E0.toInt(), 0xFF2196F3.toInt(), progress)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = ColorUtils.blendARGB(0xFF797979.toInt(), 0xFF2196F3.toInt(), progress)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.blendARGB(0xFF797979.toInt(), Color.WHITE, progress)
        val start = height / 2f
        val end = width - start
        val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) end + (start - end) * progress
            else start + (end - start) * progress
        val radius = (Dimensions.THUMB_OFF_RADIUS_DP +
            (Dimensions.THUMB_ON_RADIUS_DP - Dimensions.THUMB_OFF_RADIUS_DP) * progress) * density
        canvas.drawCircle(x, height / 2f, radius, paint)
    }

    override fun onDetachedFromWindow() {
        transition.stop()
        progress = target
        super.onDetachedFromWindow()
    }
}
