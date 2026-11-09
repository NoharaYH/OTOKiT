package com.otokit.noharayh.ui.constants.dimensions

import com.otokit.noharayh.R

/**
 * 外壳固定尺寸的资源 ID 统一入口。
 *
 * 真值只存在于 `res/values/dimens.xml`：这里不复制 dp/sp 数值，避免出现第二份来源；
 * 也不读取 Resources 或当前窗口。
 */
object ShellDimensions {

    val navCapsuleHeight: Int = R.dimen.nav_capsule_height
    val navCapsuleIconCircle: Int = R.dimen.nav_capsule_icon_circle
    val navCapsuleIconInner: Int = R.dimen.nav_capsule_icon_inner
    val navCapsulePaddingStart: Int = R.dimen.nav_capsule_padding_start
    val navCapsulePaddingVertical: Int = R.dimen.nav_capsule_padding_v
    val navCapsulePaddingEnd: Int = R.dimen.nav_capsule_padding_end
    val navCapsuleTextGap: Int = R.dimen.nav_capsule_text_gap
    val navCapsuleListSpacing: Int = R.dimen.nav_capsule_list_spacing
    val navCapsuleSubLabelTextSize: Int = R.dimen.nav_capsule_sub_label_text_size
    val navCapsuleLabelZhTextSize: Int = R.dimen.nav_capsule_label_zh_text_size

    val chromeEnglishTextLeadingInset: Int = R.dimen.chrome_en_text_leading_inset
    val chromeDualLineTextOpticalNudgeY: Int = R.dimen.chrome_dual_line_text_optical_nudge_y

    val titleBarPadding: Int = R.dimen.title_bar_padding
    val titleBarIconSize: Int = R.dimen.title_bar_icon_size
    val chromeDualLineGap: Int = R.dimen.chrome_dual_line_gap
    val navCapsuleListTop: Int = R.dimen.nav_capsule_list_top
    val navCapsuleSettingsGap: Int = R.dimen.nav_capsule_settings_gap
    val navCapsuleCornerRadius: Int = R.dimen.nav_capsule_corner_radius

    val padNavRailWidth: Int = R.dimen.pad_nav_rail_width
    val padNavRailIconCircle: Int = R.dimen.pad_nav_rail_icon_circle
    val padNavRailIconInner: Int = R.dimen.pad_nav_rail_icon_inner
    val padNavRailLabelTextSize: Int = R.dimen.pad_nav_rail_label_text_size

    val shellComponentSpacing: Int = R.dimen.shell_component_spacing
    val shellSideNavWidth: Int = R.dimen.shell_side_nav_width
}

/** 设计比例没有单位，不在资源和组件之间复制固定尺寸。 */
object ShellProportions {
    const val TITLE_BUTTON_SCALE = 2.55f
    const val TITLE_ENGLISH_SIZE = 0.30f
    const val TITLE_CHINESE_SIZE = 0.506f
    const val NAV_CAPSULE_WIDTH = 0.69f
    const val DRAWER_MAX_WINDOW_WIDTH = 0.92f
}
