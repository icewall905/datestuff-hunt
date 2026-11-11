package dk.datestuff.hunt

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

data class LatLng(val lat: Double, val lng: Double, val acc: Float = 0f, val heading: Float? = null)

data class HuntState(
    val pair: PairInfo? = null,
    val session: Session? = null,
    val localSolved: Set<String> = emptySet(),
    val inbox: List<InboxItem> = emptyList(),
    val online: Boolean = true,
    val pending: Int = 0,
    val receivedAt: Long = 0,        // SystemClock.elapsedRealtime() of the session snapshot
) {
    val campaign get() = session?.campaign
    val running get() = session?.state == "running" || session?.state == "paused"
    val solvedIds: Set<String> get() = (session?.solved?.keys ?: emptySet()) + localSolved
    val openIds: List<String>
        get() {
            val c = campaign ?: return emptyList()
            return if (!running) emptyList() else Game.openSteps(c, solvedIds, session?.forced?.toSet() ?: emptySet())
        }
    val openPhoneSteps: List<Step> get() = campaign?.steps?.filter { it.id in openIds && it.device == "phone" } ?: emptyList()
    fun step(id: String): Step? = campaign?.steps?.firstOrNull { it.id == id }
    fun fragments(): Map<String, String> {
        val s = session ?: return emptyMap()
        return s.fragments.ifEmpty { s.campaign?.let { Game.fragments(it) } ?: emptyMap() }
    }
    /** Fragments earned so far, in step order: (stepId, letters). */
    fun collectedFragments(): List<Pair<String, String>> {
        val f = fragments(); val solved = solvedIds
        return campaign?.steps?.filter { it.id in solved && f.containsKey(it.id) }?.map { it.id to f.getValue(it.id) } ?: emptyList()
    }
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long? {
        val s = session ?: return null
        val r = s.remainingMs ?: return null
        return if (s.state == "running") r - (now - receivedAt) else r
    }
}

sealed interface UiEvent {
    data class Solved(val step: String, val fragment: String?, val by: String?) : UiEvent
    data class Opened(val steps: List<String>) : UiEvent
    data class Inbox(val item: InboxItem) : UiEvent
    data class Photo(val step: String, val status: String) : UiEvent
    data class Paused(val paused: Boolean) : UiEvent
    data object Locked : UiEvent
    data object Done : UiEvent
    data class Approach(val step: String, val arrived: Boolean) : UiEvent
}

sealed interface AnswerOutcome {
    data object Correct : AnswerOutcome
    data object Wrong : AnswerOutcome
    data object Locked : AnswerOutcome
    data object Error : AnswerOutcome
}

object HuntRepo {
    private lateinit var queue: PendingQueue
    private lateinit var sessionFile: File
    private lateinit var inboxFile: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var api: Api? = null
    @Volatile private var validatedApi: Api? = null
    private var lastSeq = 0L
    private var baseline = -1L

    private val _state = MutableStateFlow(HuntState())
    val state: StateFlow<HuntState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    private val _location = MutableStateFlow<LatLng?>(null)
    val location: StateFlow<LatLng?> = _location.asStateFlow()

    private val notified = mutableMapOf<String, Int>() // step -> 1 approach, 2 arrived
    private val inboxSer = ListSerializer(InboxItem.serializer())

    fun init(ctx: Context) {
        if (this::sessionFile.isInitialized) return
        val app = ctx.applicationContext
        sessionFile = File(app.filesDir, "session.json")
        inboxFile = File(app.filesDir, "inbox.json")
        val photos = File(app.filesDir, "photos").apply { mkdirs() }
        queue = PendingQueue(File(app.filesDir, "queue.json"), photos)
        val cached = runCatching { AppJson.decodeFromString<Session>(sessionFile.readText()) }.getOrNull()
        val inbox = runCatching { AppJson.decodeFromString(inboxSer, inboxFile.readText()) }.getOrDefault(emptyList())
        _state.update { it.copy(session = cached, inbox = inbox, localSolved = pendingSolved(queue.snapshot(), cached?.id), pending = queue.size(), receivedAt = SystemClock.elapsedRealtime()) }
    }

    fun setPair(p: PairInfo?) {
        if (p == _state.value.pair && pollJob?.isActive == true) return
        pollJob?.cancel()
        validatedApi = null
        _state.update { it.copy(pair = p) }
        api = p?.let { Api(it.url, it.token) }
        lastSeq = 0; baseline = -1
        if (api != null) startPolling()
    }

