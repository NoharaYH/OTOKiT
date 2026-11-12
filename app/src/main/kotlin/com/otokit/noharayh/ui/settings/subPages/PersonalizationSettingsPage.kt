package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    onModeSelected: (ThemeSelectionMode) -> Unit,
    onOptionSelected: (ThemeOption, GameLine?) -> Unit,
) : ScrollView(context) {
    private var controlsEnabled = true
    private val fields = mutableListOf<LabeledDropdownField>()
    private val segment = SettingsSegmentedChoice(context)

    init {
        var state = selection
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (split) 0 else margin, margin, margin, margin)
            clipChildren = false
            clipToPadding = false
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        fun renderContent() {
            fields.forEach { it.dismissMenu() }
            fields.clear()
            content.removeAllViews()
            when (state.themeMode) {
                ThemeSelectionMode.None -> Unit
                ThemeSelectionMode.Independent -> {
                    val mai = field(
                        context = context,
                        label = context.getString(R.string.settings_theme_mai),
                        optionsFor = {
                            ThemeOptions.forGame(GameLine.Mai).filterNot {
                                it.id == state.maiThemeId
                            }
                        },
                        selectedTitle = ThemeOptions.resolveForGame(
                            GameLine.Mai,
                            state.maiThemeId,
                        ).title,
                        onSelected = { option ->
                            state = state.copy(maiThemeId = option.id)
                            onOptionSelected(option, GameLine.Mai)
                        },
                    )
                    val chu = field(
                        context = context,
                        label = context.getString(R.string.settings_theme_chu),
                        optionsFor = {
                            ThemeOptions.forGame(GameLine.Chu).filterNot {
                                it.id == state.chuThemeId
                            }
                        },
                        selectedTitle = ThemeOptions.resolveForGame(
                            GameLine.Chu,
                            state.chuThemeId,
                        ).title,
                        onSelected = { option ->
                            state = state.copy(chuThemeId = option.id)
                            onOptionSelected(option, GameLine.Chu)
                        },
                    )
                    content.addView(mai)
                    content.addView(chu, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = dp(context, 12)
                    })
                }
                ThemeSelectionMode.Merged -> {
                    val merged = field(
                        context = context,
                        label = context.getString(R.string.settings_theme_merged_field),
                        optionsFor = {
                            ThemeOptions.merged().filterNot { it.id == state.mergedThemeId }
                        },
                        selectedTitle = ThemeOptions.resolveMerged(state.mergedThemeId).title,
                        onSelected = { option ->
                            state = state.copy(mergedThemeId = option.id)
                            onOptionSelected(option, null)
                        },
                    )
                    content.addView(merged)
                }
            }
        }

        segment.setItems(
            labels = listOf(
                context.getString(R.string.settings_theme_independent),
                context.getString(R.string.settings_theme_merged),
                context.getString(R.string.settings_theme_none),
            ),
            selectedIndex = when (state.themeMode) {
                ThemeSelectionMode.Independent -> 0
                ThemeSelectionMode.Merged -> 1
                ThemeSelectionMode.None -> 2
            },
        ) { index ->
            val mode = when (index) {
                0 -> ThemeSelectionMode.Independent
                1 -> ThemeSelectionMode.Merged
                else -> ThemeSelectionMode.None
            }
            if (mode != state.themeMode) {
                state = state.copy(themeMode = mode)
                onModeSelected(mode)
                renderContent()
            }
        }

        body.addView(
            card(
                context = context,
                icon = R.drawable.ic_format_paint_24,
                title = R.string.settings_theme_selection,
                content = content,
                trailing = segment,
            ),
        )
        renderContent()

        isFillViewport = true
        clipChildren = false
        clipToPadding = false
        addView(body, FrameLayout.LayoutParams(-1, -2))
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
