package ru.faith.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class WavRecorder {
    private val sampleRate = 16_000
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null
    @Volatile private var currentAmplitude = 0f

    fun amplitude(): Float = currentAmplitude

    fun start(context: Context): Boolean {
        if (running.get()) return false
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        releaseRecorder()
        val minimumBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) return false

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minimumBuffer * 2,
            )
        } catch (_: SecurityException) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        val file = File(context.cacheDir, "faith-recording-${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { it.write(ByteArray(WavHeaderSize)) }
        audioRecord = recorder
        outputFile = file
        currentAmplitude = 0f
        running.set(true)
        try {
            recorder.startRecording()
        } catch (_: RuntimeException) {
            running.set(false)
            releaseRecorder()
            return false
        }
        recordingThread = Thread {
            val buffer = ByteArray(minimumBuffer)
            FileOutputStream(file, true).use { output ->
                while (running.get()) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        currentAmplitude = calculateAmplitude(buffer, bytesRead)
                    }
                }
            }
        }.apply {
            name = "faith-wav-recorder"
            start()
        }
        return true
    }

    fun stop(): File? {
        val wasRunning = running.getAndSet(false)
        releaseRecorder()
        return if (wasRunning) outputFile?.also(::writeWavHeader) else null
    }

    private fun releaseRecorder() {
        running.set(false)
        runCatching { audioRecord?.stop() }
        runCatching { recordingThread?.join(1_500) }
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        currentAmplitude = 0f
    }

    private fun calculateAmplitude(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < length) {
            val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)).toShort()
            val normalized = sample.toDouble() / Short.MAX_VALUE
            sum += normalized * normalized
            samples += 1
            index += 2
        }
        if (samples == 0) return 0f
        val rms = kotlin.math.sqrt(sum / samples).toFloat()
        return (rms * 7f).coerceIn(0f, 1f)
    }

    private fun writeWavHeader(file: File) {
        val audioLength = (file.length() - WavHeaderSize).coerceAtLeast(0)
        RandomAccessFile(file, "rw").use { target ->
            target.seek(0)
            target.writeBytes("RIFF")
            target.writeLittleEndianInt((audioLength + 36).toInt())
            target.writeBytes("WAVEfmt ")
            target.writeLittleEndianInt(16)
            target.writeLittleEndianShort(1)
            target.writeLittleEndianShort(1)
            target.writeLittleEndianInt(sampleRate)
            target.writeLittleEndianInt(sampleRate * 2)
            target.writeLittleEndianShort(2)
            target.writeLittleEndianShort(16)
            target.writeBytes("data")
            target.writeLittleEndianInt(audioLength.toInt())
        }
    }

    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
    }

    private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
        write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
    }

    private companion object {
        const val WavHeaderSize = 44
    }
}
