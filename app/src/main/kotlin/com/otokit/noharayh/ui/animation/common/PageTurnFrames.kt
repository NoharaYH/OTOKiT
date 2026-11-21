package com.otokit.noharayh.ui.animation.common

/** Finite opacity, free spatial progress: a spring may cross its destination. */
object PageTurnFrames {
    fun enter(progress: Float, dx: Float = 0f, dy: Float = 0f) = TransitionFrame(
        ((progress - .3f) / .7f).coerceIn(0f, 1f), 1f,
        dx * (1f - progress), dy * (1f - progress),
    )

    fun exit(progress: Float, dx: Float = 0f, dy: Float = 0f,
        from: TransitionFrame = TransitionFrame(1f, 1f, 0f, 0f),
    ) = TransitionFrame(
        from.alpha * (1f - progress / .55f).coerceIn(0f, 1f),
        from.scale + (1f - from.scale) * progress,
        from.translationX + (dx - from.translationX) * progress,
        from.translationY + (dy - from.translationY) * progress,
    )
}
