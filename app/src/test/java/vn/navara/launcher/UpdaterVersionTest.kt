package vn.navara.launcher

import org.junit.Assert.*
import org.junit.Test

class UpdaterVersionTest {
    @Test fun newerMinor() = assertTrue(isNewerVersion("v0.29", "0.28"))
    @Test fun sameVersion() = assertFalse(isNewerVersion("v0.28", "0.28"))
    @Test fun olderVersion() = assertFalse(isNewerVersion("0.27", "0.28"))
    @Test fun numericNotLexical() = assertTrue(isNewerVersion("0.10", "0.9"))
    @Test fun patchLevel() = assertTrue(isNewerVersion("0.28.1", "0.28"))
    @Test fun trailingZeros() = assertFalse(isNewerVersion("0.28.0", "0.28"))
    @Test fun garbageIsNotNewer() = assertFalse(isNewerVersion("abc", "0.28"))
}
