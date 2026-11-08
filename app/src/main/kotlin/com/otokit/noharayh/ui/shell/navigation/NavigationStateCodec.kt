package com.otokit.noharayh.ui.shell.navigation

import android.os.Bundle

/**
 * 只负责 Bundle 字段的读取、类型检查与写入，不持有 Activity 或 View。
 *
 * 版本、路由与默认值规则由纯函数 [decodeNavigationState] / [encodeNavigationState] 决定。
 */
object NavigationStateCodec {

    const val BUNDLE_KEY: String = "ui_shell_state"

    private const val KEY_VERSION = "version"
    private const val KEY_ROUTE = "route"
    private const val KEY_SETTINGS_OPEN = "settings_open"

    /**
     * 使用无类型读取做类型检查：带类型的 getter 在类型不符时会抛异常或返回默认值，
     * 无法区分“缺失”和“错误类型”。该 API 自 33 起标记弃用，但 26—35 均可用。
     */
    @Suppress("DEPRECATION")
    fun read(bundle: Bundle?): NavigationState {
        val container = bundle?.getBundle(BUNDLE_KEY)
        return decodeNavigationState(
            version = container?.get(KEY_VERSION) as? Int,
            route = container?.get(KEY_ROUTE) as? String,
            settingsOpen = container?.get(KEY_SETTINGS_OPEN) as? Boolean,
        )
    }

    fun write(bundle: Bundle, state: NavigationState) {
        val saved = encodeNavigationState(state)
        val container = Bundle().apply {
            putInt(KEY_VERSION, saved.version)
            putString(KEY_ROUTE, saved.route)
            putBoolean(KEY_SETTINGS_OPEN, saved.settingsOpen)
        }
        bundle.putBundle(BUNDLE_KEY, container)
    }
}
