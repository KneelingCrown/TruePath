package ru.truepath.app.domain

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun `stored choice survives restoration and overrides system appearance`() {
        assertTrue(ThemeMode.restore("DARK").isDark(false))
        assertFalse(ThemeMode.restore("LIGHT").isDark(true))
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.restore(it.name)) }
    }

    @Test fun `missing or unknown preferences follow system theme`() {
        listOf(null, "", "old-value").forEach {
            val mode = ThemeMode.restore(it)
            assertTrue(mode.isDark(true))
            assertFalse(mode.isDark(false))
        }
    }
}
