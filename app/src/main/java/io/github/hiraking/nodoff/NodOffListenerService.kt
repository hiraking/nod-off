package io.github.hiraking.nodoff

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import io.github.hiraking.nodoff.core.RestorePolicy
import io.github.hiraking.nodoff.platform.MediaPauser

/**
 * 通知へのアクセスを得るためのサービス。MediaPauser が getActiveSessions の ComponentName に使う。
 *
 * システムが常駐させるので、音量の後始末の見張りもここで行う（設計書 §4.3「音量の後始末」）。
 * - 画面ロックの解除（ACTION_USER_PRESENT）で戻す
 * - 再生中のメディアがなくなったら戻す
 * - つながったとき（再起動のあとを含む）にも、何も再生されていなければ戻す
 *
 * コールバックはすべてメインスレッドで受ける（FadeService・TimerController と同じスレッド）。
 */
class NodOffListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connected = false

    /** 見張っているセッションと、それぞれに付けたコールバック */
    private val watched = mutableListOf<Pair<MediaController, MediaController.Callback>>()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) restore(RestorePolicy.Trigger.SCREEN_UNLOCKED)
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watchSessions(controllers.orEmpty())
        restoreIfNothingPlaying()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (connected) return
        connected = true

        // ACTION_USER_PRESENT はシステムだけが送る。マニフェストでは受けられないので動的に登録する
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), RECEIVER_NOT_EXPORTED)

        val sessionManager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, NodOffListenerService::class.java)
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
            watchSessions(sessionManager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // つながった直後にアクセスが外された。次につながったときにやり直す
        }

        // 再起動やプロセスが落ちたあとの後始末
        restoreIfNothingPlaying()
    }

    override fun onListenerDisconnected() {
        unregisterAll()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        unregisterAll()
        super.onDestroy()
    }

    private fun unregisterAll() {
        if (!connected) return
        connected = false
        unregisterReceiver(unlockReceiver)
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener)
        unwatchSessions()
    }

    /** セッションの一覧が変わるたびに、コールバックを付け直す */
    private fun watchSessions(controllers: List<MediaController>) {
        unwatchSessions()
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    restoreIfNothingPlaying()
                }

                override fun onSessionDestroyed() {
                    restoreIfNothingPlaying()
                }
            }
            controller.registerCallback(callback, mainHandler)
            watched += controller to callback
        }
    }

    private fun unwatchSessions() {
        watched.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        watched.clear()
    }

    private fun restoreIfNothingPlaying() {
        if (!MediaPauser(this).isAnythingPlaying()) restore(RestorePolicy.Trigger.NO_MEDIA_PLAYING)
    }

    private fun restore(trigger: RestorePolicy.Trigger) {
        // フェードの途中は FadeService が一時停止を確かめてから戻す。ここで戻すと保存が消え、
        // そのあと 0 まで下げた音量が戻らなくなる。保存の状態（Fading）はプロセスが落ちても残るので、
        // 「フェードが本当に動いているか」で判断する（落ちたあと・再起動のあとは戻す）
        if (FadeService.running) return
        TimerController.dispatchRestore(this, trigger)
    }
}
