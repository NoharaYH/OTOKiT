package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.musicData.ScoreCatalog
import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.score.json.ScoreDatabase
import com.otokit.noharayh.score.json.ScoreSnapshots
import com.otokit.noharayh.transfer.task.CaptureTaskCoordinator
import java.io.File

private var data: ScoreData? = null

@Synchronized
private fun scoreData(context: Context): ScoreData {
    val app = context.applicationContext
    return data ?: ScoreData(
        FileHtmlCaptures(File(app.filesDir, "score/html")),
        ScoreSnapshots(File(app.filesDir, "score/json")) { game ->
            ScoreCatalog(app.assets.open("musicData/${game.key}-catalog.json"))
        },
        ScoreDatabase(app),
        CaptureTaskCoordinator::isActive,
    ).also {
        data = it
        it.startRecovery()
    }
}

internal fun initializeScoreData(context: Context) { scoreData(context) }
internal fun scoreCaptures(context: Context): HtmlCaptures = scoreData(context)
internal fun scoreArchiveQueries(context: Context): ScoreArchiveQueries = scoreData(context)
internal fun scoreArchiveActions(context: Context): ScoreArchiveActions = scoreData(context)
internal fun personalScoreQueries(context: Context): PersonalScoreQueries = scoreData(context)
