package io.celox.pegel.device

import io.celox.pegel.audio.BAND_COUNT
import io.celox.pegel.audio.SoundState

/**
 * One headphone model with the ten-band correction AutoEq computed for it
 * (see `scripts/autoeq.py` and `docs/AUTOEQ-NOTICE.md`). [source] is who measured it.
 */
data class HeadphoneModel(
    val name: String,
    val source: String,
    val form: String,
    val preampDb: Float,
    val gainsDb: List<Float>,
) {
    /**
     * The curve as Pegel's bands take it: the preamp is folded in, so the correction never asks
     * for more level than the signal has — nothing is pushed into the limiter.
     */
    fun correctionDb(): List<Float> = gainsDb.map { SoundState.snapGain(it + preampDb) }
}

/** The shipped headphone table: recognises a device by its name and searches by hand. */
class HeadphoneCatalog(val models: List<HeadphoneModel>) {

    private class Entry(val model: HeadphoneModel, val tokens: List<String>) {
        val full = tokens.joinToString("")
        val bare = tokens.drop(1)
        val bareCompact = bare.joinToString("")
        /** Without its brand a model must still say something: "Pro" or "Aero" alone is no model. */
        val bareDistinctive = bareCompact.length >= 5 && (bare.size >= 2 || bareCompact.any(Char::isDigit))
        val haystack = model.name.lowercase()
    }

    private val entries by lazy {
        models.map { m -> Entry(m, tokens(m.name)) }
            .filter { it.tokens.isNotEmpty() }
    }

    /**
     * Models a device of this name most likely is, best first; empty when unsure. A wrong
     * correction is worse than none, so only whole names count: the full model name, the name
     * without its brand, or either of them as whole words inside a longer (renamed) device name.
     */
    fun match(deviceName: String): List<HeadphoneModel> {
        val words = tokens(deviceName).dropWhile { it == "le" }
        val compact = words.joinToString("")
        if (compact.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            val score = when {
                compact == e.full -> 4
                e.bareDistinctive && compact == e.bareCompact -> 3
                contains(words, e.tokens) -> 2
                e.bareDistinctive && contains(words, e.bare) -> 1
                else -> 0
            }
            if (score == 0) null else score to e.model
        }.sortedWith(compareBy({ -it.first }, { it.second.name.lowercase() })) // a plain model sorts before its "(ANC on)" variants
            .map { it.second }.take(MAX_MATCHES)
    }

    /** Every model whose name has all the words of [query], by name. */
    fun search(query: String, limit: Int = 50): List<HeadphoneModel> {
        val words = query.lowercase().split(SPACES).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.asSequence().filter { e -> words.all { it in e.haystack } }.map { it.model }.take(limit).toList()
    }

    companion object {
        const val MAX_MATCHES = 6
        private val EMPTY = HeadphoneCatalog(emptyList())

        /** Lower-case words of letters and digits; what stands in brackets is a variant or a serial. */
        private fun tokens(name: String): List<String> =
            name.replace(BRACKETS, " ").lowercase().split(NOT_WORD).filter { it.isNotEmpty() }

        // Compiled once: the table has thousands of names.
        private val BRACKETS = Regex("\\([^)]*\\)")
        private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val SPACES = Regex("\\s+")

        private fun contains(words: List<String>, part: List<String>): Boolean {
            if (part.isEmpty() || part.size > words.size) return false
            return (0..words.size - part.size).any { i -> words.subList(i, i + part.size) == part }
        }

        /** One model per line: name, source, form, preamp, ten gains — tab-separated. */
        fun parse(text: String): HeadphoneCatalog {
            val models = text.lineSequence().mapNotNull { line ->
                val f = line.split('\t')
                if (f.size != 4 + BAND_COUNT || f[0].isBlank()) return@mapNotNull null
                val numbers = f.drop(3).map { it.toFloatOrNull() ?: return@mapNotNull null }
                HeadphoneModel(f[0].trim(), f[1], f[2], numbers[0], numbers.drop(1))
            }.toList()
            return if (models.isEmpty()) EMPTY else HeadphoneCatalog(models)
        }
    }
}
