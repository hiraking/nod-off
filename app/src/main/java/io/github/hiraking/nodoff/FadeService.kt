package io.github.hiraking.nodoff

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import io.github.hiraking.nodoff.core.Config
import io.github.hiraking.nodoff.core.FadeCurve
import io.github.hiraking.nodoff.core.RestorePolicy
import io.github.hiraking.nodoff.core.TimerEvent
import io.github.hiraking.nodoff.core.TimerState
import io.github.hiraking.nodoff.platform.MediaPauser
import io.github.hiraking.nodoff.platform.NodOffStore
import io.github.hiraking.nodoff.platform.PauseResult
import io.github.hiraking.nodoff.platform.TimerNotification
import io.github.hiraking.nodoff.platform.VolumeControl
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * フェードの開始時刻から、15 秒のフェード・一時停止・音量の復帰を行う短時間のフォアグラウンドサービス（shortService）。
 *
 * 処理はメインスレッドのコルーチンで動かす。TimerController.dispatch もメインスレッド（レシーバー・タイル）から
 * 呼ばれるので、「状態を確かめてから音量を設定する」あいだに状態が変わることはない。
 */
class FadeService : Service() {

    private val scope = MainScope()
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = NodOffStore(this)
        val endAt = (store.state as? TimerState.Fading)?.endAt ?: SystemClock.elapsedRealtime()
        // カウントダウンの通知をそのままフォアグラウンドの通知にする（通知を 1 つに保つ）
        startForeground(
            TimerNotification.NOTIFICATION_ID,
            TimerNotification.build(this, endAt),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
        )
        // 二重に起動されたら、動いているフェードに任せる
        if (job?.isActive != true) {
            job = scope.launch { fadeAndPause(store) }
        }
        return START_NOT_STICKY
    }

    private suspend fun fadeAndPause(store: NodOffStore) {
        val volume = VolumeControl(this)

        // 1. 戻すべき音量を、下げ始める前に保存する。前回戻せなかった値が残っていれば、そちらを優先する
        val original = volume.current()
        if (store.pendingRestoreVolume == null) store.pendingRestoreVolume = original

        // 2. 15 秒かけて 0 まで下げる
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            // stopService は非同期なので、RestoreVolumeNow のあとに下げてしまわないよう毎回確かめる
            if (store.state !is TimerState.Fading) return
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            volume.set(FadeCurve.volumeAt(original, elapsed))
            if (elapsed >= Config.FADE_MILLIS) break
            delay(FADE_STEP_MILLIS)
        }

        // 3. 一時停止し、確かめられたときだけ音量を戻す
        val trigger = when (MediaPauser(this).pauseAll()) {
            PauseResult.PAUSED -> RestorePolicy.Trigger.PAUSE_CONFIRMED
            PauseResult.NOTHING_PLAYING -> RestorePolicy.Trigger.NO_MEDIA_PLAYING
            PauseResult.STILL_PLAYING -> RestorePolicy.Trigger.STILL_PLAYING_AFTER_PAUSE
        }
        if (store.state !is TimerState.Fading) return
        TimerController.dispatchRestore(this, trigger)

        // 4. タイマーを終える
        TimerController.dispatch(this, TimerEvent.FadeEnded)
        stopSelf()
    }

    /** shortService の時間切れ。フェードは 15 秒＋確認の最大 6 秒なので、ふつうは来ない */
    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        // Fading のまま残すと、タイルと通知が動いている表示のままになる。戻せていない音量は後始末で戻す
        TimerController.dispatch(this, TimerEvent.FadeEnded)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        // REMOVE だと、+10 分やタイルで始め直したときのカウントダウンまで消える。
        // 通知はサービスから切り離し、保存された状態に合わせて出し直すか消す
        stopForeground(STOP_FOREGROUND_DETACH)
        when (val state = NodOffStore(this).state) {
            TimerState.Idle -> TimerNotification.clear(this)
            is TimerState.Running -> TimerNotification.show(this, state.endAt)
            is TimerState.Fading -> TimerNotification.show(this, state.endAt)
        }
        super.onDestroy()
    }

    private companion object {
        const val FADE_STEP_MILLIS = 200L
    }
}
