package com.otokit.noharayh.ui.kit.common

import android.view.View
import com.otokit.noharayh.ui.animation.common.GameFrame

/**
 * 页面提供自己的展示根、颜色与双音游帧入口；调用方无需了解内部 View 结构。
 *
 * 页面的 Mai/Chu 两根只在页面内部创建和持有；shell 只传纯帧，不取得内部节点。
 */
class PageContent(
    val view: View,
    private val colorUpdater: (Int) -> Unit = {},
    private val frameUpdater: ((GameFrame) -> Unit)? = null,
    private val interactiveUpdater: ((Boolean) -> Unit)? = null,
    private val disposeAction: (() -> Unit)? = null,
) {
    fun updateColor(color: Int) {
        colorUpdater(color)
    }

    fun updateGameFrame(frame: GameFrame) {
        frameUpdater?.invoke(frame)
    }

    fun setInteractive(interactive: Boolean) {
        interactiveUpdater?.invoke(interactive)
    }

    fun dispose() {
        disposeAction?.invoke()
    }
}
