package io.github.hiraking.nodoff.core

import io.github.hiraking.nodoff.core.RestorePolicy.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePolicyTest {
    @Test fun nothingPendingNeverRestores() {
        for (trigger in Trigger.entries) {
            assertFalse("trigger $trigger", RestorePolicy.shouldRestore(null, trigger))
        }
    }

    @Test fun stillPlayingDoesNotRestore() = assertFalse(RestorePolicy.shouldRestore(8, Trigger.STILL_PLAYING_AFTER_PAUSE))
    @Test fun restoresOnPauseConfirmed() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.PAUSE_CONFIRMED))
    @Test fun restoresWhenNoMediaPlaying() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.NO_MEDIA_PLAYING))
    @Test fun restoresOnUnlock() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.SCREEN_UNLOCKED))
    @Test fun restoresWhenAppUsed() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.APP_USED))
    @Test fun pendingZeroStillCountsAsPending() = assertTrue(RestorePolicy.shouldRestore(0, Trigger.SCREEN_UNLOCKED))
}
