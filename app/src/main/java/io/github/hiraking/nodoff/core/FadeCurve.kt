package io.github.hiraking.nodoff.core

/** フェード中の音量の計算。 */
object FadeCurve {

    /**
     * フェードを始めてから [elapsedMs] 経ったときの音量。
     * ceil(original * (1 - elapsed / fade)) を 0..original に収める。
     * 浮動小数の誤差を避けるため整数で計算する。original が 0 なら常に 0（0 で割らない）。
     */
    fun volumeAt(original: Int, elapsedMs: Long, fadeMs: Long = Config.FADE_MILLIS): Int {
        if (original <= 0 || fadeMs <= 0L) return 0
        val remaining = (fadeMs - elapsedMs).coerceIn(0L, fadeMs)
        val v = (original.toLong() * remaining + fadeMs - 1) / fadeMs
        return v.toInt().coerceIn(0, original)
    }
}
