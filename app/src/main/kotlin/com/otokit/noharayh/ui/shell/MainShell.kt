package com.otokit.noharayh.ui.shell

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.otokit.noharayh.R
import com.otokit.noharayh.storage.preferences.SharedPreferencesUiPreferences
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.chrome.SideBarHost
import com.otokit.noharayh.ui.chrome.TitleBar
import com.otokit.noharayh.ui.chrome.TitleBarStateFactory
import com.otokit.noharayh.ui.pages.pageMusicData.MusicDataPlaceholder
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPlaceholder
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.NavigationController
import com.otokit.noharayh.ui.shell.navigation.NavigationState
import com.otokit.noharayh.ui.shell.background.BackgroundHost
import com.otokit.noharayh.ui.shell.selection.GameSelectionController
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.shell.selection.SelectionSource
import com.otokit.noharayh.ui.shell.selection.UiPreferencesAdapter
import com.otokit.noharayh.ui.shell.selection.restoreGameSelection
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeRegistry
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot
import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import com.otokit.noharayh.ui.window.metrics.WindowMetricsSnapshot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 主壳：唯一知道“往哪一层挂什么”的地方。
 *
 * 只连接 [NavigationController] 与页面占位；不持有 Activity、不处理返回键，也不把状态写入口交给
 * 其他模块。返回消费能力由 [setOnCanHandleBackChanged] 对外通知，Activity 只同步 callback.isEnabled。
 */
