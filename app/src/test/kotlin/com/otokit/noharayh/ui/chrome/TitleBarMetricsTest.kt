package com.otokit.noharayh.ui.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBarMetricsTest {

    /** density = 1 时的取整顺序与来源公式一致。 */
    @Test
    fun calculatesAtDensityOne() {
        val metrics = calculate(1f)

        assertEquals(12, metrics.padPx)
        assertEquals(45, metrics.buttonSizePx)
        assertEquals(70, metrics.rowHeightPx)
        assertEquals(69, metrics.titleAnchorStartPx)
        assertEquals(10, metrics.iconPaddingPx)
        assertEquals(13.5f, metrics.englishTextSizePx, 0.001f)
        assertEquals(22.77f, metrics.chineseTextSizePx, 0.01f)
    }

    /** 本机密度下的实际值：间距 53、按钮 135、行高 206。 */
    @Test
    fun calculatesAtDeviceDensity() {
        val metrics = calculate(2.975f)

        assertEquals(35, metrics.padPx)
        assertEquals(135, metrics.buttonSizePx)
        assertEquals(206, metrics.rowHeightPx)
        assertEquals(205, metrics.titleAnchorStartPx)
        assertEquals(32, metrics.iconPaddingPx)
        assertEquals(40.5f, metrics.englishTextSizePx, 0.001f)
        assertEquals(68.31f, metrics.chineseTextSizePx, 0.01f)
    }

    /** 小密度下图标内边距不为负，行高保持正数。 */
    @Test
    fun keepsValuesNonNegative() {
        val metrics = calculate(0.1f)

        assertTrue(metrics.rowHeightPx > 0)
        assertTrue(metrics.iconPaddingPx >= 0)
        assertTrue(metrics.buttonSizePx > 0)
    }

    /** 行高随密度单调增加，不出现固定 72dp 的写死值。 */
    @Test
    fun rowHeightGrowsWithDensity() {
        val compact = calculate(1f)
        val device = calculate(3.5f)

        assertTrue(device.rowHeightPx > compact.rowHeightPx)
    }

    private fun calculate(density: Float): TitleBarMetrics = TitleBarMetricsCalculator.calculate(
        TitleBarDimensionInput(18f * density, 12f * density, 24f * density,
            2.5f * density, 1.25f * density),
    )
}
