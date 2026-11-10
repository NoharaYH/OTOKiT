package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.pages.GamePlaceholderPage

/** 成绩同步占位：Mai/Chu 两个独立测试根，由页面自己持有。 */
object ScoreSyncPlaceholder {

    fun create(context: Context): PageContent = GamePlaceholderPage.create(
        context = context,
        maiDescription = context.getString(R.string.placeholder_mai_score_sync),
        chuDescription = context.getString(R.string.placeholder_chu_score_sync),
    )
}
