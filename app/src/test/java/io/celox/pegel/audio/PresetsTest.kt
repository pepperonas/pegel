package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun `ten band centres an octave apart from 31 Hz to 16 kHz`() {
        assertEquals(BAND_COUNT, BAND_CENTERS_HZ.size)
        assertEquals(31.25f, BAND_CENTERS_HZ.first())
        assertEquals(16000f, BAND_CENTERS_HZ.last())
        BAND_CENTERS_HZ.zipWithNext().forEach { (a, b) -> assertEquals(a * 2, b) }
    }

    @Test
    fun `twenty-two built-in presets with unique ids`() {
        assertEquals(22, BUILT_IN_PRESETS.size)
        assertEquals(22, BUILT_IN_PRESETS.map { it.id }.toSet().size)
        assertTrue(BUILT_IN_PRESETS.all { it.builtIn })
    }

    @Test
    fun `the genres and media uses people ask for are there`() {
        val ids = BUILT_IN_PRESETS.map { it.id }
        for (id in listOf("techno", "house", "dnb", "electronic", "rock", "metal", "classical", "movie", "gaming", "night", "speech")) {
            assertTrue(id, id in ids)
        }
    }

    @Test
    fun `presets are listed group by group, own ones last`() {
        assertEquals(PresetGroup.TONE, presetGroup(BUILT_IN_PRESETS.first()))
        val groups = BUILT_IN_PRESETS.map { presetGroup(it) }
        assertEquals("built-in presets must be ordered by group", groups.sortedBy { it.ordinal }, groups)
        assertEquals(setOf(PresetGroup.TONE, PresetGroup.MUSIC, PresetGroup.MEDIA), groups.toSet())
        assertEquals(PresetGroup.MEDIA, presetGroup(BUILT_IN_PRESETS.first { it.id == "movie" }))
        assertEquals(PresetGroup.MUSIC, presetGroup(BUILT_IN_PRESETS.first { it.id == "techno" }))
        assertEquals(PresetGroup.OWN, presetGroup(Preset("custom:3", "Mine", List(BAND_COUNT) { 0f }, builtIn = false)))
    }

    @Test
    fun `every built-in preset has a name in both languages and a label in the picker`() {
        val root = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.exists() }
        val en = java.io.File(root, "res/values/strings.xml").readText()
        val de = java.io.File(root, "res/values-de/strings.xml").readText()
        val picker = java.io.File(root, "java/io/celox/pegel/ui/sound/EqCard.kt").readText()
        for (p in BUILT_IN_PRESETS) {
            assertTrue("en ${p.id}", en.contains("name=\"preset_${p.id}\""))
            assertTrue("de ${p.id}", de.contains("name=\"preset_${p.id}\""))
            assertTrue("picker ${p.id}", picker.contains("\"${p.id}\" -> R.string.preset_${p.id}"))
        }
    }

    @Test
    fun `every preset has ten gains inside the range, on the half-decibel grid`() {
        for (p in BUILT_IN_PRESETS) {
            assertEquals(p.id, BAND_COUNT, p.gainsDb.size)
            assertTrue(p.id, p.gainsDb.all { it in -GAIN_LIMIT_DB..GAIN_LIMIT_DB && (it * 2) % 1f == 0f })
        }
    }

    @Test
    fun `flat is flat and comes first`() {
        assertEquals("flat", BUILT_IN_PRESETS.first().id)
        assertEquals(List(BAND_COUNT) { 0f }, BUILT_IN_PRESETS.first().gainsDb)
    }

    @Test
    fun `no two presets sound the same`() {
        assertEquals(BUILT_IN_PRESETS.size, BUILT_IN_PRESETS.map { it.gainsDb }.toSet().size)
    }
}
