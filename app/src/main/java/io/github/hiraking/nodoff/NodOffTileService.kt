package io.github.hiraking.nodoff

import android.graphics.drawable.Icon
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.hiraking.nodoff.core.RestorePolicy
import io.github.hiraking.nodoff.core.TimerEvent
import io.github.hiraking.nodoff.core.TimerState
import io.github.hiraking.nodoff.platform.NodOffStore

/**
 * クイック設定のタイル（主な入り口）。表示は DESIGN.md「タイル」。
 * タップは ON／OFF の切り替え：止まっていれば既定の時間で始め、動いていれば（フェード中も）キャンセルする。
 */
class NodOffTileService : TileService() {

    override fun onClick() {
        val store = NodOffStore(this)
        // 利用者がタイルを使った＝起きている。前の晩に戻せなかった音量があれば戻す（設計書 §4.3）。
        // フェードの途中なら、TileTapped（キャンセル）の RestoreVolumeNow が戻すので、ここでは触らない
        if (!FadeService.running) {
            TimerController.dispatchRestore(this, RestorePolicy.Trigger.APP_USED)
        }
        TimerController.dispatch(this, TimerEvent.TileTapped(SystemClock.elapsedRealtime(), store.defaultDurationMin))
        // dispatch の RefreshTile は次の listening で描かれる。いま見えているタイルもすぐ描き直す
        render()
    }

    /** パネルの編集からタイルが足されたとき。設定画面の「準備」を済みにする */
    override fun onTileAdded() {
        NodOffStore(this).tileAdded = true
    }

    /** パネルの編集からタイルが外されたとき。設定画面の「準備」に「タイルを追加」を戻す */
    override fun onTileRemoved() {
        NodOffStore(this).tileAdded = false
    }

    /**
     * クイックパネルが開かれたとき（または RefreshTile で頼まれたとき）。描くだけで、音量は戻さない。
     * RefreshTile はフェードの終わり（FadeEnded）にも来るので、ここで戻すと、一時停止を確かめられず
     * まだ再生中なのに音量を戻してしまう（設計書 §3 の失敗）。音量を戻すのは onClick だけ。
     */
    override fun onStartListening() {
        listening = this
        render()
    }

    override fun onStopListening() {
        if (listening === this) listening = null
    }

    private fun render() {
        val tile = qsTile ?: return
        val store = NodOffStore(this)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_mono)
        tile.label = LABEL
        when (val state = TimerController.currentState(this)) {
            TimerState.Idle -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "${store.defaultDurationMin} 分"
            }
            is TimerState.Running -> showRemaining(tile, state.endAt)
            is TimerState.Fading -> showRemaining(tile, state.endAt)
        }
        tile.updateTile()
    }

    private fun showRemaining(tile: Tile, endAt: Long) {
        tile.state = Tile.STATE_ACTIVE
        tile.subtitle = "残り ${remainingMinutesCeil(endAt - SystemClock.elapsedRealtime())} 分"
    }

    companion object {
        private const val LABEL = "Nod Off"

        /**
         * いまパネルに表示されていて listening 中のタイル。表示中は requestListeningState が
         * onStartListening を呼び直さないので、通知からキャンセルしたときなどはこれを直接描き直す。
         * メインスレッドだけで読み書きする。
         */
        private var listening: NodOffTileService? = null

        /** 表示中のタイルがあれば、いまの状態ですぐ描き直す */
        fun renderIfListening() {
            listening?.render()
        }

        /** 残りのミリ秒を分に切り上げる（フェード中に終了時刻を過ぎても「残り 0 分」とは出さず 1 分で止める） */
        private fun remainingMinutesCeil(remainingMillis: Long): Long =
            if (remainingMillis <= 0) 1 else (remainingMillis + 59_999L) / 60_000L
    }
}
