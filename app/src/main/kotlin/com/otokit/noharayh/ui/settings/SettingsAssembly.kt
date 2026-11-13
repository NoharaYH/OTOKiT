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
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.settings.subPages.PersonalizationSettingsPage
import com.otokit.noharayh.ui.settings.subPages.AppSettingsPage
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeSelectionMode

/** 设置展示宿主。section 只是最后渲染值；所有稳定页面命令交回导航控制器。 */
@android.annotation.SuppressLint("ViewConstructor") // 由主壳传入明确事件构造，不提供 XML 入口。
class SettingsAssembly(
    context: Context,
    private val onOpenSection: (SettingsSection) -> Unit,
    private val onOpenLink: (String) -> Unit,
    private val displayScale: DisplayScaleOption,
    private val onDisplayScaleSelected: (DisplayScaleOption) -> Unit,
    private val renderingProvider: () -> BackgroundRenderingOptions,
    private val onHardwareAccelerationSelected: (Boolean) -> Unit,
    private val onHtmlAnimationsSelected: (Boolean) -> Unit,
    private val takeHardwareWarning: () -> Boolean,
    private val selectionProvider: () -> GameSelectionState,
    private val onThemeModeSelected: (ThemeSelectionMode) -> Unit,
    private val onThemeOptionSelected: (ThemeOption, GameLine?) -> Unit,
    private val onTransitionStart: (SettingsSection?, SettingsSection?, Float, Boolean) -> Unit,
    private val onTransitionFrame: (TransitionProgress) -> Unit,
) : FrameLayout(context) {
    private val rootPage = SettingsHome(context, onOpenSection)
    private val childHost = FrameLayout(context)
    private val divider = View(context).apply { setBackgroundColor(Color.WHITE) }
    private val options = rootPage.options
    private var renderedSection: SettingsSection? = null
    private var fromSection: SettingsSection? = null
    private var toSection: SettingsSection? = null
    private var fromView: View? = null
    private var toView: View? = null
    private var transitioning = false
    private var unfold = false
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

    fun renderThemeSelection(selection: GameSelectionState) {
        if (disposed) return
        for (index in 0 until childHost.childCount) {
            (childHost.getChildAt(index) as? PersonalizationSettingsPage)?.renderSelection(selection)
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
        settle(renderedSection, rebuild = true)
        requestLayout()
    }

    fun render(section: SettingsSection?) {
        if (disposed) return
        if (transitioning && toSection == section) {
            renderedSection = section
            runner.play(1f) { settle(section) }
        } else if (renderedSection != section) {
            settle(renderedSection)
            prepare(renderedSection, section)
            renderedSection = section
            runner.play(1f) { settle(section) }
        }
    }

    private fun personalizationPage(): PersonalizationSettingsPage? =
        if (renderedSection == SettingsSection.Personalization && !transitioning)
            childHost.getChildAt(childHost.childCount - 1) as? PersonalizationSettingsPage else null

    fun closeThemePanel(): Boolean = personalizationPage()?.closeThemePanel() == true
    fun beginThemePanelBack(): Boolean = personalizationPage()?.beginThemePanelBack() == true
    fun previewThemePanelBack(progress: Float) { personalizationPage()?.previewThemePanelBack(progress) }
    fun cancelThemePanelBack() { personalizationPage()?.cancelThemePanelBack() }
    fun finishThemePanelBack() { personalizationPage()?.finishThemePanelBack() }

    fun beginBackPreview(): Boolean {
        val current = renderedSection ?: return false
        settle(current)
        prepare(current, null)
        return true
    }
    fun preview(progress: Float) { if (transitioning) runner.preview(progress) }
    fun cancelPreview() {
        if (!transitioning) return
        val restore = renderedSection
        runner.play(0f) { settle(restore) }
    }

    /** 窗口/生命周期接管：无回调提交，无延迟布局任务。 */
    fun settle(section: SettingsSection?, rebuild: Boolean = false) {
        if (disposed) return
        runner.stop()
        if (transitioning) onTransitionFrame(TransitionProgress(if (section == toSection) 1f else 0f, false))
        transitioning = false
        fromView = null
        toView = null
        val keep = if (!rebuild && section == renderedSection && childHost.childCount > 0) childHost.getChildAt(childHost.childCount - 1) else null
        childHost.removeAllViews()
        if (section != null) childHost.addView(keep ?: buildChild(section), LayoutParams(-1, -1))
        renderedSection = section
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

    private fun prepare(from: SettingsSection?, to: SettingsSection?) {
        fromSection = from
        toSection = to
        fromView = if (from == null) rootPage else childHost.getChildAt(0)
        toView = if (to == null) rootPage else buildChild(to).also { childHost.addView(it, LayoutParams(-1, -1)) }
        rootPage.visibility = VISIBLE
        childHost.visibility = VISIBLE
        transitioning = true
        unfold = true
        runner.reset(unfold)
        onTransitionStart(from, to, width.toFloat(), unfold)
        renderFrame(TransitionProgress(0f, true))
        updateAccessibility()
    }

    private fun renderFrame(frame: TransitionProgress) {
        if (!transitioning || disposed) return
        val forward = toSection != null
        val distance = width.toFloat()
        val phoneUnfold = unfold && !split
        val pageProgress = if (phoneUnfold) SettingsPageFrames.phoneUnfoldProgress(frame.value, forward)
            else frame.value
        fromView?.let {
            if (!split || it !== rootPage) applyPageFrame(it, pageProgress, false, forward, distance)
        }
        toView?.let {
            if (!split || it !== rootPage) applyPageFrame(it, pageProgress, true, forward, distance)
        }
        val changed = if (phoneUnfold) SettingsPageFrames.phoneSelectionChange(frame.value, forward)
            else SettingsPageFrames.selectionChange(frame.value)
        options.forEach { (key, option) ->
            val start = if (key == fromSection) 1f else 0f
            val end = if (key == toSection) 1f else 0f
            option.setSelectionProgress(start + (end - start) * changed)
        }
        onTransitionFrame(TransitionProgress(pageProgress, frame.running))
    }

    private fun applyPageFrame(view: View, progress: Float, entering: Boolean, forward: Boolean, distance: Float) {
        val visual = if (unfold) SettingsPageFrames.unfoldPage(progress, entering, forward, resources.displayMetrics.density)
            else SettingsPageFrames.page(progress, entering, forward, split, distance)
        // 分栏中直接换子页时，旧子页完整退场；仅展开侧使用揭示裁剪。
        val reveal = if (unfold && view !== rootPage && entering == forward)
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
        rootPage.importantForAccessibility = if (!transitioning && (split || renderedSection == null)) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        childHost.importantForAccessibility = if (!transitioning && renderedSection != null) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        rootPage.descendantFocusability = if (!transitioning && (split || renderedSection == null)) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        childHost.descendantFocusability = if (!transitioning && renderedSection != null) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    private fun updatePadding() = rootPage.setContentMargins(margin, split)
    private fun buildChild(section: SettingsSection) = SettingsPages.child(
        context = context,
        section = section,
        margin = margin,
        split = split,
        openLink = onOpenLink,
        displayScale = displayScale,
        onDisplayScaleSelected = onDisplayScaleSelected,
        rendering = renderingProvider(),
        onHardwareAccelerationSelected = onHardwareAccelerationSelected,
        onHtmlAnimationsSelected = onHtmlAnimationsSelected,
        takeHardwareWarning = takeHardwareWarning,
        selection = selectionProvider(),
        onThemeModeSelected = {
            onThemeModeSelected(it)
            renderThemeSelection(selectionProvider())
        },
        onThemeOptionSelected = { option, game ->
            onThemeOptionSelected(option, game)
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
