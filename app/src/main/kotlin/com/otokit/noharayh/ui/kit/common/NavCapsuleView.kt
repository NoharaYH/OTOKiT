package com.otokit.noharayh.ui.kit.common

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.constants.dimensions.ShellProportions
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 抽屉导航胶囊：普通 View 组合，不依赖 Material。
 *
 * - Pill 模式：白色药丸 + 强调色图标圆 + 中英双行文字；
 * - Circle 模式：白色圆形 + 单色图标，用于设置入口。
 *
 * 只接文案、图标、颜色、选中态与点击回调；不导入 Destination、shell 或导航控制器。
 * 胶囊自绘白色药丸；按下时四边内收，外环与触觉由 chrome 侧控制器负责。无阴影、无 elevation。
 */
class NavCapsuleView(context: Context) : FrameLayout(context) {

    private val iconCircle = FrameLayout(context)
    private val iconView = AppCompatImageView(context)
    private val column = LinearLayout(context)
    private val subLabel = TextView(context)
    private val label = TextView(context)

    private var accentColor: Int = Color.GRAY
    private var navSelected: Boolean = false
    private var circleMode: Boolean = false
    private var selectedFraction: Float = 0f
    private var selectionAnimator: ValueAnimator? = null
    private val iconCircleDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pillRect = RectF()
    private var pressInsetPx = 0f
    private var pressProgress = 0f

