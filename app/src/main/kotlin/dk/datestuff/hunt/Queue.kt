package dk.datestuff.hunt

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
data class Pending(
    val id: String,
    val kind: String,          // answer | solve | photo | chat (text in `answer`)
    val step: String,
    val answer: String? = null,
    val photoFile: String? = null,
    val attempts: Int = 0,
    val sessionId: String? = null,
    val ts: Long = 0,
) {
    fun belongsTo(activeSessionId: String?): Boolean = sessionId != null && sessionId == activeSessionId
}

fun pendingSolved(items: List<Pending>, sessionId: String?): Set<String> =
    items.filter { it.belongsTo(sessionId) && it.kind in setOf("answer", "solve") }.map { it.step }.toSet()


/** Persistent FIFO of uploads waiting for connectivity. */
class PendingQueue(private val file: File, private val photoDir: File) {
    private val ser = ListSerializer(Pending.serializer())
    private var items: MutableList<Pending> = load()

    private fun load(): MutableList<Pending> = runCatching {
        if (file.exists()) AppJson.decodeFromString(ser, file.readText()).toMutableList() else mutableListOf()
    }.getOrDefault(mutableListOf())

    @Synchronized private fun save() { Store.writeText(file, AppJson.encodeToString(ser, items)) }

    @Synchronized fun add(p: Pending) { items.add(p); save() }
    @Synchronized fun snapshot(): List<Pending> = items.toList()
    @Synchronized fun size() = items.size
    @Synchronized fun remove(id: String) {
        items.firstOrNull { it.id == id }?.photoFile?.let { File(photoDir, it).delete() }
        items.removeAll { it.id == id }; save()
    }
    @Synchronized fun bump(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(attempts = items[i].attempts + 1); save() }
    }
    fun photoPath(name: String) = File(photoDir, name)
}

/** Exponential backoff with cap: 1,2,4,8,15 s. */
fun backoffMs(attempt: Int): Long = (1000L shl attempt.coerceIn(0, 4)).coerceAtMost(15000L)
