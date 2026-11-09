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
 * 只消费已提交选择与主题描述，不读偏好、不决定恢复优先级、不导入 MainShell。
 * 选择提交以有效 `onPageSelected` 为界，初始化与程序化定位由门禁与归位屏障屏蔽。
 */
class BackgroundHost(
    private val container: FrameLayout,
    private val requestSelection: (GameLine) -> GameLine,
) {

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
    private var committedGame = GameLine.Mai
    private var progress = BackgroundPagerMapping.endpointProgress(GameLine.Mai)
    private var phase = BackgroundPhase.Idle
    private var gate = BackgroundInputGate()
    private var pendingProgrammaticTarget: Int? = null
    private var ensureAttempts = 0
    private var lastScrolledPosition = 0

    private val listeners = LinkedHashSet<(BackgroundSnapshot) -> Unit>()

    private var snapshot = BackgroundSnapshot(
        selectedGame = GameLine.Mai,
        progress = BackgroundPagerMapping.endpointProgress(GameLine.Mai),
        phase = BackgroundPhase.Idle,
    )

    val currentSnapshot: BackgroundSnapshot
        get() = snapshot

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {
            if (!attached || disposed) return

            val programmatic = pendingProgrammaticTarget
            if (programmatic != null && position == programmatic) {
                pendingProgrammaticTarget = null
                return
            }

            val gameLine = BackgroundPagerMapping.gameForPage(position) ?: return
            if (gameLine == committedGame) return
            if (!gate.canCommit) return
            // 只有拖拽或惯性过程才算用户选择；初始化/归位的程序化回零发生在 Idle。
            if (phase == BackgroundPhase.Idle) return

            val confirmed = requestSelection(gameLine)
            committedGame = confirmed
            publishCurrent()
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int,
        ) {
            if (!attached || disposed) return
            progress = BackgroundPagerMapping.progress(position, positionOffset)
            lastScrolledPosition = position

            val programmatic = pendingProgrammaticTarget
            if (programmatic != null && position == programmatic && positionOffsetPixels == 0) {
                pendingProgrammaticTarget = null
            }

            if (gate.barrierActive) {
                val committedPage = BackgroundPagerMapping.pageFor(committedGame)
                if (position == committedPage && positionOffsetPixels == 0) {
                    gate = gate.copy(barrierActive = false)
                    phase = BackgroundPhase.Idle
                }
            }
            publishCurrent()
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (!attached || disposed) return
            phase = when (state) {
                ViewPager2.SCROLL_STATE_DRAGGING -> BackgroundPhase.Dragging
                ViewPager2.SCROLL_STATE_SETTLING -> BackgroundPhase.Settling
                else -> BackgroundPhase.Idle
            }
            publishCurrent()
        }
    }

    /** 建立 Pager 与两页；每个实例只允许挂载一次。 */
    fun attach(initialGame: GameLine) {
        check(!attached && !disposed) { "背景宿主只允许挂载一次" }
        attached = true
        committedGame = initialGame
        progress = BackgroundPagerMapping.endpointProgress(initialGame)
        gate = gate.copy(initialized = false, barrierActive = true)

        val pager = ViewPager2(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            isUserInputEnabled = false
            // 交叠淡变的抵消公式只在 LTR 下成立；只固定背景 Pager 的方向。
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            adapter = BackgroundPagerAdapter(pages)
            setPageTransformer { page, position ->
                page.translationX = -position * page.width
                page.alpha = (1f - abs(position)).coerceIn(0f, 1f)
            }
            registerOnPageChangeCallback(pageCallback)
        }
        this.pager = pager
        container.addView(pager)
        pager.setCurrentItem(BackgroundPagerMapping.pageFor(initialGame), false)

        pager.post {
            if (!attached || disposed) return@post
            ensureAtCommittedPage()
        }
    }

    /** 应用外部已提交选择与两个音游当前主题；同页只换主题时不移动 Pager。 */
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
        if (currentGame == committedGame && pager.currentItem == target) return

        committedGame = currentGame
        progress = BackgroundPagerMapping.endpointProgress(currentGame)
        phase = BackgroundPhase.Idle
        gate = gate.copy(barrierActive = true)
        pendingProgrammaticTarget = target
        pager.setCurrentItem(target, false)
        pager.post {
            if (!attached || disposed) return@post
            ensureAtCommittedPage()
        }
    }

    /**
     * 布局完成后确认 Pager 真实停在已提交页。
     *
     * ViewPager2 可能在首次布局时把程序化定位重置回第 0 页，因此以真实 currentItem 为准，
     * 必要时再定位一次，并在到达前保持归位屏障；到达后才开放选择提交。
     */
    private fun ensureAtCommittedPage() {
        val pager = pager ?: return
        if (!attached || disposed) return
        val target = BackgroundPagerMapping.pageFor(committedGame)

        // currentItem 会被程序化定位立即改写，不能单独作为“已到达”的证据；
        // 必须同时看到该页的滚动事实，否则可能把框架恢复造成的回零当成已到位。
        val reachedEndpoint = pager.currentItem == target &&
            lastScrolledPosition == target &&
            pager.scrollState == ViewPager2.SCROLL_STATE_IDLE

        if (reachedEndpoint) {
            ensureAttempts = 0
            pendingProgrammaticTarget = null
            gate = gate.copy(initialized = true, barrierActive = false)
            progress = BackgroundPagerMapping.endpointProgress(committedGame)
            phase = BackgroundPhase.Idle
            publishCurrent()
            return
        }

        if (ensureAttempts >= 5) return
        ensureAttempts++
        pendingProgrammaticTarget = target
        gate = gate.copy(barrierActive = true)
        pager.setCurrentItem(target, false)
        pager.postOnAnimation {
            if (!attached || disposed) return@postOnAnimation
            ensureAtCommittedPage()
        }
    }

    /** 订阅只读快照；首次订阅立即收到当前值，返回可重复调用的解绑句柄。 */
    fun subscribe(listener: (BackgroundSnapshot) -> Unit): AutoCloseable {
        if (disposed) return AutoCloseable { }
        listeners.add(listener)
        listener(snapshot)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun setInputEnabled(enabled: Boolean) {
        if (disposed) return
        inputEnabled = enabled
        gate = gate.copy(inputEnabled = enabled)
        pager?.isUserInputEnabled = enabled && resumed
        if (!enabled) settleToCommitted()
    }

    fun setResumed(resumed: Boolean) {
        if (!attached || disposed || this.resumed == resumed) return
        this.resumed = resumed
        gate = gate.copy(resumed = resumed)
        pager?.isUserInputEnabled = inputEnabled && resumed
        pages.values.forEach { page ->
            if (resumed) page.resume() else page.pause()
        }
        if (!resumed) settleToCommitted()
    }

    /** 永久卸载：解绑、移除、销毁两页并清空引用；重复调用安全。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        attached = false
        resumed = false
        listeners.clear()

        pager?.let { pager ->
            pager.unregisterOnPageChangeCallback(pageCallback)
            pager.adapter = null
            pager.isUserInputEnabled = false
            container.removeView(pager)
        }
        pager = null
        pages.values.forEach(BackgroundPage::destroy)
        container.removeAllViews()
    }

    /** 关闭输入或离开前台时，把真实 Pager 与快照都归位到已提交音游端点。 */
    private fun settleToCommitted() {
        val pager = pager ?: return
        gate = gate.copy(barrierActive = true)
        phase = BackgroundPhase.Idle
        val target = BackgroundPagerMapping.pageFor(committedGame)
        progress = BackgroundPagerMapping.endpointProgress(committedGame)

        if (pager.currentItem == target &&
            lastScrolledPosition == target &&
            pager.scrollState == ViewPager2.SCROLL_STATE_IDLE
        ) {
            pendingProgrammaticTarget = null
            gate = gate.copy(barrierActive = false)
            publishCurrent()
            return
        }

        pendingProgrammaticTarget = target
        pager.setCurrentItem(target, false)
        pager.post {
            if (!attached || disposed) return@post
            if (pager.currentItem == target && pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                pendingProgrammaticTarget = null
                gate = gate.copy(barrierActive = false)
                progress = BackgroundPagerMapping.endpointProgress(committedGame)
                publishCurrent()
            }
        }
    }

    private fun publishCurrent() {
        publish(committedGame, progress, phase)
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
