package com.otokit.noharayh.ui.settings

import com.otokit.noharayh.R
import com.otokit.noharayh.ui.chrome.TitleText
import com.otokit.noharayh.ui.settings.subPages.ScoreArchiveKind

/** 数据与同步内部的有限查看入口，不扩展全局设置分类。 */
internal sealed interface SyncRecordPage {
    val title: TitleText

    data class Archive(val kind: ScoreArchiveKind) : SyncRecordPage {
        override val title get() = kind.title
    }

    data object Logs : SyncRecordPage {
        override val title = TitleText(R.string.title_settings_sync_logs, R.string.settings_sync_logs)
    }
}
