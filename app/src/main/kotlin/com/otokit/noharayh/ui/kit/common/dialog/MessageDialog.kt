package com.otokit.noharayh.ui.kit.common.dialog

import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.ui.constants.dimensions.ConfirmationDialogDimensions as Dimensions
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.motion.ContainerMotion
import kotlin.math.roundToInt

/** 平级的另一种说明；不表示导航层级，也不持久化用户的阅读选择。 */
internal data class AlternateExplanation(val message: CharSequence, val showLabel: String, val returnLabel: String)
internal data class MessageDialogAction(val label: String, val color: Int, val onClick: () -> Unit)

/** 应用共用消息窗：确认操作或说明切换，组件拥有测量和过渡，页面只提供内容与事件。 */
internal class MessageDialog(
    context: Context,
    iconRes: Int,
    message: CharSequence,
    confirmLabel: String,
    cancelLabel: String?,
    private val accentColor: Int = 0xFFE57373.toInt(),
    private val alternateExplanation: AlternateExplanation? = null,
    additionalAction: MessageDialogAction? = null,
    cancelOnTouchOutside: Boolean = true,
    private val onConfirmed: () -> Unit,
) : Dialog(context) {
    private val content = DialogMessageContent(context, message, alternateExplanation?.message)
    private var toggle: DialogButton? = null
    private var exiting = false
    private val transition = ContentTransition(1f, ContainerMotion.CONTENT, { a, b, t -> a + (b - a) * t }, ::render)

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            val inset = dp(Dimensions.CONTENT_INSET_DP)
            setPadding(inset, inset, inset, inset)
            background = ContinuousCornerDrawable(Color.WHITE, dp(Dimensions.CORNER_DP).toFloat())
        }
        body.addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(accentColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(Dimensions.ICON_SIZE_DP), dp(Dimensions.ICON_SIZE_DP)))
        body.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.CONTENT_GAP_DP) })
        val actions = PressFrontColumn(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun addAction(button: DialogButton) = actions.addView(button,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (actions.childCount > 0) marginStart = dp(Dimensions.BUTTON_GAP_DP)
            })
        val cancel = cancelLabel?.let { DialogButton(context, it, 0xFF757575.toInt()) { dismiss() } }
        if (additionalAction != null && cancel != null) addAction(cancel)
        addAction(DialogButton(context, confirmLabel, accentColor) {
            if (isShowing) { dismiss(); onConfirmed() }
        })
        val right = if (alternateExplanation != null) {
            DialogButton(context, alternateExplanation.showLabel, accentColor, ::switchExplanation).also { toggle = it }
        } else if (additionalAction != null) {
            DialogButton(context, additionalAction.label, additionalAction.color) {
                if (isShowing) { dismiss(); additionalAction.onClick() }
            }
        } else cancel
        right?.let(::addAction)
        body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Dimensions.CONTENT_GAP_DP)
        })
        setContentView(body)
        setCanceledOnTouchOutside(cancelOnTouchOutside)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            decorView.elevation = 0f
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次展示均回到主说明，主说明的自然高度也是该窗口的下限。
        transition.stop()
        exiting = false
        content.select(false)
        content.render(1f, resize = true)
        toggle?.apply {
            renderLabel(alternateExplanation!!.showLabel)
            label.alpha = 1f
            isEnabled = true
        }
    }

    private fun switchExplanation() {
        toggle?.isEnabled = false
        exiting = true
        transition.play(0f)
    }

    private fun render(progress: Float) {
        content.render(progress, resize = !exiting)
        toggle?.label?.alpha = progress
        if (exiting && progress == 0f && !transition.isRunning) {
            exiting = false
            content.select(!content.showingAlternate)
            toggle?.renderLabel(if (content.showingAlternate) alternateExplanation!!.returnLabel else alternateExplanation!!.showLabel)
            transition.play(1f, from = 0f)
        } else if (!exiting && progress == 1f && !transition.isRunning) {
            toggle?.isEnabled = true
        }
    }

    override fun onStop() {
        transition.stop()
        super.onStop()
    }

    fun showWithin(availableWidth: Int) {
        show()
        window?.setLayout(
            minOf(dp(Dimensions.MAX_WIDTH_DP), (availableWidth - dp(Dimensions.WINDOW_MARGIN_DP * 2)).coerceAtLeast(1)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}

private class DialogButton(context: Context, text: String, color: Int, onClick: () -> Unit) : PressSurface(context) {
    val label = TextView(context).apply {
        textSize = 14f
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        AppTypography.applyAppTypeface(this, bold = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        outerHaloEnabled = true
        deferClickUntilReleaseFeedback = true
        fillColor = 0xFFF5F5F5.toInt()
        baseRadius = dp(Dimensions.BUTTON_RADIUS_DP).toFloat()
        addView(label, FrameLayout.LayoutParams(-1, dp(Dimensions.BUTTON_HEIGHT_DP)))
        renderLabel(text)
        setOnClickListener { onClick() }
    }

    fun renderLabel(value: String) { label.text = value; contentDescription = value }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
