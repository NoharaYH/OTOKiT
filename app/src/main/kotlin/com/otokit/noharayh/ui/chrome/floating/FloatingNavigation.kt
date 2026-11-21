package com.otokit.noharayh.ui.chrome.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.NavigationTransition
import com.otokit.noharayh.ui.constants.motion.PressHaloSpec
import com.otokit.noharayh.ui.constants.motion.TransitionTiming
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.chrome.ChromeDisplayInput
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.insets.EdgeInsets
import com.otokit.noharayh.ui.theme.GameLine
import kotlin.math.roundToInt

internal class FloatingNavigation(
    context: Context,
    private val curve: () -> MotionCurve,
    private val navigate: (Destination) -> Unit,
    private val settings: () -> Unit,
    private val back: () -> Unit,
    private val game: (GameLine) -> Unit,
    private val modalChanged: () -> Unit,
) : FrameLayout(context) {
    private fun dp(n: Int) = ViewPrimitives.dp(context, n)
    private val capsuleHeight = dp(55)
    private val edgeMargin = dp(20)
    private val rowGap = dp(6)
    private val haloSpace = dp(PressHaloSpec.PEAK_OUTSET_DP.toInt() + 1)
    private val indicatorInset = dp(4)
    val reservedHeight = edgeMargin + capsuleHeight + dp(10)
    private val fadeHeight = dp(120).toFloat()
    private val shadowColors = intArrayOf(
        0xB3000000.toInt(), 0x80000000.toInt(), 0x4D000000, 0x1A000000, Color.TRANSPARENT,
    )
    private val closedStops = floatArrayOf(0f, .10f, .24f, .53f, 1f)
    private val openStops = floatArrayOf(0f, .25f, .5f, .75f, 1f)
    private val shadowStops = closedStops.copyOf()
    private val shadowPaint = Paint(Paint.DITHER_FLAG)
    private var shadowProgress = -1f
    private val bottomAccentHeight = fadeHeight / 3f
    private val bottomAccentPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, -bottomAccentHeight,
            Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    }
    private var menuLift = 0f
    private val shade = View(context)
    val heading = FloatingHeading(context)
    private val destinations = mutableMapOf<Destination, FloatingMenuItem>()
    private val menu = PressFrontColumn(context)
    private val menuViewport = android.widget.ScrollView(context)
    private val primary = PressSurface(context)
    private val icon = ImageView(context)
    private val track = FrameLayout(context)
    private val indicator = View(context)
    private val labels = mutableListOf<TextView>()
    private var latestGameProgress = 0f
    private var input: ChromeDisplayInput? = null
    var expanded = false
        private set
    private var previewStart = 0f
    private val motion = ContentTransition(0f,
        TransitionTiming(NavigationTransition.DURATION_MS, MotionCurve.Standard),
        { a, b, p -> a + (b - a) * p }, ::drawMenu)

    init {
        setWillNotDraw(false)
        shade.setBackgroundColor(Color.BLACK)
        shade.setOnClickListener { show(false) }
        shade.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(shade, LayoutParams(-1, -1))
        menu.setPadding(haloSpace, haloSpace, haloSpace, haloSpace)
        addDestination(Destination.ScoreSync, R.string.nav_score_sync_label, R.string.nav_score_sync_sub,
            R.drawable.ic_sync_24, 0xff4caf50.toInt())
        addDestination(Destination.MusicData, R.string.nav_music_data_label, R.string.nav_music_data_sub,
            R.drawable.ic_library_music_24, 0xff2196f3.toInt())
        addDestination(Destination.ComingSoon, R.string.nav_coming_soon_label, R.string.nav_coming_soon_sub,
            R.drawable.ic_more_horiz_24, 0xff9e9e9e.toInt())
        addItem(R.string.nav_settings, R.string.title_setting, R.drawable.ic_settings_24, 0xff9e9e9e.toInt(), settings)
        menuViewport.isVerticalScrollBarEnabled = false
        menuViewport.addView(menu)
        addView(menuViewport, LayoutParams(dp(214) + 2 * haloSpace, -2, Gravity.BOTTOM or Gravity.LEFT).apply {
            leftMargin = edgeMargin - haloSpace
            bottomMargin = reservedHeight - haloSpace
        })
        menuViewport.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) positionMenu(visibleProgress)
        }
        primary.fillColor = Color.WHITE
        primary.baseRadius = capsuleHeight / 2f
        icon.setColorFilter(Color.DKGRAY)
        primary.addView(icon, LayoutParams(dp(24), dp(24), Gravity.CENTER))
        primary.setOnClickListener { if (input?.settingsOpen == true) back() else show(!expanded) }
        addView(primary, LayoutParams(capsuleHeight, capsuleHeight, Gravity.BOTTOM or Gravity.LEFT).apply {
            leftMargin = edgeMargin; bottomMargin = edgeMargin
        })
        track.background = ContinuousCornerDrawable(Color.WHITE, capsuleHeight / 2f)
        addView(track, LayoutParams(-1, capsuleHeight, Gravity.BOTTOM).apply {
            setMargins(edgeMargin + capsuleHeight + dp(12), 0, edgeMargin, edgeMargin)
        })
        track.addView(indicator, LayoutParams(0, capsuleHeight - 2 * indicatorInset, Gravity.CENTER_VERTICAL or Gravity.LEFT).apply { leftMargin = indicatorInset })
        val choices = LinearLayout(context)
        track.addView(choices, LayoutParams(-1, -1))
        listOf(GameLine.Mai to R.string.floating_mai, GameLine.Chu to R.string.floating_chu).forEach { (line, name) ->
            val label = TextView(context).apply {
                setText(name); gravity = Gravity.CENTER
                AppTypography.applyAppTypeface(this, bold = true)
            }
            val button = PressSurface(context).apply {
                baseRadius = capsuleHeight / 2f; fillColor = Color.TRANSPARENT
                addView(label, LayoutParams(-1, -1))
                contentDescription = context.getString(name)
                setOnClickListener { if (expanded) show(false); game(line) }
            }
            labels += label
            choices.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
        }
        track.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val width = ((r-l)/2 - indicatorInset).coerceAtLeast(0)
            if (indicator.layoutParams.width != width) indicator.layoutParams = indicator.layoutParams.apply { this.width = width }
            gameProgress(latestGameProgress)
        }
        addView(heading, LayoutParams(-1, heading.rowHeight, Gravity.TOP))
        drawMenu(0f)
    }

    fun layoutInWindow(bounds: PixelRect, safe: EdgeInsets) {
        val header = heading.layoutParams as LayoutParams
        if (header.leftMargin != safe.left || header.rightMargin != safe.right) {
            header.setMargins(safe.left, 0, safe.right, 0)
            heading.layoutParams = header
        }
        val params = layoutParams as LayoutParams
        if (params.width == bounds.width && params.height == bounds.height &&
            params.leftMargin == bounds.left && params.topMargin == bounds.top) return
        params.width = bounds.width
        params.height = bounds.height
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.setMargins(bounds.left, bounds.top, 0, 0)
        layoutParams = params
    }

    private fun addDestination(destination: Destination, label: Int, subtitle: Int, icon: Int, color: Int) {
        destinations[destination] = addItem(label, subtitle, icon, color) { navigate(destination) }
    }

    private fun addItem(label: Int, subtitle: Int, icon: Int, color: Int, action: () -> Unit): FloatingMenuItem {
        val row = FloatingMenuItem(context, label, subtitle, icon, color) { show(false); action() }
        menu.addView(row, LinearLayout.LayoutParams(-1, capsuleHeight).apply { bottomMargin = rowGap })
        return row
    }

    fun render(state: ChromeDisplayInput) {
        input = state
        if (state.settingsOpen && expanded) show(false)
        track.visibility = if (state.settingsOpen) GONE else VISIBLE
        destinations.forEach { (destination, row) -> row.isSelected = destination == state.destination }
        heading.render(state)
        indicator.background = ContinuousCornerDrawable(state.themeColor, capsuleHeight / 2f - indicatorInset)
        gameProgress(state.gameFrame?.normalizedProgress ?: if (state.currentGame == GameLine.Mai) 0f else 1f)
        refreshButton()
    }

    fun gameProgress(p: Float) {
        latestGameProgress = p
        indicator.translationX = p * track.width / 2f
        labels.forEachIndexed { i, text ->
            val selected = (p >= .5f) == (i == 1)
            text.setTextColor(if (selected && androidx.core.graphics.ColorUtils.calculateLuminance(input?.themeColor ?: Color.WHITE) < .4) Color.WHITE else Color.DKGRAY)
            (text.parent as View).isSelected = selected
        }
    }

    fun show(open: Boolean, immediate: Boolean = false) {
        val changed = expanded != open
        expanded = open
        refreshButton()
        motion.play(if (open) 1f else 0f, animate = !immediate, from = visibleProgress, curve = curve())
        if (changed) modalChanged()
    }
    private var visibleProgress = 0f

    private fun positionMenu(progress: Float) {
        val travel = (menuViewport.height - 2 * haloSpace).coerceAtLeast(0)
        menuLift = travel * progress
        val folded = (1f - progress).coerceAtLeast(0f)
        // 各行从底部同一位置展开，避免整列滑过视口底边时被切成半颗。
        val lastRowTop = menuViewport.scrollY + menuViewport.height - haloSpace - rowGap - capsuleHeight
        for (index in 0 until menu.childCount) {
            val row = menu.getChildAt(index)
            row.translationY = (lastRowTop - row.top) * folded
        }
        menuViewport.translationY = (capsuleHeight + rowGap) * folded - (menuLift - travel).coerceAtLeast(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate(0f, height.toFloat())
        canvas.scale(1f, (fadeHeight + menuLift) / fadeHeight)
        canvas.drawRect(0f, -fadeHeight, width.toFloat(), 0f, shadowPaint)
        if (bottomAccentPaint.alpha > 0) {
            canvas.drawRect(0f, -bottomAccentHeight, width.toFloat(), 0f, bottomAccentPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun drawMenu(p: Float) {
        visibleProgress = p
        val progress = p.coerceIn(0f, 1f)
        if (progress != shadowProgress) {
            shadowProgress = progress
            for (i in shadowStops.indices) {
                shadowStops[i] = closedStops[i] + (openStops[i] - closedStops[i]) * progress
            }
            shadowPaint.shader = LinearGradient(0f, 0f, 0f, -fadeHeight,
                shadowColors, shadowStops, Shader.TileMode.CLAMP)
        }
        // 在原有约 70% 的底色上叠加，完全展开时底端约为 74% 黑色。
        bottomAccentPaint.alpha = (34 * p).roundToInt().coerceIn(0, 255)
        val visible = expanded || p > 0f
        shade.visibility = if (visible) VISIBLE else GONE
        menuViewport.visibility = if (visible) VISIBLE else INVISIBLE
        shade.alpha = .2f * p.coerceIn(0f, 1f)
        menu.alpha = p.coerceIn(0f, 1f)
        positionMenu(p)
        track.visibility = if (input?.settingsOpen == true) GONE else VISIBLE
        track.importantForAccessibility = if (expanded) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }
    private fun refreshButton() {
        val inSettings = input?.settingsOpen == true
        icon.setImageResource(if (inSettings) R.drawable.ic_arrow_back_24 else if (expanded) R.drawable.ic_cancel_24 else R.drawable.ic_menu_24)
        primary.contentDescription = context.getString(if (inSettings) R.string.floating_back else if (expanded) R.string.floating_close else R.string.floating_menu)
    }
    fun beginPreview(): Boolean {
        if (!expanded) return false
        motion.stop(); previewStart = visibleProgress
        return true
    }
    fun preview(p: Float) = drawMenu(previewStart * (1f - p))
    fun focusMenu() = menu.getChildAt(0).requestFocus()
    fun focusButton() = primary.requestFocus()
    override fun onDetachedFromWindow() { motion.stop(); super.onDetachedFromWindow() }
}
