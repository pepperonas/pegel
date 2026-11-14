package io.celox.pegel.device

import io.celox.pegel.data.RepoFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoApplyTest {

    private val fx = RepoFixture()
    private val repo = fx.repository()
    private val auto = AutoApply(repo)
    private val buds = OutputDevice(DeviceKind.BLUETOOTH, "Buds")
    private val speaker = OutputDevice(DeviceKind.SPEAKER, "Phone")

    @After fun tearDown() = fx.close()

    private suspend fun profile(name: String, gain: Float): Long {
        repo.update { it.withGain(0, gain) }
        return repo.saveAsProfile(name).getOrThrow()
    }

    @Test
    fun `a device with a bound profile gets that profile`() = runBlocking {
        val loud = profile("Loud", 6f)
        profile("Quiet", -6f)
        repo.bindDevice(buds.key, loud)

        assertEquals("Loud", auto.onActiveDevice(buds)!!.name)
        assertEquals(6f, repo.state.value.gainsDb[0])
        assertEquals("Loud", repo.activeProfile.value!!.name)
    }

    @Test
    fun `a device without a binding changes nothing but becomes known`() = runBlocking {
        profile("Quiet", -6f)
        assertNull(auto.onActiveDevice(buds))
        assertEquals(-6f, repo.state.value.gainsDb[0])
        val row = repo.devices.first().single()
        assertEquals("BLUETOOTH:Buds", row.key)
        assertEquals("Buds", row.label)
    }

    @Test
    fun `the same device reported twice does not overwrite what was changed by hand since`() = runBlocking {
        val loud = profile("Loud", 6f)
        repo.bindDevice(buds.key, loud)
        auto.onActiveDevice(buds)
        repo.update { it.withGain(0, 2f) }
        assertNull(auto.onActiveDevice(buds))
        assertEquals(2f, repo.state.value.gainsDb[0])
    }

    @Test
    fun `coming back to a device applies its profile again`() = runBlocking {
        val loud = profile("Loud", 6f)
        val quiet = profile("Quiet", -6f)
        repo.bindDevice(buds.key, loud)
        repo.bindDevice(speaker.key, quiet)
        auto.onActiveDevice(buds)
        assertEquals("Quiet", auto.onActiveDevice(speaker)!!.name)
        assertEquals("Loud", auto.onActiveDevice(buds)!!.name)
        assertEquals(6f, repo.state.value.gainsDb[0])
    }

    @Test
    fun `leaving for a device without a profile keeps the last sound`() = runBlocking {
        val loud = profile("Loud", 6f)
        repo.bindDevice(buds.key, loud)
        auto.onActiveDevice(buds)
        assertNull(auto.onActiveDevice(speaker))
        assertEquals("Loud", repo.activeProfile.value!!.name)
    }

    @Test
    fun `no device at all is not an event`() = runBlocking {
        assertNull(auto.onActiveDevice(null))
        assertEquals(emptyList<Any>(), repo.devices.first())
    }

    // Reviewer finding: the "same device" memory lived in an object the service re-created, so
    // switching Pegel off and on (or a reboot) reloaded the bound profile over unsaved changes.
    @Test
    fun `a restart on the same device does not undo unsaved changes`() = runBlocking {
        val loud = profile("Loud", 6f)
        repo.bindDevice(buds.key, loud)
        auto.onActiveDevice(buds)
        repo.update { it.withGain(0, 2f) }

        val afterRestart = fx.repository()
        assertNull(AutoApply(afterRestart).onActiveDevice(buds))
        assertEquals(2f, afterRestart.state.value.gainsDb[0])
        // a different device still gets its profile
        afterRestart.bindDevice(speaker.key, loud)
        assertEquals("Loud", AutoApply(afterRestart).onActiveDevice(speaker)!!.name)
    }
}
