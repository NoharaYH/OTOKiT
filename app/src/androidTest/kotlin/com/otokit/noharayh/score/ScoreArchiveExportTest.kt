package com.otokit.noharayh.score

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.score.json.ScoreDatabase
import com.otokit.noharayh.score.json.ScoreSnapshots
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ScoreArchiveExportTest {
    @Test fun htmlRedactionChangesOnlyExportedBodiesAndPreservesArchiveNames() = Fixture().use { fixture ->
        val batches = ScoreGame.entries.map(fixture::seed).toSet()
        val records = batches.associateWith(fixture.html::read)
        val originals = records.values.flatMap { it.pages }.associateWith { page ->
            fixture.html.open(page).use { it.readBytes() }
        }
        val expected = originals.filterKeys { !it.kind.isDiagnostic }.mapKeys { (page, _) ->
            "${page.batch.game.key}/${page.batch.id}/${page.fileName}"
        }

        val raw = fixture.export(ScoreArchiveFormat.Html, batches, redact = false)
        val redacted = fixture.export(ScoreArchiveFormat.Html, batches, redact = true)
        assertEquals(expected.keys, raw.keys)
        assertEquals(expected.keys, redacted.keys)
        expected.forEach { (name, bytes) ->
            assertArrayEquals(name, bytes, raw.getValue(name))
            val text = redacted.getValue(name).toString(Charsets.UTF_8)
            assertTrue(name, text.contains("[REDACTED]"))
            listOf("export-player", "123456789012", "export-token", "export-team", "export-comment")
                .forEach { assertFalse("$name leaked $it", text.contains(it)) }
            if (name.endsWith("_Master.html")) {
                assertTrue(name, text.contains("Synthetic Chart &amp; 歌曲"))
                assertTrue(name, text.contains("100.1234%"))
                assertTrue(name, text.contains("value=\"42\""))
            }
        }
        originals.forEach { (page, bytes) ->
            assertArrayEquals(page.fileName, bytes, fixture.html.open(page).use { it.readBytes() })
        }
        records.forEach { (batch, record) -> assertEquals(record, fixture.html.read(batch)) }
        ScoreGame.entries.forEach { assertTrue(fixture.database.profiles(it).isEmpty()) }
    }

    @Test fun jsonExportRemainsByteExactWithEitherRedactionChoice() = Fixture().use { fixture ->
        val batches = ScoreGame.entries.map(fixture::seed).toSet()
        val jsonFiles = batches.associateWith { batch ->
            fixture.snapshots.finalFile(fixture.html.read(batch)).apply {
                check(parentFile!!.mkdirs())
                writeText("""{ "displayName": "export-player", "friendCode": "123456789012", "songs": [] }""",
                    Charsets.UTF_8)
            }
        }
        val expected = jsonFiles.mapKeys { (batch, file) -> "${batch.game.key}/${batch.id}/${file.name}" }
            .mapValues { (_, file) -> file.readBytes() }
        for (redact in listOf(false, true)) {
            val exported = fixture.export(ScoreArchiveFormat.Json, batches, redact)
            assertEquals(expected.keys, exported.keys)
            expected.forEach { (name, bytes) -> assertArrayEquals(name, bytes, exported.getValue(name)) }
        }
        jsonFiles.forEach { (batch, file) ->
            assertArrayEquals(expected.getValue("${batch.game.key}/${batch.id}/${file.name}"), file.readBytes())
        }
        ScoreGame.entries.forEach { assertTrue(fixture.database.profiles(it).isEmpty()) }
    }

    /** 隔离的测试归档及数据库；不使用应用容器，也不启动成绩派生处理。 */
    private class Fixture : Closeable {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val root = Files.createTempDirectory(context.cacheDir.toPath(), "score-export-").toFile()
        private val databaseName = "test-score-export-${UUID.randomUUID()}.db"
        val html = FileHtmlCaptures(File(root, "html"))
        val snapshots = ScoreSnapshots(File(root, "json")) { error("Export must not parse scores or load the catalog") }
        val database = ScoreDatabase(context, databaseName)
        private val archives = ScoreData(html, snapshots, database)

        fun seed(game: ScoreGame): HtmlBatchRef {
            val batch = html.begin(game, listOf(HtmlPageKind.PLAYER, HtmlPageKind.MASTER), "export-fixture",
                Instant.parse("2026-11-20T10:20:30Z"))
            val player = if (game == ScoreGame.MAIMAI) """
                <div class="name_block">export-player</div><img class="friend_code_icon"><div>123456789012</div>
                <div class="friend_comment_block">export-comment</div>
            """ else """
                <div class="player_name_in">export-player</div>
                <div class="user_data_friend_code"><div class="user_data_friend_tap"><span>123456789012</span></div></div>
                <div class="player_team_data_space">export-team</div>
            """
            val score = """
                <div class="music_name_block music_title">Synthetic Chart &amp; 歌曲</div>
                <div class="music_score_block w_112">100.1234%</div>
                <input type="hidden" name="idx" value="42"><input type="hidden" name="token" value="export-token">
            """
            for ((kind, text) in listOf(HtmlPageKind.PLAYER to player, HtmlPageKind.MASTER to score)) {
                val page = text.trimIndent().byteInputStream(Charsets.UTF_8).use { html.save(batch, kind, it) { false } }
                html.recordEvidence(page, HtmlPageEvidence(HtmlValidity.VALID))
            }
            "diagnostic must stay private".byteInputStream().use {
                html.save(batch, HtmlPageKind.FAILED_RESPONSE, it) { false }
            }
            html.finish(batch, HtmlBatchStatus.COMPLETED)
            return batch
        }

        fun export(format: ScoreArchiveFormat, batches: Set<HtmlBatchRef>, redact: Boolean): Map<String, ByteArray> {
            val completed = CompletableFuture<Boolean>()
            val output = ByteArrayOutputStream()
            archives.export(format, batches, redact, destination = { write -> write(output) }) { completed.complete(it) }
            assertTrue("Archive export failed", completed.get(5, TimeUnit.SECONDS))
            return ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                buildMap {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        put(entry.name, zip.readBytes())
                    }
                }
            }
        }

        override fun close() {
            database.close()
            context.deleteDatabase(databaseName)
            assertTrue("Temporary export fixture was not removed", root.deleteRecursively())
            assertFalse(root.exists())
            assertFalse(context.getDatabasePath(databaseName).exists())
        }
    }
}
