package com.otokit.noharayh.ui.theme

/** 纯资源描述；舞萌三层，中二两层。所有路径相对 assets。 */
data class ThemePreviewDescription(
    val assetFolder: String,
    val hasEyes: Boolean = true,
    val decorationFromRight: Boolean = false,
    val decorationCrop: ThemePreviewCrop? = null,
) {
    init {
        require(assetFolder.startsWith("theme-preview/") && !assetFolder.contains("..") &&
            !assetFolder.contains('\\') && !assetFolder.contains(':'))
    }
    val background: String get() = "$assetFolder/background.webp"
    val decoration: String get() = "$assetFolder/decoration.webp"
    val eyes: String? get() = if (hasEyes) "$assetFolder/eyes.webp" else null
}

/** 裁掉透明留白后在原画布中的位置，保证切入与静帧使用同一构图。 */
data class ThemePreviewCrop(
    val canvasWidth: Int, val canvasHeight: Int,
    val left: Int, val top: Int, val width: Int, val height: Int,
) {
    init {
        require(canvasWidth > 0 && canvasHeight > 0 && left >= 0 && top >= 0 && width > 0 && height > 0)
        require(left + width <= canvasWidth && top + height <= canvasHeight)
    }
}
