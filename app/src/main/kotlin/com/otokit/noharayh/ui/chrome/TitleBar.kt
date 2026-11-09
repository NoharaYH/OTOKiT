package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography

/**
 * 标题栏：圆形菜单按钮 + 中英双行标题 + 右侧装饰三角。
 *
 * 只消费 [TitleBarState] 展示数据和点击意图；不读导航/选择状态、不查 Registry、不写偏好。
 * 只在 Compact 形态挂载，应用行高由自身实测后回报给宿主。
 */
class TitleBar(context: Context) : FrameLayout(context) {

    private val metrics = TitleBarMetricsCalculator.calculate(resources.displayMetrics.density)

    private val decor = TitleBarDecorTrianglesView(context)
    private val menuHost = FrameLayout(context)
    private val menuButton = ImageButton(context)
    private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val column = LinearLayout(context)
    private val english = AppCompatTextView(context)
    private val chinese = AppCompatTextView(context)

    private var lastReportedHeight = -1

    private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val height = height
        if (height > 0 && height != lastReportedHeight) {
            lastReportedHeight = height
            onHeightChanged?.invoke(height)
        }
    }

    /** 主按钮点击意图；功能态菜单在 07 之前保持禁用，设置态为返回。 */
    var onPrimaryAction: (() -> Unit)? = null

    /** 实际应用标题行高度（不含系统状态区）。 */
    var onHeightChanged: ((Int) -> Unit)? = null

    val contentHeightPx: Int
        get() = metrics.rowHeightPx

    init {
        clipChildren = false
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            metrics.rowHeightPx,
        )

        decor.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        addView(decor)

        menuHost.layoutParams = LayoutParams(
            metrics.buttonSizePx,
            metrics.buttonSizePx,
            Gravity.START or Gravity.CENTER_VERTICAL,
        )
        menuButton.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        menuButton.background = circle
        menuButton.scaleType = ImageView.ScaleType.CENTER_INSIDE
        menuButton.setPadding(
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
        )
        menuButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
        menuButton.setOnClickListener { onPrimaryAction?.invoke() }
        menuHost.addView(menuButton)
        addView(menuHost)

        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        column.translationY = metrics.opticalNudgePx

        english.apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.englishTextSizePx)
            AppTypography.applyTitleBarRogSans(this)
        }
        chinese.apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.chineseTextSizePx)
            AppTypography.applyAppTypeface(this, bold = true)
        }

        column.addView(
            english,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        column.addView(
            chinese,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = metrics.lineGapPx },
        )

        addView(
            column,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = metrics.titleAnchorStartPx
                marginEnd = metrics.buttonSizePx
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
        )

        addOnLayoutChangeListener(layoutListener)
    }

    /** 同值幂等：更新文案、颜色、图标与许可，不重建子节点。 */
    fun render(state: TitleBarState) {
        english.setText(state.text.english)
        chinese.setText(state.text.chinese)
        english.setTextColor(state.textColor)
        chinese.setTextColor(state.textColor)
        circle.setColor(state.circleColor)
        decor.setColors(state.maiDecorColor, state.chuDecorColor)

        val isBack = state.primaryAction == TitleBarPrimaryAction.Back
        menuButton.setImageResource(
            if (isBack) R.drawable.ic_arrow_back_24 else R.drawable.ic_menu_24,
        )
        menuButton.contentDescription = context.getString(
            if (isBack) R.string.title_back else R.string.title_open_navigation_menu,
        )
        menuButton.isEnabled = state.primaryEnabled
        menuButton.alpha = if (state.primaryEnabled) 1f else DISABLED_ALPHA
    }

    /** 终态：解除回调与布局监听，之后不再上报高度或派发点击。 */
    fun dispose() {
        onPrimaryAction = null
        onHeightChanged = null
        removeOnLayoutChangeListener(layoutListener)
        menuButton.setOnClickListener(null)
    }

    private companion object {
        const val DISABLED_ALPHA = 0.55f
    }
}
