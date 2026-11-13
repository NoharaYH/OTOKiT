package com.otokit.noharayh.ui.theme

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ColorChannelsTest {
    @Test fun allModelsRoundTripSrgbWithoutDrift() {
        val random = Random(73)
        val colors = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()) +
            List(4000) { random.nextInt(0x1000000) or (0xFF shl 24) }
        for (model in ColorChannels.entries) for (color in colors) {
            assertEquals("$model ${ColorChannels.hex(color)}", color, model.toColor(model.fromColor(color)))
        }
    }
    @Test fun hueWrapAndAchromaticHueAreStable() {
        for (model in listOf(ColorChannels.HSV, ColorChannels.HSL)) {
            assertEquals(237f, model.fromColor(0xFF808080.toInt(), 237f)[0], 0f)
            assertEquals(model.toColor(floatArrayOf(0f, 80f, 70f)), model.toColor(floatArrayOf(360f, 80f, 70f)))
        }
        assertEquals(0xFFFF0000.toInt(), ColorChannels.HSV.toColor(floatArrayOf(0f, 100f, 100f)))
        assertEquals(0xFFFF0000.toInt(), ColorChannels.HSL.toColor(floatArrayOf(0f, 100f, 50f)))
    }
    @Test fun partialOrInvalidHexNeverBecomesAColor() {
        for (text in listOf("", "#", "ABC", "ABCDE", "1234567", "GG7788", "FFFFFFFF", " 123456"))
            assertNull(text, ColorChannels.parseHex(text))
        assertEquals(0xFF12ABEF.toInt(), ColorChannels.parseHex("#12abEF"))
        assertEquals("0000AA", ColorChannels.hex(0xFF0000AA.toInt()))
    }

    @Test fun channelRampsRepresentHueSaturationLightnessAndRgbSeparately() {
        val hsl = ColorChannels.HSL.ramps(floatArrayOf(120f, 100f, 50f))
        assertEquals(0xFFFF0000.toInt(), hsl[0].first())
        assertEquals(0xFF00FF00.toInt(), hsl[0][2])
        assertEquals(0xFF0000FF.toInt(), hsl[0][4])
        assertEquals(0xFF808080.toInt(), hsl[1].first())
        assertEquals(0xFF00FF00.toInt(), hsl[1].last())
        assertEquals(0xFF000000.toInt(), hsl[2].first())
        assertEquals(0xFF00FF00.toInt(), hsl[2][3])
        assertEquals(0xFFFFFFFF.toInt(), hsl[2].last())
        val rgb = ColorChannels.RGB.ramps(floatArrayOf(12f, 34f, 56f))
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
            rgb.map { it.last() }.toIntArray())
    }
}
