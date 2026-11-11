package io.github.hiraking.nodoff.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerMachineTest {

    // --- TileTapped ---

    @Test fun tapWhileIdleStartsDefaultDuration() {
        val t = TimerMachine.reduce(TimerState.Idle, TimerEvent.TileTapped(now = 1_000L, durationMin = 30))
        assertEquals(TimerState.Running(1_000L + 1_800_000L), t.state)
        assertEquals(
            listOf(
                Effect.CancelFadeAlarm, Effect.ScheduleFadeAlarm(1_801_000L - 15_000L),
                Effect.ShowCountdown(1_801_000L), Effect.RefreshTile,
            ),
            t.effects,
        )
    }

    @Test fun tapWhileRunningRestartsFromNowNotAdds() {
        val t = TimerMachine.reduce(TimerState.Running(endAt = 5_000_000L), TimerEvent.TileTapped(now = 2_000_000L, durationMin = 30))
        assertEquals(TimerState.Running(3_800_000L), t.state)
        assertEquals(
            listOf(
                Effect.CancelFadeAlarm, Effect.ScheduleFadeAlarm(3_800_000L - 15_000L),
                Effect.ShowCountdown(3_800_000L), Effect.RefreshTile,
            ),
            t.effects,
        )
    }

    @Test fun tapWhileFadingRestoresVolumeFirst() {
        val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.TileTapped(now = 90_000L, durationMin = 30))
        assertEquals(listOf(Effect.StopFade, Effect.RestoreVolumeNow), t.effects.take(2))
        assertTrue(t.state is TimerState.Running)
        assertEquals(TimerState.Running(1_890_000L), t.state)
        assertEquals(
            listOf(
                Effect.StopFade, Effect.RestoreVolumeNow,
                Effect.CancelFadeAlarm, Effect.ScheduleFadeAlarm(1_890_000L - 15_000L),
                Effect.ShowCountdown(1_890_000L), Effect.RefreshTile,
            ),
            t.effects,
        )
    }

    // --- ExtendPressed ---

    @Test fun extendWhileRunningAddsTenMinutes() {
        val t = TimerMachine.reduce(TimerState.Running(endAt = 100_000L), TimerEvent.ExtendPressed)
        assertEquals(TimerState.Running(700_000L), t.state)
        assertEquals(
            listOf(
                Effect.CancelFadeAlarm, Effect.ScheduleFadeAlarm(700_000L - 15_000L),
                Effect.ShowCountdown(700_000L), Effect.RefreshTile,
            ),
            t.effects,
        )
    }

    @Test fun extendWhileFadingRestoresVolumeAndAddsTenMinutes() {
        val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.ExtendPressed)
        assertEquals(TimerState.Running(700_000L), t.state)
        assertEquals(listOf(Effect.StopFade, Effect.RestoreVolumeNow), t.effects.take(2))
        assertEquals(
            listOf(
                Effect.StopFade, Effect.RestoreVolumeNow,
                Effect.CancelFadeAlarm, Effect.ScheduleFadeAlarm(700_000L - 15_000L),
                Effect.ShowCountdown(700_000L), Effect.RefreshTile,
            ),
            t.effects,
        )
    }

    // --- CancelPressed ---

    @Test fun cancelWhileRunningGoesIdle() {
        val t = TimerMachine.reduce(TimerState.Running(endAt = 100_000L), TimerEvent.CancelPressed)
        assertEquals(TimerState.Idle, t.state)
        assertEquals(listOf(Effect.CancelFadeAlarm, Effect.ClearCountdown, Effect.RefreshTile), t.effects)
    }

    @Test fun cancelWhileFadingRestoresVolume() {
        val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.CancelPressed)
        assertEquals(TimerState.Idle, t.state)
        assertTrue(Effect.StopFade in t.effects)
        assertTrue(Effect.RestoreVolumeNow in t.effects)
        assertEquals(
            listOf(
                Effect.StopFade, Effect.RestoreVolumeNow,
                Effect.CancelFadeAlarm, Effect.ClearCountdown, Effect.RefreshTile,
            ),
            t.effects,
        )
    }

    // --- FadeAlarmFired / FadeEnded ---

    @Test fun fadeAlarmWhileRunningStartsFade() {
        val t = TimerMachine.reduce(TimerState.Running(endAt = 100_000L), TimerEvent.FadeAlarmFired)
        assertEquals(TimerState.Fading(100_000L), t.state)
        assertEquals(listOf(Effect.StartFade), t.effects)
    }

    @Test fun fadeEndedGoesIdleAndClearsNotification() {
        val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.FadeEnded)
        assertEquals(TimerState.Idle, t.state)
        assertEquals(listOf(Effect.ClearCountdown, Effect.RefreshTile), t.effects)
    }

    @Test fun staleEventsWhileIdleOnlyCleanUp() {
        val events = listOf(
            TimerEvent.ExtendPressed, TimerEvent.CancelPressed,
            TimerEvent.FadeAlarmFired, TimerEvent.FadeEnded,
        )
        for (e in events) {
            val t = TimerMachine.reduce(TimerState.Idle, e)
            assertEquals("state after $e", TimerState.Idle, t.state)
            assertEquals("effects after $e", listOf(Effect.ClearCountdown, Effect.RefreshTile), t.effects)
        }
    }

    // --- 表にない組み合わせ：二重に届いた・遅れて届いたイベントは無視する ---

    @Test fun duplicateFadeAlarmWhileFadingIsIgnored() {
        val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.FadeAlarmFired)
        assertEquals(TimerState.Fading(100_000L), t.state)
        assertEquals(emptyList<Effect>(), t.effects)
    }

    @Test fun lateFadeEndedWhileRunningIsIgnored() {
        val t = TimerMachine.reduce(TimerState.Running(endAt = 100_000L), TimerEvent.FadeEnded)
        assertEquals(TimerState.Running(100_000L), t.state)
        assertEquals(emptyList<Effect>(), t.effects)
    }
}
