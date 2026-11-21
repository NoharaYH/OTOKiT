package com.otokit.noharayh.ui.animation.shell

import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.animation.common.PageTurnFrames
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.constants.motion.NavigationTransition

/**
 * 功能根切页执行器。
 *
 * contentHost 至多保留 active/exiting 两个功能根；每根内部的双音游由页面自己管理。
 * 只操作外层 scale/alpha/translationY，不触碰页面内部、导航状态或背景。
 */
class ContentTransitionRunner(
    private val host: FrameLayout,
    private val curve: () -> MotionCurve,
    private val onProgress: (TransitionProgress) -> Unit,
) {

    private var active: View? = null
    private var exiting: View? = null
    private var animator: ValueAnimator? = null
    private var token = 0

    val activeView: View?
        get() = active

    /** 首次挂载或恢复时直接落到唯一功能根。 */
    fun set(view: View) {
        token++
        cancelAnimator()
        exiting?.let { removeAndReset(it) }
        exiting = null
        val children = ArrayList<View>()
        for (index in 0 until host.childCount) children.add(host.getChildAt(index))
        for (child in children) {
            if (child !== view) removeAndReset(child)
        }
        ensureChild(view)
        reset(view)
        active = view
        onProgress(TransitionProgress(1f, running = false))
    }

    /**
     * 从当前 active 进入目标根；再次调用时旧 exiting 立即移除，
     * 当前 active 从现有帧继续退场，不得等定时器结束再处理。
     */
    fun run(from: View, to: View, direction: Int, onFinished: () -> Unit) {
        if (from === to) {
            set(to)
            return
        }
        token++
        val runToken = token
        cancelAnimator()
        exiting?.let { removeAndReset(it) }
        exiting = null

        ensureChild(to)
        if (from.parent !== host) {
            set(to)
            onFinished()
            return
        }

        if (!ValueAnimator.areAnimatorsEnabled() || host.height <= 0) {
            set(to)
            onFinished()
            return
        }

        val selectedCurve = curve()
        val distance = direction * NavigationTransition.pageDistanceDp(selectedCurve) * host.resources.displayMetrics.density
        val startFrom = TransitionFrame(from.alpha, from.scaleX, from.translationX, from.translationY)
        apply(to, PageTurnFrames.enter(0f, dy = distance))
        to.bringToFront()
        active = to
        exiting = from

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NavigationTransition.DURATION_MS
            interpolator = MotionInterpolators.of(selectedCurve)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                apply(to, PageTurnFrames.enter(p, dy = distance))
                apply(from, PageTurnFrames.exit(p, dy = -distance, from = startFrom))
                onProgress(TransitionProgress(p, running = true))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (token != runToken) return
                    animator = null
                    if (exiting === from) {
                        removeAndReset(from)
                        exiting = null
                    }
                    reset(to)
                    onProgress(TransitionProgress(1f, running = false))
                    onFinished()
                }
            })
            start()
        }
    }

    /** 窗口/设置/销毁时终止本轮，只保留已提交的 active 并落稳。 */
    fun cancel() {
        token++
        cancelAnimator()
        exiting?.let { removeAndReset(it) }
        exiting = null
        active?.let { reset(it) }
        onProgress(TransitionProgress(1f, running = false))
    }

    fun dispose() {
        token++
        cancelAnimator()
        exiting?.let { removeAndReset(it) }
        exiting = null
        active?.let { reset(it) }
        active = null
    }

    private fun ensureChild(view: View) {
        if (view.parent === host) return
        (view.parent as? ViewGroup)?.removeView(view)
        host.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun removeAndReset(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        reset(view)
    }

    private fun reset(view: View) {
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
    }

    private fun apply(view: View, frame: TransitionFrame) {
        view.alpha = frame.alpha.coerceIn(0f, 1f)
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
    }

    private fun cancelAnimator() {
        val previous = animator
        animator = null
        previous?.cancel()
    }
}
