package com.otokit.noharayh.ui.pages.pageMusicData

import android.content.Context
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.pages.GamePlaceholderPage

/** 歌曲详情占位：路由仍是 `music_data`，展示由六占位骨架负责。 */
object MusicDataPlaceholder {

    fun create(context: Context): PageContent = GamePlaceholderPage.create(
        context = context,
        maiDescription = context.getString(R.string.placeholder_mai_music_data),
        chuDescription = context.getString(R.string.placeholder_chu_music_data),
    )
}
