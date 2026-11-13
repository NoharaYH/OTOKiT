package com.otokit.noharayh.ui.shell.background

/** 背景运行选项的确认快照；不持有 View、偏好或动画对象。 */
data class BackgroundRenderingOptions(
    val hardwareAccelerated: Boolean = true,
    val htmlAnimations: Boolean = true,
)
