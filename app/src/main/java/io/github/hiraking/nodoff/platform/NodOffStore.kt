package io.github.hiraking.nodoff.platform

import android.content.Context
import android.provider.Settings
import io.github.hiraking.nodoff.core.Config
import io.github.hiraking.nodoff.core.TimerState

/**
 * アプリの保存（SharedPreferences「nodoff」）。
 * フェード直前の音量はプロセスが落ちる前に確実に残したいので、すべて commit() で同期的に書く。
 */
class NodOffStore(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Idle／Running／Fading と endAt（elapsedRealtime のミリ秒）を保存する */
    var state: TimerState
        get() {
            val endAt = prefs.getLong(KEY_END_AT, 0L)
            return when (prefs.getString(KEY_STATE, STATE_IDLE)) {
                STATE_RUNNING -> TimerState.Running(endAt)
                STATE_FADING -> TimerState.Fading(endAt)
                else -> TimerState.Idle
            }
        }
        set(value) {
            val editor = prefs.edit()
            when (value) {
                TimerState.Idle -> editor.putString(KEY_STATE, STATE_IDLE).remove(KEY_END_AT)
                is TimerState.Running -> editor.putString(KEY_STATE, STATE_RUNNING).putLong(KEY_END_AT, value.endAt)
                is TimerState.Fading -> editor.putString(KEY_STATE, STATE_FADING).putLong(KEY_END_AT, value.endAt)
            }
            // endAt は elapsedRealtime なので、どの起動で保存したかも残す（再起動したら意味を失う）
            editor.putInt(KEY_BOOT_COUNT, currentBootCount())
            editor.commit()
        }

    /** 保存された状態が、いまの起動で保存されたものか */
    fun isStateFromThisBoot(): Boolean = prefs.getInt(KEY_BOOT_COUNT, -1) == currentBootCount()

    private fun currentBootCount(): Int = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)

    /** 既定の時間（分）。プリセット以外の値ならカスタム */
    var defaultDurationMin: Int
        get() = prefs.getInt(KEY_DEFAULT_DURATION_MIN, Config.DEFAULT_DURATION_MIN)
        set(value) {
            prefs.edit().putInt(KEY_DEFAULT_DURATION_MIN, value).commit()
        }

    /** 設定画面からタイルの追加に成功したら true（アプリからは追加済みかを調べられないため） */
    var tileAdded: Boolean
        get() = prefs.getBoolean(KEY_TILE_ADDED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TILE_ADDED, value).commit()
        }

    /** 戻すべき音量。なければ null（0 も戻すべき値として扱う） */
    var pendingRestoreVolume: Int?
        get() = if (prefs.contains(KEY_PENDING_RESTORE_VOLUME)) prefs.getInt(KEY_PENDING_RESTORE_VOLUME, 0) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_PENDING_RESTORE_VOLUME) else editor.putInt(KEY_PENDING_RESTORE_VOLUME, value)
            editor.commit()
        }

    private companion object {
        const val PREFS_NAME = "nodoff"
        const val KEY_STATE = "state"
        const val KEY_END_AT = "end_at"
        const val KEY_BOOT_COUNT = "boot_count"
        const val KEY_DEFAULT_DURATION_MIN = "default_duration_min"
        const val KEY_TILE_ADDED = "tile_added"
        const val KEY_PENDING_RESTORE_VOLUME = "pending_restore_volume"

        const val STATE_IDLE = "idle"
        const val STATE_RUNNING = "running"
        const val STATE_FADING = "fading"
    }
}
