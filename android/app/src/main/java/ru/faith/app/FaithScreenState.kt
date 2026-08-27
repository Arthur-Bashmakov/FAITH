package ru.faith.app

internal data class SelectedAudioInfo(
    val name: String,
    val format: String?,
    val sizeBytes: Long?,
    val durationMillis: Long?,
    val trackTitle: String?,
    val artist: String?,
)

internal enum class AudioPage {
    HOME,
    RECORDING,
    PREVIEW,
    PROCESSING,
    RESULT,
    HISTORY,
}
