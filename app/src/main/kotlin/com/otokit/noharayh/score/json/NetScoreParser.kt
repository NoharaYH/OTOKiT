package com.otokit.noharayh.score.json

import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class NetScore(
    val title: String,
    val difficulty: HtmlPageKind,
    val chartType: String,
    val achievement: Int? = null,
    val score: Int? = null,
    val dxScore: Int? = null,
    val maxDxScore: Int? = null,
    val level: String? = null,
    val rank: String? = null,
    val combo: String? = null,
    val sync: String? = null,
    val special: String? = null,
    val playedAt: String? = null,
    val clear: String? = null,
    val worldsEndStar: Int? = null,
    val badgeIcon: String? = null,
    val starIcon: String? = null,
)

internal data class NetPlayer(val friendCode: String, val name: String?)
internal data class NetScorePage(val player: NetPlayer? = null, val scores: List<NetScore> = emptyList())
internal class InvalidScorePage : IllegalArgumentException("Unexpected NET score layout")

internal object NetScoreParser {
    const val VERSION = 2
    private val friendCode = Regex("[0-9]{8,16}")
    private val achievement = Regex("([0-9]{1,3})\\.([0-9]{4})\\s*%")
    private val dx = Regex("([0-9,]+)\\s*/\\s*([0-9,]+)")
    private val playTime = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm")
    private val maiRanks = setOf("d", "c", "b", "bb", "bbb", "a", "aa", "aaa", "s", "sp", "ss", "ssp", "sss", "sssp")
    private val maiCombos = setOf("fc", "fcp", "ap", "app")
    private val maiSyncs = setOf("sync", "fs", "fsp", "fdx", "fdxp")
    private val chuCombos = setOf("fullcombo", "alljustice", "alljusticecritical")
    private val chuSyncs = setOf("fullchain", "fullchain2")
    private val chuClears = setOf("clear", "hard", "brave", "absolute", "catastrophy")
    private val chuRanks = listOf("d", "c", "b", "bb", "bbb", "a", "aa", "aaa", "s", "sp", "ss", "ssp", "sss", "sssp")
    // Confirmed against the CN NET list and catalog. Unknown icons stay as source evidence.
    private val worldsEndTags = mapOf(
        1 to "招", 2 to "狂", 3 to "止", 4 to "改", 5 to "両", 7 to "半", 8 to "時", 10 to "割",
        11 to "跳", 13 to "戻", 16 to "敷", 17 to "翔", 19 to "？", 20 to "！", 21 to "避",
        24 to "撃", 25 to "舞", 27 to "蔵",
    )

    fun parse(game: ScoreGame, kind: HtmlPageKind, input: InputStream): NetScorePage {
        val doc = Jsoup.parse(input, "UTF-8", "")
        if (kind == HtmlPageKind.PLAYER) {
            val selector = if (game == ScoreGame.MAIMAI) ".friend_code_icon + div" else ".user_data_friend_code .user_data_friend_tap span"
            val code = doc.select(selector).map { it.text() }.singleOrNull { friendCode.matches(it) }
            val name = doc.selectFirst(if (game == ScoreGame.MAIMAI) ".name_block" else ".player_name_in")?.text()
            return NetScorePage(player = code?.let { NetPlayer(it, name?.takeIf(String::isNotBlank)) })
        }
        val scores = when {
            game == ScoreGame.MAIMAI && kind == HtmlPageKind.RECENT ->
                doc.select(".playlog_top_container").map { maiRecent(it.parent() ?: throw InvalidScorePage()) }
            game == ScoreGame.MAIMAI -> {
                val cards = doc.select(".music_" + kind.suffix.lowercase() + "_score_back")
                if (cards.isEmpty()) throw InvalidScorePage()
                cards.mapNotNull { maiBest(kind, it) }
            }
            kind == HtmlPageKind.RECENT ->
                doc.select(".play_musicdata_score_text").map { chuRecent(it.closest(".frame02") ?: throw InvalidScorePage()) }
            else -> {
                val css = if (kind == HtmlPageKind.WORLDS_END) "worldsend" else kind.suffix.lowercase()
                val cards = doc.select(".musiclist_box.bg_" + css)
                if (cards.isEmpty()) throw InvalidScorePage()
                cards.mapNotNull { chuBest(kind, it) }
            }
        }
        return NetScorePage(scores = scores)
    }

    private fun maiBest(kind: HtmlPageKind, card: Element): NetScore? {
        val score = card.selectFirst(".music_score_block.w_112") ?: return null
        val icons = card.icons().filter { it.startsWith("music_icon_") }.map { it.removePrefix("music_icon_") }
        val type = if (kind == HtmlPageKind.UTAGE) "UTAGE" else maiType(card.selectFirst(".music_kind_icon")?.icon())
        val (dxScore, maxDxScore) = dxScore(card.selectFirst(".music_score_block.w_190")?.text())
        return NetScore(
            title = card.required(".music_name_block").wholeText(), difficulty = kind, chartType = type,
            achievement = parseAchievement(score.text()), dxScore = dxScore, maxDxScore = maxDxScore,
            level = card.selectFirst(".music_lv_block")?.text(), rank = icons.firstOrNull { it in maiRanks },
            combo = icons.firstOrNull { it in maiCombos } ?: "none".takeIf { icons.size == 3 && icons[1] == "back" },
            sync = icons.firstOrNull { it in maiSyncs } ?: "none".takeIf { icons.size == 3 && icons[0] == "back" },
            special = card.selectFirst(".music_kind_icon_utage_text")?.text(),
        )
    }

