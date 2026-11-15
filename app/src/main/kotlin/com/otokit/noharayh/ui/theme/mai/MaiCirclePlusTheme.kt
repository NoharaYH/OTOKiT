package com.otokit.noharayh.ui.theme.mai

import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemePreviewCrop
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemeDefinition

/** Mai「CiRCLE+」主题：提供 HTML 相对入口、原始两色与静态预览描述。 */
object MaiCirclePlusTheme {

    /** 相对 assets 根目录的入口，不带 `assets/` 前缀。 */
    const val HTML_ASSET_PATH: String = "background/MaiCirclePlus/html/index.html"

    val definition: ThemeDefinition = ThemeDefinition(
        htmlAssetPath = HTML_ASSET_PATH,
        preview = ThemePreviewDescription("theme-preview/MaiCirclePlus", hasEyes = true, decorationFromRight = false, decorationCrop = ThemePreviewCrop(1152, 160, 166, 97, 887, 63)),
        colors = ThemeColors(
            primaryColor = 0xFFFF93D0.toInt(),
            secondaryColor = 0xFFC1A1FD.toInt(),
        ),
    )
}
