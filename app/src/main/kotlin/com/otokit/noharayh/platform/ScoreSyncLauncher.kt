package com.otokit.noharayh.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.otokit.noharayh.R
import com.otokit.noharayh.transfer.CaptureSnapshot
import com.otokit.noharayh.transfer.CaptureStage
import com.otokit.noharayh.transfer.CaptureStartResult
import com.otokit.noharayh.transfer.runtime.NetCaptureService
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncStartState
import com.otokit.noharayh.ui.pages.pageScoreSync.SyncStartProblem
import com.otokit.noharayh.ui.pages.pageScoreSync.SyncStartStep
import java.io.Closeable

/** Executes system UI effects only while the Activity is resumed. Never owns or cancels the task. */
internal class ScoreSyncLauncher(private val activity: ComponentActivity, private val state: ScoreSyncStartState) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private var resumed = false
    private var closed = false
    private var bound = false
    private var observation: Closeable? = null
    private var snapshot: CaptureSnapshot? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.permissionResult(it.resultCode == Activity.RESULT_OK)
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            observation = (service as NetCaptureService.CaptureBinder).observe { value ->
                main.post { if (!closed) { snapshot = value; render() } }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            state.pending?.let { state.fail(it.request.launchId, SyncStartProblem.UNAVAILABLE) }
        }
    }
    private val subscription = state.observe { main.post { if (!closed) render() } }

    fun setResumed(value: Boolean) { resumed = value; if (value) render() }
    fun onWindowFocusChanged() { if (resumed) render() }

    private fun render() {
        val pending = state.pending
        if (pending == null) { unbind(); return }
        val request = pending.request
        val id = request.launchId
        if (!resumed) return
        when (pending.step) {
            SyncStartStep.PERMISSION -> {
                if (Build.VERSION.SDK_INT < 29) { state.fail(id, SyncStartProblem.UNSUPPORTED_ANDROID); return }
                if (activity.packageManager.getLaunchIntentForPackage("com.tencent.mm") == null) {
                    state.fail(id, SyncStartProblem.WECHAT_UNAVAILABLE); return
                }
                val intent = VpnService.prepare(activity)
                state.advance(id, SyncStartStep.PERMISSION_RESULT)
                if (intent == null) state.permissionResult(true) else permission.launch(intent)
            }
            SyncStartStep.PERMISSION_RESULT -> Unit
            SyncStartStep.SERVICE -> {
                state.advance(id, SyncStartStep.STARTING)
                try {
                    activity.startForegroundService(NetCaptureService.startIntent(activity, request,
                        StartReceiver(state, id)))
                } catch (_: IllegalStateException) {
                    state.fail(id, SyncStartProblem.UNAVAILABLE)
                } catch (_: SecurityException) {
                    state.fail(id, SyncStartProblem.UNAVAILABLE)
                }
            }
            SyncStartStep.STARTING, SyncStartStep.ENTRY -> {
                if (!bound) {
                    bound = activity.bindService(Intent(activity, NetCaptureService::class.java), connection, 0)
                    if (!bound) { state.fail(id, SyncStartProblem.UNAVAILABLE); return }
                }
                val current = snapshot?.takeIf { it.request.launchId == id } ?: return
                if (current.stage != CaptureStage.PREPARING && current.stage != CaptureStage.WAITING_WECHAT) {
                    state.finish(id); return
                }
                val entry = current.entryUrl?.takeIf { current.stage == CaptureStage.WAITING_WECHAT } ?: return
                if (!activity.hasWindowFocus()) return
                // The handoff is consumed after launch; later snapshots cannot reopen WeChat.
                val wechat = activity.packageManager.getLaunchIntentForPackage("com.tencent.mm")
                if (wechat == null) { state.fail(id, SyncStartProblem.WECHAT_UNAVAILABLE); return }
                try {
                    activity.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.sync_start), entry))
                    activity.startActivity(wechat)
                    Toast.makeText(activity, R.string.sync_wechat_hint, Toast.LENGTH_LONG).show()
                    state.finish(id)
                } catch (_: ActivityNotFoundException) {
                    state.fail(id, SyncStartProblem.WECHAT_UNAVAILABLE)
                }
            }
        }
    }

    private fun unbind() {
        observation?.close(); observation = null
        snapshot = null
        if (bound) { activity.unbindService(connection); bound = false }
    }

    override fun close() {
        closed = true
        subscription.close()
        main.removeCallbacksAndMessages(null)
        unbind()
    }

    /** Keeps no Activity reference: an acknowledgement can arrive across configuration changes. */
    private class StartReceiver(private val state: ScoreSyncStartState, private val id: String) : ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            state.startResult(id, CaptureStartResult.entries[resultCode])
        }
    }
}
