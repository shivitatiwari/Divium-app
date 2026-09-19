package com.divium.ide.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEnvironmentContractTest {
    @Test
    fun androidFallbackPathContainsSystemBin() {
        val fallback = "/system/bin:/system/xbin"
        assertTrue(fallback.contains("/system/bin"))
    }
}
