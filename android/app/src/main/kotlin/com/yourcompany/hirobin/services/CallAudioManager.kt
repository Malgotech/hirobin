package com.yourcompany.hirobin.services

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.telecom.Connection
import android.util.Log
import com.yourcompany.hirobin.CallEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

object CallAudioManager {

    private const val TAG = "CallAudioManager"
    private var wsBaseUrl = "ws://0.tcp.in.ngrok.io:27365"

    // Capture format — 16 kHz mono PCM-16 matches common STT model input requirements.
    private const val CAPTURE_SAMPLE_RATE = 16_000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val CHUNK_MS = 100

    // Playback format — must match whatever sample rate the backend TTS sends.
    private const val TTS_SAMPLE_RATE = 24_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var activeConnection: Connection? = null

    // OkHttpClient is reused across calls; readTimeout(0) keeps the socket open indefinitely.
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var ttsTrack: AudioTrack? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun setBackendUrl(url: String) {
        wsBaseUrl = url
    }

    // Called from HiRobinInCallService — no Connection object available there.
    fun startStreaming(audioManager: AudioManager) = startStreaming(null, audioManager)

    fun startStreaming(connection: Connection?, audioManager: AudioManager) {
        if (captureJob?.isActive == true) return

        activeConnection = connection
        this.audioManager = audioManager

        requestAudioFocus(audioManager)
        initTtsTrack()
        connectWebSocket()
        startCapture()
    }

    fun stopStreaming() {
        captureJob?.cancel()
        captureJob = null

        webSocket?.close(1000, "Call ended")
        webSocket = null

        ttsTrack?.stop()
        ttsTrack?.release()
        ttsTrack = null

        abandonAudioFocus()
        activeConnection = null
    }

    // -------------------------------------------------------------------------
    // WebSocket
    // -------------------------------------------------------------------------

    private fun connectWebSocket() {
        val request = Request.Builder().url("$wsBaseUrl/ws?userId=default").build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected: ${response.code}")
                CallEventBus.onAudioStateChanged("ws_connected", null)
            }

            // Binary frame = TTS PCM audio from the backend; write directly to AudioTrack.
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                Log.d(TAG, "TTS audio chunk: ${bytes.size} bytes")
                val pcm = bytes.toByteArray()
                ttsTrack?.write(pcm, 0, pcm.size)
            }

            // Text frame = JSON control message from the backend.
            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "JSON frame: $text")
                handleJsonFrame(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                CallEventBus.onAudioStateChanged("ws_error", t.message)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
            }
        })
    }

    private fun handleJsonFrame(text: String) {
        try {
            val json = JSONObject(text)
            when (val type = json.optString("type")) {
                // Deepgram STT — interim or final transcript
                "transcript" -> {
                    val transcript = json.optString("text")
                    val isFinal = json.optBoolean("is_final", false)
                    Log.i(TAG, "[Deepgram] transcript (final=$isFinal): \"$transcript\"")
                }
                // LLM — streaming token or completed response
                "llm_token" -> {
                    Log.i(TAG, "[LLM] token: \"${json.optString("text")}\"")
                }
                "llm_response" -> {
                    Log.i(TAG, "[LLM] response complete: \"${json.optString("text")}\"")
                }
                // ElevenLabs TTS — synthesis started or done (audio arrives as binary frames)
                "tts_start" -> {
                    Log.i(TAG, "[ElevenLabs] TTS synthesis started")
                }
                "turn_end" -> {
                    Log.i(TAG, "[Pipeline] turn_end — AI finished speaking")
                    CallEventBus.onAudioStateChanged("turn_end", null)
                }
                "error" -> {
                    val msg = json.optString("message")
                    Log.e(TAG, "[Pipeline] error: $msg")
                    CallEventBus.onAudioStateChanged("ws_error", msg)
                }
                else -> Log.d(TAG, "Unknown frame type=\"$type\": $text")
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Malformed JSON frame: $text", e)
        }
    }

    // -------------------------------------------------------------------------
    // Audio capture → WebSocket
    // -------------------------------------------------------------------------

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(CAPTURE_SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val chunkSize = CAPTURE_SAMPLE_RATE * 2 * CHUNK_MS / 1000  // bytes: 16-bit = 2 bytes/sample
        val bufferSize = maxOf(minBuf, chunkSize * 4)

        // MIC captures the acoustic loopback: HiRobinInCallService routes the caller's
        // voice to the speakerphone so the microphone picks up both sides of the call.
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, CAPTURE_SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize)
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord(MIC) threw ${e.javaClass.simpleName}", e)
            CallEventBus.onAudioStateChanged("error", "AudioRecord init threw: ${e.message}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            CallEventBus.onAudioStateChanged("error", "AudioRecord(MIC) failed to initialise — state=${recorder.state}")
            return
        }

        Log.d(TAG, "AudioRecord ready — source=MIC, sampleRate=$CAPTURE_SAMPLE_RATE Hz, " +
                "encoding=${if (ENCODING == AudioFormat.ENCODING_PCM_16BIT) "PCM_16BIT" else ENCODING}, " +
                "chunkSize=$chunkSize bytes, state=${recorder.state}")

        audioRecord = recorder
        recorder.startRecording()
        Log.d(TAG, "AudioRecord started — recordingState=${recorder.recordingState}")
        CallEventBus.onAudioStateChanged("started", null)

        captureJob = scope.launch {
            val buffer = ByteArray(chunkSize)
            var firstChunkLogged = false
            try {
                while (isActive) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        val chunk = buffer.copyOf(bytesRead)

                        if (!firstChunkLogged) {
                            val hex = chunk.take(32).joinToString(" ") { "%02X".format(it) }
                            Log.d(TAG, "First PCM chunk — $bytesRead bytes, first 32 bytes (hex): $hex")
                            firstChunkLogged = true
                        }

                        // Send raw PCM to the backend as a binary WebSocket frame.
                        webSocket?.send(chunk.toByteString())

                        val rms = rms(chunk)
                        withContext(Dispatchers.Main) {
                            CallEventBus.onAudioLevel(rms)
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
                audioRecord = null
                withContext(Dispatchers.Main) {
                    CallEventBus.onAudioStateChanged("stopped", null)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // TTS playback ← WebSocket binary frames
    // -------------------------------------------------------------------------

    private fun initTtsTrack() {
        val minBuf = AudioTrack.getMinBufferSize(
            TTS_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            ENCODING
        )
        ttsTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(TTS_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf * 4)
            .build()
        ttsTrack?.play()
        Log.d(TAG, "AudioTrack ready — sampleRate=$TTS_SAMPLE_RATE Hz, state=${ttsTrack?.state}")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // RMS amplitude in [0.0, 1.0] for the Flutter mic-level indicator.
    private fun rms(pcm: ByteArray): Float {
        var sum = 0.0
        var i = 0
        while (i < pcm.size - 1) {
            val sample = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
            i += 2
        }
        val count = pcm.size / 2
        if (count == 0) return 0f
        return (sqrt(sum / count) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    private fun requestAudioFocus(audioManager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener {}
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        focusRequest = null
        audioManager = null
    }
}
