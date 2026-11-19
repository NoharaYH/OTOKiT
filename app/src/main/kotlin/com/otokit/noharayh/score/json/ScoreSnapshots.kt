package com.otokit.noharayh.score.json

import com.otokit.noharayh.musicData.ScoreCatalog
import com.otokit.noharayh.score.*
import com.otokit.noharayh.storage.files.writeAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.format.DateTimeParseException

/** Single writer. The final file is immutable and is the input to the database importer. */
internal class ScoreSnapshots(
    private val root: File,
    private val loadCatalog: (ScoreGame) -> ScoreCatalog,
) {
    private val catalogs = mutableMapOf<ScoreGame, ScoreCatalog>()
    private var cachedDraft: Pair<String, JSONObject>? = null

    fun finalFile(batch: HtmlBatchRecord): File {
        require(batch.fileStamp.matches(Regex("[0-9]{14}")))
        val prefix = if (batch.batch.game == ScoreGame.MAIMAI) "MaiRecord" else "ChuRecord"
        return File(directory(batch.batch), prefix + "_Json_" + batch.fileStamp + ".json")
    }

    fun reference(batch: HtmlBatchRecord): ScoreSnapshotRef? = finalFile(batch).takeIf { it.isFile && !wasRemoved(batch.batch) }?.let {
        ScoreSnapshotRef(batch.batch, id(batch.batch), it.name, it.length())
    }

    private fun removedMarker(batch: HtmlBatchRef) = File(directory(batch), ".snapshot-removed")
    fun wasRemoved(batch: HtmlBatchRef): Boolean = removedMarker(batch).isFile

    fun removeArchive(batch: HtmlBatchRecord): Boolean {
        val file = finalFile(batch)
        if (file.exists()) removedMarker(batch.batch).createNewFile()
        return !file.exists() || file.delete()
    }

    fun advance(batch: HtmlBatchRecord, open: (HtmlPageRef) -> InputStream): File? {
        if (wasRemoved(batch.batch)) {
            removeArchive(batch)
            return null
        }
        val final = finalFile(batch)
        if (final.isFile) return final
        val draftFile = File(directory(batch.batch), "draft.json")
        val id = id(batch.batch)
        val draft = cachedDraft?.takeIf { it.first == id }?.second
            ?: draftFile.takeIf(File::isFile)?.let { JSONObject(it.readText()) }
                ?.takeIf { it.optInt("parserVersion") == NetScoreParser.VERSION && it.optString("id") == id }
            ?: JSONObject().put("id", id).put("parserVersion", NetScoreParser.VERSION).put("pages", JSONObject())
        val pages = draft.getJSONObject("pages")

        for (page in batch.pages) {
            if (page.kind !in batch.expectedPages || batch.evidence[page.kind]?.validity !in readable) continue
            val key = page.kind.name
            if (pages.optJSONObject(key)?.optString("sha256") == page.sha256) continue
            val result = JSONObject().put("sha256", page.sha256).put("capturedAt", page.capturedAt.toString())
            try {
                val parsed = open(page).use { NetScoreParser.parse(batch.batch.game, page.kind, it) }
                if (page.kind.isDifficulty && parsed.scores.size != batch.evidence.getValue(page.kind).scoreCount) {
                    throw InvalidScorePage()
                }
                parsed.player?.let {
                    result.put("profileId", profileId(batch.batch.game, it.friendCode)).put("displayName", it.name)
                }
                if (page.kind == HtmlPageKind.PLAYER && parsed.player == null) {
                    result.put("issue", "PLAYER_IDENTITY")
                } else {
                    result.put("scores", JSONArray(parsed.scores.map(::encode)))
                }
            } catch (_: InvalidScorePage) {
                result.put("issue", "PAGE_LAYOUT")
            } catch (_: DateTimeParseException) {
                result.put("issue", "PLAY_TIME")
            }
            pages.put(key, result)
            cachedDraft = null
            write(draftFile, draft)
        }
        cachedDraft = id to draft
        if (batch.status == HtmlBatchStatus.RUNNING) return null
        val successful = batch.expectedPages.filter {
            pages.optJSONObject(it.name)?.has("scores") == true
        }
        if (successful.none { it != HtmlPageKind.PLAYER }) return null
        val finalJson = assemble(batch, pages, successful)
        write(final, finalJson)
        draftFile.delete()
        cachedDraft = null
        return final
    }

    private fun assemble(batch: HtmlBatchRecord, pages: JSONObject, successful: List<HtmlPageKind>): JSONObject {
        val game = batch.batch.game
        val isChu = game == ScoreGame.CHUNITHM
        val catalog = catalogs.getOrPut(game) { loadCatalog(game) }
        val player = pages.optJSONObject(HtmlPageKind.PLAYER.name)
        val songs = linkedMapOf<Int, JSONObject>()
        val unmatched = JSONArray()
        val recent = JSONArray()
        for (page in successful) {
            if (page == HtmlPageKind.PLAYER) continue
            val data = pages.getJSONObject(page.name)
            val records = data.getJSONArray("scores")
            for (ordinal in 0 until records.length()) {
                val saved = records.getJSONObject(ordinal)
                val record = JSONObject(saved, saved.keys().asSequence().toList().toTypedArray())
                val difficulty = HtmlPageKind.valueOf(record.getString("difficulty"))
                val worldsEnd = if (record.isNull("special") || record.isNull("star")) null else
                    ScoreCatalog.WorldsEnd(record.getString("special"), record.getInt("star"))
                val matched = catalog.find(record.getString("title"), record.getString("chartType"), difficulty.chartIndex, worldsEnd)
                record.put("sourcePage", page.name).put("sourceOrdinal", ordinal)
                if (isChu) {
                    record.put("difficulty", difficulty.chartIndex)
                        .put("clear", if (record.isNull("clear")) JSONObject.NULL else record.getString("clear"))
                        .put("full_combo", record.remove("combo") ?: JSONObject.NULL)
                        .put("full_chain", record.remove("sync") ?: JSONObject.NULL)
                    if (matched != null && page != HtmlPageKind.RECENT) {
                        for (key in listOf("title", "chartType", "special", "star", "badgeIcon", "starIcon")) record.remove(key)
                    } else {
                        record.put("songId", matched?.id ?: JSONObject.NULL)
                    }
                } else {
                    record.put("capturedAt", data.getString("capturedAt")).put("songId", matched?.id ?: JSONObject.NULL)
                }
                if (page == HtmlPageKind.RECENT) {
                    recent.put(record)
                } else if (matched == null) {
                    unmatched.put(record)
                } else {
                    val song = songs.getOrPut(matched.id) { JSONObject(matched.data).put("records", JSONArray()) }
                    song.getJSONArray("records").put(record)
                }
            }
        }
        return JSONObject()
            .put("schemaVersion", 1).put("parserVersion", NetScoreParser.VERSION)
            .put("id", id(batch.batch)).put("batchId", batch.batch.id).put("game", batch.batch.game.key)
            .put("region", "CN").put("startedAt", batch.batch.startedAt.toString())
            .put("profileId", player?.optString("profileId")?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            .put("displayName", player?.optString("displayName")?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            .put("catalogVersion", catalog.version)
            .put("captureStatus", batch.status.name)
            .put("expectedPages", JSONArray(batch.expectedPages.map { it.name }))
            .put("includedPages", JSONArray(successful.map { it.name }))
            .put("missingPages", JSONArray(batch.expectedPages.filterNot { it in successful }.map { it.name }))
            .put("sources", JSONObject().apply {
                for (page in batch.pages.filter { it.kind in batch.expectedPages }) {
                    val result = pages.optJSONObject(page.kind.name)
                    val evidence = JSONObject().put("sha256", page.sha256).put("capturedAt", page.capturedAt.toString())
                        .put("issue", result?.optString("issue")?.takeIf(String::isNotBlank))
                    if (page.kind != HtmlPageKind.PLAYER) result?.optJSONArray("scores")?.let {
                        evidence.put("recordCount", it.length())
                    }
                    put(page.kind.name, evidence)
                }
            })
            .put("songs", JSONArray(songs.values)).put("unmatched", unmatched).put("recent", recent)
    }

    private fun encode(score: NetScore) = JSONObject().put("title", score.title).put("difficulty", score.difficulty.name)
        .put("chartType", score.chartType).put("achievement", score.achievement).put("score", score.score)
        .put("dxScore", score.dxScore).put("maxDxScore", score.maxDxScore).put("level", score.level).put("rank", score.rank)
        .put("combo", score.combo).put("sync", score.sync).put("special", score.special).put("playedAt", score.playedAt)
        .put("clear", score.clear).put("star", score.worldsEndStar).put("badgeIcon", score.badgeIcon).put("starIcon", score.starIcon)

    private fun profileId(game: ScoreGame, friendCode: String): String = MessageDigest.getInstance("SHA-256")
        .digest(("CN/" + game.key + "/" + friendCode).toByteArray()).joinToString("") { "%02x".format(it) }

    private fun directory(batch: HtmlBatchRef): File {
        require(batch.id.matches(Regex("[0-9]+-[a-f0-9]{8}")))
        return File(root, batch.game.key + "/" + batch.id)
    }

    private fun write(file: File, json: JSONObject) =
        file.writeAtomically { it.write(json.toString().toByteArray(Charsets.UTF_8)) }

    internal companion object {
        val readable = setOf(HtmlValidity.VALID, HtmlValidity.VALID_EMPTY_SCORES)
        fun id(batch: HtmlBatchRef) = batch.game.key + "/" + batch.id + "/1"
    }
}
