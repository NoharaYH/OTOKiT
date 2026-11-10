package com.otokit.noharayh.ui.chrome

/**
 * 外围 UI 向 shell 上报的应用额外占用，不含系统安全区。
 *
 * 纯契约：Assembly 只输出这里的四边数值，不参与内容几何计算。
 */
data class ChromeInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        val Zero: ChromeInsets = ChromeInsets()
    }
}
