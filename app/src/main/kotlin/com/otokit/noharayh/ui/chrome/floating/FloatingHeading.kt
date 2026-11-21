package com.otokit.noharayh.ui.chrome.floating

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.PageTurnFrames
import com.otokit.noharayh.ui.animation.common.SettingsPageFrames
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.animation.shell.SettingsTransitionFrames
import com.otokit.noharayh.ui.chrome.ChromeDisplayInput
import com.otokit.noharayh.ui.chrome.SettingsHeading
import com.otokit.noharayh.ui.chrome.TitleText
import com.otokit.noharayh.ui.chrome.TitleTexts
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import com.otokit.noharayh.ui.theme.GameLine

/** 浮动版的双行页名。各层只消费页面进度，不持有导航状态或动画时钟。 */
internal class FloatingHeading(context: Context) : FrameLayout(context) {
    val rowHeight = dp(context, 70)
    private val feature = FrameLayout(context)
    private val games = FrameLayout(context)
    private val mai = Lines(context)
    private val chu = Lines(context)
    private val previousFeature = Lines(context)
    private val settings = FrameLayout(context)
    private val currentSettings = Lines(context)
    private val previousSettings = Lines(context)
    private var display: ChromeDisplayInput? = null
    private var gameFrame: GameFrame? = null
    private var navDirection = 1
    private var forward = true
    private var sourceHeading: SettingsHeading? = null
    private var settingsMotion = false

    init {
        setPadding(dp(context, 12), 0, dp(context, 12), 0)
        games.addView(mai)
        games.addView(chu)
        feature.addView(previousFeature)
        feature.addView(games)
        settings.addView(previousSettings)
        settings.addView(currentSettings)
        addView(feature)
        addView(settings)
        previousFeature.visibility = GONE
        previousSettings.visibility = GONE
        settings.visibility = INVISIBLE
    }

    fun render(state: ChromeDisplayInput) {
        val old = display
        display = state
        if (old?.destination != state.destination) {
            if (old != null && !state.settingsOpen) {
                previousFeature.bind(TitleTexts.forDestination(old.destination, old.currentGame),
                    if (old.currentGame == GameLine.Mai) old.maiTextColor else old.chuTextColor)
                previousFeature.visibility = VISIBLE
                navDirection = if (state.navDirection < 0) -1 else 1
                navigationProgress(TransitionProgress(0f, true))
            }
            mai.bind(TitleTexts.forDestination(state.destination, GameLine.Mai), state.maiTextColor)
            chu.bind(TitleTexts.forDestination(state.destination, GameLine.Chu), state.chuTextColor)
        }
        if (sourceHeading == null) {
            currentSettings.bind(state.settingsTitle ?: TitleTexts.forSettings(state.settingsSection),
                TitleTexts.colorForSettings(state.settingsSection))
        }
        renderGame(state.gameFrame)
        if (old == null || (!settingsMotion && old.settingsOpen == state.settingsOpen)) {
            settingsProgress(TransitionProgress(if (state.settingsOpen) 1f else 0f, false))
        }
    }

    fun renderGame(frame: GameFrame?) {
        gameFrame = frame
        val state = display ?: return
        val p = frame?.normalizedProgress ?: if (state.currentGame == GameLine.Mai) 0f else 1f
        val travel = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val edge = if (frame != null && frame.contentWidthPx > 0) frame.edgePullPx * travel / frame.contentWidthPx else 0f
        val color = ColorUtils.blendARGB(state.maiTextColor, state.chuTextColor, p)
        mai.tint(color)
        chu.tint(color)
        mai.translationX = -p * travel + edge
        chu.translationX = (1f - p) * travel + edge
        mai.visibility = if (p >= 1f && edge == 0f) INVISIBLE else VISIBLE
        chu.visibility = if (p <= 0f && edge == 0f) INVISIBLE else VISIBLE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderGame(gameFrame)
    }

    fun navigationProgress(frame: TransitionProgress) {
        if (previousFeature.visibility != VISIBLE) return
        val dy = dp(context, 44).toFloat() * navDirection
        games.present(PageTurnFrames.enter(frame.value, dy = dy))
        previousFeature.present(PageTurnFrames.exit(frame.value, dy = -dy))
        if (!frame.running) previousFeature.visibility = GONE
    }

    fun settingsProgress(frame: TransitionProgress) {
        settingsMotion = frame.running
        feature.present(SettingsTransitionFrames.feature(frame.value))
        settings.present(SettingsTransitionFrames.overlay(frame.value, dp(context, 20).toFloat()))
        feature.visibility = if (feature.alpha > 0f) VISIBLE else INVISIBLE
        settings.visibility = if (settings.alpha > 0f) VISIBLE else INVISIBLE
    }

    fun prepareSubpage(from: SettingsHeading, to: SettingsHeading, entering: Boolean) {
        sourceHeading = from
        forward = entering
        previousSettings.bind(from.title, from.color)
        currentSettings.bind(to.title, to.color)
        previousSettings.visibility = VISIBLE
        subpageProgress(TransitionProgress(0f, true))
    }

    fun subpageProgress(frame: TransitionProgress) {
        val from = sourceHeading ?: return
        val density = resources.displayMetrics.density
        previousSettings.present(SettingsPageFrames.unfoldTitle(frame.value, false, forward, density))
        currentSettings.present(SettingsPageFrames.unfoldTitle(frame.value, true, forward, density))
        if (!frame.running) {
            if (frame.value <= 0f) currentSettings.bind(from.title, from.color)
            currentSettings.present(TransitionFrame(1f, 1f, 0f, 0f))
            previousSettings.visibility = GONE
            sourceHeading = null
        }
    }

    fun settle() {
        sourceHeading = null
        previousSettings.visibility = GONE
        currentSettings.present(TransitionFrame(1f, 1f, 0f, 0f))
        navigationProgress(TransitionProgress(1f, false))
        settingsMotion = false
        display?.let(::render)
    }

    private fun View.present(frame: TransitionFrame) {
        alpha = frame.alpha
        translationX = frame.translationX
        translationY = frame.translationY
    }

    private class Lines(context: Context) : LinearLayout(context) {
        private val english = line(13.8f)
        private val chinese = line(23.2f)

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 2), 0, 0)
            AppTypography.applyTitleBarRogSans(english)
            AppTypography.applyAppTypeface(chinese, bold = true)
            addView(english)
            addView(chinese, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (2.5f * resources.displayMetrics.density).toInt()
            })
        }

        private fun line(size: Float) = TextView(context).apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        }

        fun bind(title: TitleText, color: Int) {
            english.setText(title.english)
            chinese.setText(title.chinese)
            tint(color)
        }

        fun tint(color: Int) {
            if (english.currentTextColor == color && chinese.currentTextColor == color) return
            english.setTextColor(color)
            chinese.setTextColor(color)
        }
    }
}
