package io.github.hiraking.nodoff.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigTest {
    @Test fun defaultsMatchSpec() {
        assertEquals(15_000L, Config.FADE_MILLIS)
        assertEquals(600_000L, Config.EXTEND_MILLIS)
        assertEquals(listOf(15, 20, 30, 45, 60), Config.DURATION_PRESETS_MIN)
        assertEquals(30, Config.DEFAULT_DURATION_MIN)
        assertEquals(1..180, Config.CUSTOM_DURATION_RANGE_MIN)
    }
}
