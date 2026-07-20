package com.aliothmoon.maameow.domain.models

internal enum class DesiredGameAudio {
    MUTED,
    AUDIBLE;

    companion object {
        fun fromStoredValue(value: String): DesiredGameAudio =
            entries.firstOrNull { it.name == value } ?: MUTED
    }
}
