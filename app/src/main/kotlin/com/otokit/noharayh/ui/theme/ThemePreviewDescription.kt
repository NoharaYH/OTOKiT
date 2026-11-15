package com.otokit.noharayh.ui.theme

/** 纯资源描述；舞萌为背景与人物，中二只有背景。所有路径相对 assets。 */
data class ThemePreviewDescription(
    val assetFolder: String,
    val hasEyes: Boolean = true,
    val background: String = "$assetFolder/background.webp",
) {
    init {
        require(assetFolder.startsWith("theme-preview/") && !assetFolder.contains("..") &&
            !assetFolder.contains('\\') && !assetFolder.contains(':'))
        require(background.startsWith("theme-preview/") && background.endsWith(".webp") &&
            !background.contains("..") && !background.contains('\\') && !background.contains(':'))
    }
    val eyes: String? get() = if (hasEyes) "$assetFolder/eyes.webp" else null
}
