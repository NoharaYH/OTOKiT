package com.otokit.noharayh.ui.shell.background

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.otokit.noharayh.ui.theme.GameLine

/**
 * 固定两项的页面承载：0 = Mai、1 = Chu。
 *
 * Holder 只承载对应页面，不缓存或复制 WebView；回收时解除父子关系，页面实例仍归宿主所有。
 */
internal class BackgroundPagerAdapter(
    private val pages: Map<GameLine, BackgroundPage>,
) : RecyclerView.Adapter<BackgroundPagerAdapter.Holder>() {
    private val boundHolders = mutableSetOf<Holder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val container = FrameLayout(parent.context)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return Holder(container)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val gameLine = BackgroundPagerMapping.gameForPage(position) ?: return
        val page = pages.getValue(gameLine)
        val pageView = page.view
        (pageView.parent as? ViewGroup)?.removeView(pageView)
        holder.container.removeAllViews()
        holder.gameLine = gameLine
        boundHolders.add(holder)
        holder.container.addView(
            pageView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun onViewRecycled(holder: Holder) {
        boundHolders.remove(holder)
        holder.gameLine = null
        holder.container.removeAllViews()
    }

    /** 读取本 Adapter 创建的页根原始布局坐标；不受淡变 translationX 影响。 */
    fun isAtEndpoint(gameLine: GameLine, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && boundHolders.any { holder ->
            holder.gameLine == gameLine && holder.container.run {
                isAttachedToWindow && isLaidOut && !isLayoutRequested &&
                    left == 0 && top == 0 && this.width == width && this.height == height
            }
        }

    override fun getItemCount(): Int = 2

    class Holder(val container: FrameLayout) : RecyclerView.ViewHolder(container) {
        var gameLine: GameLine? = null
    }
}
