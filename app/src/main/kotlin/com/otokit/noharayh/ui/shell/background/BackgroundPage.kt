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
import com.otokit.noharayh.ui.theme.BackgroundSource
import com.otokit.noharayh.ui.theme.ThemeColors

/**
 * 单个渲染来源的原生背景或按需创建的 WebView。
 *
 * 对外只暴露触摸容器；WebView 是渲染缓存，不是已选主题的另一份写入口。
 * 来源在实例生命周期内固定，切换音游与尺寸变化可以复用该实例。
 */
internal class BackgroundPage(
    context: Context,
    val source: BackgroundSource,
    private val loader: AssetBackgroundLoader,
    private var materkitColors: ThemeColors,
    private val onFirstVisualReady: (BackgroundPage) -> Unit,
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

    private val webViewDelegate = lazy { createWebView(context) }
    private val webView: WebView get() = webViewDelegate.value

    @android.annotation.SuppressLint("SetJavaScriptEnabled") // 仅操作本地背景的 CSS 状态，不暴露 Java bridge。
    private fun createWebView(context: Context): WebView = object : WebView(context) {
        private var lastDrawHardware: Boolean? = null
        override fun onDraw(canvas: Canvas) {
            if (debugLogging && lastDrawHardware != canvas.isHardwareAccelerated) {
                lastDrawHardware = canvas.isHardwareAccelerated
                Log.d("BackgroundRendering", "$source drawCanvasHardware=${canvas.isHardwareAccelerated}")
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
                if (url != expectedUrl) return
                documentReady = true
                val requestId = ++visualRequest
                // onPageFinished 仍可能早于最后一张 WebP 上屏；再等视觉状态提交。
                applyAnimationState {
                    if (requestId == visualRequest && documentReady) {
                        view.postVisualStateCallback(
                            requestId,
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(id: Long) {
                                    if (id == visualRequest && !readySent) {
                                        readySent = true
                                        onFirstVisualReady(this@BackgroundPage)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private var noneBackgroundColor = BackgroundPalette.NONE_COLOR
    private var visualRequest = 0L
    private var readySent = false
    private var resumed = false
    private var mediaVisible = false
    private val expectedUrl = (source as? BackgroundSource.Html)?.let {
        AssetBackgroundLoader.BASE_URL + it.assetPath
    }

    /** 只切换背景层的绘制路径及 CSS 状态，不重载页面或覆盖 Activity 的硬件设置。 */
    fun setRenderingOptions(options: BackgroundRenderingOptions) {
        rendering = options
        updateNativePlayback()
        val layer = if (options.hardwareAccelerated) View.LAYER_TYPE_NONE else View.LAYER_TYPE_SOFTWARE
        if (!webViewDelegate.isInitialized()) return
        if (webView.layerType != layer) webView.setLayerType(layer, null)
        if (documentReady) applyAnimationState()
        if (debugLogging) Log.d("BackgroundRendering", "$source hardware=${options.hardwareAccelerated} " +
            "layer=${webView.layerType} windowAccelerated=${webView.isHardwareAccelerated} animations=${options.htmlAnimations}")
    }

    private fun applyAnimationState(onApplied: () -> Unit = {}) {
        if (!documentReady) return
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
                Log.d("BackgroundRendering", "$source resumed=$resumed visible=$mediaVisible media=$mediaState")
            }
            onApplied()
        }
    }

    private val nativeBackgroundDelegate = lazy {
        MistBackgroundDrawable(context).apply { setPrimaryColor(materkitColors.primaryColor) }
    }
    private val nativeBackground get() = nativeBackgroundDelegate.value

    fun setMaterkitColors(colors: ThemeColors) {
        materkitColors = colors
        if (nativeBackgroundDelegate.isInitialized()) nativeBackground.setPrimaryColor(colors.primaryColor)
    }

    private fun updateNativePlayback() {
        if (!nativeBackgroundDelegate.isInitialized()) return
        if (resumed && mediaVisible && rendering.htmlAnimations) {
            nativeBackground.start()
        } else {
            nativeBackground.stop()
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
            solidView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun load() {
        when (source) {
            is BackgroundSource.Html -> {
                (view as ViewGroup).addView(webView, 0, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                setRenderingOptions(rendering)
                if (resumed) webView.onResume() else webView.onPause()
                loader.load(webView, source.assetPath)
            }
            BackgroundSource.Placeholder -> {
                showSolid(BackgroundPalette.PLACEHOLDER_COLOR, nativeBackground)
            }
            BackgroundSource.None -> {
                showSolid(noneBackgroundColor)
            }
        }
    }

    /** 同一个无主题来源只换底色，不重启 WebView、首帧回调或背景切换。 */
    fun setNoneBackgroundColor(color: Int) {
        if (noneBackgroundColor == color) return
        noneBackgroundColor = color
        if (source == BackgroundSource.None) solidView.setBackgroundColor(color)
    }

    private fun showSolid(color: Int, drawable: android.graphics.drawable.Drawable? = null) {
        if (drawable != null) solidView.background = drawable else solidView.setBackgroundColor(color)
        solidView.visibility = View.VISIBLE
        solidView.post { onFirstVisualReady(this) }
    }

    fun resume() {
        resumed = true
        updateNativePlayback()
        if (webViewDelegate.isInitialized()) {
            webView.onResume()
            if (documentReady) applyAnimationState()
        }
    }

    /** 淡入淡出期间可见层都可播放，隐藏层停止解码。 */
    fun setMediaVisible(visible: Boolean) {
        if (mediaVisible == visible) return
        mediaVisible = visible
        updateNativePlayback()
        if (documentReady) applyAnimationState()
    }

    fun pause() {
        resumed = false
        updateNativePlayback()
        if (documentReady) applyAnimationState()
        if (webViewDelegate.isInitialized()) webView.onPause()
    }

    fun destroy() {
        if (nativeBackgroundDelegate.isInitialized()) nativeBackground.release()
        documentReady = false
        visualRequest++
        if (webViewDelegate.isInitialized()) {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        (solidView.parent as? ViewGroup)?.removeView(solidView)
        (view as ViewGroup).removeAllViews()
    }
}
