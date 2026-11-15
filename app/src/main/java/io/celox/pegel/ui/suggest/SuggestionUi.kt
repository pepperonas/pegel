package io.celox.pegel.ui.suggest

import io.celox.pegel.data.DeviceEntity
import io.celox.pegel.device.DeviceKind
import io.celox.pegel.device.DeviceType
import io.celox.pegel.device.HeadphoneCatalog
import io.celox.pegel.device.HeadphoneModel
import io.celox.pegel.device.OutputDevice

/** What Pegel offers for the output that is active right now. */
sealed interface Suggestion {
    data object None : Suggestion
    /** A measured correction for a headphone the table knows. */
    data class Headphones(val device: OutputDevice, val model: HeadphoneModel, val alternatives: List<HeadphoneModel>) : Suggestion
    /** Pegel's own starting point for this kind of output. */
    data class StartCurve(val device: OutputDevice, val type: DeviceType) : Suggestion
    /** Nothing is known about the device: ask what it is. */
    data class AskType(val device: OutputDevice) : Suggestion
    /** Headphones the table did not recognise by name: let the user search the model. */
    data class PickModel(val device: OutputDevice) : Suggestion
}

/**
 * [askAgain]: the user asked for a suggestion — an earlier "not now" and a bound profile no longer
 * stand in the way.
 */
fun suggestionFor(
    connected: OutputDevice?,
    stored: DeviceEntity?,
    /** Null while the table is still being read: then only what does not depend on it is offered. */
    catalog: HeadphoneCatalog?,
    askAgain: Boolean = false,
): Suggestion {
    connected ?: return Suggestion.None
    if (!askAgain && (stored?.profileId != null || stored?.suggestion != null)) return Suggestion.None
    if (connected.kind == DeviceKind.SPEAKER) return Suggestion.StartCurve(connected, DeviceType.PHONE_SPEAKER)
    val type = DeviceType.of(stored?.type)
    if (type != null && type != DeviceType.HEADPHONES) return Suggestion.StartCurve(connected, type)
    catalog ?: return Suggestion.None
    val matches = catalog.match(connected.name)
    return when {
        matches.isNotEmpty() -> Suggestion.Headphones(connected, matches.first(), matches.drop(1))
        type == DeviceType.HEADPHONES -> Suggestion.PickModel(connected)
        else -> Suggestion.AskType(connected)
    }
}
