package io.celox.pegel.audio

/** Audio session 0: everything the device plays, mixed. */
const val GLOBAL_SESSION = 0

/** Which parts of a chain the device actually granted. */
data class ChainStatus(val dynamics: Boolean, val virtualizer: Boolean, val reverb: Boolean)

/** The effects attached to one audio session. */
interface EffectChain {
    val sessionId: Int
    val status: ChainStatus
    fun apply(plan: ChainPlan, enabled: Boolean)
    /** Safe to call more than once. */
    fun release()
    /** Called (from any thread) when the session is gone or another app took the effects over. */
    var onControlLost: (() -> Unit)?
}

fun interface EffectChainFactory {
    /** Null when not a single effect could be created on this session. */
    fun create(sessionId: Int): EffectChain?
}
