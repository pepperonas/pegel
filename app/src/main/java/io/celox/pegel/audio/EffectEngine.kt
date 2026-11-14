package io.celox.pegel.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where Pegel attaches its effects. */
enum class SessionMode {
    /** Players that announce their session; the global mix while none does. */
    AUTO,
    GLOBAL_ONLY,
    PLAYERS_ONLY,
}

sealed interface Target {
    data object None : Target
    data object GlobalMix : Target
    data class Players(val packages: List<String>) : Target
}

/**
 * [failed]: Pegel is on and should be attached somewhere, but the device granted nothing.
 * [status]: what the live chains can do (an effect counts only if every live chain has it).
 */
data class EngineState(val target: Target = Target.None, val status: ChainStatus? = null, val failed: Boolean = false)

/**
 * Decides which audio sessions carry an effect chain and keeps them in step with the current plan.
 * Pure bookkeeping — everything that touches the platform sits behind [EffectChainFactory].
 */
class EffectEngine(private val factory: EffectChainFactory) {

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private var enabled = false
    private var mode = SessionMode.AUTO
    private var plan: ChainPlan = planFor(SoundState(), Tuning())

    /** Announced player sessions, in the order they appeared: session id → package. */
    private val players = LinkedHashMap<Int, String>()
    private val chains = LinkedHashMap<Int, EffectChain>()

    /**
     * Set when another app took the global mix away. Pegel does not grab it back on its own —
     * two equalizers snatching the session from each other would never settle. Any deliberate
     * event (switch, mode, player) clears it.
     */
    private var globalLost = false

    @Synchronized fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        globalLost = false
        reconcile()
    }

    @Synchronized fun setMode(mode: SessionMode) {
        if (this.mode == mode) return
        this.mode = mode
        globalLost = false
        reconcile()
    }

    @Synchronized fun setPlan(plan: ChainPlan) {
        if (this.plan == plan) return
        this.plan = plan
        // A chain may report its session dead from inside apply(), which removes it from the map.
        chains.values.toList().forEach { it.apply(plan, true) }
    }

    @Synchronized fun onSessionOpened(sessionId: Int, packageName: String) {
        if (sessionId <= GLOBAL_SESSION) return
        if (players.put(sessionId, packageName) == packageName && chains.containsKey(sessionId)) return
        globalLost = false
        reconcile()
    }

    @Synchronized fun onSessionClosed(sessionId: Int) {
        if (players.remove(sessionId) == null) return
        globalLost = false
        reconcile()
    }

    @Synchronized fun shutdown() {
        enabled = false
        reconcile()
    }

    @Synchronized private fun onControlLost(sessionId: Int) {
        val chain = chains.remove(sessionId) ?: return
        chain.onControlLost = null
        chain.release()
        if (sessionId == GLOBAL_SESSION) globalLost = true else players.remove(sessionId)
        reconcile()
    }

    private fun wanted(): Set<Int> = when {
        !enabled -> emptySet()
        mode == SessionMode.GLOBAL_ONLY -> setOf(GLOBAL_SESSION)
        mode == SessionMode.PLAYERS_ONLY -> players.keys.toSet()
        players.isNotEmpty() -> players.keys.toSet()
        else -> setOf(GLOBAL_SESSION)
    }

    private fun reconcile() {
        // A player whose session cannot be equalized is forgotten, which can change what is wanted
        // (AUTO falls back to the global mix) — hence the loop. It ends: each round drops a player.
        while (true) {
            val wanted = wanted()
            chains.keys.filter { it !in wanted }.forEach { id ->
                chains.remove(id)?.let { it.onControlLost = null; it.release() }
            }
            var dropped = false
            for (id in wanted) {
                if (chains.containsKey(id)) continue
                if (id == GLOBAL_SESSION && globalLost) continue
                val chain = factory.create(id)
                if (chain == null) {
                    if (id != GLOBAL_SESSION) { players.remove(id); dropped = true }
                    continue
                }
                chain.onControlLost = { onControlLost(id) }
                chains[id] = chain
                chain.apply(plan, true)
            }
            if (!dropped) break
        }
        publish()
    }

    private fun publish() {
        val live = chains.values.toList()
        val target = when {
            live.isEmpty() -> Target.None
            chains.containsKey(GLOBAL_SESSION) -> Target.GlobalMix
            else -> Target.Players(chains.keys.mapNotNull { players[it] })
        }
        val status = if (live.isEmpty()) null else ChainStatus(
            dynamics = live.all { it.status.dynamics },
            virtualizer = live.all { it.status.virtualizer },
            reverb = live.all { it.status.reverb },
        )
        _state.value = EngineState(target, status, failed = live.isEmpty() && wanted().isNotEmpty())
    }
}
