package com.otokit.noharayh.score

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.composition.personalScoreQueries
import com.otokit.noharayh.composition.scoreArchiveQueries
import com.otokit.noharayh.musicData.ScoreCatalog
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.score.json.ScoreDatabase
import com.otokit.noharayh.score.json.ScoreSnapshots
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ScoreIngestionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun draftRecoveryAndDatabaseReopenPreserveOneSnapshot() {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "score-ingestion").toFile()
        val databaseName = "test-score-" + UUID.randomUUID() + ".db"
        val html = FileHtmlCaptures(File(root, "html"))
        fun snapshots() = ScoreSnapshots(File(root, "json")) {
            ScoreCatalog("""
                {"version":"fixture","songs":[
                  {"basic_info":{"id":42,"title":"Synthetic Chart","type":"CHUNITHM"},"charts":[{"difficulty":3,"level":"12"}]}
                ]}
            """.trimIndent().byteInputStream())
        }
        try {
            val first = snapshots()
            val game = ScoreGame.CHUNITHM
            val batch = html.begin(game, listOf(HtmlPageKind.PLAYER, HtmlPageKind.MASTER), "test", Instant.now())
            val player = """
                <div class="user_data_friend_code"><div class="user_data_friend_tap">
                <span>Code</span><span>123456789012</span></div></div><div class="player_name_in">Fixture</div>
            """.trimIndent()
            val score = """
                <div class="musiclist_box bg_master"><div class="music_title">Synthetic Chart</div>
                <div class="play_musicdata_highscore"><span class="text_b">0</span></div></div>
                <div class="musiclist_box bg_master"><div class="music_title">Unplayed Chart</div></div>
            """.trimIndent()
            fun save(kind: HtmlPageKind, text: String, count: Int) {
                val page = html.save(batch, kind, text.byteInputStream()) { false }
                html.recordEvidence(page, HtmlPageEvidence(HtmlValidity.VALID, scoreCount = count))
            }
            save(HtmlPageKind.PLAYER, player, 0)
            assertNull(first.advance(html.read(batch), html::open))
            // A new writer resumes from disk. The player page is already in the draft.
            save(HtmlPageKind.MASTER, score, 1)
            html.finish(batch, HtmlBatchStatus.COMPLETED)
            val resumed = snapshots()
            val file = requireNotNull(resumed.advance(html.read(batch)) {
                assertEquals(HtmlPageKind.MASTER, it.kind)
                html.open(it)
            })
            assertTrue(file.name.matches(Regex("ChuRecord_Json_[0-9]{14}\\.json")))
            val data = file.readText()
            assertFalse(data.contains("123456789012"))
            val song = JSONObject(data).getJSONArray("songs").getJSONObject(0)
            assertEquals(3, song.getJSONArray("records").getJSONObject(0).getInt("difficulty"))
            ScoreDatabase(context, databaseName).use { db -> db.importFile(batch, file) }
            ScoreDatabase(context, databaseName).use { db ->
                db.importFile(batch, file)
                val profile = db.profiles(game).single()
                val record = db.scores(profile).items.single()
                assertEquals(0, record.score)
                assertEquals(42, record.songId)
                assertEquals("12", record.level)
                assertEquals(record, db.scoreDetail(profile, record.id))
                assertNull(db.scoreDetail(profile.copy(id = "other"), record.id))
                assertEquals(ScoreImportState.IMPORTED, db.importProgress(batch)?.state)

                val playerPage = html.read(batch).pages.single { it.kind == HtmlPageKind.PLAYER }
                var taskActive = true
                val archives = ScoreData(html, resumed, db) { taskActive }
                fun remove(format: ScoreArchiveFormat): Set<HtmlBatchRef> {
                    val completion = CompletableFuture<ScoreArchiveRemoval>()
                    archives.remove(format, setOf(batch)) { completion.complete(it) }
                    val removal = completion.get(5, TimeUnit.SECONDS)
                    assertEquals(0, removal.cleanupPending)
                    return removal.removed
                }
                fun zip(format: ScoreArchiveFormat): Map<String, String> {
                    val completion = CompletableFuture<Boolean>()
                    val output = ByteArrayOutputStream()
                    archives.export(format, setOf(batch), { it(output) }) { completion.complete(it) }
                    assertTrue(completion.get(5, TimeUnit.SECONDS))
                    return ZipInputStream(output.toByteArray().inputStream()).use { stream ->
                        buildMap {
                            while (true) {
                                val entry = stream.nextEntry ?: break
                                put(entry.name.substringAfterLast('/'), String(stream.readBytes(), Charsets.UTF_8))
                            }
                        }
                    }
                }
                assertEquals(setOf(player, score), zip(ScoreArchiveFormat.Html).values.toSet())
                assertEquals(mapOf(file.name to data), zip(ScoreArchiveFormat.Json))
                assertTrue(remove(ScoreArchiveFormat.Html).isEmpty())
                taskActive = false
                assertEquals(setOf(batch), remove(ScoreArchiveFormat.Json))
                assertNull(snapshots().advance(html.read(batch), html::open))
                assertFalse(file.exists())
                assertEquals(player, html.open(playerPage).bufferedReader().use { it.readText() })
                assertEquals(setOf(batch), remove(ScoreArchiveFormat.Html))
                assertTrue(archives.detail(batch).html.isEmpty())
                assertTrue(File(root, "html").walkTopDown().none { it.extension == "html" })
                assertEquals(record, db.scoreDetail(profile, record.id))
            }
        } finally {
            context.deleteDatabase(databaseName)
            root.deleteRecursively()
        }
    }

    @Test fun retainedDeviceArchivesPublishBothGamesAndExposeSqlResults() {
        val args = InstrumentationRegistry.getArguments()
        val mai = args.getString("maiBatch")
        val chu = args.getString("chuBatch")
        assumeTrue(mai != null && chu != null)
        val archives = scoreArchiveQueries(context)
        val scores = personalScoreQueries(context)
        for ((game, id) in listOf(ScoreGame.MAIMAI to mai, ScoreGame.CHUNITHM to chu)) {
            val batch = archives.listBatches().single { it.ref.game == game && it.ref.id == id }.ref
            val deadline = System.nanoTime() + 50_000_000_000
            var progress = archives.importProgress(batch)
            while (progress.state == ScoreImportState.WAITING && System.nanoTime() < deadline) {
                Thread.sleep(50)
                progress = archives.importProgress(batch)
            }
            val detail = archives.detail(batch)
            assertEquals(8, detail.html.size)
            assertTrue(archives.previewHtml(detail.html.first()).text.contains("<html", ignoreCase = true))
            val ref = requireNotNull(detail.batch.json)
            assertTrue(archives.previewJson(ref).text.startsWith("{\"schemaVersion\":1"))
            val profile = scores.profiles(game).single { it.id == progress.profileId }
            assertEquals(ScoreImportState.IMPORTED, progress.state)
            val best = scores.scores(profile, ScoreFilter(difficulty = HtmlPageKind.MASTER), limit = 200)
            assertTrue(best.items.any { it.batchId == id })
            assertNotNull(scores.scoreDetail(profile, best.items.first().id))
            val recent = scores.scores(profile, ScoreFilter(kind = ScoreRecordKind.RECENT), limit = 200).items
            assertTrue(recent.any { it.batchId == id && it.playedAt != null })
            if (game == ScoreGame.CHUNITHM) {
                val observations = mutableListOf<PersonalScore>()
                var offset: Int? = 0
                while (offset != null) {
                    val result = scores.scores(profile, offset = offset, limit = 200)
                    observations += result.items.filter { it.batchId == id }
                    offset = result.nextOffset
                }
                assertEquals(args.getString("chuBestCount")?.toInt(), observations.size)
                assertEquals(3, observations.count { it.score == 0 })
                assertEquals(7, observations.count { it.difficulty == HtmlPageKind.WORLDS_END })
                assertTrue(observations.all { it.songId != null && it.clear != null })
                val star = observations.single { it.songId == 8020 }
                assertEquals("戻", star.special)
                assertEquals(4, star.worldsEndStar)
            }
        }
    }
}
