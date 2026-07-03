package com.litter.android.state

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.litter.android.util.LLog
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.AppVoiceTranscriptionRequest

private const val DEFAULT_DEVICE_SAMPLE_RATE = 44100
private const val TRANSCRIPTION_SAMPLE_RATE = 24000
private const val MIN_DURATION_SECONDS = 0.5f
private const val RECORDING_THREAD_JOIN_MS = 1000L
private const val AUDIO_LEVEL_GAIN = 3.0

/**
 * Records microphone input and asks the paired desktop daemon to transcribe it.
 */
class VoiceTranscriptionManager {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private val buffers = mutableListOf<ShortArray>()
    private var deviceSampleRate = DEFAULT_DEVICE_SAMPLE_RATE
    private var recordingThread: Thread? = null

    fun hasMicPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(context: Context) {
        if (_isRecording.value) return
        if (!hasMicPermission(context)) {
            _error.value = "需要麦克风权限"
            return
        }

        buffers.clear()
        _error.value = null
        deviceSampleRate = DEFAULT_DEVICE_SAMPLE_RATE

        val bufferSize = AudioRecord.getMinBufferSize(
            deviceSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            deviceSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2,
        )
        audioRecord?.startRecording()
        _isRecording.value = true

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (_isRecording.value) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(buffers) {
                        buffers.add(buffer.copyOfRange(0, read))
                    }
                    _audioLevel.value = rms(buffer, read)
                }
            }
        }.also { it.start() }
    }

    suspend fun stopAndTranscribe(
        appModel: AppModel,
        options: VoiceTranscriptionOptions,
    ): String? {
        stopRecorder()
        val allSamples = drainSamples()
        val durationSec = allSamples.size.toFloat() / deviceSampleRate
        if (durationSec < MIN_DURATION_SECONDS) {
            _error.value = "录音时间太短"
            return null
        }

        val wav = encodeWav(
            resample(allSamples, deviceSampleRate, TRANSCRIPTION_SAMPLE_RATE),
            TRANSCRIPTION_SAMPLE_RATE,
        )
        _isTranscribing.value = true
        return try {
            withContext(Dispatchers.IO) {
                LLog.i(
                    "VoiceTranscription",
                    "transcription request",
                    fields = mapOf(
                        "serverId" to options.serverId,
                        "runtime" to options.agentRuntimeKind,
                        "model" to options.asrModel,
                        "bytes" to wav.size,
                    ),
                )
                appModel.transcribeVoice(options, voiceRequest(wav, options))
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("语音识别结果为空")
            }
        } catch (e: Exception) {
            LLog.e(
                "VoiceTranscription",
                "transcription failed",
                e,
                fields = mapOf("serverId" to options.serverId),
            )
            _error.value = e.message
            null
        } finally {
            _isTranscribing.value = false
        }
    }

    fun cancelRecording() {
        _isRecording.value = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        buffers.clear()
        _audioLevel.value = 0f
    }

    private fun stopRecorder() {
        _isRecording.value = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join(RECORDING_THREAD_JOIN_MS)
        recordingThread = null
        _audioLevel.value = 0f
    }

    private fun drainSamples(): ShortArray {
        synchronized(buffers) {
            val allSamples = ShortArray(buffers.sumOf { it.size })
            var offset = 0
            for (buffer in buffers) {
                buffer.copyInto(allSamples, offset)
                offset += buffer.size
            }
            buffers.clear()
            return allSamples
        }
    }

    private fun voiceRequest(
        wav: ByteArray,
        options: VoiceTranscriptionOptions,
    ): AppVoiceTranscriptionRequest {
        return AppVoiceTranscriptionRequest(
            audioBytes = wav,
            mimeType = "audio/wav",
            fileName = "audio.wav",
            model = options.asrModel?.trim()?.takeIf { it.isNotEmpty() },
            language = transcriptionLanguage(options.language),
            agentRuntimeKind = options.agentRuntimeKind?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun rms(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble() / Short.MAX_VALUE
            sum += sample * sample
        }
        return (sqrt(sum / size) * AUDIO_LEVEL_GAIN).coerceAtMost(1.0).toFloat()
    }

    private fun resample(input: ShortArray, inputRate: Int, outputRate: Int): ShortArray {
        if (inputRate == outputRate) return input
        val ratio = inputRate.toDouble() / outputRate
        val output = ShortArray((input.size / ratio).toInt())
        for (i in output.indices) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex
            val s0 = input[srcIndex.coerceAtMost(input.size - 1)]
            val s1 = input[(srcIndex + 1).coerceAtMost(input.size - 1)]
            output[i] = (s0 + frac * (s1 - s0)).toInt().toShort()
        }
        return output
    }

    private fun encodeWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val output = ByteArrayOutputStream(44 + dataSize)
        val data = DataOutputStream(output)
        data.writeBytes("RIFF")
        data.writeIntLE(36 + dataSize)
        data.writeBytes("WAVE")
        data.writeBytes("fmt ")
        data.writeIntLE(16)
        data.writeShortLE(1)
        data.writeShortLE(1)
        data.writeIntLE(sampleRate)
        data.writeIntLE(sampleRate * 2)
        data.writeShortLE(2)
        data.writeShortLE(16)
        data.writeBytes("data")
        data.writeIntLE(dataSize)
        val buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) buffer.putShort(sample)
        data.write(buffer.array())
        return output.toByteArray()
    }

    private fun transcriptionLanguage(language: String?): String {
        val configured = language?.trim()?.takeIf { it.isNotEmpty() }
        if (configured != null) return configured
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "zh" -> "zh"
            else -> "en"
        }
    }

    private fun DataOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }
}
