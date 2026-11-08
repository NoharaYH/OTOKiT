package com.otokit.noharayh.ui.constants.typography

/**
 * 字体角色与资源路径。
 *
 * 路径是相对 assets 的 POSIX 路径；交给 AssetManager 时不带 `assets/` 前缀。
 * 这里只描述，不加载 Typeface、不持有 Context。
 */
object AppFonts {

    /** 固定字体缩放：保留系统/OEM density，但应用不跟随系统字体大小。 */
    const val FIXED_FONT_SCALE: Float = 1f

    const val BODY_ASSET_PATH: String = "fonts/jiangcheng_yuanti_500w.ttf"
    const val ENGLISH_TITLE_ASSET_PATH: String = "fonts/rogsans_title_ub.otf"

    enum class Role(val assetPath: String) {
        /** 正文与中文标题：江城圆体。 */
        Body(BODY_ASSET_PATH),

        /** 英文标题：ROG Sans UB，已是标题字形，不再叠加合成粗体。 */
        EnglishTitle(ENGLISH_TITLE_ASSET_PATH),
    }
}