    /** 按压开始/结束；外环与触觉由 chrome 侧的反馈控制器负责。 */
    var onPressChanged: ((pressed: Boolean, clicked: Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        background = null
        setWillNotDraw(false)

        val circleSize = size(ShellDimensions.navCapsuleIconCircle)
        iconCircle.layoutParams = LayoutParams(circleSize, circleSize, Gravity.START or Gravity.CENTER_VERTICAL)
        val iconSize = size(ShellDimensions.navCapsuleIconInner)
        iconView.layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
        iconCircle.background = iconCircleDrawable
        iconCircle.addView(iconView)
        addView(iconCircle)

        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_VERTICAL
        subLabel.apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(ShellDimensions.navCapsuleSubLabelTextSize))
            AppTypography.applyTitleBarRogSans(this)
        }
        label.apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(ShellDimensions.navCapsuleLabelZhTextSize))
            AppTypography.applyAppTypeface(this, bold = true)
        }
        column.addView(
            subLabel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        column.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = size(ShellDimensions.chromeDualLineGap) },
        )
        addView(
            column,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL,
            ).apply {
                // 父级是 FrameLayout：需自行跳过图标（38.4dp）与图标到文字的间距（9.6dp）。
                leftMargin = (resources.getDimension(ShellDimensions.navCapsuleIconCircle) +
                    resources.getDimension(ShellDimensions.navCapsuleTextGap)).roundToInt()
            },
        )

        setPadding(
            size(ShellDimensions.navCapsulePaddingStart), size(ShellDimensions.navCapsulePaddingVertical),
            size(ShellDimensions.navCapsulePaddingEnd), size(ShellDimensions.navCapsulePaddingVertical),
        )
    }

    /** Pill 模式数据绑定；文案与图标由调用方解析，组件不感知路由。 */
    fun bind(
        label: CharSequence,
        subLabel: CharSequence,
        iconRes: Int,
        accentColor: Int,
        selected: Boolean,
    ) {
        circleMode = false
        navSelected = selected
        super.setSelected(selected)
        this.accentColor = accentColor
        column.visibility = View.VISIBLE
        this.subLabel.text = subLabel
        this.label.text = label
        iconView.setImageResource(iconRes)
        selectedFraction = if (selected) 1f else 0f
        applySelectionColors()
    }

    /** Circle 模式：白色圆形 + 单色图标，用于设置入口。 */
    fun bindCircle(iconRes: Int, iconColor: Int) {
        circleMode = true
        column.visibility = View.GONE
        selectedFraction = 0f
        pillPaint.color = Color.WHITE
        iconCircleDrawable.setColor(Color.WHITE)
        iconView.setImageResource(iconRes)
        iconView.imageTintList = ColorStateList.valueOf(iconColor)
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        if (circleMode || navSelected == selected) return
        navSelected = selected
        val target = if (selected) 1f else 0f
        selectionAnimator?.cancel()
        selectionAnimator = ValueAnimator.ofFloat(selectedFraction, target).apply {
            duration = SELECTION_ANIM_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                selectedFraction = it.animatedValue as Float
                applySelectionColors()
            }
            start()
        }
    }

    /** 面板变窄时限制胶囊宽度，保证非负可布局。 */
    fun setMaxWidthPx(maxWidthPx: Int) {
        if (maxWidthPx <= 0) return
        val params = layoutParams ?: return
        if (params.width != maxWidthPx) {
            params.width = maxWidthPx
            layoutParams = params
        }
    }

    /** 由 chrome 传入内收像素（当前 3dp）。 */
    fun setPressInsetPx(px: Float) {
        pressInsetPx = px
    }

    /** 按压进度 0..1，驱动药丸四边内收。 */
    fun setPressProgress(progress: Float) {
        val bounded = progress.coerceIn(0f, 1f)
        if (bounded == pressProgress) return
        pressProgress = bounded
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = pressInsetPx * pressProgress
        val radius: Float
        if (circleMode) {
            val diameter = min(width, height).toFloat()
            pillRect.set(
                (width - diameter) / 2f + inset,
                (height - diameter) / 2f + inset,
                (width + diameter) / 2f - inset,
                (height + diameter) / 2f - inset,
            )
            radius = (diameter / 2f - inset).coerceAtLeast(0f)
        } else {
            pillRect.set(inset, inset, width - inset, height - inset)
            radius = ((height - 2f * inset) / 2f).coerceAtLeast(0f)
        }
        if (radius > 0f) canvas.drawRoundRect(pillRect, radius, radius, pillPaint)
        super.onDraw(canvas)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                onPressChanged?.invoke(true, false)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isPressed && (event.x !in 0f..width.toFloat() || event.y !in 0f..height.toFloat())) {
                    isPressed = false
                    onPressChanged?.invoke(false, false)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val clicked = isPressed && event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                isPressed = false
                onPressChanged?.invoke(false, clicked)
                if (clicked) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                onPressChanged?.invoke(false, false)
                return true
            }
        }
        return true
    }

    /**
     * 未选中：白药丸 + 原始强调色（文字与图标同色，不加深、不透明削浅）；
     * 选中：药丸用原始强调色，文字与图标圆变白、图标回到强调色，按进度插值过渡。
     */
    private fun applySelectionColors() {
        val content = accentColor
        val fraction = selectedFraction.coerceIn(0f, 1f)
        pillPaint.color = ColorUtils.blendARGB(Color.WHITE, content, fraction)
        iconCircleDrawable.setColor(ColorUtils.blendARGB(content, Color.WHITE, fraction))
        iconView.imageTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(Color.WHITE, content, fraction),
        )
        val textColor = ColorUtils.blendARGB(content, Color.WHITE, fraction)
        subLabel.setTextColor(textColor)
        label.setTextColor(textColor)
        invalidate()
    }

    private fun size(id: Int): Int = resources.getDimensionPixelSize(id)

    override fun onDetachedFromWindow() {
        selectionAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 选中色互换的过渡时长。 */
        private const val SELECTION_ANIM_MS = 180L

        /** 固定胶囊宽度：`(308dp - 2×18dp) × 0.69`。 */
        fun standardWidthPx(resources: Resources): Int =
            ((resources.getDimension(ShellDimensions.shellSideNavWidth) -
                2 * resources.getDimension(ShellDimensions.shellComponentSpacing)) *
                ShellProportions.NAV_CAPSULE_WIDTH).roundToInt()
    }
}
