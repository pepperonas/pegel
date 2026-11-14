package io.celox.pegel.data

import io.celox.pegel.audio.SessionMode
import io.celox.pegel.audio.SoundState
import io.celox.pegel.audio.Tuning
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
class SoundRepositoryTest {

    private val fx = RepoFixture()
    private val repo = fx.repository()

    @After fun tearDown() = fx.close()

    private fun nameError(r: Result<*>) = (r.exceptionOrNull() as? NameException)?.error

    @Test
    fun `a fresh install starts switched off with a flat state`() {
        assertEquals(SoundState(), repo.state.value)
        assertEquals(Settings(), repo.settings.value)
        assertFalse(repo.settings.value.enabled)
        assertNull(repo.activeProfile.value)
        assertFalse(repo.modified.value)
    }

    @Test
    fun `the working state survives a restart`() = runBlocking {
        repo.update { it.withGain(2, 5f).copy(bassOn = true) }
        repo.updateSettings { it.copy(enabled = true, mode = SessionMode.PLAYERS_ONLY) }
        val again = fx.repository()
        assertEquals(5f, again.state.value.gainsDb[2])
        assertTrue(again.state.value.bassOn)
        assertTrue(again.settings.value.enabled)
        assertEquals(SessionMode.PLAYERS_ONLY, again.settings.value.mode)
    }

    @Test
    fun `whatever is written is normalized first`() = runBlocking {
        repo.update { it.copy(gainsDb = List(10) { 99f }, balance = 4f) }
        assertEquals(12f, repo.state.value.gainsDb[0])
        assertEquals(1f, repo.state.value.balance)
        repo.updateSettings { it.copy(frameDurationMs = 5, tuning = Tuning(bassCutoffHz = 9999)) }
        assertEquals(10, repo.settings.value.frameDurationMs)
        assertEquals(150, repo.settings.value.tuning.bassCutoffHz)
        repo.updateSettings { it.copy(frameDurationMs = 5000) }
        assertEquals(1000, repo.settings.value.frameDurationMs)
    }

    @Test
    fun `saving as a profile makes it the active, unmodified one`() = runBlocking {
        repo.update { it.withGain(0, 3f) }
        val id = repo.saveAsProfile("Auto").getOrThrow()
        assertEquals("Auto", repo.activeProfile.value!!.name)
        assertEquals(id, repo.activeProfile.value!!.id)
        assertFalse(repo.modified.value)
        assertEquals(listOf("Auto"), repo.profiles.first().map { it.name })
    }

    @Test
    fun `a change marks the profile modified, discard restores it, save keeps it`() = runBlocking {
        repo.saveAsProfile("Auto").getOrThrow()
        repo.update { it.withGain(0, 6f) }
        assertTrue(repo.modified.value)

        repo.discardChanges()
        assertFalse(repo.modified.value)
        assertEquals(0f, repo.state.value.gainsDb[0])

        repo.update { it.withGain(0, 6f) }
        repo.saveActiveProfile()
        assertFalse(repo.modified.value)
        assertEquals(6f, repo.profiles.first().single().state.gainsDb[0])
    }

    @Test
    fun `loading a profile replaces the working state`() = runBlocking {
        repo.update { it.withGain(0, 6f) }
        val loud = repo.saveAsProfile("Loud").getOrThrow()
        repo.update { SoundState() }
        repo.saveAsProfile("Quiet").getOrThrow()
        repo.loadProfile(loud)
        assertEquals(6f, repo.state.value.gainsDb[0])
        assertEquals("Loud", repo.activeProfile.value!!.name)
        assertFalse(repo.modified.value)
    }

    @Test
    fun `the active profile survives a restart, including the modified mark`() = runBlocking {
        repo.saveAsProfile("Auto").getOrThrow()
        repo.update { it.withGain(0, 6f) }
        val again = fx.repository()
        assertEquals("Auto", again.activeProfile.value!!.name)
        assertTrue(again.modified.value)
    }

    @Test
    fun `names are trimmed, capped at forty characters and must not be empty`() = runBlocking {
        assertEquals(NameError.EMPTY, nameError(repo.saveAsProfile("")))
        assertEquals(NameError.EMPTY, nameError(repo.saveAsProfile("   ")))
        repo.saveAsProfile("  Auto  ").getOrThrow()
        repo.saveAsProfile("x".repeat(60)).getOrThrow()
        assertEquals(setOf("Auto", "x".repeat(40)), repo.profiles.first().map { it.name }.toSet())
    }

    @Test
    fun `a name that only differs in case or spacing is a duplicate`() = runBlocking {
        val id = repo.saveAsProfile("Auto").getOrThrow()
        assertEquals(NameError.DUPLICATE, nameError(repo.saveAsProfile("auto")))
        assertEquals(NameError.DUPLICATE, nameError(repo.saveAsProfile(" AUTO ")))
        val other = repo.saveAsProfile("Other").getOrThrow()
        assertEquals(NameError.DUPLICATE, nameError(repo.renameProfile(other, "auto")))
        // renaming a profile to its own name in another case is fine
        assertTrue(repo.renameProfile(id, "AUTO").isSuccess)
        assertEquals(1, repo.profiles.first().count { it.name == "AUTO" })
    }

