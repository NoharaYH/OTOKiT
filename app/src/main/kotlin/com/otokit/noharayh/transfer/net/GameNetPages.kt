package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureProtocolIssue
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream
import java.net.URI
import java.net.URLEncoder

/** Game-specific paths and form steps stay out of task/runtime/UI code. */
internal class GameNetPages(private val game: ScoreGame) {
    private val base = if (game == ScoreGame.MAIMAI) "https://maimai.wahlap.com/maimai-mobile/" else "https://chunithm.wahlap.com/mobile/"

    fun uri(kind: HtmlPageKind): URI = URI(base + when (kind) {
        HtmlPageKind.PLAYER -> if (game == ScoreGame.MAIMAI) "friend/userFriendCode/" else "home/playerData/"
        HtmlPageKind.RECENT -> if (game == ScoreGame.MAIMAI) "record/" else "record/playlog/"
        HtmlPageKind.UTAGE -> "record/musicGenre/search/?genre=99&diff=10"
        HtmlPageKind.WORLDS_END -> "record/worldsEndList/"
        else -> if (game == ScoreGame.MAIMAI) {
            "record/musicSort/search/?search=V&sort=1&playCheck=on&diff=${difficulties(game).indexOf(kind)}"
        } else "record/musicGenre/${kind.name.lowercase()}"
    })

    fun prepare(session: NetSession, kind: HtmlPageKind, rejected: (NetSession.Page) -> Unit = {}) {
        if (game != ScoreGame.CHUNITHM || !kind.isDifficulty || kind == HtmlPageKind.WORLDS_END) return
        val token = session.read(URI(base + "record/musicGenre/")) { response ->
                val prefix = ByteArray(1024 * 1024)
                var count = 0
                while (count < prefix.size) { val n = response.stream.read(prefix, count, prefix.size - count); if (n < 0) break; count += n }
                val text = String(prefix, 0, count, Charsets.UTF_8)
                fun preserve() { rejected(response.copy(stream = SequenceInputStream(ByteArrayInputStream(prefix, 0, count), response.stream))) }
                NetPageInspection.businessErrorCode(text)?.let { preserve(); throw NetBusinessException(it) }
                if (!response.contentType.contains("html", true)) {
                    preserve(); throw NetProtocolException(CaptureProtocolIssue.NON_HTML_RESPONSE)
                }
                val input = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(text).map { it.value }
                    .firstOrNull { Regex("name\\s*=\\s*['\"]token['\"]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                    ?: run { preserve(); throw NetProtocolException(CaptureProtocolIssue.FORM_TOKEN_MISSING) }
                Regex("value\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(input)?.groupValues?.get(1)
                    ?: run { preserve(); throw NetProtocolException(CaptureProtocolIssue.FORM_TOKEN_VALUE_MISSING) }
        }
        val command = "send" + kind.name.lowercase().replaceFirstChar { it.uppercase() }
        session.read(URI(base + "record/musicGenre/$command"), "genre=99&token=" + URLEncoder.encode(token, "UTF-8")) { }
    }

    companion object {
        fun difficulties(game: ScoreGame): List<HtmlPageKind> = if (game == ScoreGame.MAIMAI) {
            listOf(HtmlPageKind.BASIC, HtmlPageKind.ADVANCED, HtmlPageKind.EXPERT, HtmlPageKind.MASTER, HtmlPageKind.REMASTER, HtmlPageKind.UTAGE)
        } else listOf(HtmlPageKind.BASIC, HtmlPageKind.ADVANCED, HtmlPageKind.EXPERT, HtmlPageKind.MASTER, HtmlPageKind.ULTIMA, HtmlPageKind.WORLDS_END)
    }
}

internal class NetBusinessException(val code: String) : IllegalStateException("NET business error $code")
