package dk.datestuff.hunt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private fun fixture(): Campaign =
    AppJson.decodeFromString(LogicTest::class.java.getResource("/campaign.json")!!.readText())

class LogicTest {
    @Test fun mapIgnoresUnpinnedZeroZero() {
        assertFalse(dk.datestuff.hunt.ui.pinned(0.0, 0.0))
        assertFalse(dk.datestuff.hunt.ui.pinned(Double.NaN, 12.5))
        assertTrue(dk.datestuff.hunt.ui.pinned(55.65, 12.53))
    }

    @Test fun decodeScriptHoldsOnLastStageAndFormatsHex() {
        val d = dk.datestuff.hunt.ui.DecodeScript
        assertEquals("UPLINK ESTABLISHED", d.stage(0))
        assertEquals(d.stages.last(), d.stage(99))
        assertTrue(d.stages.any { it.startsWith("DECODING HIDDEN IMAGE DATA") })
        assertTrue(d.hexLine(kotlin.random.Random(1)).matches(Regex("([0-9A-F]{2} ){7}[0-9A-F]{2}")))
    }

    @Test fun offlineProgressSurvivesOnlyItsOwnSession() {
        val entries = listOf(
            Pending("a", "answer", "checkpoint", sessionId = "night-one"),
            Pending("b", "solve", "chest", sessionId = "night-one"),
            Pending("c", "photo", "portrait", sessionId = "night-one"),
            Pending("d", "solve", "checkpoint", sessionId = "night-two"),
            Pending("legacy", "solve", "old"),
        )
        assertEquals(setOf("checkpoint", "chest"), pendingSolved(entries, "night-one"))
        assertEquals(setOf("checkpoint"), pendingSolved(entries, "night-two"))
        assertTrue(pendingSolved(entries, "night-three").isEmpty())
        assertFalse(entries.last().belongsTo(null))
        assertFalse(entries.first().belongsTo("night-two"))
    }

    @Test fun lockedResponseIsPreserved() {
        val result = AppJson.decodeFromString<AnswerResult>("""{"ok":false,"locked":true}""")
        assertFalse(result.ok)
        assertTrue(result.locked)
    }

    @Test fun haversine() {
        // Copenhagen city hall -> Malmo central ~ 27 km; 1 degree latitude ~ 111.2 km
        assertEquals(111195.0, Geo.distance(55.0, 12.0, 56.0, 12.0), 200.0)
        assertEquals(0.0, Geo.distance(55.0, 12.0, 55.0, 12.0), 0.001)
    }

