package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isNotEmpty
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.card
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.dp
import com.otokit.noharayh.ui.kit.settings.SettingsSegmentedChoice
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode

@android.annotation.SuppressLint("ViewConstructor") // 页面装配必须提供选择快照和事件回调。
internal class PersonalizationSettingsPage(
    context: Context,
    margin: Int,
    split: Boolean,
    selection: GameSelectionState,
    private val onModeSelected: (ThemeSelectionMode) -> Unit,
    private val onOptionSelected: (ThemeOption, GameLine?) -> Unit,
) : ScrollView(context) {
    private var controlsEnabled = true
    private val fields = mutableListOf<LabeledDropdownField>()
    private val segment = SettingsSegmentedChoice(context)

    // 只缓存最后一次确认的展示快照；点击不会在这里修改主题选择。
    private var renderedSelection: GameSelectionState? = null
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    init {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (split) 0 else margin, margin, margin, margin)
            clipChildren = false
            clipToPadding = false
        }
        segment.setItems(
            listOf(
                context.getString(R.string.settings_theme_independent),
                context.getString(R.string.settings_theme_merged),
                context.getString(R.string.settings_theme_none),
            ),
            modeIndex(selection.themeMode),
        ) { index ->
            onModeSelected(when (index) {
                0 -> ThemeSelectionMode.Independent
                1 -> ThemeSelectionMode.Merged
                else -> ThemeSelectionMode.None
            })
        }
        body.addView(card(context, R.drawable.ic_format_paint_24,
            R.string.settings_theme_selection, content, trailing = segment))
        renderSelection(selection)
        isFillViewport = true
        clipChildren = false
        clipToPadding = false
        addView(body, FrameLayout.LayoutParams(-1, -2))
    }

    fun renderSelection(selection: GameSelectionState) {
        val modeChanged = renderedSelection?.themeMode != selection.themeMode
        renderedSelection = selection
        segment.setSelection(modeIndex(selection.themeMode))
        if (modeChanged) {
            fields.forEach { it.dismissMenu() }
            fields.clear()
            content.removeAllViews()
            when (selection.themeMode) {
                ThemeSelectionMode.None -> Unit
                ThemeSelectionMode.Independent -> {
                    addField(R.string.settings_theme_mai, GameLine.Mai)
                    addField(R.string.settings_theme_chu, GameLine.Chu)
                }
                ThemeSelectionMode.Merged -> addField(R.string.settings_theme_merged_field, null)
            }
        }
        fields.forEachIndexed { index, field ->
            val option = if (selection.themeMode == ThemeSelectionMode.Merged) {
                ThemeOptions.resolveMerged(selection.mergedThemeId)
            } else {
                val game = if (index == 0) GameLine.Mai else GameLine.Chu
                ThemeOptions.resolveForGame(game, selection.selectedThemeId(game))
            }
            field.setValue(option.title)
        }
    }

    private fun addField(label: Int, game: GameLine?) {
        val field = field(context, context.getString(label), optionsFor = {
            val state = requireNotNull(renderedSelection)
            val selected = if (game == null) state.mergedThemeId else state.selectedThemeId(game)
            (if (game == null) ThemeOptions.merged() else ThemeOptions.forGame(game))
                .filterNot { it.id == selected }
        }, selectedTitle = "", onSelected = { onOptionSelected(it, game) })
        content.addView(field, LinearLayout.LayoutParams(-1, -2).apply {
            if (content.isNotEmpty()) topMargin = dp(context, 12)
        })
    }

    private fun modeIndex(mode: ThemeSelectionMode): Int = when (mode) {
        ThemeSelectionMode.Independent -> 0
        ThemeSelectionMode.Merged -> 1
        ThemeSelectionMode.None -> 2
    }

    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
        segment.setControlsEnabled(enabled)
        fields.forEach { it.setControlsEnabled(enabled) }
    }

    private fun field(
        context: Context,
        label: String,
        optionsFor: () -> List<ThemeOption>,
        selectedTitle: String,
        onSelected: (ThemeOption) -> Unit,
    ): LabeledDropdownField = LabeledDropdownField(context, label).apply {
        setValue(selectedTitle)
        fields += this
        setControlsEnabled(controlsEnabled)
        setOnClickListener {
            toggleMenu(optionsFor(), onSelected)
        }
    }
}
