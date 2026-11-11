package dk.datestuff.hunt

/** Optimistic agent message not yet confirmed by the server. */
data class LocalChat(val id: String, val ts: Long, val text: String, val baseSeq: Long = 0, val failed: Boolean = false)

data class ChatLine(
    val seq: Long, val ts: Long, val from: String, val text: String,
    val step: String? = null, val pending: Boolean = false,
    val localId: String? = null, val failed: Boolean = false,
)

/** One COMMS feed row: a chat line or a hint/message note from the inbox. */
data class FeedEntry(val ts: Long, val chat: ChatLine? = null, val note: InboxItem? = null)

/** Union by seq (incoming wins), ordered by seq, capped to the newest [cap]. */
fun mergeServerChat(existing: List<ChatMsg>, incoming: List<ChatMsg>, cap: Int = 300): List<ChatMsg> {
    val m = LinkedHashMap<Long, ChatMsg>()
    for (c in existing) m[c.seq] = c
    for (c in incoming) m[c.seq] = c
    return m.values.sortedBy { it.seq }.takeLast(cap)
}

/** Local messages with no matching server agent message yet. Only server messages newer than the seq seen at send time
 *  match, and each server message matches at most one local. Failed locals never match. */
fun unmatchedLocal(server: List<ChatMsg>, local: List<LocalChat>): List<LocalChat> {
    val used = mutableSetOf<Long>()
    return local.sortedBy { it.ts }.filter { l ->
        val hit = if (l.failed) null else server.firstOrNull { it.from == "agent" && it.seq !in used && it.seq > l.baseSeq && it.text == l.text }
        if (hit != null) { used += hit.seq; false } else true
    }
}

/** Server chat (deduped by seq) followed by still-pending optimistic agent messages. */
fun mergeChat(server: List<ChatMsg>, local: List<LocalChat>): List<ChatLine> {
    val s = mergeServerChat(emptyList(), server)
    return s.map { ChatLine(it.seq, it.ts, it.from, it.text, it.step) } +
        unmatchedLocal(s, local).map { ChatLine(0, it.ts, "agent", it.text, pending = !it.failed, localId = it.id, failed = it.failed) }
}

/** Chat in order with hints (and legacy messages not mirrored into chat) interleaved by time. */
fun buildFeed(chat: List<ChatLine>, inbox: List<InboxItem>): List<FeedEntry> {
    val texts = chat.map { it.text }.toSet()
    val notes = inbox.filter { it.kind == "hint" || it.text !in texts }.sortedBy { it.ts }
    val out = ArrayList<FeedEntry>(chat.size + notes.size)
    var i = 0
    for (c in chat) {
        while (i < notes.size && notes[i].ts <= c.ts) { out += FeedEntry(notes[i].ts, note = notes[i]); i++ }
        out += FeedEntry(c.ts, chat = c)
    }
    while (i < notes.size) { out += FeedEntry(notes[i].ts, note = notes[i]); i++ }
    return out
}
