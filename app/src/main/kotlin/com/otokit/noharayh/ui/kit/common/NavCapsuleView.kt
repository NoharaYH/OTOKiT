package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.constants.dimensions.ShellProportions
import kotlin.math.roundToInt

/**
 * 抽屉导航胶囊：普通 View 组合，不依赖 Material。
 *
 * - Pill 模式：白色药丸 + 强调色图标圆 + 中英双行文字；
 * - Circle 模式：白色圆形 + 单色图标，用于设置入口。
 *
 * 只接文案、图标、颜色、选中态与点击回调；不导入 Destination、shell 或导航控制器。
 * 点击涟漪被同形状 RippleDrawable 约束在轮廓内；无阴影、无 elevation。
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

    init {
        isClickable = true
        isFocusable = true

        val circleSize = size(ShellDimensions.navCapsuleIconCircle)
        iconCircle.layoutParams = LayoutParams(circleSize, circleSize, Gravity.START or Gravity.CENTER_VERTICAL)
        val iconSize = size(ShellDimensions.navCapsuleIconInner)
        iconView.layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
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
        iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
        applyPillBackground()
        applyIconCircle(accentColor)
        applyLabelColors()
    }

    /** Circle 模式：白色圆形 + 单色图标，用于设置入口。 */
    fun bindCircle(iconRes: Int, iconColor: Int) {
        circleMode = true
        column.visibility = View.GONE
        iconView.setImageResource(iconRes)
        iconView.imageTintList = ColorStateList.valueOf(iconColor)
        applyPillBackground()
        applyIconCircle(Color.WHITE)
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        if (navSelected == selected) return
        navSelected = selected
        applyLabelColors()
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

    private fun applyPillBackground() {
        val shape = GradientDrawable().apply {
            if (circleMode) {
                this.shape = GradientDrawable.OVAL
            } else {
                cornerRadius = resources.getDimension(ShellDimensions.navCapsuleCornerRadius)
            }
            setColor(Color.WHITE)
        }
        val mask = shape.constantState?.newDrawable()?.mutate()
        background = RippleDrawable(ColorStateList.valueOf(0x1F000000), shape, mask)
    }

    private fun applyIconCircle(color: Int) {
        iconCircle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun applyLabelColors() {
        val alpha = if (navSelected) 255 else (255 * 0.65f).roundToInt()
        val color = Color.argb(alpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        subLabel.setTextColor(color)
        label.setTextColor(color)
    }

    private fun size(id: Int): Int = resources.getDimensionPixelSize(id)

    companion object {
        /** 固定胶囊宽度：`(308dp - 2×18dp) × 0.69`。 */
        fun standardWidthPx(resources: Resources): Int =
            ((resources.getDimension(ShellDimensions.shellSideNavWidth) -
                2 * resources.getDimension(ShellDimensions.shellComponentSpacing)) *
                ShellProportions.NAV_CAPSULE_WIDTH).roundToInt()
    }
}
