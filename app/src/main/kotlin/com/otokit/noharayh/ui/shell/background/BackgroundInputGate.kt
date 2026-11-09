package com.otokit.noharayh.ui.shell.background

/**
 * 选择提交门禁。
 *
 * 只有恢复/归位屏障结束、宿主 resumed 且背景输入允许时，`onPageSelected` 才算用户选择。
 * 纯数据便于单测，不参与渲染。
 */
internal data class BackgroundInputGate(
    val initialized: Boolean = false,
    val resumed: Boolean = false,
    val inputEnabled: Boolean = false,
    val barrierActive: Boolean = true,
    val generation: Long = 0,
) {
    val canCommit: Boolean
        get() = initialized && resumed && inputEnabled && !barrierActive

    fun beginAlignment(): BackgroundInputGate = copy(
        barrierActive = true,
        generation = generation + 1,
    )

    /** 本轮真实布局和像素端点同时成立才结束屏障；旧任务无权完成新一轮。 */
    fun confirmAlignment(
        expectedGeneration: Long,
        layoutCompleted: Boolean,
        atEndpoint: Boolean,
        motionStopped: Boolean,
    ): BackgroundInputGate = if (
        expectedGeneration == generation && barrierActive && layoutCompleted && atEndpoint && motionStopped
    ) {
        copy(initialized = true, barrierActive = false)
    } else {
        this
    }
}
