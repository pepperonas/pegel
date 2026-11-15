package io.celox.pegel.ui.update

import io.celox.pegel.update.InstallState
import io.celox.pegel.update.UpdateFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCardTest {

    @Test
    fun `no newer version and nothing running shows nothing`() {
        assertNull(updateCard(newer = null, state = InstallState.Idle))
    }

    @Test
    fun `a newer version offers the update`() {
        assertEquals(UpdateCard("1.1.0", UpdateCard.Phase.OFFER, null, UpdateCard.Action.INSTALL), updateCard("1.1.0", InstallState.Idle))
    }

    @Test
    fun `progress is shown in whole percent, unknown stays unknown`() {
        assertEquals(42, updateCard("1.1.0", InstallState.Downloading(0.42f))!!.percent)
        assertNull(updateCard("1.1.0", InstallState.Downloading(null))!!.percent)
        assertEquals(UpdateCard.Action.NONE, updateCard("1.1.0", InstallState.Downloading(0.1f))!!.action)
        assertEquals(UpdateCard.Action.NONE, updateCard("1.1.0", InstallState.Verifying)!!.action)
        assertEquals(UpdateCard.Action.NONE, updateCard("1.1.0", InstallState.Installing)!!.action)
    }

    @Test
    fun `a missing permission asks for it, a failure offers another try`() {
        assertEquals(UpdateCard.Action.ALLOW, updateCard("1.1.0", InstallState.NeedsPermission)!!.action)
        val failed = updateCard("1.1.0", InstallState.Failed(UpdateFailure.CHECKSUM))!!
        assertEquals(UpdateCard.Phase.FAILED, failed.phase)
        assertEquals(UpdateCard.Action.RETRY, failed.action)
        assertEquals(UpdateFailure.CHECKSUM, failed.failure)
    }

    @Test
    fun `a failure stays visible even when the finding is gone`() {
        assertEquals(UpdateCard.Phase.FAILED, updateCard(null, InstallState.Failed(UpdateFailure.NETWORK))!!.phase)
    }
}
