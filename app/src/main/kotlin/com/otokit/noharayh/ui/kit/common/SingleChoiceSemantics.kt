package com.otokit.noharayh.ui.kit.common

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** 只表达外部确认的单选状态；无障碍点击仍走原点击回调，不自行修改选择。 */
internal fun PressSurface.configureSingleChoiceSemantics(selected: Boolean, column: Int? = null) {
    isSelected = selected
    accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = "android.widget.RadioButton"
            info.isCheckable = true
            info.isChecked = host.isSelected
            if (column != null) {
                info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(
                    0, 1, column, 1, false, host.isSelected)
            }
        }
    }
}
