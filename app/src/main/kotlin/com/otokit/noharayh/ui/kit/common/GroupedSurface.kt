package com.otokit.noharayh.ui.kit.common

/** 分组宿主提供位置，组件负责底形；无业务状态。 */
enum class GroupPosition(val first: Boolean, val last: Boolean) {
    Single(true, true), First(true, false), Middle(false, false), Last(false, true)
}

interface GroupedSurface {
    fun setGroupPosition(position: GroupPosition)
}
