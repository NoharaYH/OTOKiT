package com.otokit.noharayh.ui.theme.chu

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** CHUNITHM「X-Verse-X」主题：提供 HTML 相对入口、原始两色与静态预览描述。 */
object ChuXVerseXTheme {

    /** 相对 assets 根目录的入口，不带 `assets/` 前缀。 */
    const val HTML_ASSET_PATH: String = "background/ChuXVerseX/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        preview = ThemePreviewDescription("theme-preview/ChuXVerseX", hasEyes = false, decorationFromRight = true),
        colors = ThemeColors(
            primaryColor = 0xFF6F8CFF.toInt(),
            secondaryColor = 0xFFB4CBF6.toInt(),
        ),
    )
}
