package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doAfterTextChanged
import com.otokit.noharayh.ui.animation.common.CapsuleCursorRunner
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.theme.ColorChannels
import kotlin.math.roundToInt

/** 保留原生输入/选择/粘贴，只替换光标绘制与闪烁。 */
internal class ColorHexField(context: Context) : AppCompatEditText(context) {
    var onColor: ((Int) -> Unit)? = null
    private var confirmedColor = 0
    private var binding = false
    private val density = resources.displayMetrics.density
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF323232.toInt() }
    private var caretAlpha = 1f
    private var cursorMotion: CapsuleCursorRunner? = null

    init {
        cursorMotion = CapsuleCursorRunner { caretAlpha = it; invalidate() }
        textSize = 14f
        setTextColor(0xFF323232.toInt())
        gravity = Gravity.CENTER
        includeFontPadding = false
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        filters = arrayOf(InputFilter.LengthFilter(7))
        setPadding((4 * density).roundToInt(), 0, (4 * density).roundToInt(), 0)
        background = ContinuousCornerDrawable(0xFFF5F5F5.toInt(), 4 * density)
        isCursorVisible = false
        AppTypography.applyAppTypeface(this, bold = true)
        doAfterTextChanged { value ->
            if (!binding) ColorChannels.parseHex(value.toString())?.let {
                confirmedColor = it
                onColor?.invoke(it)
            }
        }
        setOnFocusChangeListener { _, focused ->
            if (focused) updateCursor() else {
                cursorMotion?.stop()
                renderColor(confirmedColor, force = true)
                invalidate()
            }
        }
        setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_DONE) false else {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(windowToken, 0)
                clearFocus()
                true
            }
        }
    }

    fun renderColor(color: Int, force: Boolean = false) {
        confirmedColor = color
        if (!force && hasFocus()) return
        val hex = ColorChannels.hex(color)
        if (text.toString() == hex) return
        binding = true
        setText(hex)
        setSelection(hex.length)
        binding = false
    }

    private fun updateCursor() {
        if (isAttachedToWindow && hasWindowFocus() && hasFocus() && isShown && selectionStart == selectionEnd)
            cursorMotion?.restart() else cursorMotion?.stop()
        invalidate()
    }

    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        updateCursor()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateCursor()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateCursor()
    }
    override fun onDetachedFromWindow() {
        cursorMotion?.stop()
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasFocus() || !hasWindowFocus() || selectionStart < 0 || selectionStart != selectionEnd) return
        val textLayout = layout ?: return
        val offset = selectionStart.coerceAtMost(text?.length ?: 0)
        val line = textLayout.getLineForOffset(offset)
        val half = 1.5f * density
        val x = (compoundPaddingLeft + textLayout.getPrimaryHorizontal(offset) - scrollX)
            .coerceIn(half, width - half)
        val available = height - extendedPaddingTop - extendedPaddingBottom
        val vertical = ((available - textLayout.height) / 2f).coerceAtLeast(0f)
        val top = extendedPaddingTop + vertical + textLayout.getLineTop(line) - scrollY
        val bottom = extendedPaddingTop + vertical + textLayout.getLineBottom(line) - scrollY
        caretPaint.alpha = (caretAlpha * 255).roundToInt()
        canvas.drawRoundRect(x - half, top, x + half, bottom, half, half, caretPaint)
    }
}
