package io.celox.pegel.device

import io.celox.pegel.audio.BAND_COUNT
import io.celox.pegel.audio.GAIN_LIMIT_DB
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphoneCatalogTest {

    private val small = HeadphoneCatalog.parse(
        listOf(
            "Samsung Galaxy Buds2 Pro\toratory1990\tin-ear\t-1.9\t-2.4\t0.5\t-0.3\t-0.7\t0.0\t0.2\t0.6\t-1.9\t2.6\t-7.9",
            "Samsung Galaxy Buds2 Pro (ANC mode)\tcrinacle\tin-ear\t-3.0\t1\t1\t1\t1\t1\t1\t1\t1\t1\t1",
            "Sony WH-1000XM4\toratory1990\tover-ear\t-4.0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "1MORE Aero (ANC Off)\tHypetheSonics\tin-ear\t-2.0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "1MORE Aero (ANC On)\tHypetheSonics\tin-ear\t-2.0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "Apple AirPods Pro\toratory1990\tin-ear\t-5.0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t20",
            "Koss KSC75\toratory1990\tearbud\t-6.0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
        ).joinToString("\n")
    )

    private fun names(device: String) = small.match(device).map { it.name }

    @Test
    fun `a line becomes a model`() {
        val m = small.models.first { it.name == "Samsung Galaxy Buds2 Pro" }
        assertEquals("oratory1990", m.source)
        assertEquals("in-ear", m.form)
        assertEquals(-1.9f, m.preampDb, 0f)
        assertEquals(listOf(-2.4f, 0.5f, -0.3f, -0.7f, 0f, 0.2f, 0.6f, -1.9f, 2.6f, -7.9f), m.gainsDb)
    }

    @Test
    fun `broken lines are skipped, not fatal`() {
        val c = HeadphoneCatalog.parse("no tabs here\nA\tb\tc\t0\t1\t2\nB\ts\tf\tx\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\n\n")
        assertTrue(c.models.isEmpty())
    }

    @Test
    fun `the full name matches, the plain model before its variants`() {
        assertEquals(
            listOf("Samsung Galaxy Buds2 Pro", "Samsung Galaxy Buds2 Pro (ANC mode)"),
            names("Samsung Galaxy Buds2 Pro"),
        )
    }

    @Test
    fun `a device that leaves out the brand matches`() {
        assertEquals("Samsung Galaxy Buds2 Pro", names("Galaxy Buds2 Pro").first())
        assertEquals(listOf("Sony WH-1000XM4"), names("WH-1000XM4"))
    }

    @Test
    fun `spelling noise does not matter`() {
        assertEquals(listOf("Sony WH-1000XM4"), names("LE_WH-1000XM4"))
        assertEquals(listOf("Sony WH-1000XM4"), names("wh1000xm4"))
        assertEquals("Samsung Galaxy Buds2 Pro", names("Galaxy Buds2 Pro (80A1)").first())
    }

    @Test
    fun `a renamed device still matches on the model inside its name`() {
        assertEquals(listOf("Apple AirPods Pro"), names("Martins AirPods Pro"))
    }

    @Test
    fun `a model that only exists as variants offers all of them`() {
        assertEquals(listOf("1MORE Aero (ANC Off)", "1MORE Aero (ANC On)"), names("1MORE Aero"))
    }

    @Test
    fun `no match is better than a wrong one`() {
        for (device in listOf("JBL Charge 4", "CAR BT", "Saya", "", "  ", "Pro", "Sony", "KSC", "Aero")) {
            assertEquals(device, emptyList<String>(), names(device))
        }
    }

    @Test
    fun `search finds by any words in any order`() {
        assertEquals(listOf("Samsung Galaxy Buds2 Pro", "Samsung Galaxy Buds2 Pro (ANC mode)"), small.search("buds2 samsung").map { it.name })
        assertEquals(listOf("Sony WH-1000XM4"), small.search("1000xm4").map { it.name })
        assertTrue(small.search("").isEmpty())
        assertTrue(small.search("zzz").isEmpty())
    }

    @Test
    fun `the preamp is folded into the bands, snapped and clamped`() {
        val buds = small.models.first { it.name == "Samsung Galaxy Buds2 Pro" }
        // -2.4-1.9=-4.3 → -4.5 ; 2.6-1.9=0.7 → 0.5 ; -7.9-1.9=-9.8 → -10
        assertEquals(listOf(-4.5f, -1.5f, -2f, -2.5f, -2f, -1.5f, -1.5f, -4f, 0.5f, -10f), buds.correctionDb())
        val airpods = small.models.first { it.name == "Apple AirPods Pro" }
        assertEquals(GAIN_LIMIT_DB, airpods.correctionDb().last(), 0f)
        assertEquals(BAND_COUNT, airpods.correctionDb().size)
    }

    // ---- the shipped table ----

    private val shipped by lazy {
        val f = listOf(File("src/main/assets/headphones.tsv"), File("app/src/main/assets/headphones.tsv")).first { it.exists() }
        f.readText() to HeadphoneCatalog.parse(f.readText())
    }

    @Test
    fun `every line of the shipped table is a model with a unique name`() {
        val (text, catalog) = shipped
        assertEquals(text.lines().count { it.isNotBlank() }, catalog.models.size)
        assertTrue(catalog.models.size > 5000)
        assertEquals(catalog.models.size, catalog.models.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun `the shipped table carries the published values`() {
        val m = shipped.second.models.first { it.name == "Samsung Galaxy Buds2 Pro" }
        assertEquals(-1.9f, m.preampDb, 0f)
        assertEquals(listOf(-2.4f, 0.5f, -0.3f, -0.7f, 0f, 0.2f, 0.6f, -1.9f, 2.6f, -7.9f), m.gainsDb)
    }

    @Test
    fun `real device names against the shipped table`() {
        val c = shipped.second
        // paired with the developer's phone: speakers, cars, adapters — none is a headphone
        for (device in listOf(
            "JBL Charge 4", "JBL GO 2", "Teufel Power HiFi", "Logitech BT Adapter", "CAR BT", "Porsche BT 0873",
            "KMM-BT5**DAB", "MB Bluetooth", "Daily DAB Radio", "Saya", "LEDSpeaker-6084CA", "Galaxy Watch (80A1)", "OBDII",
            "JBL Flip 6", "UE BOOM 3", "Bose SoundLink Mini II", "Marshall Emberton", "Soundcore 2", "Echo Dot", "SRS-XB13",
        )) {
            assertEquals(device, emptyList<String>(), c.match(device).map { it.name })
        }
        assertEquals("Samsung Galaxy Buds2 Pro", c.match("Galaxy Buds2 Pro").first().name)
        assertEquals("Sony WF-1000XM5", c.match("WF-1000XM5").first().name)
        assertEquals("Sony WH-1000XM4", c.match("LE_WH-1000XM4").first().name)
        assertNull(c.match("Galaxy S24 Ultra").firstOrNull())
    }
}
