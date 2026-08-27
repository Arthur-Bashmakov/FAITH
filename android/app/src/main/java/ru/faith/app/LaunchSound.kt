package ru.faith.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

private const val HapticPreferences = "faith_haptic_preferences"
private const val LaunchHapticEnabled = "launch_haptic_enabled"

internal fun Context.isLaunchHapticEnabled(): Boolean =
    getSharedPreferences(HapticPreferences, Context.MODE_PRIVATE)
        .getBoolean(LaunchHapticEnabled, true)

internal fun Context.saveLaunchHapticEnabled(enabled: Boolean) {
    getSharedPreferences(HapticPreferences, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(LaunchHapticEnabled, enabled)
        .apply()
}

internal fun Context.playFaithLaunchHaptic() {
    if (!isLaunchHapticEnabled()) return
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 28, 72, 42, 88, 58),
            intArrayOf(0, 55, 0, 78, 0, 105),
            -1,
        )
    )
}
