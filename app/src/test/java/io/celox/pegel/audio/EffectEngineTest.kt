package io.celox.pegel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectEngineTest {

    private val factory = FakeEffectChainFactory()
    private val engine = EffectEngine(factory)
    private val plan = planFor(SoundState(bassOn = true), Tuning())
    private val other = planFor(SoundState(gainsDb = List(10) { 3f }), Tuning())

    private fun liveSessions() = factory.live.map { it.sessionId }.sorted()

    @Test
    fun `nothing is attached while the engine is off`() {
        engine.onSessionOpened(42, "a.b")
        assertTrue(factory.created.isEmpty())
        assertEquals(Target.None, engine.state.value.target)
    }

    @Test
    fun `auto without a player uses the global mix`() {
        engine.setEnabled(true)
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        assertEquals(Target.GlobalMix, engine.state.value.target)
        assertFalse(engine.state.value.failed)
    }

    @Test
    fun `auto follows a player that announces itself and returns to the global mix when it leaves`() {
        engine.setEnabled(true)
        val global = factory.liveOn(GLOBAL_SESSION)
        engine.onSessionOpened(42, "a.b")
        assertEquals(listOf(42), liveSessions())
        assertEquals(1, global.released)
        assertEquals(Target.Players(listOf("a.b")), engine.state.value.target)

        engine.onSessionClosed(42)
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        assertEquals(Target.GlobalMix, engine.state.value.target)
    }

    @Test
    fun `two players each get a chain`() {
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        engine.onSessionOpened(43, "c.d")
        assertEquals(listOf(42, 43), liveSessions())
        assertEquals(Target.Players(listOf("a.b", "c.d")), engine.state.value.target)
    }

    @Test
    fun `global only ignores players`() {
        engine.setMode(SessionMode.GLOBAL_ONLY)
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        assertEquals(Target.GlobalMix, engine.state.value.target)
    }

    @Test
    fun `players only never touches the global mix`() {
        engine.setMode(SessionMode.PLAYERS_ONLY)
        engine.setEnabled(true)
        assertTrue(factory.created.isEmpty())
        assertEquals(Target.None, engine.state.value.target)
        assertFalse("waiting for a player is not a failure", engine.state.value.failed)
        engine.onSessionOpened(42, "a.b")
        assertEquals(listOf(42), liveSessions())
    }

    @Test
    fun `changing the mode rebuilds the chains and remembers known players`() {
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        engine.setMode(SessionMode.GLOBAL_ONLY)
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        engine.setMode(SessionMode.PLAYERS_ONLY)
        assertEquals(listOf(42), liveSessions())
    }

    @Test
    fun `a plan reaches every live chain, and a new chain starts with the current plan`() {
        engine.setPlan(plan)
        engine.setEnabled(true)
        assertEquals(plan to true, factory.liveOn(GLOBAL_SESSION).applied.last())

        engine.onSessionOpened(42, "a.b")
        engine.onSessionOpened(43, "c.d")
        engine.setPlan(other)
        assertEquals(other to true, factory.liveOn(42).applied.last())
        assertEquals(other to true, factory.liveOn(43).applied.last())
    }

    @Test
    fun `switching off releases everything but remembers the players`() {
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        engine.setEnabled(false)
        assertTrue(factory.live.isEmpty())
        assertEquals(Target.None, engine.state.value.target)
        engine.setEnabled(true)
        assertEquals(listOf(42), liveSessions())
    }

    @Test
    fun `shutdown releases everything`() {
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        engine.shutdown()
        assertTrue(factory.live.isEmpty())
        assertTrue(factory.created.all { it.released == 1 })
    }

    @Test
    fun `the same session announced twice gets one chain`() {
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        engine.onSessionOpened(42, "a.b")
        assertEquals(1, factory.created.count { it.sessionId == 42 })
    }

    @Test
    fun `session zero and negative ids are not players`() {
        engine.setEnabled(true)
        engine.onSessionOpened(0, "a.b")
        engine.onSessionOpened(-7, "a.b")
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        assertEquals(Target.GlobalMix, engine.state.value.target)
    }

    @Test
    fun `closing an unknown session is harmless`() {
        engine.setEnabled(true)
        engine.onSessionClosed(99)
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
    }

    // Review focus 1
    @Test
    fun `a device that refuses the global mix is reported, not fatal`() {
        factory.failing += GLOBAL_SESSION
        engine.setEnabled(true)
        assertTrue(engine.state.value.failed)
        assertEquals(Target.None, engine.state.value.target)
        assertNull(engine.state.value.status)
    }

    @Test
    fun `a failure clears once a chain can be created`() {
        factory.failing += GLOBAL_SESSION
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        assertFalse(engine.state.value.failed)
        assertEquals(Target.Players(listOf("a.b")), engine.state.value.target)
    }

    @Test
    fun `a player whose session cannot be equalized falls back to the global mix`() {
        factory.failing += 42
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        assertEquals(Target.GlobalMix, engine.state.value.target)
    }

    @Test
    fun `a partly available chain shows which effect is missing`() {
        factory.status = ChainStatus(dynamics = false, virtualizer = true, reverb = false)
        engine.setEnabled(true)
        assertEquals(ChainStatus(dynamics = false, virtualizer = true, reverb = false), engine.state.value.status)
        assertFalse(engine.state.value.failed)
    }

    // Review focus 3
    @Test
    fun `a player that vanishes without saying goodbye is dropped`() {
        engine.setEnabled(true)
        engine.onSessionOpened(42, "a.b")
        val chain = factory.liveOn(42)
        chain.onControlLost!!.invoke()
        assertEquals(1, chain.released)
        assertEquals(listOf(GLOBAL_SESSION), liveSessions())
        assertEquals(Target.GlobalMix, engine.state.value.target)
        // and it stays forgotten
        engine.setMode(SessionMode.PLAYERS_ONLY)
        assertTrue(factory.live.isEmpty())
    }

    @Test
    fun `losing the global mix to another equalizer is reported`() {
        engine.setEnabled(true)
        factory.failing += GLOBAL_SESSION
        factory.liveOn(GLOBAL_SESSION).onControlLost!!.invoke()
        assertTrue(engine.state.value.failed)
        assertEquals(Target.None, engine.state.value.target)
    }
}
