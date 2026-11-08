package com.otokit.noharayh.ui.shell.navigation

/**
 * 导航状态的唯一修改者。
 *
 * 所有命令在主线程由 shell 串行调用；命令只改状态，不操作 View。
 */
class NavigationController(initialState: NavigationState = NavigationState()) {

    var state: NavigationState = initialState
        private set

    /** 由状态派生，不额外保存副本。 */
    val canHandleBack: Boolean
        get() = state.settingsOpen

    /** 一次提交目标目的地并关闭设置；结果状态相同时才是无操作。 */
    fun navigate(destination: Destination) {
        val next = NavigationState(destination = destination, settingsOpen = false)
        if (next != state) state = next
    }

    /** 保留当前目的地，打开设置；重复打开无副作用。 */
    fun openSettings() {
        if (!state.settingsOpen) state = state.copy(settingsOpen = true)
    }

    /** 保留当前目的地，关闭设置；重复关闭无副作用。 */
    fun closeSettings() {
        if (state.settingsOpen) state = state.copy(settingsOpen = false)
    }

    /** 设置打开时关闭并返回已消费；设置关闭时不修改状态并返回未消费。 */
    fun consumeBack(): Boolean {
        if (!state.settingsOpen) return false
        state = state.copy(settingsOpen = false)
        return true
    }
}
