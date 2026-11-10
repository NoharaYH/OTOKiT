package com.otokit.noharayh.ui.pages

import android.content.Context
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.PageContent

/** 敬请期待占位：两音游各一份独立根，文字相同也不共用 View。 */
object ComingSoonPlaceholder {

    fun create(context: Context): PageContent = GamePlaceholderPage.create(
        context = context,
        maiDescription = context.getString(R.string.placeholder_mai_coming_soon),
        chuDescription = context.getString(R.string.placeholder_chu_coming_soon),
    )
}
