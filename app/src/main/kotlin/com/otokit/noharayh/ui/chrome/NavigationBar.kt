package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.NavRailItemView
import com.otokit.noharayh.ui.shell.navigation.Destination
import kotlin.math.roundToInt

/**
 * 宽屏左侧 Rail：三个功能项 + 底部设置入口。
 *
 * 只渲染传入的展示状态并回传导航/设置意图；不持有控制器、不保存第二份导航选择。
 * 顺序、图标与强调色复用 [NavDisplay]，与抽屉保持一致。
 */
class NavigationBar(context: Context) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val scroll = ScrollView(context)
    private val group = LinearLayout(context)
    private val items = LinkedHashMap<Destination, NavRailItemView>()
    private val settingsItem = NavRailItemView(context)
    private var appliedSpacing = -1

    var onNavigate: ((Destination) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        scroll.isFillViewport = true
        group.orientation = VERTICAL
        group.gravity = Gravity.CENTER
        group.setPadding(0, 0, 0, dp(8))

        NavDisplay.items.forEach { item ->
            val view = NavRailItemView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener { onNavigate?.invoke(item.destination) }
            }
            items[item.destination] = view
            group.addView(view)
        }
        scroll.addView(
            group,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            scroll,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val settingsHost = FrameLayout(context).apply {
            setPadding(0, 0, 0, dp(16))
        }
        settingsItem.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        settingsItem.setOnClickListener { onSettings?.invoke() }
        settingsHost.addView(settingsItem)
        settingsHost.setOnClickListener { onSettings?.invoke() }
        addView(
            settingsHost,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val spacing = (w * 0.7f * 0.6f).roundToInt()
        if (spacing == appliedSpacing) return
        appliedSpacing = spacing
        items.values.forEachIndexed { index, view ->
            val params = view.layoutParams as? LinearLayout.LayoutParams ?: return@forEachIndexed
            val margin = if (index == 0) 0 else spacing
            if (params.topMargin == margin) return@forEachIndexed
            params.topMargin = margin
            view.layoutParams = params
        }
    }

    fun render(input: ChromeDisplayInput) {
        NavDisplay.items.forEach { item ->
            items[item.destination]?.bind(
                labelText = context.getString(item.label),
                iconRes = item.icon,
                accentColor = item.accentColor,
                selected = item.destination == input.destination,
            )
        }
        settingsItem.bindSettings(
            iconRes = R.drawable.ic_settings_24,
            circleColor = NavDisplay.SETTINGS_ICON_COLOR,
            description = context.getString(R.string.nav_settings),
        )
    }

    fun dispose() {
        onNavigate = null
        onSettings = null
        items.values.forEach { it.setOnClickListener(null) }
        items.clear()
        settingsItem.setOnClickListener(null)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()
}
