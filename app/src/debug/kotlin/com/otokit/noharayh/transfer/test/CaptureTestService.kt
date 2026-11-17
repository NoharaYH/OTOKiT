package com.otokit.noharayh.transfer.test

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.net.VpnService
import android.net.ProxyInfo
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import com.otokit.noharayh.R
import com.otokit.noharayh.score.html.RawHtmlArchive
import com.otokit.noharayh.transfer.capture.LoopbackCaptureProxy
import com.otokit.noharayh.transfer.net.GameNetClient
import com.otokit.noharayh.transfer.net.NetPageInspection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only in the debug APK. The service owns the experiment independently of Activity lifetime. */
class CaptureTestService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val client = GameNetClient { host, status ->
        synchronized(state) { state.put("lastResponse", JSONObject().put("host", host).put("status", status)) }
    }
    private var started = false
    private var terminal = false
    @Volatile private var proxy: LoopbackCaptureProxy? = null
    @Volatile private var vpn: ParcelFileDescriptor? = null
    private lateinit var batch: File
    private val state = JSONObject()
    private val pages = JSONArray()

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("net_capture_test", "NET 采集测试", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0,
            Intent(this, CaptureTestActivity::class.java).putExtra("start", false), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "net_capture_test")
            .setSmallIcon(R.drawable.ic_sync_24).setContentTitle("NET 页面采集测试")
            .setContentText("仅保存原始页面；点此查看或停止")
            .setContentIntent(pending).setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(3101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(3101, notification)
        main.postDelayed({ if (!terminal) finishExperiment("TIMEOUT", "测试超时") }, 10 * 60_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            // A stop delivered after the service ended must not overwrite the last result.
            if (!started) {
                terminal = true
                stopped.set(true)
                stopSelf()
            } else finishExperiment("CANCELLED", "测试已停止，已保存文件保留")
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        started = true
        worker.execute {
            try {
                val game = intent?.getStringExtra("game") ?: "maimai"
                require(game in listOf("maimai", "chunithm"))
                val difficulties = (intent?.getStringExtra("difficulties") ?: "3")
                    .split(',').map { it.trim().toInt() }.distinct().sorted()
                require(difficulties.isNotEmpty() && difficulties.all { it in if (game == "maimai") listOf(0, 1, 2, 3, 4, 10) else (0..5).toList() })
                batch = File(filesDir, "transfer-capture/${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}")
                check(batch.mkdirs())
                synchronized(state) {
                    state.put("game", game).put("difficulties", JSONArray(difficulties))
                        .put("transport", "okhttp-4.10-on-demand-authorization")
                        .put("directory", batch.absolutePath).put("pages", pages)
                        .put("launchId", intent?.getStringExtra("launchId"))
                }
                publish("PREPARING", "启动本地捕获服务")
                check(!stopped.get())
                val path = "/${UUID.randomUUID().toString().replace("-", "").take(10)}"
                val listening = LoopbackCaptureProxy(path, onCallback = { callback ->
                    if (!stopped.get()) runCatching {
                        worker.execute { collect(game, difficulties, callback) }
                    }
                }, onEvent = { event ->
                    synchronized(state) {
                        val counters = state.optJSONObject("proxyEvents") ?: JSONObject().also { state.put("proxyEvents", it) }
                        counters.put(event, counters.optInt(event) + 1)
                        if (!terminal) runCatching { publish(state.optString("stage"), state.optString("detail")) }
                    }
                }, authorizationFactory = { client.authorizationUrl(game) })
                proxy = listening
                if (stopped.get()) { listening.close(); return@execute }
                listening.start()
                if (android.os.Build.VERSION.SDK_INT < 29) error("VPN proxy test requires Android 10")
                // No default IP route: HTTP-aware apps use the loopback proxy; other traffic
                // falls through to the underlying network. This experiment tests whether
                // current WeChat honors Android's per-VPN HTTP proxy recommendation.
                vpn = Builder().setSession("OTOKiT NET capture test")
                    .addAddress("198.18.0.1", 32).allowFamily(OsConstants.AF_INET6)
                    .addAllowedApplication("com.tencent.mm")
                    .setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", listening.port))
                    .establish() ?: error("VPN permission unavailable")
                if (stopped.get()) { vpn?.close(); listening.close(); return@execute }
                synchronized(state) {
                    state.put("proxyPort", listening.port).put("entryUrl", "http://127.0.0.2:8284$path")
                }
                publish("WAITING_WECHAT", "代理已就绪，等待在微信中打开入口")
            } catch (error: Exception) {
                if (!stopped.get()) finishExperiment("FAILED", "准备失败：${error.javaClass.simpleName}")
            }
        }
        return START_NOT_STICKY
    }

    private fun collect(game: String, difficulties: List<Int>, callback: URI) {
        try {
            proxy?.close(); proxy = null
            vpn?.close(); vpn = null
            val query = callback.rawQuery.orEmpty().split('&').map { part ->
                val pair = part.split('=', limit = 2)
                java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }.toMap()
            synchronized(state) {
                state.put("callbackShape", JSONObject().put("codeLength", query["code"].orEmpty().length)
                    .put("denied", query["code"] == "authdeny").put("path", callback.path))
            }
            // Controlled comparison with CrawlerCaller.fetchData's two three-second waits.
            Thread.sleep(6000)
            publish("LOGIN", "已取得一次性回调，正在建立 NET 会话")
            val archive = RawHtmlArchive(batch)
            client.login(callback) { response ->
                val saved = archive.save("login_response.html", response.stream) { stopped.get() }
                synchronized(state) { state.put("loginDiagnostic", JSONObject().put("file", saved.file.name).put("bytes", saved.bytes).put("status", response.status)) }
                NetPageInspection.businessErrorCode(saved.prefix)?.let { code ->
                    synchronized(state) { state.put("netBusinessErrorCode", code) }
                    error("NET business error $code")
                }
            }
            val base = if (game == "maimai") "https://maimai.wahlap.com/maimai-mobile/" else "https://chunithm.wahlap.com/mobile/"
            val plan = mutableListOf("player" to if (game == "maimai") "friend/userFriendCode/" else "home/playerData/")
            plan += "recent" to if (game == "maimai") "record/" else "record/playlog/"
            difficulties.forEach { diff ->
                val path = if (game == "maimai") {
                    if (diff == 10) "record/musicGenre/search/?genre=99&diff=10"
                    else "record/musicSort/search/?search=V&sort=1&playCheck=on&diff=$diff"
                } else {
                    if (diff == 5) "record/worldsEndList/" else "record/musicGenre/${listOf("basic", "advanced", "expert", "master", "ultima")[diff]}"
                }
                plan += "difficulty_$diff" to path
            }
            for ((index, page) in plan.withIndex()) {
                check(!stopped.get())
                val (name, path) = page
                if (game == "chunithm" && name.startsWith("difficulty_") && name != "difficulty_5") {
                    val diff = name.substringAfter('_').toInt()
                    val command = listOf("sendBasic", "sendAdvanced", "sendExpert", "sendMaster", "sendUltima")[diff]
                    val token = getChunithmFormToken(base)
                    client.read(URI(base + "record/musicGenre/$command"), "genre=99&token=" + URLEncoder.encode(token, "UTF-8")) { }
                }
                publish("FETCHING", "${index + 1}/${plan.size}：$name")
                client.read(URI(base + path)) { response ->
                    check(response.uri.host == URI(base).host && response.uri.rawPath == URI(base + path).rawPath) { "Unexpected page redirect" }
                    check(response.contentType.contains("html", true)) { "Not an HTML response" }
                    val saved = archive.save("$name.html", response.stream) { stopped.get() }
                    NetPageInspection.businessErrorCode(saved.prefix)?.let { code ->
                        synchronized(state) { state.put("netBusinessErrorCode", code) }
                        error("NET business error $code")
                    }
                    // Raw evidence is retained even when the current heuristic cannot confirm a score page.
                    val looksLikePage = saved.prefix.contains("<html", true)
                    val marker = if (game == "maimai") "music_name_block" else "music_title"
                    val kind = when {
                        !looksLikePage -> "UNRECOGNIZED"
                        name.startsWith("difficulty_") && saved.prefix.contains(marker) -> "SCORE_CANDIDATE"
                        else -> "HTML_NEEDS_INSPECTION"
                    }
                    synchronized(state) {
                        pages.put(JSONObject().put("file", saved.file.name).put("bytes", saved.bytes)
                            .put("sha256", saved.sha256).put("contentType", response.contentType)
                            .put("inspection", kind).put("capturedAt", java.time.Instant.now().toString()))
                    }
                    publish("SAVED", "$name.html：${saved.bytes} bytes，$kind")
                }
                if (index < plan.lastIndex) Thread.sleep(1200)
            }
            finishExperiment("SAVED_FOR_INSPECTION", "页面已保存，需导出检查是否为有效成绩，尚未解析或上传")
        } catch (error: Exception) {
            if (!stopped.get()) {
                synchronized(state) {
                    state.put("failedStage", state.optString("stage"))
                    state.put("errorFrames", JSONArray(error.stackTrace.filter { it.className.startsWith("com.otokit.noharayh") }
                        .take(4).map { "${it.className}.${it.methodName}:${it.lineNumber}" }))
                    val safe = error.message.orEmpty()
                    if (safe.matches(Regex("NET returned HTTP [0-9]{3}|NET business error [0-9]{4,12}|Unexpected NET destination|Unexpected page redirect|Not an HTML response|Session closed"))) state.put("reason", safe)
                }
                finishExperiment("FAILED", "采集失败：${error.javaClass.simpleName}；已保存文件保留")
            }
        }
    }

    private fun getChunithmFormToken(base: String): String = client.read(URI(base + "record/musicGenre/")) { response ->
        val text = response.stream.bufferedReader().use { reader ->
            val chars = CharArray(256 * 1024)
            var count = 0
            while (count < chars.size) { val n = reader.read(chars, count, chars.size - count); if (n < 0) break; count += n }
            String(chars, 0, count)
        }
        val input = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(text)
            .map { it.value }.firstOrNull { Regex("name\\s*=\\s*['\"]token['\"]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?: error("Missing form token")
        Regex("value\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(input)?.groupValues?.get(1) ?: error("Missing form token value")
    }

    private fun publish(stage: String, detail: String) {
        synchronized(state) {
            state.put("stage", stage).put("detail", detail).put("updatedAt", java.time.Instant.now().toString())
            val root = File(filesDir, "transfer-capture").apply { mkdirs() }
            if (::batch.isInitialized) writeJson(File(batch, "manifest.json"), state.toString(2))
            writeJson(File(root, "status.json"), state.toString(2))
        }
        // Only controlled labels and sizes; no exception messages, URLs, cookies, or HTML.
        Log.i("NetCaptureTest", "$stage $detail")
    }

    private fun writeJson(file: File, json: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(json.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }

    private fun finishExperiment(stage: String, detail: String) {
        synchronized(state) {
            if (terminal) return
            terminal = true
            runCatching { publish(stage, detail) }
        }
        stopped.set(true)
        runCatching { proxy?.close() }; proxy = null
        runCatching { vpn?.close() }; vpn = null
        client.close()
        worker.shutdownNow()
        main.post { stopSelf() }
    }

    override fun onDestroy() {
        stopped.set(true)
        main.removeCallbacksAndMessages(null)
        if (!terminal) { terminal = true; runCatching { publish("CANCELLED", "测试已停止，已保存文件保留") } }
        proxy?.close(); vpn?.close(); client.close(); worker.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else null
    override fun onRevoke() { finishExperiment("CANCELLED", "VPN 授权被撤销"); super.onRevoke() }
    override fun onTimeout(startId: Int, fgsType: Int) { finishExperiment("TIMEOUT", "系统结束后台采集测试") }
}
