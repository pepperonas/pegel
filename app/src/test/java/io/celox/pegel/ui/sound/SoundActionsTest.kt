package io.celox.pegel.ui.sound

import io.celox.pegel.audio.BUILT_IN_PRESETS
import io.celox.pegel.data.RepoFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundActionsTest {

    private val fx = RepoFixture()
    private val repo = fx.repository()
    private val actions = SoundActions(repo)

    @After fun tearDown() = fx.close()

    @Test
    fun `choosing a built-in preset sets its gains`() = runBlocking {
        actions.selectPreset("rock")
        assertEquals(BUILT_IN_PRESETS.first { it.id == "rock" }.gainsDb, repo.state.value.gainsDb)
        assertEquals("rock", repo.state.value.presetId)
    }

    @Test
    fun `moving a band snaps to half decibels and leaves the preset`() = runBlocking {
        actions.selectPreset("rock")
        actions.setGain(4, 3.3f)
        assertEquals(3.5f, repo.state.value.gainsDb[4])
        assertNull(repo.state.value.presetId)
    }

    @Test
    fun `saving the sliders as a preset selects the new preset`() = runBlocking {
        actions.setGain(0, 7f)
        assertTrue(actions.saveAsPreset("Mine").isSuccess)
        val saved = repo.customPresets.first().single()
        assertEquals(7f, saved.gainsDb[0])
        assertEquals(saved.id, repo.state.value.presetId)
    }

    @Test
    fun `a custom preset can be chosen and deleted`() = runBlocking {
        actions.setGain(0, 7f)
        actions.saveAsPreset("Mine")
        val id = repo.customPresets.first().single().id
        actions.selectPreset("flat")
        actions.selectPreset(id)
        assertEquals(7f, repo.state.value.gainsDb[0])

        actions.deletePreset(id)
        assertTrue(repo.customPresets.first().isEmpty())
        // the sound stays, it just no longer belongs to a preset
        assertEquals(7f, repo.state.value.gainsDb[0])
        assertNull(repo.state.value.presetId)
    }

    @Test
    fun `an unknown preset id changes nothing`() = runBlocking {
        actions.setGain(0, 7f)
        actions.selectPreset("custom:999")
        actions.selectPreset("nonsense")
        assertEquals(7f, repo.state.value.gainsDb[0])
    }

    @Test
    fun `reset returns the equalizer to flat without touching the other effects`() = runBlocking {
        repo.update { it.copy(bassOn = true).withGain(0, 7f) }
        actions.resetEqualizer()
        assertEquals(List(10) { 0f }, repo.state.value.gainsDb)
        assertEquals("flat", repo.state.value.presetId)
        assertTrue(repo.state.value.bassOn)
    }
}
