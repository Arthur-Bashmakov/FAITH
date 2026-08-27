package ru.faith.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun PlaybackButton(
    isPlaying: Boolean,
    progress: Float,
    context: Context,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .width(if (compact) 104.dp else 158.dp)
            .height(if (compact) 40.dp else 46.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF4B2B73))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Purple.copy(alpha = 0.78f)),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
            )
            Text(
                context.getString(if (isPlaying) R.string.pause_audio else R.string.play_audio),
                modifier = Modifier.padding(start = if (compact) 2.dp else 6.dp),
                color = Color.White,
                fontSize = if (compact) 11.sp else 14.sp,
            )
        }
    }
}

@Composable
internal fun LiveWaveform(amplitudes: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
    ) {
        val values = amplitudes.ifEmpty { List(32) { 0f } }
        val spacing = size.width / values.size
        val centerY = size.height / 2f
        values.forEachIndexed { index, amplitude ->
            val barHeight = 4f + amplitude.coerceIn(0f, 1f) * size.height * 0.82f
            val x = spacing * index + spacing / 2f
            drawLine(
                color = Purple.copy(alpha = 0.45f + amplitude * 0.55f),
                start = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2f),
                end = androidx.compose.ui.geometry.Offset(x, centerY + barHeight / 2f),
                strokeWidth = (spacing * 0.42f).coerceAtLeast(3f),
                cap = StrokeCap.Round,
            )
        }
    }
}

internal fun readAudioInfo(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
): SelectedAudioInfo {
    val localFile = uri.path?.let(::File)?.takeIf { uri.scheme == "file" }
    var name = localFile?.name ?: uri.lastPathSegment ?: "audio"
    var size: Long? = localFile?.length()
    if (localFile == null) {
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) {
                        name = cursor.getString(nameIndex)
                            .substringAfterLast('/')
                            .substringAfterLast('\\')
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    var trackTitle: String? = null
    var artist: String? = null
    val duration = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            if (localFile != null) {
                retriever.setDataSource(localFile.absolutePath)
            } else {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                }
            }
            trackTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()
    return SelectedAudioInfo(
        name = name,
        format = contentResolver.getType(uri)?.substringAfter('/') ?: name.substringAfterLast('.', ""),
        sizeBytes = size,
        durationMillis = duration,
        trackTitle = trackTitle,
        artist = artist,
    )
}

@Composable
internal fun AudioInfoCard(
    info: SelectedAudioInfo,
    context: Context,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlayback: () -> Unit,
) {
    val unknown = context.getString(R.string.audio_value_unknown)
    val duration = info.durationMillis?.let {
        val seconds = it / 1000
        "%d:%02d".format(seconds / 60, seconds % 60)
    } ?: unknown
    val description = buildList {
            info.trackTitle?.let { add(context.getString(R.string.audio_track_title, it)) }
            info.artist?.let { add(context.getString(R.string.audio_artist, it)) }
            add(context.getString(R.string.audio_format, info.format?.uppercase() ?: unknown))
            add(
                context.getString(
                    R.string.audio_size,
                    info.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: unknown,
                )
            )
            add(context.getString(R.string.audio_duration, duration))
        }.joinToString("\n")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                context.getString(R.string.audio_information),
                color = LightPurple,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
        }
        PlaybackButton(
            isPlaying = isPlaying,
            progress = playbackProgress,
            context = context,
            onClick = onTogglePlayback,
            compact = true,
        )
    }
}

@Composable
internal fun ResultCard(result: AnalysisResult, audioInfo: SelectedAudioInfo?, context: Context) {
    val percent = (result.probability * 100).toInt()
    val title = when (result.verdict) {
        "synthetic" -> context.getString(R.string.result_synthetic)
        "uncertain" -> context.getString(R.string.result_uncertain)
        else -> context.getString(R.string.result_human)
    }
    val score = context.getString(R.string.synthetic_score, percent) +
        if (result.cached) context.getString(R.string.cached_result) else ""
    val displayAudioName = audioInfo?.name?.let {
        if (it.startsWith("faith-recording-")) {
            context.getString(R.string.microphone_recording_name)
        } else {
            it
        }
    }
    val description = listOfNotNull(
        displayAudioName?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.result_audio_name, it)
        },
        audioInfo?.trackTitle?.let { context.getString(R.string.audio_track_title, it) },
        audioInfo?.artist?.let { context.getString(R.string.audio_artist, it) },
        score,
        if (result.verdict == "uncertain") {
            context.getString(R.string.result_uncertain_hint)
        } else {
            null
        },
    ).joinToString("\n")
    MessageCard(
        title,
        description,
        if (result.verdict == "uncertain") Color(0xFFFFC857) else Purple,
        compact = true,
    )
}

internal fun Throwable.localizedUserMessage(context: Context): String = when (this) {
    is AudioReadException -> context.getString(R.string.error_file_read)
    is ApiServerException -> if (statusCode == 422) {
        context.getString(R.string.error_invalid_audio)
    } else {
        context.getString(R.string.error_server, statusCode)
    }
    is java.io.IOException -> context.getString(R.string.error_network)
    else -> context.getString(R.string.error_unknown)
}

@Composable
internal fun MessageCard(
    title: String,
    description: String,
    accent: Color,
    compact: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(if (compact) 14.dp else 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 15.sp else 18.sp,
            )
            Text(
                description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = if (compact) 13.sp else 14.sp,
            )
        }
    }
}
