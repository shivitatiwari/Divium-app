package com.divium.ide

import com.divium.core.model.LanguagePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguagePackTest {
    @Test
    fun idsResolveToStableLanguagePacks() {
        assertEquals(LanguagePack.PYTHON, LanguagePack.fromId("python"))
        assertEquals(LanguagePack.JAVASCRIPT, LanguagePack.fromId("javascript"))
        assertNull(LanguagePack.fromId("unknown"))
    }
}
