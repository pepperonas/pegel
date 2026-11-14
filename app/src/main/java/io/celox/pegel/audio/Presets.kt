package io.celox.pegel.audio

/** Octave centres of the ten bands, 31 Hz to 16 kHz. */
val BAND_CENTERS_HZ: List<Float> = List(BAND_COUNT) { 31.25f * (1 shl it) }

/**
 * A named set of band gains. Built-in presets carry their id as [name]; the UI resolves it to a
 * translated label (string resource `preset_<id>`). Custom ones have the id `custom:<rowId>`.
 */
data class Preset(val id: String, val name: String, val gainsDb: List<Float>, val builtIn: Boolean)

private fun builtIn(id: String, vararg db: Number) = Preset(id, id, db.map { it.toFloat() }, builtIn = true)

/** Pegel's own tunings, 31 Hz → 16 kHz in dB. */
val BUILT_IN_PRESETS: List<Preset> = listOf(
    builtIn("flat", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    builtIn("bass", 6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
    builtIn("treble", 0, 0, 0, 0, 0, 0, 2, 4, 5, 6),
    builtIn("vocal", -2, -2, -1, 1, 3, 4, 4, 3, 1, 0),
    builtIn("rock", 5, 4, 2, 0, -1, -1, 1, 3, 4, 5),
    builtIn("pop", -1, 0, 2, 3, 4, 3, 1, 0, -1, -1),
    builtIn("jazz", 3, 2, 1, 2, -1, -1, 0, 1, 2, 3),
    builtIn("classical", 4, 3, 2, 1, 0, 0, 0, 1, 2, 3),
    builtIn("electronic", 5, 4, 2, 0, -2, 1, 0, 2, 4, 5),
    builtIn("hiphop", 6, 5, 3, 1, -1, -1, 1, 0, 2, 3),
    builtIn("acoustic", 3, 3, 2, 1, 1, 1, 2, 3, 2, 1),
    builtIn("vshape", 6, 4, 2, 0, -2, -2, 0, 2, 4, 6),
    builtIn("speech", -4, -3, -1, 1, 3, 4, 4, 2, 0, -2),
)
