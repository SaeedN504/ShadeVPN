package com.shadevpn.android.parser

import android.net.Uri
import com.shadevpn.android.model.CatalogProfile
import com.shadevpn.android.model.ProfileSecrets
import com.shadevpn.android.model.Protocol
import com.shadevpn.android.model.VlessProfile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object VlessProfileParser {
    fun parse(uri: String): Result<VlessProfile> = runCatching {
        require(uri.startsWith("vless://")) { "Only vless:// profiles are supported" }
        val parsed = Uri.parse(uri)
        val uuid = parsed.userInfo?.takeIf { it.isNotBlank() } ?: error("Missing UUID")
        val host = parsed.host?.takeIf { it.isNotBlank() } ?: error("Missing host")
        val port = parsed.port.takeIf { it > 0 } ?: 443
        val params = parsed.queryParameterNames.associateWith { key -> parsed.getQueryParameter(key).orEmpty() }
        val fragment = parsed.fragment?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        val security = params["security"].orEmpty().ifBlank { "reality" }
        require(security.equals("reality", ignoreCase = true)) { "Only Reality profiles are supported" }
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
            fingerprint = params["fp"]?.ifBlank { null } ?: params["fingerprint"]?.ifBlank { null },
        )
    }

    fun toCatalogProfile(profile: VlessProfile, id: String = UUID.randomUUID().toString()): CatalogProfile =
        CatalogProfile(
            id = id,
            name = profile.name,
            serverAddress = profile.serverAddress,
            serverPort = profile.serverPort,
            protocol = Protocol.VLESS,
            network = profile.network,
            sni = profile.sni,
            host = profile.host,
            path = profile.path,
            fingerprint = profile.fingerprint,
            createdAtEpochMillis = System.currentTimeMillis(),
        )

    /** Exports a portable URI. Secret material is supplied only at export time. */
    fun toUri(profile: CatalogProfile, secrets: ProfileSecrets): String {
        require(profile.protocol == Protocol.VLESS) { "Catalog profile is not VLESS" }
        val uuid = requireNotNull(secrets.uuid) { "VLESS UUID is missing" }
        val query = buildList {
            add("security=${encode("reality")}")
            add("type=${encode(profile.network)}")
            profile.sni?.let { add("sni=${encode(it)}") }
            profile.host?.let { add("host=${encode(it)}") }
            profile.path?.let { add("path=${encode(it)}") }
            secrets.publicKey?.let { add("pbk=${encode(it)}") }
            secrets.shortId?.let { add("sid=${encode(it)}") }
            profile.fingerprint?.let { add("fp=${encode(it)}") }
        }.joinToString("&")
        val label = URLEncoder.encode(profile.name, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "vless://$uuid@${profile.serverAddress}:${profile.serverPort}?$query#$label"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
