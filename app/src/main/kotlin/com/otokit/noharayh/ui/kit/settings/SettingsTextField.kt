package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Canvas
import android.text.InputType
import android.view.View
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doAfterTextChanged
import com.otokit.noharayh.ui.constants.dimensions.LabeledFieldDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsTextFieldDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.LabeledFieldColors
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import kotlin.math.ceil

/** DMI 同款浮动标签表面中的原生编辑器；无下拉菜单、箭头或主题业务。 */
internal class SettingsTextField(context: Context, label: String, value: String = "",
    inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
    hint: String? = null,
) : LinearLayout(context) {
    private var rendering = false
    private var drawingAlpha = 255
    private val input = AppCompatEditText(context).apply {
        setText(value)
        setSingleLine(true)
        this.inputType = inputType
        this.hint = hint
        textSize = LabeledFieldDimensions.TEXT_SP
        setTextColor(LabeledFieldColors.TEXT)
        setHintTextColor(LabeledFieldColors.STROKE)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        contentDescription = label
        imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setPadding(dp(LabeledFieldDimensions.TEXT_INSET_DP), 0, dp(LabeledFieldDimensions.END_INSET_DP), 0)
        minimumHeight = dp(LabeledFieldDimensions.HEIGHT_DP)
        background = null
        stateListAnimator = null
        elevation = 0f
        AppTypography.applyAppTypeface(this)
        doAfterTextChanged { if (!rendering) onValueChanged?.invoke(it.toString()) }
    }
    private val surface = object : LabeledFieldSurface(context, label, contentTouchFeedback = true) {
        init { setFieldContent(input, inset = false) }
        override fun renderDisabledContent(progress: Float, textColor: Int) { input.setTextColor(textColor) }
    }
    private val error = ViewPrimitives.text(context, "", Dimensions.LABEL_SP, Colors.ERROR_TEXT).apply {
        visibility = GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    var onValueChanged: ((String) -> Unit)? = null
    val text get() = input.text.toString()

    init {
        orientation = VERTICAL
        // 硬件录制须经过 draw，才能使用覆盖浮标和外环的整组透明度合成。
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        addView(surface, LayoutParams(-1, -2))
        addView(error, LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.GAP_DP) })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val labelInset = ceil(surface.floatingLabelOutset).toInt()
        val contentInset = ceil(surface.floatingLabelContentInset).toInt()
        val minimum = maxOf(dp(LabeledFieldDimensions.HEIGHT_DP),
            input.layout.height + input.compoundPaddingTop + input.compoundPaddingBottom + 2 * contentInset)
        if (paddingTop != labelInset || input.minimumHeight != minimum) {
            setPadding(paddingLeft, labelInset, paddingRight, paddingBottom)
            input.minimumHeight = minimum
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onSetAlpha(alpha: Int): Boolean {
        if (drawingAlpha != alpha) {
            drawingAlpha = alpha
            invalidate()
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        if (drawingAlpha == 255) { super.draw(canvas); return }
        val overflow = surface.drawingOverflow
        val saved = canvas.saveLayerAlpha(-overflow, -overflow, width + overflow, height + overflow, drawingAlpha)
        super.draw(canvas)
        canvas.restoreToCount(saved)
    }

    fun setText(value: String) {
        if (text == value) return
        rendering = true
        input.setText(value)
        rendering = false
    }

    fun showError(message: String?) {
        error.text = message.orEmpty()
        error.visibility = if (message == null) GONE else VISIBLE
    }

    fun setInputEnabled(enabled: Boolean) {
        if (!enabled) input.clearFocus()
        input.isEnabled = enabled
        surface.setControlsEnabled(enabled)
        surface.setDisabledProgress(if (enabled) 0f else 1f)
    }

    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
