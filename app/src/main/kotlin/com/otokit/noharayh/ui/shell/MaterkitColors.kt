package com.otokit.noharayh.ui.shell

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.theme.ThemeColors

/** Shared system dynamic palette for Materkit's native background and shell chrome. */
internal object MaterkitColors {
    fun resolve(context: Context): ThemeColors {
        val themed = DynamicColors.wrapContextIfAvailable(
            ContextThemeWrapper(context, R.style.Theme_OTOKiT_ColorSlider))
        return ThemeColors(
            MaterialColors.getColor(themed, androidx.appcompat.R.attr.colorPrimary, 0xFF599E7D.toInt()),
            MaterialColors.getColor(themed, com.google.android.material.R.attr.colorSecondary, 0xFF526457.toInt()),
        )
    }
}
