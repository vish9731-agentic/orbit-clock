package dev.orbit.clock.core

/** Pure state transitions: completion waits for explicit input, never skips unattended phases. */
data class FocusSession(
    val id: String = "", val phase: String = "Focus", val status: String = "Ready",
    val remaining: Long = 25 * 60_000L, val deadline: Long = 0,
    val completed: Int = 0, val focus: Int = 25, val shortBreak: Int = 5, val longBreak: Int = 15,
    val revision: Long = 0, val writer: String = "", val owner: String = ""
) {
    fun left(now: Long) = if (status == "Running") (deadline - now).coerceAtLeast(0) else remaining
    fun duration() = (if (phase == "Focus") focus else if (phase == "Long break") longBreak else shortBreak) * 60_000L
    fun complete(now: Long): FocusSession = if (status == "Running" && left(now) == 0L)
        copy(status = "Complete", remaining = 0, deadline = 0, completed = completed + if (phase == "Focus") 1 else 0) else this
    fun act(action: String, now: Long): FocusSession {
        val s = complete(now)
        return when (action) {
            "pause" -> if (s.status == "Running") s.copy(status = "Paused", remaining = s.left(now), deadline = 0) else s
            "start" -> when (s.status) {
                "Ready", "Paused" -> s.copy(status = "Running", deadline = now + s.remaining)
                "Complete" -> {
                    val next = if (s.phase != "Focus") "Focus" else if (s.completed % 4 == 0) "Long break" else "Break"
                    val ready = s.copy(phase = next, status = "Running")
                    ready.copy(remaining = ready.duration(), deadline = now + ready.duration())
                }
                else -> s
            }
            "end" -> FocusSession(focus = s.focus, shortBreak = s.shortBreak, longBreak = s.longBreak, remaining = s.focus * 60_000L)
            else -> s
        }
    }
    fun newerThan(other: FocusSession) = revision > other.revision || (revision == other.revision && writer > other.writer)
}
