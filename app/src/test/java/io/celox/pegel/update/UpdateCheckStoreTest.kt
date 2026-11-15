package io.celox.pegel.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateCheckStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the check is on until someone switches it off, and off stays off`() {
        assertTrue(UpdateCheckStore.isEnabled(context))
        UpdateCheckStore.setEnabled(context, false)
        assertFalse(UpdateCheckStore.isEnabled(context))
    }

    @Test
    fun `switched off, a check asks nobody`() {
        UpdateCheckStore.setEnabled(context, false)
        var asked = 0
        assertEquals(UpdatePolicy.Outcome.DISABLED, UpdateChecker.check(context) { asked++; "v9.9.9" })
        assertEquals(0, asked)
        assertNull(UpdateChecker.bannerVersion(context))
    }

    @Test
    fun `an answered check is remembered with its time, a failed one is not`() {
        UpdateChecker.check(context) { null }
        assertEquals(0L, UpdateCheckStore.lastCheckAt(context))
        UpdateChecker.check(context) { "v9.9.9" }
        assertTrue(UpdateCheckStore.lastCheckAt(context) > 0)
        assertEquals("9.9.9", UpdateChecker.bannerVersion(context))
    }

    @Test
    fun `forgetting the findings forgets the time too`() {
        UpdateChecker.check(context) { "v9.9.9" }
        UpdateCheckStore.clearFindings(context)
        assertEquals(0L, UpdateCheckStore.lastCheckAt(context))
        assertNull(UpdateCheckStore.latestSeen(context))
    }

    @Test
    fun `the self-update flag is read once`() {
        assertFalse(SelfUpdateMarker.consume(context))
        SelfUpdateMarker.set(context)
        assertTrue(SelfUpdateMarker.consume(context))
        assertFalse(SelfUpdateMarker.consume(context))
    }
}
