package com.otokit.noharayh.ui.shell

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.pages.pageMusicData.MusicDataPlaceholder
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPlaceholder
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.NavigationController
import com.otokit.noharayh.ui.shell.navigation.NavigationState
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot
import com.otokit.noharayh.ui.window.metrics.WindowMetricsSnapshot

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

    private enum class LifecycleState { New, Attached, Disposed }

    private val controller = NavigationController(initialState)

    private var onCanHandleBackChanged: ((Boolean) -> Unit)? = null
    private var mountedDestination: Destination? = null
    private var appliedSettingsOpen: Boolean? = null
    private var contentAccessibility: Int = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var contentFocusability: Int = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private var lifecycleState = LifecycleState.New

    val state: NavigationState
        get() = controller.state

    val canHandleBack: Boolean
        get() = lifecycleState == LifecycleState.Attached && controller.canHandleBack

    fun setOnCanHandleBackChanged(listener: ((Boolean) -> Unit)?) {
        if (lifecycleState == LifecycleState.Disposed) return
        onCanHandleBackChanged = listener
    }

    /** 挂载覆盖层并首次渲染；每个实例只允许挂载一次，重复调用直接失败。 */
    fun attach() {
        check(lifecycleState == LifecycleState.New) { "MainShell 只允许挂载一次，销毁后不可重新挂载" }
        check(backgroundHost.childCount == 0 && chromeHost.childCount == 0) {
            "背景与外围 UI 由后续步骤挂载，本步必须为空"
        }
        lifecycleState = LifecycleState.Attached
        overlayHost.addView(createSettingsPlaceholder(overlayHost.context))
        render()
    }

    /** 恢复宿主临时配置并解除监听；销毁为终态，即使尚未挂载也不能再次使用。 */
    fun detach() {
        if (lifecycleState == LifecycleState.Disposed) return
        val wasAttached = lifecycleState == LifecycleState.Attached
        lifecycleState = LifecycleState.Disposed
        onCanHandleBackChanged = null
        if (!wasAttached) return

        if (appliedSettingsOpen == true) applySettingsVisibility(false)
        overlayHost.visibility = View.GONE
        contentHost.removeAllViews()
        overlayHost.removeAllViews()
        mountedDestination = null
        appliedSettingsOpen = null
    }

    fun navigate(destination: Destination) {
        if (lifecycleState != LifecycleState.Attached) return
        controller.navigate(destination)
        render()
    }

    fun openSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        controller.openSettings()
        render()
    }

    fun closeSettings() {
        if (lifecycleState != LifecycleState.Attached) return
        controller.closeSettings()
        render()
    }

    fun consumeBack(): Boolean {
        if (lifecycleState != LifecycleState.Attached) return false
        val consumed = controller.consumeBack()
        if (consumed) render()
        return consumed
    }

    /**
     * 应用最新窗口快照：背景用基础矩形，内容与覆盖层用避让 IME 的交互矩形。
     *
     * 只调整自己持有的宿主，不重建页面、不修改导航与字体；没有变化时不写 LayoutParams。
     */
    fun updateWindow(snapshot: WindowSnapshot) {
        if (lifecycleState != LifecycleState.Attached) return
        val rects = ContentGeometry.compute(snapshot, AppChromeInsets.Zero)
        applyContentRect(backgroundHost, rects.base, snapshot.metrics)
        applyContentRect(contentHost, rects.interactive, snapshot.metrics)
        applyContentRect(overlayHost, rects.interactive, snapshot.metrics)
    }

    private fun applyContentRect(host: View, rect: PixelRect, metrics: WindowMetricsSnapshot) {
        val params = host.layoutParams as? FrameLayout.LayoutParams ?: return
        val localRect = ContentGeometry.relativeToRoot(rect, metrics.rootLeftPx, metrics.rootTopPx)
        // 窗口矩形使用物理左上坐标，不随 RTL 镜像；超出根的部分由父容器裁剪。
        @SuppressLint("RtlHardcoded") // 这是物理窗口坐标，不是随语言切换的内容起始方向。
        val gravity = Gravity.TOP or Gravity.LEFT
        if (params.width == localRect.width && params.height == localRect.height &&
            params.leftMargin == localRect.left && params.topMargin == localRect.top &&
            params.rightMargin == 0 && params.bottomMargin == 0 && params.gravity == gravity
        ) {
            return
        }

        // 明确尺寸，避免 match_parent 随 IME 缩小的根重新测量背景。
        params.width = localRect.width
        params.height = localRect.height
        params.gravity = gravity
        params.setMargins(localRect.left, localRect.top, 0, 0)
        host.layoutParams = params
    }

    private fun render() {
        val current = controller.state
        if (mountedDestination != current.destination) {
            mountContent(current.destination)
        }
        if (appliedSettingsOpen != current.settingsOpen) {
            applySettingsVisibility(current.settingsOpen)
        }
        onCanHandleBackChanged?.invoke(canHandleBack)
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
        AppTypography.applyAppTypeface(this)
    }

    private fun resolveColorBackground(context: Context): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) {
            return 0
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
