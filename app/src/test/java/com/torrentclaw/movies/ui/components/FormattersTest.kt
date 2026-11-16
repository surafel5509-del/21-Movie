package com.torrentclaw.movies.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `fileSize renders binary units`() {
        assertEquals("—", Formatters.fileSize(null))
        assertEquals("—", Formatters.fileSize(0))
        assertEquals("512 B", Formatters.fileSize(512))
        assertEquals("1.0 KB", Formatters.fileSize(1_024))
        assertEquals("500.0 KB", Formatters.fileSize(512_000))
        assertEquals("2.0 GB", Formatters.fileSize(2_147_483_648))
        assertEquals("1.5 TB", Formatters.fileSize(1_649_267_441_664))
    }

    @Test
    fun `compactCount abbreviates thousands and millions`() {
        assertEquals("0", Formatters.compactCount(0))
        assertEquals("999", Formatters.compactCount(999))
        assertEquals("1k", Formatters.compactCount(1_000))
        assertEquals("1.5k", Formatters.compactCount(1_500))
        assertEquals("12.4k", Formatters.compactCount(12_400))
        assertEquals("1M", Formatters.compactCount(1_000_000))
        assertEquals("2.5M", Formatters.compactCount(2_500_000))
    }

    @Test
    fun `yearFromIso parses the year prefix`() {
        assertEquals("2024", Formatters.yearFromIso("2024-03-15T12:00:00Z"))
        assertEquals(null, Formatters.yearFromIso("abc"))
        assertEquals(null, Formatters.yearFromIso(null))
    }

    @Test
    fun `cleanText strips tags and collapses whitespace`() {
        assertEquals("A thief who steals.", Formatters.cleanText("  A <b>thief</b>\n who   steals. "))
        assertEquals(null, Formatters.cleanText("   "))
        assertEquals(null, Formatters.cleanText(null))
    }

    @Test
    fun `rating and healthRatio are defensive`() {
        assertEquals("8.8", Formatters.rating(8.83))
        assertEquals("10.0", Formatters.rating(12.0))
        assertEquals(null, Formatters.rating(null))
        assertEquals(0f, Formatters.healthRatio(0, 0))
        assertEquals(0.9f, Formatters.healthRatio(90, 10))
        assertEquals(92, Formatters.percent(0.92f))
        assertEquals(100, Formatters.percent(4f))
        assertEquals(0, Formatters.percent(-2f))
    }
}
