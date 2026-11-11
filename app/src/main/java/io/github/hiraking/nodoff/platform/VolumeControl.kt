package io.github.hiraking.nodoff.platform

import android.content.Context
import android.media.AudioManager

/** メディアの音量（STREAM_MUSIC）の取得と設定。音量の UI は出さない（flags = 0）。 */
class VolumeControl(context: Context) {

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    fun current(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun set(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
}
