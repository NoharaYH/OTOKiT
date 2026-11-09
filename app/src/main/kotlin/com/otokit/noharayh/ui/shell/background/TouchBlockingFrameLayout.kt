package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 装饰背景的触摸容器。
 *
 * 拦截触摸起点，避免内部 WebView 消费手势；不调用
 * `requestDisallowInterceptTouchEvent(true)`，父 Pager 仍可在横向拖动时正常接管并发送 CANCEL。
 */
internal class TouchBlockingFrameLayout(context: Context) : FrameLayout(context) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean = true
}
