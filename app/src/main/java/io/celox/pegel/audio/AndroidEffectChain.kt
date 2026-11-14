package io.celox.pegel.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log

private const val TAG = "PegelChain"
private const val PRIORITY = Int.MAX_VALUE
private const val CHANNELS = 2
private const val PRE_EQ_BANDS = 2

/**
 * The only file that touches `android.media.audiofx`. Every call into the platform can throw
 * (unsupported effect, session gone, another app holds control), so every call is guarded: a
 * failing effect is marked unavailable in [status] and the rest of the chain carries on.
 *
 * One [DynamicsProcessing] carries four things:
 *  - post-EQ, ten bands → the equalizer
 *  - pre-EQ, two bands → bass (band 0 below the cutoff gets the boost, band 1 stays neutral)
 *  - limiter post gain → loudness; the limiter itself keeps boosted audio from clipping
 *  - input gain per channel → balance
 */
class AndroidEffectChain private constructor(
    override val sessionId: Int,
    private var dynamics: DynamicsProcessing?,
    private var virtualizer: Virtualizer?,
    private var reverb: PresetReverb?,
) : EffectChain {

    override val status = ChainStatus(dynamics != null, virtualizer != null, reverb != null)

    @Volatile override var onControlLost: (() -> Unit)? = null

    private var last: ChainPlan? = null
    private var released = false

    init {
        guard("control listener") {
            dynamics?.setControlStatusListener { _, granted -> if (!granted) onControlLost?.invoke() }
        }
    }

    @Synchronized override fun apply(plan: ChainPlan, enabled: Boolean) {
        if (released) return
        val old = last
        dynamics?.let { dp ->
            val ok = guard("dynamics") {
                if (old?.bassCutoffHz != plan.bassCutoffHz || old.bassGainDb != plan.bassGainDb) {
                    dp.setPreEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(true, plan.bassCutoffHz, plan.bassGainDb))
                    dp.setPreEqBandAllChannelsTo(1, DynamicsProcessing.EqBand(true, 20000f, 0f))
                }
                for (i in 0 until BAND_COUNT) {
                    if (old?.postEqGainsDb?.get(i) != plan.postEqGainsDb[i]) {
                        dp.setPostEqBandAllChannelsTo(
                            i, DynamicsProcessing.EqBand(true, POST_EQ_CUTOFFS_HZ[i], plan.postEqGainsDb[i])
                        )
                    }
                }
                if (old?.limiterPostGainDb != plan.limiterPostGainDb) {
                    dp.setLimiterAllChannelsTo(limiter(plan.limiterPostGainDb))
                }
                if (old?.leftGainDb != plan.leftGainDb) dp.setInputGainbyChannel(0, plan.leftGainDb)
                if (old?.rightGainDb != plan.rightGainDb) dp.setInputGainbyChannel(1, plan.rightGainDb)
                if (dp.enabled != enabled) dp.enabled = enabled
            }
            // A session that died surfaces here first; tell the engine instead of failing silently.
            if (!ok) onControlLost?.invoke()
        }
        virtualizer?.let { v ->
            guard("virtualizer") {
                val on = enabled && plan.virtualizerStrength != null
                plan.virtualizerStrength?.let { if (v.strengthSupported) v.setStrength(it.toShort()) }
                if (v.enabled != on) v.enabled = on
            }
        }
        reverb?.let { r ->
            guard("reverb") {
                val on = enabled && plan.reverb != ReverbRoom.NONE
                if (on) r.preset = presetOf(plan.reverb)
                if (r.enabled != on) r.enabled = on
            }
        }
        last = plan
    }

    @Synchronized override fun release() {
        if (released) return
        released = true
        guard("release dynamics") { dynamics?.release() }
        guard("release virtualizer") { virtualizer?.release() }
        guard("release reverb") { reverb?.release() }
        dynamics = null; virtualizer = null; reverb = null
    }

    companion object {
        fun create(sessionId: Int, frameDurationMs: Float): AndroidEffectChain? {
            val dp = attempt("dynamics", sessionId) {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, CHANNELS,
                    true, PRE_EQ_BANDS,
                    false, 0,
                    true, BAND_COUNT,
                    true,
                ).setPreferredFrameDuration(frameDurationMs).build()
                DynamicsProcessing(PRIORITY, sessionId, config)
            }
            val virt = attempt("virtualizer", sessionId) { Virtualizer(PRIORITY, sessionId) }
            // On the global mix a reverb is an auxiliary effect players would have to send into —
            // it would be a switch that does nothing, so it is not offered there.
            val rev = if (sessionId == GLOBAL_SESSION) null
            else attempt("reverb", sessionId) { PresetReverb(PRIORITY, sessionId) }
            if (dp == null && virt == null && rev == null) return null
            return AndroidEffectChain(sessionId, dp, virt, rev)
        }

        private fun limiter(postGainDb: Float) = DynamicsProcessing.Limiter(
            true, true, 0, 1f, 60f, 10f, -2f, postGainDb
        )

        private fun presetOf(room: ReverbRoom): Short = when (room) {
            ReverbRoom.NONE -> PresetReverb.PRESET_NONE
            ReverbRoom.SMALL_ROOM -> PresetReverb.PRESET_SMALLROOM
            ReverbRoom.MEDIUM_ROOM -> PresetReverb.PRESET_MEDIUMROOM
            ReverbRoom.LARGE_ROOM -> PresetReverb.PRESET_LARGEROOM
            ReverbRoom.MEDIUM_HALL -> PresetReverb.PRESET_MEDIUMHALL
            ReverbRoom.LARGE_HALL -> PresetReverb.PRESET_LARGEHALL
            ReverbRoom.PLATE -> PresetReverb.PRESET_PLATE
        }

        private fun <T> attempt(what: String, sessionId: Int, block: () -> T): T? = try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "$what not available on session $sessionId: $e")
            null
        }

        /** True when [block] ran without the platform throwing. */
        private fun guard(what: String, block: () -> Unit): Boolean = try {
            block(); true
        } catch (e: RuntimeException) {
            Log.w(TAG, "$what failed: $e")
            false
        }
    }
}

class AndroidEffectChainFactory(private val frameDurationMs: () -> Float = { 10f }) : EffectChainFactory {
    override fun create(sessionId: Int): EffectChain? = AndroidEffectChain.create(sessionId, frameDurationMs())
}