    @Test fun bearings() {
        assertEquals(0.0, Geo.bearing(55.0, 12.0, 56.0, 12.0), 0.01)
        assertEquals(180.0, Geo.bearing(56.0, 12.0, 55.0, 12.0), 0.01)
        assertEquals(90.0, Geo.bearing(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals(270.0, Geo.bearing(0.0, 1.0, 0.0, 0.0), 0.01)
    }

    @Test fun angleDiffWraps() {
        assertEquals(20.0, Geo.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.angleDiff(10.0, 350.0), 1e-9)
        assertTrue(abs(Geo.angleDiff(0.0, 180.0)) == 180.0)
    }

    @Test fun heat() {
        assertEquals(Geo.Heat.BURNING, Geo.heat(10.0, 25.0))
        assertEquals(Geo.Heat.ICE, Geo.heat(5000.0, 25.0))
    }

    @Test fun normMatchesServer() {
        assertEquals("aeble", Game.norm("Æble!"))
        assertEquals("oe", Game.norm("ø e"))
        assertEquals("cafe", Game.norm("Café"))
    }

    @Test fun openStepsFlow() {
        val c = fixture()
        // start: a (first), b (start). p-tilt is gated by chapter.
        assertEquals(listOf("s-a", "s-b"), Game.openSteps(c, emptySet()))
        // a solved: still b open; chapter gate holds trail
        assertEquals(listOf("s-b"), Game.openSteps(c, setOf("s-a")))
        // both solved: disabled step ignored; chapter gate lifts -> p-gps (prev of enabled list = s-b) and p-tilt (start)
        assertEquals(listOf("p-gps", "p-tilt"), Game.openSteps(c, setOf("s-a", "s-b")))
        // after list
        assertEquals(listOf("p-q", "p-tilt"), Game.openSteps(c, setOf("s-a", "s-b", "p-gps")))
        assertEquals(listOf("p-q"), Game.openSteps(c, setOf("s-a", "s-b", "p-gps", "p-tilt")))
        // forced bypasses gates
        assertTrue("p-q" in Game.openSteps(c, emptySet(), setOf("p-q")))
    }

    @Test fun fragmentsRoundRobin() {
        val c = fixture()
        // NIGHT over fragment steps [s-a, s-b, p-gps, p-tilt]
        assertEquals(mapOf("s-a" to "NT", "s-b" to "I", "p-gps" to "G", "p-tilt" to "H"), Game.fragments(c))
        val slots = Game.wordSlots(c, setOf("s-a", "p-gps"))
        assertEquals(listOf('N', null, 'G', null, 'T'), slots)
    }

    @Test fun checkAnswer() {
        val c = fixture()
        val q = c.steps.first { it.id == "p-q" }
        assertTrue(Game.check(q, "aeble"))
        assertTrue(Game.check(q, " ÆBLE "))
        assertFalse(Game.check(q, "pear"))
        assertFalse(Game.check(q, ""))
    }

    @Test fun parsesCampaignAndConfig() {
        val c = fixture()
        assertEquals("AGENT T", c.settings.agent)
        val gps = c.steps.first { it.id == "p-gps" }
        assertEquals(25.0, gps.waypoint!!.radius, 0.0)
        assertEquals(7.5, c.steps.first { it.id == "p-q" }.cfgDouble("tolerance", 0.0), 0.0)
        assertEquals(listOf("left", "up", "right"), c.steps.first { it.id == "p-tilt" }.cfgStrings("sequence"))
        assertEquals("What year?", c.steps.first { it.id == "p-q" }.cfgString("question"))
    }

    @Test fun parsesSessionAndEvents() {
        val json = """{"seq":42,"session":{"id":"2026-11-20T19:00:00","state":"running","startedAt":1,"pausedMs":0,"extraMinutes":5,
          "solved":{"s-a":{"at":5,"by":"terminal","skipped":false}},"forced":[],"hintsUsed":{"s-a":1},"open":["s-b"],"fragments":{"s-a":"NT"},
          "trail":[{"lat":1,"lng":2}],"photos":[{"id":"p1","step":"p-q","ts":1,"status":"pending"}],
          "messages":[{"seq":1,"ts":2,"to":"all","text":"hi"}],"remainingMs":1000,"elapsedMs":5,"unknownField":true,
          "campaign":{"schema":2,"meta":{"id":"x","title":"T"},"settings":{"agent":"A"},"chapters":[],"steps":[]}},
          "events":[{"seq":41,"ts":1,"kind":"solved","step":"s-a","by":"terminal","open":["s-b"],"fragment":"NT"},
                    {"seq":42,"ts":2,"kind":"hint","step":"s-b","text":"look up","index":0,"pushed":true},
                    {"seq":43,"ts":2,"kind":"time","extraMinutes":5}]}"""
        val r = AppJson.decodeFromString<PollResult>(json)
        assertEquals(42, r.seq)
        assertEquals("running", r.session!!.state)
        assertEquals(5.0, r.session!!.extraMinutes, 0.0)
        assertEquals("NT", r.session!!.fragments["s-a"])
        assertEquals("pending", r.session!!.photos.single().status)
        assertEquals(3, r.events.size)
        assertEquals("NT", r.events[0].fragment)
        assertEquals("look up", r.events[1].text)
        assertEquals(5.0, r.events[2].extraMinutes!!, 0.0)
    }

    @Test fun parsesIdleSession() {
        val s = AppJson.decodeFromString<Session>("""{"id":null,"campaign":null,"state":"idle","startedAt":0,"pausedMs":0,"extraMinutes":0,"solved":{},"forced":[],"hintsUsed":{},"open":[],"fragments":{},"trail":[],"photos":[],"messages":[],"seq":3,"elapsedMs":0,"remainingMs":null}""")
        assertNull(s.id); assertNull(s.campaign); assertNull(s.remainingMs)
    }

    @Test fun pairQr() {
        val p = parsePairQr("esc://pair?u=http%3A%2F%2F192.168.1.5%3A8667&t=abc123")
        assertNotNull(p)
        assertEquals("http://192.168.1.5:8667", p!!.url)
        assertEquals("abc123", p.token)
        assertNull(parsePairQr("https://example.com"))
        assertNull(parsePairQr("esc://pair?u=x"))
    }

    @Test fun backoffCaps() {
        assertEquals(1000, backoffMs(0)); assertEquals(8000, backoffMs(3)); assertEquals(15000, backoffMs(9))
    }
    @Test fun sessionParsesChatAndPhotoAi() {
        val s = AppJson.decodeFromString<Session>("""{"id":"x","state":"running","chat":[
            {"seq":3,"ts":30,"from":"handler","text":"Copy that.","step":"tree"},
            {"seq":2,"ts":20,"from":"agent","text":"where?"}],
          "photos":[{"id":"p1","step":"tree","ts":5,"status":"rejected","by":"ai",
            "ai":{"match":false,"confidence":0.9,"reason":"No tree visible"},"extra":1}]}""")
        assertEquals(2, s.chat.size)
        assertEquals("handler", s.chat[0].from)
        assertEquals("tree", s.chat[0].step)
        assertNull(s.chat[1].step)
        val p = s.photos.single()
        assertEquals("ai", p.by)
        assertFalse(p.ai!!.match)
        assertEquals(0.9, p.ai.confidence, 1e-9)
        assertEquals("No tree visible", p.ai.reason)
        assertTrue(AppJson.decodeFromString<Session>("""{"id":"y"}""").chat.isEmpty())
        assertNull(AppJson.decodeFromString<Session>("""{"photos":[{"id":"p"}]}""").photos[0].ai)
    }

    @Test fun parsesChatAndHandlerEvents() {
        val r = AppJson.decodeFromString<PollResult>("""{"seq":9,"events":[
            {"seq":7,"kind":"chat","msg":{"seq":4,"ts":1,"from":"control","text":"hi"}},
            {"seq":8,"kind":"handler","state":"thinking"},
            {"seq":9,"kind":"photo","id":"p","step":"s","status":"approved","by":"ai","ai":{"match":true,"confidence":0.8,"reason":"tree"}}]}""")
        assertEquals("hi", r.events[0].msg!!.text)
        assertEquals("control", r.events[0].msg!!.from)
        assertEquals("thinking", r.events[1].state)
        assertEquals("ai", r.events[2].by)
        assertTrue(r.events[2].ai!!.match)
    }

    @Test fun chatQueueItemRoundTrips() {
        val item = Pending("id1", "chat", "", answer = "hello", sessionId = "sess", ts = 42)
        val ser = kotlinx.serialization.builtins.ListSerializer(Pending.serializer())
        val back = AppJson.decodeFromString(ser, AppJson.encodeToString(ser, listOf(item))).single()
        assertEquals(item, back)
        assertEquals("hello", back.answer)
        assertTrue(back.belongsTo("sess"))
        assertTrue(pendingSolved(listOf(back), "sess").isEmpty())
        // old queue files without ts still load
        assertEquals(0L, AppJson.decodeFromString<Pending>("""{"id":"a","kind":"solve","step":"s"}""").ts)
    }

    @Test fun mergeChatDedupesBySeqAndOrders() {
        val a = ChatMsg(2, 20, "handler", "b")
        val merged = mergeServerChat(listOf(ChatMsg(1, 10, "agent", "a"), a), listOf(a.copy(text = "b2"), ChatMsg(3, 30, "control", "c")))
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.seq })
        assertEquals("b2", merged[1].text)
        assertEquals(2, mergeServerChat(merged, emptyList(), cap = 2).size)
    }

