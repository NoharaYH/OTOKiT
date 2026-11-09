package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.R
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitleTextsTest {

    /** 三个功能 × 两个音游的六组文案映射与旧 NavText 一致。 */
    @Test
    fun mapsSixFunctionalTitles() {
        assertEquals(
            TitleText(R.string.title_mai_score_sync, R.string.title_zh_mai_score_sync),
            TitleTexts.forDestination(Destination.ScoreSync, GameLine.Mai),
        )
        assertEquals(
            TitleText(R.string.title_chu_score_sync, R.string.title_zh_chu_score_sync),
            TitleTexts.forDestination(Destination.ScoreSync, GameLine.Chu),
        )
        assertEquals(
            TitleText(R.string.title_mai_music_data, R.string.title_zh_mai_music_data),
            TitleTexts.forDestination(Destination.MusicData, GameLine.Mai),
        )
        assertEquals(
            TitleText(R.string.title_chu_music_data, R.string.title_zh_chu_music_data),
            TitleTexts.forDestination(Destination.MusicData, GameLine.Chu),
        )
    }

    /** 敬请期待不区分音游，两行都相同。 */
    @Test
    fun comingSoonIsGameIndependent() {
        val mai = TitleTexts.forDestination(Destination.ComingSoon, GameLine.Mai)
        val chu = TitleTexts.forDestination(Destination.ComingSoon, GameLine.Chu)

        assertEquals(mai, chu)
        assertEquals(TitleText(R.string.title_coming_soon, R.string.title_zh_coming_soon), mai)
    }

    /** 设置标题独立于功能与音游，且不与任何功能文案混用。 */
    @Test
    fun settingTitleIsSeparate() {
        assertEquals(TitleText(R.string.title_setting, R.string.title_zh_setting), TitleTexts.Setting)
        assertNotEquals(
            TitleTexts.Setting,
            TitleTexts.forDestination(Destination.ScoreSync, GameLine.Mai),
        )
    }
}
