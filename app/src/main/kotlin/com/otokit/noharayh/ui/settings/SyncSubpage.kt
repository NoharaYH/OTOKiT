package com.otokit.noharayh.ui.settings

import com.otokit.noharayh.R
import com.otokit.noharayh.ui.chrome.TitleText
import com.otokit.noharayh.ui.settings.subPages.ProxySettingsSection
import com.otokit.noharayh.ui.settings.subPages.ScoreArchiveKind

/** 数据与同步内部的有限三级目标，不扩展全局设置分类。 */
internal sealed interface SyncSubpage {
    val title: TitleText

    data class Archive(val kind: ScoreArchiveKind) : SyncSubpage {
        override val title get() = kind.title
    }

    data object Logs : SyncSubpage {
        override val title = TitleText(R.string.title_settings_sync_logs, R.string.settings_sync_logs)
    }

    data class Proxy(val section: ProxySettingsSection) : SyncSubpage {
        override val title get() = section.title
    }
}