    private fun startPolling() {
        pollJob = scope.launch {
            var fails = 0
            while (isActive) {
                val a = api ?: break
                try {
                    val r = a.poll(lastSeq)
                    fails = 0
                    if (api !== a) continue
                    handlePoll(r)
                    validatedApi = a
                    _state.update { it.copy(online = true) }
                    flushQueue()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    fails++
                    _state.update { it.copy(online = false) }
                    delay(backoffMs(fails))
                    flushQueue()
                }
            }
        }
    }

    private suspend fun handlePoll(r: PollResult) {
        val fresh = baseline >= 0
        if (baseline < 0) baseline = r.seq
        val prev = _state.value
        val s = r.session
        if (s != null) {
            runCatching { Store.writeText(sessionFile, AppJson.encodeToString(Session.serializer(), s)) }
            // New session id => forget local progress/notifications.
            val reset = prev.session?.id != s.id
            if (reset) { notified.clear() }
            val serverSolved = s.solved.keys
            _state.update {
                it.copy(session = s, localSolved = if (reset) emptySet() else it.localSolved - serverSolved,
                    receivedAt = SystemClock.elapsedRealtime())
            }
            mergeMessages(s)
        }
        for (e in r.events) {
            if (fresh) {
                when (e.kind) {
                    "solved" -> _events.emit(UiEvent.Solved(e.step ?: "", e.fragment, e.by))
                    "opened" -> _events.emit(UiEvent.Opened(e.steps ?: emptyList()))
                    "photo" -> if (e.step != null) _events.emit(UiEvent.Photo(e.step, e.status ?: ""))
                    "paused" -> _events.emit(UiEvent.Paused(e.paused == true))
                    "done" -> _events.emit(UiEvent.Done)
                }
            }
            if (e.kind == "hint" && e.text != null && e.pushed != false) addInbox("h${e.seq}", InboxItem(e.ts, "hint", e.text, e.step), notify = fresh)
            if (e.kind == "message" && e.text != null && (e.to == "all" || e.to == "phone")) addInbox("m${e.seq}", InboxItem(e.ts, "message", e.text), notify = fresh)
        }
        lastSeq = maxOf(lastSeq, r.seq)
    }

    private val seenKeys = mutableSetOf<String>()
    private fun mergeMessages(s: Session) {
        for (m in s.messages) if (m.to == "all" || m.to == "phone") addInbox("m${m.seq}", InboxItem(m.ts, "message", m.text), notify = false)
    }

    private fun addInbox(key: String, item: InboxItem, notify: Boolean) {
        synchronized(seenKeys) { if (!seenKeys.add(key)) return }
        if (_state.value.inbox.any { it.text == item.text && it.ts == item.ts }) return
        _state.update { st -> st.copy(inbox = (st.inbox + item).sortedBy { it.ts }.takeLast(200)) }
        runCatching { Store.writeText(inboxFile, AppJson.encodeToString(inboxSer, _state.value.inbox)) }
        if (notify) _events.tryEmit(UiEvent.Inbox(item))
    }

    // ---- actions ----
    suspend fun requestHint(step: String): String? = withContext(Dispatchers.IO) {
        try {
            val h = api?.hint(step) ?: return@withContext null
            addInbox("hr${step}${h.index}", InboxItem(System.currentTimeMillis(), "hint", h.hint, step), notify = false)
            h.hint
        } catch (e: Exception) { null }
    }

    suspend fun answer(step: Step, text: String): AnswerOutcome = withContext(Dispatchers.IO) {
        if (step.id !in _state.value.openIds) return@withContext AnswerOutcome.Locked
        val missionId = _state.value.session?.id
        try {
            val actionApi = api?.takeIf { it === validatedApi } ?: throw java.io.IOException("Waiting for mission validation")
            val r = actionApi.answer(step.id, text)
            if (r.ok) _state.update { if (it.session?.id == missionId) it.copy(localSolved = it.localSolved + step.id) else it }
            if (r.locked) AnswerOutcome.Locked else if (r.ok) AnswerOutcome.Correct else AnswerOutcome.Wrong
        } catch (e: ApiException) {
            AnswerOutcome.Error
        } catch (e: Exception) {
            // offline: the frozen campaign carries the answers, so decide locally and queue the report
            if (Game.check(step, text)) {
                queue.add(Pending(UUID.randomUUID().toString(), "answer", step.id, answer = text, sessionId = missionId))
                _state.update { if (it.session?.id == missionId) it.copy(localSolved = it.localSolved + step.id, pending = queue.size()) else it }
                AnswerOutcome.Correct
            } else AnswerOutcome.Wrong
        }
    }