    private fun maiRecent(card: Element): NetScore {
        val kind = difficulty(card.required(".playlog_diff").icon().removePrefix("diff_"))
        val icons = card.select(".playlog_result_innerblock img").map { it.icon() }
        val (dxScore, maxDxScore) = dxScore(card.selectFirst(".playlog_score_block")?.text())
        val title = card.required(".basic_block.break")
        return NetScore(
            title = title.ownText().ifEmpty { title.textNodes().joinToString("") { it.wholeText }.trim('\r', '\n', '\t', ' ') },
            difficulty = kind, chartType = if (kind == HtmlPageKind.UTAGE) "UTAGE" else maiType(card.selectFirst(".playlog_music_kind_icon")?.icon()),
            achievement = parseAchievement(card.required(".playlog_achievement_txt").text()), dxScore = dxScore, maxDxScore = maxDxScore,
            level = card.selectFirst(".playlog_level_icon")?.text(), rank = card.selectFirst(".playlog_scorerank")?.icon(),
            combo = icons.firstOrNull { it in maiCombos } ?: "none".takeIf { "fc_dummy" in icons },
            sync = icons.firstOrNull { it in maiSyncs } ?: "none".takeIf { "sync_dummy" in icons },
            playedAt = time(card.selectFirst(".playlog_top_container .sub_title span:not(.red)")?.text()),
        )
    }

    private fun chuBest(kind: HtmlPageKind, card: Element): NetScore? {
        val score = card.selectFirst(".play_musicdata_highscore") ?: return null
        val worldsEnd = kind == HtmlPageKind.WORLDS_END
        return chuScore(kind, card, card.required(if (worldsEnd) ".musiclist_worldsend_title" else ".music_title").wholeText(),
            score.selectFirst(".text_b")?.text() ?: score.text().substringAfter('：'),
            badgeIcon = card.selectFirst(".musiclist_worldsend_icon img")?.icon(),
            starIcon = card.selectFirst(".musiclist_worldsend_star img")?.icon())
    }

    private fun chuRecent(card: Element): NetScore {
        val kind = difficulty(card.required(".play_track_result img").icon().removePrefix("musiclevel_"))
        val playedAt = card.selectFirst(".play_datalist_date")?.text()?.let {
            LocalDateTime.parse(it, playTime).atOffset(ZoneOffset.ofHours(8)).toString()
        }
        return chuScore(kind, card, card.required(".play_musicdata_title").wholeText(),
            card.required(".play_musicdata_score_text").text(), playedAt = playedAt,
            badgeIcon = card.selectFirst(".musiclist_worldsend_icon img")?.icon(),
            starIcon = card.selectFirst(".musiclist_worldsend_star img")?.icon())
    }

    private fun chuScore(
        kind: HtmlPageKind, card: Element, title: String, score: String,
        playedAt: String? = null, badgeIcon: String? = null, starIcon: String? = null,
    ): NetScore {
        val icons = card.select(".play_musicdata_icon img").map { it.icon().removePrefix("icon_") }
        val rank = icons.firstOrNull { it.startsWith("rank_") }?.removePrefix("rank_")?.toIntOrNull()?.let(chuRanks::getOrNull)
        val starImage = starIcon?.removePrefix("icon_we_star")?.toIntOrNull()
        val star = starImage?.takeIf { it in 1..9 && it % 2 == 1 }?.let { (it + 1) / 2 }
        val kanji = badgeIcon?.removePrefix("icon_we_")?.toIntOrNull()?.let(worldsEndTags::get)
        return NetScore(title, kind, if (kind == HtmlPageKind.WORLDS_END) "WORLDS_END" else "CHUNITHM",
            score = number(score), rank = rank,
            combo = icons.firstOrNull { it in chuCombos } ?: "none".takeIf { rank != null },
            sync = icons.firstOrNull { it in chuSyncs } ?: "none".takeIf { rank != null },
            special = kanji, playedAt = playedAt,
            clear = icons.firstOrNull { it in chuClears } ?: "failed".takeIf { rank != null },
            worldsEndStar = star, badgeIcon = badgeIcon, starIcon = starIcon)
    }

    private fun parseAchievement(text: String): Int {
        val value = achievement.matchEntire(text.replace(" ", "")) ?: throw InvalidScorePage()
        return value.groupValues[1].toInt() * 10_000 + value.groupValues[2].toInt()
    }

    private fun dxScore(text: String?): Pair<Int?, Int?> {
        if (text == null) return null to null
        val parts = dx.matchEntire(text) ?: throw InvalidScorePage()
        return number(parts.groupValues[1]) to number(parts.groupValues[2])
    }

    private fun number(text: String) = text.trim().replace(",", "").toIntOrNull() ?: throw InvalidScorePage()
    private fun time(text: String?) = text?.takeIf(String::isNotBlank)?.let { LocalDateTime.parse(it, playTime).toString() }
    private fun difficulty(value: String) =
        HtmlPageKind.entries.firstOrNull { it.isDifficulty && it.suffix.equals(value.replace("_", ""), true) }
            ?: throw InvalidScorePage()

    private fun maiType(icon: String?) = when (icon) {
        "music_standard" -> "SD"
        "music_dx" -> "DX"
        else -> throw InvalidScorePage()
    }

    private fun Element.required(selector: String) = selectFirst(selector) ?: throw InvalidScorePage()
    private fun Element.icons() = select("img").map { it.icon() }
    private fun Element.icon() = attr("src").substringBefore('?').substringAfterLast('/').removeSuffix(".png")
}
