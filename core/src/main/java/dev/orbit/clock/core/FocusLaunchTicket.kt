package dev.orbit.clock.core

/** A short-lived UI-only authorization delivered over the signed Wear Data Layer. */
data class FocusLaunchTicket(
    val nonce: String, val node: String, val session: String, val revision: Long,
    val writer: String, val boot: Int, val expires: Long, val opening: Boolean = false
) {
    fun permits(s: FocusSession, currentBoot: Int, elapsed: Long): Boolean =
        boot == currentBoot && elapsed >= 0 && elapsed < expires &&
            s.id == session && s.revision == revision && s.writer == writer &&
            s.status in listOf("Running", "Paused", "Complete")
}
