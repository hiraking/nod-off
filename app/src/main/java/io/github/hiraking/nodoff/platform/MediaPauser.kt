package io.github.hiraking.nodoff.platform

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import io.github.hiraking.nodoff.NodOffListenerService
import kotlinx.coroutines.delay

enum class PauseResult { NOTHING_PLAYING, PAUSED, STILL_PLAYING }

/** 再生中のメディアを一時停止する。方式 A（セッションに pause()）と、予備の方式 B（メディアキー PAUSE）。 */
class MediaPauser(context: Context) {

    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val listener = ComponentName(app, NodOffListenerService::class.java)

    fun hasListenerAccess(): Boolean =
        app.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)

    fun isAnythingPlaying(): Boolean =
        if (hasListenerAccess()) playingSessions().isNotEmpty() else audioManager.isMusicActive

    /** 再生中（PLAYING・BUFFERING）のセッションすべてに pause()。最大 3 秒、250ms ごとに確かめ、
     *  止まらなければ KEYCODE_MEDIA_PAUSE を送ってもう一度 3 秒確かめる。
     *  通知へのアクセスがなければ最初から方式 B を送り、AudioManager.isMusicActive で確かめる。 */
    suspend fun pauseAll(): PauseResult {
        if (!isAnythingPlaying()) return PauseResult.NOTHING_PLAYING

        if (hasListenerAccess()) {
            // 方式 A
            playingSessions().forEach { it.transportControls.pause() }
            if (waitUntilStopped()) return PauseResult.PAUSED
        }

        // 方式 B
        sendMediaPauseKey()
        return if (waitUntilStopped()) PauseResult.PAUSED else PauseResult.STILL_PLAYING
    }

    /** 最大 CHECK_TIMEOUT_MILLIS、CHECK_INTERVAL_MILLIS ごとに確かめる。止まったら true */
    private suspend fun waitUntilStopped(): Boolean {
        var waited = 0L
        while (waited < CHECK_TIMEOUT_MILLIS) {
            delay(CHECK_INTERVAL_MILLIS)
            waited += CHECK_INTERVAL_MILLIS
            if (!isAnythingPlaying()) return true
        }
        return false
    }

    private fun playingSessions(): List<MediaController> {
        val sessions = try {
            app.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener)
        } catch (e: SecurityException) {
            // 通知へのアクセスが確かめたあとで外された
            return emptyList()
        }
        return sessions.filter {
            when (it.playbackState?.state) {
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> true
                else -> false
            }
        }
    }

    private fun sendMediaPauseKey() {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    private companion object {
        const val CHECK_TIMEOUT_MILLIS = 3_000L
        const val CHECK_INTERVAL_MILLIS = 250L
    }
}
