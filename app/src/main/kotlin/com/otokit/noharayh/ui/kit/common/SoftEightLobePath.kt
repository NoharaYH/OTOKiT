package com.otokit.noharayh.ui.kit.common

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Materkit 原有八瓣圆润装饰；调用方缓存路径并负责缩放、裁切与动画。 */
internal fun softEightLobePath(radius: Float = 82f): Path = Path().apply {
    val count = 96
    val points = Array(count) { i ->
        val angle = 2.0 * PI * i / count
        val r = (76.0 + 6.0 * cos(8.0 * angle)) * radius / 82.0
        floatArrayOf((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
    }
    moveTo((points.last()[0] + points[0][0]) / 2f, (points.last()[1] + points[0][1]) / 2f)
    for (i in points.indices) {
        val next = points[(i + 1) % count]
        quadTo(points[i][0], points[i][1], (points[i][0] + next[0]) / 2f, (points[i][1] + next[1]) / 2f)
    }
    close()
}
