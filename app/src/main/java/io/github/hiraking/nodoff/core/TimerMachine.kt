package io.github.hiraking.nodoff.core

/*
 * タイマーの状態機械。Android に依存しない純粋な Kotlin。
 * 時刻はすべて SystemClock.elapsedRealtime() のミリ秒。
 */

sealed interface TimerState {
    data object Idle : TimerState
    data class Running(val endAt: Long) : TimerState
    data class Fading(val endAt: Long) : TimerState
}

sealed interface TimerEvent {
    data class TileTapped(val now: Long, val durationMin: Int) : TimerEvent
    data object ExtendPressed : TimerEvent
    data object CancelPressed : TimerEvent
    data object FadeAlarmFired : TimerEvent

    /** フェードと一時停止の処理が終わった */
    data object FadeEnded : TimerEvent
}

sealed interface Effect {
    /** at = endAt - FADE_MILLIS */
    data class ScheduleFadeAlarm(val at: Long) : Effect
    data object CancelFadeAlarm : Effect
    data class ShowCountdown(val endAt: Long) : Effect
    data object ClearCountdown : Effect

    /** FadeService を起動する */
    data object StartFade : Effect

    /** FadeService を止める */
    data object StopFade : Effect

    /** 保存した音量があれば戻し、保存を消す */
    data object RestoreVolumeNow : Effect
    data object RefreshTile : Effect
}

data class Transition(val state: TimerState, val effects: List<Effect>)

object TimerMachine {

    fun reduce(state: TimerState, event: TimerEvent): Transition = when (event) {
        is TimerEvent.TileTapped -> when (state) {
            // 止まっていれば既定の時間で始め、動いていれば（フェード中も）キャンセルする。ON／OFF の切り替え
            TimerState.Idle -> runUntil(state, event.now + event.durationMin * 60_000L)
            is TimerState.Running, is TimerState.Fading -> cancel(state)
        }

        TimerEvent.ExtendPressed -> when (state) {
            is TimerState.Running -> runUntil(state, state.endAt + Config.EXTEND_MILLIS)
            is TimerState.Fading -> runUntil(state, state.endAt + Config.EXTEND_MILLIS)
            TimerState.Idle -> cleanUp()
        }

        TimerEvent.CancelPressed -> when (state) {
            is TimerState.Running, is TimerState.Fading -> cancel(state)
            TimerState.Idle -> cleanUp()
        }

        TimerEvent.FadeAlarmFired -> when (state) {
            is TimerState.Running -> Transition(TimerState.Fading(state.endAt), listOf(Effect.StartFade))
            // 二重に届いたアラーム。フェードは動いているので何もしない
            is TimerState.Fading -> Transition(state, emptyList())
            TimerState.Idle -> cleanUp()
        }

        TimerEvent.FadeEnded -> when (state) {
            // 音量を戻すかは FadeService が RestorePolicy で決める
            is TimerState.Fading -> Transition(
                TimerState.Idle,
                listOf(Effect.ClearCountdown, Effect.RefreshTile),
            )
            // 止めたフェードの終わりが遅れて届いた。次のタイマーを壊さない
            is TimerState.Running -> Transition(state, emptyList())
            TimerState.Idle -> cleanUp()
        }
    }

    /** endAt まで動かす。フェード中なら先にフェードを止めて音量を戻す。 */
    private fun runUntil(from: TimerState, endAt: Long) = Transition(
        TimerState.Running(endAt),
        stopFadeIfFading(from) + listOf(
            Effect.CancelFadeAlarm,
            Effect.ScheduleFadeAlarm(endAt - Config.FADE_MILLIS),
            Effect.ShowCountdown(endAt),
            Effect.RefreshTile,
        ),
    )

    /** タイマーを止める。フェード中なら先にフェードを止めて音量を戻す。 */
    private fun cancel(from: TimerState) = Transition(
        TimerState.Idle,
        stopFadeIfFading(from) + listOf(Effect.CancelFadeAlarm, Effect.ClearCountdown, Effect.RefreshTile),
    )

    private fun stopFadeIfFading(state: TimerState): List<Effect> =
        if (state is TimerState.Fading) listOf(Effect.StopFade, Effect.RestoreVolumeNow) else emptyList()

    /**
     * 保存された状態が実態と合っているかを確かめ、合っていなければ Idle を返す。
     * - 前回の起動の状態（[sameBoot] が false）：elapsedRealtime が 0 からやり直しなので、endAt は意味を失っている
     * - 終了時刻を [Config.STALE_GRACE_MILLIS] より過ぎた Running：アラームが届かなかった
     * - 終了時刻を同じだけ過ぎ、フェードも動いていない Fading：フェードの途中でプロセスが落ちた
     */
    fun sanitize(state: TimerState, now: Long, sameBoot: Boolean, fadeRunning: Boolean): TimerState {
        if (state == TimerState.Idle) return state
        if (!sameBoot) return TimerState.Idle
        val pastEnd = when (state) {
            is TimerState.Running -> state.endAt
            is TimerState.Fading -> state.endAt
            TimerState.Idle -> return state
        }.let { now > it + Config.STALE_GRACE_MILLIS }
        val stale = when (state) {
            is TimerState.Fading -> pastEnd && !fadeRunning
            else -> pastEnd
        }
        return if (stale) TimerState.Idle else state
    }

    /** Idle で届いたイベント。古い通知やアラームの残りを片づけるだけ */
    private fun cleanUp() = Transition(TimerState.Idle, listOf(Effect.ClearCountdown, Effect.RefreshTile))
}
