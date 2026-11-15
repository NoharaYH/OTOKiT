package com.otokit.noharayh.ui.theme

/** 纯资源描述；舞萌三层，中二两层。所有路径相对 assets。 */
data class ThemePreviewDescription(
    val assetFolder: String,
    val hasEyes: Boolean = true,
    val decorationFromRight: Boolean = false,
) {
    init {
        require(assetFolder.startsWith("theme-preview/") && !assetFolder.contains("..") &&
            !assetFolder.contains('\\') && !assetFolder.contains(':'))
    }
    val background: String get() = "$assetFolder/background.webp"
    val decoration: String get() = "$assetFolder/decoration.webp"
    val eyes: String? get() = if (hasEyes) "$assetFolder/eyes.webp" else null
}
