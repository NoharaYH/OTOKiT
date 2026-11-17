package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.score.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NetHtmlInspectorTest {
    private fun inspect(body: String, kind: HtmlPageKind = HtmlPageKind.ULTIMA) =
        NetHtmlInspector.inspect(ScoreGame.CHUNITHM, kind, body.byteInputStream())

    @Test fun chartTitlesDoNotPretendToBeScores() {
        val evidence = inspect("<html><div class=\"musiclist_box bg_ultima\"><div class=\"music_title\">unplayed</div></div></html>")
        assertEquals(HtmlValidity.VALID_EMPTY_SCORES, evidence.validity)
        assertEquals(1, evidence.chartCount)
        assertEquals(0, evidence.scoreCount)
    }

    @Test fun numericScoreIsFoundAcrossStreamingWindowBoundaries() {
        val body = "<html>" + " ".repeat(8185) + "<div class=\"musiclist_box bg_ultima\"><div class=\"music_title\">played</div>" +
            "<div class=\"play_musicdata_highscore\">分数：<span>973,915</span></div></div></html>"
        val evidence = inspect(body)
        assertEquals(HtmlValidity.VALID, evidence.validity)
        assertEquals(1, evidence.chartCount)
        assertEquals(1, evidence.scoreCount)
    }

    @Test fun malformedScoreAndTruncatedDocumentAreNotValidEmptyScores() {
        val body = "<html><div class=\"musiclist_box bg_ultima\"><div class=\"music_title\">title</div><div class=\"play_musicdata_highscore\">unknown</div></div></html>"
        assertEquals(HtmlValidity.UNRECOGNIZED, inspect(body).validity)
        assertEquals(HtmlValidity.UNRECOGNIZED, inspect(body.replace("</html>", "")).validity)
    }

    @Test fun worldsEndUsesItsOwnTitleAndScoreStructure() {
        val body = "<html><div class=\"musiclist_box bg_worldsend\"><div class=\"musiclist_worldsend_title\">title</div>" +
            "<div class=\"play_musicdata_highscore\">分数：1,000,000</div></div></html>"
        val evidence = inspect(body, HtmlPageKind.WORLDS_END)
        assertEquals(HtmlValidity.VALID, evidence.validity)
        assertEquals(1, evidence.scoreCount)
    }

    @Test fun optionalPrivateDeviceEvidenceMatchesNumericScoreCounts() {
        val root = System.getenv("OTOKIT_CAPTURE_EVIDENCE_ROOT")?.let(::File) ?: return
        val expected = mapOf(0 to 0, 1 to 5, 2 to 53, 3 to 80, 4 to 1, 5 to 7)
        val kinds = GameNetPages.difficulties(ScoreGame.CHUNITHM)
        root.walkTopDown().filter { it.isFile && it.name.matches(Regex("difficulty_[0-5]\\.html")) }.forEach { file ->
            val diff = file.name.removePrefix("difficulty_").removeSuffix(".html").toInt()
            val evidence = file.inputStream().use { NetHtmlInspector.inspect(ScoreGame.CHUNITHM, kinds[diff], it) }
            assertTrue("${file.name}: $evidence", evidence.validity in listOf(HtmlValidity.VALID, HtmlValidity.VALID_EMPTY_SCORES))
            assertEquals(file.name, expected.getValue(diff), evidence.scoreCount)
        }
    }

    @Test fun optionalPrivateMaimaiEvidenceContainsRealAchievementFields() {
        val root = System.getenv("OTOKIT_MAIMAI_EVIDENCE_ROOT")?.let(::File) ?: return
        val kinds = GameNetPages.difficulties(ScoreGame.MAIMAI)
        root.walkTopDown().filter { it.isFile && it.name.matches(Regex("difficulty_(0|1|2|3|4|10)\\.html")) }.forEach { file ->
            val diff = file.name.removePrefix("difficulty_").removeSuffix(".html").toInt()
            val evidence = file.inputStream().use { NetHtmlInspector.inspect(ScoreGame.MAIMAI, kinds[if (diff == 10) 5 else diff], it) }
            assertEquals(file.name, HtmlValidity.VALID, evidence.validity)
            assertTrue(file.name, evidence.chartCount > 0)
            if (diff == 10) assertTrue(file.name, evidence.scoreCount in 1..evidence.chartCount)
            else assertEquals(file.name, evidence.chartCount, evidence.scoreCount)
        }
    }
}
