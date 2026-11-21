package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 装饰背景的触摸容器。
 *
 * 拦截落在装饰背景区域内的触摸，避免 WebView 接收手势。
 * 自身可点击只是为了让默认 onTouchEvent 消费事件，不承载实际点击行为。
 */
internal class TouchBlockingFrameLayout(context: Context) : FrameLayout(context) {

    init {
        isClickable = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
}
