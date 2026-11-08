package com.otokit.noharayh.ui.shell.navigation

/**
 * 导航的稳定状态，只由 [NavigationController] 修改。
 *
 * 只有一份 destination 和一个设置开关，不保存“设置打开前的目的地”等第二份状态。
 */
data class NavigationState(
    val destination: Destination = Destination.ScoreSync,
    val settingsOpen: Boolean = false,
)

/** 保存到 Bundle 的纯字段集合，不含 Android 类型。 */
data class SavedNavigationState(
    val version: Int,
    val route: String,
    val settingsOpen: Boolean,
)

const val NAVIGATION_STATE_VERSION: Int = 1

/**
 * 把可空的保存字段转换为合法状态。
 *
 * 版本或路由无效时整份回退到默认状态；版本与路由合法但开关缺失时按关闭处理。
 */
fun decodeNavigationState(
    version: Int?,
    route: String?,
    settingsOpen: Boolean?,
): NavigationState {
    if (version != NAVIGATION_STATE_VERSION) return NavigationState()
    val destination = Destination.fromSavedValue(route) ?: return NavigationState()
    return NavigationState(destination = destination, settingsOpen = settingsOpen ?: false)
}

/** 把状态转换为纯字段值，由适配器负责写入 Bundle。 */
fun encodeNavigationState(state: NavigationState): SavedNavigationState =
    SavedNavigationState(
        version = NAVIGATION_STATE_VERSION,
        route = state.destination.savedValue,
        settingsOpen = state.settingsOpen,
    )
