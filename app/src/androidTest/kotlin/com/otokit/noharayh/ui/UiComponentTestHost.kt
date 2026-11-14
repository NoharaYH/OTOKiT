package com.otokit.noharayh.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.otokit.noharayh.MainActivity
import org.junit.After
import org.junit.Before

/** 在真实 Activity 窗口挂载组件，通过系统输入分发验证，而非调用私有处理函数。 */
abstract class UiComponentTestHost {
    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected lateinit var activity: Activity
    protected lateinit var host: FrameLayout
    private var downTime = 0L

    @Before fun launchHost() {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        main {
            host = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE); isClickable = true }
            activity.addContentView(host, ViewGroup.LayoutParams(-1, -1))
        }
        instrumentation.waitForIdleSync()
    }

    @After fun closeHost() {
        if (::activity.isInitialized) main { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    protected fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    protected fun mount(view: View, width: Int = 300, height: Int = 180) {
        main {
            val systemTop = ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
            host.addView(view, FrameLayout.LayoutParams(width, height).apply {
                leftMargin = 80
                topMargin = maxOf(80, systemTop + 40)
            })
        }
        instrumentation.waitForIdleSync()
    }

    protected fun touch(view: View, action: Int, vararg points: Triple<Int, Float, Float>) {
        val origin = IntArray(2)
        main { view.getLocationOnScreen(origin) }
        val time = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val properties = points.map { (id, _, _) -> MotionEvent.PointerProperties().apply {
            this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = points.map { (_, x, y) -> MotionEvent.PointerCoords().apply {
            this.x = x + origin[0]; this.y = y + origin[1]; pressure = 1f; size = 1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(downTime, time, action, points.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        instrumentation.waitForIdleSync()
    }
}
