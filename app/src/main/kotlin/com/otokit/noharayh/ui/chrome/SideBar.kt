package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.ShellDimensions
import com.otokit.noharayh.ui.kit.common.NavCapsuleView
import com.otokit.noharayh.ui.shell.navigation.Destination
import kotlin.math.min

/**
 * 抽屉面板内容：三项功能胶囊 + 设置圆形入口。
 *
 * 只接收选中 destination 与点击回调；不持有导航状态、不读写偏好、不查主题 Registry。
 */
class SideBar(context: Context) : FrameLayout(context) {

    private val list = LinearLayout(context)
    private val capsules = LinkedHashMap<Destination, NavCapsuleView>()
    private val settingsCapsule = NavCapsuleView(context)
    private val halo = NavCapsuleHaloView(context)

    var onNavigate: ((Destination) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    init {
        isFocusableInTouchMode = true
        val scroll = ScrollView(context).apply { isFillViewport = true }
        list.orientation = LinearLayout.VERTICAL
        val spacing = size(ShellDimensions.shellComponentSpacing)
        list.setPadding(spacing, size(ShellDimensions.navCapsuleListTop), spacing, spacing)
        scroll.addView(
            list,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(halo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            scroll,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val standardWidth = NavCapsuleView.standardWidthPx(resources)
        NavDisplay.items.forEachIndexed { index, item ->
            val capsule = NavCapsuleView(context).apply {
                layoutParams = LinearLayout.LayoutParams(standardWidth, size(ShellDimensions.navCapsuleHeight)).apply {
                    topMargin = if (index == 0) 0 else size(ShellDimensions.navCapsuleListSpacing)
                }
                bind(
                    label = context.getString(item.label),
                    subLabel = context.getString(item.subLabel),
                    iconRes = item.icon,
                    accentColor = item.accentColor,
                    selected = false,
                )
                setPressInsetPx(NavCapsulePressSpec.INSET_DP * resources.displayMetrics.density)
                onPressChanged = { pressed, clicked ->
                    if (pressed) halo.press(this) else halo.release(this, clicked)
                }
                setOnClickListener { onNavigate?.invoke(item.destination) }
                contentDescription = context.getString(item.label)
            }
            capsules[item.destination] = capsule
            list.addView(capsule)
        }

        settingsCapsule.apply {
            val diameter = size(ShellDimensions.navCapsuleHeight)
            layoutParams = LinearLayout.LayoutParams(diameter, diameter).apply {
                topMargin = size(ShellDimensions.navCapsuleSettingsGap)
            }
            setPressInsetPx(NavCapsulePressSpec.INSET_DP * resources.displayMetrics.density)
            onPressChanged = { pressed, clicked ->
                if (pressed) halo.press(this) else halo.release(this, clicked)
            }
            bindCircle(R.drawable.ic_settings_24, NavDisplay.SETTINGS_ICON_COLOR)
            contentDescription = context.getString(R.string.nav_settings)
            setOnClickListener { onSettings?.invoke() }
        }
        list.addView(settingsCapsule)
    }

    fun setSelected(destination: Destination) {
        capsules.forEach { (key, capsule) -> capsule.setSelected(key == destination) }
    }

    /** 面板变窄时同步收窄胶囊，避免负宽度或文字被挤出。 */
    fun setPanelWidth(panelWidthPx: Int) {
        val available = panelWidthPx - 2 * size(ShellDimensions.shellComponentSpacing)
        if (available <= 0) return
        val standard = NavCapsuleView.standardWidthPx(resources)
        val width = min(standard, available)
        capsules.values.forEach { it.setMaxWidthPx(width) }
    }

    /** 键盘模式优先选中项；触摸模式由面板接住焦点，避免底层继续持有。 */
    fun requestNavigationFocus(): Boolean =
        capsules.values.firstOrNull { it.isSelected }?.requestFocus() == true || requestFocus()

    fun dispose() {
        halo.reset()
        onNavigate = null
        onSettings = null
        capsules.values.forEach { it.setOnClickListener(null) }
        capsules.clear()
        settingsCapsule.setOnClickListener(null)
    }

    private fun size(id: Int): Int = resources.getDimensionPixelSize(id)
}
