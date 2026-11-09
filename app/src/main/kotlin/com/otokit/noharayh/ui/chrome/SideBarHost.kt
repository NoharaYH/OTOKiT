package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.window.PixelRect
import kotlin.math.roundToInt

/**
 * 抽屉共同父容器的运行部分：内容场景之上叠遮罩与面板。
 *
 * 只接收应用交互矩形、选中 destination 与点击意图；开合是临时呈现，不写偏好或 Bundle。
 * 本段先提供点击开合，拖动与 22% 起手带在后续段落接入。
 */
class SideBarHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val scrim = SideBarScrimView(context)
    private val sideBar = SideBar(context)

    private var interactionLeftPx = 0
    private var interactionTopPx = 0
    private var interactionRightPx = 0
    private var interactionBottomPx = 0
    private var drawerWidthPx = 0

    private var progress = 0f

    var onNavigate: ((Destination) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    /** 开合变化通知，宿主据此同步返回消费与输入阻断。 */
    var onOpenChanged: ((Boolean) -> Unit)? = null

    init {
        addView(
            scrim,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(
            sideBar,
            LayoutParams(dp(308f), LayoutParams.MATCH_PARENT),
        )
        scrim.visibility = GONE
        sideBar.visibility = GONE
        scrim.setOnClickListener { close() }
        sideBar.onNavigate = { destination -> onNavigate?.invoke(destination) }
        sideBar.onSettings = { onSettings?.invoke() }
    }

    val isOpen: Boolean
        get() = progress >= 0.999f

    val isVisible: Boolean
        get() = progress > 0f

    /**
     * XML 的内容场景在构造后才加入；这里把它压回底层，保证遮罩与面板始终在内容之上。
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        bringChildToFront(scrim)
        bringChildToFront(sideBar)
    }

    /** 应用交互矩形为根局部坐标；抽屉宽度取 308dp 与 92% 交互宽度的较小值。 */
    fun setMetrics(interaction: PixelRect, widthPx: Int) {
        interactionLeftPx = interaction.left
        interactionTopPx = interaction.top
        interactionRightPx = interaction.right
        interactionBottomPx = interaction.bottom
        drawerWidthPx = widthPx
            .coerceAtMost(interaction.width)
            .coerceAtLeast(0)
        sideBar.setPanelWidth(drawerWidthPx)
        applyLayout()
    }

    fun setSelected(destination: Destination) {
        sideBar.setSelected(destination)
    }

    fun open() {
        if (drawerWidthPx <= 0) return
        setProgress(1f)
    }

    fun close() {
        setProgress(0f)
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun release() {
        sideBar.dispose()
        onNavigate = null
        onSettings = null
        onOpenChanged = null
    }

    private fun setProgress(value: Float) {
        val bounded = value.coerceIn(0f, 1f)
        if (bounded == progress) return
        progress = bounded
        scrim.setProgress(progress)
        applyLayout()
        updateVisibility()
        onOpenChanged?.invoke(progress > 0f)
    }

    private fun applyLayout() {
        val width = (interactionRightPx - interactionLeftPx).coerceAtLeast(0)
        val height = (interactionBottomPx - interactionTopPx).coerceAtLeast(0)
        if (width <= 0 || height <= 0) return

        scrim.layoutParams = LayoutParams(width, height).apply {
            leftMargin = interactionLeftPx
            topMargin = interactionTopPx
        }

        val visibleWidth = (drawerWidthPx * progress).roundToInt()
        sideBar.layoutParams = LayoutParams(drawerWidthPx, height).apply {
            leftMargin = interactionLeftPx - drawerWidthPx + visibleWidth
            topMargin = interactionTopPx
        }
    }

    private fun updateVisibility() {
        val visible = progress > 0f
        scrim.visibility = if (visible) VISIBLE else GONE
        sideBar.visibility = if (visible) VISIBLE else GONE
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()
}
