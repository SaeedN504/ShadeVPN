package com.shadevpn.android.parser

import android.net.Uri
import com.shadevpn.android.model.VlessProfile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object VlessProfileParser {
    fun parse(uri: String): Result<VlessProfile> = runCatching {
        require(uri.startsWith("vless://")) { "Only vless:// profiles are supported in milestone 2" }
        val parsed = Uri.parse(uri)
        val uuid = parsed.userInfo?.takeIf { it.isNotBlank() }
            ?: error("Missing UUID")
        val host = parsed.host?.takeIf { it.isNotBlank() }
            ?: error("Missing host")
        val port = parsed.port.takeIf { it > 0 } ?: 443
        val params = parsed.queryParameterNames.associateWith { key -> parsed.getQueryParameter(key).orEmpty() }
        val fragment = parsed.fragment?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        val security = params["security"].orEmpty().ifBlank { "reality" }
        require(security.equals("reality", ignoreCase = true)) { "Milestone 3 only supports Reality" }
        val network = params["type"].orEmpty().ifBlank { "tcp" }
        require(network.lowercase() in setOf("tcp", "ws", "xhttp")) { "Unsupported network type: $network" }

        VlessProfile(
            name = fragment ?: "ShadeVPN Reality",
            serverAddress = host,
            serverPort = port,
            uuid = uuid,
            flow = params["flow"]?.ifBlank { null },
            security = security,
            network = network,
            host = params["host"]?.ifBlank { null },
            path = params["path"]?.ifBlank { null },
            sni = params["sni"]?.ifBlank { null } ?: params["serverName"]?.ifBlank { null },
            publicKey = params["pbk"]?.ifBlank { null } ?: params["publicKey"]?.ifBlank { null },
            shortId = params["sid"]?.ifBlank { null } ?: params["shortId"]?.ifBlank { null },
            fingerprint = params["fp"]?.ifBlank { null } ?: params["fingerprint"]?.ifBlank { null }
        )
    }

    /** Native-only payload. Never log, persist, or display this string. */
    fun toSanitizedJson(profile: VlessProfile): String = buildString {
        append('{')
        append("\"name\":\"").append(escape(profile.name)).append("\",")
        append("\"serverAddress\":\"").append(escape(profile.serverAddress)).append("\",")
        append("\"serverPort\":").append(profile.serverPort).append(',')
        append("\"uuid\":\"").append(escape(profile.uuid)).append("\",")
        append("\"network\":\"").append(escape(profile.network)).append("\",")
        append("\"security\":\"").append(escape(profile.security)).append("\",")
        append("\"sni\":").append(nullable(profile.sni)).append(',')
        append("\"host\":").append(nullable(profile.host)).append(',')
        append("\"path\":").append(nullable(profile.path)).append(',')
        append("\"flow\":").append(nullable(profile.flow)).append(',')
        append("\"publicKeyPresent\":").append(profile.publicKey != null).append(',')
        append("\"shortIdPresent\":").append(profile.shortId != null).append(',')
        append("\"fingerprint\":").append(nullable(profile.fingerprint))
        append('}')
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun nullable(value: String?): String = value?.let { "\"${escape(it)}\"" } ?: "null"
}