    @Test
    fun `renaming the active profile shows at once`() = runBlocking {
        val id = repo.saveAsProfile("Auto").getOrThrow()
        repo.renameProfile(id, "Car").getOrThrow()
        assertEquals("Car", repo.activeProfile.value!!.name)
    }

    @Test
    fun `deleting the active profile keeps the sound and leaves no active profile`() = runBlocking {
        repo.update { it.withGain(0, 6f) }
        val id = repo.saveAsProfile("Auto").getOrThrow()
        repo.deleteProfile(id)
        assertNull(repo.activeProfile.value)
        assertFalse(repo.modified.value)
        assertEquals(6f, repo.state.value.gainsDb[0])
        assertTrue(repo.profiles.first().isEmpty())
        assertNull(fx.repository().activeProfile.value)
    }

    // Review focus 5
    @Test
    fun `deleting a profile unbinds its devices instead of leaving them pointing nowhere`() = runBlocking {
        val id = repo.saveAsProfile("Auto").getOrThrow()
        repo.seeDevice("BLUETOOTH:Buds", "Buds")
        repo.bindDevice("BLUETOOTH:Buds", id)
        assertEquals("Auto", repo.profileForDevice("BLUETOOTH:Buds")!!.name)

        repo.deleteProfile(id)
        assertNull(repo.profileForDevice("BLUETOOTH:Buds"))
        val row = repo.devices.first().single()
        assertEquals("BLUETOOTH:Buds", row.key)
        assertNull(row.profileId)
    }

    @Test
    fun `seeing a device again keeps one row and its binding`() = runBlocking {
        val id = repo.saveAsProfile("Auto").getOrThrow()
        repo.seeDevice("BLUETOOTH:Buds", "Buds")
        repo.bindDevice("BLUETOOTH:Buds", id)
        val first = repo.devices.first().single().lastSeen
        Thread.sleep(5)
        repo.seeDevice("BLUETOOTH:Buds", "Buds Pro")
        val row = repo.devices.first().single()
        assertEquals(id, row.profileId)
        assertEquals("Buds Pro", row.label)
        assertTrue(row.lastSeen > first)
    }

    @Test
    fun `a device can be unbound and forgotten`() = runBlocking {
        val id = repo.saveAsProfile("Auto").getOrThrow()
        repo.bindDevice("SPEAKER", id) // binding creates the row if the device was never seen
        assertEquals(id, repo.devices.first().single().profileId)
        repo.bindDevice("SPEAKER", null)
        assertNull(repo.profileForDevice("SPEAKER"))
        repo.forgetDevice("SPEAKER")
        assertTrue(repo.devices.first().isEmpty())
    }

    @Test
    fun `a damaged profile row loads as a flat state instead of crashing`() = runBlocking {
        fx.db.profiles().insert(ProfileEntity(name = "Broken", stateJson = "{{{", updatedAt = 1))
        val p = repo.profiles.first().single()
        assertEquals("Broken", p.name)
        assertEquals(SoundState(), p.state)
        repo.loadProfile(p.id)
        assertEquals(SoundState(), repo.state.value)
    }

    @Test
    fun `custom presets are stored, listed and deleted`() = runBlocking {
        val gains = listOf(1f, 2f, 3f, 4f, 5f, 4f, 3f, 2f, 1f, 0f)
        val rowId = repo.savePreset("Mine", gains).getOrThrow()
        val p = repo.customPresets.first().single()
        assertEquals("custom:$rowId", p.id)
        assertEquals("Mine", p.name)
        assertEquals(gains, p.gainsDb)
        assertFalse(p.builtIn)
        assertEquals(NameError.DUPLICATE, nameError(repo.savePreset("mine", gains)))
        assertEquals(NameError.EMPTY, nameError(repo.savePreset(" ", gains)))
        repo.deletePreset(p.id)
        assertTrue(repo.customPresets.first().isEmpty())
    }

    @Test
    fun `a burst of changes takes effect at once and the last one ends up stored`() = runBlocking {
        repeat(300) { i -> repo.update { it.copy(bassStrength = (i % 100) / 100f, bassOn = true) } }
        assertEquals(0.99f, repo.state.value.bassStrength)
        repo.flush()
        assertEquals(0.99f, fx.store.read().working.bassStrength)
    }

    @Test
    fun `a pending slider write cannot overwrite a later profile load`() = runBlocking {
        repo.update { it.withGain(0, 6f) }
        val loud = repo.saveAsProfile("Loud").getOrThrow()
        repeat(50) { i -> repo.update { it.withGain(0, -(i % 12).toFloat()) } }
        repo.loadProfile(loud) // waits for the disk; whatever the writer does afterwards must agree
        repo.flush()
        Thread.sleep(100)
        val stored = fx.store.read()
        assertEquals(6f, stored.working.gainsDb[0])
        assertEquals(loud, stored.activeProfileId)
    }
}
