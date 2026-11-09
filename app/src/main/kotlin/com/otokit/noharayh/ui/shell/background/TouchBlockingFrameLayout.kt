package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 装饰背景的触摸容器。
 *
 * 拦截触摸起点，避免内部 WebView 消费手势；不调用
 * `requestDisallowInterceptTouchEvent(true)`，父 Pager 仍可在横向拖动时正常接管并发送 CANCEL。
 * 自身可点击只是为了让默认 onTouchEvent 消费事件，不承载实际点击行为。
 */
internal class TouchBlockingFrameLayout(context: Context) : FrameLayout(context) {

    init {
        isClickable = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
}
