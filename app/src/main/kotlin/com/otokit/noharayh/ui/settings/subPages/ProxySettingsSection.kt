package com.otokit.noharayh.ui.settings.subPages

import com.otokit.noharayh.R
import com.otokit.noharayh.ui.chrome.TitleText

/** 数据与同步二级页的六个独立三级设置目标。 */
internal enum class ProxySettingsSection(val titleRes: Int, englishTitle: Int) {
    UPSTREAM(R.string.settings_proxy_upstream, R.string.title_settings_proxy_upstream),
    PORTS(R.string.settings_proxy_ports, R.string.title_settings_proxy_ports),
    LISTEN(R.string.settings_proxy_listen, R.string.title_settings_proxy_listen),
    CHECK(R.string.settings_proxy_check, R.string.title_settings_proxy_check),
    URLS(R.string.settings_proxy_urls, R.string.title_settings_proxy_urls),
    RESTORE(R.string.settings_proxy_restore, R.string.title_settings_proxy_restore);

    val title = TitleText(englishTitle, titleRes)
}
