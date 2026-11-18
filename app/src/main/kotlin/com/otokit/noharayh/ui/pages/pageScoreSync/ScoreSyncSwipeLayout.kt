package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** 控件先接收按下。横向拖动交还背景 Pager；点按与纵向滚动留给原控件。 */
internal class ScoreSyncSwipeLayout(
    context: Context,
    private val pageSwipe: (MotionEvent) -> Boolean,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var down: MotionEvent? = null
    private var paging = false
    private var active = true

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            reset()
            val handled = super.dispatchTouchEvent(event)
            if (active && handled) down = MotionEvent.obtain(event)
            return handled
        }
        val first = down
        if (!paging && first != null) {
            if (action == MotionEvent.ACTION_POINTER_DOWN) reset()
            if (action == MotionEvent.ACTION_MOVE) {
                val dx = abs(event.x - first.x)
                val dy = abs(event.y - first.y)
                if (maxOf(dx, dy) > slop) {
                    if (dx > dy && pageSwipe(first)) {
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        paging = true
                    } else reset()
                }
            }
        }
        val handled = if (paging) {
            pageSwipe(event)
            true
        } else super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) reset()
        return handled
    }

    fun setInteractive(allowed: Boolean) {
        active = allowed
        if (!allowed) cancelPaging()
    }

    private fun cancelPaging() {
        if (paging) down?.let {
            it.action = MotionEvent.ACTION_CANCEL
            pageSwipe(it)
        }
        reset()
    }

    private fun reset() {
        paging = false
        down?.recycle()
        down = null
    }

    override fun onDetachedFromWindow() {
        cancelPaging()
        super.onDetachedFromWindow()
    }
}
