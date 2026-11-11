package io.github.hiraking.nodoff.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** 保存された状態が実態と合わないとき（再起動・プロセスの終了・届かなかったアラーム）に Idle へ戻す判定 */
class TimerSanitizeTest {

    private fun sanitize(state: TimerState, now: Long, sameBoot: Boolean = true, fadeRunning: Boolean = false) =
        TimerMachine.sanitize(state, now, sameBoot, fadeRunning)

    @Test fun idleStaysIdle() =
        assertEquals(TimerState.Idle, sanitize(TimerState.Idle, now = 0L, sameBoot = false))

    @Test fun runningBeforeEndIsKept() =
        assertEquals(TimerState.Running(100_000L), sanitize(TimerState.Running(100_000L), now = 50_000L))

    @Test fun anythingFromPreviousBootBecomesIdle() {
        assertEquals(TimerState.Idle, sanitize(TimerState.Running(9_000_000L), now = 1_000L, sameBoot = false))
        assertEquals(TimerState.Idle, sanitize(TimerState.Fading(9_000_000L), now = 1_000L, sameBoot = false, fadeRunning = true))
    }

    @Test fun runningLongPastEndBecomesIdle() =
        assertEquals(TimerState.Idle, sanitize(TimerState.Running(100_000L), now = 100_000L + Config.STALE_GRACE_MILLIS + 1))

    @Test fun runningJustPastEndIsKept() =
        assertEquals(TimerState.Running(100_000L), sanitize(TimerState.Running(100_000L), now = 100_000L + Config.STALE_GRACE_MILLIS))

    @Test fun fadingWithoutServiceLongPastEndBecomesIdle() =
        assertEquals(TimerState.Idle, sanitize(TimerState.Fading(100_000L), now = 100_000L + Config.STALE_GRACE_MILLIS + 1))

    @Test fun fadingBeforeServiceStartsIsKept() =
        // アラームで Fading になってから FadeService が動き出すまでの間（endAt はまだ先）
        assertEquals(TimerState.Fading(100_000L), sanitize(TimerState.Fading(100_000L), now = 86_000L))

    @Test fun fadingWithServiceRunningIsKept() =
        assertEquals(TimerState.Fading(100_000L), sanitize(TimerState.Fading(100_000L), now = 200_000L, fadeRunning = true))
}
