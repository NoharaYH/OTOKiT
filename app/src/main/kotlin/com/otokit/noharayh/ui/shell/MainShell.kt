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
import com.otokit.noharayh.storage.preferences.SharedPreferencesUiPreferences
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.pages.pageMusicData.MusicDataPlaceholder
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPlaceholder
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.NavigationController
import com.otokit.noharayh.ui.shell.navigation.NavigationState
import com.otokit.noharayh.ui.shell.selection.GameSelectionController
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.shell.selection.SelectionSource
import com.otokit.noharayh.ui.shell.selection.UiPreferencesAdapter
import com.otokit.noharayh.ui.shell.selection.restoreGameSelection
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeRegistry
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
    savedSelection: SelectionSource = SelectionSource(null, null, null, null),
    private val selectionPreferences: UiPreferencesAdapter =
        UiPreferencesAdapter(SharedPreferencesUiPreferences(contentHost.context)),
) {

    private enum class LifecycleState { New, Attached, Disposed }

    private val controller = NavigationController(initialState)

    /** 稳定的音游/主题选择；恢复顺序为保存状态、偏好、Registry 默认。 */
    private val selection = GameSelectionController(
        restoreGameSelection(
            saved = savedSelection,
            preferences = selectionPreferences.read(),
            lookup = ThemeRegistry::findById,
            defaultFor = ThemeRegistry::defaultFor,
        ),
    )

    private var mountedContentView: View? = null
    private var overlayTextView: TextView? = null

    private var onCanHandleBackChanged: ((Boolean) -> Unit)? = null
    private var mountedDestination: Destination? = null
    private var appliedSettingsOpen: Boolean? = null
    private var contentAccessibility: Int = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var contentFocusability: Int = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private var lifecycleState = LifecycleState.New

    val state: NavigationState
        get() = controller.state

    val selectionState: GameSelectionState
        get() = selection.state

    val canHandleBack: Boolean
        get() = lifecycleState == LifecycleState.Attached && controller.canHandleBack

    fun setOnCanHandleBackChanged(listener: ((Boolean) -> Unit)?) {
        if (lifecycleState == LifecycleState.Disposed) return
        onCanHandleBackChanged = listener
    }

    /** 挂载覆盖层并首次渲染；每个实例只允许挂载一次，重复调用直接失败。 */
    fun attach() {
        check(lifecycleState == LifecycleState.New) { "MainShell 只允许挂载一次，销毁后不可重新挂载" }
        check(chromeHost.childCount == 0) { "外围 UI 由后续步骤挂载，本步必须为空" }
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
        mountedContentView = null
        overlayTextView = null
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

    /** 提交已确认的音游选择；同值无副作用，变化时更新占位颜色并写偏好。 */
    fun selectGame(gameLine: GameLine) {
        if (lifecycleState != LifecycleState.Attached) return
        if (!selection.selectGame(gameLine)) return
        applySelection()
    }

    /** 提交某个已登记主题；归属由 [RegisteredTheme] 本身保证。 */
    fun selectTheme(theme: RegisteredTheme) {
        if (lifecycleState != LifecycleState.Attached) return
        if (!selection.selectTheme(theme)) return
        applySelection()
    }

    /** 选择变化后只更新颜色并落盘，不重建页面、不改导航。 */
    private fun applySelection() {
        val color = placeholderTextColor()
        (mountedContentView as? TextView)?.setTextColor(color)
        overlayTextView?.setTextColor(color)
        selectionPreferences.write(selection.state)
    }

    private fun placeholderTextColor(): Int {
        val current = selection.state
        val theme = ThemeRegistry.findById(current.selectedThemeId())
            ?: ThemeRegistry.defaultFor(current.currentGame)
        return theme.definition.colors.basicColor
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
        val view = createContent(contentHost.context, destination)
        contentHost.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        mountedContentView = view
        mountedDestination = destination
    }

    private fun createContent(context: Context, destination: Destination): View =
        when (destination) {
            Destination.ScoreSync -> ScoreSyncPlaceholder.create(context, placeholderTextColor())
            Destination.MusicData -> MusicDataPlaceholder.create(context, placeholderTextColor())
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
        val text = placeholderText(context, R.string.placeholder_settings)
        overlayTextView = text
        container.addView(
            text,
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
        // 颜色放在 TextAppearance 与字体之后，避免被前者覆盖。
        setTextColor(placeholderTextColor())
    }

    private fun resolveColorBackground(context: Context): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) {
            return 0
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
