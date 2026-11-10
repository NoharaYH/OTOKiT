package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.motion.TransitionKind
import kotlin.math.roundToInt

/** 进度帧：所有字段都是相对量，由消费者换算成像素或直接写入属性。 */
data class TransitionFrame(
    val alpha: Float,
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
)

/**
 * 转场纯帧计算。
 *
 * 进度统一为 0→1 的已提交方向；反向由消费者从当前帧重算，不在这里维护方向状态。
 * 类型只决定允许变化的属性，幅度和像素量由调用方传入；in/out 共用同一套映射。
 */
object TransitionFrames {

    fun enter(
        kind: TransitionKind,
        progress: Float,
        scaleFrom: Float = 1f,
        translationXFrom: Float = 0f,
        translationYFrom: Float = 0f,
    ): TransitionFrame {
        val p = progress.coerceIn(0f, 1f)
        return when (kind) {
            TransitionKind.Fade -> TransitionFrame(alpha = p, scale = 1f, translationX = 0f, translationY = 0f)
            TransitionKind.Slide -> TransitionFrame(
                alpha = 1f,
                scale = 1f,
                translationX = translationXFrom * (1f - p),
                translationY = translationYFrom * (1f - p),
            )

            TransitionKind.Scale -> TransitionFrame(
                alpha = 1f,
                scale = lerp(scaleFrom, 1f, p),
                translationX = 0f,
                translationY = 0f,
            )

            TransitionKind.ScaleFadeSlide -> TransitionFrame(
                alpha = p,
                scale = lerp(scaleFrom, 1f, p),
                translationX = translationXFrom * (1f - p),
                translationY = translationYFrom * (1f - p),
            )
        }
    }

    fun exit(
        kind: TransitionKind,
        progress: Float,
        scaleTo: Float = 1f,
        translationXTo: Float = 0f,
        translationYTo: Float = 0f,
    ): TransitionFrame {
        val p = progress.coerceIn(0f, 1f)
        return when (kind) {
            TransitionKind.Fade -> TransitionFrame(alpha = 1f - p, scale = 1f, translationX = 0f, translationY = 0f)
            TransitionKind.Slide -> TransitionFrame(
                alpha = 1f,
                scale = 1f,
                translationX = translationXTo * p,
                translationY = translationYTo * p,
            )

            TransitionKind.Scale -> TransitionFrame(
                alpha = 1f,
                scale = lerp(1f, scaleTo, p),
                translationX = 0f,
                translationY = 0f,
            )

            TransitionKind.ScaleFadeSlide -> TransitionFrame(
                alpha = 1f - p,
                scale = lerp(1f, scaleTo, p),
                translationX = translationXTo * p,
                translationY = translationYTo * p,
            )
        }
    }

    /**
     * Mai/Chu 双页跟手帧。
     *
     * progress 固定为 Mai(0)→Chu(1)，不随手势方向翻转；类型只裁剪参与变化的属性。
     */
    fun pagerPair(
        kind: TransitionKind,
        progress: Float,
        slideDistancePx: Float,
        minScale: Float,
        minAlpha: Float,
    ): Pair<TransitionFrame, TransitionFrame> {
        val p = progress.coerceIn(0f, 1f)
        val scaleDelta = 1f - minScale.coerceIn(0f, 1f)
        val alphaDelta = 1f - minAlpha.coerceIn(0f, 1f)
        val useSlide = kind == TransitionKind.Slide || kind == TransitionKind.ScaleFadeSlide
        val useScale = kind == TransitionKind.Scale || kind == TransitionKind.ScaleFadeSlide
        val useFade = kind == TransitionKind.Fade || kind == TransitionKind.ScaleFadeSlide

        val mai = TransitionFrame(
            alpha = if (useFade) 1f - alphaDelta * p else 1f,
            scale = if (useScale) 1f - scaleDelta * p else 1f,
            translationX = if (useSlide) -slideDistancePx * p else 0f,
            translationY = 0f,
        )
        val chu = TransitionFrame(
            alpha = if (useFade) minAlpha + alphaDelta * p else 1f,
            scale = if (useScale) minScale + scaleDelta * p else 1f,
            translationX = if (useSlide) slideDistancePx * (1f - p) else 0f,
            translationY = 0f,
        )
        return mai to chu
    }

    /** 线性插值，保留给需要纯计算的测试与消费者。 */
    fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress.coerceIn(0f, 1f)

    /** 进度对应的整数像素，避免各执行器自行四舍五入产生偏差。 */
    fun px(value: Float): Int = value.roundToInt()
}