    @Test fun pendingLocalIsShownUntilServerEchoes() {
        val local = listOf(LocalChat("l1", 1000, "where is it?", baseSeq = 1))
        val before = mergeChat(listOf(ChatMsg(1, 500, "handler", "ready")), local)
        assertEquals(2, before.size)
        assertTrue(before.last().pending)
        assertEquals("agent", before.last().from)
        val after = mergeChat(listOf(ChatMsg(1, 500, "handler", "ready"), ChatMsg(2, 1100, "agent", "where is it?")), local)
        assertEquals(2, after.size)
        assertFalse(after.any { it.pending })
        assertTrue(unmatchedLocal(listOf(ChatMsg(2, 1100, "agent", "where is it?")), local).isEmpty())
    }

    @Test fun duplicateTextMatchesOnePendingEach() {
        val local = listOf(LocalChat("a", 1000, "ok"), LocalChat("b", 2000, "ok"))
        val one = listOf(ChatMsg(1, 1100, "agent", "ok"))
        assertEquals(listOf("b"), unmatchedLocal(one, local).map { it.id })
        // handler text equal to pending text must not match
        assertEquals(2, unmatchedLocal(listOf(ChatMsg(1, 1100, "handler", "ok")), local).size)
    }

    @Test fun feedInterleavesHintsByTime() {
        val chat = listOf(ChatLine(1, 10, "agent", "a"), ChatLine(2, 30, "handler", "b"))
        val inbox = listOf(InboxItem(20, "hint", "h"), InboxItem(25, "message", "b"), InboxItem(26, "message", "legacy"))
        val feed = buildFeed(chat, inbox)
        assertEquals(listOf("a", "h", "legacy", "b"), feed.map { it.chat?.text ?: it.note!!.text })
    }

    @Test fun oldIdenticalServerMessageDoesNotMatchNewSend() {
        val old = ChatMsg(5, 900, "agent", "ok")
        val local = listOf(LocalChat("n", 1000, "ok", baseSeq = 5))
        assertEquals(listOf("n"), unmatchedLocal(listOf(old), local).map { it.id })
        assertTrue(unmatchedLocal(listOf(old, ChatMsg(6, 1100, "agent", "ok")), local).isEmpty())
    }

    @Test fun failedLocalIsShownFailedAndNeverMatches() {
        val local = listOf(LocalChat("f", 1000, "hi", baseSeq = 0, failed = true))
        val lines = mergeChat(listOf(ChatMsg(1, 1100, "agent", "hi")), local)
        val f = lines.last()
        assertTrue(f.failed)
        assertFalse(f.pending)
        assertEquals("f", f.localId)
        assertEquals(2, lines.size)
    }
}

