package com.otokit.noharayh.ui.shell.background

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import kotlin.math.abs

/**
 * 双音游背景宿主：唯一持有两份 WebView 与 Pager 的入口。
 *
 * 不读偏好、不决定恢复优先级、不导入 MainShell；对外只提供选择渲染、输入开关和只读快照订阅。
 */
class BackgroundHost(private val container: FrameLayout) {

    private val context = container.context
    private val loader = AssetBackgroundLoader(context)
    private val pages = mapOf(
        GameLine.Mai to BackgroundPage(context, GameLine.Mai, loader),
        GameLine.Chu to BackgroundPage(context, GameLine.Chu, loader),
    )

    private var pager: ViewPager2? = null
    private var attached = false
    private var disposed = false
    private var resumed = false
    private var inputEnabled = false

    private val listeners = LinkedHashSet<(BackgroundSnapshot) -> Unit>()

    private var snapshot = BackgroundSnapshot(
        selectedGame = GameLine.Mai,
        progress = BackgroundPagerMapping.endpointProgress(GameLine.Mai),
        phase = BackgroundPhase.Idle,
    )

    val currentSnapshot: BackgroundSnapshot
        get() = snapshot

    /** 建立 Pager 与两页；每个实例只允许挂载一次。 */
    fun attach(initialGame: GameLine) {
        check(!attached && !disposed) { "背景宿主只允许挂载一次" }
        attached = true

        val pager = ViewPager2(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            isUserInputEnabled = inputEnabled
            // 交叠淡变的抵消公式只在 LTR 下成立；只固定背景 Pager 的方向。
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            adapter = BackgroundPagerAdapter(pages)
            setPageTransformer { page, position ->
                page.translationX = -position * page.width
                page.alpha = (1f - abs(position)).coerceIn(0f, 1f)
            }
        }
        this.pager = pager
        container.addView(pager)
        pager.setCurrentItem(BackgroundPagerMapping.pageFor(initialGame), false)
        publish(
            selectedGame = initialGame,
            progress = BackgroundPagerMapping.endpointProgress(initialGame),
            phase = BackgroundPhase.Idle,
        )
        if (resumed) pages.values.forEach(BackgroundPage::resume)
    }

    /** 应用已提交选择与两个音游当前主题；入口不变时不重新加载。 */
    fun renderSelection(
        currentGame: GameLine,
        maiTheme: RegisteredTheme,
        chuTheme: RegisteredTheme,
    ) {
        if (!attached || disposed) return
        pages.getValue(GameLine.Mai).show(maiTheme)
        pages.getValue(GameLine.Chu).show(chuTheme)

        val pager = pager ?: return
        val target = BackgroundPagerMapping.pageFor(currentGame)
        if (pager.currentItem != target) {
            pager.setCurrentItem(target, false)
        }
        publish(
            selectedGame = currentGame,
            progress = BackgroundPagerMapping.endpointProgress(currentGame),
            phase = BackgroundPhase.Idle,
        )
    }

    /** 订阅只读快照；首次订阅立即收到当前值，返回可重复调用的解绑句柄。 */
    fun subscribe(listener: (BackgroundSnapshot) -> Unit): AutoCloseable {
        if (disposed) return AutoCloseable { }
        listeners.add(listener)
        listener(snapshot)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        pager?.isUserInputEnabled = enabled
    }

    fun setResumed(resumed: Boolean) {
        if (!attached || disposed || this.resumed == resumed) return
        this.resumed = resumed
        pages.values.forEach { page ->
            if (resumed) page.resume() else page.pause()
        }
    }

    /** 永久卸载：解绑、移除、销毁两页并清空引用；重复调用安全。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        attached = false
        resumed = false
        listeners.clear()

        pager?.let { pager ->
            pager.adapter = null
            pager.isUserInputEnabled = false
            container.removeView(pager)
        }
        pager = null
        pages.values.forEach(BackgroundPage::destroy)
        container.removeAllViews()
    }

    private fun publish(selectedGame: GameLine, progress: Float, phase: BackgroundPhase) {
        val next = BackgroundSnapshot(selectedGame, progress, phase)
        if (next == snapshot) return
        snapshot = next
        for (listener in listeners.toList()) {
            listener(snapshot)
        }
    }
}
