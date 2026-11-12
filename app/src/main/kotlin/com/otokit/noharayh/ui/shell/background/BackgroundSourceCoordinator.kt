package com.otokit.noharayh.ui.shell.background

import com.otokit.noharayh.ui.theme.BackgroundSelection
import com.otokit.noharayh.ui.theme.GameLine

/** 协调加载次序和渲染缓存；不持有已提交的音游选择，也不操作 Pager。 */
internal class BackgroundSourceCoordinator(
    private val show: (GameLine, BackgroundSelection) -> Boolean,
) {
    private val sources = mutableMapOf<GameLine, BackgroundSelection>()
    private val ready = mutableSetOf<GameLine>()

    fun render(current: GameLine, mai: BackgroundSelection, chu: BackgroundSelection) {
        sources[GameLine.Mai] = mai
        sources[GameLine.Chu] = chu
        select(current)
    }

    fun select(current: GameLine) {
        ensureLoaded(current)
        if (isReady(current)) loadOther(current)
    }

    fun onVisualReady(game: GameLine, current: GameLine) {
        ready.add(game)
        if (game == current) loadOther(current)
    }

    fun isReady(game: GameLine): Boolean = game in ready

    fun clear() {
        sources.clear()
        ready.clear()
    }

    private fun loadOther(current: GameLine) {
        ensureLoaded(if (current == GameLine.Mai) GameLine.Chu else GameLine.Mai)
    }

    private fun ensureLoaded(game: GameLine) {
        val source = sources[game] ?: return
        if (show(game, source)) ready.remove(game)
    }
}
