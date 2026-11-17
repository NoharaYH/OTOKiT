package com.otokit.noharayh.transfer.test

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.VpnService
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** ADB-only debug entry. No production UI or supplier credentials. */
class CaptureTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var entryUrl: String? = null
    private var launchedWechat = false
    private val refresh = object : Runnable {
        override fun run() {
            val file = File(filesDir, "transfer-capture/status.json")
            runCatching {
                val state = JSONObject(file.readText())
                val matchesLaunch = !intent.getBooleanExtra("start", false) ||
                    state.optString("launchId") == intent.getStringExtra("launchId")
                if (!matchesLaunch) {
                    entryUrl = null
                    status.text = "等待本次测试就绪；若已有测试，请先停止。"
                    return@runCatching
                }
                entryUrl = state.optString("entryUrl").takeIf { it.isNotBlank() }
                status.text = "阶段：${state.optString("stage")}\n" +
                    "代理：127.0.0.1:${state.optInt("proxyPort")}\n" +
                    "入口：${entryUrl.orEmpty()}\n" +
                    "保存：${state.optString("directory")}\n" +
                    "${state.optString("detail")}\n\n" +
                    "在微信文件传输助手中发送并打开入口。HTML 仅保存到文件，不输出正文。"
                if (!launchedWechat && state.optString("stage") == "WAITING_WECHAT" &&
                    entryUrl != null && hasWindowFocus() &&
                    intent.getBooleanExtra("openWechat", false)) {
                    if (copyEntry()) {
                        launchedWechat = true
                        packageManager.getLaunchIntentForPackage("com.tencent.mm")?.let { startActivity(it) }
                    }
                }
            }
            handler.postDelayed(this, 750)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stop = intent.getBooleanExtra("stop", false)
        if (stop) {
            stopCapture(); finish(); return
        }
        if (savedInstanceState == null && intent.getBooleanExtra("start", false)) {
            requestCapture()
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        status = TextView(this).apply { text = "准备采集测试…"; textSize = 16f }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "复制微信入口"
            setOnClickListener {
                copyEntry()
            }
        })
        layout.addView(Button(this).apply {
            text = "停止测试"
            setOnClickListener { stopCapture() }
        })
        setContentView(layout)
    }

    override fun onStart() { super.onStart(); if (::status.isInitialized) handler.post(refresh) }
    override fun onStop() { handler.removeCallbacks(refresh); super.onStop() }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        intent = newIntent
        launchedWechat = false
        entryUrl = null
        if (newIntent.getBooleanExtra("stop", false)) {
            stopCapture(); finish()
        } else if (newIntent.getBooleanExtra("start", false)) requestCapture()
    }

    private fun copyEntry(): Boolean {
        val url = entryUrl ?: return false
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("采集测试", url))
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString() == url
        if (copied) android.util.Log.i("NetCaptureTest", "CLIPBOARD_VERIFIED")
        return copied
    }

    private fun stopCapture() { startService(Intent(this, CaptureTestService::class.java).setAction("stop")) }

    private fun requestCapture() {
        if (!intent.hasExtra("launchId")) intent.putExtra("launchId", java.util.UUID.randomUUID().toString())
        val permission = VpnService.prepare(this)
        if (permission != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(permission, 701)
        } else startCapture()
    }

    private fun startCapture() {
        startForegroundService(Intent(this, CaptureTestService::class.java).apply {
            putExtra("game", intent.getStringExtra("game") ?: "maimai")
            putExtra("difficulties", intent.getStringExtra("difficulties") ?: "3")
            putExtra("launchId", intent.getStringExtra("launchId"))
        })
    }

    @Deprecated("Platform permission callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701) {
            if (resultCode == RESULT_OK) startCapture()
            else status.text = "VPN 授权被拒绝，未启动测试或打开微信。"
        }
    }
}
