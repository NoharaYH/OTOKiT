package com.otokit.noharayh.ui.kit.settings

import kotlin.math.abs
import kotlin.math.sign

/** 一次触摸的相对位移；落指只抓取，越过横向 slop 后才调值。 */
internal class RelativeSliderDrag(private val touchSlop: Float) {
    enum class Phase { Idle, Pressed, Dragging, Vertical }

    var phase = Phase.Idle
        private set
    var fraction = 0f
        private set
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var trackWidth = 1f
    private var direction = 1f

    fun begin(x: Float, y: Float, value: Float, width: Float, rtl: Boolean) {
        phase = Phase.Pressed
        fraction = value.coerceIn(0f, 1f)
        trackWidth = width.coerceAtLeast(1f)
        direction = if (rtl) -1f else 1f
        reanchor(x, y)
    }

    fun move(x: Float, y: Float): Float {
        if (phase == Phase.Pressed) {
            val dx = x - downX
            val dy = y - downY
            if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                phase = Phase.Vertical
            } else if (abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
                phase = Phase.Dragging
                // 扣掉用于识别拖动的死区，跨过阈值时不突然蹿一截。
                lastX = downX + sign(dx) * touchSlop
            }
        }
        if (phase == Phase.Dragging) {
            fraction = (fraction + direction * (x - lastX) / trackWidth).coerceIn(0f, 1f)
            // 即便抵达端点也更新锚点，反向移动立即生效，没有越界回程死区。
            lastX = x
        }
        return fraction
    }

    /** 主指抬起后交接到剩余手指，保持数值和拖动阶段。 */
    fun reanchor(x: Float, y: Float) {
        downX = x
        downY = y
        lastX = x
    }

    fun end() { phase = Phase.Idle }
}
