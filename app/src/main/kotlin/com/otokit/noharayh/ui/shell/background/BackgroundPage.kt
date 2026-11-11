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
 * 对外只暴露触摸容器；WebView 是渲染缓存，不是已选主题的另一份写入口。
 * 同入口重绑、切换音游与尺寸变化都不重新 loadUrl。
 */
internal class BackgroundPage(
    context: Context,
    val gameLine: GameLine,
    private val loader: AssetBackgroundLoader,
    private val onFirstVisualReady: () -> Unit,
) {

    val view: View = TouchBlockingFrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val webView: WebView = WebView(context).apply {
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

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                val requestId = ++visualRequest
                // onPageFinished 仍可能早于最后一张 WebP 上屏；再等视觉状态提交。
                view.postVisualStateCallback(
                    requestId,
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(id: Long) {
                            if (id == visualRequest && loadedPath != null && !readySent) {
                                readySent = true
                                onFirstVisualReady()
                            }
                        }
                    },
                )
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                // 主内容已经提交；真正的遮罩消失仍以 visual callback 为准。
                // 保留观察点：真正遮罩消失仍等 visual callback。
            }
        }
    }

    private var loadedPath: String? = null
    private var visualRequest = 0L
    private var readySent = false

    val isLoaded: Boolean
        get() = loadedPath != null

    init {
        (view as ViewGroup).addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** 入口变化时才加载；只改颜色不重新加载。返回是否更换了入口。 */
    fun show(theme: RegisteredTheme): Boolean {
        val path = theme.definition.htmlAssetPath
        if (path == loadedPath) return false
        loadedPath = path
        visualRequest++
        readySent = false
        loader.load(webView, path)
        return true
    }

    fun resume() {
        webView.onResume()
    }

    fun pause() {
        webView.onPause()
    }

    fun destroy() {
        visualRequest++
        loadedPath = null
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        (view as ViewGroup).removeAllViews()
        loadedPath = null
    }
}
