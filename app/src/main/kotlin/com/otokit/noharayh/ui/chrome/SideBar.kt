package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.NavCapsuleView
import com.otokit.noharayh.ui.shell.navigation.Destination
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 抽屉面板内容：三项功能胶囊 + 设置圆形入口。
 *
 * 只接收选中 destination 与点击回调；不持有导航状态、不读写偏好、不查主题 Registry。
 */
class SideBar(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val list = LinearLayout(context)
    private val capsules = LinkedHashMap<Destination, NavCapsuleView>()
    private val settingsCapsule = NavCapsuleView(context)

    var onNavigate: ((Destination) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    init {
        val scroll = ScrollView(context).apply { isFillViewport = true }
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(18), dp(24), dp(18), dp(18))
        scroll.addView(
            list,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            scroll,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val standardWidth = NavCapsuleView.standardWidthPx(density)
        NavDisplay.items.forEachIndexed { index, item ->
            val capsule = NavCapsuleView(context).apply {
                layoutParams = LinearLayout.LayoutParams(standardWidth, dp(55.2f)).apply {
                    topMargin = if (index == 0) 0 else dp(16)
                }
                bind(
                    label = context.getString(item.label),
                    subLabel = context.getString(item.subLabel),
                    iconRes = item.icon,
                    accentColor = item.accentColor,
                    selected = false,
                )
                setOnClickListener { onNavigate?.invoke(item.destination) }
                contentDescription = context.getString(item.label)
            }
            capsules[item.destination] = capsule
            list.addView(capsule)
        }

        settingsCapsule.apply {
            layoutParams = LinearLayout.LayoutParams(dp(55.2f), dp(55.2f)).apply { topMargin = dp(24) }
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
        val available = panelWidthPx - dp(36)
        if (available <= 0) return
        val standard = NavCapsuleView.standardWidthPx(density)
        val width = min(standard, available)
        capsules.values.forEach { it.setMaxWidthPx(width) }
    }

    fun dispose() {
        onNavigate = null
        onSettings = null
        capsules.values.forEach { it.setOnClickListener(null) }
        capsules.clear()
        settingsCapsule.setOnClickListener(null)
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()
    private fun dp(value: Int): Int = (value * density).roundToInt()
}
