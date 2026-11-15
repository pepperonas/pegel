package io.celox.pegel.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Where an in-app update stands. */
sealed interface InstallState {
    data object Idle : InstallState
    /** [progress] 0..1, or null while the size is unknown. */
    data class Downloading(val progress: Float?) : InstallState
    data object Verifying : InstallState
    /** Verified and waiting for "Install unknown apps". */
    data object NeedsPermission : InstallState
    /** Handed to Android; its confirmation dialog is the next thing the user sees. */
    data object Installing : InstallState
    data class Failed(val reason: UpdateFailure) : InstallState
}

enum class UpdateFailure { NO_RELEASE, NETWORK, CHECKSUM, INSTALL }

/** What Android's installer reported, reduced to what the app acts on. */
enum class InstallOutcome { SUCCESS, CANCELLED, FAILED }

/** Android's package installer, behind an interface so the flow is testable. */
interface InstallGateway {
    fun canInstallPackages(): Boolean
    fun install(apk: File)
}

/**
 * Updates the app from inside the app: fetch the newest release file, verify it against the
 * published SHA-256, hand it to Android's package installer.
 *
 * Why not just open the download link: browsers hold an APK back behind a "this file might be
 * harmful" question that can stay hidden when the browser was opened from another app. Here nothing
 * is left to a browser. Android still shows its own confirmation, and it refuses any file that is
 * not signed with the key of the installed app.
 *
 * The file lives in the app's cache; a broken download resumes on the next try, and a file that
 * does not verify is deleted, never installed.
 */
class UpdateInstaller(
    private val source: ApkSource,
    private val gateway: InstallGateway,
    private val dir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** Verified and waiting for the install permission. */
    private var ready: File? = null

    /** Starts the update unless one is already running. [installedVersion] is this build's versionName. */
    fun start(installedVersion: String) {
        if (busy()) return
        _state.value = InstallState.Downloading(null)
        scope.launch { run(installedVersion) }
    }

    /** After the user came back from "Install unknown apps": install if it is allowed now. */
    fun onPermissionMaybeGranted() {
        val file = ready ?: return
        if (_state.value != InstallState.NeedsPermission || !gateway.canInstallPackages()) return
        install(file)
    }

    fun onInstallResult(outcome: InstallOutcome) {
        _state.value = when (outcome) {
            // On success the process is replaced and never sees this; a cancel is not an error.
            InstallOutcome.SUCCESS, InstallOutcome.CANCELLED -> InstallState.Idle
            InstallOutcome.FAILED -> InstallState.Failed(UpdateFailure.INSTALL)
        }
    }

    fun dismissFailure() {
        if (_state.value is InstallState.Failed) _state.value = InstallState.Idle
    }

    private fun busy(): Boolean = when (_state.value) {
        is InstallState.Downloading, InstallState.Verifying, InstallState.Installing -> true
        else -> false
    }

    private suspend fun run(installedVersion: String) {
        try {
            val apk = withContext(io) { source.latestApk() }
            if (apk == null || !AppVersion.isNewer(apk.version, installedVersion)) return fail(UpdateFailure.NO_RELEASE)
            val file = withContext(io) {
                prepare(apk).also { target ->
                    source.download(apk, target) { have, total ->
                        _state.value = InstallState.Downloading(if (total > 0) have.toFloat() / total else null)
                    }
                }
            }
            _state.value = InstallState.Verifying
            if (withContext(io) { sha256(file) } != apk.sha256) {
                withContext(io) { file.delete() }
                return fail(UpdateFailure.CHECKSUM)
            }
            ready = file
            if (gateway.canInstallPackages()) install(file) else _state.value = InstallState.NeedsPermission
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Network, storage: the partial file stays, the next attempt resumes from it.
            fail(UpdateFailure.NETWORK)
        }
    }

    private fun install(file: File) {
        _state.value = InstallState.Installing
        runCatching { gateway.install(file) }.onFailure { fail(UpdateFailure.INSTALL) }
    }

    /** The target file for [apk]; files of other versions are removed so the cache holds one APK. */
    private fun prepare(apk: ApkRelease): File {
        dir.mkdirs()
        dir.listFiles()?.filter { it.name != apk.name }?.forEach { it.delete() }
        return File(dir, apk.name)
    }

    private fun fail(reason: UpdateFailure) {
        _state.value = InstallState.Failed(reason)
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
