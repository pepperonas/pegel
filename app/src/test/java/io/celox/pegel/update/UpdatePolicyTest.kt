package io.celox.pegel.update

import io.celox.pegel.update.UpdatePolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun `nothing happens once it is switched off`() {
        assertEquals(Outcome.DISABLED, UpdatePolicy.decide(false, "v9.9.9", "1.9.1", null))
        assertNull(UpdatePolicy.bannerVersion(false, "v9.9.9", "1.9.1"))
        // The privacy guarantee: switched off, no work is enqueued and nothing is asked at app start.
        assertFalse(UpdatePolicy.scheduleOnStart(false))
        assertFalse(UpdatePolicy.checkOnStart(false, lastCheckMs = 0, nowMs = DAY))
    }

    @Test
    fun `a newer release is announced once`() {
        assertEquals(Outcome.NOTIFY, UpdatePolicy.decide(true, "v1.10.0", "1.9.1", null))
        assertEquals(Outcome.NOTIFY, UpdatePolicy.decide(true, "v1.10.0", "1.9.1", "v1.9.5"))
        assertEquals(Outcome.ALREADY_NOTIFIED, UpdatePolicy.decide(true, "v1.10.0", "1.9.1", "v1.10.0"))
    }

    @Test
    fun `the installed or an older version is up to date`() {
        assertEquals(Outcome.UP_TO_DATE, UpdatePolicy.decide(true, "v1.9.1", "1.9.1", null))
        assertEquals(Outcome.UP_TO_DATE, UpdatePolicy.decide(true, "v1.8.0", "1.9.1", null))
    }

    @Test
    fun `no answer or garbage is a failure, not an update`() {
        assertEquals(Outcome.FAILED, UpdatePolicy.decide(true, null, "1.9.1", null))
        assertEquals(Outcome.FAILED, UpdatePolicy.decide(true, "Not Found", "1.9.1", null))
    }

    @Test
    fun `the banner offers only a newer version`() {
        assertEquals("1.10.0", UpdatePolicy.bannerVersion(true, "v1.10.0", "1.9.1"))
        assertNull(UpdatePolicy.bannerVersion(true, "v1.9.1", "1.9.1"))
        assertNull(UpdatePolicy.bannerVersion(true, null, "1.9.1"))
    }

    @Test
    fun `app start asks at most every twelve hours`() {
        assertTrue("never checked", UpdatePolicy.checkOnStart(true, lastCheckMs = 0, nowMs = DAY))
        assertFalse("an hour ago", UpdatePolicy.checkOnStart(true, lastCheckMs = DAY, nowMs = DAY + HOUR))
        assertTrue("twelve hours ago", UpdatePolicy.checkOnStart(true, lastCheckMs = DAY, nowMs = DAY + 12 * HOUR))
        // A clock set back must not silence the check for good.
        assertTrue(UpdatePolicy.checkOnStart(true, lastCheckMs = DAY, nowMs = DAY - HOUR))
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
