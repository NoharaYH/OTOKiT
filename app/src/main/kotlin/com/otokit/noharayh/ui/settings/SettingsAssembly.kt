package com.otokit.noharayh.ui.settings

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.animation.common.SettingsPageFrames
import com.otokit.noharayh.ui.animation.settings.SettingsPageTransitionRunner
import com.otokit.noharayh.ui.chrome.SettingsHeading
import com.otokit.noharayh.ui.chrome.TitleTexts
import com.otokit.noharayh.composition.scoreArchiveQueries
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.settings.subPages.PersonalizationSettingsPage
import com.otokit.noharayh.ui.settings.subPages.AppSettingsPage
import com.otokit.noharayh.ui.settings.subPages.ScoreArchiveKind
import com.otokit.noharayh.ui.settings.subPages.ScoreArchivePage
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions
import com.otokit.noharayh.ui.theme.NoneThemeColorRole
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.ThemeColors

/** 设置展示宿主。section 只是最后渲染值；所有稳定页面命令交回导航控制器。 */
@android.annotation.SuppressLint("ViewConstructor") // 由主壳传入明确事件构造，不提供 XML 入口。
class SettingsAssembly(
    context: Context,
    private val onOpenSection: (SettingsSection) -> Unit,
    private val onOpenLink: (String) -> Unit,
    private val displayScale: DisplayScaleOption,
    private val onDisplayScaleSelected: (DisplayScaleOption) -> Unit,
    private val renderingProvider: () -> BackgroundRenderingOptions,
    private val startupAnimationProvider: () -> Boolean,
    private val onStartupAnimationSelected: (Boolean) -> Unit,
    private val onHardwareAccelerationSelected: (Boolean) -> Unit,
    private val onHtmlAnimationsSelected: (Boolean) -> Unit,
    private val takeHardwareWarning: () -> Boolean,
    private val selectionProvider: () -> GameSelectionState,
    private var materkitColors: ThemeColors,
    private val onThemeModeSelected: (ThemeSelectionMode) -> Unit,
    private val onThemeOptionSelected: (ThemeOption, GameLine?) -> Unit,
    private val onNoneThemeBackgroundSelected: (Boolean) -> Unit,
    private val onNoneThemeWallpaperSelected: (Boolean) -> Unit,
    private val onNoneThemeColorSelected: (NoneThemeColorRole, Int) -> Unit,
    private val onTransitionStart: (SettingsHeading, SettingsHeading, Float, Boolean) -> Unit,
    private val onTransitionFrame: (TransitionProgress) -> Unit,
    private val onArchiveChanged: () -> Unit,
) : FrameLayout(context) {
    private data class Page(val section: SettingsSection?, val archive: ScoreArchiveKind? = null) {
        val heading get() = SettingsHeading(archive?.title ?: TitleTexts.forSettings(section), TitleTexts.colorForSettings(section))
    }

    private val rootPage = SettingsHome(context, onOpenSection)
    private val childHost = FrameLayout(context)
    private val divider = View(context).apply { setBackgroundColor(Color.WHITE) }
    private val options = rootPage.options
    private var current = Page(null)
    private var fromPage = current
    private var toPage = current
    private var fromView: View? = null
    private var toView: View? = null
    private var transitioning = false
    private var forward = true
    private var split = false
    private var margin = dp(20)
    private var disposed = false
    private var themeControlsEnabled = true
    private var themeDisabledProgress = 0f

    fun renderBackgroundRendering(options: BackgroundRenderingOptions) {
        if (disposed) return
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? AppSettingsPage)?.renderBackgroundRendering(options)
        }
    }

    private fun renderStartupAnimation(enabled: Boolean) {
        if (disposed) return
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? AppSettingsPage)?.renderStartupAnimation(enabled)
        }
    }

    fun renderThemeSelection(selection: GameSelectionState) {
        if (disposed) return
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? PersonalizationSettingsPage)?.renderSelection(selection)
        }
    }

    fun renderMaterkitColors(colors: ThemeColors) {
        if (disposed || materkitColors == colors) return
        materkitColors = colors
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? PersonalizationSettingsPage)?.renderMaterkitColors(colors)
        }
    }

    fun renderThemeDisabledProgress(progress: Float) {
        themeDisabledProgress = progress
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? PersonalizationSettingsPage)?.setDisabledProgress(progress)
        }
    }

    fun setThemeControlsEnabled(enabled: Boolean) {
        themeControlsEnabled = enabled
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? PersonalizationSettingsPage)?.setControlsEnabled(enabled)
        }
    }
    private val runner = SettingsPageTransitionRunner(::renderFrame)

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        // Split 时卡片外环会越出左右列；分隔线必须垫在内容下面，不能反遮按压效果。
        clipChildren = false
        clipToPadding = false
        childHost.clipChildren = false
        childHost.clipToPadding = false
        addView(divider)
        addView(rootPage)
        addView(childHost)
        updatePadding()
        settle(null)
    }

    fun updateWindow(useSplit: Boolean, windowWidthDp: Float) {
        val nextMargin = dp(if (windowWidthDp >= 840f) 32 else if (windowWidthDp >= 600f) 24 else 20)
        if (split == useSplit && margin == nextMargin) return
        split = useSplit
        margin = nextMargin
        updatePadding()
        settle(current.section, rebuild = true)
        requestLayout()
    }

    fun render(section: SettingsSection?) {
        if (!disposed && current.section != section) goTo(Page(section))
    }

    val archiveTitle get() = current.archive?.title

    private fun goTo(target: Page) {
        if (!transitioning || toPage != target) {
            settlePage(current)
            prepare(current, target)
        }
        val titleChanged = current.archive != target.archive
        current = target
        if (titleChanged) onArchiveChanged()
        runner.play(1f) { settlePage(target) }
    }

    fun closeArchive(): Boolean {
        if (current.archive == null) return false
        goTo(Page(current.section))
        return true
    }

    private fun personalizationPage(): PersonalizationSettingsPage? =
        if (current.section == SettingsSection.Personalization && !transitioning)
            childHost.getChildAt(childHost.childCount - 1) as? PersonalizationSettingsPage else null

    fun closeThemePanel(): Boolean = personalizationPage()?.closeThemePanel() == true
    fun beginThemePanelBack(): Boolean = personalizationPage()?.beginThemePanelBack() == true
    fun previewThemePanelBack(progress: Float) { personalizationPage()?.previewThemePanelBack(progress) }
    fun cancelThemePanelBack() { personalizationPage()?.cancelThemePanelBack() }
    fun finishThemePanelBack() { personalizationPage()?.finishThemePanelBack() }

    fun beginBackPreview(): Boolean {
        if (current.section == null) return false
        settlePage(current)
        prepare(current, Page(current.section.takeIf { current.archive != null }))
        return true
    }
    fun preview(progress: Float) { if (transitioning) runner.preview(progress) }
    fun cancelPreview() {
        if (!transitioning) return
        val restore = current
        runner.play(0f) { settlePage(restore) }
    }

    /** 窗口/生命周期接管：无回调提交，无延迟布局任务。 */
    fun settle(section: SettingsSection?, rebuild: Boolean = false) {
        settlePage(if (current.section == section) current else Page(section), rebuild)
    }

    private fun settlePage(page: Page, rebuild: Boolean = false) {
        if (disposed) return
        runner.stop()
        val keep = when {
            rebuild || page.section == null -> null
            transitioning && page == toPage -> toView
            transitioning && page == fromPage -> fromView
            !transitioning && page == current -> childHost.getChildAt(0)
            else -> null
        }
        if (transitioning) onTransitionFrame(TransitionProgress(if (page == toPage) 1f else 0f, false))
        transitioning = false
        fromView = null
        toView = null
        for (index in childHost.childCount - 1 downTo 0) {
            if (childHost.getChildAt(index) !== keep) childHost.removeViewAt(index)
        }
        if (page.section != null && keep == null) childHost.addView(buildPage(page), LayoutParams(-1, -1))
        val titleChanged = current.archive != page.archive
        current = page
        if (titleChanged) onArchiveChanged()
        val section = page.section
        reset(rootPage)
        for (index in 0 until childHost.childCount) reset(childHost.getChildAt(index))
        rootPage.visibility = if (split || section == null) VISIBLE else INVISIBLE
        childHost.visibility = if (section != null) VISIBLE else INVISIBLE
        divider.visibility = if (split) VISIBLE else GONE
        options.forEach { (key, option) -> option.setSelectionProgress(if (key == section) 1f else 0f) }
        updateAccessibility()
    }

    fun dispose() {
        runner.stop()
        disposed = true
        removeAllViews()
        fromView = null
        toView = null
    }

    private fun prepare(from: Page, to: Page) {
        fromPage = from
        toPage = to
        fromView = if (from.section == null) rootPage else childHost.getChildAt(0)
        toView = if (to.section == null) rootPage else buildPage(to).also { childHost.addView(it, LayoutParams(-1, -1)) }
        rootPage.visibility = if (split || from.section == null || to.section == null) VISIBLE else INVISIBLE
        childHost.visibility = VISIBLE
        transitioning = true
        forward = to.section != null && (from.section != to.section || from.archive == null)
        runner.reset(unfold = true)
        onTransitionStart(from.heading, to.heading, width.toFloat(), forward)
        renderFrame(TransitionProgress(0f, true))
        updateAccessibility()
    }

    private fun renderFrame(frame: TransitionProgress) {
        if (!transitioning || disposed) return
        val phoneUnfold = !split
        val pageProgress = if (phoneUnfold) SettingsPageFrames.phoneUnfoldProgress(frame.value, forward)
            else frame.value
        fromView?.let {
            if (!split || it !== rootPage) applyPageFrame(it, pageProgress, false)
        }
        toView?.let {
            if (!split || it !== rootPage) applyPageFrame(it, pageProgress, true)
        }
        val changed = if (phoneUnfold) SettingsPageFrames.phoneSelectionChange(frame.value, forward)
            else SettingsPageFrames.selectionChange(frame.value)
        options.forEach { (key, option) ->
            val start = if (key == fromPage.section) 1f else 0f
            val end = if (key == toPage.section) 1f else 0f
            option.setSelectionProgress(start + (end - start) * changed)
        }
        onTransitionFrame(TransitionProgress(pageProgress, frame.running))
    }

    private fun applyPageFrame(view: View, progress: Float, entering: Boolean) {
        val visual = SettingsPageFrames.unfoldPage(progress, entering, forward, resources.displayMetrics.density)
        // 分栏中直接换子页时，旧子页完整退场；仅展开侧使用揭示裁剪。
        val reveal = if (view !== rootPage && entering == forward)
            SettingsPageFrames.unfoldReveal(progress, forward) else 1f
        runner.apply(view, visual, reveal)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = transitioning || super.onInterceptTouchEvent(event)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val gap = if (split) dp(24) else 0
        val left = if (split) ((w - gap).coerceAtLeast(0) / 3) else w
        rootPage.measure(exact(left), exact(h))
        childHost.measure(exact(if (split) (w - left - gap).coerceAtLeast(0) else w), exact(h))
        divider.measure(exact(dp(2)), exact((h - dp(64)).coerceAtLeast(0)))
        setMeasuredDimension(w, h)
    }

    @android.annotation.SuppressLint("DrawAllocation") // 几何变化时重算纯帧，未持有延迟布局回调。
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        rootPage.layout(0, 0, rootPage.measuredWidth, b - t)
        val childLeft = if (split) rootPage.measuredWidth + dp(24) else 0
        childHost.layout(childLeft, 0, r - l, b - t)
        divider.layout(rootPage.measuredWidth + dp(11), dp(32), rootPage.measuredWidth + dp(13), b - t - dp(32))
        if (transitioning) renderFrame(TransitionProgress(runner.progress, true))
    }

    private fun updateAccessibility() {
        rootPage.importantForAccessibility = if (!transitioning && (split || current.section == null)) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        childHost.importantForAccessibility = if (!transitioning && current.section != null) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rootPage.descendantFocusability = if (!transitioning && (split || current.section == null)) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        childHost.descendantFocusability = if (!transitioning && current.section != null) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    private fun updatePadding() = rootPage.setContentMargins(margin, split)

    private fun buildPage(page: Page): View = page.archive?.let { archive ->
        ScoreArchivePage(context, archive, margin, split, scoreArchiveQueries(context)) { closeArchive() }
    } ?: buildChild(requireNotNull(page.section))

    private fun buildChild(section: SettingsSection) = SettingsPages.child(
        context = context,
        section = section,
        margin = margin,
        split = split,
        openLink = onOpenLink,
        openArchive = { goTo(Page(SettingsSection.ScoreSync, it)) },
        displayScale = displayScale,
        onDisplayScaleSelected = onDisplayScaleSelected,
        rendering = renderingProvider(),
        startupAnimationEnabled = startupAnimationProvider(),
        onStartupAnimationSelected = {
            onStartupAnimationSelected(it)
            renderStartupAnimation(startupAnimationProvider())
        },
        onHardwareAccelerationSelected = onHardwareAccelerationSelected,
        onHtmlAnimationsSelected = onHtmlAnimationsSelected,
        takeHardwareWarning = takeHardwareWarning,
        selection = selectionProvider(),
        materkitColors = materkitColors,
        onThemeModeSelected = {
            onThemeModeSelected(it)
            renderThemeSelection(selectionProvider())
        },
        onThemeOptionSelected = { option, game ->
            onThemeOptionSelected(option, game)
            renderThemeSelection(selectionProvider())
        },
        onNoneThemeWallpaperSelected = onNoneThemeWallpaperSelected,
        onNoneThemeColorSelected = onNoneThemeColorSelected,
        onNoneThemeBackgroundSelected = {
            onNoneThemeBackgroundSelected(it)
            renderThemeSelection(selectionProvider())
        },
    ).also {
        (it as? PersonalizationSettingsPage)?.apply {
            setControlsEnabled(themeControlsEnabled)
            setDisabledProgress(themeDisabledProgress)
        }
    }
    private fun reset(view: View) { view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f; view.translationX = 0f; view.translationY = 0f; view.clipBounds = null }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)
}
