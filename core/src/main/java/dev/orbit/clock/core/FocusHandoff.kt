package dev.orbit.clock.core

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.*
import org.json.JSONObject
import java.util.UUID

/** Live handoff is distinct from durable state sync: old/reconnected records never open a screen. */
object FocusHandoff {
    const val CHANGED = "dev.orbit.clock.FOCUS_HANDOFF_CHANGED"
    const val CAPABILITY = "orbit_focus_handoff_v1"
    const val PREPARE = "/orbit/focus/handoff/prepare"
    const val PREPARED = "/orbit/focus/handoff/prepared"
    const val VISIBLE = "/orbit/focus/handoff/visible"
    const val EXTRA_NONCE = "focus_handoff_nonce"
    private const val TTL = 20_000L
    private val handler = Handler(Looper.getMainLooper())
    private data class Pending(val c: Context, val session: FocusSession, val nonce: String,
        var node: String = "", var opening: Boolean = false)
    @Volatile private var pending: Pending? = null // accessed only on the main looper
    private fun prefs(c: Context) = c.getSharedPreferences("focus_handoff", 0)
    fun isWatch(c: Context) = c.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun matches(a: FocusSession, b: FocusSession) = a.id == b.id && a.revision == b.revision && a.writer == b.writer
    private fun status(c: Context, code: String, text: String) {
        prefs(c).edit().putString("status", text).putString("code", code).apply()
        c.sendBroadcast(Intent(CHANGED).setPackage(c.packageName))
    }
    fun statusText(c: Context): String {
        val p = prefs(c)
        return if (p.getString("code", "") == "opening" && pending == null)
            "Watch handoff interrupted. Tap Open on watch to retry." else p.getString("status", "") ?: ""
    }
    fun isPending() = pending != null
    fun statusCode(c: Context) = prefs(c).getString("code", "") ?: ""
    fun open(c: Context, s: FocusSession = FocusStore.read(c)) {
        if (isWatch(c) || s.id.isEmpty() || s.status !in listOf("Running", "Paused", "Complete")) return
        val app = c.applicationContext
        handler.post {
            if (!matches(s, FocusStore.read(app))) return@post
            val p = Pending(app, s, UUID.randomUUID().toString()); pending = p
            status(app, "opening", "Opening Focus on watch…")
            handler.postDelayed({
                if (pending === p) finish(p, "unconfirmed", "Couldn’t confirm the watch opened. Open Orbit on both devices and retry.")
            }, TTL)
            Wearable.getCapabilityClient(app).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { info ->
                    if (!current(p)) return@addOnSuccessListener
                    val node = info.nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
                    if (node == null) {
                        noCapableWatch(p)
                    } else {
                        p.node = node.id
                        val data = JSONObject().put("nonce", p.nonce).put("sentAt", System.currentTimeMillis())
                            .put("record", FocusStore.encode(s)).toString().toByteArray(Charsets.UTF_8)
                        Wearable.getMessageClient(app).sendMessage(node.id, PREPARE, data).addOnFailureListener {
                            if (current(p)) finish(p, "unavailable", "Couldn’t reach Orbit on the watch. Open both apps and retry.")
                        }
                    }
                }.addOnFailureListener { if (current(p)) noCapableWatch(p) }
        }
    }
    private fun noCapableWatch(p: Pending) {
        Wearable.getNodeClient(p.c).connectedNodes.addOnSuccessListener { nodes ->
            if (current(p)) {
                if (nodes.isEmpty()) finish(p, "disconnected", "No watch connected. Connect your watch, then retry.")
                else finish(p, "update-watch", "Update Orbit on your watch and open it once, then retry.")
            }
        }.addOnFailureListener { if (current(p)) finish(p, "unavailable", "Watch connection unavailable. Your phone timer continues.") }
    }
    private fun current(p: Pending) = pending === p && matches(p.session, FocusStore.read(p.c))
    private fun finish(p: Pending, code: String, text: String) {
        if (pending !== p) return
        pending = null; status(p.c, code, text)
    }
    fun sessionChanged(c: Context, s: FocusSession) {
        if (isWatch(c)) {
            val t = ticket(c)
            if (t != null && !t.permits(s, boot(c), SystemClock.elapsedRealtime())) prefs(c).edit().remove("ticket").commit()
        } else handler.post {
            pending?.let { if (!matches(it.session, s)) finish(it, "changed", "Session changed before watch handoff completed. Tap Open on watch to retry.") }
            if (s.status == "Ready") status(c, "", "")
        }
    }
    /** Called only at the authenticated Wear message boundary (never by the exported URI handler). */
    @Synchronized fun prepare(c: Context, node: String, bytes: ByteArray): ByteArray {
        var nonce = ""
        var accepted = false
        runCatching {
            require(isWatch(c) && bytes.size <= 16_384 && node.isNotEmpty() && node.length <= 256)
            val j = JSONObject(bytes.toString(Charsets.UTF_8)); nonce = j.getString("nonce")
            require(UUID.fromString(nonce).toString() == nonce)
            val age = System.currentTimeMillis() - j.getLong("sentAt")
            require(age in -60_000L..60_000L)
            val raw = j.getString("record"); val s = FocusStore.decode(raw)
            require(s.id.isNotEmpty() && s.writer.isNotEmpty() && s.owner.isNotEmpty() && s.status in listOf("Running", "Paused", "Complete"))
            require(s.status != "Running" || s.deadline > System.currentTimeMillis())
            FocusStore.merge(c, raw)
            require(matches(s, FocusStore.read(c))) // a newer watch edit must win
            saveTicket(c, FocusLaunchTicket(nonce, node, s.id, s.revision, s.writer, boot(c), SystemClock.elapsedRealtime() + TTL))
            accepted = true
        }
        return JSONObject().put("nonce", nonce).put("accepted", accepted)
            .put("record", FocusStore.encode(FocusStore.read(c))).toString().toByteArray(Charsets.UTF_8)
    }
    fun receive(c: Context, node: String, path: String, bytes: ByteArray) {
        if (bytes.size > 16_384) return
        if (path == PREPARE && isWatch(c)) {
            val reply = prepare(c, node, bytes)
            Wearable.getMessageClient(c).sendMessage(node, PREPARED, reply)
            return
        }
        if (isWatch(c) || path !in listOf(PREPARED, VISIBLE)) return
        handler.post {
            val p = pending ?: return@post
            if (node != p.node || !current(p)) return@post
            runCatching {
                val j = JSONObject(bytes.toString(Charsets.UTF_8))
                if (j.getString("nonce") != p.nonce) return@runCatching
                if (path == VISIBLE) {
                    if (p.opening) finish(p, "opened", "Watch opened Focus.")
                } else if (!p.opening) {
                    val actual = FocusStore.decode(j.getString("record"))
                    if (!j.optBoolean("accepted") || !matches(p.session, actual)) {
                        FocusStore.merge(c, FocusStore.encode(actual))
                        finish(p, "changed", "Watch has a newer session. Check the synced timer, then retry.")
                        return@runCatching
                    }
                    p.opening = true
                    val launch = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(launchUri(p.nonce))
                    val result = RemoteActivityHelper(p.c, p.c.mainExecutor).startRemoteActivity(launch, node)
                    result.addListener({
                        if (current(p)) runCatching { result.get() }.onFailure {
                            finish(p, "manual", "Session shared. Open Focus on your watch; Wear OS couldn’t open it automatically.")
                        }
                    }, p.c.mainExecutor)
                    // Success means intent delivery, not visible UI. Wait for VISIBLE from the watch.
                }
            }.onFailure { if (current(p)) finish(p, "unavailable", "Watch handoff failed. Your phone timer continues; tap Open on watch to retry.") }
        }
    }
    fun launchUri(nonce: String): Uri = Uri.Builder().scheme("dev.orbit.clock.focus").authority("handoff").path("/open").appendQueryParameter("nonce", nonce).build()
    private fun saveTicket(c: Context, t: FocusLaunchTicket) {
        prefs(c).edit().putString("ticket", JSONObject().put("nonce", t.nonce).put("node", t.node)
            .put("session", t.session).put("revision", t.revision).put("writer", t.writer)
            .put("boot", t.boot).put("expires", t.expires).put("opening", t.opening).toString()).commit()
    }
    private fun ticket(c: Context): FocusLaunchTicket? = runCatching {
        val j = JSONObject(prefs(c).getString("ticket", "")!!)
        FocusLaunchTicket(j.getString("nonce"), j.getString("node"), j.getString("session"), j.getLong("revision"),
            j.getString("writer"), j.getInt("boot"), j.getLong("expires"), j.getBoolean("opening"))
    }.getOrNull()
    @Synchronized fun consumeLaunch(c: Context, uri: Uri?): String? {
        if (!isWatch(c) || uri?.scheme != "dev.orbit.clock.focus" || uri.host != "handoff" || uri.path != "/open") return null
        val t = ticket(c) ?: return null
        if (t.opening || uri.getQueryParameter("nonce") != t.nonce || !t.permits(FocusStore.read(c), boot(c), SystemClock.elapsedRealtime())) return null
        saveTicket(c, t.copy(opening = true)); return t.nonce
    }
    fun isOpening(c: Context, nonce: String?) = ticket(c)?.let {
        nonce == it.nonce && it.opening && it.permits(FocusStore.read(c), boot(c), SystemClock.elapsedRealtime())
    } ?: false
    fun visible(a: Activity) {
        if (!isWatch(a) || !a.hasWindowFocus() || !a.window.decorView.isLaidOut || !a.window.decorView.isShown) return
        val nonce = a.intent.getStringExtra(EXTRA_NONCE) ?: return
        val t = ticket(a) ?: return
        if (!isOpening(a, nonce)) return
        prefs(a).edit().remove("ticket").commit()
        val data = JSONObject().put("nonce", nonce).toString().toByteArray(Charsets.UTF_8)
        Wearable.getMessageClient(a).sendMessage(t.node, VISIBLE, data)
    }
}

/** Only Wear OS declares this browsable route. Its URI cannot create or change session state. */
class FocusLinkActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val nonce = if (intent.action == Intent.ACTION_VIEW) FocusHandoff.consumeLaunch(this, intent.data) else null
        if (nonce != null) {
            startActivity(Intent(this, FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(FocusHandoff.EXTRA_NONCE, nonce))
        }
        finish()
    }
}
