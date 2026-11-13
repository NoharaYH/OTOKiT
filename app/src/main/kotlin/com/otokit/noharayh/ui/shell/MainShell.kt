package com.otokit.noharayh.ui.shell

import com.otokit.noharayh.ui.constants.motion.ColorPickerMotion

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.otokit.noharayh.ui.theme.NoneThemeColorRole
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.otokit.noharayh.storage.preferences.SharedPreferencesUiPreferences
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.chrome.ChromeAssembly
import com.otokit.noharayh.ui.chrome.ChromeDisplayInput
import com.otokit.noharayh.ui.chrome.ChromeInsets
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.shell.BackgroundSwitchRunner
import com.otokit.noharayh.ui.animation.shell.BackgroundVeilRunner
import com.otokit.noharayh.ui.animation.shell.ContentTransitionRunner
import com.otokit.noharayh.ui.animation.shell.SettingsTransitionRunner
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.chrome.SideBarHost
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.pages.ComingSoonPlaceholder
import com.otokit.noharayh.ui.pages.pageMusicData.MusicDataPlaceholder
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPlaceholder
import com.otokit.noharayh.ui.settings.SettingsAssembly
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import com.otokit.noharayh.ui.shell.background.BackgroundHost
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions
import com.otokit.noharayh.ui.shell.background.BackgroundPhase
import com.otokit.noharayh.ui.shell.background.BackgroundSnapshot
import com.otokit.noharayh.ui.shell.background.BackgroundVeilView
import com.otokit.noharayh.ui.constants.motion.BackgroundVeilTransition
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.BackPreviewSession
import com.otokit.noharayh.ui.shell.navigation.BackPreviewSession.Target
import com.otokit.noharayh.ui.shell.navigation.NavigationController
import com.otokit.noharayh.ui.shell.navigation.NavigationOrder
import com.otokit.noharayh.ui.shell.navigation.NavigationState
import com.otokit.noharayh.ui.shell.selection.GameSelectionController
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.shell.selection.SelectionSource
import com.otokit.noharayh.ui.shell.selection.UiPreferencesAdapter
import com.otokit.noharayh.ui.shell.selection.restoreGameSelection
import com.otokit.noharayh.ui.theme.BackgroundPalette
import com.otokit.noharayh.ui.theme.NoneThemePalette
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.BackgroundSelection
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeRegistry
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
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
    private val displayScale: DisplayScaleOption = DisplayScaleOption.DEFAULT,
    private val onDisplayScaleSelected: (DisplayScaleOption) -> Unit = {},
    private val initialSettingsSettled: Boolean = false,
    private val onOpenExternalLink: (String) -> Unit = {},
    private val selectionPreferences: UiPreferencesAdapter =
        UiPreferencesAdapter(SharedPreferencesUiPreferences(contentHost.context)),
) {

    private enum class LifecycleState { New, Attached, Disposed }

    private val controller = NavigationController(initialState)
    private var backgroundRendering = selectionPreferences.readBackgroundRendering()

    /** 稳定的音游/主题选择；恢复顺序为保存状态、偏好、Registry 默认。 */
    private val selection = GameSelectionController(
        restoreGameSelection(
            saved = savedSelection,
            preferences = selectionPreferences.read(),
            lookup = ThemeRegistry::findById,
            defaultFor = ThemeRegistry::defaultFor,
        ),
    )

    private var noneThemePalette = resolveNonePalette()
    private val colorHandler = Handler(Looper.getMainLooper())
    private var colorSavePending = false
    private val saveColors = Runnable {
        colorSavePending = false
        selectionPreferences.write(selection.state)
    }
    private val wallpaperColors = WallpaperColorSource(contentHost.context) { seed ->
        if (lifecycleState == LifecycleState.Attached && selection.updateWallpaperSeed(seed)) applyNoneColors()
    }

    private fun resolveNonePalette(): NoneThemePalette = selection.state.let {
        NoneThemePalette.resolve(it.noneThemeSeedColor, it.noneThemeUseWallpaper,
            it.noneThemePrimaryColor, it.noneThemeSecondaryColor)
    }

    private val chrome = ChromeAssembly(
        chromeHost = chromeHost,
        sidebarHost = sidebarHost,
        onNavigate = { destination -> onDrawerNavigate(destination) },
        onOpenSettings = { openSettings() },
        onCloseSettings = { navigateSettingsBack() },
        onInsetsChanged = { insets -> onChromeInsetsChanged(insets) },
        onInputBlockedChanged = { onDrawerInputChanged() },
        onStatusBarHiddenChanged = { hidden -> statusBarHiddenListener?.invoke(hidden) },
    )

    private var mountedContent: PageContent? = null
    private var outgoingContent: PageContent? = null
    private var contentRunner: ContentTransitionRunner? = null
    private var settingsRunner: SettingsTransitionRunner? = null
    private var settingsClosing = false
    private var settleInitialSettings = initialSettingsSettled
    private val backPreview = BackPreviewSession()
    private var settingsContent: PageContent? = null
    private var settingsPage: SettingsAssembly? = null
    private var background: BackgroundHost? = null
    private var backgroundVeil: BackgroundVeilRunner? = null
    private var backgroundVeilView: BackgroundVeilView? = null
    private var backgroundSwitchScrim: View? = null
    private var backgroundSwitchRunner: BackgroundSwitchRunner? = null
    private var backgroundSwitching = false
    private var lastMaiBackground: BackgroundSelection? = null
    private var lastChuBackground: BackgroundSelection? = null
    private var backgroundSubscription: AutoCloseable? = null
    private var latestWindowSnapshot: WindowSnapshot? = null
    private var latestBackgroundSnapshot: BackgroundSnapshot? = null
    private var latestGameFrame: GameFrame? = null
    private var lastChromeDestination: Destination? = null
    private var contentWidthPx = 0
    private var contentHeightPx = 0
    private var chromeInsets = ChromeInsets.Zero
    private var appliedBackgroundRect: PixelRect? = null
    private var appliedBackgroundWindowRect: PixelRect? = null

    private var onCanHandleBackChanged: ((Boolean) -> Unit)? = null
    private var statusBarHiddenListener: ((Boolean) -> Unit)? = null
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
            (backPreview.awaitingTerminal || controller.canHandleBack || chrome.blocksUnderlyingInput)

    fun setOnCanHandleBackChanged(listener: ((Boolean) -> Unit)?) {
        if (lifecycleState == LifecycleState.Disposed) return
        onCanHandleBackChanged = listener
    }

    /** 平台层只执行显示意图，不参与形态判断。 */
    fun setOnStatusBarHiddenChanged(listener: ((Boolean) -> Unit)?) {
        if (lifecycleState == LifecycleState.Disposed) return
        statusBarHiddenListener = listener
    }

    /** 挂载覆盖层与外围 UI，并首次渲染；每个实例只允许挂载一次。 */
    fun attach() {
        check(lifecycleState == LifecycleState.New) { "MainShell 只允许挂载一次，销毁后不可重新挂载" }
        check(chromeHost.childCount == 0) { "外围 UI 由 ChromeAssembly 挂载，开始时必须为空" }
        lifecycleState = LifecycleState.Attached
        val current = selection.state
        background = BackgroundHost(backgroundHost, backgroundRendering) { gameLine ->
            onBackgroundSelectionConfirmed(gameLine)
        }.also { host ->
            host.setNoneBackgroundColor(noneBackgroundColor())
            host.attach(current.currentGame)
            host.renderSelection(
                currentGame = current.currentGame,
                maiBackground = backgroundFor(current, GameLine.Mai),
                chuBackground = backgroundFor(current, GameLine.Chu),
            )
            lastMaiBackground = backgroundFor(current, GameLine.Mai)
            lastChuBackground = backgroundFor(current, GameLine.Chu)
            host.setInputEnabled(!(controller.state.settingsOpen || chrome.blocksUnderlyingInput))
            backgroundSubscription = host.subscribe { snapshot ->
                latestBackgroundSnapshot = snapshot
                applyGameFrame()
            }
        }
        val veilView = BackgroundVeilView(backgroundHost.context)
        backgroundHost.addView(
            veilView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        backgroundHost.bringChildToFront(veilView)
        val veilRunner = BackgroundVeilRunner(veilView)
        backgroundVeilView = veilView
        backgroundVeil = veilRunner
        val switchScrim = View(backgroundHost.context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        backgroundHost.addView(
            switchScrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        backgroundHost.bringChildToFront(switchScrim)
        backgroundSwitchScrim = switchScrim
        backgroundSwitchRunner = BackgroundSwitchRunner(switchScrim)
        background?.setOnFirstVisualReady(veilRunner::ready)
        veilRunner.show()
        val observer = backgroundHost.viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // 视图可能在首帧前被 detach，旧 observer 已 not alive；直接 remove 会抛
                // IllegalStateException，必须容错，不能因此闪退。
                try {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                } catch (_: IllegalStateException) {
                    // observer 已随 View 销毁，无需移除。
                }
                backgroundVeil?.start(BackgroundVeilTransition.TIMEOUT_MS)
                return true
            }
        })
        val page = SettingsAssembly(
            overlayHost.context,
            onOpenSection = ::openSettingsSection,
            onOpenLink = onOpenExternalLink,
            displayScale = displayScale,
            onDisplayScaleSelected = onDisplayScaleSelected,
            renderingProvider = { backgroundRendering },
            takeHardwareWarning = {
                if (hardwareWarningShownThisProcess) false else {
                    hardwareWarningShownThisProcess = true
                    true
                }
            },
            onHardwareAccelerationSelected = {
                updateBackgroundRendering(backgroundRendering.copy(hardwareAccelerated = it))
            },
            onHtmlAnimationsSelected = {
                updateBackgroundRendering(backgroundRendering.copy(htmlAnimations = it))
            },
            selectionProvider = { selection.state },
            onThemeModeSelected = ::selectThemeMode,
            onThemeOptionSelected = ::selectThemeOption,
            onNoneThemeBackgroundSelected = ::selectNoneThemeBackground,
            onNoneThemeWallpaperSelected = ::selectNoneThemeWallpaper,
            onNoneThemeColorSelected = ::selectNoneThemeColor,
            onTransitionStart = chrome::prepareSettingsPageTransition,
            onTransitionFrame = chrome::renderSettingsPageProgress,
        )
        settingsPage = page
        val settings = PageContent(page, disposeAction = page::dispose)
        settings.view.visibility = View.GONE
        settingsContent = settings
        overlayHost.addView(
            settings.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // 背景切换期间只压黑幕；不额外加全屏触摸锁，避免锁在设置页上。
        contentRunner = ContentTransitionRunner(contentHost) { frame ->
            chrome.renderNavigationProgress(frame)
        }
        settingsRunner = SettingsTransitionRunner(
            overlayHost = overlayHost,
            featureProvider = { mountedContent?.view },
            settingsProvider = { settingsContent?.view },
            onProgress = { frame -> chrome.renderSettingsProgress(frame) },
        )
        if (settleInitialSettings && controller.state.settingsOpen) {
            // 显示比例重建：直接落到设置子页，不重播功能页→设置、父页→子页两套动画。
            settingsPage?.settle(controller.state.settingsSection)
        }
        chrome.attach()
        render()
        wallpaperColors.start()
    }

    /** 恢复宿主临时配置并解除监听；销毁为终态。 */
    fun detach() {
        if (lifecycleState == LifecycleState.Disposed) return
        flushColorSave()
        wallpaperColors.close()
        val wasAttached = lifecycleState == LifecycleState.Attached
        lifecycleState = LifecycleState.Disposed
        onCanHandleBackChanged = null
        if (!wasAttached) return
        cancelBackgroundSwitch()

        backPreview.reset()
        backgroundSubscription?.close()
        backgroundSubscription = null
        restoreContentIsolation(restoreFocus = false)
        appliedSettingsOpen = false
        contentRunner?.dispose()
        contentRunner = null
        settingsRunner?.dispose()
        settingsRunner = null
        chrome.dispose()
        settingsClosing = false
        outgoingContent?.dispose()
        outgoingContent = null
        mountedContent?.dispose()
        settingsContent?.dispose()
        latestBackgroundSnapshot = null
        latestGameFrame = null
        contentWidthPx = 0
        contentHeightPx = 0
        settingsContent = null
        settingsPage = null
        background?.setOnFirstVisualReady(null)
        backgroundVeil?.dispose()
        backgroundVeil = null
        backgroundVeilView?.let { backgroundHost.removeView(it) }
        backgroundVeilView = null
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

    /**
     * 恢复 View 层级后强制回到功能页：设置是瞬态，不能由 saved state 或子 View 恢复可见性。
     */
    fun resetTransientSettingsAfterRestore() {
        if (lifecycleState != LifecycleState.Attached) return
        invalidateBackPreview()
        controller.closeSettings()
        settingsPage?.settle(null)
        settingsClosing = false
        appliedSettingsOpen = false
        settingsContent?.view?.visibility = View.GONE
        overlayHost.visibility = View.GONE
        render()
        // 展示层先接收恢复后的状态，再由唯一进度源下发稳定端点。
        settingsRunner?.resetClosed()
        chrome.settleTransitions()
    }
    fun navigate(destination: Destination) {
        if (lifecycleState != LifecycleState.Attached) return
        invalidateBackPreview()
        settingsPage?.settle(controller.state.settingsSection)
        controller.navigate(destination)
        render()
    }

    fun openSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        invalidateBackPreview()
        if (!controller.state.settingsOpen) settingsPage?.settle(null)
        contentRunner?.cancel()
        outgoingContent?.dispose()
        outgoingContent = null
        chrome.closeDrawer(immediate = false)
        controller.openSettings()
        render()
    }

    fun openSettingsSection(section: SettingsSection) {
        if (lifecycleState != LifecycleState.Attached || !controller.state.settingsOpen) return
        invalidateBackPreview()
        // 子页接管前先结束整体入场，避免两个执行器竞争同一标题层。
        settingsRunner?.endPreview(true)
        chrome.settleTransitions()
        controller.openSettingsSection(section)
        render()
    }

    fun closeSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        invalidateBackPreview()
        settingsPage?.settle(controller.state.settingsSection)
        controller.closeSettings()
        render()
    }

    /** 标题栏返回明确离开当前设置层级，不消费局部选择面板。 */
    private fun navigateSettingsBack() {
        if (lifecycleState != LifecycleState.Attached || !controller.state.settingsOpen) return
        invalidateBackPreview()
        if (controller.consumeBack()) render()
    }

    fun consumeBack(): Boolean {
        if (lifecycleState != LifecycleState.Attached) return false
        if (backPreview.awaitingTerminal) {
            finishBackPreview()
            return true
        }
        if (chrome.consumeBack()) return true
        if (controller.state.settingsOpen && settingsPage?.closeThemePanel() == true) return true
        if (controller.state.settingsOpen && controller.state.settingsSection == null) settingsPage?.settle(null)
        val consumed = controller.consumeBack()
        if (consumed) render()
        return consumed
    }

    /** 提交已确认的音游选择；同值无副作用，变化时更新占位颜色并写偏好。 */
    fun selectGame(gameLine: GameLine) {
        if (lifecycleState != LifecycleState.Attached) return
        invalidateBackPreview()
        if (!selection.selectGame(gameLine)) return
        applySelection()
    }

    /** 提交某个已登记主题；控制器校验登记 ID 与音游归属。 */
    fun selectTheme(theme: RegisteredTheme) {
        if (lifecycleState != LifecycleState.Attached || backgroundSwitching) return
        if (!selection.selectTheme(theme)) return
        applySelection()
    }

    /** 个性化设置的主题选项写入口；通用占位也可通过 target 落到单个音游。 */
    fun selectThemeOption(option: ThemeOption, target: GameLine? = null) {
        if (lifecycleState != LifecycleState.Attached || backgroundSwitching) return
        if (!selection.selectOption(option, target)) return
        applySelection()
    }

    fun selectThemeMode(mode: ThemeSelectionMode) {
        if (lifecycleState != LifecycleState.Attached || backgroundSwitching) return
        if (!selection.selectThemeMode(mode)) return
        applySelection()
    }

    private fun selectNoneThemeBackground(enabled: Boolean) {
        if (lifecycleState != LifecycleState.Attached || backgroundSwitching) return
        if (!selection.selectNoneThemeBackground(enabled)) return
        invalidateBackPreview()
        background?.setNoneBackgroundColor(noneBackgroundColor(), animate = true)
        settingsPage?.renderThemeSelection(selection.state)
        selectionPreferences.write(selection.state)
    }

    private fun selectNoneThemeWallpaper(enabled: Boolean) {
        if (lifecycleState != LifecycleState.Attached || backgroundSwitching) return
        if (selection.selectNoneThemeWallpaper(enabled)) applyNoneColors()
    }

    private fun selectNoneThemeColor(role: NoneThemeColorRole, color: Int) {
        if (lifecycleState != LifecycleState.Attached || backgroundSwitching) return
        if (selection.selectNoneThemeColor(role, color)) applyNoneColors()
    }

    /** 连续调色不走背景换页/黑幕；保留当前 View 和手势，只刷新颜色。 */
    private fun applyNoneColors() {
        noneThemePalette = resolveNonePalette()
        invalidateBackPreview()
        background?.setNoneBackgroundColor(noneBackgroundColor())
        settingsPage?.renderThemeSelection(selection.state)
        if (selection.state.themeMode == ThemeSelectionMode.None) {
            mountedContent?.updateColor(placeholderTextColor())
            settingsContent?.updateColor(placeholderTextColor())
            mountedContent?.let(::updatePageThemeColors)
            chrome.render(chromeDisplay(), animateColors = false)
        }
        colorHandler.removeCallbacks(saveColors)
        colorSavePending = true
        colorHandler.postDelayed(saveColors, ColorPickerMotion.SAVE_DELAY_MS)
    }

    private fun flushColorSave() {
        colorHandler.removeCallbacks(saveColors)
        if (colorSavePending) saveColors.run()
    }

    private fun noneBackgroundColor(): Int = if (selection.state.noneThemeTintBackground) {
        noneThemePalette.backgroundColor
    } else {
        BackgroundPalette.NONE_COLOR
    }

    /** 选择变化后只更新颜色、背景页与落盘，不重建页面、不改导航。 */
    private fun applySelection() {
        val current = selection.state
        settingsPage?.renderThemeSelection(current)
        val color = placeholderTextColor()
        mountedContent?.updateColor(color)
        settingsContent?.updateColor(color)
        mountedContent?.let(::updatePageThemeColors)
        val nextMai = backgroundFor(current, GameLine.Mai)
        val nextChu = backgroundFor(current, GameLine.Chu)
        val changed = lastMaiBackground != null && (
            lastMaiBackground != nextMai || lastChuBackground != nextChu
        )
        lastMaiBackground = nextMai
        lastChuBackground = nextChu
        if (changed) {
            switchBackground(current, nextMai, nextChu)
        } else {
            background?.renderSelection(
                currentGame = current.currentGame,
                maiBackground = nextMai,
                chuBackground = nextChu,
            )
        }
        chrome.render(chromeDisplay())
        selectionPreferences.write(current)
    }

    /** 背景硬切换：黑幕压住切换瞬间，切换完成后再淡出。 */
    private fun switchBackground(
        current: GameSelectionState,
        mai: BackgroundSelection,
        chu: BackgroundSelection,
    ) {
        val runner = backgroundSwitchRunner
        val host = background
        if (runner == null || host == null) {
            host?.renderSelection(current.currentGame, mai, chu)
            return
        }
        backgroundSwitching = true
        settingsPage?.setThemeControlsEnabled(false)
        runner.play(onCovered = {
            if (lifecycleState == LifecycleState.Attached) {
                host.renderSelection(selection.state.currentGame, mai, chu)
            }
        }, onFinished = {
            if (lifecycleState == LifecycleState.Attached) {
                backgroundSwitching = false
                settingsPage?.setThemeControlsEnabled(true)
            }
        }, onProgress = { progress ->
            settingsPage?.renderThemeDisabledProgress(progress)
        })
    }

    private fun cancelBackgroundSwitch() {
        backgroundSwitchRunner?.cancel()
        backgroundSwitchRunner = null
        backgroundSwitchScrim?.let(backgroundHost::removeView)
        backgroundSwitchScrim = null
        backgroundSwitching = false
    }

    private fun placeholderTextColor(): Int {
        val current = selection.state
        return themeColorsFor(current, current.currentGame).primaryColor
    }

    /** 无主题共用种子色生成的配色；HTML 和通用主题保留原配色规则。 */
    private fun themeColorsFor(
        state: GameSelectionState,
        gameLine: GameLine,
    ): ThemeColors = if (state.themeMode == ThemeSelectionMode.None) {
        noneThemePalette.colors
    } else {
        BackgroundPalette.colorsFor(backgroundFor(state, gameLine))
    }

    private fun backgroundFor(
        state: GameSelectionState,
        gameLine: GameLine,
    ): BackgroundSelection = when (state.themeMode) {
        ThemeSelectionMode.None -> BackgroundSelection.None
        ThemeSelectionMode.Merged -> BackgroundPalette.fromOption(
            ThemeOptions.resolveMerged(state.mergedThemeId),
        )
        ThemeSelectionMode.Independent -> BackgroundPalette.fromOption(
            ThemeOptions.resolveForGame(gameLine, state.selectedThemeId(gameLine)),
        )
    }

    /** 主壳确认并保存背景选项，再分别交给背景运行层与设置展示层。 */
    private fun updateBackgroundRendering(options: BackgroundRenderingOptions) {
        if (lifecycleState != LifecycleState.Attached || backgroundRendering == options) return
        backgroundRendering = options
        selectionPreferences.writeBackgroundRendering(options)
        background?.setRenderingOptions(options)
        settingsPage?.renderBackgroundRendering(options)
    }

    /** 背景随 Activity 生命周期暂停/恢复，不重建 WebView；离开前台时收起抽屉。 */
    fun setBackgroundResumed(resumed: Boolean) {
        if (lifecycleState != LifecycleState.Attached) return
        this.resumed = resumed
        if (resumed) wallpaperColors.refresh() else flushColorSave()
        if (!resumed) invalidateBackPreview()
        chrome.setResumed(resumed)
        background?.setResumed(resumed)
        if (!resumed) settleTransitions()
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
        settingsPage?.renderThemeSelection(current)
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
        val previous = latestWindowSnapshot
        // 只有实际窗口尺寸/形态变化才失效返回预览与切页；IME、系统栏等 insets 变化不能中断跟手。
        val geometryChanged = previous != null && (
            previous.layoutClass != snapshot.layoutClass ||
                previous.metrics.widthPx != snapshot.metrics.widthPx ||
                previous.metrics.heightPx != snapshot.metrics.heightPx
            )
        if (geometryChanged) {
            invalidateBackPreview()
            settleTransitions()
        }
        latestWindowSnapshot = snapshot
        chrome.updateWindow(snapshot)
        settingsPage?.updateWindow(
            snapshot.layoutClass != WindowLayoutClass.Compact,
            snapshot.metrics.widthPx / snapshot.metrics.density,
        )
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
        contentWidthPx = rects.interactive.width
        contentHeightPx = rects.interactive.height
        applyGameFrame()

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
        // 先准备文字与旧字截图，再启动内容持有的唯一计时驱动。
        chrome.render(chromeDisplay())
        if (current.settingsOpen) settingsPage?.render(current.settingsSection)
        if (mountedDestination != current.destination) {
            showContent(current.destination)
        }
        applyGameFrame()
        lastChromeDestination = current.destination
        val modal = current.settingsOpen || chrome.blocksUnderlyingInput
        if (appliedSettingsOpen != current.settingsOpen || appliedModal != modal) {
            applyModalState(current.settingsOpen, settleSettings = settleInitialSettings)
        }
        settleInitialSettings = false
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    private fun chromeDisplay(): ChromeDisplayInput {
        val nav = controller.state
        val current = selection.state
        val currentColors = themeColorsFor(current, current.currentGame)
        val maiColors = themeColorsFor(current, GameLine.Mai)
        val chuColors = themeColorsFor(current, GameLine.Chu)
        val direction = if (
            lastChromeDestination == null || lastChromeDestination == nav.destination
        ) {
            0
        } else {
            NavigationOrder.direction(lastChromeDestination, nav.destination)
        }
        return ChromeDisplayInput(
            destination = nav.destination,
            currentGame = current.currentGame,
            settingsOpen = nav.settingsOpen,
            settingsSection = nav.settingsSection,
            themeColor = currentColors.primaryColor,
            maiTextColor = maiColors.primaryColor,
            chuTextColor = chuColors.primaryColor,
            maiDecorColor = maiColors.primaryColor,
            chuDecorColor = if (current.themeMode != ThemeSelectionMode.Independent) {
                currentColors.secondaryColor
            } else {
                chuColors.primaryColor
            },
            gameFrame = latestGameFrame,
            navDirection = direction,
        )
    }

    private fun showContent(destination: Destination) {
        val previous = mountedContent
        val runner = contentRunner
        val content = createContent(contentHost.context, destination)
        if (previous == null || runner == null) {
            contentHost.addView(
                content.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            runner?.set(content.view)
            mountedContent = content
            mountedDestination = destination
            applyGameFrame()
            content.setInteractive(true)
            return
        }
        val direction = navDirection(previousDestination = mountedDestination, destination = destination)
        outgoingContent?.dispose()
        outgoingContent = previous
        mountedContent = content
        mountedDestination = destination
        previous.setInteractive(false)
        content.setInteractive(true)
        applyGameFrame()
        runner.run(previous.view, content.view, direction) {
            outgoingContent?.dispose()
            outgoingContent = null
        }
    }

    private fun navDirection(previousDestination: Destination?, destination: Destination): Int =
        NavigationOrder.direction(previousDestination, destination)

    private fun createContent(context: Context, destination: Destination): PageContent =
        when (destination) {
            Destination.ScoreSync -> ScoreSyncPlaceholder.create(context)
            Destination.MusicData -> MusicDataPlaceholder.create(context)
            Destination.ComingSoon -> ComingSoonPlaceholder.create(context)
        }.also(::updatePageThemeColors)

    private fun updatePageThemeColors(content: PageContent) {
        val current = selection.state
        val maiColor = themeColorsFor(current, GameLine.Mai).primaryColor
        val chuColor = themeColorsFor(current, GameLine.Chu).primaryColor
        content.updateGameColors(maiColor, chuColor)
    }

    /** 背景是唯一进度源；shell 把它转成纯帧，页面和 chrome 只消费帧。 */
    private fun applyGameFrame() {
        val snapshot = latestBackgroundSnapshot
        val game = snapshot?.selectedGame ?: selection.state.currentGame
        val phase = when (snapshot?.phase) {
            BackgroundPhase.Dragging -> GamePhase.Dragging
            BackgroundPhase.Settling -> GamePhase.Settling
            BackgroundPhase.Idle, null -> GamePhase.Idle
        }
        val progress = snapshot?.progress ?: if (game == GameLine.Mai) 0f else 1f
        val frame = GameFrame(
            selectedMai = game == GameLine.Mai,
            progress = progress,
            phase = phase,
            windowWidthPx = latestWindowSnapshot?.metrics?.widthPx ?: 0,
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
        )
        latestGameFrame = frame
        mountedContent?.updateGameFrame(frame)
        chrome.renderGameFrame(frame)
    }

    /**
     * 设置与抽屉共同决定模态：覆盖层只服务设置，输入与焦点按两者合并。
     */
    private fun applyModalState(settingsOpen: Boolean, settleSettings: Boolean = false) {
        val modal = settingsOpen || chrome.blocksUnderlyingInput
        val enteringModal = modal && !appliedModal
        val previousSettings = appliedSettingsOpen
        val enteringSettings = settingsOpen && previousSettings != true
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

        if (settingsOpen) {
            if (enteringSettings) {
                settingsClosing = false
                settingsRunner?.cancel()
                settingsContent?.view?.visibility = View.VISIBLE
                overlayHost.visibility = View.VISIBLE
                if (settleSettings) settingsRunner?.openImmediate() else settingsRunner?.animateOpen()
            } else if (!settingsClosing) {
                overlayHost.visibility = View.VISIBLE
            }
        } else if (previousSettings == true && !settingsClosing) {
            settingsClosing = true
            overlayHost.visibility = View.VISIBLE
            settingsRunner?.animateClose(settingsRunner?.openProgress ?: 1f) {
                settingsClosing = false
                overlayHost.visibility = View.GONE
                settingsContent?.view?.visibility = View.GONE
            }
        } else if (!settingsClosing) {
            overlayHost.visibility = View.GONE
            settingsContent?.view?.visibility = View.GONE
        }

        when {
            enteringSettings -> overlayHost.requestFocus()
            enteringModal && !settingsOpen -> chrome.requestPanelFocus()
        }
        background?.setInputEnabled(!modal)
    }

    /** 平台预测返回：开始只锁定目标，不提交任何业务状态。 */
    fun beginBackPreview(): Boolean {
        if (lifecycleState != LifecycleState.Attached) return false
        if (backPreview.target != Target.None) return true
        if (chrome.blocksUnderlyingInput && chrome.beginDrawerBackPreview()) {
            backPreview.begin(Target.Drawer)
            return true
        }
        if (controller.state.settingsOpen && settingsPage?.beginThemePanelBack() == true) {
            backPreview.begin(Target.SettingsMenu)
            return true
        }
        if (controller.state.settingsOpen && controller.state.settingsSection != null) {
            settingsRunner?.endPreview(true)
            chrome.settleTransitions()
            if (settingsPage?.beginBackPreview() == true) {
                backPreview.begin(Target.SettingsChild)
                return true
            }
        }
        if (controller.state.settingsOpen) {
            contentRunner?.cancel()
            backPreview.begin(Target.Settings)
            chrome.beginBackPreview()
            settingsRunner?.preview(0f)
            return true
        }
        backPreview.begin(Target.None)
        onCanHandleBackChanged?.invoke(canHandleBack)
        return false
    }

    fun updateBackPreview(progress: Float) {
        if (lifecycleState != LifecycleState.Attached) return
        backPreview.update(progress)
        when (backPreview.target) {
            Target.Settings -> {
                settingsRunner?.preview(backPreview.progress)
            }

            Target.SettingsMenu -> settingsPage?.previewThemePanelBack(backPreview.progress)
            Target.SettingsChild -> settingsPage?.preview(backPreview.progress)
            Target.Drawer -> chrome.updateDrawerBackPreview(backPreview.progress)
            Target.None -> Unit
        }
    }

    fun cancelBackPreview() {
        if (lifecycleState != LifecycleState.Attached) return
        when (backPreview.take()?.target) {
            Target.Settings -> {
                chrome.cancelBackPreview()
                settingsRunner?.cancelPreview()
            }

            Target.SettingsMenu -> settingsPage?.cancelThemePanelBack()
            Target.SettingsChild -> settingsPage?.cancelPreview()
            Target.Drawer -> chrome.cancelDrawerBackPreview()
            Target.None, null -> Unit
        }
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    /** 完成一次预测返回：只提交一次，已到端点不重放整段动画。 */
    fun finishBackPreview() {
        if (lifecycleState != LifecycleState.Attached) return
        val preview = backPreview.take() ?: run {
            // 失效手势的完成事件在这里结束，不能再作为普通返回消费新状态。
            onCanHandleBackChanged?.invoke(canHandleBack)
            return
        }
        when (preview.target) {
            Target.SettingsMenu -> settingsPage?.finishThemePanelBack()
            Target.Settings -> {
                if (!controller.state.settingsOpen) return
                chrome.endBackPreview()
                controller.closeSettings()
                render()
                // render 从预览最后一帧接续关闭；已到端点则直接落稳。
                if (preview.progress >= 0.99f) {
                    settingsRunner?.cancel()
                    settingsRunner?.endPreview(settingsOpen = false)
                    settingsClosing = false
                    overlayHost.visibility = View.GONE
                    settingsContent?.view?.visibility = View.GONE
                }
            }

            Target.SettingsChild -> {
                if (!controller.state.settingsOpen || controller.state.settingsSection == null) return
                controller.backToSettingsRoot()
                render()
            }
            Target.Drawer -> chrome.finishDrawerBackPreview()
            Target.None -> Unit
        }
        onCanHandleBackChanged?.invoke(canHandleBack)
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

    /** 预览期间任何命令或窗口变化都让会话失效；旧完成回调不得再提交返回。 */
    private fun invalidateBackPreview() {
        when (backPreview.invalidate()) {
            Target.Settings -> {
                chrome.cancelBackPreview()
                settingsRunner?.cancelPreview()
            }

            Target.SettingsMenu -> settingsPage?.cancelThemePanelBack()
            Target.SettingsChild -> settingsPage?.cancelPreview()
            Target.Drawer -> chrome.cancelDrawerBackPreview()
            Target.None -> return
        }
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    private fun onDrawerNavigate(destination: Destination) {
        navigate(destination)
    }

    /** 丢弃失效几何上的动画，只呈现控制器已提交的状态。 */
    private fun settleTransitions() {
        contentRunner?.cancel()
        outgoingContent?.dispose()
        outgoingContent = null
        settingsPage?.settle(controller.state.settingsSection)
        settingsRunner?.endPreview(controller.state.settingsOpen)
        settingsClosing = false
        overlayHost.visibility = if (controller.state.settingsOpen) View.VISIBLE else View.GONE
        chrome.settleTransitions()
    }

    /** 抽屉开合会改变返回消费与底层输入；设置与抽屉共同决定模态。 */
    private fun onDrawerInputChanged() {
        if (lifecycleState != LifecycleState.Attached) return
        applyModalState(controller.state.settingsOpen)
        onCanHandleBackChanged?.invoke(canHandleBack)
    }

    private companion object {
        // 只记录本进程是否展示过：取消也算已提醒；Activity/壳重建不重置，不写入偏好。
        var hardwareWarningShownThisProcess = false
    }
}
