package io.celox.pegel.data

import io.celox.pegel.audio.SessionMode
import io.celox.pegel.audio.SoundState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundRepositoryBackupTest {

    private val fx = RepoFixture()
    private val repo = fx.repository()

    @After fun tearDown() = fx.close()

    private suspend fun populate() {
        repo.update { it.withGain(0, 6f) }
        val loud = repo.saveAsProfile("Loud").getOrThrow()
        repo.update { SoundState() }
        repo.saveAsProfile("Flat").getOrThrow()
        repo.savePreset("Mine", List(10) { 2f })
        repo.seeDevice("BLUETOOTH:Buds", "Buds")
        repo.bindDevice("BLUETOOTH:Buds", loud)
        repo.updateSettings { it.copy(mode = SessionMode.GLOBAL_ONLY, showBalance = true) }
        repo.update { it.withGain(3, -4f) } // Flat, modified
    }

    @Test
    fun `export then restore on a fresh install brings everything back`() = runBlocking {
        populate()
        val text = repo.backupText()

        val other = RepoFixture()
        try {
            val fresh = other.repository()
            assertTrue(fresh.restoreFrom(text).isSuccess)
            assertEquals(listOf("Flat", "Loud"), fresh.profiles.first().map { it.name })
            assertEquals(6f, fresh.profiles.first().first { it.name == "Loud" }.state.gainsDb[0])
            assertEquals(listOf("Mine"), fresh.customPresets.first().map { it.name })
            assertEquals("Loud", fresh.profileForDevice("BLUETOOTH:Buds")!!.name)
            assertEquals(SessionMode.GLOBAL_ONLY, fresh.settings.value.mode)
            assertTrue(fresh.settings.value.showBalance)
            assertEquals(-4f, fresh.state.value.gainsDb[3])
            assertEquals("Flat", fresh.activeProfile.value!!.name)
            assertTrue(fresh.modified.value)
            // and it is really stored, not just in memory
            assertEquals("Flat", other.repository().activeProfile.value!!.name)
        } finally {
            other.close()
        }
    }

    @Test
    fun `a restore replaces what was there`() = runBlocking {
        populate()
        val text = repo.backupText()
        repo.saveAsProfile("Extra").getOrThrow()
        repo.seeDevice("USB:DAC", "DAC")
        repo.restoreFrom(text).getOrThrow()
        assertEquals(listOf("Flat", "Loud"), repo.profiles.first().map { it.name })
        assertEquals(listOf("BLUETOOTH:Buds"), repo.devices.first().map { it.key })
    }

    @Test
    fun `a restore never switches Pegel on or off`() = runBlocking {
        populate()
        repo.updateSettings { it.copy(enabled = true) }
        val text = repo.backupText()
        repo.updateSettings { it.copy(enabled = false) }
        repo.restoreFrom(text).getOrThrow()
        assertFalse(repo.settings.value.enabled)
    }

    // Review focus 4
    @Test
    fun `a rejected file leaves everything as it was`() = runBlocking {
        populate()
        val before = repo.backupText()
        for (bad in listOf("", "kein json", "{}", """{"app":"pegel","format":2,"kind":"backup"}""", BackupCodec.encodeProfile("X", SoundState()))) {
            assertTrue("accepted: $bad", repo.restoreFrom(bad).isFailure)
        }
        assertEquals(before, repo.backupText())
        assertEquals("Flat", repo.activeProfile.value!!.name)
    }

    @Test
    fun `a binding to a profile the file does not contain is dropped, not invented`() = runBlocking {
        val text = """{"app":"pegel","format":1,"kind":"backup",
            "profiles":[{"name":"Good","state":{}}],
            "devices":[{"key":"SPEAKER","label":"","profile":"Gone"},{"key":"USB:DAC","label":"DAC","profile":"good"}]}"""
        repo.restoreFrom(text).getOrThrow()
        assertNull(repo.profileForDevice("SPEAKER"))
        assertEquals("Good", repo.profileForDevice("USB:DAC")!!.name)
        assertNull(repo.activeProfile.value)
    }

    @Test
    fun `duplicate names inside a file do not abort the restore`() = runBlocking {
        val text = """{"app":"pegel","format":1,"kind":"backup",
            "profiles":[{"name":"A","state":{"bassOn":true}},{"name":"a","state":{}}],
            "presets":[{"name":"P","gains":[1]},{"name":"p","gains":[2]}]}"""
        repo.restoreFrom(text).getOrThrow()
        val profiles = repo.profiles.first()
        assertEquals(listOf("A"), profiles.map { it.name })
        assertTrue("the first one wins", profiles.single().state.bassOn)
        assertEquals(1, repo.customPresets.first().size)
    }

    @Test
    fun `importing a single profile adds it without touching the sound`() = runBlocking {
        populate()
        val working = repo.state.value
        val id = repo.importProfile(BackupCodec.encodeProfile("Car", SoundState(bassOn = true))).getOrThrow()
        val car = repo.profiles.first().first { it.id == id }
        assertEquals("Car", car.name)
        assertTrue(car.state.bassOn)
        assertEquals(working, repo.state.value)
        assertEquals("Flat", repo.activeProfile.value!!.name)
    }

    @Test
    fun `an imported profile never overwrites one of the same name`() = runBlocking {
        populate()
        repo.importProfile(BackupCodec.encodeProfile("loud", SoundState())).getOrThrow()
        repo.importProfile(BackupCodec.encodeProfile("Loud", SoundState())).getOrThrow()
        val names = repo.profiles.first().map { it.name }
        assertEquals(listOf("Flat", "Loud", "loud (2)", "Loud (3)"), names)
        assertEquals(6f, repo.profiles.first().first { it.name == "Loud" }.state.gainsDb[0])
    }

    @Test
    fun `a broken profile file is rejected`() = runBlocking {
        assertTrue(repo.importProfile("nope").isFailure)
        assertTrue(repo.profiles.first().isEmpty())
    }

    @Test
    fun `the text of one profile can be produced for sharing`() = runBlocking {
        populate()
        val loud = repo.profiles.first().first { it.name == "Loud" }
        assertEquals(BackupProfile("Loud", loud.state), BackupCodec.decodeProfile(repo.profileText(loud.id)!!).getOrThrow())
        assertNull(repo.profileText(9999))
    }
}
