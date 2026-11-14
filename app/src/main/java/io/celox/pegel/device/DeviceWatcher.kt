package io.celox.pegel.device

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Watches which output the device is playing through. */
class DeviceWatcher(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    fun current(): OutputDevice? = pickActive(
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { info ->
            kindOf(info.type)?.let { OutputDevice(it, deviceName(info.productName, Build.MODEL)) }
        }
    )

    val active: Flow<OutputDevice?> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { trySend(current()) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { trySend(current()) }
        }
        // Registering delivers the currently connected devices once, so no explicit first emission.
        audio.registerAudioDeviceCallback(callback, null)
        awaitClose { audio.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()
}
