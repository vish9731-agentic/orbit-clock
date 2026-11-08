package dev.orbit.clock.core

import org.junit.Assert.*
import org.junit.Test

class FocusLaunchTicketTest {
    private val session = FocusSession(id = "session", status = "Running", revision = 12, writer = "phone")
    private val ticket = FocusLaunchTicket("nonce", "peer", session.id, session.revision, session.writer, 3, 20_000)
    @Test fun onlyExactPreparedSnapshotIsAllowed() {
        assertTrue(ticket.permits(session, 3, 1000))
        assertFalse(ticket.permits(session.copy(id = "other"), 3, 1000))
        assertFalse(ticket.permits(session.copy(revision = 13), 3, 1000))
        assertFalse(ticket.permits(session.copy(writer = "watch"), 3, 1000))
        assertFalse(ticket.permits(session.copy(status = "Ready"), 3, 1000))
    }
    @Test fun staleAndRebootedTicketsCannotOpenWatch() {
        assertFalse(ticket.permits(session, 3, 20_000))
        assertFalse(ticket.permits(session, 3, 90_000))
        assertFalse(ticket.permits(session, 4, 100))
        assertFalse(ticket.permits(session, 3, -1))
    }
    @Test fun manualOpenCanShowPausedAndCompleteWithoutRestarting() {
        assertTrue(ticket.permits(session.copy(status = "Paused"), 3, 1000))
        assertTrue(ticket.permits(session.copy(status = "Complete"), 3, 1000))
    }
}
