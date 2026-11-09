package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme

/**
 * 单个音游页的 WebView 与已加载入口。
 *
 * 持有的是渲染缓存，不是已选主题的另一份写入口；同入口重绑、切换音游与尺寸变化都不重新 loadUrl。
 */
internal class BackgroundPage(
    context: Context,
    val gameLine: GameLine,
    private val loader: AssetBackgroundLoader,
) {

    val webView: WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = loader.intercept(request)
        }
    }

    private var loadedPath: String? = null

    /** 入口变化时才加载；只改颜色不重新加载。 */
    fun show(theme: RegisteredTheme) {
        val path = theme.definition.htmlAssetPath
        if (path == loadedPath) return
        loadedPath = path
        loader.load(webView, path)
    }

    fun resume() {
        webView.onResume()
    }

    fun pause() {
        webView.onPause()
    }

    fun destroy() {
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        loadedPath = null
    }
}
