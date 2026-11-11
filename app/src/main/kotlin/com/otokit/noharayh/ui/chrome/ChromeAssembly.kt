package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.constants.dimensions.ShellProportions
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot
import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import com.otokit.noharayh.ui.window.relativeToRoot
import kotlin.math.roundToInt

/**
 * 外围 UI 的唯一运行入口。
 *
 * 根据窗口分类选择标题+抽屉或 Rail，管理挂载、定位、局部瞬态与公开能力；
 * 输出应用占用和系统栏意图，不计算内容几何、不提交导航状态。
 */
class ChromeAssembly(
    private val chromeHost: FrameLayout,
    private val sidebarHost: SideBarHost,
    private val onNavigate: (Destination) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onCloseSettings: () -> Unit,
    private val onInsetsChanged: (ChromeInsets) -> Unit,
    private val onInputBlockedChanged: () -> Unit,
    private val onStatusBarHiddenChanged: (Boolean) -> Unit,
) {

    private var titleBar: TitleBar? = null
    private var rail: NavigationBar? = null
    private var display: ChromeDisplayInput? = null
    private var snapshot: WindowSnapshot? = null
    private var attached = false
    private var disposed = false
    private var compact = false
    private var appChromeTopPx = 0
    private var lastInsets = ChromeInsets.Zero
    private var lastStatusBarHidden = false
    private var statusBarIntentInitialized = false

    private val railWidthPx: Int
        get() = chromeHost.resources.getDimensionPixelSize(ShellDimensions.padNavRailWidth)

    val insets: ChromeInsets
        get() = lastInsets

    val blocksUnderlyingInput: Boolean
        get() = attached && sidebarHost.blocksUnderlyingInput

    fun attach() {
        if (attached || disposed) return
        attached = true
        sidebarHost.onNavigate = { destination ->
            // 普通导航沿当前进度动画收起，保留胶囊释放反馈；强制复位仅用于窗口/生命周期失效。
            sidebarHost.close()
            onNavigate(destination)
        }
        sidebarHost.onSettings = {
            sidebarHost.close(immediate = true)
            onOpenSettings()
        }
        sidebarHost.onInputBlockedChanged = {
            if (attached) onInputBlockedChanged()
        }
    }

    /** 窗口分类只消费外部结果；形态变化时清理旧 chrome 的临时交互。 */
    fun updateWindow(window: WindowSnapshot) {
        if (!attached || disposed) return
        snapshot = window
        val compactNow = !ChromeLayoutPolicy.usesRail(window.layoutClass)
        compact = compactNow
        if (compactNow) {
            removeRail()
            mountTitleIfNeeded()
            applyTitleRect(window)
            requestStatusBarHidden(false)
            sidebarHost.setDragEnabled(display?.settingsOpen != true)
        } else {
            removeTitle()
            mountRailIfNeeded()
            sidebarHost.setDragEnabled(false)
            sidebarHost.close(immediate = true)
            requestStatusBarHidden(true)
        }
        publishInsets(
            ChromeLayoutPolicy.insetsFor(
                layoutClass = window.layoutClass,
                titleHeightPx = appChromeTopPx,
                railWidthPx = railWidthPx,
            ),
        )
    }

    /** 背景帧只更新标题跟手，不重建导航/设置的其他展示。 */
    fun renderGameFrame(frame: com.otokit.noharayh.ui.animation.common.GameFrame) {
        if (!attached || disposed) return
        val current = display ?: return
        display = current.copy(gameFrame = frame)
        titleBar?.renderGameFrame(frame)
    }

    /** 只渲染展示输入；导航与设置的稳定状态仍由 shell 唯一修改。 */
    fun render(input: ChromeDisplayInput) {
        if (!attached || disposed) return
        display = input
        sidebarHost.setSelected(input.destination)
        if (input.settingsOpen) sidebarHost.close(immediate = true)
        sidebarHost.setDragEnabled(compact && !input.settingsOpen)
        renderTitle()
        rail?.render(input)
    }

    /** shell 完成唯一内容几何后，把根局部交互矩形交给当前 chrome 布局。 */
    @SuppressLint("RtlHardcoded") // Rail 固定在物理左侧，不随 RTL 镜像。
    fun layout(interaction: PixelRect) {
        if (!attached || disposed) return
        if (compact) {
            if (interaction.isEmpty) {
                sidebarHost.close(immediate = true)
                return
            }
            val width = minOf(
                chromeHost.resources.getDimensionPixelSize(ShellDimensions.shellSideNavWidth),
                (interaction.width * ShellProportions.DRAWER_MAX_WINDOW_WIDTH).roundToInt(),
            )
            sidebarHost.setMetrics(interaction, width)
            return
        }

        val bar = rail ?: return
        if (interaction.isEmpty) {
            bar.visibility = android.view.View.INVISIBLE
            return
        }
        bar.visibility = android.view.View.VISIBLE
        val window = snapshot ?: return
        val localLeft = window.insets.systemSafeEdges.left - window.metrics.rootLeftPx
        val params = bar.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width == railWidthPx && params.height == interaction.height &&
            params.leftMargin == localLeft && params.topMargin == interaction.top
        ) {
            return
        }
        params.width = railWidthPx
        params.height = interaction.height
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.setMargins(localLeft, interaction.top, 0, 0)
        bar.layoutParams = params
    }

    /** 普通返回只消费抽屉；设置由 shell 的导航状态处理。 */
    fun consumeBack(): Boolean {
        if (!attached || disposed || !compact) return false
        if (!sidebarHost.blocksUnderlyingInput) return false
        sidebarHost.close()
        return true
    }

    fun closeDrawer(immediate: Boolean = true) {
        if (attached && !disposed) sidebarHost.close(immediate)
    }

    /** 设置标题的预测返回只驱动标题层，不提交设置状态。 */
    fun beginBackPreview() {
        if (compact) titleBar?.beginBackPreview()
    }

    fun updateBackPreview(progress: Float) {
        if (compact) titleBar?.updateBackProgress(progress)
    }

    fun cancelBackPreview() {
        titleBar?.cancelBackPreview()
    }

    fun endBackPreview() {
        titleBar?.endBackPreview()
    }

    fun beginDrawerBackPreview(): Boolean = sidebarHost.beginBackPreview()

    fun updateDrawerBackPreview(progress: Float) = sidebarHost.updateBackPreview(progress)

    fun cancelDrawerBackPreview() = sidebarHost.cancelBackPreview()

    fun finishDrawerBackPreview() = sidebarHost.finishBackPreview()

    fun requestFocus(): Boolean = titleBar?.requestPrimaryFocus() == true

    fun requestPanelFocus(): Boolean = attached && sidebarHost.requestPanelFocus()

    fun setResumed(resumed: Boolean) {
        if (disposed) return
        sidebarHost.setResumed(resumed)
    }

    /** 生命周期或窗口几何中断：各自清理局部动画与反馈。 */
    fun settleTransitions() {
        if (!attached || disposed) return
        sidebarHost.close(immediate = true)
        titleBar?.settleTransitions()
    }

    /** 终态：释放自有反馈、面板与挂载；共同父容器不在中途重建。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        attached = false
        sidebarHost.onNavigate = null
        sidebarHost.onSettings = null
        sidebarHost.onInputBlockedChanged = null
        sidebarHost.release()
        titleBar?.dispose()
        titleBar = null
        rail?.dispose()
        rail = null
        chromeHost.removeAllViews()
        appChromeTopPx = 0
        compact = false
        display = null
        snapshot = null
    }

    private fun mountTitleIfNeeded() {
        if (titleBar != null) return
        val bar = TitleBar(chromeHost.context).apply {
            onPrimaryAction = { handlePrimaryAction() }
            onHeightChanged = { height -> onTitleHeightChanged(height) }
        }
        chromeHost.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                bar.contentHeightPx,
            ),
        )
        titleBar = bar
        // 行高由固定公式得出，先用于首帧几何；实测变化再由回调纠正。
        appChromeTopPx = bar.contentHeightPx
        renderTitle()
        publishInsets(ChromeLayoutPolicy.insetsFor(WindowLayoutClass.Compact, appChromeTopPx, railWidthPx))
    }

    private fun removeTitle() {
        titleBar?.let { bar ->
            bar.dispose()
            chromeHost.removeView(bar)
        }
        titleBar = null
        appChromeTopPx = 0
    }

    private fun mountRailIfNeeded() {
        if (rail != null) return
        val bar = NavigationBar(chromeHost.context).apply {
            onNavigate = { destination -> onNavigate(destination) }
            onSettings = { onOpenSettings() }
        }
        chromeHost.addView(
            bar,
            FrameLayout.LayoutParams(railWidthPx, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        rail = bar
        display?.let(bar::render)
    }

    private fun removeRail() {
        rail?.let { bar ->
            bar.dispose()
            chromeHost.removeView(bar)
        }
        rail = null
    }

    /** 标题只在系统安全区内的应用行出现；不含系统顶区。 */
    @SuppressLint("RtlHardcoded") // 窗口矩形使用物理左上坐标，不随 RTL 镜像。
    private fun applyTitleRect(window: WindowSnapshot) {
        val bar = titleBar ?: return
        val safe = window.insets.systemSafeEdges
        val metrics = window.metrics
        val windowRect = PixelRect(
            left = safe.left,
            top = safe.top,
            right = (metrics.widthPx - safe.right).coerceAtLeast(safe.left),
            bottom = (safe.top + bar.contentHeightPx).coerceAtMost(metrics.heightPx),
        )
        val local = windowRect.relativeToRoot(metrics.rootLeftPx, metrics.rootTopPx)
        val params = bar.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width == local.width && params.height == local.height &&
            params.leftMargin == local.left && params.topMargin == local.top
        ) {
            return
        }
        params.width = local.width
        params.height = local.height
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.setMargins(local.left, local.top, 0, 0)
        bar.layoutParams = params
    }

    private fun onTitleHeightChanged(height: Int) {
        if (height == appChromeTopPx) return
        appChromeTopPx = height
        // 布局过程中只记录，下一帧再上报，避免测量 → 回报 → 重排循环。
        chromeHost.post {
            if (attached && !disposed && compact) {
                publishInsets(ChromeLayoutPolicy.insetsFor(WindowLayoutClass.Compact, appChromeTopPx, railWidthPx))
            }
        }
    }

    private fun renderTitle() {
        val bar = titleBar ?: return
        val input = display ?: return
        bar.render(
            TitleBarStateFactory.create(
                destination = input.destination,
                currentGame = input.currentGame,
                settingsOpen = input.settingsOpen,
                menuEnabled = compact,
                gameFrame = input.gameFrame,
                navDirection = input.navDirection,
                maiTextColor = input.maiTextColor,
                chuTextColor = input.chuTextColor,
                themeCircleColor = input.themeColor,
                maiDecorColor = input.maiDecorColor,
                chuDecorColor = input.chuDecorColor,
            ),
        )
    }

    private fun handlePrimaryAction() {
        if (display?.settingsOpen == true) {
            onCloseSettings()
            return
        }
        if (compact) sidebarHost.toggle()
    }

    private fun publishInsets(value: ChromeInsets) {
        if (value == lastInsets) return
        lastInsets = value
        onInsetsChanged(value)
    }

    private fun requestStatusBarHidden(hidden: Boolean) {
        // 首次必须显式下发：重建后的新窗口可能继承旧的隐藏状态。
        if (statusBarIntentInitialized && hidden == lastStatusBarHidden) return
        statusBarIntentInitialized = true
        lastStatusBarHidden = hidden
        onStatusBarHiddenChanged(hidden)
    }
}
