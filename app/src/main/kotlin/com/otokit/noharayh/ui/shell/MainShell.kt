package com.otokit.noharayh.ui.shell

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.otokit.noharayh.storage.preferences.SharedPreferencesUiPreferences
import com.otokit.noharayh.ui.chrome.ChromeAssembly
import com.otokit.noharayh.ui.chrome.ChromeDisplayInput
import com.otokit.noharayh.ui.chrome.ChromeInsets
import com.otokit.noharayh.ui.chrome.SideBarHost
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.pages.ComingSoonPlaceholder
import com.otokit.noharayh.ui.pages.pageMusicData.MusicDataPlaceholder
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPlaceholder
import com.otokit.noharayh.ui.settings.SettingsPlaceholder
import com.otokit.noharayh.ui.shell.background.BackgroundHost
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.NavigationController
import com.otokit.noharayh.ui.shell.navigation.NavigationState
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
import com.otokit.noharayh.ui.window.metrics.WindowMetricsSnapshot
import com.otokit.noharayh.ui.window.relativeToRoot
import java.lang.ref.WeakReference

/**
 * 主壳：总组装与跨模块协调者。
 *
 * 持有唯一导航状态、音游选择与统一几何；外围 UI 通过 [ChromeAssembly] 挂载，
 * 只从它读取应用占用与模态事实，不直接操作标题或抽屉内部。
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

    /** 稳定的音游/主题选择；恢复顺序为保存状态、偏好、Registry 默认。 */
    private val selection = GameSelectionController(
        restoreGameSelection(
            saved = savedSelection,
            preferences = selectionPreferences.read(),
            lookup = ThemeRegistry::findById,
            defaultFor = ThemeRegistry::defaultFor,
        ),
    )

    private val chrome = ChromeAssembly(
        chromeHost = chromeHost,
        sidebarHost = sidebarHost,
        onNavigate = { destination -> onDrawerNavigate(destination) },
        onOpenSettings = { openSettings() },
        onCloseSettings = { closeSettings() },
        onInsetsChanged = { insets -> onChromeInsetsChanged(insets) },
        onInputBlockedChanged = { onDrawerInputChanged() },
    )

    private var mountedContent: PageContent? = null
    private var settingsContent: PageContent? = null
    private var background: BackgroundHost? = null
    private var latestWindowSnapshot: WindowSnapshot? = null
    private var chromeInsets = ChromeInsets.Zero
    private var appliedBackgroundRect: PixelRect? = null
    private var appliedBackgroundWindowRect: PixelRect? = null

    private var onCanHandleBackChanged: ((Boolean) -> Unit)? = null
    private var mountedDestination: Destination? = null
    private var appliedSettingsOpen: Boolean? = null
    private var preModalFocus: WeakReference<View>? = null
    private var appliedModal: Boolean = false
    private var contentAccessibility: Int = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var contentFocusability: Int = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private var resumed = false
    private var lifecycleState = LifecycleState.New

    val state: NavigationState
        get() = controller.state

    val selectionState: GameSelectionState
        get() = selection.state

    val canHandleBack: Boolean
        get() = lifecycleState == LifecycleState.Attached &&
            (controller.canHandleBack || chrome.blocksUnderlyingInput)

    fun setOnCanHandleBackChanged(listener: ((Boolean) -> Unit)?) {
        if (lifecycleState == LifecycleState.Disposed) return
        onCanHandleBackChanged = listener
    }

    /** 挂载覆盖层与外围 UI，并首次渲染；每个实例只允许挂载一次。 */
    fun attach() {
        check(lifecycleState == LifecycleState.New) { "MainShell 只允许挂载一次，销毁后不可重新挂载" }
        check(chromeHost.childCount == 0) { "外围 UI 由 ChromeAssembly 挂载，开始时必须为空" }
        lifecycleState = LifecycleState.Attached
        val current = selection.state
        background = BackgroundHost(backgroundHost) { gameLine ->
            onBackgroundSelectionConfirmed(gameLine)
        }.also { host ->
            host.attach(current.currentGame)
            host.renderSelection(
                currentGame = current.currentGame,
                maiTheme = themeFor(current.maiThemeId, GameLine.Mai),
                chuTheme = themeFor(current.chuThemeId, GameLine.Chu),
            )
            host.setInputEnabled(!(controller.state.settingsOpen || chrome.blocksUnderlyingInput))
        }
        val settings = SettingsPlaceholder.create(overlayHost.context, placeholderTextColor())
        settingsContent = settings
        overlayHost.addView(
            settings.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        chrome.attach()
        render()
    }

    /** 恢复宿主临时配置并解除监听；销毁为终态。 */
    fun detach() {
        if (lifecycleState == LifecycleState.Disposed) return
        val wasAttached = lifecycleState == LifecycleState.Attached
        lifecycleState = LifecycleState.Disposed
        onCanHandleBackChanged = null
        if (!wasAttached) return

        chrome.dispose()
        restoreContentIsolation(restoreFocus = false)
        appliedSettingsOpen = false
        settingsContent = null
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
        latestWindowSnapshot = null
        chromeInsets = ChromeInsets.Zero
    }

    fun navigate(destination: Destination) {
        if (lifecycleState != LifecycleState.Attached) return
        controller.navigate(destination)
        render()
    }

    fun openSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        chrome.closeDrawer(immediate = true)
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
        if (chrome.consumeBack()) return true
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

    /** 提交某个已登记主题；控制器校验登记 ID 与音游归属。 */
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
        settingsContent?.updateColor(color)
        background?.renderSelection(
            currentGame = current.currentGame,
            maiTheme = themeFor(current.maiThemeId, GameLine.Mai),
            chuTheme = themeFor(current.chuThemeId, GameLine.Chu),
        )
        chrome.render(chromeDisplay())
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
        this.resumed = resumed
        chrome.setResumed(resumed)
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
        settingsContent?.updateColor(color)
        chrome.render(chromeDisplay())
        selectionPreferences.write(current)
        return current.currentGame
    }

    /** 应用最新窗口快照：chrome 先决定形态与占用，再由唯一几何入口落布局。 */
    fun updateWindow(snapshot: WindowSnapshot) {
        if (lifecycleState != LifecycleState.Attached) return
        latestWindowSnapshot = snapshot
        chrome.updateWindow(snapshot)
        recomputeGeometry()
    }

    /** 唯一几何入口：窗口快照或 chrome 占用变化都走这里。 */
    private fun recomputeGeometry() {
        val snapshot = latestWindowSnapshot ?: return
        val rects = ContentGeometry.compute(snapshot, chromeInsets)
        applyContentRect(backgroundHost, rects.base, snapshot.metrics)
        val backgroundRect = rects.base.relativeToRoot(
            snapshot.metrics.rootLeftPx,
            snapshot.metrics.rootTopPx,
        )
        // 同时比较窗口与根局部矩形，覆盖根和安全区一同平移、局部坐标不变的情况。
        if (appliedBackgroundRect != backgroundRect || appliedBackgroundWindowRect != rects.base) {
            appliedBackgroundRect = backgroundRect
            appliedBackgroundWindowRect = rects.base
            background?.onViewportChanged()
        }
        applyContentRect(contentHost, rects.interactive, snapshot.metrics)
        applyContentRect(overlayHost, rects.interactive, snapshot.metrics)

        val interaction = rects.interactive.relativeToRoot(
            snapshot.metrics.rootLeftPx,
            snapshot.metrics.rootTopPx,
        )
        chrome.layout(interaction)
    }

    private fun onChromeInsetsChanged(insets: ChromeInsets) {
        if (lifecycleState != LifecycleState.Attached) return
        if (insets == chromeInsets) return
        chromeInsets = insets
        recomputeGeometry()
    }

    private fun applyContentRect(host: View, rect: PixelRect, metrics: WindowMetricsSnapshot) {
        val params = host.layoutParams as? FrameLayout.LayoutParams ?: return
        val localRect = rect.relativeToRoot(metrics.rootLeftPx, metrics.rootTopPx)
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
        chrome.render(chromeDisplay())
        val modal = current.settingsOpen || chrome.blocksUnderlyingInput
        if (appliedSettingsOpen != current.settingsOpen || appliedModal != modal) {
            applyModalState(current.settingsOpen)
        }
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    private fun chromeDisplay(): ChromeDisplayInput {
        val nav = controller.state
        val current = selection.state
        val theme = themeFor(current.selectedThemeId(), current.currentGame)
        return ChromeDisplayInput(
            destination = nav.destination,
            currentGame = current.currentGame,
            settingsOpen = nav.settingsOpen,
            themeColor = theme.definition.colors.basicColor,
            maiDecorColor = themeFor(current.maiThemeId, GameLine.Mai).definition.colors.basicColor,
            chuDecorColor = themeFor(current.chuThemeId, GameLine.Chu).definition.colors.basicColor,
        )
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
            Destination.ComingSoon -> ComingSoonPlaceholder.create(context, placeholderTextColor())
        }

    /**
     * 设置与抽屉共同决定模态：覆盖层只服务设置，输入与焦点按两者合并。
     */
    private fun applyModalState(settingsOpen: Boolean) {
        val modal = settingsOpen || chrome.blocksUnderlyingInput
        val enteringModal = modal && !appliedModal
        val enteringSettings = settingsOpen && appliedSettingsOpen != true
        if (enteringModal) {
            preModalFocus = contentHost.rootView.findFocus()?.let(::WeakReference)
            contentAccessibility = contentHost.importantForAccessibility
            contentFocusability = contentHost.descendantFocusability
            contentHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            contentHost.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            contentHost.findFocus()?.clearFocus()
        }
        if (!modal && appliedModal) restoreContentIsolation(restoreFocus = true)
        appliedModal = modal
        appliedSettingsOpen = settingsOpen
        overlayHost.visibility = if (settingsOpen) View.VISIBLE else View.GONE
        when {
            enteringSettings -> overlayHost.requestFocus()
            enteringModal && !settingsOpen -> chrome.requestPanelFocus()
        }
        background?.setInputEnabled(!modal)
    }

    /** 只恢复主壳自己修改的属性；旧页面销毁或后台/销毁时不强行恢复焦点。 */
    private fun restoreContentIsolation(restoreFocus: Boolean) {
        if (appliedModal) {
            contentHost.importantForAccessibility = contentAccessibility
            contentHost.descendantFocusability = contentFocusability
        }
        appliedModal = false
        val previous = preModalFocus?.get()
        preModalFocus = null
        if (!restoreFocus || !resumed || lifecycleState != LifecycleState.Attached) return
        val restored = previous != null && previous.isAttachedToWindow && previous.isShown &&
            previous.rootView === contentHost.rootView && previous.requestFocus()
        if (!restored && !contentHost.requestFocus()) chrome.requestFocus()
    }

    private fun onDrawerNavigate(destination: Destination) {
        if (lifecycleState != LifecycleState.Attached) return
        controller.navigate(destination)
        render()
    }

    /** 抽屉开合会改变返回消费与底层输入；设置与抽屉共同决定模态。 */
    private fun onDrawerInputChanged() {
        if (lifecycleState != LifecycleState.Attached) return
        applyModalState(controller.state.settingsOpen)
        onCanHandleBackChanged?.invoke(canHandleBack)
    }
}
