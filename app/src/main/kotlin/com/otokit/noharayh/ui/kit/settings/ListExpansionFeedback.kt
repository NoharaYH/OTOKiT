package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.constants.motion.ListExpansionMotion

/** 同一行收缩到指定比例后，才可再次报告到达完整像素高度。 */
internal class ListExpansionFeedback(initiallyComplete: IntRange = IntRange.EMPTY) {
    private val completed = initiallyComplete.toMutableSet()

    fun update(index: Int, expansion: Float, atFullHeight: Boolean): Boolean {
        if (!atFullHeight && expansion <= ListExpansionMotion.FEEDBACK_REARM_FRACTION) forget(index)
        return atFullHeight && completed.add(index)
    }

    fun forget(index: Int) { completed.remove(index) }
}
