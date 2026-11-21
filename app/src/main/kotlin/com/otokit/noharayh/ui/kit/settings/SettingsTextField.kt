package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doAfterTextChanged
import com.otokit.noharayh.ui.constants.dimensions.SettingsTextFieldDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

/** 设置表单的原生单行输入；只展示标签、内容和错误，不解析业务值。 */
internal class SettingsTextField(context: Context, label: String, value: String = "",
    inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
    hint: String? = null,
) : LinearLayout(context) {
    private val input = AppCompatEditText(context).apply {
        setText(value)
        this.inputType = inputType
        this.hint = hint
        setSingleLine(true)
        textSize = Dimensions.TEXT_SP
        setTextColor(Colors.TEXT)
        setHintTextColor(Colors.MUTED)
        includeFontPadding = false
        contentDescription = label
        imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setPadding(dp(Dimensions.INSET_DP), 0, dp(Dimensions.INSET_DP), 0)
        background = ContinuousCornerDrawable(Colors.CONTENT, dp(Dimensions.RADIUS_DP).toFloat())
        stateListAnimator = null
        elevation = 0f
        AppTypography.applyAppTypeface(this)
        doAfterTextChanged { onValueChanged?.invoke(it.toString()) }
    }
    private val error = ViewPrimitives.text(context, "", Dimensions.LABEL_SP, Colors.ERROR_TEXT).apply {
        visibility = GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    var onValueChanged: ((String) -> Unit)? = null
    val text get() = input.text.toString()

    init {
        orientation = VERTICAL
        addView(ViewPrimitives.text(context, label, Dimensions.LABEL_SP, Colors.TEXT).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(-1, -2))
        addView(input, LayoutParams(-1, dp(Dimensions.HEIGHT_DP)).apply { topMargin = dp(Dimensions.GAP_DP) })
        addView(error, LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.GAP_DP) })
    }

    fun showError(message: String?) {
        error.text = message.orEmpty()
        error.visibility = if (message == null) GONE else VISIBLE
    }

    fun setInputEnabled(enabled: Boolean) { input.isEnabled = enabled }

    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
