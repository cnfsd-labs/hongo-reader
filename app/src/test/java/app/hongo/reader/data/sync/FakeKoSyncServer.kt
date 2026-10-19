package app.hongo.reader.data.sync

import app.hongo.reader.data.opds.HeaderServer
import app.hongo.reader.data.opds.respond
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A KOReader sync server (koreader-sync-server's API) on localhost, for tests. [cwa] makes it
 * answer like Calibre-Web Automated: under `/kosync`, with the password as HTTP Basic only.
 */
internal class FakeKoSyncServer(
    private val user: String = "reader",
    private val password: String = "secret",
    private val cwa: Boolean = false,
) {
    private val key = KoSyncAccount.userKey(password)
    val users = ConcurrentHashMap(mapOf(user to key))

    /** Saved progress by document: the request body plus a timestamp. */
    val progress = ConcurrentHashMap<String, JsonObject>()
    var now: Long = 1_000_000

    private lateinit var http: HeaderServer
    val url: String get() = "http://127.0.0.1:${http.port}"
    val requests get() = http.requests

    init {
        http = HeaderServer { fullPath, out ->
            val request = http.requests.last()
            val path = if (cwa) fullPath.removePrefix("/kosync") else fullPath
            val basic = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
            val authorized = if (cwa) {
                request.headers["authorization"] == basic
            } else {
                request.headers["x-auth-user"]?.let { users[it] }?.let { it == request.headers["x-auth-key"] } == true
            }
            when {
                cwa && !fullPath.startsWith("/kosync/") -> out.respond("308 Permanent Redirect", "")
                request.headers["accept"] != "application/vnd.koreader.v1+json" -> out.respond("412 Precondition Failed", "{}")
                request.method == "POST" && path == "/users/create" -> {
                    val body = Json.parseToJsonElement(request.body) as JsonObject
                    val name = body["username"]!!.jsonPrimitive.content
                    if (users.containsKey(name)) {
                        out.respond("402 Payment Required", """{"code":2002,"message":"Username is already registered."}""")
                    } else {
                        users[name] = body["password"]!!.jsonPrimitive.content
                        out.respond("201 Created", """{"username":"$name"}""")
                    }
                }
                !authorized -> out.respond("401 Unauthorized", """{"code":2001,"message":"Unauthorized"}""")
                path == "/users/auth" -> out.respond("200 OK", """{"authorized":"OK"}""")
                request.method == "PUT" && path == "/syncs/progress" -> {
                    val body = Json.parseToJsonElement(request.body) as JsonObject
                    val document = body["document"]!!.jsonPrimitive.content
                    progress[document] = JsonObject(body + ("timestamp" to kotlinx.serialization.json.JsonPrimitive(now)))
                    out.respond("200 OK", """{"document":"$document","timestamp":$now}""")
                }
                path.startsWith("/syncs/progress/") -> {
                    val saved = progress[path.removePrefix("/syncs/progress/")]
                    out.respond("200 OK", saved?.toString() ?: "{}")
                }
                else -> out.respond("404 Not Found", "")
            }
        }
    }

    fun save(document: String, xpointer: String, percentage: Double, device: String, deviceId: String, timestamp: Long) {
        progress[document] = buildJsonObject {
            put("document", document)
            put("progress", xpointer)
            put("percentage", percentage)
            put("device", device)
            put("device_id", deviceId)
            put("timestamp", timestamp)
        }
    }

    fun stop() = http.stop()
}
