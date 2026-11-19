package com.otokit.noharayh.transfer.runtime

import android.app.*
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
import android.net.VpnService
import android.os.*
import android.system.OsConstants
import android.util.Log
import com.otokit.noharayh.R
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.composition.scoreUploadFactory
import com.otokit.noharayh.score.HtmlCaptures
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.transfer.capture.LoopbackCaptureProxy
import com.otokit.noharayh.transfer.net.GameNetPages
import com.otokit.noharayh.transfer.task.CaptureChannel
import com.otokit.noharayh.transfer.task.CaptureTaskCoordinator
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Android host only. Task facts, NET steps and archive rules belong to their owners. */
open class NetCaptureService : VpnService() {
    protected lateinit var archives: HtmlCaptures
        private set
    private lateinit var coordinator: CaptureTaskCoordinator
    private var observation: Closeable? = null
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { coordinator.cancel(timedOut = true) }

    inner class CaptureBinder : Binder() {
        fun snapshot() = coordinator.snapshot()
        fun observe(observer: (CaptureSnapshot) -> Unit) = coordinator.observe(observer)
        fun cancel() = coordinator.cancel()
    }
    private val binder = CaptureBinder()

    override fun onCreate() {
        super.onCreate()
        archives = FileHtmlCaptures(File(filesDir, "score/html"))
        coordinator = CaptureTaskCoordinator(archives, newUploadTask = scoreUploadFactory(this, archives), openChannel = { authorize, callback, event ->
            if (Build.VERSION.SDK_INT < 29) error("Capture requires Android 10")
            check(prepare(this) == null) { "VPN permission required" }
            val path = "/${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val proxy = LoopbackCaptureProxy(path, onCallback = callback, onEvent = event, authorizationFactory = authorize)
            var descriptor: ParcelFileDescriptor? = null
            try {
                proxy.start()
                descriptor = Builder().setSession("OTOKiT NET capture")
                    .addAddress("198.18.0.1", 32).allowFamily(OsConstants.AF_INET6)
                    .addAllowedApplication("com.tencent.mm")
                    .setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", proxy.port)).establish()
                    ?: error("VPN unavailable")
                val vpn = descriptor
                object : CaptureChannel {
                    private val closed = AtomicBoolean(false)
                    override val entryUrl = "http://127.0.0.2:8284$path"
                    override val proxyPort = proxy.port
                    override fun close() {
                        if (!closed.compareAndSet(false, true)) return
                        try { proxy.close() } finally { vpn.close() }
                    }
                }
            } catch (error: Exception) { proxy.close(); descriptor?.close(); throw error }
        })
        observation = coordinator.observe { snapshot ->
            if (snapshot.request.uploadTargets.isNotEmpty() && (snapshot.stage == CaptureStage.UPLOADING || snapshot.terminal)) {
                val report = UploadReport(snapshot.request.launchId, snapshot.startedAt, snapshot.stage, snapshot.uploads)
                main.post { UploadReports.publish(snapshot.request.game, report) }
            }
            runCatching { onSnapshot(snapshot) }.onFailure { Log.w("NetCapture", "Diagnostic projection failed: ${it.javaClass.simpleName}") }
            if (snapshot.terminal) main.post { main.removeCallbacks(timeout); stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            if (coordinator.snapshot()?.terminal != false) stopSelf() else coordinator.cancel()
            return START_NOT_STICKY
        }
        showNotification()
        val request = runCatching {
            val game = ScoreGame.entries.first { it.key == intent?.getStringExtra("game") }
            val kinds = requireNotNull(intent?.getStringArrayExtra("pageKinds")).map { HtmlPageKind.valueOf(it) }
            CaptureRequest(game, kinds, intent?.getStringExtra("launchId") ?: UUID.randomUUID().toString(),
                intent?.getBooleanExtra("keepCallbackCaptureUntilLogin", false) ?: false,
                OAuthProvider.entries.mapNotNull { provider ->
                    intent?.getStringExtra("upload.${provider.name}")?.let { UploadTarget(provider, it) }
                })
        }.getOrNull()
        if (request == null) { if (coordinator.snapshot()?.terminal != false) stopSelf() }
        else if (coordinator.start(request) == CaptureStartResult.ACCEPTED) {
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, 10 * 60_000L)
        } else if (coordinator.snapshot() == null) stopSelf()
        return START_NOT_STICKY
    }

    protected open fun onSnapshot(snapshot: CaptureSnapshot) = Unit
    protected open fun notificationIntent(): Intent = Intent(this, com.otokit.noharayh.MainActivity::class.java)

    private fun showNotification() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("net_capture", "NET 页面采集", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, notificationIntent(), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "net_capture")
            .setSmallIcon(R.drawable.ic_sync_24).setContentTitle("NET 页面采集")
            .setContentText("采集正在进行，原始页面保存在本机").setContentIntent(pending).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(3101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(3101, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else binder
    override fun onRevoke() { coordinator.cancel(); super.onRevoke() }
    override fun onTimeout(startId: Int, fgsType: Int) { coordinator.cancel(timedOut = true) }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        coordinator.close()
        observation?.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        /** Call from a visible Android entry after VpnService.prepare has been approved. */
        fun startIntent(context: Context, request: CaptureRequest) = Intent(context, NetCaptureService::class.java)
            .putExtra("game", request.game.key).putExtra("pageKinds", request.difficulties.map { it.name }.toTypedArray())
            .putExtra("launchId", request.launchId)
            .putExtra("keepCallbackCaptureUntilLogin", request.keepCallbackCaptureUntilLogin)
            .apply {
                request.uploadTargets.forEach { putExtra("upload.${it.provider.name}", it.subject) }
            }
    }
}
