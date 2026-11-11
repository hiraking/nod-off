package io.github.hiraking.nodoff

import android.content.Context
import android.os.SystemClock
import io.github.hiraking.nodoff.core.RestorePolicy
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
        val transition = TimerMachine.reduce(store.state, event)
        // 副作用より先に保存する。FadeService のループはこの状態を見てフェードをやめる
        store.state = transition.state
        EffectRunner(context).run(transition.effects)
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
     */
    fun debugStart(context: Context, seconds: Int) {
        val now = SystemClock.elapsedRealtime()
        dispatch(context, TimerEvent.TileTapped(now + seconds * 1000L - DEBUG_DURATION_MIN * 60_000L, DEBUG_DURATION_MIN))
    }
}
