package io.celox.pegel.ui.suggest

import io.celox.pegel.data.DeviceEntity
import io.celox.pegel.data.SUGGESTION_DISMISSED
import io.celox.pegel.data.SUGGESTION_DONE
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.DeviceType
import io.celox.pegel.device.HeadphoneCatalog
import io.celox.pegel.device.OutputDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionUiTest {

    private val catalog = HeadphoneCatalog.parse(
        listOf(
            "Samsung Galaxy Buds2 Pro\toratory1990\tin-ear\t-1.9\t-2.4\t0.5\t-0.3\t-0.7\t0.0\t0.2\t0.6\t-1.9\t2.6\t-7.9",
            "Samsung Galaxy Buds2 Pro (ANC mode)\tcrinacle\tin-ear\t-3.0\t1\t1\t1\t1\t1\t1\t1\t1\t1\t1",
        ).joinToString("\n")
    )
    private val buds = OutputDevice(DeviceKind.BLUETOOTH, "Galaxy Buds2 Pro")
    private val jbl = OutputDevice(DeviceKind.BLUETOOTH, "JBL Charge 4")
    private val speaker = OutputDevice(DeviceKind.SPEAKER, "")

    private fun stored(d: OutputDevice, profileId: Long? = null, type: String? = null, suggestion: String? = null) =
        DeviceEntity(d.key, d.name, profileId, 0, type, suggestion)

    private fun on(d: OutputDevice?, e: DeviceEntity? = null) = suggestionFor(d, e, catalog)

    @Test
    fun `nothing connected, nothing suggested`() {
        assertEquals(Suggestion.None, on(null))
    }

    @Test
    fun `known headphones get their measured curve, with the variants as alternatives`() {
        val s = on(buds) as Suggestion.Headphones
        assertEquals("Samsung Galaxy Buds2 Pro", s.model.name)
        assertEquals(listOf("Samsung Galaxy Buds2 Pro (ANC mode)"), s.alternatives.map { it.name })
        assertEquals(buds, s.device)
    }

    @Test
    fun `the phone's own speaker gets its start curve`() {
        assertEquals(Suggestion.StartCurve(speaker, DeviceType.PHONE_SPEAKER), on(speaker))
    }

    @Test
    fun `an unknown device is asked what it is`() {
        assertEquals(Suggestion.AskType(jbl), on(jbl))
        assertEquals(Suggestion.AskType(jbl), on(jbl, stored(jbl)))
        assertEquals(Suggestion.AskType(OutputDevice(DeviceKind.WIRED, "")), on(OutputDevice(DeviceKind.WIRED, "")))
    }

    @Test
    fun `once the type is known the matching start curve is offered`() {
        assertEquals(
            Suggestion.StartCurve(jbl, DeviceType.SPEAKER_LARGE),
            on(jbl, stored(jbl, type = DeviceType.SPEAKER_LARGE.name)),
        )
    }

    @Test
    fun `headphones the table does not know are sent to the search`() {
        val unknown = OutputDevice(DeviceKind.BLUETOOTH, "Saya")
        assertEquals(Suggestion.PickModel(unknown), on(unknown, stored(unknown, type = DeviceType.HEADPHONES.name)))
    }

    @Test
    fun `a device with a profile is left alone`() {
        assertEquals(Suggestion.None, on(buds, stored(buds, profileId = 7)))
        assertEquals(Suggestion.None, on(speaker, stored(speaker, profileId = 7)))
    }

    @Test
    fun `a suggestion that was answered does not come back`() {
        assertEquals(Suggestion.None, on(buds, stored(buds, suggestion = SUGGESTION_DISMISSED)))
        assertEquals(Suggestion.None, on(jbl, stored(jbl, suggestion = SUGGESTION_DONE)))
        assertEquals(Suggestion.None, on(speaker, stored(speaker, suggestion = SUGGESTION_DISMISSED)))
    }

    @Test
    fun `while the table is still loading only what does not need it is offered`() {
        assertEquals(Suggestion.StartCurve(speaker, DeviceType.PHONE_SPEAKER), suggestionFor(speaker, null, null))
        assertEquals(
            Suggestion.StartCurve(jbl, DeviceType.CAR),
            suggestionFor(jbl, stored(jbl, type = DeviceType.CAR.name), null),
        )
        // these would be wrong guesses without the table: the device may be a known headphone
        assertEquals(Suggestion.None, suggestionFor(jbl, null, null))
        assertEquals(Suggestion.None, suggestionFor(buds, stored(buds, type = DeviceType.HEADPHONES.name), null))
    }

    @Test
    fun `asking again ignores the earlier answer but not the facts`() {
        val dismissed = stored(jbl, type = DeviceType.CAR.name, suggestion = SUGGESTION_DISMISSED)
        assertEquals(Suggestion.StartCurve(jbl, DeviceType.CAR), suggestionFor(jbl, dismissed, catalog, askAgain = true))
        // a bound profile does not stop someone who asks for a suggestion
        assertEquals(
            "Samsung Galaxy Buds2 Pro",
            (suggestionFor(buds, stored(buds, profileId = 7), catalog, askAgain = true) as Suggestion.Headphones).model.name,
        )
    }
}
