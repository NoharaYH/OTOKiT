package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/** 观察整棵 Pager 的手势流，不把 Pager 正常接管时发给子页的 CANCEL 当作整轮取消。 */
internal class BackgroundTouchFrameLayout(context: Context) : FrameLayout(context) {
    var beforeGestureCancel: (() -> Unit)? = null
    var afterGestureCancel: (() -> Unit)? = null
    var layoutEpoch: Long = 0
        private set

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_CANCEL) return super.dispatchTouchEvent(event)
        beforeGestureCancel?.invoke()
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            afterGestureCancel?.invoke()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutEpoch++
    }
}
