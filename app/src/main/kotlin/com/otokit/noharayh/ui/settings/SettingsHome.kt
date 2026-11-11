package com.otokit.noharayh.ui.settings

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.ui.kit.settings.SettingsOptionView
import com.otokit.noharayh.ui.shell.navigation.SettingsSection

/** 只组装四个分类入口，不持有选中项或动画时钟。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class SettingsHome(context: Context, onOpenSection: (SettingsSection) -> Unit) : ScrollView(context) {
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val options = settingsEntries.associate { entry ->
        entry.section to SettingsOptionView(context, context.getString(entry.label), entry.icon, entry.color).apply {
            setOnClickListener { onOpenSection(entry.section) }
        }
    }

    init {
        options.values.forEachIndexed { index, option ->
            body.addView(option, LinearLayout.LayoutParams(-1, -2).apply {
                if (index < options.size - 1) bottomMargin = (12 * resources.displayMetrics.density + 0.5f).toInt()
            })
        }
        addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
    }

    fun setContentMargins(margin: Int, split: Boolean) {
        body.setPadding(margin, margin, if (split) 0 else margin, margin)
    }
}
