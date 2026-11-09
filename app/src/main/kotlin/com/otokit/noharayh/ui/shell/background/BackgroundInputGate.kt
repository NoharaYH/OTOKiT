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
) {
    val canCommit: Boolean
        get() = initialized && resumed && inputEnabled && !barrierActive
}
