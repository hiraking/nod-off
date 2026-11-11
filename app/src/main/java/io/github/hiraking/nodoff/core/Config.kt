package io.github.hiraking.nodoff.core

/** 仕様で決めた定数（設計書と計画の Global Constraints）。 */
object Config {
    /** フェードの長さ。音量 0 まで下げる */
    const val FADE_MILLIS = 15_000L

    /** 通知の「+10 分」で残り時間に足す長さ */
    const val EXTEND_MILLIS = 600_000L

    /** 既定の時間の選択肢（分） */
    val DURATION_PRESETS_MIN = listOf(15, 20, 30, 45, 60)

    /** 既定の時間の初期値（分） */
    const val DEFAULT_DURATION_MIN = 30

    /** カスタムで選べる時間の範囲（分） */
    val CUSTOM_DURATION_RANGE_MIN = 1..180

    /** 終了時刻をこれ以上過ぎても Running／Fading のままなら、保存の状態が古い（アラームが届かなかった・プロセスが落ちた） */
    const val STALE_GRACE_MILLIS = 60_000L
}
