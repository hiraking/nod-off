package io.github.hiraking.nodoff.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeCurveTest {
    @Test fun startsAtOriginal() = assertEquals(10, FadeCurve.volumeAt(10, 0L))
    @Test fun halfwayIsHalfRoundedUp() = assertEquals(5, FadeCurve.volumeAt(10, 7_500L))
    @Test fun reachesZeroAtEnd() = assertEquals(0, FadeCurve.volumeAt(10, 15_000L))
    @Test fun neverNegativeAfterEnd() = assertEquals(0, FadeCurve.volumeAt(10, 20_000L))
    @Test fun zeroOriginalStaysZero() = assertEquals(0, FadeCurve.volumeAt(0, 3_000L))

    @Test fun nonIncreasing() {
        var prev = FadeCurve.volumeAt(15, 0L)
        for (ms in 0L..15_000L step 100L) {
            val v = FadeCurve.volumeAt(15, ms)
            assertTrue("at $ms: $v > $prev", v <= prev)
            assertTrue("at $ms: $v out of range", v in 0..15)
            prev = v
        }
    }
}
