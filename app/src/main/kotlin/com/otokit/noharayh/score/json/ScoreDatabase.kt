package com.otokit.noharayh.score.json

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.otokit.noharayh.score.*
import com.otokit.noharayh.storage.database.writeTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

internal class ScoreDatabase(context: Context, name: String = "score-records.db") :
    SQLiteOpenHelper(context, name, null, 2), PersonalScoreQueries {
    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE snapshots (
                id TEXT PRIMARY KEY NOT NULL, batch_id TEXT NOT NULL, game TEXT NOT NULL,
                profile_id TEXT, display_name TEXT, started_at INTEGER NOT NULL, partial INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE scores (
                id INTEGER PRIMARY KEY, snapshot_id TEXT NOT NULL REFERENCES snapshots(id),
                source_page TEXT NOT NULL, source_ordinal INTEGER NOT NULL, captured_at TEXT NOT NULL,
                record_kind TEXT NOT NULL, title TEXT NOT NULL, difficulty TEXT NOT NULL, chart_type TEXT NOT NULL,
                song_id INTEGER, achievement INTEGER, score INTEGER, dx_score INTEGER, max_dx_score INTEGER,
                level TEXT, rank TEXT, combo TEXT, sync TEXT, special TEXT, played_at TEXT,
                clear TEXT, worlds_end_star INTEGER,
                UNIQUE(snapshot_id, source_page, source_ordinal)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX snapshots_profile ON snapshots(game, profile_id, started_at)")
        db.execSQL("CREATE INDEX scores_difficulty ON scores(snapshot_id, record_kind, difficulty, song_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2)
        db.execSQL("ALTER TABLE scores ADD COLUMN clear TEXT")
        db.execSQL("ALTER TABLE scores ADD COLUMN worlds_end_star INTEGER")
    }

    fun importProgress(batch: HtmlBatchRef): ScoreImportProgress? =
        readableDatabase.rawQuery("SELECT partial, profile_id FROM snapshots WHERE id = ?", arrayOf(ScoreSnapshots.id(batch))).use {
            if (!it.moveToFirst()) null else ScoreImportProgress(
                if (it.getInt(0) == 0) ScoreImportState.IMPORTED else ScoreImportState.PARTIAL,
                it.getString(1),
            )
        }

    fun importFile(batch: HtmlBatchRef, file: File) {
        val id = ScoreSnapshots.id(batch)
        if (importProgress(batch) != null) return
        val json = JSONObject(file.readText())
        require(json.getInt("schemaVersion") == 1 && json.getString("id") == id &&
            json.getString("batchId") == batch.id && json.getString("game") == batch.game.key)

        // Decode the immutable file outside the write transaction.
        val sources = json.getJSONObject("sources")
        val records = buildList {
            val songs = json.getJSONArray("songs")
            for (i in 0 until songs.length()) {
                val song = songs.getJSONObject(i)
                addRecords(id, sources, song.getJSONArray("records"), ScoreRecordKind.BEST, song)
            }
            addRecords(id, sources, json.getJSONArray("unmatched"), ScoreRecordKind.BEST)
            addRecords(id, sources, json.getJSONArray("recent"), ScoreRecordKind.RECENT)
        }
        writableDatabase.writeTransaction {
            val inserted = insertWithOnConflict("snapshots", null, ContentValues().apply {
                put("id", id)
                put("batch_id", batch.id)
                put("game", batch.game.key)
                put("profile_id", json.stringOrNull("profileId"))
                put("display_name", json.stringOrNull("displayName"))
                put("started_at", batch.startedAt.toEpochMilli())
                put("partial", if (json.getJSONArray("missingPages").length() == 0) 0 else 1)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted != -1L) records.forEach { insertOrThrow("scores", null, it) }
        }
    }

    private fun MutableList<ContentValues>.addRecords(
        id: String, sources: JSONObject, records: JSONArray, kind: ScoreRecordKind, song: JSONObject? = null,
    ) {
        val basic = song?.getJSONObject("basic_info")
        val worldsEnd = song?.optJSONObject("worlds_end_info")
        val charts = song?.optJSONArray("charts")
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val page = record.getString("sourcePage")
            val difficulty = when (val value = record.get("difficulty")) {
                is Number -> chuDifficulties[value.toInt()]
                else -> HtmlPageKind.valueOf(value.toString())
            }
            val chart = charts?.let { list ->
                (0 until list.length()).asSequence().map(list::getJSONObject)
                    .firstOrNull { it.getInt("difficulty") == difficulty.chartIndex }
            }
            add(ContentValues().apply {
                put("snapshot_id", id)
                put("source_page", page)
                put("source_ordinal", record.getInt("sourceOrdinal"))
                put("captured_at", record.stringOrNull("capturedAt") ?: sources.getJSONObject(page).getString("capturedAt"))
                put("record_kind", kind.name)
                put("title", record.stringOrNull("title") ?: requireNotNull(basic).getString("title"))
                put("difficulty", difficulty.name)
                put("chart_type", record.stringOrNull("chartType")
                    ?: if (worldsEnd != null) "WORLDS_END" else requireNotNull(basic).getString("type"))
                put("song_id", record.intOrNull("songId") ?: basic?.getInt("id"))
                put("achievement", record.intOrNull("achievement"))
                put("score", record.intOrNull("score"))
                put("dx_score", record.intOrNull("dxScore"))
                put("max_dx_score", record.intOrNull("maxDxScore"))
                put("rank", record.stringOrNull("rank"))
                put("level", record.stringOrNull("level") ?: chart?.stringOrNull("level"))
                put("combo", record.stringOrNull("full_combo") ?: record.stringOrNull("combo"))
                put("sync", record.stringOrNull("full_chain") ?: record.stringOrNull("sync"))
                put("special", worldsEnd?.getString("kanji") ?: record.stringOrNull("special"))
                put("played_at", record.stringOrNull("playedAt"))
                put("clear", record.stringOrNull("clear"))
                put("worlds_end_star", worldsEnd?.getInt("star") ?: record.intOrNull("star"))
            })
        }
    }

    override fun profiles(game: ScoreGame): List<ScoreProfile> = readableDatabase.rawQuery(
        """SELECT profile_id, display_name FROM snapshots WHERE game = ? AND profile_id IS NOT NULL
            ORDER BY started_at DESC, id DESC""",
        arrayOf(game.key),
    ).use {
        val seen = mutableSetOf<String>()
        buildList { while (it.moveToNext()) if (seen.add(it.getString(0))) add(ScoreProfile(game, it.getString(0), it.getString(1))) }
    }

    override fun scores(profile: ScoreProfile, filter: ScoreFilter, offset: Int, limit: Int): ScorePage<PersonalScore> {
        require(offset >= 0 && limit in 1..200)
        val where = StringBuilder("s.game = ? AND s.profile_id = ? AND r.record_kind = ?")
        val args = mutableListOf(profile.game.key, profile.id, filter.kind.name)
        filter.difficulty?.let { where.append(" AND r.difficulty = ?"); args.add(it.name) }
        filter.songId?.let { where.append(" AND r.song_id = ?"); args.add(it.toString()) }
        val results = readableDatabase.rawQuery(
            select + " WHERE " + where + " ORDER BY s.started_at DESC, r.id DESC LIMIT ? OFFSET ?",
            (args + listOf((limit + 1).toString(), offset.toString())).toTypedArray(),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.readScore()) } }
        return ScorePage(results.take(limit), (offset + limit).takeIf { results.size > limit })
    }

    override fun scoreDetail(profile: ScoreProfile, id: Long): PersonalScore? = readableDatabase.rawQuery(
        select + " WHERE s.game = ? AND s.profile_id = ? AND r.id = ?",
        arrayOf(profile.game.key, profile.id, id.toString()),
    ).use { if (it.moveToFirst()) it.readScore() else null }

    private fun Cursor.readScore() = PersonalScore(
        getLong(0), getString(1), Instant.parse(getString(2)), ScoreRecordKind.valueOf(getString(3)),
        getString(4), HtmlPageKind.valueOf(getString(5)), getString(6), intOrNull(7), intOrNull(8), intOrNull(9),
        intOrNull(10), intOrNull(11), getString(12), getString(13), getString(14), getString(15), getString(16), getString(17),
        getString(18), intOrNull(19),
    )

    private fun Cursor.intOrNull(index: Int) = if (isNull(index)) null else getInt(index)
    private fun JSONObject.intOrNull(key: String) = if (isNull(key)) null else getInt(key)
    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)

    private companion object {
        val chuDifficulties = listOf(HtmlPageKind.BASIC, HtmlPageKind.ADVANCED, HtmlPageKind.EXPERT,
            HtmlPageKind.MASTER, HtmlPageKind.ULTIMA, HtmlPageKind.WORLDS_END)
        const val select = """SELECT r.id, s.batch_id, r.captured_at, r.record_kind, r.title, r.difficulty,
            r.chart_type, r.song_id, r.achievement, r.score, r.dx_score, r.max_dx_score, r.level, r.rank,
            r.combo, r.sync, r.special, r.played_at, r.clear, r.worlds_end_star
            FROM scores r JOIN snapshots s ON r.snapshot_id = s.id"""
    }
}
