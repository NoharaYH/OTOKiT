package com.otokit.noharayh.ui.settings

import android.content.Context
import android.graphics.Rect
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
import com.otokit.noharayh.composition.scoreArchiveActions
import com.otokit.noharayh.composition.scoreSyncLogs
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.kit.common.EdgeToEdgeScrollable
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.settings.subPages.PersonalizationSettingsPage
import com.otokit.noharayh.ui.settings.subPages.AppSettingsPage
import com.otokit.noharayh.ui.settings.subPages.ScoreArchivePage
import com.otokit.noharayh.ui.settings.subPages.ScoreSyncLogPage
import com.otokit.noharayh.ui.settings.subPages.ProxySettingsPage
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions
import com.otokit.noharayh.ui.theme.NoneThemeColorRole
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.ThemeColors
import kotlin.math.roundToInt

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
    private val onSubpageChanged: () -> Unit,
) : FrameLayout(context) {
    private data class Page(val section: SettingsSection?, val subpage: SyncSubpage? = null) {
        val heading get() = SettingsHeading(subpage?.title ?: TitleTexts.forSettings(section), TitleTexts.colorForSettings(section))
    }

    private val rootPage = SettingsHome(context, onOpenSection)
    private val childHost = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (split) setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
            else {
                val drawHeight = MeasureSpec.getSize(heightMeasureSpec)
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    child.layoutParams.height = paneHeight(child, drawHeight)
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (split) layoutSplitPages()
            else super.onLayout(changed, left, top, right, bottom)
        }
    }
    private val childPages = linkedMapOf<Page, View>()
    private val options = rootPage.options
    private var current = Page(null)
    private var fromPage = current
    private var toPage = current
    private var transitioning = false
    private var forward = true
    private var split = false
    private var margin = dp(20)
    private var bottomSafeInset = 0
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
        clipChildren = false
        clipToPadding = false
        childHost.clipChildren = false
        childHost.clipToPadding = false
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

    /** 绘制面保持延伸；滚动页用安全留白控制终点，固定记录页保留安全边界。 */
    fun setBottomSafeInset(bottom: Int) {
        if (disposed || bottomSafeInset == bottom) return
        bottomSafeInset = bottom
        rootPage.setBottomSafeInset(bottom)
        childPages.values.filterIsInstance<EdgeToEdgeScrollable>().forEach { it.setBottomSafeInset(bottom) }
        requestLayout()
    }

    fun render(section: SettingsSection?) {
        if (!disposed && current.section != section) goTo(Page(section))
    }

    val subpageTitle get() = current.subpage?.title

    private fun goTo(target: Page) {
        if (current == target) return
        if (!transitioning || toPage != target) {
            settlePage(current)
            prepare(current, target)
        }
        val titleChanged = current.subpage != target.subpage
        current = target
        if (titleChanged) onSubpageChanged()
        runner.play(1f) { settlePage(target) }
    }

    fun closeSubpage(): Boolean {
        if (current.subpage == null) return false
        goTo(Page(current.section))
        return true
    }

    private fun personalizationPage(): PersonalizationSettingsPage? =
        if (current.section == SettingsSection.Personalization && !transitioning)
            childPages[current] as? PersonalizationSettingsPage else null

    fun closeThemePanel(): Boolean = personalizationPage()?.closeThemePanel() == true
    fun beginThemePanelBack(): Boolean = personalizationPage()?.beginThemePanelBack() == true
    fun previewThemePanelBack(progress: Float) { personalizationPage()?.previewThemePanelBack(progress) }
    fun cancelThemePanelBack() { personalizationPage()?.cancelThemePanelBack() }
    fun finishThemePanelBack() { personalizationPage()?.finishThemePanelBack() }

    fun beginBackPreview(): Boolean {
        if (current.section == null) return false
        settlePage(current)
        prepare(current, Page(current.section.takeIf { current.subpage != null }))
        return true
    }
    fun preview(progress: Float) {
        if (transitioning) runner.preview(SettingsPageFrames.resistedBackProgress(progress))
    }
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
        if (transitioning) onTransitionFrame(TransitionProgress(if (page == toPage) 1f else 0f, false))
        transitioning = false
        val keep = panesFor(page)
        val entries = childPages.iterator()
        while (entries.hasNext()) {
            val (key, view) = entries.next()
            if (rebuild || key !in keep) {
                childHost.removeView(view)
                entries.remove()
            }
        }
        mount(page)
        val titleChanged = current.subpage != page.subpage
        current = page
        if (titleChanged) onSubpageChanged()
        val section = page.section
        reset(rootPage)
        childPages.values.forEach(::reset)
        rootPage.visibility = if (split || section == null) VISIBLE else INVISIBLE
        childHost.visibility = if (section != null) VISIBLE else INVISIBLE
        options.forEach { (key, option) -> option.setSelectionProgress(if (key == section) 1f else 0f) }
        updateAccessibility()
    }

    fun dispose() {
        runner.stop()
        disposed = true
        removeAllViews()
        childPages.clear()
    }

    private fun panesFor(page: Page): List<Page> = when {
        page.section == null -> emptyList()
        split && page.subpage != null -> listOf(Page(page.section), page)
        else -> listOf(page)
    }

    private fun mount(page: Page) {
        panesFor(page).forEach { pane ->
            childPages.getOrPut(pane) { buildPage(pane).also {
                (it as? EdgeToEdgeScrollable)?.setBottomSafeInset(bottomSafeInset)
                childHost.addView(it, LayoutParams(-1, -1))
            } }
        }
    }

    private fun viewFor(page: Page): View? = if (page.section == null) rootPage else childPages[page]

    private fun prepare(from: Page, to: Page) {
        fromPage = from
        toPage = to
        mount(to)
        rootPage.visibility = if (split || from.section == null || to.section == null) VISIBLE else INVISIBLE
        childHost.visibility = VISIBLE
        transitioning = true
        forward = to.section != null && (from.section != to.section || from.subpage == null || to.subpage != null)
        val resizesSplit = split && from.section == to.section && ((from.subpage == null) != (to.subpage == null))
        when {
            resizesSplit && to.subpage != null -> runner.reset(SettingsTransition.ENTER_DURATION_MS, SettingsTransition.CURVE)
            resizesSplit -> runner.reset(curve = SettingsPageTransition.SPLIT_EXIT_CURVE)
            else -> runner.reset(SettingsPageTransition.UNFOLD_DURATION_MS, SettingsPageTransition.UNFOLD_CURVE)
        }
        onTransitionStart(from.heading, to.heading, width.toFloat(), forward)
        renderFrame(TransitionProgress(0f, true))
        updateAccessibility()
    }

    private fun renderFrame(frame: TransitionProgress) {
        if (!transitioning || disposed) return
        val phoneUnfold = !split
        val pageProgress = if (phoneUnfold) SettingsPageFrames.phoneUnfoldProgress(frame.value, forward)
            else frame.value
        if (split && (fromPage.subpage != null || toPage.subpage != null)) {
            renderSplitSubpageFrame(frame.value)
        } else {
            viewFor(fromPage)?.let { if (!split || it !== rootPage) applyPageFrame(it, pageProgress, false) }
            viewFor(toPage)?.let { if (!split || it !== rootPage) applyPageFrame(it, pageProgress, true) }
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
        runner.apply(view, visual)
    }

    private fun renderSplitSubpageFrame(progress: Float) {
        when {
            fromPage.section != toPage.section -> {
                applySplitSectionFrame(fromPage, progress, entering = false)
                applySplitSectionFrame(toPage, progress, entering = true)
            }
            fromPage.subpage != null && toPage.subpage != null -> {
                childPages[fromPage]?.let { applyPageFrame(it, progress, false) }
                childPages[toPage]?.let { applyPageFrame(it, progress, true) }
            }
            else -> {
                // 右栏移出时已被右缘裁切，前半程保留更多不透明度，让标题随栏移动。
                if (fromPage.subpage != null) childPages[fromPage]?.alpha = 1f - progress * progress
                if (toPage.subpage != null) childPages[toPage]?.alpha = progress
            }
        }
        layoutSplitPages()
    }

    private fun applySplitSectionFrame(page: Page, progress: Float, entering: Boolean) {
        val frame = SettingsPageFrames.unfoldPage(progress, entering, forward, resources.displayMetrics.density)
        childPages[Page(page.section)]?.let { runner.apply(it, frame) }
        if (page.subpage != null) childPages[page]?.let { runner.apply(it, frame) }
    }

    private fun layoutSplitPages() {
        if (width == 0 || childHost.height == 0) return
        val from = if (transitioning) fromPage else current
        val to = if (transitioning) toPage else current
        val switchesSection = from.section != to.section
        val outgoingProgress = SettingsPageFrames.splitArchiveProgress(runner.progress, from.subpage != null,
            to.subpage != null, switchesSection, entering = false)
        val incomingProgress = if (switchesSection) {
            SettingsPageFrames.splitArchiveProgress(runner.progress, from.subpage != null,
                to.subpage != null, switchesSection, entering = true)
        } else outgoingProgress
        val gap = dp(SPLIT_GAP_DP)
        val outgoingPanes = SettingsPageFrames.splitPanes(width, gap, outgoingProgress)
        val incomingPanes = if (incomingProgress == outgoingProgress) outgoingPanes
            else SettingsPageFrames.splitPanes(width, gap, incomingProgress)
        childPages.forEach { (page, view) ->
            val enteringSection = switchesSection && page.section == to.section
            val progress = if (enteringSection) incomingProgress else outgoingProgress
            val panes = if (enteringSection) incomingPanes else outgoingPanes
            val isSubpage = page.subpage != null
            val paneWidth = if (isSubpage) panes.archiveWidth else panes.secondaryWidth
            val left = if (isSubpage) panes.archiveLeft - panes.secondaryLeft else 0
            if (!isSubpage && page.section == SettingsSection.ScoreSync) {
                val endInset = (margin * (1f - progress)).roundToInt()
                if (view.paddingRight != endInset) view.setPadding(0, 0, endInset, view.paddingBottom)
            }
            val paneHeight = paneHeight(view, childHost.height)
            view.measure(exact(paneWidth), exact(paneHeight))
            view.layout(left, 0, left + paneWidth, paneHeight)
            val shift = view.translationX.roundToInt()
            view.clipBounds = if (isSubpage && !switchesSection && (progress < 1f || shift != 0)) {
                val clipLeft = (-shift).coerceIn(0, paneWidth)
                val clipRight = (width - panes.archiveLeft - shift).coerceIn(clipLeft, paneWidth)
                Rect(clipLeft, 0, clipRight, paneHeight)
            } else null
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = transitioning || super.onInterceptTouchEvent(event)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val panes = if (split) SettingsPageFrames.splitPanes(w, dp(SPLIT_GAP_DP), 0f) else null
        val left = panes?.rootWidth ?: w
        rootPage.measure(exact(left), exact(h))
        childHost.measure(exact(w - (panes?.secondaryLeft ?: 0)), exact(h))
        setMeasuredDimension(w, h)
    }

    @android.annotation.SuppressLint("DrawAllocation") // 几何变化时重算纯帧，未持有延迟布局回调。
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        rootPage.layout(0, 0, rootPage.measuredWidth, b - t)
        val childLeft = if (split) rootPage.measuredWidth + dp(SPLIT_GAP_DP) else 0
        childHost.layout(childLeft, 0, r - l, b - t)
        if (transitioning) renderFrame(TransitionProgress(runner.progress, true))
    }

    private fun updateAccessibility() {
        rootPage.importantForAccessibility = if (!transitioning && (split || current.section == null)) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        childHost.importantForAccessibility = if (!transitioning && current.section != null) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rootPage.descendantFocusability = if (!transitioning && (split || current.section == null)) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        childHost.descendantFocusability = if (!transitioning && current.section != null) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    private fun updatePadding() = rootPage.setContentMargins(margin, split)

    private fun buildPage(page: Page): View = when (val subpage = page.subpage) {
        is SyncSubpage.Archive -> ScoreArchivePage(context, subpage.kind, margin, split,
            scoreArchiveQueries(context), scoreArchiveActions(context)) { closeSubpage() }
        SyncSubpage.Logs -> ScoreSyncLogPage(context, margin, split, scoreSyncLogs(context)) { closeSubpage() }
        is SyncSubpage.Proxy -> ProxySettingsPage(context, margin, split, subpage.section) { closeSubpage() }
        null -> buildChild(requireNotNull(page.section))
    }

    private fun buildChild(section: SettingsSection) = SettingsPages.child(
        context = context,
        section = section,
        margin = margin,
        split = split,
        openLink = onOpenLink,
        openArchive = { goTo(Page(SettingsSection.ScoreSync, SyncSubpage.Archive(it))) },
        openLogs = { goTo(Page(SettingsSection.ScoreSync, SyncSubpage.Logs)) },
        openProxy = { goTo(Page(SettingsSection.ScoreSync, SyncSubpage.Proxy(it))) },
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
    private fun reset(view: View) {
        view.clipBounds = null
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun paneHeight(view: View, drawHeight: Int) =
        if (view is EdgeToEdgeScrollable) drawHeight else (drawHeight - bottomSafeInset).coerceAtLeast(0)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)

    private companion object {
        const val SPLIT_GAP_DP = 12
    }
}
