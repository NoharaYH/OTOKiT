package com.otokit.noharayh.ui.window

import android.app.Activity
import android.view.View
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.otokit.noharayh.ui.window.breakpoints.WindowBreakpointPolicy
import com.otokit.noharayh.ui.window.insets.WindowInsetsReader
import com.otokit.noharayh.ui.window.metrics.WindowMetricsReader

/**
 * 当前 Activity 内唯一的窗口观测入口。
 *
 * 只负责采样、start/stop/dispose 与通知，不操作宿主 padding 或 margin。
 * `stop` 只暂停监听；`dispose` 是终态，释放 Activity/View/回调引用，重复调用无副作用。
 */
class WindowEnvironmentObserver(
    activity: Activity,
    root: View,
    onSnapshot: (WindowSnapshot) -> Unit,
) {

    private var activity: Activity? = activity
    private var root: View? = root
    private var onSnapshot: ((WindowSnapshot) -> Unit)? = onSnapshot

    private var started = false
    private var disposed = false
    private var latestInsets: WindowInsetsCompat? = null
    private var lastSnapshot: WindowSnapshot? = null

    private val sampleRunnable = Runnable { sample() }

    private val insetsListener = OnApplyWindowInsetsListener { _, insets ->
        latestInsets = insets
        scheduleSample()
        insets
    }

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        scheduleSample()
    }

    fun start() {
        if (disposed || started) return
        val root = root ?: return
        started = true
        lastSnapshot = null
        ViewCompat.setOnApplyWindowInsetsListener(root, insetsListener)
        root.addOnLayoutChangeListener(layoutListener)
        ViewCompat.requestApplyInsets(root)
        scheduleSample()
    }

    fun stop() {
        if (!started) return
        started = false
        val root = root ?: return
        root.removeCallbacks(sampleRunnable)
        ViewCompat.setOnApplyWindowInsetsListener(root, null)
        root.removeOnLayoutChangeListener(layoutListener)
        lastSnapshot = null
    }

    fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        activity = null
        root = null
        onSnapshot = null
    }

    private fun scheduleSample() {
        if (!started || disposed) return
        val root = root ?: return
        root.removeCallbacks(sampleRunnable)
        root.post(sampleRunnable)
    }

    private fun sample() {
        if (!started || disposed) return
        val activity = activity ?: return
        val root = root ?: return
        val insets = latestInsets ?: ViewCompat.getRootWindowInsets(root) ?: return

        val insetsSnapshot = WindowInsetsReader.read(insets)
        val metrics = WindowMetricsReader.read(activity, root)
        if (!metrics.isValid) return

        val widthDp = WindowBreakpointPolicy.stableWidthDp(
            widthPx = metrics.widthPx,
            stableLeftPx = insetsSnapshot.stableLeftPx,
            stableRightPx = insetsSnapshot.stableRightPx,
            density = metrics.density,
        ) ?: return

        val snapshot = WindowSnapshot(
            metrics = metrics,
            insets = insetsSnapshot,
            breakpointWidthDp = widthDp,
            layoutClass = WindowBreakpointPolicy.classify(widthDp),
        )
        if (snapshot == lastSnapshot) return

        lastSnapshot = snapshot
        onSnapshot?.invoke(snapshot)
    }
}
