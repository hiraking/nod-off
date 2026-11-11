package io.github.hiraking.nodoff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hiraking.nodoff.core.TimerEvent

/** 通知のボタンとフェード開始のアラームを受ける。debug ビルドでは adb からの開始も受ける。 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_EXTEND -> TimerController.dispatch(context, TimerEvent.ExtendPressed)
            ACTION_CANCEL -> TimerController.dispatch(context, TimerEvent.CancelPressed)
            ACTION_FADE_ALARM -> TimerController.dispatch(context, TimerEvent.FadeAlarmFired)
            // 外から届くのは debug ビルドだけ（main のマニフェストではこのレシーバーは exported="false"）
            ACTION_DEBUG_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                if (seconds > 0) TimerController.debugStart(context, seconds)
            }
        }
    }

    companion object {
        const val ACTION_EXTEND = "io.github.hiraking.nodoff.EXTEND"
        const val ACTION_CANCEL = "io.github.hiraking.nodoff.CANCEL"
        const val ACTION_FADE_ALARM = "io.github.hiraking.nodoff.FADE_ALARM"
        const val ACTION_DEBUG_START = "io.github.hiraking.nodoff.DEBUG_START"

        /** ACTION_DEBUG_START で、何秒後に終わるか（Int）。FADE_MILLIS より長くする */
        const val EXTRA_SECONDS = "seconds"
    }
}
