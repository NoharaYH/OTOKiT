package com.otokit.noharayh.transfer.net

/** Extract only controlled numeric error metadata; never expose page contents. */
internal object NetPageInspection {
    fun businessErrorCode(prefix: String): String? =
        Regex("(?:错误码|エラーコード)\\s*[:：]\\s*([0-9]{4,12})(?![0-9])")
            .find(prefix)?.groupValues?.get(1)
}
