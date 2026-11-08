package dev.orbit.clock.core

import android.content.*
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.util.UUID

/** Immediate bidirectional snapshots with receipts; DataItems remain the offline durable fallback. */
object FocusSync {
    const val CHANGED = "dev.orbit.clock.FOCUS_SYNC_CHANGED"
    const val CAPABILITY = "orbit_focus_sync_v1"
    const val STATE = "/orbit/focus/state"
    const val ACK = "/orbit/focus/state-ack"
    private val handler = Handler(Looper.getMainLooper())
    private data class Delivery(val c: Context, val node: String, val nonce: String, val state: FocusSession)
    private val waiting = mutableMapOf<String, Delivery>() // main looper only
    private fun prefs(c: Context) = c.getSharedPreferences("focus_sync", 0)
    private fun same(a: FocusSession, b: FocusSession) = a.id == b.id && a.revision == b.revision && a.writer == b.writer
    private fun peer(c: Context) = if (FocusHandoff.isWatch(c)) "phone" else "watch"
    private fun status(c: Context, code: String, message: String) {
        prefs(c).edit().putString("code", code).putString("message", message).apply()
        c.sendBroadcast(Intent(CHANGED).setPackage(c.packageName))
    }
    fun statusText(c: Context) = prefs(c).getString("message", "") ?: ""
    fun statusCode(c: Context) = prefs(c).getString("code", "") ?: ""
    fun publish(c: Context, s: FocusSession = FocusStore.read(c)) {
        val app = c.applicationContext
        handler.post {
            if (!same(s, FocusStore.read(app))) return@post
            status(app, "sending", "Syncing with ${peer(app)}…")
            Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
                if (!same(s, FocusStore.read(app))) return@addOnSuccessListener
                if (nodes.isEmpty()) {
                    waiting.clear()
                    status(app, "disconnected", "${peer(app).replaceFirstChar { it.uppercase() }} not connected. Open Orbit on both devices.")
                } else nodes.forEach { node ->
                    val delivery = Delivery(app, node.id, UUID.randomUUID().toString(), s)
                    waiting[node.id] = delivery
                    val bytes = JSONObject().put("nonce", delivery.nonce).put("record", FocusStore.encode(s)).toString().toByteArray(Charsets.UTF_8)
                    Wearable.getMessageClient(app).sendMessage(node.id, STATE, bytes).addOnFailureListener {
                        if (waiting[node.id] === delivery) {
                            waiting.remove(node.id)
                            status(app, "unavailable", "Couldn’t reach ${peer(app)}. Update and open Orbit on both devices.")
                        }
                    }
                    handler.postDelayed({
                        if (waiting[node.id] === delivery) {
                            waiting.remove(node.id)
                            if (same(s, FocusStore.read(app))) status(app, "unconfirmed", "${peer(app).replaceFirstChar { it.uppercase() }} hasn’t confirmed sync. Check Bluetooth and both app versions.")
                        }
                    }, 15_000)
                }
            }.addOnFailureListener {
                if (same(s, FocusStore.read(app))) status(app, "unavailable", "${peer(app).replaceFirstChar { it.uppercase() }} connection unavailable. This timer continues locally.")
            }
        }
    }
    /** Pure transport boundary: persist the peer's newer record and return our authoritative state. */
    fun accept(c: Context, bytes: ByteArray): ByteArray? = runCatching {
        require(bytes.size <= 16_384)
        val value = JSONObject(bytes.toString(Charsets.UTF_8)); val nonce = value.getString("nonce")
        require(UUID.fromString(nonce).toString() == nonce)
        val raw = value.getString("record"); val incoming = FocusStore.decode(raw)
        FocusStore.merge(c, raw)
        val actual = FocusStore.read(c)
        if (same(incoming, actual)) status(c, "received", "Received session from ${peer(c)}.")
        else status(c, "current", "Kept the newer local session; sending it to ${peer(c)}.")
        JSONObject().put("nonce", nonce).put("record", FocusStore.encode(actual))
            .put("matched", same(incoming, actual)).toString().toByteArray(Charsets.UTF_8)
    }.getOrNull()
    fun receive(c: Context, node: String, path: String, bytes: ByteArray): Boolean {
        if (path !in listOf(STATE, ACK)) return false
        if (bytes.size > 16_384) return true
        if (path == STATE) {
            accept(c, bytes)?.let { Wearable.getMessageClient(c).sendMessage(node, ACK, it) }
        } else handler.post {
            val d = waiting[node] ?: return@post
            runCatching {
                val reply = JSONObject(bytes.toString(Charsets.UTF_8))
                if (reply.getString("nonce") != d.nonce) return@runCatching
                waiting.remove(node)
                val actual = FocusStore.decode(reply.getString("record"))
                FocusStore.merge(c, FocusStore.encode(actual))
                if (same(actual, FocusStore.read(c))) {
                    if (same(actual, d.state)) status(c, "synced", "Synced with ${peer(c)}.")
                    else status(c, "updated", "Updated to the newer session from ${peer(c)}.")
                }
            }
        }
        return true
    }
}
