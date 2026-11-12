package io.github.hiraking.nodoff.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.hiraking.nodoff.R
import io.github.hiraking.nodoff.TimerActionReceiver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** タイマー中の通知（カウントダウン・止まる時刻・「+10 分」「キャンセル」）。文言と色は DESIGN.md「通知」。 */
object TimerNotification {
    const val CHANNEL_ID = "timer"
    const val NOTIFICATION_ID = 1

    private const val CHANNEL_NAME = "タイマー"
    private const val TITLE = "Nod Off"
    private const val ACTION_EXTEND_LABEL = "+10 分"
    private const val ACTION_CANCEL_LABEL = "キャンセル"

    /** DESIGN.md の accent */
    private const val ACCENT = 0xFFB9C3FF.toInt()

    private val STOP_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")

    /** [endAt] は elapsedRealtime のミリ秒 */
    fun show(context: Context, endAt: Long) {
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context, endAt))
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** FadeService もフォアグラウンドの通知に同じものを使う（通知を 1 つに保つため） */
    fun build(context: Context, endAt: Long): Notification {
        ensureChannel(context)
        // カウントダウンと本文は壁時計で表す
        val endWallMillis = System.currentTimeMillis() + (endAt - SystemClock.elapsedRealtime())
        val stopTime = STOP_TIME_FORMAT.format(Instant.ofEpochMilli(endWallMillis).atZone(ZoneId.systemDefault()))

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_mono)
            .setColor(ACCENT)
            .setContentTitle(TITLE)
            .setContentText("$stopTime に停止")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endWallMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(action(context, ACTION_EXTEND_LABEL, TimerActionReceiver.ACTION_EXTEND))
            .addAction(action(context, ACTION_CANCEL_LABEL, TimerActionReceiver.ACTION_CANCEL))
            .build()
    }

    private fun ensureChannel(context: Context) {
        // 重要度 LOW：音も振動も出さない。同じ ID で作り直しても、利用者が変えた設定は残る
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun action(context: Context, label: String, intentAction: String): Notification.Action {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(intentAction)
        val pi = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, pi).build()
    }
}
