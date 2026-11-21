package com.otokit.noharayh.ui.shell.background

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.shell.NoneBackgroundColorRunner
import com.otokit.noharayh.ui.shell.MaterkitColors
import com.otokit.noharayh.ui.theme.BackgroundPalette
import com.otokit.noharayh.ui.theme.BackgroundSelection
import com.otokit.noharayh.ui.theme.BackgroundSource
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.renderSource

/** 固定在视口内的背景层。按渲染来源复用页面，消费前景的切换进度。 */
class BackgroundHost(
    private val container: FrameLayout,
    private var rendering: BackgroundRenderingOptions = BackgroundRenderingOptions(),
) {
    private class Layer(val page: BackgroundPage, var ready: Boolean = false)

    private val context = container.context
    private val loader = AssetBackgroundLoader(context)
    private val layersRoot = FrameLayout(context)
    private val layers = mutableMapOf<BackgroundSource, Layer>()
    private var maiSource: BackgroundSource = BackgroundSource.None
    private var chuSource: BackgroundSource = BackgroundSource.None
    private var committedGame = GameLine.Mai
    private val committedSource: BackgroundSource
        get() = if (committedGame == GameLine.Mai) maiSource else chuSource
    private var foregroundProgress: Float? = null
    private var lastVisible: Layer? = null
    private var initialVisualReady: (() -> Unit)? = null
    private var attached = false
    private var disposed = false
    private var resumed = false

    private var materkitColors = MaterkitColors.resolve(context)
    private var noneColor = BackgroundPalette.NONE_COLOR
    private val noneColorTransition = NoneBackgroundColorRunner(noneColor) { color ->
        noneColor = color
        layers[BackgroundSource.None]?.page?.setNoneBackgroundColor(color)
    }

    fun attach(initialGame: GameLine) {
        check(!attached && !disposed) { "背景宿主只允许挂载一次" }
        attached = true
        committedGame = initialGame
        container.addView(layersRoot, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun renderSelection(
        currentGame: GameLine,
        maiBackground: BackgroundSelection,
        chuBackground: BackgroundSelection,
    ) {
        if (!attached || disposed) return
        committedGame = currentGame
        maiSource = maiBackground.renderSource
        chuSource = chuBackground.renderSource
        updateLayers()
    }

    internal fun renderGameProgress(value: Float) {
        if (!attached || disposed || foregroundProgress == value) return
        foregroundProgress = value
        if (maiSource != chuSource) renderBackgroundFrame()
    }

    fun setMaterkitColors(colors: ThemeColors) {
        materkitColors = colors
        layers[BackgroundSource.Placeholder]?.page?.setMaterkitColors(colors)
    }

    fun setRenderingOptions(options: BackgroundRenderingOptions) {
        rendering = options
        layers.values.forEach { it.page.setRenderingOptions(options) }
    }

    fun setNoneBackgroundColor(color: Int, animate: Boolean = false) {
        if (!disposed) noneColorTransition.setColor(color, animate && attached && resumed)
    }

    fun setResumed(resumed: Boolean) {
        if (!attached || disposed || this.resumed == resumed) return
        this.resumed = resumed
        if (!resumed) noneColorTransition.finish()
        layers.values.forEach { if (resumed) it.page.resume() else it.page.pause() }
    }

    fun setOnFirstVisualReady(listener: (() -> Unit)?) {
        if (disposed) return
        initialVisualReady = listener
        deliverInitialVisualReady()
    }

    private fun deliverInitialVisualReady() {
        if (layers[committedSource]?.ready != true) return
        val listener = initialVisualReady
        initialVisualReady = null
        listener?.invoke()
    }

    private fun onPageVisualReady(page: BackgroundPage) {
        val layer = layers[page.source] ?: return
        if (layer.page !== page) return
        layer.ready = true
        updateLayers()
    }

    private fun updateLayers() {
        val selected = committedSource
        val selectedReady = layers[selected]?.ready == true
        val iterator = layers.entries.iterator()
        while (iterator.hasNext()) {
            val (source, layer) = iterator.next()
            // 换源期间保留上一张可见画面，直到已确认来源的首帧就绪。
            if (source == maiSource || source == chuSource || (!selectedReady && layer === lastVisible)) continue
            iterator.remove()
            if (layer === lastVisible) lastVisible = null
            release(layer.page)
        }
        mount(selected)
        // 冷启动先显示当前来源，再预载另一个。两边同源时 mount 只执行一次。
        if (selectedReady) mount(if (committedGame == GameLine.Mai) chuSource else maiSource)
        renderBackgroundFrame()
        deliverInitialVisualReady()
    }

    private fun mount(source: BackgroundSource) {
        if (source in layers) return
        val page = BackgroundPage(context, source, loader, materkitColors, ::onPageVisualReady)
        layers[source] = Layer(page)
        page.view.alpha = 0f
        layersRoot.addView(page.view)
        page.setRenderingOptions(rendering)
        page.setNoneBackgroundColor(noneColor)
        if (resumed) page.resume()
        page.load()
    }

    private fun renderBackgroundFrame() {
        val mai = layers[maiSource]?.takeIf { it.ready }
        val chu = layers[chuSource]?.takeIf { it.ready }
        val progress = foregroundProgress ?: if (committedGame == GameLine.Mai) 0f else 1f
        val blend = mai != null && chu != null && mai !== chu
        val primary = if (blend) {
            if (progress < 0.5f) mai else chu
        } else {
            val selected = if (committedGame == GameLine.Mai) mai else chu
            selected ?: lastVisible ?: mai ?: chu
        }
        lastVisible = primary
        for (layer in layers.values) {
            val opacity = when {
                blend && layer === mai -> 1f - progress
                blend && layer === chu -> progress
                layer === primary -> 1f
                else -> 0f
            }
            if (layer.page.view.alpha != opacity) layer.page.view.alpha = opacity
            layer.page.setMediaVisible(opacity > 0f)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        attached = false
        initialVisualReady = null
        lastVisible = null
        noneColorTransition.dispose()
        val pages = layers.values.map { it.page }
        layers.clear()
        pages.forEach(::release)
        container.removeView(layersRoot)
    }

    private fun release(page: BackgroundPage) {
        layersRoot.removeView(page.view)
        page.destroy()
    }
}
