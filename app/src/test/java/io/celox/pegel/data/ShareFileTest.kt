package io.celox.pegel.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFileTest {
    @Test
    fun `a profile name becomes a harmless file name`() {
        assertEquals("Auto.pegel", shareFileName("Auto"))
        assertEquals("Büro links.pegel", shareFileName("Büro links"))
        assertEquals("a_b_c.pegel", shareFileName("a/b\\c"))
        assertEquals("evil.pegel", shareFileName("../../evil"))
        assertEquals("profile.pegel", shareFileName("???"))
        assertEquals("profile.pegel", shareFileName(" . "))
    }

    @Test
    fun `backup files carry the date`() {
        assertEquals("pegel-backup-2026-11-14.json", backupFileName(java.time.LocalDate.of(2026, 11, 14)))
    }
}
