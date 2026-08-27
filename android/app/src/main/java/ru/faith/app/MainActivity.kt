package ru.faith.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val sharedAudio = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptSharedAudio(intent)
        setContent {
            FaithApp(
                contentResolver = contentResolver,
                sharedAudio = sharedAudio.value,
                onSharedAudioConsumed = { sharedAudio.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedAudio(intent)
    }

    private fun acceptSharedAudio(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("audio/") != true) return
        sharedAudio.value = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }
}

internal val DarkBackground = Color(0xFF090716)
internal val Purple = Color(0xFFA96CFF)
internal val LightPurple = Color(0xFFE8D9FF)

@Composable
fun FaithApp(
    contentResolver: android.content.ContentResolver,
    sharedAudio: Uri? = null,
    onSharedAudioConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var splashVisible by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf(context.savedLanguage()) }
    var launchSoundEnabled by remember { mutableStateOf(context.isLaunchSoundEnabled()) }

    LaunchedEffect(launchSoundEnabled) {
        if (launchSoundEnabled) {
            delay(650)
            context.applicationContext.playFaithLaunchSound()
        }
    }

    if (splashVisible) {
        FaithSplash(
            soundEnabled = launchSoundEnabled,
            onSoundEnabledChange = { enabled ->
                launchSoundEnabled = enabled
                context.saveLaunchSoundEnabled(enabled)
            },
            onFinished = { splashVisible = false },
        )
    } else if (selectedLanguage == null) {
        LanguageSelectionScreen(
            onLanguageSelected = { language ->
                context.saveLanguage(language)
                selectedLanguage = language
            },
        )
    } else {
        FaithMainScreen(
            contentResolver = contentResolver,
            language = requireNotNull(selectedLanguage),
            sharedAudio = sharedAudio,
            onSharedAudioConsumed = onSharedAudioConsumed,
        )
    }
}
