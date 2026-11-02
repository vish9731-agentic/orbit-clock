package dev.orbit.clock.core

import org.junit.Assert.*
import org.junit.Test

class FocusSessionTest {
    @Test fun pauseResumePreservesRemainder() {
        val start = FocusSession().act("start", 1000)
        val paused = start.act("pause", 61000)
        assertEquals(24 * 60_000L, paused.remaining)
        assertEquals(paused, paused.act("pause", 90000))
        val resumed = paused.act("start", 100000)
        assertEquals(100000 + paused.remaining, resumed.deadline)
        assertEquals(resumed, resumed.act("start", 101000))
    }
    @Test fun completionIsIdempotentAndWaits() {
        val s = FocusSession().act("start", 0)
        val done = s.complete(9_000_000)
        assertEquals("Complete", done.status)
        assertEquals(1, done.completed)
        assertEquals(done, done.complete(10_000_000))
        val rest = done.act("start", 10_000_000)
        assertEquals("Break", rest.phase)
        assertEquals(5 * 60_000L, rest.left(10_000_000))
    }
    @Test fun fourthFocusOffersLongBreak() {
        var s = FocusSession(focus = 1, shortBreak = 1, longBreak = 2)
        var now = 0L
        repeat(4) { cycle ->
            s = s.act("start", now); now += 60_000
            s = s.complete(now)
            assertEquals(cycle + 1, s.completed)
            s = s.act("start", now)
            assertEquals(if (cycle == 3) "Long break" else "Break", s.phase)
            now += if (cycle == 3) 120_000 else 60_000
            s = s.complete(now)
            assertEquals(cycle + 1, s.completed)
        }
        assertEquals("Focus", s.act("start", now).phase)
    }
    @Test fun endResetsCycleButKeepsPreferences() {
        val s = FocusSession(focus = 50, shortBreak = 10, completed = 3).act("start", 0).act("end", 100)
        assertEquals("Ready", s.status); assertEquals(0, s.completed)
        assertEquals(50 * 60_000L, s.remaining); assertEquals(10, s.shortBreak)
    }
    @Test fun concurrentWritesHaveDeterministicOrdering() {
        val a = FocusSession(revision = 9, writer = "a")
        val b = a.copy(writer = "b")
        assertTrue(b.newerThan(a)); assertFalse(a.newerThan(b))
        assertFalse(a.newerThan(a)); assertTrue(a.copy(revision = 10).newerThan(b))
    }
}