class MainShell(
    private val backgroundHost: FrameLayout,
    private val contentHost: FrameLayout,
    private val chromeHost: FrameLayout,
    private val overlayHost: FrameLayout,
    private val sidebarHost: SideBarHost,
    initialState: NavigationState = NavigationState(),
    savedSelection: SelectionSource = SelectionSource(null, null, null, null),
    private val selectionPreferences: UiPreferencesAdapter =
        UiPreferencesAdapter(SharedPreferencesUiPreferences(contentHost.context)),
) {

    private enum class LifecycleState { New, Attached, Disposed }

    private val controller = NavigationController(initialState)

    private val density = contentHost.resources.displayMetrics.density

    /** 稳定的音游/主题选择；恢复顺序为保存状态、偏好、Registry 默认。 */
    private val selection = GameSelectionController(
        restoreGameSelection(
            saved = savedSelection,
            preferences = selectionPreferences.read(),
            lookup = ThemeRegistry::findById,
            defaultFor = ThemeRegistry::defaultFor,
        ),
    )

    private var mountedContent: PageContent? = null
    private var overlayTextView: TextView? = null
    private var background: BackgroundHost? = null
    private var titleBar: TitleBar? = null
    private var latestWindowSnapshot: WindowSnapshot? = null
    private var appChromeTopPx = 0
    private var appliedBackgroundRect: PixelRect? = null
    private var appliedBackgroundWindowRect: PixelRect? = null

    private var onCanHandleBackChanged: ((Boolean) -> Unit)? = null
    private var mountedDestination: Destination? = null
    private var appliedSettingsOpen: Boolean? = null
    private var appliedModal: Boolean = false
    private var contentAccessibility: Int = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var contentFocusability: Int = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private var lifecycleState = LifecycleState.New

    val state: NavigationState
        get() = controller.state

    val selectionState: GameSelectionState
        get() = selection.state

    val canHandleBack: Boolean
        get() = lifecycleState == LifecycleState.Attached &&
            (controller.canHandleBack || sidebarHost.isVisible)

    fun setOnCanHandleBackChanged(listener: ((Boolean) -> Unit)?) {
        if (lifecycleState == LifecycleState.Disposed) return
        onCanHandleBackChanged = listener
    }

    /** 挂载覆盖层并首次渲染；每个实例只允许挂载一次，重复调用直接失败。 */
    fun attach() {
        check(lifecycleState == LifecycleState.New) { "MainShell 只允许挂载一次，销毁后不可重新挂载" }
        check(chromeHost.childCount == 0) { "外围 UI 由后续步骤挂载，本步必须为空" }
        lifecycleState = LifecycleState.Attached
        val selection = selection.state
        background = BackgroundHost(backgroundHost) { gameLine ->
            onBackgroundSelectionConfirmed(gameLine)
        }.also { host ->
            host.attach(selection.currentGame)
            host.renderSelection(
                currentGame = selection.currentGame,
                maiTheme = themeFor(selection.maiThemeId, GameLine.Mai),
                chuTheme = themeFor(selection.chuThemeId, GameLine.Chu),
            )
            host.setInputEnabled(!(controller.state.settingsOpen || sidebarHost.isVisible))
        }
        sidebarHost.onNavigate = { destination -> onDrawerNavigate(destination) }
        sidebarHost.onSettings = { openSettings() }
        sidebarHost.onOpenChanged = { open -> onDrawerVisibilityChanged(open) }
        overlayHost.addView(createSettingsPlaceholder(overlayHost.context))
        render()
    }

    /** 恢复宿主临时配置并解除监听；销毁为终态，即使尚未挂载也不能再次使用。 */
    fun detach() {
        if (lifecycleState == LifecycleState.Disposed) return
        val wasAttached = lifecycleState == LifecycleState.Attached
        lifecycleState = LifecycleState.Disposed
        onCanHandleBackChanged = null
        if (!wasAttached) return

        if (appliedSettingsOpen == true || appliedModal) applyModalState(false)
        sidebarHost.close()
        sidebarHost.release()
        titleBar?.dispose()
        titleBar = null
        chromeHost.removeAllViews()
        appChromeTopPx = 0
        background?.dispose()
        background = null
        overlayHost.visibility = View.GONE
        contentHost.removeAllViews()
        overlayHost.removeAllViews()
        mountedDestination = null
        appliedSettingsOpen = null
        mountedContent = null
        appliedBackgroundRect = null
        appliedBackgroundWindowRect = null
        overlayTextView = null
        latestWindowSnapshot = null
    }

    fun navigate(destination: Destination) {
        if (lifecycleState != LifecycleState.Attached) return
        controller.navigate(destination)
        render()
    }

    fun openSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        sidebarHost.close()
        controller.openSettings()
        render()
    }

    fun closeSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        controller.closeSettings()
        render()
    }

    fun consumeBack(): Boolean {
        if (lifecycleState != LifecycleState.Attached) return false
        if (sidebarHost.isVisible) {
            sidebarHost.close()
            return true
        }
        val consumed = controller.consumeBack()
        if (consumed) render()
        return consumed
    }

    /** 提交已确认的音游选择；同值无副作用，变化时更新占位颜色并写偏好。 */
    fun selectGame(gameLine: GameLine) {
        if (lifecycleState != LifecycleState.Attached) return
        if (!selection.selectGame(gameLine)) return
        applySelection()
    }

    /** 提交某个已登记主题；控制器校验登记 ID 与音游归属，拒绝未知或错属项。 */
    fun selectTheme(theme: RegisteredTheme) {
        if (lifecycleState != LifecycleState.Attached) return
        if (!selection.selectTheme(theme)) return
        applySelection()
    }

    /** 选择变化后只更新颜色、背景页与落盘，不重建页面、不改导航。 */
    private fun applySelection() {
        val current = selection.state
        val color = placeholderTextColor()
        mountedContent?.updateColor(color)
        overlayTextView?.setTextColor(color)
        background?.renderSelection(
            currentGame = current.currentGame,
            maiTheme = themeFor(current.maiThemeId, GameLine.Mai),
            chuTheme = themeFor(current.chuThemeId, GameLine.Chu),
        )
        renderTitle()
        selectionPreferences.write(current)
    }

    private fun placeholderTextColor(): Int {
        val current = selection.state
        return themeFor(current.selectedThemeId(), current.currentGame).definition.colors.basicColor
    }

    private fun themeFor(themeId: String, gameLine: GameLine): RegisteredTheme =
        ThemeRegistry.resolveForGame(gameLine, themeId)

    /** 背景随 Activity 生命周期暂停/恢复，不重建 WebView；离开前台时收起抽屉。 */
    fun setBackgroundResumed(resumed: Boolean) {
        if (!resumed) sidebarHost.close()
        background?.setResumed(resumed)
    }

    /**
     * 背景确认了用户翻页：只提交选择、更新颜色并落盘。
     *
     * 此时 Pager 已在该页，不能再调用 renderSelection 触发定位或截断 Settling。
     */
    private fun onBackgroundSelectionConfirmed(gameLine: GameLine): GameLine {
        if (lifecycleState != LifecycleState.Attached) return selection.state.currentGame
        if (!selection.selectGame(gameLine)) return selection.state.currentGame

        val current = selection.state
        val color = placeholderTextColor()
        mountedContent?.updateColor(color)
        overlayTextView?.setTextColor(color)
        renderTitle()
        selectionPreferences.write(current)
        return current.currentGame
    }

    /**
     * 应用最新窗口快照：先按断点决定标题有无，再用系统安全区 + 应用占用统一重算内容矩形。
     */
    fun updateWindow(snapshot: WindowSnapshot) {
        if (lifecycleState != LifecycleState.Attached) return
        latestWindowSnapshot = snapshot
        updateChromeForWindow(snapshot)
        recomputeGeometry()
    }

    private fun updateChromeForWindow(snapshot: WindowSnapshot) {
        if (snapshot.layoutClass == WindowLayoutClass.Compact) {
            mountTitleIfNeeded()
            applyTitleRect(snapshot)
        } else {
            removeTitle()
        }
        sidebarHost.setDragEnabled(
            snapshot.layoutClass == WindowLayoutClass.Compact && !controller.state.settingsOpen,
        )
    }

    private fun mountTitleIfNeeded() {
        if (titleBar != null) return
        val bar = TitleBar(chromeHost.context).apply {
            onPrimaryAction = { handlePrimaryAction() }
            onHeightChanged = { height -> onTitleHeightChanged(height) }
        }
        chromeHost.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bar.contentHeightPx),
        )
        titleBar = bar
        // 行高由固定公式得出，先用于首帧几何；实测变化再由回调纠正。
        appChromeTopPx = bar.contentHeightPx
        renderTitle()
    }

    private fun removeTitle() {
        titleBar?.let { bar ->
            bar.dispose()
            chromeHost.removeView(bar)
        }
        titleBar = null
        if (appChromeTopPx != 0) {
            appChromeTopPx = 0
            recomputeGeometry()
        }
    }

    /** 标题只在系统安全区内的应用行出现；不含系统顶区。 */
    @SuppressLint("RtlHardcoded") // 窗口矩形使用物理左上坐标，不随 RTL 镜像。
    private fun applyTitleRect(snapshot: WindowSnapshot) {
        val bar = titleBar ?: return
        val safe = snapshot.insets.systemSafeEdges
        val metrics = snapshot.metrics
        val windowRect = PixelRect(
            left = safe.left,
            top = safe.top,
            right = (metrics.widthPx - safe.right).coerceAtLeast(safe.left),
            bottom = (safe.top + bar.contentHeightPx).coerceAtMost(metrics.heightPx),
        )
        val local = ContentGeometry.relativeToRoot(windowRect, metrics.rootLeftPx, metrics.rootTopPx)
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
        // 布局过程中只记录，下一帧再统一重算，避免测量 → 回报 → 重排循环。
        chromeHost.post { if (lifecycleState == LifecycleState.Attached) recomputeGeometry() }
    }

    /** 唯一几何入口：窗口快照或应用占用变化都走这里。 */
    private fun recomputeGeometry() {
        val snapshot = latestWindowSnapshot ?: return
        val rects = ContentGeometry.compute(snapshot, AppChromeInsets(top = appChromeTopPx))
        applyContentRect(backgroundHost, rects.base, snapshot.metrics)
        val backgroundRect = ContentGeometry.relativeToRoot(
            rects.base, snapshot.metrics.rootLeftPx, snapshot.metrics.rootTopPx,
        )
        // 同时比较窗口与根局部矩形，覆盖根和安全区一同平移、局部坐标不变的情况。
        if (appliedBackgroundRect != backgroundRect || appliedBackgroundWindowRect != rects.base) {
            appliedBackgroundRect = backgroundRect
            appliedBackgroundWindowRect = rects.base
            background?.onViewportChanged()
        }
        applyContentRect(contentHost, rects.interactive, snapshot.metrics)
        applyContentRect(overlayHost, rects.interactive, snapshot.metrics)

        // 抽屉使用同一交互矩形；Medium/Expanded 关闭并清掉命中区。
        val compact = snapshot.layoutClass == WindowLayoutClass.Compact
        if (compact) {
            val interaction = ContentGeometry.relativeToRoot(
                rects.interactive, snapshot.metrics.rootLeftPx, snapshot.metrics.rootTopPx,
            )
            val drawerWidth = minOf(
                (308f * density).roundToInt(),
                (interaction.width * 0.92f).roundToInt(),
            )
            sidebarHost.setMetrics(interaction, drawerWidth)
        } else {
            sidebarHost.close()
        }
    }

    /** 由已提交的导航与选择状态生成标题展示数据。 */
    private fun renderTitle() {
        val bar = titleBar ?: return
        val nav = controller.state
        val current = selection.state
        val theme = themeFor(current.selectedThemeId(), current.currentGame)
        bar.render(
            TitleBarStateFactory.create(
                destination = nav.destination,
                currentGame = current.currentGame,
                settingsOpen = nav.settingsOpen,
                menuEnabled = titleBar != null,
                functionalTextColor = theme.definition.colors.basicColor,
                themeCircleColor = theme.definition.colors.basicColor,
                maiDecorColor = themeFor(current.maiThemeId, GameLine.Mai).definition.colors.basicColor,
                chuDecorColor = themeFor(current.chuThemeId, GameLine.Chu).definition.colors.basicColor,
            ),
        )
    }

    private fun handlePrimaryAction() {
        if (lifecycleState != LifecycleState.Attached) return
        if (controller.state.settingsOpen) {
            closeSettings()
            return
        }
        // 功能态：只有标题存在（Compact）时菜单才可用。
        if (titleBar != null) sidebarHost.toggle()
    }

    /** 胶囊点击：先收起抽屉，再以一次导航提交切页。 */
    private fun onDrawerNavigate(destination: Destination) {
        if (lifecycleState != LifecycleState.Attached) return
        sidebarHost.close()
        controller.navigate(destination)
        render()
    }

    /** 抽屉开合会改变返回消费与底层输入；设置与抽屉共同决定模态。 */
    private fun onDrawerVisibilityChanged(open: Boolean) {
        if (lifecycleState != LifecycleState.Attached) return
        applyModalState(controller.state.settingsOpen)
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    private fun applyContentRect(host: View, rect: PixelRect, metrics: WindowMetricsSnapshot) {
        val params = host.layoutParams as? FrameLayout.LayoutParams ?: return
        val localRect = ContentGeometry.relativeToRoot(rect, metrics.rootLeftPx, metrics.rootTopPx)
        // 窗口矩形使用物理左上坐标，不随 RTL 镜像；超出根的部分由父容器裁剪。
        @SuppressLint("RtlHardcoded") // 这是物理窗口坐标，不是随语言切换的内容起始方向。
        val gravity = Gravity.TOP or Gravity.LEFT
        if (params.width == localRect.width && params.height == localRect.height &&
            params.leftMargin == localRect.left && params.topMargin == localRect.top &&
            params.rightMargin == 0 && params.bottomMargin == 0 && params.gravity == gravity
        ) {
            return
        }

        // 明确尺寸，避免 match_parent 随 IME 缩小的根重新测量背景。
        params.width = localRect.width
        params.height = localRect.height
        params.gravity = gravity
        params.setMargins(localRect.left, localRect.top, 0, 0)
        host.layoutParams = params
    }

    private fun render() {
        val current = controller.state
        if (mountedDestination != current.destination) {
            mountContent(current.destination)
        }
        sidebarHost.setSelected(current.destination)
        sidebarHost.setDragEnabled(titleBar != null && !current.settingsOpen)
        val modal = current.settingsOpen || sidebarHost.isVisible
        if (appliedSettingsOpen != current.settingsOpen || appliedModal != modal) {
            applyModalState(current.settingsOpen)
        }
        renderTitle()
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    private fun mountContent(destination: Destination) {
        contentHost.removeAllViews()
        val content = createContent(contentHost.context, destination)
        contentHost.addView(
            content.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        mountedContent = content
        mountedDestination = destination
    }

    private fun createContent(context: Context, destination: Destination): PageContent =
        when (destination) {
            Destination.ScoreSync -> ScoreSyncPlaceholder.create(context, placeholderTextColor())
            Destination.MusicData -> MusicDataPlaceholder.create(context, placeholderTextColor())
            Destination.ComingSoon -> {
                val text = placeholderText(context, R.string.placeholder_coming_soon)
                PageContent(text, text::setTextColor)
            }
        }

    /**
     * 设置与抽屉共同决定模态：遮罩只服务设置覆盖层，输入与焦点按两者合并。
     */
    private fun applyModalState(settingsOpen: Boolean) {
        val modal = settingsOpen || sidebarHost.isVisible
        if (modal && !appliedModal) {
            contentAccessibility = contentHost.importantForAccessibility
            contentFocusability = contentHost.descendantFocusability
            contentHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            contentHost.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else if (!modal && appliedModal) {
            contentHost.importantForAccessibility = contentAccessibility
            contentHost.descendantFocusability = contentFocusability
        }
        appliedModal = modal

        appliedSettingsOpen = settingsOpen
        overlayHost.visibility = if (settingsOpen) View.VISIBLE else View.GONE
        if (settingsOpen) overlayHost.requestFocus()

        background?.setInputEnabled(!modal)
    }

    private fun createSettingsPlaceholder(context: Context): View {
        val container = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(resolveColorBackground(context))
        }
        val text = placeholderText(context, R.string.placeholder_settings)
        overlayTextView = text
        container.addView(
            text,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return container
    }

    private fun placeholderText(context: Context, textRes: Int): TextView = TextView(context).apply {
        setText(textRes)
        gravity = Gravity.CENTER
        setTextAppearance(android.R.style.TextAppearance_Material_Large)
        AppTypography.applyAppTypeface(this)
        // 颜色放在 TextAppearance 与字体之后，避免被前者覆盖。
        setTextColor(placeholderTextColor())
    }

    private fun resolveColorBackground(context: Context): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) {
            return 0
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
