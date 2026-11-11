package dk.datestuff.hunt

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/** Thin blocking client; call from Dispatchers.IO. */
class Api(baseUrl: String, private val token: String) {
    private val base = baseUrl.trim().trimEnd('/')
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val pollHttp = http.newBuilder().readTimeout(35, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder().url(base + path).header("Authorization", "Bearer $token")

    private fun exec(client: OkHttpClient, r: Request): String {
        client.newCall(r).execute().use { resp ->
            val body = resp.body.string()
            if (!resp.isSuccessful) {
                val msg = runCatching { AppJson.parseToJsonElement(body).let { (it as kotlinx.serialization.json.JsonObject)["error"].toString() } }.getOrDefault(body)
                throw ApiException(resp.code, msg)
            }
            return body
        }
    }

    private fun post(path: String, json: String, client: OkHttpClient = http): String =
        exec(client, req(path).post(json.toRequestBody(jsonType)).build())

    fun valid(): Boolean = base.toHttpUrlOrNull() != null

    fun session(): Session = AppJson.decodeFromString(exec(http, req("/api/session").build()))

    fun poll(since: Long): PollResult =
        AppJson.decodeFromString(exec(pollHttp, req("/api/device/poll?since=$since").build()))

    fun answer(step: String, answer: String): AnswerResult {
        val b = buildJsonObject { put("step", step); put("answer", answer); put("device", "phone") }
        return AppJson.decodeFromString(post("/api/answer", b.toString()))
    }

    fun solve(step: String): AnswerResult {
        return AppJson.decodeFromString(post("/api/solve", buildJsonObject { put("step", step); put("device", "phone") }.toString()))
    }

    fun hint(step: String): HintResult =
        AppJson.decodeFromString(post("/api/hint", buildJsonObject { put("step", step) }.toString()))

    fun chat(text: String): ChatResult =
        AppJson.decodeFromString(post("/api/device/chat", buildJsonObject { put("text", text) }.toString()))

    fun location(lat: Double, lng: Double, acc: Float, heading: Float?, ts: Long) {
        val b = buildJsonObject {
            put("lat", lat); put("lng", lng); put("acc", acc.toDouble()); put("ts", ts)
            if (heading != null) put("heading", heading.toDouble())
        }
        post("/api/device/location", b.toString())
    }

    fun photo(step: String, mime: String, base64: String) {
        post("/api/device/photo", buildJsonObject { put("step", step); put("mime", mime); put("data", base64) }.toString())
    }

    /** Verifies connectivity + token; returns true if the session endpoint answers. */
    fun ping(): Boolean = try { session(); true } catch (e: Exception) { false }

    companion object {
        @Suppress("unused") fun encode(s: Session) = AppJson.encodeToString(s)
    }
}
