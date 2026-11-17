package com.otokit.noharayh.transfer.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetPageInspectionTest {
    @Test fun identifiesBusinessErrorInSuccessfulHttpResponse() {
        assertEquals("100106", NetPageInspection.businessErrorCode("<h2>错误</h2><p>错误码: 100106</p>"))
        assertEquals("100106", NetPageInspection.businessErrorCode("<p>エラーコード：100106</p>"))
    }
    @Test fun doesNotTreatScoreOrUnboundedTextAsErrorCode() {
        assertNull(NetPageInspection.businessErrorCode("<div>100.1060%</div>"))
        assertNull(NetPageInspection.businessErrorCode("错误码: 1234567890123"))
    }
}
