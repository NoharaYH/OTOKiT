package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.constants.dimensions.ColorPickerDimensions as Dimensions
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.settings.ColorPickerTransitionRunner
import com.otokit.noharayh.ui.animation.settings.SettingsChoiceTransitionRunner
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.theme.ColorChannels
import com.otokit.noharayh.ui.theme.NoneThemeColorRole
import com.otokit.noharayh.ui.theme.ThemeColors
import kotlin.math.roundToInt

/** 展示值与真实颜色分离：归位/切换模型不会回写主题，只有用户编辑会提交。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class NoneThemeColorPicker(context: Context, private val onColor: (NoneThemeColorRole, Int) -> Unit) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val swatches = mutableListOf<PressSurface>()
    private val hexFields = mutableListOf<ColorHexField>()
    private val sliders = mutableListOf<ColorChannelSlider>()
    private val numbers = mutableListOf<TextView>()
    private val channelLabels = mutableListOf<TextView>()
    private val models = SettingsSegmentedChoice(context)
    private val selectionMotion = SettingsChoiceTransitionRunner()
    private val channelMotion = ColorPickerTransitionRunner()
    private var model = ColorChannels.HSV
    private var selected: Int? = null
    private var colors = intArrayOf(0xFF6750A4.toInt(), 0xFF7D5260.toInt())
    private var channels = FloatArray(3)
    private var display = FloatArray(3)
    private var displayRamps = Array(3) { IntArray(7) { GRAY } }
    private var numberReveal = 0f
    private var controlsEnabled = true
    val panelHeight: Int get() = dp(Dimensions.PANEL_HEIGHT_DP)

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        isFocusableInTouchMode = true
        setPadding(dp(12), 0, dp(12), dp(12))
        val palette = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
        }
        repeat(2) { index ->
            val roleLabel = context.getString(if (index == 0) R.string.settings_color_primary else R.string.settings_color_secondary)
            val pair = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
            }
            val swatch = PressSurface(context).apply {
                baseRadius = dp(4).toFloat()
                outerHaloEnabled = true
                innerRippleEnabled = false
                contentDescription = roleLabel
                setOnClickListener { select(index) }
            }
            // 48dp 热区承载 32dp 高色块，布局只看见图中的紧凑色条。
            val swatchHost = FrameLayout(context).apply {
                clipChildren = false; clipToPadding = false
                addView(swatch, FrameLayout.LayoutParams(-1, dp(32), Gravity.CENTER))
                post { touchDelegate = android.view.TouchDelegate(android.graphics.Rect(0, 0, width, height), swatch) }
            }
            val hex = ColorHexField(context).apply {
                contentDescription = context.getString(R.string.settings_color_hex, roleLabel)
                onColor = { value ->
                    if (selected != index) select(index)
                    colors[index] = value
                    swatches[index].fillColor = value
                    channels = model.fromColor(value, channels[0])
                    animateChannels()
                    this@NoneThemeColorPicker.onColor(role(index), value)
                }
            }
            swatches += swatch
            hexFields += hex
            pair.addView(swatchHost, LayoutParams(dp(48), dp(48)))
            pair.addView(hex, LayoutParams(0, dp(36), 1f).apply { marginStart = dp(8) })
            palette.addView(pair, LayoutParams(0, dp(48), 1f).apply { if (index > 0) marginStart = dp(12) })
        }
        addView(palette, LayoutParams(-1, dp(48)))
        repeat(3) { index ->
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false; clipToPadding = false
            }
            val label = text(12f).apply { text = model.labels[index] }
            val slider = ColorChannelSlider(context).apply {
                onGrab = {
                    channelMotion.stop()
                    finishEditing()
                    // 抓住当前显示位置；不因落指把尚未走完的归位动画硬切到终点。
                }
                onRelease = {
                    if (selected != null && !this@NoneThemeColorPicker.display.contentEquals(channels)) animateChannels()
                }
                onValue = { fraction ->
                    selected?.let { active ->
                        channelMotion.stop()
                        channels[index] = fraction * model.maxima[index]
                        val color = model.toColor(channels)
                        colors[active] = color
                        swatches[active].fillColor = color
                        hexFields[active].renderColor(color, force = true)
                        showChannels(channels, model.ramps(channels), 1f)
                        onColor(role(active), color)
                    }
                }
            }
            val number = text(14f).apply { alpha = 0f }
            val numberBox = FrameLayout(context).apply {
                background = ContinuousCornerDrawable(0xFFF5F5F5.toInt(), dp(4).toFloat())
                addView(number, FrameLayout.LayoutParams(-1, -1))
            }
            channelLabels += label
            sliders += slider
            numbers += number
            row.addView(label, LayoutParams(dp(16), -1))
            row.addView(slider, LayoutParams(0, dp(Dimensions.CHANNEL_HEIGHT_DP), 1f))
            row.addView(numberBox, LayoutParams(dp(48), dp(36)).apply { marginStart = dp(4) })
            addView(row, LayoutParams(-1, dp(Dimensions.CHANNEL_HEIGHT_DP)))
        }
        models.setItems(ColorChannels.entries.map { it.name }, 0) { index ->
            if (model.ordinal == index) return@setItems
            finishEditing()
            val oldMax = model.maxima
            model = ColorChannels.entries[index]
            models.setSelection(index)
            // 位移从原来的归一化位置接续；标签与计数使用新模型的单位。
            display = FloatArray(3) { display[it] / oldMax[it] * model.maxima[it] }
            updateChannelLabels()
            selected?.let { channels = model.fromColor(colors[it], channels[0]); animateChannels() }
        }
        addView(models, LayoutParams(-1, dp(Dimensions.MODEL_HEIGHT_DP)).apply { topMargin = dp(4) })
        updateChannelLabels()
        resetSelection()
    }

    fun renderColors(value: ThemeColors) {
        val next = intArrayOf(value.primaryColor, value.secondaryColor)
        val selectedChanged = selected?.let { colors[it] != next[it] } == true
        colors = next
        swatches.forEachIndexed { i, view -> view.fillColor = colors[i] }
        hexFields.forEachIndexed { i, view -> view.renderColor(colors[i]) }
        if (selectedChanged) {
            channels = model.fromColor(colors[requireNotNull(selected)], channels[0])
            animateChannels()
        }
    }

    fun resetSelection() {
        selected = null
        channelMotion.stop()
        selectionMotion.stop()
        swatches.forEach { it.baseRadius = dp(4).toFloat(); it.isSelected = false; it.invalidate() }
        finishEditing()
        channels = FloatArray(3)
        showChannels(channels, Array(3) { IntArray(7) { GRAY } }, 0f)
        updateControls()
    }

    private fun select(index: Int) {
        if (selected == index) return
        selected = index
        swatches.forEachIndexed { i, view ->
            view.isSelected = i == index
            selectionMotion.play(i, view.baseRadius, dp(if (i == index) 16 else 4).toFloat(), 0, 0) { radius, _ ->
                view.baseRadius = radius; view.invalidate()
            }
        }
        channels = model.fromColor(colors[index], channels[0])
        animateChannels()
        updateControls()
    }

    private fun animateChannels() {
        val from = display.copyOf()
        val target = channels.copyOf()
        if (selected == null) return
        val fromRamps = displayRamps
        val toRamps = model.ramps(channels)
        val fromReveal = numberReveal
        channelMotion.play { t ->
            showChannels(FloatArray(3) { from[it] + (target[it] - from[it]) * t },
                Array(3) { channel -> IntArray(7) { stop ->
                    ColorUtils.blendARGB(fromRamps[channel][stop], toRamps[channel][stop], t)
                } }, fromReveal + (1f - fromReveal) * t)
        }
    }

    private fun showChannels(values: FloatArray, ramps: Array<IntArray>, reveal: Float) {
        display = values.copyOf()
        displayRamps = ramps
        numberReveal = reveal
        sliders.forEachIndexed { i, slider ->
            slider.value = (values[i] / model.maxima[i]).coerceIn(0f, 1f)
            slider.renderRamp(ramps[i])
            numbers[i].text = if (selected == null) "" else values[i].roundToInt().toString()
            numbers[i].alpha = reveal
            numbers[i].translationY = dp(8) * (1f - reveal)
        }
    }

    private fun updateChannelLabels() {
        channelLabels.forEachIndexed { i, label ->
            label.text = model.labels[i]
            sliders[i].contentDescription = "${model.name} ${model.labels[i]}"
            sliders[i].setLabelFormatter { (it * model.maxima[i]).roundToInt().toString() }
        }
    }

    fun setControlsEnabled(enabled: Boolean) { controlsEnabled = enabled; updateControls() }
    private fun finishEditing() {
        if (hexFields.none { it.hasFocus() }) return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
        hexFields.forEach { it.clearFocus() }
        requestFocus()
    }
    private fun updateControls() {
        swatches.forEach { it.isEnabled = controlsEnabled }
        hexFields.forEach { it.isEnabled = controlsEnabled }
        sliders.forEach { it.isEnabled = controlsEnabled && selected != null }
        models.setControlsEnabled(controlsEnabled)
    }
    override fun onDetachedFromWindow() {
        channelMotion.stop()
        selectionMotion.stop()
        super.onDetachedFromWindow()
    }
    private fun text(size: Float) = TextView(context).apply {
        textSize = size; gravity = Gravity.CENTER; includeFontPadding = false
        setTextColor(0xFF616161.toInt()); AppTypography.applyAppTypeface(this, bold = true)
    }
    private fun role(index: Int) = if (index == 0) NoneThemeColorRole.Primary else NoneThemeColorRole.Secondary
    private fun dp(value: Int) = (value * density).roundToInt()
    private companion object { val GRAY = 0xFF9E9E9E.toInt() }
}
