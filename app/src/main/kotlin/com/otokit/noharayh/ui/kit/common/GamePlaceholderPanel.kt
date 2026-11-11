package com.otokit.noharayh.ui.kit.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.otokit.noharayh.R
import kotlin.math.roundToInt

/**
 * 单个音游的测试占位：白色半透明大圆角矩形 + 居中的 PressSurface。
 *
 * 只负责自身绘制和按钮尺寸；轮换、缩放、透明度由页面/通用执行器写外层属性，
 * 本组件不参与双页过渡，也不读取导航或背景状态。
 */
@SuppressLint("ViewConstructor") // 只允许页面按描述与尺寸构造，不提供 XML 使用路径。
class GamePlaceholderPanel(
    context: Context,
    description: CharSequence,
    buttonDescription: CharSequence,
    private val onButtonClick: () -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(PANEL_ALPHA, 255, 255, 255)
    }
    private val panelRect = RectF()
    private var windowWidthPx = 0

    val button: PressSurface = PressSurface(context).apply {
        outerHaloEnabled = true
        contentDescription = buttonDescription
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { onButtonClick() }
    }

    init {
        setWillNotDraw(false)
        // 预览按钮的外环绘制在按钮自身边界之外；父容器默认 clipChildren=true 会把它
        // 截回按钮原来的方形区域，看起来像外圈被限位。
        clipChildren = false
        clipToPadding = false
        contentDescription = description
        isClickable = false
        isFocusable = false
        addView(button)
    }

    /** 使用 Application 当前窗口宽度算按钮目标宽度，不用物理屏幕或固定最小宽度。 */
    fun setWindowWidth(widthPx: Int) {
        if (windowWidthPx == widthPx) return
        windowWidthPx = widthPx.coerceAtLeast(0)
        requestLayout()
    }

    /** 只有当前功能根的音游占位可点击；拖动中的另一根不能成为第二份交互入口。 */
    fun setInteractive(interactive: Boolean) {
        button.isEnabled = interactive
        button.isClickable = interactive
        button.isFocusable = interactive
        button.importantForAccessibility = if (interactive) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        importantForAccessibility = if (interactive) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val rect = panelRectFor(width, height)
        val buttonWidth = PlaceholderLayout.buttonWidthPx(
            windowWidthPx = windowWidthPx,
            panelWidthPx = rect.width().toInt(),
            density = density,
        )
        val buttonHeight = PlaceholderLayout.buttonHeightPx(density)
        button.measure(
            MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val rect = panelRectFor(right - left, bottom - top)
        val buttonWidth = button.measuredWidth
        val buttonHeight = button.measuredHeight
        val buttonLeft = (rect.centerX() - buttonWidth / 2f).roundToInt()
        val buttonTop = (rect.centerY() - buttonHeight / 2f).roundToInt()
        button.layout(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = panelRectFor(width, height)
        canvas.drawRoundRect(rect, 24f * density, 24f * density, fillPaint)
    }

    private fun panelRectFor(width: Int, height: Int): RectF {
        val panelWidth = width * PlaceholderLayout.PANEL_WIDTH_FRACTION
        val panelHeight = height * PlaceholderLayout.PANEL_HEIGHT_FRACTION
        val left = (width - panelWidth) / 2f
        val top = (height - panelHeight) / 2f
        panelRect.set(left, top, left + panelWidth, top + panelHeight)
        return panelRect
    }

    private companion object {
        const val PANEL_ALPHA = 204 // 白色 80% 不透明度
    }
}
