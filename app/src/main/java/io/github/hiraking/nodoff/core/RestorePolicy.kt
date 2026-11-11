package io.github.hiraking.nodoff.core

/** 保存してある「戻すべき音量」をいつ戻すか。 */
object RestorePolicy {

    enum class Trigger {
        /** 一時停止を確かめられた */
        PAUSE_CONFIRMED,

        /** 時間になったとき何も再生されていなかった */
        NO_MEDIA_PLAYING,

        /** 画面ロックが解除された */
        SCREEN_UNLOCKED,

        /** 利用者がアプリ（タイル・通知・設定画面）を使った */
        APP_USED,

        /** 方式 A・B を試しても再生されたまま。夜中に大きな音を出さないため戻さない */
        STILL_PLAYING_AFTER_PAUSE,
    }

    /** [pendingVolume] は保存してある戻すべき音量（なければ null。0 も戻すべき値として扱う）。 */
    fun shouldRestore(pendingVolume: Int?, trigger: Trigger): Boolean {
        if (pendingVolume == null) return false
        return when (trigger) {
            Trigger.PAUSE_CONFIRMED,
            Trigger.NO_MEDIA_PLAYING,
            Trigger.SCREEN_UNLOCKED,
            Trigger.APP_USED -> true
            Trigger.STILL_PLAYING_AFTER_PAUSE -> false
        }
    }
}
