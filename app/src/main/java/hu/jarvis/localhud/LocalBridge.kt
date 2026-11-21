package hu.jarvis.localhud

import android.os.SystemClock
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal fun result(ok: Boolean, error: String = ""): JSONObject =
    JSONObject().put("ok", ok).also { if (error.isNotEmpty()) it.put("error", error) }

internal object LocalBridge {
    private val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    @Volatile var connected = false
    @Volatile private var server: BridgeServer? = null
    @Synchronized fun start(): Boolean {
        if (server != null) return true
        return try {
            val candidate = BridgeServer()
            candidate.start(5000, true)
            server = candidate
            true
        } catch (_: Exception) { false }
    }
    fun request(path: String, body: String? = null): JSONObject {
        if (!start()) return result(false, "Cannot bind local port 8080.")
        return try {
            val c = URL("http://127.0.0.1:8080" + path).openConnection(Proxy.NO_PROXY) as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 1000
                c.readTimeout = 6500
                c.setRequestProperty("X-Jarvis-Session", token)
                if (body != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    c.setFixedLengthStreamingMode(bytes.size)
                    c.outputStream.use { it.write(bytes) }
                }
                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
            } finally { c.disconnect() }
        } catch (_: Exception) { result(false, "Local bridge not reachable.") }
    }
    private data class Job(val id: String, val command: JSONObject, val deadline: Long, val future: CompletableFuture<JSONObject>)
    private val queue = ArrayBlockingQueue<Job>(8)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val allowed = setOf("home","back","recents","scroll_down","scroll_up","tap","click_text","type_text","open_app","snapshot")
    private fun submit(cmd: JSONObject): JSONObject {
        if (!connected) return result(false, "Enable JARVIS Local Bridge in Accessibility settings.")
        if (cmd.optString("action") !in allowed) return result(false, "Unsupported command.").put("connected", true)
        val id = UUID.randomUUID().toString()
        val j = Job(id, cmd, SystemClock.elapsedRealtime()+4000L, CompletableFuture())
        jobs[id] = j
        if (!queue.offer(j)) { jobs.remove(id); return result(false,"Bridge busy.").put("connected",true) }
        return try {
            j.future.get(5, TimeUnit.SECONDS).put("connected", true)
        } catch (_: Exception) {
            result(false,"No confirmed result; command timed out.").put("connected",connected)
        } finally { jobs.remove(id); queue.remove(j) }
    }
    private class BridgeServer : NanoHTTPD("127.0.0.1", 8080) {
        private fun reply(j: JSONObject, status: Response.Status = Response.Status.OK): Response =
            newFixedLengthResponse(status, "application/json", j.toString()).also { it.addHeader("Cache-Control","no-store") }
        override fun serve(s: IHTTPSession): Response {
            if (s.headers["x-jarvis-session"] != token) return reply(result(false,"Forbidden"),Response.Status.FORBIDDEN)
            try {
                if (s.method == Method.GET && s.uri == "/bridge/poll") {
                    var j = queue.poll()
                    while (j != null) {
                        val item = j
                        if (jobs.containsKey(item.id) && SystemClock.elapsedRealtime() < item.deadline) {
                            return reply(JSONObject().put("job",item.command).put("id",item.id).put("deadline",item.deadline))
                        }
                        j = queue.poll()
                    }
                    return reply(JSONObject())
                }
                if (s.method == Method.GET && s.uri == "/bridge/state")
                    return reply(submit(JSONObject().put("action","snapshot")))
                if (s.method != Method.POST) return reply(result(false,"Not found"),Response.Status.NOT_FOUND)
                val len = s.headers["content-length"]?.toIntOrNull() ?: 0
                if (len !in 1..65536) return reply(result(false,"Invalid body size"),Response.Status.BAD_REQUEST)
                val data = HashMap<String,String>()
                s.parseBody(data)
                val body = JSONObject(data["postData"] ?: "{}")
                return when (s.uri) {
                    "/bridge/command" -> reply(submit(body))
                    "/bridge/result" -> {
                        jobs[body.optString("id")]?.future?.complete(body.optJSONObject("result") ?: result(false,"Missing result"))
                        reply(result(true))
                    }
                    else -> reply(result(false,"Not found"),Response.Status.NOT_FOUND)
                }
            } catch (_: Exception) { return reply(result(false,"Invalid request"),Response.Status.BAD_REQUEST) }
        }
    }
}
