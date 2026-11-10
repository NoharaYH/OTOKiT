package com.otokit.noharayh.ui.window

/** 纯像素矩形，坐标系由调用方约定，左上右下的语义与 Android Rect 一致。 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    companion object {
        val Zero: PixelRect = PixelRect(0, 0, 0, 0)
    }
}

/** 窗口坐标转根局部坐标；保留尺寸与负坐标，由父容器负责裁剪。 */
fun PixelRect.relativeToRoot(rootLeftPx: Int, rootTopPx: Int): PixelRect = PixelRect(
    left = left - rootLeftPx,
    top = top - rootTopPx,
    right = right - rootLeftPx,
    bottom = bottom - rootTopPx,
)
