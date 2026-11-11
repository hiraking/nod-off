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
 * タップは状態にかかわらず「今から既定の時間で始め直す」。タイルからはキャンセルしない。
 */
class NodOffTileService : TileService() {

    override fun onClick() {
        val store = NodOffStore(this)
        TimerController.dispatch(this, TimerEvent.TileTapped(SystemClock.elapsedRealtime(), store.defaultDurationMin))
        // dispatch の RefreshTile は次の listening で描かれる。いま見えているタイルもすぐ描き直す
        render()
    }

    /** クイックパネルが開かれたとき（または RefreshTile で頼まれたとき） */
    override fun onStartListening() {
        TimerController.dispatchRestore(this, RestorePolicy.Trigger.APP_USED)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val store = NodOffStore(this)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_mono)
        tile.label = LABEL
        when (val state = store.state) {
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

    private companion object {
        const val LABEL = "nod-off"

        /** 残りのミリ秒を分に切り上げる（0 未満は 0） */
        fun remainingMinutesCeil(remainingMillis: Long): Long =
            if (remainingMillis <= 0) 0 else (remainingMillis + 59_999L) / 60_000L
    }
}
