package ru.truepath.app.domain

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean) = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun restore(value: String?) = entries.find { it.name == value } ?: SYSTEM
    }
}
