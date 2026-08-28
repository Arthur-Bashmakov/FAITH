package ru.faith.app

import kotlin.math.roundToInt

internal enum class ModelScoreBand {
    LOW,
    INTERMEDIATE,
    HIGH,
}

internal fun syntheticScorePercent(probability: Double): Int =
    (probability.coerceIn(0.0, 1.0) * 100).roundToInt()

internal fun modelScoreBand(verdict: String): ModelScoreBand = when (verdict) {
    "synthetic" -> ModelScoreBand.HIGH
    "uncertain" -> ModelScoreBand.INTERMEDIATE
    else -> ModelScoreBand.LOW
}

internal fun isAuthSubmitEnabled(
    identifier: String,
    password: String,
    registerMode: Boolean,
): Boolean = identifier.isNotBlank() && if (registerMode) {
    password.length >= 10
} else {
    password.isNotBlank()
}
