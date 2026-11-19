package com.otokit.noharayh.ui.pages.pageScoreSync

import androidx.lifecycle.ViewModel
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.CaptureStartResult
import com.otokit.noharayh.transfer.UploadReports
import java.io.Closeable

internal enum class SyncStartStep { PERMISSION, PERMISSION_RESULT, SERVICE, STARTING, ENTRY }
internal enum class SyncStartProblem { VPN_DENIED, WECHAT_UNAVAILABLE, UNSUPPORTED_ANDROID, ALREADY_RUNNING, INVALID_REQUEST, UNAVAILABLE }
internal data class PendingSyncStart(val request: CaptureRequest, val step: SyncStartStep)

/** Only the pending platform handoff survives Activity recreation; the service owns the actual task. */
internal class ScoreSyncStartState : ViewModel() {
    var pending: PendingSyncStart? = null
        private set
    private val problems = mutableMapOf<ScoreGame, SyncStartProblem>()
    private val observers = mutableSetOf<() -> Unit>()

    fun problem(game: ScoreGame) = problems[game]

    fun observe(observer: () -> Unit): Closeable {
        observers.add(observer)
        observer()
        return Closeable { observers.remove(observer) }
    }

    fun start(request: CaptureRequest) {
        if (pending != null || ScoreGame.entries.any { UploadReports.latest(it)?.terminal == false }) return
        problems.remove(request.game)
        pending = PendingSyncStart(request.copy(difficulties = request.difficulties.toList(),
            uploadTargets = request.uploadTargets.toList()), SyncStartStep.PERMISSION)
        changed()
    }

    fun advance(id: String, step: SyncStartStep) {
        val current = pending?.takeIf { it.request.launchId == id } ?: return
        pending = current.copy(step = step)
        changed()
    }

    fun permissionResult(allowed: Boolean) {
        val current = pending?.takeIf { it.step == SyncStartStep.PERMISSION_RESULT } ?: return
        if (allowed) advance(current.request.launchId, SyncStartStep.SERVICE)
        else fail(current.request.launchId, SyncStartProblem.VPN_DENIED)
    }

    fun startResult(id: String, result: CaptureStartResult) {
        if (pending?.request?.launchId != id) return
        when (result) {
            CaptureStartResult.ACCEPTED -> advance(id, SyncStartStep.ENTRY)
            CaptureStartResult.ALREADY_RUNNING -> fail(id, SyncStartProblem.ALREADY_RUNNING)
            CaptureStartResult.INVALID_REQUEST -> fail(id, SyncStartProblem.INVALID_REQUEST)
        }
    }

    fun fail(id: String, problem: SyncStartProblem) {
        val current = pending?.takeIf { it.request.launchId == id } ?: return
        problems[current.request.game] = problem
        finish(id)
    }

    fun finish(id: String) {
        if (pending?.request?.launchId != id) return
        pending = null
        changed()
    }

    private fun changed() = observers.toList().forEach { it() }
}
