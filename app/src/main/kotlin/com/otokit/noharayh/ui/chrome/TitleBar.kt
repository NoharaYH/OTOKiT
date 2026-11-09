package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.kit.common.AppTypography

/**
 * 标题栏：圆形菜单按钮 + 中英双行标题 + 右侧装饰三角。
 *
 * 只消费 [TitleBarState] 展示数据和点击意图；不读导航/选择状态、不查 Registry、不写偏好。
 * 只在 Compact 形态挂载，应用行高由自身实测后回报给宿主。
 */
class TitleBar(context: Context) : FrameLayout(context) {

    private val metrics = TitleBarMetricsCalculator.calculate(
        TitleBarDimensionInput(
            spacingPx = resources.getDimension(ShellDimensions.shellComponentSpacing),
            padPx = resources.getDimension(ShellDimensions.titleBarPadding),
            iconPx = resources.getDimension(ShellDimensions.titleBarIconSize),
            lineGapPx = resources.getDimension(ShellDimensions.chromeDualLineGap),
            opticalNudgePx = resources.getDimension(ShellDimensions.chromeDualLineTextOpticalNudgeY),
        ),
    )

    private val decor = TitleBarDecorTrianglesView(context)
    private val menuHost = FrameLayout(context)
    private val menuButton = TitleBarPrimaryButton(context)
    private val halo = TitleButtonHalo(context, menuButton.feedback)
    private val column = LinearLayout(context)
    private val english = AppCompatTextView(context)
    private val chinese = AppCompatTextView(context)

    private var lastReportedHeight = -1
    private var lastPrimaryAction: TitleBarPrimaryAction? = null

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
        clipChildren = true
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            metrics.rowHeightPx,
        )

        decor.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        addView(decor)
        addView(halo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        menuButton.onFeedbackFrame = { halo.invalidate() }

        // 旧行容器有 12dp padding；标题自身没有容器，所以在这里补上左边的 pad，
        // 圆右缘到文字的距离正好也是 pad，与 titleAnchorStart 公式一致。
        @SuppressLint("RtlHardcoded") // 标题按物理左缘定位，与窗口矩形同一坐标口径。
        val menuGravity = Gravity.TOP or Gravity.LEFT
        menuHost.layoutParams = LayoutParams(
            metrics.buttonSizePx,
            metrics.buttonSizePx,
            menuGravity,
        ).apply {
            leftMargin = metrics.padPx
            topMargin = metrics.padPx
        }
        menuButton.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
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
                leftMargin = metrics.titleAnchorStartPx
                rightMargin = metrics.buttonSizePx
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
        )

        addOnLayoutChangeListener(layoutListener)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        halo.setButtonBounds(menuHost.left, menuHost.top, menuHost.right, menuHost.bottom)
    }

    /** 同值幂等：更新文案、颜色、图标与许可，不重建子节点。 */
    fun render(state: TitleBarState) {
        if (!state.primaryEnabled || (menuButton.isPressed &&
                lastPrimaryAction != null && lastPrimaryAction != state.primaryAction)) {
            menuButton.cancelFeedback()
        }
        lastPrimaryAction = state.primaryAction
        english.setText(state.text.english)
        chinese.setText(state.text.chinese)
        english.setTextColor(state.textColor)
        chinese.setTextColor(state.textColor)
        menuButton.circleColor = state.circleColor
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

    fun cancelPressFeedback() = menuButton.cancelFeedback()

    /** 供宿主在旧焦点已失效时回到主操作入口，不暴露内部按钮。 */
    fun requestPrimaryFocus(): Boolean = menuButton.requestFocus()

    /** 终态：解除回调与布局监听，之后不再上报高度或派发点击。 */
    fun dispose() {
        menuButton.cancelFeedback()
        menuButton.onFeedbackFrame = null
        onPrimaryAction = null
        onHeightChanged = null
        removeOnLayoutChangeListener(layoutListener)
        menuButton.setOnClickListener(null)
    }

    private companion object {
        const val DISABLED_ALPHA = 0.55f
    }
}
