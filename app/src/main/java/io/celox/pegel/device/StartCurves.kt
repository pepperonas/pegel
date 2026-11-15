package io.celox.pegel.device

/**
 * What an output is, as far as the sound is concerned. Android tells an app without the Bluetooth
 * permission only a device's name, so for anything that is not a known headphone the user says it once.
 */
enum class DeviceType {
    HEADPHONES, SPEAKER_SMALL, SPEAKER_LARGE, CAR, PHONE_SPEAKER;

    companion object {
        fun of(stored: String?): DeviceType? = entries.firstOrNull { it.name == stored }
    }
}

/**
 * Pegel's own starting points per kind of output, 31 Hz → 16 kHz in dB. They are not measurements
 * of any device — there is no open measurement database for speakers — and the UI says so.
 * Headphones have none: they get a measured correction (see [HeadphoneCatalog]) or nothing.
 */
fun startCurveDb(type: DeviceType): List<Float>? = when (type) {
    DeviceType.HEADPHONES -> null
    // A phone's drivers move no air below ~200 Hz; asking for bass only costs headroom and distorts.
    DeviceType.PHONE_SPEAKER -> listOf(-6f, -4f, -2f, 1f, 2f, 0f, 0f, 1f, 2f, 1f)
    // Pocket speakers: the same in milder form — body from the upper bass, not from the sub-bass.
    DeviceType.SPEAKER_SMALL -> listOf(-4f, -1f, 2f, 2f, 0f, 0f, 0f, 1f, 1f, 1f)
    // Speakers with real woofers need little: a touch of weight and air.
    DeviceType.SPEAKER_LARGE -> listOf(0f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f)
    // Cabins boom around 125 Hz and swallow treble from low-mounted drivers.
    DeviceType.CAR -> listOf(1f, 1f, -2f, -1f, 0f, 0f, 1f, 1f, 2f, 2f)
}
