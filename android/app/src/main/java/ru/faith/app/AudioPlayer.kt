package ru.faith.app

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri

class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var currentUri: Uri? = null
    private var prepared = false

    fun toggle(uri: Uri, onPlayingChanged: (Boolean) -> Unit): Boolean = runCatching {
        val active = player
        if (active != null && currentUri == uri && prepared) {
            if (active.isPlaying) {
                active.pause()
                onPlayingChanged(false)
            } else {
                active.start()
                onPlayingChanged(true)
            }
            return true
        }

        stop(onPlayingChanged)
        currentUri = uri
        prepared = false
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setVolume(1f, 1f)
            setDataSource(context, uri)
            setOnPreparedListener {
                prepared = true
                it.start()
                onPlayingChanged(true)
            }
            setOnCompletionListener {
                it.seekTo(0)
                onPlayingChanged(false)
            }
            setOnErrorListener { _, _, _ ->
                stop(onPlayingChanged)
                true
            }
            prepareAsync()
        }
        true
    }.getOrElse {
        stop(onPlayingChanged)
        false
    }

    fun stop(onPlayingChanged: (Boolean) -> Unit = {}) {
        player?.let { current ->
            runCatching { if (current.isPlaying) current.stop() }
            current.release()
        }
        player = null
        currentUri = null
        prepared = false
        onPlayingChanged(false)
    }

    fun progress(): Float = runCatching {
        val active = player ?: return 0f
        if (!prepared || active.duration <= 0) return 0f
        (active.currentPosition.toFloat() / active.duration.toFloat()).coerceIn(0f, 1f)
    }.getOrDefault(0f)
}
