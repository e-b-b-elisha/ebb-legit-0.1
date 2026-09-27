package com.ebb.jarvis.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface VoiceEvent {
    data object Listening : VoiceEvent
    data class Amplitude(val level: Float) : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Failed(val reason: String) : VoiceEvent
}

/**
 * Ears and voice. [listen] is a cold flow: collect it to start a single recognition
 * turn, cancel the collection to abort. [speak] suspends until the utterance finishes
 * so a reply and the next listen turn never talk over each other.
 */
class VoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null

    /** Android requires SpeechRecognizer to be built and driven from the main thread. */
    fun listen(): Flow<VoiceEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceEvent.Failed("No speech recognition service on this device"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var spoken = false

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceEvent.Listening)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                // Raw dB is roughly -2..10; normalise for the reactor animation.
                trySend(VoiceEvent.Amplitude(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                // A no-match after a good final result is noise, not a failure.
                if (!spoken) trySend(VoiceEvent.Failed(describe(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) {
                    spoken = true
                    trySend(VoiceEvent.Final(text))
                } else {
                    trySend(VoiceEvent.Failed("Nothing caught"))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { trySend(VoiceEvent.Partial(it)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching { recognizer.startListening(intent) }
            .onFailure {
                trySend(VoiceEvent.Failed(it.message ?: "Recogniser refused to start"))
                close()
            }

        awaitClose {
            runCatching {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

    suspend fun speak(text: String) {
        if (text.isBlank()) return
        val engine = ensureTts() ?: return
        val done = CompletableDeferred<Unit>()
        val id = "jarvis-" + System.nanoTime()

        withContext(Dispatchers.Main.immediate) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) done.complete(Unit)
                }

                @Deprecated("Superseded by onError(String, int)", ReplaceWith("onError"))
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) done.complete(Unit)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) done.complete(Unit)
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        done.await()
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    private suspend fun ensureTts(): TextToSpeech? {
        ttsReady?.let { pending ->
            return if (pending.await()) tts else null
        }
        val gate = CompletableDeferred<Boolean>()
        ttsReady = gate

        val engine = withContext(Dispatchers.Main.immediate) {
            TextToSpeech(context) { status ->
                gate.complete(status == TextToSpeech.SUCCESS)
            }
        }
        tts = engine

        val ok = gate.await()
        if (!ok) {
            Log.w(TAG, "Text-to-speech unavailable")
            return null
        }
        runCatching {
            // Slightly under pitch, slightly over rate: composed rather than chirpy.
            engine.language = Locale.UK
            engine.setPitch(0.92f)
            engine.setSpeechRate(1.04f)
        }
        return engine
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = null
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed"
        SpeechRecognizer.ERROR_CLIENT -> "Recogniser stopped"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unreachable"
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing caught"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Recognition failed ($error)"
    }

    private companion object {
        const val TAG = "VoiceEngine"
    }
}
