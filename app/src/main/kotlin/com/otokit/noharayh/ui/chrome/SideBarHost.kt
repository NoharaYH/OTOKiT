package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * 窄屏抽屉的共同父容器。
 *
 * 全尺寸、无 padding/margin：内容场景仍是原来的四层宿主，窗口坐标原点不变。
 * 遮罩与抽屉面板后续作为该容器的直接子节点加入，位于内容场景之上。
 */
class SideBarHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr)
