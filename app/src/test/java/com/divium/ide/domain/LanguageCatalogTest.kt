package com.divium.ide.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCatalogTest {
    @Test
    fun starterPacksHaveUniqueIds() {
        val ids = LanguageCatalog.starterPacks.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun selectedLanguageSummaryIsUseful() {
        assertEquals("No packs selected", LanguageCatalog.totalSizeFor(emptySet()))
        assertTrue(LanguageCatalog.totalSizeFor(setOf("javascript")).contains("1 selected"))
    }
}
