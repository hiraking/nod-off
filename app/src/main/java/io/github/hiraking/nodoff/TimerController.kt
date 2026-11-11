package io.github.hiraking.nodoff

import android.content.Context
import android.os.SystemClock
import io.github.hiraking.nodoff.core.Effect
import io.github.hiraking.nodoff.core.RestorePolicy
import io.github.hiraking.nodoff.core.TimerState
import io.github.hiraking.nodoff.core.TimerEvent
import io.github.hiraking.nodoff.core.TimerMachine
import io.github.hiraking.nodoff.platform.EffectRunner
import io.github.hiraking.nodoff.platform.NodOffStore
import io.github.hiraking.nodoff.platform.VolumeControl

/** タイマーを動かす唯一の入口。 */
object TimerController {

    private const val DEBUG_DURATION_MIN = 30

    /** イベントを状態機械に通し、新しい状態を保存してから、副作用を実行する。 */
    @Synchronized
    fun dispatch(context: Context, event: TimerEvent) {
        val store = NodOffStore(context)
        val transition = TimerMachine.reduce(currentState(context), event)
        // 副作用より先に保存する。FadeService のループはこの状態を見てフェードをやめる
        store.state = transition.state
        EffectRunner(context).run(transition.effects)
    }

    /**
     * 保存された状態を、実態と合わせてから返す（TimerMachine.sanitize）。
     * 再起動やフェード中のプロセスの終了で古くなった Running／Fading は Idle に戻し、残った通知とアラームも片づける。
     */
    @Synchronized
    fun currentState(context: Context): TimerState {
        val store = NodOffStore(context)
        val saved = store.state
        val sane = TimerMachine.sanitize(
            saved, SystemClock.elapsedRealtime(), store.isStateFromThisBoot(), FadeService.running,
        )
        if (sane != saved) {
            store.state = sane
            EffectRunner(context).run(listOf(Effect.CancelFadeAlarm, Effect.ClearCountdown))
        }
        return sane
    }

    /** 保存された音量を RestorePolicy.shouldRestore に従って戻し、戻したら保存を消す */
    @Synchronized
    fun dispatchRestore(context: Context, trigger: RestorePolicy.Trigger) {
        val store = NodOffStore(context)
        val volume = store.pendingRestoreVolume ?: return
        if (!RestorePolicy.shouldRestore(volume, trigger)) return
        VolumeControl(context).set(volume)
        store.pendingRestoreVolume = null
    }

    /**
     * debug だけの入口。[seconds] 秒後に終わるタイマーを始める。
     * 状態機械に debug 用の分岐を足さないよう、now をずらした 30 分のタイルのタップとして渡す。
     * 動いているときのタップはキャンセルになるので、先に止めてから始める。
     */
    fun debugStart(context: Context, seconds: Int) {
        if (currentState(context) != TimerState.Idle) dispatch(context, TimerEvent.CancelPressed)
        val now = SystemClock.elapsedRealtime()
        dispatch(context, TimerEvent.TileTapped(now + seconds * 1000L - DEBUG_DURATION_MIN * 60_000L, DEBUG_DURATION_MIN))
    }
}
