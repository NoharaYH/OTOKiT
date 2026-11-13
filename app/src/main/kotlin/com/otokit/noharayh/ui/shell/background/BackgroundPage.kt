package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.otokit.noharayh.ui.theme.BackgroundPalette
import com.otokit.noharayh.ui.theme.BackgroundSelection
import com.otokit.noharayh.ui.theme.GameLine

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

    private var rendering = BackgroundRenderingOptions()
    private var documentReady = false
    private val debugLogging = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @android.annotation.SuppressLint("SetJavaScriptEnabled") // 仅操作本地背景的 CSS 状态，不暴露 Java bridge。
    private val webView: WebView = object : WebView(context) {
        private var lastDrawHardware: Boolean? = null
        override fun onDraw(canvas: Canvas) {
            if (debugLogging && lastDrawHardware != canvas.isHardwareAccelerated) {
                lastDrawHardware = canvas.isHardwareAccelerated
                Log.d("BackgroundRendering", "$gameLine drawCanvasHardware=${canvas.isHardwareAccelerated}")
            }
            super.onDraw(canvas)
        }
    }.apply {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // LUMINOUS 官网背景使用本地静音视频，播放状态仍由渲染选项统一控制。
            mediaPlaybackRequiresUserGesture = false
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith(AssetBackgroundLoader.BASE_URL)

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = loader.intercept(request)

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url != expectedUrl || loadedHtmlThemeId == null) return
                documentReady = true
                val requestId = ++visualRequest
                // onPageFinished 仍可能早于最后一张 WebP 上屏；再等视觉状态提交。
                applyAnimationState {
                    if (requestId == visualRequest && documentReady && loadedHtmlThemeId != null) {
                        view.postVisualStateCallback(
                            requestId,
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(id: Long) {
                                    if (id == visualRequest && loadedHtmlThemeId != null && !readySent) {
                                        readySent = true
                                        onFirstVisualReady()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                // 主内容已经提交；真正的遮罩消失仍以 visual callback 为准。
                // 保留观察点：真正遮罩消失仍等 visual callback。
            }
        }
    }

    private var loadedHtmlThemeId: String? = null
    private var loadedSelectionKey: String? = null
    private var noneBackgroundColor = BackgroundPalette.NONE_COLOR
    private var visualRequest = 0L
    private var readySent = false
    private var resumed = false
    private var mediaVisible = false
    private var expectedUrl: String? = null

    /** 只切换背景层的绘制路径及 CSS 状态，不重载页面或覆盖 Activity 的硬件设置。 */
    fun setRenderingOptions(options: BackgroundRenderingOptions) {
        rendering = options
        val layer = if (options.hardwareAccelerated) View.LAYER_TYPE_NONE else View.LAYER_TYPE_SOFTWARE
        if (webView.layerType != layer) webView.setLayerType(layer, null)
        if (documentReady) applyAnimationState()
        if (debugLogging) Log.d("BackgroundRendering", "$gameLine hardware=${options.hardwareAccelerated} " +
            "layer=${webView.layerType} windowAccelerated=${webView.isHardwareAccelerated} animations=${options.htmlAnimations}")
    }

    private fun applyAnimationState(onApplied: () -> Unit = {}) {
        if (!documentReady || loadedHtmlThemeId == null) return
        // 单一样式同时覆盖元素和伪元素；移除覆盖后由原始 CSS 从暂停位置接续。
        // JS 只在设置、文档或生命周期变化时执行，没有计时器、逐帧回调或原生接口。
        val disabled = rendering.htmlAnimations
        // Android WebView 的软件绘制路径不显示视频；使用同一视频的首帧保留背景。
        val hardwareMedia = rendering.hardwareAccelerated
        val playMedia = rendering.htmlAnimations && resumed && mediaVisible && hardwareMedia
        webView.evaluateJavascript("""
            (() => {
              let style = document.getElementById('otokit-animation-control');
              if (!style) {
                style = document.createElement('style');
                style.id = 'otokit-animation-control';
                style.textContent = '*,*::before,*::after{animation-play-state:paused!important}';
                document.head.appendChild(style);
              }
              style.disabled = $disabled;
              document.querySelectorAll('video').forEach(video => {
                video.muted = true;
                const poster = video.parentElement.querySelector('[data-otokit-video-poster]');
                if (poster) {
                  video.hidden = !$hardwareMedia;
                  poster.hidden = $hardwareMedia;
                }
                if ($playMedia) {
                  video.play().catch(() => {});
                } else {
                  video.pause();
                }
              });
              return Array.from(document.querySelectorAll('video'), video => ({
                paused: video.paused,
                time: video.currentTime,
                ready: video.readyState,
                poster: video.hidden,
                error: video.error ? video.error.code : null
              }));
            })()
        """.trimIndent()) { mediaState ->
            if (debugLogging && mediaState != "[]" && mediaState != "null") {
                Log.d("BackgroundRendering", "$gameLine resumed=$resumed visible=$mediaVisible media=$mediaState")
            }
            onApplied()
        }
    }

    private val solidView: View = View(context).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }

    init {
        (view as ViewGroup).addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        (view as ViewGroup).addView(
            solidView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** 入口变化时才加载；只改颜色不重新加载。返回是否更换了入口。 */
    fun show(selection: BackgroundSelection): Boolean {
        val key = when (selection) {
            is BackgroundSelection.Html -> "html:${selection.theme.id}"
            BackgroundSelection.Placeholder -> "placeholder"
            BackgroundSelection.None -> "none"
        }
        if (key == loadedSelectionKey) return false
        loadedSelectionKey = key
        visualRequest++
        readySent = false
        documentReady = false
        when (selection) {
            is BackgroundSelection.Html -> {
                loadedHtmlThemeId = selection.theme.id
                expectedUrl = AssetBackgroundLoader.BASE_URL + selection.theme.definition.htmlAssetPath
                if (resumed) webView.onResume() else webView.onPause()
                solidView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                loader.load(webView, selection.theme.definition.htmlAssetPath)
            }
            BackgroundSelection.Placeholder -> {
                showSolid(BackgroundPalette.PLACEHOLDER_COLOR)
            }
            BackgroundSelection.None -> {
                showSolid(noneBackgroundColor)
            }
        }
        return true
    }

    /** 同一个无主题来源只换底色，不重启 WebView、首帧回调或背景切换。 */
    fun setNoneBackgroundColor(color: Int) {
        if (noneBackgroundColor == color) return
        noneBackgroundColor = color
        if (loadedSelectionKey == "none") solidView.setBackgroundColor(color)
    }

    private fun showSolid(color: Int) {
        if (documentReady) {
            webView.evaluateJavascript("document.querySelectorAll('video').forEach(video => video.pause())", null)
        }
        loadedHtmlThemeId = null
        documentReady = false
        expectedUrl = null
        webView.onPause()
        webView.stopLoading()
        webView.visibility = View.GONE
        solidView.setBackgroundColor(color)
        solidView.visibility = View.VISIBLE
        val key = loadedSelectionKey
        solidView.post {
            if (key == loadedSelectionKey && !readySent) {
                readySent = true
                onFirstVisualReady()
            }
        }
    }

    fun resume() {
        resumed = true
        if (loadedHtmlThemeId != null) {
            webView.onResume()
            if (documentReady) applyAnimationState()
        }
    }

    /** 由宿主现有的翻页进度确定；淡入淡出期间两页都可播放，隐藏页停止解码。 */
    fun setMediaVisible(visible: Boolean) {
        if (mediaVisible == visible) return
        mediaVisible = visible
        if (documentReady) applyAnimationState()
    }

    fun pause() {
        resumed = false
        if (documentReady) applyAnimationState()
        webView.onPause()
    }

    fun destroy() {
        documentReady = false
        visualRequest++
        loadedHtmlThemeId = null
        loadedSelectionKey = null
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        (solidView.parent as? ViewGroup)?.removeView(solidView)
        webView.destroy()
        (view as ViewGroup).removeAllViews()
    }
}
