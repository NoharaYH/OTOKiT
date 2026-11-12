package com.otokit.noharayh.ui.constants.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayScaleTest {

    @Test
    fun stableDensityAndFallbackProduceFixedLogicalDensity() {
        assertEquals(476, DisplayScale.densityDpi(stableDensityDpi = 560, fallbackDensityDpi = 420))
        assertEquals(476, DisplayScale.densityDpi(stableDensityDpi = 0, fallbackDensityDpi = 560))
        assertEquals(392, DisplayScale.densityDpi(560, 0, DisplayScaleOption.Small.scale))
        assertEquals(476, DisplayScale.densityDpi(560, 0, DisplayScaleOption.Standard.scale))
        assertEquals(560, DisplayScale.densityDpi(560, 0, DisplayScaleOption.Large.scale))
        assertEquals(DisplayScaleOption.Standard, DisplayScaleOption.DEFAULT)
    }

    @Test
    fun optionStorageValuesRoundTrip() {
        DisplayScaleOption.entries.forEach { option ->
            assertEquals(option, DisplayScaleOption.fromSavedValue(option.savedValue))
        }
        assertNull(DisplayScaleOption.fromSavedValue(null))
        assertNull(DisplayScaleOption.fromSavedValue("invalid"))
    }

    @Test
    fun legacyStorageKeeps85And100PercentAndCapsRemoved115Percent() {
        assertEquals(DisplayScaleOption.Standard, DisplayScaleOption.fromSavedValue("small"))
        assertEquals(DisplayScaleOption.Large, DisplayScaleOption.fromSavedValue("standard"))
        assertEquals(DisplayScaleOption.Large, DisplayScaleOption.fromSavedValue("large"))
    }
}
