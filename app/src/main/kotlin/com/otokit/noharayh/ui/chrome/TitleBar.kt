package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.chrome.TitleAppearanceTransitionRunner
import com.otokit.noharayh.ui.animation.chrome.TitleTextTransitionRunner
import com.otokit.noharayh.ui.animation.chrome.TitleTransitionFrames
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.constants.motion.NavTitleTransition
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.theme.GameLine
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 标题栏：圆形菜单按钮 + 中英双行标题 + 右侧装饰三角。
 *
 * 布局、文案与 LM 坐标归本类；动画和临时资源归 animation/chrome 的执行器。
 * 只消费 [TitleBarState] 的纯数据和外部返回进度，
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

    private val textMotion = TitleTextTransitionRunner(
        titleHost, titleMotionHost, functionalHost, settingsColumn, maiStack, chuStack,
        refreshPivots = { refreshFunctionalPivot(); refreshSettingsPivot() },
        refreshStackPivot = ::refreshPivot,
    )
    private val appearanceMotion = TitleAppearanceTransitionRunner(
        iconLayer, outgoingIconLayer,
        applyCircleColor = { menuButton.circleColor = it },
        applyDecorColors = decor::setColors,
    )

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
        if (!textMotion.backPreviewActive) refreshPivots(lastState)
    }

    /** 同值幂等：更新文案、颜色、图标与许可；只有文案/形态变化才启动过渡。 */
    fun render(state: TitleBarState) {
        lastState = state
        appearanceMotion.updateIcon(iconFor(state.primaryAction))
        appearanceMotion.updateDecor(state.maiDecorColor, state.chuDecorColor)

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
            textMotion.settleSettings(state.settingsOpen)
        } else if (textChanged) {
            if (state.settingsOpen) {
                // 设置态不播放功能标题转场，更新后由下次退出直接呈现。
                setFunctionalTitles(state)
                displayedMaiTitle = state.maiTitle
                displayedChuTitle = state.chuTitle
            } else {
                val direction = if (state.navDirection == 0) 1 else state.navDirection
                runNavTransition(state, direction)
            }
        }

        if (displayedSettingsOpen == null) displayedSettingsOpen = state.settingsOpen
        if (settingsStateChanged && !initial) {
            textMotion.cancelNavigation()
            val open = state.settingsOpen
            displayedSettingsOpen = open
            settingsColumn.setTexts(state.settingTitle)
            if (textMotion.backPreviewActive) {
                // 预测返回的 finish/cancel 由外部进度决定，状态切换只更新真实层内容。
                textMotion.endBackPreview()
            }
            if (!textMotion.animateSettings(open)) textMotion.settleSettings(open)
        } else if (!textMotion.backPreviewActive && !textMotion.settingsAnimating) {
            textMotion.settleSettings(state.settingsOpen)
        }

        textMotion.applyGameFrame(state.gameFrame, state.currentGame)
        applyTextColors(state)
        setColumnColor(settingsColumn, state.settingTextColor)
        appearanceMotion.updateCircleColor(circleColorFor(state), animate = state.settingsOpen || state.gameFrame?.phase == GamePhase.Idle)
        menuButton.contentDescription = context.getString(
            if (state.primaryAction == TitleBarPrimaryAction.Back) R.string.title_back
            else R.string.title_open_navigation_menu,
        )
        menuButton.isEnabled = state.primaryEnabled
        menuButton.alpha = if (state.primaryEnabled) 1f else DISABLED_ALPHA
        lastPrimaryAction = state.primaryAction
    }

    /** 背景帧单独更新，避免每帧重建标题状态或触碰导航。 */
    fun renderGameFrame(frame: GameFrame) {
        val state = lastState ?: return
        if (state.gameFrame == frame) return
        lastState = state.copy(gameFrame = frame)
        val updated = lastState ?: return
        textMotion.applyGameFrame(updated.gameFrame, updated.currentGame)
        applyTextColors(updated)
        appearanceMotion.updateCircleColor(circleColorFor(updated), animate = false)
    }

    /** 平台返回进度只交给文字执行器，不提交导航状态。 */
    fun beginBackPreview() {
        if (lastState != null) textMotion.beginBackPreview()
    }

    fun updateBackProgress(progress: Float) {
        if (!textMotion.backPreviewActive) return
        textMotion.updateBackProgress(progress)
        applyTextColors(lastState ?: return)
    }

    fun cancelBackPreview() = textMotion.cancelBackPreview(lastState?.settingsOpen == true)

    fun endBackPreview() = textMotion.endBackPreview()

    fun cancelPressFeedback() = menuButton.cancelFeedback()

    /** 供宿主在旧焦点已失效时回到主操作入口，不暴露内部按钮。 */
    fun requestPrimaryFocus(): Boolean = menuButton.requestFocus()

    /** 终态：解除回调、动画、ghost 与布局监听。 */
    fun dispose() {
        textMotion.dispose()
        appearanceMotion.dispose()
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

    private fun setFunctionalTitles(state: TitleBarState) {
        maiColumn.setTexts(state.maiTitle)
        chuColumn.setTexts(state.chuTitle)
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


    private fun runNavTransition(state: TitleBarState, direction: Int) {
        val dy = min(
            metrics.rowHeightPx * NavTitleTransition.TRANSLATION_ROW_HEIGHT_FRACTION,
            NavTitleTransition.TRANSLATION_MAX_DP * resources.displayMetrics.density,
        ) * direction
        textMotion.runNavigation(dy) {
            setFunctionalTitles(state)
            displayedMaiTitle = state.maiTitle
            displayedChuTitle = state.chuTitle
        }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.55f
    }
}
