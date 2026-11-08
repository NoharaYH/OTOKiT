package com.otokit.noharayh.ui.shell

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.pages.pageMusicData.MusicDataPlaceholder
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPlaceholder
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.NavigationController
import com.otokit.noharayh.ui.shell.navigation.NavigationState

/**
 * 主壳：唯一知道“往哪一层挂什么”的地方。
 *
 * 只连接 [NavigationController] 与页面占位；不持有 Activity、不处理返回键，也不把状态写入口交给
 * 其他模块。返回消费能力由 [setOnCanHandleBackChanged] 对外通知，Activity 只同步 callback.isEnabled。
 */
class MainShell(
    private val backgroundHost: FrameLayout,
    private val contentHost: FrameLayout,
    private val chromeHost: FrameLayout,
    private val overlayHost: FrameLayout,
    initialState: NavigationState = NavigationState(),
) {

    private val controller = NavigationController(initialState)

    private var onCanHandleBackChanged: ((Boolean) -> Unit)? = null
    private var mountedDestination: Destination? = null
    private var appliedSettingsOpen: Boolean? = null
    private var contentAccessibility: Int = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var contentFocusability: Int = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private var attached = false

    val state: NavigationState
        get() = controller.state

    val canHandleBack: Boolean
        get() = controller.canHandleBack

    fun setOnCanHandleBackChanged(listener: ((Boolean) -> Unit)?) {
        onCanHandleBackChanged = listener
    }

    /** 挂载覆盖层并首次渲染；每个实例只允许挂载一次，重复调用直接失败。 */
    fun attach() {
        check(!attached) { "MainShell 只允许挂载一次" }
        check(backgroundHost.childCount == 0 && chromeHost.childCount == 0) {
            "背景与外围 UI 由后续步骤挂载，本步必须为空"
        }
        attached = true
        overlayHost.addView(createSettingsPlaceholder(overlayHost.context))
        render()
    }

    /** 解除监听并释放宿主子 View；幂等，结束后不再接收命令。 */
    fun detach() {
        if (!attached) return
        attached = false
        onCanHandleBackChanged = null
        contentHost.removeAllViews()
        overlayHost.removeAllViews()
        mountedDestination = null
        appliedSettingsOpen = null
    }

    fun navigate(destination: Destination) {
        if (!attached) return
        controller.navigate(destination)
        render()
    }

    fun openSettings() {
        if (!attached) return
        controller.openSettings()
        render()
    }

    fun closeSettings() {
        if (!attached) return
        controller.closeSettings()
        render()
    }

    fun consumeBack(): Boolean {
        if (!attached) return false
        val consumed = controller.consumeBack()
        if (consumed) render()
        return consumed
    }

    private fun render() {
        val current = controller.state
        if (mountedDestination != current.destination) {
            mountContent(current.destination)
        }
        if (appliedSettingsOpen != current.settingsOpen) {
            applySettingsVisibility(current.settingsOpen)
        }
        onCanHandleBackChanged?.invoke(controller.canHandleBack)
    }

    private fun mountContent(destination: Destination) {
        contentHost.removeAllViews()
        contentHost.addView(
            createContent(contentHost.context, destination),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        mountedDestination = destination
    }

    private fun createContent(context: Context, destination: Destination): View =
        when (destination) {
            Destination.ScoreSync -> ScoreSyncPlaceholder.create(context)
            Destination.MusicData -> MusicDataPlaceholder.create(context)
            Destination.ComingSoon -> placeholderText(context, R.string.placeholder_coming_soon)
        }

    private fun applySettingsVisibility(open: Boolean) {
        if (open) {
            contentAccessibility = contentHost.importantForAccessibility
            contentFocusability = contentHost.descendantFocusability
            contentHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            contentHost.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            overlayHost.visibility = View.VISIBLE
            overlayHost.requestFocus()
        } else {
            overlayHost.visibility = View.GONE
            contentHost.importantForAccessibility = contentAccessibility
            contentHost.descendantFocusability = contentFocusability
        }
        appliedSettingsOpen = open
    }

    private fun createSettingsPlaceholder(context: Context): View {
        val container = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(resolveColorBackground(context))
        }
        container.addView(
            placeholderText(context, R.string.placeholder_settings),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return container
    }

    private fun placeholderText(context: Context, textRes: Int): TextView = TextView(context).apply {
        setText(textRes)
        gravity = Gravity.CENTER
        setTextAppearance(android.R.style.TextAppearance_Material_Large)
    }

    private fun resolveColorBackground(context: Context): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) {
            return 0
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
