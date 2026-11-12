package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePanelOutlineTest {
    @Test fun touchingArcsTolerateRoundingAtPhoneAndTabletScales() {
        // 两台设备崩溃日志中的实际反向区间；闭合胶囊没有可展开的直线段。
        for ((top, bottom) in listOf(54.000004f to 54f, 77.00001f to 77f,
            58.000004f to 58f, 40.500004f to 40.5f)) {
            for (preferred in listOf(0f, bottom, 200f)) {
                assertEquals(top, themePanelSideEnd(top, bottom, preferred), 0f)
            }
        }
    }

    @Test fun expandedPanelRetainsItsOriginalLimits() {
        assertEquals(20f, themePanelSideEnd(20f, 100f, 10f), 0f)
        assertEquals(55f, themePanelSideEnd(20f, 100f, 55f), 0f)
        assertEquals(100f, themePanelSideEnd(20f, 100f, 120f), 0f)
    }
}
