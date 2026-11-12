package com.otokit.noharayh.ui.animation.settings

import android.view.View
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion as Spec

/** 弹出容器的进退与取消；挂载及菜单释放由组件负责。 */
internal class ThemeDropdownTransitionRunner(private val view: View, private val translation: Float) {
    private var generation = 0
    fun show() {
        cancel()
        view.alpha = 0f
        view.translationY = translation
        view.animate().alpha(1f).translationY(0f).setDuration(Spec.MENU_OPEN_MS).start()
    }
    fun hide(onEnd: () -> Unit) {
        cancel()
        val token = generation
        view.animate().alpha(0f).translationY(translation).setDuration(Spec.MENU_CLOSE_MS)
            .withEndAction { if (generation == token) onEnd() }.start()
    }
    fun cancel() {
        generation++
        view.animate().withEndAction(null).cancel()
    }
}

/** DMI 箭头的唯一属性动画入口。 */
internal class ThemeDropdownArrowRunner(private val arrow: View) {
    fun render(expanded: Boolean) {
        arrow.animate().cancel()
        arrow.animate().rotation(if (expanded) 270f else 90f)
            .setDuration(Spec.ARROW_DURATION_MS).start()
    }
    fun reset() {
        arrow.animate().cancel()
        arrow.rotation = 90f
    }
}
