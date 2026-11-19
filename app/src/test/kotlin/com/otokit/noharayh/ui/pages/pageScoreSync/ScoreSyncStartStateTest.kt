package com.otokit.noharayh.ui.pages.pageScoreSync

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.CaptureStartResult
import com.otokit.noharayh.transfer.UploadTarget
import org.junit.Assert.*
import org.junit.Test

class ScoreSyncStartStateTest {
    private fun request(id: String = "launch") = CaptureRequest(ScoreGame.MAIMAI,
        listOf(HtmlPageKind.MASTER), id, true, listOf(UploadTarget(OAuthProvider.LXNS, "original-account")))

    @Test fun deniedPermissionDoesNotDispatchAServiceAndCanBeRetried() {
        val state = ScoreSyncStartState()
        state.start(request())
        state.advance("launch", SyncStartStep.PERMISSION_RESULT)
        state.permissionResult(false)
        assertNull(state.pending)
        assertEquals(SyncStartProblem.VPN_DENIED, state.problem(ScoreGame.MAIMAI))
        state.start(request("retry"))
        assertNull(state.problem(ScoreGame.MAIMAI))
        assertEquals(SyncStartStep.PERMISSION, state.pending!!.step)
    }

    @Test fun requestIsFrozenAndDuplicateStartDoesNotReplaceIt() {
        val state = ScoreSyncStartState()
        val kinds = mutableListOf(HtmlPageKind.MASTER)
        val targets = mutableListOf(UploadTarget(OAuthProvider.LXNS, "original-account"))
        state.start(request().copy(difficulties = kinds, uploadTargets = targets))
        kinds.clear()
        targets.clear()
        state.start(request("duplicate").copy(game = ScoreGame.CHUNITHM))
        assertEquals(request(), state.pending!!.request)
    }

    @Test fun acceptanceIsDistinctFromCompletionAndConsumedHandoffIsNotReplayed() {
        val state = ScoreSyncStartState()
        state.start(request())
        state.advance("launch", SyncStartStep.PERMISSION_RESULT)
        state.permissionResult(true)
        assertEquals(SyncStartStep.SERVICE, state.pending!!.step)
        state.advance("launch", SyncStartStep.STARTING)
        state.startResult("stale", CaptureStartResult.INVALID_REQUEST)
        assertEquals(SyncStartStep.STARTING, state.pending!!.step)
        state.startResult("launch", CaptureStartResult.ACCEPTED)
        assertEquals(SyncStartStep.ENTRY, state.pending!!.step)
        state.finish("launch")
        state.startResult("launch", CaptureStartResult.ACCEPTED)
        state.permissionResult(true)
        assertNull(state.pending)
    }

    @Test fun rejectedStartReturnsItsReasonWithoutClaimingATaskStarted() {
        val state = ScoreSyncStartState()
        state.start(request())
        state.startResult("launch", CaptureStartResult.ALREADY_RUNNING)
        assertNull(state.pending)
        assertEquals(SyncStartProblem.ALREADY_RUNNING, state.problem(ScoreGame.MAIMAI))
    }
}
