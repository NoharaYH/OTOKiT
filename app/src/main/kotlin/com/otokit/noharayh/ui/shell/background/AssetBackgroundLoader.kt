package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader

/**
 * 两主题共用的背景加载能力。
 *
 * 把 `/assets/` 映射到 APK assets，URL 由背景运行层按 Registry 相对入口构造；
 * theme 仍只提供相对路径，不引入 WebKit。
 */
class AssetBackgroundLoader(context: Context) {

    private val assets = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context.applicationContext))
        .build()

    fun load(webView: WebView, htmlAssetPath: String) {
        webView.loadUrl(BASE_URL + htmlAssetPath)
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? =
        assets.shouldInterceptRequest(request.url)

    companion object {
        const val BASE_URL: String = "https://appassets.androidplatform.net/assets/"
    }
}
