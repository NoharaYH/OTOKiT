package com.otokit.noharayh.score.html

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class HtmlExportRedactorTest {
    @Test fun maimaiIdentityIsRemovedWhileMusicAndFormSelectorsSurvive() {
        val result = redact("""
            <div class="name_block f_l f_16" title="小&lt;明&amp;">小&lt;明&amp;</div>
            <img class="friend_code_icon"><div>123456789012345</div>
            <div class="friend_comment_block break f_12">私人个签</div>
            <div class="music_name_block">小明的歌 &amp; Friends</div>
            <div class="music_score_block w_112">100.1234%</div>
            <input name="idx" type="hidden" value="1234"><input name="diff" type="hidden" value="3">
            <input name="genre" value="99"><input name="playCheck" value="on">
            <input name="token" value="a&amp;b"><input type="hidden" name="unknownCredential" value="hidden-secret">
        """.trimIndent())
        assertFalse(result.contains("小&lt;明"))
        assertFalse(result.contains("123456789012345"))
        assertFalse(result.contains("私人个签"))
        assertFalse(result.contains("hidden-secret"))
        val doc = Jsoup.parse(result)
        assertEquals("[REDACTED]", doc.selectFirst(".name_block")!!.text())
        assertEquals("小明的歌 & Friends", doc.selectFirst(".music_name_block")!!.text())
        assertEquals("100.1234%", doc.selectFirst(".music_score_block")!!.text())
        assertEquals("1234", doc.selectFirst("[name=idx]")!!.attr("value"))
        assertEquals("3", doc.selectFirst("[name=diff]")!!.attr("value"))
        assertEquals("99", doc.selectFirst("[name=genre]")!!.attr("value"))
        assertEquals("on", doc.selectFirst("[name=playCheck]")!!.attr("value"))
        assertEquals("[REDACTED]", doc.selectFirst("[name=token]")!!.attr("value"))
    }

    @Test fun chunithmNameFriendCodeAndTeamAreMasked() {
        val result = redact("""
            <div class="player_name"><div class="player_name_in">玩家甲</div></div>
            <div class="user_data_friend_code"><div class="user_data_friend_tap"><span>987654321012</span></div></div>
            <div class="player_team_data_space"><span>私人队伍</span></div>
            <div class="music_title">Song</div><div class="play_musicdata_highscore">1,010,000</div>
        """.trimIndent())
        listOf("玩家甲", "987654321012", "私人队伍").forEach { assertFalse(result.contains(it)) }
        val doc = Jsoup.parse(result)
        assertEquals("[REDACTED]", doc.selectFirst(".player_name_in")!!.text())
        assertEquals("Song", doc.selectFirst(".music_title")!!.text())
        assertEquals("1,010,000", doc.selectFirst(".play_musicdata_highscore")!!.text())
    }

    @Test fun urlsCredentialsAndExecutableCopiesCannotRetainTheOriginalValues() {
        val result = redact("""
            <!-- comment-password -->
            <script>const token = 'script-secret';</script>
            <meta name="csrf-token" content="meta-secret">
            <a href="/record/?idx=1234&amp;diff=3&amp;%74oken=url-secret&amp;code=oauth-secret#state=state-secret"
               onclick="use('event-secret')" data-session-id="session-secret">成绩</a>
            <input type="password" value="password-secret">
            <textarea name="refresh_token">refresh-secret</textarea>
            <iframe srcdoc="srcdoc-secret"></iframe>
            <a href="javascript:use('javascript-secret')">链接</a>
        """.trimIndent())
        listOf("comment-password", "script-secret", "meta-secret", "url-secret", "oauth-secret", "state-secret",
            "event-secret", "session-secret", "password-secret", "refresh-secret", "srcdoc-secret", "javascript-secret")
            .forEach { assertFalse("Leaked $it", result.contains(it)) }
        val doc = Jsoup.parse(result)
        val href = doc.selectFirst("a")!!.attr("href")
        assertTrue(href.contains("idx=1234&diff=3"))
        assertTrue(href.contains("%74oken=[REDACTED]"))
        assertTrue(doc.select("script,[onclick],[srcdoc]").isEmpty())
    }

    @Test fun invalidEncodedQueryKeysAreMaskedRatherThanReturnedWithTheirValues() {
        val result = redact("<a href='/record/?%ZZ=unknown-secret&amp;idx=12'>成绩</a>")
        assertFalse(result.contains("unknown-secret"))
        assertTrue(result.contains("idx=12"))
    }

    private fun redact(html: String): String = ByteArrayOutputStream().also { output ->
        html.byteInputStream(Charsets.UTF_8).use { HtmlExportRedactor.write(it, output) }
    }.toString("UTF-8")
}
