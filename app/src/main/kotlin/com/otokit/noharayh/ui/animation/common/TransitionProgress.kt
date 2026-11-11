package com.otokit.noharayh.ui.animation.common

/** 已完成曲线映射的展示进度；接收者只应用空间幅度，不再计时或插值。 */
data class TransitionProgress(val value: Float, val running: Boolean)
