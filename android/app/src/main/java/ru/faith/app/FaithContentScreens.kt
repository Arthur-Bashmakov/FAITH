package ru.faith.app

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeScreen(
    context: Context,
    onChooseAudio: () -> Unit,
    onRecordVoice: () -> Unit,
) {
    Text(
        context.getString(R.string.choose_audio_prompt),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    Button(
        onClick = onChooseAudio,
        colors = ButtonDefaults.buttonColors(containerColor = Purple),
    ) { Text(context.getString(R.string.choose_audio)) }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onRecordVoice,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A2A92)),
    ) { Text(context.getString(R.string.record_voice)) }
}

@Composable
internal fun RecordingScreen(
    context: Context,
    recordingSeconds: Int,
    amplitudes: List<Float>,
    onStopRecording: () -> Unit,
) {
    Text(
        context.getString(R.string.recording_title),
        color = Color(0xFFFF8A9A),
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        context.getString(
            R.string.recording_time,
            recordingSeconds / 60,
            recordingSeconds % 60,
        ),
        color = Color.White,
        fontSize = 38.sp,
        fontWeight = FontWeight.Light,
    )
    Spacer(Modifier.height(16.dp))
    LiveWaveform(amplitudes = amplitudes)
    Text(
        context.getString(R.string.recording_signal_hint),
        color = LightPurple.copy(alpha = 0.8f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        context.getString(R.string.recording_description),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onStopRecording,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A3153)),
    ) { Text(context.getString(R.string.stop_recording)) }
}

@Composable
internal fun PreviewScreen(
    context: Context,
    audioInfo: SelectedAudioInfo?,
    hasSelectedFile: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackError: Boolean,
    onTogglePlayback: () -> Unit,
    onAnalyze: () -> Unit,
    onChooseAnother: () -> Unit,
    onClear: () -> Unit,
) {
    Text(
        audioInfo?.name.orEmpty(),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    audioInfo?.let { AudioInfoCard(info = it, context = context) }
    Spacer(Modifier.height(12.dp))
    if (hasSelectedFile) {
        PlaybackButton(
            isPlaying = isPlaying,
            progress = playbackProgress,
            context = context,
            onClick = onTogglePlayback,
        )
        if (playbackError) {
            Text(
                context.getString(R.string.playback_error),
                color = Color(0xFFFF8A9A),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    Button(
        onClick = onAnalyze,
        enabled = hasSelectedFile,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A2A92)),
    ) { Text(context.getString(R.string.analyze_audio)) }
    TextButton(onClick = onChooseAnother) {
        Text(context.getString(R.string.choose_another_audio), color = LightPurple)
    }
    TextButton(onClick = onClear) {
        Text(context.getString(R.string.clear_selected_audio), color = LightPurple)
    }
}

@Composable
internal fun ProcessingScreen(
    context: Context,
    uploadProgress: Float,
    onCancel: () -> Unit,
) {
    if (uploadProgress in 0.001f..<1f) {
        CircularProgressIndicator(progress = { uploadProgress }, color = Purple)
    } else {
        CircularProgressIndicator(color = Purple)
    }
    Spacer(Modifier.height(20.dp))
    Text(
        context.getString(R.string.processing_title),
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
    Text(
        if (uploadProgress < 1f) {
            context.getString(R.string.upload_progress, (uploadProgress * 100).toInt())
        } else {
            context.getString(R.string.model_processing)
        },
        color = LightPurple,
    )
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onCancel) {
        Text(context.getString(R.string.cancel_analysis), color = LightPurple)
    }
}

@Composable
internal fun AnalysisResultScreen(
    context: Context,
    result: AnalysisResult?,
    audioInfo: SelectedAudioInfo?,
    error: String?,
    validationIssue: Boolean,
    hasSelectedFile: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlayback: () -> Unit,
    onTryAgain: () -> Unit,
    onBackHome: () -> Unit,
) {
    if (error != null) {
        MessageCard(
            context.getString(
                if (validationIssue) R.string.speech_not_detected_title
                else R.string.error_title,
            ),
            error,
            Color(0xFFFF8A9A),
            compact = true,
        )
    } else {
        result?.let {
            ResultCard(result = it, audioInfo = audioInfo, context = context)
        }
    }
    if (hasSelectedFile) {
        Spacer(Modifier.height(12.dp))
        PlaybackButton(
            isPlaying = isPlaying,
            progress = playbackProgress,
            context = context,
            onClick = onTogglePlayback,
        )
    }
    Spacer(Modifier.height(14.dp))
    if (hasSelectedFile) {
        Button(onClick = onTryAgain) {
            Text(context.getString(R.string.try_again))
        }
    }
    TextButton(onClick = onBackHome) {
        Text(context.getString(R.string.back_home), color = LightPurple)
    }
}
