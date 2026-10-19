package app.hongo.reader.data.opds

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * HTTP Digest authentication (RFC 7616, and RFC 2617's form without qop), which Calibre's own
 * content server uses by default over plain http: the password never goes over the wire, only a
 * hash of it with the server's nonce.
 */
internal class DigestChallenge private constructor(
    val realm: String,
    val nonce: String,
    /** "auth", or null for the old form without qop (auth-int is not supported). */
    private val qop: String?,
    private val opaque: String?,
    /** As the server named it, e.g. "MD5", "SHA-256-sess"; null means MD5. */
    private val algorithm: String?,
    /** The server said the last nonce expired; the login itself was right. */
    val stale: Boolean,
) {
    private var count = 0

    /** The Authorization header for a [method] request to [uri] (path and query). */
    @Synchronized
    fun authorization(username: String, password: String, method: String, uri: String, cnonce: String = cnonce()): String {
        count++
        val nc = "%08x".format(count)
        val name = algorithm ?: "MD5"
        val sess = name.endsWith("-sess", ignoreCase = true)
        val hash = hashFor(name)
        val secret = hash("$username:$realm:$password")
        val ha1 = if (sess) hash("$secret:$nonce:$cnonce") else secret
        val ha2 = hash("$method:$uri")
        val response = if (qop != null) hash("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else hash("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=").append(quote(username))
            append(", realm=").append(quote(realm))
            append(", nonce=").append(quote(nonce))
            append(", uri=").append(quote(uri))
            if (algorithm != null) append(", algorithm=").append(algorithm)
            append(", response=").append(quote(response))
            if (opaque != null) append(", opaque=").append(quote(opaque))
            if (qop != null) append(", qop=").append(qop).append(", nc=").append(nc).append(", cnonce=").append(quote(cnonce))
        }
    }

    companion object {
        private val random = SecureRandom()
        private val supported = listOf("SHA-256", "SHA-256-sess", "MD5", "MD5-sess")

        /**
         * The best Digest challenge among the WWW-Authenticate header values (a header can hold
         * several challenges); null if none is one HonGO! can answer.
         */
        fun from(headers: List<String>): DigestChallenge? = headers
            .flatMap(::challenges)
            .filter { (scheme, _) -> scheme.equals("Digest", ignoreCase = true) }
            .mapNotNull { (_, params) -> fromParams(params) }
            .minByOrNull { supported.indexOfFirst { name -> name.equals(it.algorithm ?: "MD5", ignoreCase = true) } }

        private fun fromParams(params: Map<String, String>): DigestChallenge? {
            val realm = params["realm"] ?: return null
            val nonce = params["nonce"] ?: return null
            val algorithm = params["algorithm"]
            if (supported.none { it.equals(algorithm ?: "MD5", ignoreCase = true) }) return null
            val qops = params["qop"]?.split(',')?.map { it.trim().lowercase() }
            val qop = when {
                qops == null -> null
                "auth" in qops -> "auth"
                else -> return null
            }
            return DigestChallenge(
                realm = realm,
                nonce = nonce,
                qop = qop,
                opaque = params["opaque"],
                algorithm = algorithm,
                stale = params["stale"].equals("true", ignoreCase = true),
            )
        }

        /** Splits a header value into challenges: scheme, then its `name=value` parameters. */
        internal fun challenges(header: String): List<Pair<String, Map<String, String>>> {
            val result = mutableListOf<Pair<String, MutableMap<String, String>>>()
            var i = 0
            fun skip(chars: String) {
                while (i < header.length && header[i] in chars) i++
            }
            while (i < header.length) {
                skip(" \t,")
                val start = i
                while (i < header.length && header[i] !in " \t,=") i++
                if (start == i) break
                val token = header.substring(start, i)
                skip(" \t")
                if (i < header.length && header[i] == '=' && result.isNotEmpty()) {
                    i++
                    skip(" \t")
                    val value = if (i < header.length && header[i] == '"') {
                        i++
                        val text = StringBuilder()
                        while (i < header.length && header[i] != '"') {
                            if (header[i] == '\\' && i + 1 < header.length) i++
                            text.append(header[i])
                            i++
                        }
                        i++
                        text.toString()
                    } else {
                        val valueStart = i
                        while (i < header.length && header[i] !in " \t,") i++
                        header.substring(valueStart, i)
                    }
                    result.last().second[token.lowercase()] = value
                } else if (i < header.length && header[i] == '=') {
                    // A token68 credential with no scheme before it: nothing to answer.
                    while (i < header.length && header[i] != ',') i++
                } else {
                    result += token to mutableMapOf()
                }
            }
            return result
        }

        private fun hashFor(algorithm: String): (String) -> String {
            val name = if (algorithm.startsWith("SHA-256", ignoreCase = true)) "SHA-256" else "MD5"
            return { text ->
                MessageDigest.getInstance(name).digest(text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            }
        }

        private fun cnonce(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
