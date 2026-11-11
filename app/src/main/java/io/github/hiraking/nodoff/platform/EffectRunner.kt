package io.github.hiraking.nodoff.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import io.github.hiraking.nodoff.FadeService
import io.github.hiraking.nodoff.NodOffTileService
import io.github.hiraking.nodoff.TimerActionReceiver
import io.github.hiraking.nodoff.core.Effect

/** TimerMachine が返した副作用を、順番どおりに Android の API で実行する。 */
class EffectRunner(context: Context) {

    private val app = context.applicationContext
    private val alarmManager = app.getSystemService(AlarmManager::class.java)

    fun run(effects: List<Effect>) {
        effects.forEach(::runOne)
    }

    private fun runOne(effect: Effect) {
        when (effect) {
            is Effect.ScheduleFadeAlarm ->
                // USE_EXACT_ALARM を宣言しているので、正確なアラームは常に使える
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, effect.at, fadeAlarmIntent())
            Effect.CancelFadeAlarm -> alarmManager.cancel(fadeAlarmIntent())
            is Effect.ShowCountdown -> TimerNotification.show(app, effect.endAt)
            Effect.ClearCountdown -> TimerNotification.clear(app)
            Effect.StartFade -> app.startForegroundService(Intent(app, FadeService::class.java))
            // stopService は非同期。音量を下げ続けないよう、FadeService のループが状態を確かめて抜ける
            Effect.StopFade -> app.stopService(Intent(app, FadeService::class.java))
            Effect.RestoreVolumeNow -> restoreVolumeNow()
            // パネルが開いていて表示中なら直接描き直す。閉じていれば listening にしてもらい、onStartListening で描き直す
            Effect.RefreshTile -> {
                NodOffTileService.renderIfListening()
                TileService.requestListeningState(app, ComponentName(app, NodOffTileService::class.java))
            }
        }
    }

    /** 保存した音量があれば戻し、保存を消す */
    private fun restoreVolumeNow() {
        val store = NodOffStore(app)
        val volume = store.pendingRestoreVolume ?: return
        VolumeControl(app).set(volume)
        store.pendingRestoreVolume = null
    }

    private fun fadeAlarmIntent(): PendingIntent {
        val intent = Intent(app, TimerActionReceiver::class.java).setAction(TimerActionReceiver.ACTION_FADE_ALARM)
        return PendingIntent.getBroadcast(
            app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
