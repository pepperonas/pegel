package io.celox.pegel.audio

/** Octave centres of the ten bands, 31 Hz to 16 kHz. */
val BAND_CENTERS_HZ: List<Float> = List(BAND_COUNT) { 31.25f * (1 shl it) }

/**
 * A named set of band gains. Built-in presets carry their id as [name]; the UI resolves it to a
 * translated label (string resource `preset_<id>`). Custom ones have the id `custom:<rowId>`.
 */
data class Preset(val id: String, val name: String, val gainsDb: List<Float>, val builtIn: Boolean)

private fun builtIn(id: String, vararg db: Number) = Preset(id, id, db.map { it.toFloat() }, builtIn = true)

/** How the picker sorts presets: what they do to the tone, what music, what else you listen to, your own. */
enum class PresetGroup { TONE, MUSIC, MEDIA, OWN }

private val TONE = setOf("flat", "bass", "treble", "vshape")
private val MEDIA = setOf("speech", "movie", "gaming", "night")

fun presetGroup(p: Preset): PresetGroup = when {
    !p.builtIn -> PresetGroup.OWN
    p.id in TONE -> PresetGroup.TONE
    p.id in MEDIA -> PresetGroup.MEDIA
    else -> PresetGroup.MUSIC
}

/** Pegel's own tunings, 31 Hz → 16 kHz in dB, in the order of [PresetGroup]. */
val BUILT_IN_PRESETS: List<Preset> = listOf(
    // tone
    builtIn("flat", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    builtIn("bass", 6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
    builtIn("treble", 0, 0, 0, 0, 0, 0, 2, 4, 5, 6),
    builtIn("vshape", 6, 4, 2, 0, -2, -2, 0, 2, 4, 6),
    // music
    builtIn("pop", -1, 0, 2, 3, 4, 3, 1, 0, -1, -1),
    builtIn("rock", 5, 4, 2, 0, -1, -1, 1, 3, 4, 5),
    builtIn("metal", 4, 3, 0, -2, -3, 0, 2, 4, 4, 3),
    builtIn("hiphop", 6, 5, 3, 1, -1, -1, 1, 0, 2, 3),
    builtIn("rnb", 5, 5, 3, 1, -1, -1, 1, 2, 2, 2),
    builtIn("electronic", 5, 4, 2, 0, -2, 1, 0, 2, 4, 5),
    // Techno: the kick and the sub carry it, the mids step back, hi-hats stay crisp but not sharp.
    builtIn("techno", 6, 5, 3, 0, -2, -2, 0, 1, 3, 3),
    builtIn("house", 5, 4, 3, 1, 0, -1, 0, 2, 3, 3),
    builtIn("dnb", 6, 4, 1, -1, -1, 0, 1, 2, 4, 4),
    builtIn("jazz", 3, 2, 1, 2, -1, -1, 0, 1, 2, 3),
    builtIn("lounge", 2, 2, 1, 0, 0, 0, 1, 1, 2, 2),
    builtIn("classical", 4, 3, 2, 1, 0, 0, 0, 1, 2, 3),
    builtIn("acoustic", 3, 3, 2, 1, 1, 1, 2, 3, 2, 1),
    builtIn("vocal", -2, -2, -1, 1, 3, 4, 4, 3, 1, 0),
    // media
    builtIn("speech", -4, -3, -1, 1, 3, 4, 4, 2, 0, -2),
    // Film: weight for effects, dialogue lifted out of the mix.
    builtIn("movie", 4, 3, 1, 0, 0, 1, 2, 2, 2, 1),
    // Games: footsteps and cues live between 2 and 8 kHz.
    builtIn("gaming", 3, 2, 0, -1, 0, 1, 3, 4, 4, 2),
    // Late at night: less rumble through the wall, voices stay clear at low volume.
    builtIn("night", -5, -3, -1, 0, 1, 2, 2, 1, 0, -1),
)
