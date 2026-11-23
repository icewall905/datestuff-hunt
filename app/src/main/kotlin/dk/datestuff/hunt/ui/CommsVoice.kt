package dk.datestuff.hunt.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.datestuff.hunt.CommsSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

internal class CommsVoice(private val context: Context, private val scope: CoroutineScope) {
    private val preferences = context.getSharedPreferences("comms_voice", Context.MODE_PRIVATE)
    private val pending = ArrayDeque<String>()
    var autoRead by mutableStateOf(preferences.getBoolean("auto_read", true))
        private set
    var recording by mutableStateOf(false)
        private set
    var transcribing by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var player: MediaPlayer? = null
    private var playbackFile: File? = null
    private var recordingTimeout: Job? = null
    private var transcription: Job? = null
    private var synthesis: Job? = null

    @Suppress("DEPRECATION")
    fun startRecording(onTranscript: (String) -> Unit) {
        if (recording || transcribing) return
        stopPlayback()
        status = null
        try {
            val file = File.createTempFile("comms-dictation-", ".m4a", context.cacheDir)
            recordingFile = file
            val mic = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder = mic
            mic.setAudioSource(MediaRecorder.AudioSource.MIC)
            mic.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mic.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mic.setAudioChannels(1)
            mic.setAudioSamplingRate(16000)
            mic.setOutputFile(file.absolutePath)
            mic.setMaxDuration(31_000)
            mic.setOnInfoListener { source, what, _ ->
                if (source === recorder && what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    finishRecording(onTranscript)
                }
            }
            mic.setOnErrorListener { source, _, _ ->
                if (source === recorder) {
                    discardRecording()
                    status = "Recording failed. You can still type."
                }
            }
            mic.prepare()
            mic.start()
            recording = true
            recordingTimeout = scope.launch {
                delay(30_000)
                finishRecording(onTranscript)
            }
        } catch (_: Exception) {
            discardRecording()
            status = "Could not start the microphone."
        }
    }

    fun finishRecording(onTranscript: (String) -> Unit) {
        val mic = recorder ?: return
        val file = recordingFile ?: return
        recordingTimeout?.cancel()
        recordingTimeout = null
        recorder = null
        recordingFile = null
        recording = false
        try {
            mic.stop()
        } catch (_: RuntimeException) {
            file.delete()
            status = "Recording was too short or interrupted. Try again."
            return
        } finally {
            mic.release()
        }
        transcribing = true
        transcription = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val text = CommsSpeech.transcribe(file)
                if (text.isEmpty()) status = "No speech detected." else onTranscript(text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = if (error is IOException) error.message ?: "Transcription unavailable." else "Transcription unavailable."
            } finally {
                file.delete()
                transcribing = false
                playNext()
            }
        }
    }

    fun setAutoReadEnabled(enabled: Boolean) {
        autoRead = enabled
        preferences.edit().putBoolean("auto_read", enabled).apply()
        if (!enabled) stopPlayback()
    }

    fun enqueue(text: String) {
        if (!autoRead || text.isBlank()) return
        if (pending.size == 10) pending.removeFirst()
        pending.addLast(text)
        playNext()
    }

    fun listen(text: String) {
        if (recording || transcribing || speaking || text.isBlank()) return
        play(text)
    }

    private fun playNext() {
        if (recording || transcribing || speaking || pending.isEmpty()) return
        play(pending.removeFirst())
    }

    private fun play(text: String) {
        status = null
        speaking = true
        synthesis = scope.launch {
            try {
                val file = File.createTempFile("comms-playback-", ".wav", context.cacheDir)
                try {
                    CommsSpeech.synthesize(text, file)
                    val audio = MediaPlayer()
                    playbackFile = file
                    player = audio
                    audio.setDataSource(file.absolutePath)
                    audio.setOnPreparedListener { if (it === player) it.start() }
                    audio.setOnCompletionListener {
                        if (it === player) {
                            releasePlayer()
                            playNext()
                        }
                    }
                    audio.setOnErrorListener { failed, _, _ ->
                        if (failed === player) playbackFailed("Could not play the Archer voice.")
                        true
                    }
                    audio.prepareAsync()
                } finally {
                    if (playbackFile !== file) file.delete()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                playbackFailed(error.message ?: "Archer voice unavailable.")
            } catch (_: Exception) {
                playbackFailed("Archer voice unavailable.")
            }
        }
    }

    private fun playbackFailed(message: String) {
        stopPlayback()
        status = message
    }

    fun stopPlayback() {
        pending.clear()
        synthesis?.cancel()
        synthesis = null
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playbackFile?.delete()
        playbackFile = null
        speaking = false
    }

    fun cancelDictation() {
        discardRecording()
        transcription?.cancel()
    }

    fun close() {
        stopPlayback()
        cancelDictation()
    }

    private fun discardRecording() {
        recordingTimeout?.cancel()
        recordingTimeout = null
        recorder?.release()
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        recording = false
    }
}
