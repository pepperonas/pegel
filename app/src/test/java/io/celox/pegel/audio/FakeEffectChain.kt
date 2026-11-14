package io.celox.pegel.audio

/** Records what the engine does to it. */
class FakeEffectChain(override val sessionId: Int, override val status: ChainStatus) : EffectChain {
    val applied = mutableListOf<Pair<ChainPlan, Boolean>>()
    var released = 0
    override var onControlLost: (() -> Unit)? = null
    override fun apply(plan: ChainPlan, enabled: Boolean) { applied += plan to enabled }
    override fun release() { released++ }
}

class FakeEffectChainFactory : EffectChainFactory {
    val created = mutableListOf<FakeEffectChain>()
    /** Sessions on which nothing can be created at all. */
    val failing = mutableSetOf<Int>()
    var status = ChainStatus(dynamics = true, virtualizer = true, reverb = true)

    override fun create(sessionId: Int): EffectChain? {
        if (sessionId in failing) return null
        return FakeEffectChain(sessionId, status).also { created += it }
    }

    /** Chains that were created and not released since. */
    val live get() = created.filter { it.released == 0 }
    fun liveOn(sessionId: Int) = live.single { it.sessionId == sessionId }
}
