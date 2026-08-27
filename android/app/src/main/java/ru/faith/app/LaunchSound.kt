package ru.faith.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

private const val SoundPreferences = "faith_sound_preferences"
private const val LaunchSoundEnabled = "launch_sound_enabled"

internal fun Context.isLaunchSoundEnabled(): Boolean =
    getSharedPreferences(SoundPreferences, Context.MODE_PRIVATE)
        .getBoolean(LaunchSoundEnabled, true)

internal fun Context.saveLaunchSoundEnabled(enabled: Boolean) {
    getSharedPreferences(SoundPreferences, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(LaunchSoundEnabled, enabled)
        .apply()
}

internal fun Context.playFaithLaunchSound() {
    if (!isLaunchSoundEnabled()) return
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

    Thread {
        val sampleRate = 44_100
        val durationSeconds = 1.30
        val sampleCount = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(sampleCount)

        for (index in samples.indices) {
            val time = index.toDouble() / sampleRate
            val firstProgress = (time / 0.88).coerceIn(0.0, 1.0)
            val secondProgress = ((time - 0.34) / 0.94).coerceIn(0.0, 1.0)
            val firstEnvelope = sin(PI * firstProgress).let { it * it }
            val secondEnvelope = if (time < 0.34) 0.0 else {
                sin(PI * secondProgress).let { it * it }
            }
            val signal =
                sin(2.0 * PI * 392.0 * time) * firstEnvelope * 0.58 +
                    sin(2.0 * PI * 587.33 * time) * secondEnvelope * 0.50 +
                    sin(2.0 * PI * 1174.66 * time) * secondEnvelope * 0.10
            samples[index] = (signal * Short.MAX_VALUE * 0.17).toInt().toShort()
        }

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        }.getOrNull() ?: return@Thread

        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((durationSeconds * 1_000).toLong() + 80)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }.apply {
        name = "faith-launch-sound"
        isDaemon = true
        start()
    }
}
