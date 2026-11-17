package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.score.*
import java.io.InputStream

/** Streaming inspection uses a bounded overlap, including actual numeric score fields. */
internal object NetHtmlInspector {
    fun inspect(game: ScoreGame, kind: HtmlPageKind, source: InputStream, cancelled: () -> Boolean = { false }): HtmlPageEvidence {
        val charts = Regex(if (game == ScoreGame.MAIMAI) "music_name_block" else if (kind == HtmlPageKind.WORLDS_END) "class=\"musiclist_worldsend_title\"" else "class=\"music_title\"")
        val scores = Regex(if (game == ScoreGame.MAIMAI) {
            "music_score_block[^>]*>[\\s\\S]{0,256}?([0-9]{1,3}\\.[0-9]{4})\\s*%"
        } else "class=\"play_musicdata_highscore\"[^>]*>(?:\\s|<[^>]*>)*(?:分数[:：](?:\\s|<[^>]*>)*)?([0-9][0-9,]*)")
        val scoreElements = Regex(if (game == ScoreGame.MAIMAI) "music_score_block[^>]*w_112" else "class=\"play_musicdata_highscore\"")
        var chartCount = 0
        var scoreCount = 0
        var scoreElementCount = 0
        var html = false
        var end = false
        var template = false
        var businessError: String? = null
        val required = when (kind) {
            HtmlPageKind.PLAYER -> if (game == ScoreGame.MAIMAI) "friend_code_icon" else "class=\"player_name\""
            HtmlPageKind.RECENT -> if (game == ScoreGame.MAIMAI) "playlog" else "playlog"
            HtmlPageKind.LOGIN_DIAGNOSTIC -> if (game == ScoreGame.MAIMAI) "maimai-mobile" else "player_name"
            else -> if (game == ScoreGame.MAIMAI) "maimai-mobile" else "musiclist_box bg_${if (kind == HtmlPageKind.WORLDS_END) "worldsend" else kind.name.lowercase()}"
        }
        fun consume(text: String, limit: Int) {
            chartCount += charts.findAll(text).count { it.range.first < limit }
            scoreCount += scores.findAll(text).count { it.range.first < limit }
            scoreElementCount += scoreElements.findAll(text).count { it.range.first < limit }
            html = html || text.contains("<html", true)
            end = end || text.contains("</html>", true)
            template = template || text.contains(required)
            businessError = businessError ?: NetPageInspection.businessErrorCode(text)
        }
        source.reader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(8192)
            var carry = ""
            while (true) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = reader.read(chars)
                if (count < 0) break
                val text = carry + String(chars, 0, count)
                val limit = (text.length - 4096).coerceAtLeast(0)
                consume(text, limit)
                carry = text.substring(limit)
            }
            consume(carry, carry.length)
        }
        val validity = when {
            businessError != null -> HtmlValidity.BUSINESS_ERROR
            !html || !end || !template -> HtmlValidity.UNRECOGNIZED
            kind.isDifficulty && (chartCount == 0 || scoreCount < scoreElementCount) -> HtmlValidity.UNRECOGNIZED
            kind.isDifficulty && scoreCount == 0 -> HtmlValidity.VALID_EMPTY_SCORES
            else -> HtmlValidity.VALID
        }
        return HtmlPageEvidence(validity, chartCount, scoreCount, businessError)
    }
}
