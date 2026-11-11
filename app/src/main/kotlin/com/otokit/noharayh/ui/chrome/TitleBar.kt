package com.otokit.noharayh.ui.chrome

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.chrome.TitleTransitionFrames
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.constants.motion.ChromeIconTransition
import com.otokit.noharayh.ui.constants.motion.ChromeThemeTransition
import com.otokit.noharayh.ui.constants.motion.NavTitleTransition
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTitleTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.theme.GameLine
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 标题栏：圆形菜单按钮 + 中英双行标题 + 右侧装饰三角。
 *
 * 布局、LM 锚点、ghost 生成和局部动画对象都留在本类；只消费 [TitleBarState] 的纯数据和外部返回进度，
 * 不读导航/选择状态、不查 Registry、不写偏好。只在 Compact 形态挂载。
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
    private val iconLayer = ImageView(context)
    private val outgoingIconLayer = ImageView(context)
    private val halo = TitleButtonHalo(context, menuButton.feedback)

    private val titleHost = FrameLayout(context)
    private val titleMotionHost = FrameLayout(context)
    private val functionalHost = FrameLayout(context)
    private val maiStack = FrameLayout(context)
    private val chuStack = FrameLayout(context)
    private val maiColumn = newColumn()
    private val chuColumn = newColumn()
    private val settingsColumn = newColumn()

    private val maiEnglish = newLine()
    private val maiChinese = newLine()
    private val chuEnglish = newLine()
    private val chuChinese = newLine()
    private val settingsEnglish = newLine()
    private val settingsChinese = newLine()

    private var lastReportedHeight = -1
    private var lastPrimaryAction: TitleBarPrimaryAction? = null
    private var lastState: TitleBarState? = null
    private var displayedMaiTitle: TitleText? = null
    private var displayedChuTitle: TitleText? = null
    private var displayedSettingsOpen: Boolean? = null
    private var displayedGame: GameLine? = null
    private var displayedIconAction: TitleBarPrimaryAction? = null
    private var displayedMaiDecorColor: Int? = null
    private var displayedChuDecorColor: Int? = null

    private var navAnimator: ValueAnimator? = null
    private var settingsAnimator: ValueAnimator? = null
    private var iconAnimator: ValueAnimator? = null
    private var iconGeneration = 0
    private var decorAnimator: ValueAnimator? = null
    private var circleColorAnimator: ValueAnimator? = null
    private var displayedCircleColor: Int? = null
    private var circleColorGeneration = 0
    private var ghost: ImageView? = null
    private var backPreviewActive = false
    /** 0=功能态，1=设置态；手势与普通转场共用当前呈现进度。 */
    private var settingsProgress = 0f

    private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val height = height
        if (height > 0 && height != lastReportedHeight) {
            lastReportedHeight = height
            onHeightChanged?.invoke(height)
        }
    }

    var onPrimaryAction: (() -> Unit)? = null
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

        @SuppressLint("RtlHardcoded")
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
        menuButton.onFeedbackFrame = { halo.invalidate() }
        menuHost.addView(menuButton)

        // 图标单独一层，交叉淡变时不改变圆底/按压/halo 的 alpha 或 scale。
        iconLayer.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        iconLayer.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iconLayer.setPadding(
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
        )
        iconLayer.imageTintList = ColorStateList.valueOf(Color.WHITE)
        iconLayer.isClickable = false
        iconLayer.isFocusable = false
        iconLayer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        outgoingIconLayer.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        outgoingIconLayer.scaleType = ImageView.ScaleType.CENTER_INSIDE
        outgoingIconLayer.setPadding(
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
            metrics.iconPaddingPx,
        )
        outgoingIconLayer.imageTintList = ColorStateList.valueOf(Color.WHITE)
        outgoingIconLayer.isClickable = false
        outgoingIconLayer.isFocusable = false
        outgoingIconLayer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        outgoingIconLayer.visibility = View.INVISIBLE
        outgoingIconLayer.alpha = 0f
        menuHost.addView(outgoingIconLayer)
        menuHost.addView(iconLayer)
        addView(menuHost)

        columnSetup(maiColumn, maiEnglish, maiChinese)
        columnSetup(chuColumn, chuEnglish, chuChinese)
        columnSetup(settingsColumn, settingsEnglish, settingsChinese)
        settingsColumn.visibility = View.GONE

        maiStack.addView(
            maiColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        chuStack.addView(
            chuColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        functionalHost.addView(
            maiStack,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        functionalHost.addView(
            chuStack,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        titleMotionHost.addView(
            functionalHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )
        titleMotionHost.addView(
            settingsColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )
        titleHost.addView(
            titleMotionHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                leftMargin = metrics.titleAnchorStartPx
                gravity = Gravity.TOP or Gravity.LEFT
            },
        )
        // 标题只被整个标题栏裁剪；中间容器不能按文字盒子裁剪，否则文字上下/左右过渡会在
        // 文字边缘被切断，也进不到汉堡键下面。
        titleHost.clipChildren = false
        titleHost.clipToPadding = false
        titleMotionHost.clipChildren = false
        functionalHost.clipChildren = false
        maiStack.clipChildren = false
        chuStack.clipChildren = false
        maiColumn.clipChildren = false
        chuColumn.clipChildren = false
        settingsColumn.clipChildren = false
        // 标题宿主扩展到整个标题栏高度、从 0 开始；文案自身仍从 titleAnchorStart 开始。
        // 这样上下/左右过渡只被标题栏裁剪，可以进入汉堡键下面。
        addView(
            titleHost,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.TOP or Gravity.LEFT,
            ).apply {
                rightMargin = metrics.buttonSizePx
            },
        )


        // 文字滑到左侧时压在圆底下面，而不是盖住汉堡键；halo 仍在按钮下、文字上。
        bringChildToFront(halo)
        bringChildToFront(menuHost)

        addOnLayoutChangeListener(layoutListener)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        halo.setButtonBounds(menuHost.left, menuHost.top, menuHost.right, menuHost.bottom)
        if (!backPreviewActive) refreshPivots(lastState)
    }

    /** 同值幂等：更新文案、颜色、图标与许可；只有文案/形态变化才启动过渡。 */
    fun render(state: TitleBarState) {
        val previous = lastState
        lastState = state
        updatePrimary(state)
        updateDecor(state)

        val textChanged = displayedMaiTitle != null && displayedChuTitle != null &&
            (state.maiTitle != displayedMaiTitle || state.chuTitle != displayedChuTitle)
        val initial = displayedMaiTitle == null || displayedChuTitle == null
        val settingsStateChanged = displayedSettingsOpen != state.settingsOpen

        if (initial) {
            setFunctionalTitles(state)
            displayedMaiTitle = state.maiTitle
            displayedChuTitle = state.chuTitle
            displayedSettingsOpen = state.settingsOpen
            settingsColumn.setTexts(state.settingTitle)
            settleLayers(state.settingsOpen)
        } else if (textChanged) {
            if (state.settingsOpen) {
                // 设置态不播放功能标题转场，更新后由下次退出直接呈现。
                setFunctionalTitles(state)
                displayedMaiTitle = state.maiTitle
                displayedChuTitle = state.chuTitle
            } else {
                if (navAnimator != null) {
                    cancelNavTransition()
                }
                val direction = if (state.navDirection == 0) 1 else state.navDirection
                runNavTransition(state, direction)
            }
        }

        if (displayedSettingsOpen == null) displayedSettingsOpen = state.settingsOpen
        if (settingsStateChanged && !initial) {
            cancelNavTransition()
            val open = state.settingsOpen
            displayedSettingsOpen = open
            settingsColumn.setTexts(state.settingTitle)
            if (backPreviewActive) {
                // 预测返回的 finish/cancel 由外部进度决定，状态切换只更新真实层内容。
                backPreviewActive = false
            }
            if (!runSettingsTransition(open)) settleLayers(open)
        } else if (!backPreviewActive && settingsAnimator == null) {
            settleLayers(state.settingsOpen)
        }

        applyFunctionalFrame(state)
        applyTextColors(state)
        setColumnColor(settingsColumn, state.settingTextColor)
        updateCircleColor(circleColorFor(state), animate = state.settingsOpen || state.gameFrame?.phase == GamePhase.Idle)
        menuButton.contentDescription = context.getString(
            if (state.primaryAction == TitleBarPrimaryAction.Back) R.string.title_back
            else R.string.title_open_navigation_menu,
        )
        menuButton.isEnabled = state.primaryEnabled
        menuButton.alpha = if (state.primaryEnabled) 1f else DISABLED_ALPHA
        lastPrimaryAction = state.primaryAction
        if (previous == null) {
            displayedIconAction = state.primaryAction
            iconLayer.setImageResource(iconFor(state.primaryAction))
            iconLayer.alpha = 1f
        }
    }

    /** 背景帧单独更新，避免每帧重建标题状态或触碰导航。 */
    fun renderGameFrame(frame: GameFrame) {
        val state = lastState ?: return
        if (state.gameFrame == frame) return
        lastState = state.copy(gameFrame = frame)
        val updated = lastState ?: return
        applyFunctionalFrame(updated)
        applyTextColors(updated)
        updateCircleColor(circleColorFor(updated), animate = false)
    }

    /** 预测返回：0=完全设置态，1=完全功能态；调用方只给进度，不提交任何状态。 */
    fun beginBackPreview() {
        if (lastState == null) return
        cancelNavTransition()
        cancelSettingsTransition()
        backPreviewActive = true
        // 预览进度 0=设置态；内部设置进度 1=设置态。
        applySettingsProgress(1f, animate = false)
    }

    fun updateBackProgress(progress: Float) {
        if (!backPreviewActive) return
        applySettingsProgress(1f - progress.coerceIn(0f, 1f), animate = false)
        applyTextColors(lastState ?: return)
    }

    /** 取消预览：回到当前稳定设置态；不调用 closeSettings。 */
    fun cancelBackPreview() {
        if (!backPreviewActive) return
        backPreviewActive = false
        animateSettingsTo(if (lastState?.settingsOpen == true) 1f else 0f)
    }

    fun endBackPreview() {
        backPreviewActive = false
        // 保留预览最后一帧，已提交状态的退出动画从这里继续。
    }

    fun cancelPressFeedback() = menuButton.cancelFeedback()

    /** 供宿主在旧焦点已失效时回到主操作入口，不暴露内部按钮。 */
    fun requestPrimaryFocus(): Boolean = menuButton.requestFocus()

    /** 终态：解除回调、动画、ghost 与布局监听。 */
    fun dispose() {
        cancelNavTransition()
        cancelSettingsTransition()
        iconAnimator?.cancel()
        iconAnimator = null
        decorAnimator?.cancel()
        decorAnimator = null
        circleColorAnimator?.cancel()
        circleColorAnimator = null
        clearGhost()
        menuButton.cancelFeedback()
        menuButton.onFeedbackFrame = null
        onPrimaryAction = null
        onHeightChanged = null
        removeOnLayoutChangeListener(layoutListener)
        menuButton.setOnClickListener(null)
    }

    // region layout helpers

    private fun newColumn(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        translationY = metrics.opticalNudgePx
    }

    private fun newLine(): AppCompatTextView = AppCompatTextView(context).apply {
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
        setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.englishTextSizePx)
        AppTypography.applyTitleBarRogSans(this)
    }

    private fun columnSetup(column: LinearLayout, english: AppCompatTextView, chinese: AppCompatTextView) {
        english.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.englishTextSizePx)
            AppTypography.applyTitleBarRogSans(this)
        }
        chinese.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.chineseTextSizePx)
            AppTypography.applyAppTypeface(this, bold = true)
        }
        column.addView(
            english,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        column.addView(
            chinese,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = metrics.lineGapPx },
        )
    }

    private fun LinearLayout.setTexts(text: TitleText) {
        val english: AppCompatTextView
        val chinese: AppCompatTextView
        when (this) {
            maiColumn -> {
                english = maiEnglish
                chinese = maiChinese
            }

            chuColumn -> {
                english = chuEnglish
                chinese = chuChinese
            }

            else -> {
                english = settingsEnglish
                chinese = settingsChinese
            }
        }
        english.setText(text.english)
        chinese.setText(text.chinese)
    }

    // endregion

    private fun updatePrimary(state: TitleBarState) {
        val action = state.primaryAction
        if (displayedIconAction == null) {
            displayedIconAction = action
            iconLayer.setImageResource(iconFor(action))
            applyIconFrame(iconLayer, TransitionFrame(1f, 1f, 0f, 0f))
            outgoingIconLayer.visibility = View.INVISIBLE
            outgoingIconLayer.alpha = 0f
            return
        }
        if (displayedIconAction == action) return
        val previous = displayedIconAction ?: action
        displayedIconAction = action
        if (!animationsEnabled()) {
            iconGeneration++
            iconAnimator?.cancel()
            iconAnimator = null
            iconLayer.setImageResource(iconFor(action))
            applyIconFrame(iconLayer, TransitionFrame(1f, 1f, 0f, 0f))
            outgoingIconLayer.visibility = View.INVISIBLE
            outgoingIconLayer.alpha = 0f
            return
        }

        iconGeneration++
        val generation = iconGeneration
        iconAnimator?.cancel()
        iconAnimator = null
        // 双层交叉淡变：旧图标保持可见并淡出，新图标直接叠在上面淡入，不出现空白帧。
        outgoingIconLayer.setImageResource(iconFor(previous))
        outgoingIconLayer.visibility = View.VISIBLE
        outgoingIconLayer.alpha = 1f
        iconLayer.setImageResource(iconFor(action))
        iconLayer.visibility = View.VISIBLE
        iconLayer.alpha = 0f

        iconAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ChromeIconTransition.DURATION_MS
            interpolator = MotionInterpolators.of(ChromeIconTransition.CURVE)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                applyIconFrame(outgoingIconLayer, TransitionFrames.exit(
                    kind = ChromeIconTransition.KIND,
                    progress = p,
                    scaleTo = ChromeIconTransition.SCALE_FROM,
                    translationYTo = -iconLayer.height * ChromeIconTransition.TRANSLATION_SIZE_FRACTION,
                ))
                applyIconFrame(iconLayer, TransitionFrames.enter(
                    kind = ChromeIconTransition.KIND,
                    progress = p,
                    scaleFrom = ChromeIconTransition.SCALE_FROM,
                    translationYFrom = iconLayer.height * ChromeIconTransition.TRANSLATION_SIZE_FRACTION,
                ))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (generation != iconGeneration) return
                    outgoingIconLayer.alpha = 0f
                    outgoingIconLayer.visibility = View.INVISIBLE
                    iconLayer.setImageResource(iconFor(action))
                    applyIconFrame(iconLayer, TransitionFrame(1f, 1f, 0f, 0f))
                    if (iconAnimator === animation) iconAnimator = null
                }
            })
            start()
        }
    }
    private fun applyIconFrame(view: View, frame: TransitionFrame) {
        view.alpha = frame.alpha
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
    }
    private fun iconFor(action: TitleBarPrimaryAction): Int =
        if (action == TitleBarPrimaryAction.Back) R.drawable.ic_arrow_back_24 else R.drawable.ic_menu_24

    private fun circleColorFor(state: TitleBarState): Int {
        if (state.settingsOpen) return state.circleColor
        val progress = state.gameFrame?.normalizedProgress
            ?: if (state.currentGame == GameLine.Mai) 0f else 1f
        return when {
            progress <= 0f -> state.maiTextColor
            progress >= 1f -> state.chuTextColor
            else -> ColorUtils.blendARGB(state.maiTextColor, state.chuTextColor, progress)
        }
    }

    private fun updateCircleColor(target: Int, animate: Boolean) {
        val current = displayedCircleColor
        displayedCircleColor = target
        if (current == null || current == target) {
            circleColorGeneration++
            circleColorAnimator?.cancel()
            circleColorAnimator = null
            menuButton.circleColor = target
            return
        }
        if (!animate || !animationsEnabled()) {
            circleColorGeneration++
            circleColorAnimator?.cancel()
            circleColorAnimator = null
            menuButton.circleColor = target
            return
        }
        circleColorGeneration++
        val generation = circleColorGeneration
        circleColorAnimator?.cancel()
        val evaluator = ArgbEvaluator()
        circleColorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ChromeThemeTransition.DURATION_MS
            interpolator = MotionInterpolators.of(ChromeThemeTransition.CURVE)
            addUpdateListener { animation ->
                if (generation != circleColorGeneration) return@addUpdateListener
                menuButton.circleColor = evaluator.evaluate(
                    animation.animatedValue as Float,
                    current,
                    target,
                ) as Int
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (generation != circleColorGeneration) return
                    menuButton.circleColor = target
                    if (circleColorAnimator === animation) circleColorAnimator = null
                }
            })
            start()
        }
    }
    private fun updateDecor(state: TitleBarState) {
        val previousMai = displayedMaiDecorColor
        val previousChu = displayedChuDecorColor
        displayedMaiDecorColor = state.maiDecorColor
        displayedChuDecorColor = state.chuDecorColor
        if (previousMai == null || previousChu == null) {
            decor.setColors(state.maiDecorColor, state.chuDecorColor)
            return
        }
        if (previousMai == state.maiDecorColor && previousChu == state.chuDecorColor) return
        decorAnimator?.cancel()
        val evaluator = ArgbEvaluator()
        decorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ChromeThemeTransition.DURATION_MS
            interpolator = MotionInterpolators.of(ChromeThemeTransition.CURVE)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                decor.setColors(
                    evaluator.evaluate(p, previousMai, state.maiDecorColor) as Int,
                    evaluator.evaluate(p, previousChu, state.chuDecorColor) as Int,
                )
            }
            start()
        }
    }

    private fun setFunctionalTitles(state: TitleBarState) {
        maiColumn.setTexts(state.maiTitle)
        chuColumn.setTexts(state.chuTitle)
    }

    private fun applyFunctionalFrame(state: TitleBarState) {
        val frame = state.gameFrame
        val progress = frame?.normalizedProgress
            ?: if (state.currentGame == GameLine.Mai) 0f else 1f
        val phase = frame?.phase ?: GamePhase.Idle
        val selectedMai = state.currentGame == GameLine.Mai || progress < 0.5f

        if (phase == GamePhase.Idle) {
            showStack(maiStack, if (selectedMai) View.VISIBLE else View.GONE)
            showStack(chuStack, if (selectedMai) View.GONE else View.VISIBLE)
            resetStack(maiStack)
            resetStack(chuStack)
            return
        }

        val width = titleHost.width.coerceAtLeast(1)
        val pair = TransitionFrames.pagerPair(
            kind = PagerPairTransition.KIND,
            progress = progress,
            slideDistancePx = width * PagerPairTransition.TRANSLATION_RATIO,
            minScale = PagerPairTransition.MIN_SCALE,
            minAlpha = PagerPairTransition.MIN_ALPHA,
        )
        applyStackFrame(maiStack, pair.first)
        applyStackFrame(chuStack, pair.second)
        maiStack.visibility = View.VISIBLE
        chuStack.visibility = View.VISIBLE
    }

    private fun showStack(stack: FrameLayout, visibility: Int) {
        stack.visibility = visibility
        if (visibility == View.VISIBLE) refreshPivot(stack)
    }

    private fun resetStack(stack: FrameLayout) {
        stack.alpha = 1f
        stack.scaleX = 1f
        stack.scaleY = 1f
        stack.translationX = 0f
        stack.translationY = 0f
    }

    private fun applyStackFrame(stack: FrameLayout, frame: com.otokit.noharayh.ui.animation.common.TransitionFrame) {
        refreshPivot(stack)
        stack.alpha = frame.alpha
        stack.scaleX = frame.scale
        stack.scaleY = frame.scale
        stack.translationX = frame.translationX
        stack.translationY = frame.translationY
    }

    private fun refreshPivot(stack: FrameLayout) {
        stack.pivotX = 0f
        stack.pivotY = stack.height / 2f
    }

    private fun applyTextColors(state: TitleBarState) {
        if (state.settingsOpen) {
            setColumnColor(settingsColumn, state.settingTextColor)
            return
        }
        val frame = state.gameFrame
        val progress = frame?.normalizedProgress
            ?: if (state.currentGame == GameLine.Mai) 0f else 1f
        val color = if (progress <= 0f) state.maiTextColor
        else if (progress >= 1f) state.chuTextColor
        else ColorUtils.blendARGB(state.maiTextColor, state.chuTextColor, progress)
        maiEnglish.setTextColor(color)
        maiChinese.setTextColor(color)
        chuEnglish.setTextColor(color)
        chuChinese.setTextColor(color)
    }

    private fun settleLayers(settingsOpen: Boolean) {
        cancelSettingsTransition()
        if (settingsOpen) {
            applySettingsProgress(1f, animate = false)
        } else {
            applySettingsProgress(0f, animate = false)
        }
    }

    private fun applySettingsProgress(progress: Float, animate: Boolean) {
        val p = progress.coerceIn(0f, 1f)
        settingsProgress = p
        refreshFunctionalPivot()
        refreshSettingsPivot()
        val exit = TransitionFrames.exit(
            kind = SettingsTitleTransition.KIND,
            progress = p,
            scaleTo = SettingsTitleTransition.SCALE_SHRINK,
        )
        functionalHost.visibility = if (p >= 1f) View.INVISIBLE else View.VISIBLE
        functionalHost.alpha = exit.alpha
        functionalHost.scaleX = exit.scale
        functionalHost.scaleY = exit.scale

        val enter = TransitionFrames.enter(
            kind = SettingsTitleTransition.KIND,
            progress = p,
            scaleFrom = SettingsTitleTransition.SCALE_EXPAND_FROM,
        )
        settingsColumn.visibility = if (p <= 0f) View.INVISIBLE else View.VISIBLE
        settingsColumn.alpha = enter.alpha
        settingsColumn.scaleX = enter.scale
        settingsColumn.scaleY = enter.scale
    }

    private fun refreshFunctionalPivot() {
        functionalHost.pivotX = 0f
        functionalHost.pivotY = pivotYFor(
            functionalHost,
            maiEnglish,
            maiChinese,
            chuEnglish,
            chuChinese,
        )
    }

    private fun refreshSettingsPivot() {
        settingsColumn.pivotX = 0f
        settingsColumn.pivotY = pivotYFor(settingsColumn, settingsEnglish, settingsChinese)
    }

    private fun setColumnColor(column: LinearLayout, color: Int) {
        for (index in 0 until column.childCount) {
            (column.getChildAt(index) as? AppCompatTextView)?.setTextColor(color)
        }
    }

    /** 两行及以上文字的真实布局并集中心；不按单行或容器中心取锚点。 */
    private fun pivotYFor(host: ViewGroup, vararg lines: View): Float {
        val union = Rect()
        var hasLine = false
        for (line in lines) {
            if (line.width <= 0 || line.height <= 0) continue
            val rect = Rect()
            host.offsetDescendantRectToMyCoords(line, rect)
            if (rect.width() <= 0 || rect.height() <= 0) continue
            if (!hasLine) {
                union.set(rect)
                hasLine = true
            } else {
                union.union(rect)
            }
        }
        return if (hasLine) {
            TitleTransitionFrames.lmPivotY(union.top.toFloat(), union.bottom.toFloat())
        } else {
            host.height / 2f
        }
    }
    private fun refreshPivots(state: TitleBarState?) {
        if (state == null) return
        refreshFunctionalPivot()
        refreshSettingsPivot()
    }

    private fun runSettingsTransition(open: Boolean): Boolean {
        if (!animationsEnabled() || width <= 0) return false
        cancelSettingsTransition()
        val from = settingsProgress
        val to = if (open) 1f else 0f
        settingsAnimator = ValueAnimator.ofFloat(from, to).apply {
            val fullDuration = if (open) SettingsTransition.ENTER_DURATION_MS else SettingsTransition.EXIT_DURATION_MS
            duration = (fullDuration * kotlin.math.abs(to - from)).toLong().coerceAtLeast(1L)
            interpolator = MotionInterpolators.of(SettingsTitleTransition.CURVE)
            addUpdateListener { animation ->
                applySettingsProgress(animation.animatedValue as Float, animate = true)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (settingsAnimator === animation) {
                        settingsAnimator = null
                        settleLayers(open)
                    }
                }
            })
            start()
        }
        return true
    }

    private fun animateSettingsTo(target: Float) {
        cancelSettingsTransition()
        if (!animationsEnabled()) {
            applySettingsProgress(target, animate = false)
            return
        }
        settingsAnimator = ValueAnimator.ofFloat(settingsProgress, target).apply {
            duration = if (target >= 1f) SettingsTransition.ENTER_DURATION_MS else SettingsTransition.EXIT_DURATION_MS
            interpolator = MotionInterpolators.of(SettingsTitleTransition.CURVE)
            addUpdateListener { animation ->
                applySettingsProgress(animation.animatedValue as Float, animate = true)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (settingsAnimator === animation) {
                        settingsAnimator = null
                        applySettingsProgress(target, animate = false)
                    }
                }
            })
            start()
        }
    }

    private fun cancelSettingsTransition() {
        val previous = settingsAnimator
        settingsAnimator = null
        previous?.cancel()
    }

    private fun runNavTransition(state: TitleBarState, direction: Int) {
        val ghostView = captureGhost()
        val dy = min(
            metrics.rowHeightPx * NavTitleTransition.TRANSLATION_ROW_HEIGHT_FRACTION,
            NavTitleTransition.TRANSLATION_MAX_DP * resources.displayMetrics.density,
        ) * direction
        setFunctionalTitles(state)
        displayedMaiTitle = state.maiTitle
        displayedChuTitle = state.chuTitle

        if (!animationsEnabled() || ghostView == null) {
            titleMotionHost.translationY = 0f
            titleMotionHost.alpha = 1f
            clearGhost()
            return
        }
        titleMotionHost.alpha = 0f
        titleMotionHost.translationY = dy
        val entryEnd = TransitionFrames.enter(
            kind = NavTitleTransition.KIND,
            progress = 1f,
            translationYFrom = dy,
        )
        val ghostEnd = TransitionFrames.exit(
            kind = NavTitleTransition.KIND,
            progress = 1f,
            translationYTo = -dy,
        )
        navAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NavTitleTransition.DURATION_MS
            interpolator = MotionInterpolators.of(NavTitleTransition.CURVE)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                val entry = TransitionFrames.enter(
                    kind = NavTitleTransition.KIND,
                    progress = p,
                    translationYFrom = dy,
                )
                val exit = TransitionFrames.exit(
                    kind = NavTitleTransition.KIND,
                    progress = p,
                    translationYTo = -dy,
                )
                titleMotionHost.alpha = entry.alpha
                titleMotionHost.translationY = entry.translationY
                titleMotionHost.scaleX = entry.scale
                titleMotionHost.scaleY = entry.scale
                ghostView.alpha = exit.alpha
                ghostView.translationY = exit.translationY
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (navAnimator === animation) {
                        navAnimator = null
                        titleMotionHost.alpha = 1f
                        titleMotionHost.translationY = 0f
                        clearGhost()
                    }
                }
            })
            start()
        }
    }

    private fun cancelNavTransition() {
        navAnimator?.cancel()
        navAnimator = null
        titleMotionHost.alpha = 1f
        titleMotionHost.translationY = 0f
        clearGhost()
    }

    private fun captureGhost(): ImageView? {
        clearGhost()
        val width = titleHost.width
        val height = titleHost.height
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        try {
            titleHost.draw(Canvas(bitmap))
        } catch (_: Throwable) {
            bitmap.recycle()
            return null
        }
        val image = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            pivotX = 0f
            pivotY = height / 2f
        }
        titleHost.addView(
            image,
            LayoutParams(width, height),
        )
        ghost = image
        return image
    }
    private fun clearGhost() {
        val current = ghost ?: return
        ghost = null
        titleHost.removeView(current)
        val drawable = current.drawable as? BitmapDrawable
        current.setImageDrawable(null)
        drawable?.bitmap?.recycle()
    }

    private fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    private companion object {
        const val DISABLED_ALPHA = 0.55f
    }
}