    fun solve(step: String) {
        if (step in _state.value.solvedIds) return
        if (step !in _state.value.openIds) { _events.tryEmit(UiEvent.Locked); return }
        val missionId = _state.value.session?.id
        val actionApi = api?.takeIf { it === validatedApi }
        _state.update { if (it.session?.id == missionId) it.copy(localSolved = it.localSolved + step) else it }
        scope.launch {
            try {
                val result = (actionApi ?: throw java.io.IOException("Waiting for mission validation")).solve(step)
                if (result?.ok != true) {
                    _state.update { if (it.session?.id == missionId) it.copy(localSolved = it.localSolved - step) else it }
                    if (result?.locked == true) _events.emit(UiEvent.Locked)
                }
            } catch (e: ApiException) { _state.update { if (it.session?.id == missionId) it.copy(localSolved = it.localSolved - step) else it } }
            catch (e: Exception) {
                queue.add(Pending(UUID.randomUUID().toString(), "solve", step, sessionId = missionId))
                _state.update { it.copy(pending = queue.size()) }
            }
        }
    }

    fun submitPhoto(step: String, jpeg: ByteArray) {
        val missionId = _state.value.session?.id
        scope.launch {
            val name = UUID.randomUUID().toString() + ".jpg"
            queue.photoPath(name).writeBytes(jpeg)
            queue.add(Pending(UUID.randomUUID().toString(), "photo", step, photoFile = name, sessionId = missionId))
            _state.update { it.copy(pending = queue.size()) }
            flushQueue()
        }
    }

    private val flushLock = Any()
    private fun flushQueue() {
        val a = api ?: return
        if (validatedApi !== a) return
        synchronized(flushLock) {
            for (p in queue.snapshot()) {
                if (api !== a || validatedApi !== a) break
                if (!p.belongsTo(_state.value.session?.id)) { queue.remove(p.id); continue }
                try {
                    when (p.kind) {
                        "answer", "solve" -> {
                            val result = if (p.kind == "answer") a.answer(p.step, p.answer ?: "") else a.solve(p.step)
                            if (!result.ok) {
                                _state.update { if (p.belongsTo(it.session?.id)) it.copy(localSolved = it.localSolved - p.step) else it }
                                if (result.locked) _events.tryEmit(UiEvent.Locked)
                            }
                        }
                        "photo" -> {
                            val f = queue.photoPath(p.photoFile ?: "")
                            if (f.exists()) a.photo(p.step, "image/jpeg", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                        }
                    }
                    queue.remove(p.id)
                } catch (e: ApiException) {
                    if (p.attempts >= 5 || e.code in 400..499 && e.code != 409 && e.code != 408 && e.code != 429) {
                        queue.remove(p.id)
                        _state.update { if (p.belongsTo(it.session?.id)) it.copy(localSolved = it.localSolved - p.step) else it }
                    } else queue.bump(p.id)
                } catch (e: Exception) {
                    queue.bump(p.id); break
                }
            }
            _state.update { it.copy(pending = queue.size()) }
        }
    }

    // ---- location (called by the foreground service) ----
    fun onLocation(l: LatLng) {
        _location.value = l
        val st = _state.value
        for (s in st.openPhoneSteps) {
            val w = s.waypoint ?: continue
            val d = Geo.distance(l.lat, l.lng, w.lat, w.lng)
            val level = notified[s.id] ?: 0
            if (d <= w.radius && level < 2) { notified[s.id] = 2; _events.tryEmit(UiEvent.Approach(s.id, true)) }
            else if (d <= 50 && d > w.radius && level < 1) { notified[s.id] = 1; _events.tryEmit(UiEvent.Approach(s.id, false)) }
        }
    }

    fun postLocation(l: LatLng) {
        val a = api ?: return
        scope.launch { runCatching { a.location(l.lat, l.lng, l.acc, l.heading, System.currentTimeMillis()) } }
    }

    fun logout() {
        pollJob?.cancel(); api = null; validatedApi = null
        _state.update { HuntState(inbox = it.inbox) }
    }
}
