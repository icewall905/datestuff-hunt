package dk.datestuff.hunt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class Meta(val id: String = "", val title: String = "")

@Serializable
data class Settings(
    val agent: String = "AGENT",
    val minutes: Int = 0,
    val metaWord: String? = null,
    val metaHint: List<String> = emptyList(),
    val finalMessage: List<String> = emptyList(),
    val endLine: String = "MISSION COMPLETE.",
)

@Serializable
data class Chapter(
    val id: String = "",
    val title: String = "",
    val location: String = "",
    val intro: List<String> = emptyList(),
    val outro: List<String> = emptyList(),
)

@Serializable
data class Unlock(val after: JsonElement? = null)

@Serializable
data class Waypoint(val lat: Double = 0.0, val lng: Double = 0.0, val radius: Double = 25.0, val label: String = "")

@Serializable
data class Reward(val fragment: Boolean = false, val message: String = "")

@Serializable
data class Step(
    val id: String,
    val chapter: String = "",
    val type: String = "",
    val device: String = "terminal",
    val title: String = "",
    val text: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val enabled: Boolean = true,
    val unlock: Unlock? = null,
    val waypoint: Waypoint? = null,
    val reward: Reward? = null,
    val config: JsonObject = JsonObject(emptyMap()),
) {
    fun cfgString(k: String, d: String = ""): String = config[k]?.jsonPrimitive?.contentOrNull ?: d
    fun cfgInt(k: String, d: Int): Int = config[k]?.jsonPrimitive?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: d
    fun cfgDouble(k: String, d: Double): Double = config[k]?.jsonPrimitive?.doubleOrNull ?: d
    fun cfgBool(k: String, d: Boolean): Boolean = config[k]?.jsonPrimitive?.booleanOrNull ?: d
    fun cfgStrings(k: String): List<String> =
        (config[k] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
}

@Serializable
data class Campaign(
    val meta: Meta = Meta(),
    val settings: Settings = Settings(),
    val chapters: List<Chapter> = emptyList(),
    val steps: List<Step> = emptyList(),
)

@Serializable
data class SolvedInfo(val at: Long = 0, val by: String = "", val skipped: Boolean = false)

@Serializable
data class PhotoInfo(val id: String = "", val step: String = "", val ts: Long = 0, val status: String = "pending")

@Serializable
data class Message(val seq: Long = 0, val ts: Long = 0, val to: String = "all", val text: String = "")

@Serializable
data class Session(
    val id: String? = null,
    val campaign: Campaign? = null,
    val state: String = "idle",
    val startedAt: Long = 0,
    val pausedMs: Long = 0,
    val extraMinutes: Double = 0.0,
    val solved: Map<String, SolvedInfo> = emptyMap(),
    val forced: List<String> = emptyList(),
    val hintsUsed: Map<String, Int> = emptyMap(),
    val open: List<String> = emptyList(),
    val fragments: Map<String, String> = emptyMap(),
    val photos: List<PhotoInfo> = emptyList(),
    val messages: List<Message> = emptyList(),
    val seq: Long = 0,
    val elapsedMs: Long = 0,
    val remainingMs: Long? = null,
)

/** Poll/SSE event. Unused fields stay null. */
@Serializable
data class Event(
    val seq: Long = 0,
    val ts: Long = 0,
    val kind: String = "",
    val step: String? = null,
    val by: String? = null,
    val open: List<String>? = null,
    val fragment: String? = null,
    val steps: List<String>? = null,
    val text: String? = null,
    val index: Int? = null,
    val pushed: Boolean? = null,
    val to: String? = null,
    val id: String? = null,
    val status: String? = null,
    val paused: Boolean? = null,
    val extraMinutes: Double? = null,
    val ok: Boolean? = null,
    val device: String? = null,
)

@Serializable
data class PollResult(val seq: Long = 0, val session: Session? = null, val events: List<Event> = emptyList())

@Serializable
data class AnswerResult(val ok: Boolean = false, val locked: Boolean = false, val solved: List<String> = emptyList(), val open: List<String> = emptyList())

@Serializable
data class HintResult(val hint: String = "", val index: Int = 0)

@Serializable
data class PairInfo(val url: String = "", val token: String = "")

/** Inbox entry shown on the HUD. */
@Serializable
data class InboxItem(val ts: Long, val kind: String, val text: String, val step: String? = null)

object PhoneStepType {
    const val GPS = "gps-waypoint"
    const val GEOFENCE = "geofence"
    const val AR_CHEST = "ar-chest"
    const val AR_COLLECT = "ar-collect"
    const val AR_BEACON = "ar-beacon"
    const val QR = "qr-scan"
    const val PHOTO = "photo"
    const val SELFIE = "selfie"
    const val QUESTION = "field-question"
    const val COMPASS = "compass-bearing"
    const val TILT = "tilt-lock"
    const val SPRINT = "sprint"
    const val WALKIE = "walkie"
}

fun parsePairQr(raw: String): PairInfo? {
    if (!raw.startsWith("esc://pair")) return null
    val q = raw.substringAfter('?', "")
    val m = q.split('&').mapNotNull { p ->
        val i = p.indexOf('='); if (i < 0) null else p.substring(0, i) to java.net.URLDecoder.decode(p.substring(i + 1), "UTF-8")
    }.toMap()
    val u = m["u"] ?: return null
    val t = m["t"] ?: return null
    return PairInfo(u, t)
}
