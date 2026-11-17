package com.otokit.noharayh.ui.shell

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.startup.StartupRunner
import com.otokit.noharayh.ui.constants.motion.StartupMotion
import com.otokit.noharayh.ui.kit.startup.StartupView

/** 启动展示装配：不参与导航或主页加载；只隔离输入并衔接系统启动窗。 */
class StartupPresentation internal constructor(
    private val root: FrameLayout,
    enabled: Boolean,
    private val animateExit: Boolean,
) {
    private val runner = StartupRunner()
    private var overlay: StartupView? = null
    private var ready = !enabled
    private var finished = false
    private var removeSystem: (() -> Unit)? = null
    private val deadline = SystemClock.uptimeMillis() + StartupMotion.LOAD_TIMEOUT_MS
    private val accessibility = mutableListOf<Pair<View, Int>>()
    private var previousFocus: View? = null

    init {
        if (enabled) {
            previousFocus = root.findFocus()
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                accessibility.add(child to child.importantForAccessibility)
                child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            overlay = StartupView(root.context).also { view ->
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                view.requestFocus()
                view.prepare { ready = true }
            }
        }
    }

    /** 系统每次绘制前查询；仅等待资源解析，绝不等待动画完成。超时直接放行。 */
    fun shouldKeepSystemSplash(): Boolean {
        if (!ready && SystemClock.uptimeMillis() >= deadline) finish()
        return !finished && !ready
    }

    fun onSystemExit(systemView: View, remove: () -> Unit) {
        if (finished) { remove(); return }
        removeSystem = remove
        val view = overlay
        if (view?.loaded == true) {
            // 已就绪立即交接；不再串联一段系统图标淡出后才开始完整动效。
            removeSystem?.invoke()
            removeSystem = null
            runner.play(view.durationMs, { view.timeMs = it * view.durationMs }) {
                runner.play(StartupMotion.EXIT_MS, { view.alpha = 1f - it }, ::finish)
            }
        } else {
            removeOverlay()
            if (animateExit) runner.play(StartupMotion.EXIT_MS, { systemView.alpha = 1f - it }, ::finish)
            else finish()
        }
    }

    /** 后台、销毁、恢复重建共用同一个幂等收尾；不等动画结束才恢复输入。 */
    fun finish() {
        if (finished) return
        finished = true
        ready = true
        runner.stop()
        removeOverlay()
        removeSystem?.invoke()
        removeSystem = null
    }

    private fun removeOverlay() {
        overlay?.let { view ->
            view.dispose()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlay = null
        accessibility.forEach { (view, saved) -> view.importantForAccessibility = saved }
        accessibility.clear()
        previousFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        previousFocus = null
    }
}
