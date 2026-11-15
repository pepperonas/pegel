package io.celox.pegel.update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateInstallerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val content = "the new apk".toByteArray()
    private val goodSha get() = File(tmp.root, "probe").apply { writeBytes(content) }.let(UpdateInstaller::sha256)

    private class FakeGateway(var allowed: Boolean = true, var throws: Boolean = false) : InstallGateway {
        val installed = mutableListOf<File>()
        override fun canInstallPackages() = allowed
        override fun install(apk: File) { if (throws) throw IllegalStateException("no"); installed += apk }
    }

    private inner class FakeSource(
        var apk: ApkRelease? = null,
        var bytes: ByteArray = content,
        var fail: Boolean = false,
    ) : ApkSource {
        var downloads = 0
        override fun latestApk() = apk
        override fun download(apk: ApkRelease, target: File, onProgress: (Long, Long) -> Unit) {
            downloads++
            if (fail) { target.writeBytes(bytes.copyOf(3)); throw IOException("connection lost") }
            target.writeBytes(bytes)
            onProgress(bytes.size.toLong(), bytes.size.toLong())
        }
    }

    private fun release(version: String = "v1.1.0", sha: String = goodSha) =
        ApkRelease(version, "pegel-$version.apk", "https://pegel.celox.io/files/pegel-$version.apk", content.size.toLong(), sha)

    private fun installer(source: ApkSource, gateway: InstallGateway): UpdateInstaller {
        val dispatcher = UnconfinedTestDispatcher()
        return UpdateInstaller(source, gateway, File(tmp.root, "update"), TestScope(dispatcher), dispatcher)
    }

    @Test
    fun `a verified file is handed to Android`() {
        val gateway = FakeGateway()
        val i = installer(FakeSource(release()), gateway)
        i.start("1.0.0")
        assertEquals(InstallState.Installing, i.state.value)
        assertEquals(listOf("pegel-v1.1.0.apk"), gateway.installed.map { it.name })
        assertTrue(gateway.installed.single().readBytes().contentEquals(content))
    }

    @Test
    fun `a file that does not match its checksum is deleted, never installed`() {
        val gateway = FakeGateway()
        val i = installer(FakeSource(release(sha = "0".repeat(64))), gateway)
        i.start("1.0.0")
        assertEquals(InstallState.Failed(UpdateFailure.CHECKSUM), i.state.value)
        assertTrue(gateway.installed.isEmpty())
        assertFalse(File(tmp.root, "update/pegel-v1.1.0.apk").exists())
    }

    @Test
    fun `nothing newer means nothing is downloaded`() {
        val source = FakeSource(release("v1.0.0"))
        val i = installer(source, FakeGateway())
        i.start("1.0.0")
        assertEquals(InstallState.Failed(UpdateFailure.NO_RELEASE), i.state.value)
        assertEquals(0, source.downloads)

        val none = installer(FakeSource(null), FakeGateway())
        none.start("1.0.0")
        assertEquals(InstallState.Failed(UpdateFailure.NO_RELEASE), none.state.value)
    }

    @Test
    fun `without the install permission it waits, and goes on once it is granted`() {
        val gateway = FakeGateway(allowed = false)
        val i = installer(FakeSource(release()), gateway)
        i.start("1.0.0")
        assertEquals(InstallState.NeedsPermission, i.state.value)
        assertTrue(gateway.installed.isEmpty())

        i.onPermissionMaybeGranted()                       // came back without granting
        assertEquals(InstallState.NeedsPermission, i.state.value)

        gateway.allowed = true
        i.onPermissionMaybeGranted()
        assertEquals(InstallState.Installing, i.state.value)
        assertEquals(1, gateway.installed.size)
    }

    @Test
    fun `a broken download is a network failure and keeps the partial file`() {
        val source = FakeSource(release(), fail = true)
        val i = installer(source, FakeGateway())
        i.start("1.0.0")
        assertEquals(InstallState.Failed(UpdateFailure.NETWORK), i.state.value)
        assertTrue(File(tmp.root, "update/pegel-v1.1.0.apk").exists())

        source.fail = false                                 // the next attempt works
        i.start("1.0.0")
        assertEquals(InstallState.Installing, i.state.value)
    }

    @Test
    fun `an installer that refuses is a failure, a cancelled dialog is not`() {
        val refuses = installer(FakeSource(release()), FakeGateway(throws = true))
        refuses.start("1.0.0")
        assertEquals(InstallState.Failed(UpdateFailure.INSTALL), refuses.state.value)

        val i = installer(FakeSource(release()), FakeGateway())
        i.start("1.0.0")
        i.onInstallResult(InstallOutcome.CANCELLED)
        assertEquals(InstallState.Idle, i.state.value)
        i.start("1.0.0")
        i.onInstallResult(InstallOutcome.FAILED)
        assertEquals(InstallState.Failed(UpdateFailure.INSTALL), i.state.value)
        i.dismissFailure()
        assertEquals(InstallState.Idle, i.state.value)
    }

    @Test
    fun `a second tap while it is running starts nothing`() {
        val source = FakeSource(release())
        val i = installer(source, FakeGateway())
        i.start("1.0.0")
        i.start("1.0.0")
        assertEquals(1, source.downloads)
    }

    @Test
    fun `the cache holds one file - older downloads are removed`() {
        val dir = File(tmp.root, "update").apply { mkdirs() }
        File(dir, "pegel-v1.0.5.apk").writeText("old")
        installer(FakeSource(release()), FakeGateway()).start("1.0.0")
        assertEquals(listOf("pegel-v1.1.0.apk"), dir.list()!!.toList())
    }

    @Test
    fun `resume - what is already there is not fetched again`() {
        assertNull(ResumePlan.of(existing = 0, size = 100).rangeFrom)
        assertEquals(40L, ResumePlan.of(existing = 40, size = 100).rangeFrom)
        assertTrue(ResumePlan.of(existing = 100, size = 100).complete)
        // Longer than announced cannot be this file: start over.
        assertTrue(ResumePlan.of(existing = 140, size = 100).restart)
        assertNull(ResumePlan.of(existing = 140, size = 100).rangeFrom)
    }
}
