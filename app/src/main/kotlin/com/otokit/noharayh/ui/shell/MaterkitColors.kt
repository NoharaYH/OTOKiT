package com.otokit.noharayh.ui.shell

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.NoneThemePalette

/** 壁纸配色由背景与主壳共享；尚未取得壁纸色时使用系统动态色兜底。 */
internal object MaterkitColors {
    fun resolve(context: Context, wallpaperSeed: Int? = null, secondarySeed: Int? = null): ThemeColors {
        if (wallpaperSeed != null) return NoneThemePalette.fromSeed(wallpaperSeed, secondarySeed).colors
        val themed = DynamicColors.wrapContextIfAvailable(
            ContextThemeWrapper(context, R.style.Theme_OTOKiT_ColorSlider))
        return ThemeColors(
            MaterialColors.getColor(themed, androidx.appcompat.R.attr.colorPrimary, 0xFF599E7D.toInt()),
            MaterialColors.getColor(themed, com.google.android.material.R.attr.colorSecondary, 0xFF526457.toInt()),
        )
    }
}
