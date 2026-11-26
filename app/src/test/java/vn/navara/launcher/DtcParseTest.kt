package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class DtcParseTest {
    @Test fun noData() = assertTrue(ObdManager.parseDtc("NO DATA").isEmpty())
    @Test fun canTwoCodes() = assertEquals(listOf("P0133", "P0134"), ObdManager.parseDtc("43 02 01 33 01 34 00"))
    @Test fun canNoCodes() = assertTrue(ObdManager.parseDtc("43 00").isEmpty())
}
