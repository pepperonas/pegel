package io.celox.pegel.data

import io.celox.pegel.audio.SoundState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceSuggestionRepoTest {

    private val fx = RepoFixture()
    private val repo = fx.repository()
    private val key = "BLUETOOTH:JBL Charge 4"
    private val gains = listOf(0f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f)

    @After fun tearDown() = fx.close()

    private suspend fun device() = repo.devices.first().single { it.key == key }

    @Test
    fun `the type of a device is stored and survives seeing and binding it`() = runBlocking {
        repo.seeDevice(key, "JBL Charge 4")
        repo.setDeviceType(key, "JBL Charge 4", "SPEAKER_LARGE")
        repo.seeDevice(key, "JBL Charge 4")
        repo.bindDevice(key, null)
        assertEquals("SPEAKER_LARGE", device().type)
        assertNull(device().suggestion)
    }

    @Test
    fun `the type can be set for a device that was never seen`() = runBlocking {
        repo.setDeviceType(key, "JBL Charge 4", "CAR")
        assertEquals("CAR", device().type)
        assertEquals("JBL Charge 4", device().label)
    }

    @Test
    fun `not now is remembered across seeing the device again`() = runBlocking {
        repo.dismissSuggestion(key, "JBL Charge 4")
        repo.seeDevice(key, "JBL Charge 4")
        assertEquals("dismissed", device().suggestion)
    }

    @Test
    fun `applying a suggestion creates, binds and loads a profile for the device`() = runBlocking {
        repo.update { it.copy(bassOn = true, gainsDb = List(10) { 5f }) } // what the user had before
        val id = repo.applySuggestion(key, "JBL Charge 4", "JBL Charge 4", gains)
        val profile = repo.profiles.first().single()
        assertEquals(id, profile.id)
        assertEquals("JBL Charge 4", profile.name)
        // a device profile starts clean: the curve and nothing else
        assertEquals(SoundState(gainsDb = gains, presetId = null), profile.state)
        assertEquals(profile.state, repo.state.value)
        assertEquals(profile.id, repo.activeProfile.value?.id)
        assertEquals(profile.id, device().profileId)
        assertEquals("done", device().suggestion)
        assertEquals(profile.id, repo.profileForDevice(key)?.id)
        assertEquals(key, repo.lastDeviceKey)
    }

    @Test
    fun `applying a suggestion remembers the active device so starting the service does not wipe edits`() = runBlocking {
        repo.applySuggestion(key, "JBL Charge 4", "JBL Charge 4", gains)
        repo.update { it.copy(loudnessOn = true, virtualizerOn = true) }
        val device = io.celox.pegel.device.OutputDevice(io.celox.pegel.device.DeviceKind.BLUETOOTH, "JBL Charge 4")
        assertNull(io.celox.pegel.device.AutoApply(repo).onActiveDevice(device))
        assertEquals(true, repo.state.value.loudnessOn)
        assertEquals(true, repo.state.value.virtualizerOn)
    }

    @Test
    fun `a suggestion for a device that is not playing leaves the sound in use alone`() = runBlocking {
        repo.update { it.copy(bassOn = true) }
        val before = repo.state.value
        val id = repo.applySuggestion(key, "JBL Charge 4", "JBL Charge 4", gains, load = false)
        assertEquals(before, repo.state.value)
        assertNull(repo.activeProfile.value)
        assertEquals(id, device().profileId)
    }

    @Test
    fun `a taken profile name gets a number instead of failing`() = runBlocking {
        repo.saveAsProfile("JBL Charge 4")
        repo.saveAsProfile("jbl charge 4 2")
        repo.applySuggestion(key, "JBL Charge 4", "JBL Charge 4", gains)
        assertEquals(listOf("JBL Charge 4", "jbl charge 4 2", "JBL Charge 4 3"), repo.profiles.first().map { it.name }.sortedBy { it.length })
    }

    @Test
    fun `the curve is normalized before it is stored`() = runBlocking {
        repo.applySuggestion(key, "X", "X", listOf(40f, -40f, 0.26f))
        assertEquals(listOf(12f, -12f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), repo.state.value.gainsDb)
    }

    @Test
    fun `type and answer travel in a backup, and an old backup reads without them`() = runBlocking {
        repo.setDeviceType(key, "JBL Charge 4", "SPEAKER_LARGE")
        repo.dismissSuggestion(key, "JBL Charge 4")
        val text = repo.backupText()
        repo.forgetDevice(key)
        repo.restoreFrom(text).getOrThrow()
        assertEquals("SPEAKER_LARGE", device().type)
        assertEquals("dismissed", device().suggestion)

        val json = org.json.JSONObject(text)
        val devices = json.getJSONArray("devices")
        for (i in 0 until devices.length()) devices.getJSONObject(i).apply { remove("type"); remove("suggestion") }
        val old = BackupCodec.decode(json.toString()).getOrThrow()
        assertNull(old.devices.single { it.key == key }.type)
        assertNull(old.devices.single { it.key == key }.suggestion)
    }
}
