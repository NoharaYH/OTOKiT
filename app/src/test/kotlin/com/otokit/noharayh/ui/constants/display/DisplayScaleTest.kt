package com.otokit.noharayh.ui.constants.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayScaleTest {

    @Test
    fun stableDensityAndFallbackProduceFixedLogicalDensity() {
        assertEquals(476, DisplayScale.densityDpi(stableDensityDpi = 560, fallbackDensityDpi = 420))
        assertEquals(476, DisplayScale.densityDpi(stableDensityDpi = 0, fallbackDensityDpi = 560))
        assertEquals(560, DisplayScale.densityDpi(560, 0, DisplayScaleOption.Standard.scale))
        assertEquals(644, DisplayScale.densityDpi(560, 0, DisplayScaleOption.Large.scale))
    }

    @Test
    fun optionStorageValuesRoundTrip() {
        DisplayScaleOption.entries.forEach { option ->
            assertEquals(option, DisplayScaleOption.fromSavedValue(option.savedValue))
        }
        assertNull(DisplayScaleOption.fromSavedValue(null))
    }
}
