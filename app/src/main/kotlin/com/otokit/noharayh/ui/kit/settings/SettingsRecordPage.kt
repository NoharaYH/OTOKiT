package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsChoiceDimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

/** 设置三级记录页的标准容器；记录与删除、导出等业务操作由调用方提供。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class SettingsRecordPage(
    context: Context,
    private val title: Int,
    margin: Int,
    split: Boolean,
    onRangeSelectionChanged: (Set<ArchiveRecordList.Row>, Boolean) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) : FrameLayout(context) {
    private var busy = false
    private var selectedCount = 0
    private var canDelete = true
    private var canExport = true
    private val list = ArchiveRecordList(context, context.getString(R.string.settings_archive_scroll)) { rows, select ->
        if (!busy) onRangeSelectionChanged(rows, select)
    }
    private val status = ViewPrimitives.text(context, "", 14f, SettingsComponentColors.TEXT)
    private val actions = PressFrontColumn(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val deleteAction = addHeaderAction(R.string.settings_archive_delete, SettingsComponentColors.ERROR_TEXT, true) {
        if (canDelete) onDelete()
    }
    private val exportAction = addHeaderAction(R.string.settings_archive_export, SettingsComponentColors.SELECTED, true) {
        if (canExport) onExport()
    }
    private val cancelAction = addHeaderAction(R.string.settings_archive_cancel, SettingsComponentColors.TEXT) {
        list.clearRangeAnchor()
        onCancel()
    }
    private val back = if (split) SettingsButton(context, context.getString(R.string.settings_archive_back),
        SettingsButton.Kind.Action, trailingIcon = SettingsButton.ActionIcon.Back,
        contentHeight = dp(SettingsCardDimensions.HEADER_HEIGHT_DP), onClick = onBack) else null
    private val header = SettingsHeaderActions(context, back, actions)
    private val card: SettingsCard

    init {
        setPadding(if (split) 0 else margin, margin, margin, margin)
        val viewport = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        card = SettingsCard(context, R.drawable.ic_archive_record_24, title,
            content = viewport, trailing = header, fillContent = true)
        addView(card, LayoutParams(-1, -1))
        showStatus(null)
        renderSelection(emptySet(), false)
    }

    private fun addHeaderAction(label: Int, color: Int, deferClick: Boolean = false, action: () -> Unit): SettingsCompactButton {
        val button = SettingsCompactButton(context, context.getString(label), color) {
            if (!busy && selectedCount > 0) action()
        }.apply { deferClickUntilReleaseFeedback = deferClick }
        actions.addView(button, LinearLayout.LayoutParams(button.minimumWidth, button.minimumHeight).apply {
            if (actions.childCount > 0) marginStart = dp(SettingsChoiceDimensions.GAP_DP)
        })
        return button
    }

    fun setRows(rows: List<ArchiveRecordList.Row>) = list.setRows(rows)

    fun renderSelection(selection: Set<ArchiveRecordList.Row>, busy: Boolean,
                        canDelete: Boolean = true, canExport: Boolean = true) {
        this.busy = busy
        this.selectedCount = selection.size
        this.canDelete = canDelete
        this.canExport = canExport
        list.rangeSelectionEnabled = !busy
        list.setSelection(selection)
        card.setTitle(if (selection.isEmpty()) context.getString(title)
            else context.getString(R.string.settings_archive_selected, selection.size))
        header.showActions(selection.isNotEmpty())
        deleteAction.isEnabled = !busy && selectedCount > 0 && canDelete
        exportAction.isEnabled = !busy && selectedCount > 0 && canExport
        cancelAction.isEnabled = !busy && selectedCount > 0
    }

    fun showStatus(message: Int?) {
        status.visibility = if (message == null) GONE else VISIBLE
        if (message != null) status.setText(message)
    }

    fun removeRows(rows: Set<ArchiveRecordList.Row>, finished: () -> Unit) = list.removeRows(rows, finished)
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)
}
