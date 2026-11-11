package com.otokit.noharayh.ui.shell.background

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import kotlin.math.abs

/** 双背景的运行所有者；稳定选择由 shell 确认，归位期间不接受新选择。 */
class BackgroundHost(
    private val container: FrameLayout,
    private val requestSelection: (GameLine) -> GameLine,
) {
    private val context = container.context
    private val loader = AssetBackgroundLoader(context)
    private val pages = mutableMapOf(
        GameLine.Mai to BackgroundPage(context, GameLine.Mai, loader) {
            onPageVisualReady(GameLine.Mai)
        },
        GameLine.Chu to BackgroundPage(context, GameLine.Chu, loader) {
            onPageVisualReady(GameLine.Chu)
        },
    ).also { pages -> pages.values.forEach(BackgroundPage::pause) }

    private var initialVisualReady: (() -> Unit)? = null
    private var maiTheme: RegisteredTheme? = null
    private var chuTheme: RegisteredTheme? = null
    private val visuallyReady = mutableSetOf<GameLine>()

    private var pager: ViewPager2? = null
    private var adapter: BackgroundPagerAdapter? = null
    private var touchRoot: BackgroundTouchFrameLayout? = null
    private var attached = false
    private var disposed = false
    private var committedGame = GameLine.Mai
    private var progress = 0f
    private var phase = BackgroundPhase.Idle
    private var gate = BackgroundInputGate()
    private var requiredLayoutEpoch = 0L
    private var positionedGeneration: Long? = null
    private var cancelGeneration: Long? = null
    private var confirmationObserver: ViewTreeObserver? = null
    private var confirmationListener: ViewTreeObserver.OnPreDrawListener? = null
    private var alignmentCorrection: Runnable? = null
    private var callbackDepth = 0

    private val publisher = BackgroundSnapshotPublisher(
        BackgroundSnapshot(GameLine.Mai, 0f, BackgroundPhase.Idle),
    )

    val currentSnapshot: BackgroundSnapshot
        get() = publisher.current

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) = onPagerEvent {
            val pager = pager ?: return@onPagerEvent
            val game = BackgroundPagerMapping.gameForPage(position) ?: return@onPagerEvent
            if (!gate.canCommit || position != pager.currentItem || game == committedGame) {
                return@onPagerEvent
            }

            // 辅助操作可以直接 Settling；不能以触摸 DOWN/Dragging 作为提交许可。
            val confirmed = requestSelection(game)
            if (disposed) return@onPagerEvent
            committedGame = confirmed
            if (confirmed != game) {
                beginAlignment(requireNextLayout = true)
            } else {
                // 这是选中的确认，不是再次 setCurrentItem；保留真实 Settling。
                publishCurrent()
            }
        }

        override fun onPageScrolled(position: Int, offset: Float, offsetPixels: Int) = onPagerEvent {
            progress = BackgroundPagerMapping.progress(position, offset)
            if (!gate.barrierActive) publishCurrent()
        }

        override fun onPageScrollStateChanged(state: Int) = onPagerEvent {
            phase = when (state) {
                ViewPager2.SCROLL_STATE_DRAGGING -> BackgroundPhase.Dragging
                ViewPager2.SCROLL_STATE_SETTLING -> BackgroundPhase.Settling
                else -> BackgroundPhase.Idle
            }
            if (!gate.barrierActive) publishCurrent()
        }
    }

    private inline fun onPagerEvent(action: () -> Unit) {
        callbackDepth++
        try {
            if (attached && !disposed) action()
        } finally {
            callbackDepth--
        }
    }

    /** 每个宿主只挂一次；失败也释放已创建的页。恢复后的初始快照同步可读。 */
    fun attach(initialGame: GameLine) {
        check(!attached && !disposed) { "背景宿主只允许挂载一次" }
        attached = true
        committedGame = initialGame
        progress = BackgroundPagerMapping.endpointProgress(initialGame)
        publisher.publish(BackgroundSnapshot(initialGame, progress, BackgroundPhase.Idle))

        try {
            val root = BackgroundTouchFrameLayout(context)
            touchRoot = root
            root.layoutParams = matchParentParams()
            root.beforeGestureCancel = {
                // 先关提交，仍让 RecyclerView 接收这条 CANCEL；分发完成后再阻断物理输入。
                cancelGeneration = beginAlignment(requireNextLayout = true, deferPagerChanges = true)
            }
            root.afterGestureCancel = {
                val generation = cancelGeneration
                cancelGeneration = null
                if (generation != null) positionForAlignment(generation)
            }

            val adapter = BackgroundPagerAdapter(pages)
            this.adapter = adapter
            val pager = ViewPager2(context).apply {
                layoutParams = matchParentParams()
                orientation = ViewPager2.ORIENTATION_HORIZONTAL
                offscreenPageLimit = 1
                isUserInputEnabled = false
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                isSaveEnabled = false
                isSaveFromParentEnabled = false
                visibility = View.INVISIBLE // 参与布局，初始真实端点确认前不闪错误页。
                this.adapter = adapter
                setPageTransformer { page, position ->
                    page.translationX = -position * page.width
                    page.alpha = (1f - abs(position)).coerceIn(0f, 1f)
                }
                registerOnPageChangeCallback(pageCallback)
            }
            this.pager = pager
            root.addView(pager)
            container.addView(root)
            beginAlignment(requireNextLayout = true)
        } catch (failure: Throwable) {
            dispose()
            throw failure
        }
    }

    fun renderSelection(currentGame: GameLine, maiTheme: RegisteredTheme, chuTheme: RegisteredTheme) {
        if (!attached || disposed) return
        this.maiTheme = maiTheme
        this.chuTheme = chuTheme

        // 首帧只加载当前音游；另一页等当前页真正上屏后再加载，避免两个 WebView
        // 在冷启动时同时抢 Chromium 渲染。
        val selected = pages.getValue(currentGame)
        if (selected.show(if (currentGame == GameLine.Mai) maiTheme else chuTheme)) {
            visuallyReady.remove(currentGame)
        }
        if (currentGame != committedGame) {
            committedGame = currentGame
            beginAlignment(requireNextLayout = true)
        }
        if (visuallyReady.contains(currentGame)) loadOther(currentGame)
    }

    private fun loadOther(game: GameLine) {
        val other = if (game == GameLine.Mai) GameLine.Chu else GameLine.Mai
        val page = pages[other] ?: return
        if (page.isLoaded) return
        val theme = (if (other == GameLine.Mai) maiTheme else chuTheme) ?: return
        page.show(theme)
    }

    /** shell 已更新背景宿主的几何；同步关门，确认本次真实布局后再开放。 */
    fun onViewportChanged() {
        if (attached && !disposed) beginAlignment(requireNextLayout = true)
    }

    fun subscribe(listener: (BackgroundSnapshot) -> Unit): AutoCloseable = publisher.subscribe(listener)

    /** 当前已提交音游的首帧回调；只交付一次，迟到页不覆盖新的遮罩。 */
    fun setOnFirstVisualReady(listener: (() -> Unit)?) {
        initialVisualReady = listener
        if (listener != null && visuallyReady.contains(committedGame)) {
            initialVisualReady = null
            listener()
        }
    }

    private fun onPageVisualReady(game: GameLine) {
        if (disposed) return
        visuallyReady.add(game)
        if (game != committedGame) return
        val listener = initialVisualReady
        initialVisualReady = null
        listener?.invoke()
        loadOther(game)
    }

    fun setInputEnabled(enabled: Boolean) {
        if (!attached || disposed || gate.inputEnabled == enabled) return
        gate = gate.copy(inputEnabled = enabled)
        if (enabled) syncPagerInput() else beginAlignment(requireNextLayout = false)
    }

    fun setResumed(resumed: Boolean) {
        if (!attached || disposed || gate.resumed == resumed) return
        gate = gate.copy(resumed = resumed)
        if (resumed) {
            pages.values.forEach(BackgroundPage::resume)
            syncPagerInput()
            // 屏障不按帧数过期；后台未布局时继续等待本轮布局/端点。
            if (gate.barrierActive) touchRoot?.requestLayout()
        } else {
            beginAlignment(requireNextLayout = false)
            pages.values.forEach(BackgroundPage::pause)
        }
    }

    private fun beginAlignment(requireNextLayout: Boolean, deferPagerChanges: Boolean = false): Long {
        val root = touchRoot ?: return gate.generation
        val pager = pager ?: return gate.generation
        gate = gate.beginAlignment()
        val generation = gate.generation
        positionedGeneration = null
        removeAlignmentCorrection()
        removeConfirmationListener()
        val immediate = !requireNextLayout && layoutReady(root, pager) && isAtEndpoint(pager) &&
            pager.scrollState == ViewPager2.SCROLL_STATE_IDLE
        requiredLayoutEpoch = root.layoutEpoch + if (immediate) 0 else 1
        if (!deferPagerChanges) {
            syncPagerInput()
            if (immediate) {
                confirmAlignment(generation)
                return generation
            }
        }

        val listener = ViewTreeObserver.OnPreDrawListener {
            if (!disposed && generation == gate.generation) {
                confirmAlignment(generation)
                // 首次布局可能把提前指定的页回落到 0；只在本轮实际布局证实页码偏离后纠正。
                // 回调退出后再定位，避免在 RecyclerView 的布局/回调遍历中修改它。
                if (gate.barrierActive && root.layoutEpoch >= requiredLayoutEpoch &&
                    layoutReady(root, pager) && positionedGeneration == generation &&
                    pager.currentItem != BackgroundPagerMapping.pageFor(committedGame)
                ) {
                    scheduleAlignmentCorrection(generation)
                }
            }
            true
        }
        confirmationObserver = root.viewTreeObserver
        confirmationListener = listener
        confirmationObserver?.addOnPreDrawListener(listener)
        if (!deferPagerChanges) positionForAlignment(generation)
        return generation
    }

    private fun positionForAlignment(generation: Long) {
        if (disposed || generation != gate.generation || !gate.barrierActive) return
        val pager = pager ?: return
        syncPagerInput()
        pager.setCurrentItem(BackgroundPagerMapping.pageFor(committedGame), false)
        positionedGeneration = generation
        // 同页 setCurrentItem(false) 可能没有回调；本轮布局和页根像素坐标同样能确认。
        touchRoot?.requestLayout()
    }

    private fun scheduleAlignmentCorrection(generation: Long) {
        if (alignmentCorrection != null) return
        val root = touchRoot ?: return
        val correction = Runnable {
            alignmentCorrection = null
            if (!disposed && generation == gate.generation && gate.barrierActive) {
                positionForAlignment(generation)
            }
        }
        alignmentCorrection = correction
        root.post(correction)
    }

    private fun removeAlignmentCorrection() {
        alignmentCorrection?.let { touchRoot?.removeCallbacks(it) }
        alignmentCorrection = null
    }

    private fun confirmAlignment(generation: Long) {
        val root = touchRoot ?: return
        val pager = pager ?: return
        val next = gate.confirmAlignment(
            expectedGeneration = generation,
            layoutCompleted = root.layoutEpoch >= requiredLayoutEpoch && layoutReady(root, pager),
            atEndpoint = isAtEndpoint(pager),
            // 无动画定位已停止惯性；取消后的公开 scrollState 可能残留 Settling。
            // 必须再有本轮真实布局和页根像素端点，不能只凭 setCurrentItem 返回解锁。
            motionStopped = pager.scrollState == ViewPager2.SCROLL_STATE_IDLE ||
                positionedGeneration == generation,
        )
        if (next === gate) return
        gate = next
        removeAlignmentCorrection()
        removeConfirmationListener()
        progress = BackgroundPagerMapping.endpointProgress(committedGame)
        phase = BackgroundPhase.Idle
        pager.visibility = View.VISIBLE
        pager.requestTransform()
        syncPagerInput()
        publishCurrent()
    }

    private fun layoutReady(root: BackgroundTouchFrameLayout, pager: ViewPager2): Boolean =
        root.isAttachedToWindow && root.width > 0 && root.height > 0 &&
            !container.isLayoutRequested && !root.isLayoutRequested && !pager.isLayoutRequested

    private fun isAtEndpoint(pager: ViewPager2): Boolean =
        pager.currentItem == BackgroundPagerMapping.pageFor(committedGame) &&
            adapter?.isAtEndpoint(committedGame, pager.width, pager.height) == true

    private fun syncPagerInput() {
        pager?.isUserInputEnabled = !disposed && gate.canCommit
    }

    private fun removeConfirmationListener() {
        val listener = confirmationListener ?: return
        val observer = confirmationObserver?.takeIf { it.isAlive } ?: touchRoot?.viewTreeObserver
        if (observer?.isAlive == true) observer.removeOnPreDrawListener(listener)
        confirmationListener = null
        confirmationObserver = null
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        attached = false
        gate = gate.beginAlignment().copy(resumed = false, inputEnabled = false)
        initialVisualReady = null
        visuallyReady.clear()
        publisher.dispose()
        removeAlignmentCorrection()
        removeConfirmationListener()
        cancelGeneration = null
        positionedGeneration = null
        val oldRoot = touchRoot
        oldRoot?.beforeGestureCancel = null
        oldRoot?.afterGestureCancel = null
        val oldPager = pager
        oldPager?.isUserInputEnabled = false
        val oldPages = pages.values.toList()
        pages.clear()
        pager = null
        adapter = null
        touchRoot = null

        val release = {
            oldPager?.unregisterOnPageChangeCallback(pageCallback)
            oldPager?.setPageTransformer(null)
            oldPager?.adapter = null
            oldPages.forEach(BackgroundPage::destroy)
            oldRoot?.removeAllViews()
            if (oldRoot != null) container.removeView(oldRoot)
        }
        // ViewPager2 的外部回调集合不允许在遍历中增删；重入 dispose 先禁用，再退出回调栈释放。
        if (callbackDepth > 0) Handler(Looper.getMainLooper()).post { release() } else release()
    }

    private fun publishCurrent() {
        if (!disposed && !gate.barrierActive) {
            publisher.publish(BackgroundSnapshot(committedGame, progress, phase))
        }
    }

    private fun matchParentParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
}
