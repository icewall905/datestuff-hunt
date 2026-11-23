package dk.datestuff.hunt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object CommsSpeech {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val ttsBase = "https://tts.tdgs.org/v1"
    private const val preferredVoice = "archer_ref"

    suspend fun transcribe(audio: File): String = runInterruptible(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "Qwen/Qwen3-ASR-1.7B")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", "comms.m4a", audio.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val request = Request.Builder().url("https://stt.tdgs.org/qwen").post(body).build()
        client.newCall(request).execute().use { response ->
            requireSpeechSuccess(response.code, "Dictation")
            val result = response.body.string()
            Json.parseToJsonElement(result).jsonObject["text"]?.jsonPrimitive?.content.orEmpty().trim()
        }
    }

    suspend fun synthesize(text: String, audio: File) = runInterruptible(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", "omnivoice")
            put("voice", archerVoice())
            put("input", text)
            put("response_format", "wav")
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$ttsBase/audio/speech").post(body).build()
        client.newCall(request).execute().use { response ->
            requireSpeechSuccess(response.code, "Archer voice")
            val result = response.body
            if (result.contentType()?.type != "audio") throw IOException("Speech service did not return audio.")
            result.byteStream().use { input -> audio.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun archerVoice(): String {
        val request = Request.Builder().url("$ttsBase/audio/voices").build()
        client.newCall(request).execute().use { response ->
            requireSpeechSuccess(response.code, "Voice list")
            val payload = Json.parseToJsonElement(response.body.string())
            val voices = ((payload as? JsonObject)?.get("voices") ?: payload) as? JsonArray
            for (entry in voices.orEmpty()) {
                val voice = entry as? JsonObject ?: continue
                val names = listOf(voice["id"], voice["name"]) + (voice["aliases"] as? JsonArray).orEmpty()
                if (names.any { (it as? JsonPrimitive)?.contentOrNull == preferredVoice }) return preferredVoice
            }
            throw IOException("Archer voice unavailable. The speech server may be on its fallback backend.")
        }
    }

    private fun requireSpeechSuccess(code: Int, feature: String) {
        if (code in 200..299) return
        val detail = if (code in 400..499) " The speech server may be on its fallback backend." else ""
        throw IOException("$feature unavailable (HTTP $code).$detail")
    }
}
