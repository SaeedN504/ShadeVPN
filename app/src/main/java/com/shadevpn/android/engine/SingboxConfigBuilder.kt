package com.shadevpn.android.engine

import com.shadevpn.android.model.CatalogProfile
import com.shadevpn.android.model.ProfileSecrets
import com.shadevpn.android.model.Protocol
import org.json.JSONArray
import org.json.JSONObject

object SingboxConfigBuilder {
    fun build(profile: CatalogProfile, secrets: ProfileSecrets): Result<String> = runCatching {
        require(profile.serverAddress.isNotBlank()) { "Server address is required" }
        require(profile.serverPort in 1..65535) { "Server port is invalid" }

        val config = JSONObject()
            .put("log", JSONObject().put("level", "error"))
            .put("dns", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("tag", "remote").put("address", "https://1.1.1.1/dns-query").put("detour", "proxy")
            )))
            .put("inbounds", JSONArray().put(
                JSONObject()
                    .put("type", "tun")
                    .put("tag", "tun-in")
                    .put("interface_name", "shadevpn-tun")
                    .put("inet4_address", "172.19.0.1/30")
                    .put("mtu", 1500)
                    .put("auto_route", true)
                    .put("strict_route", true)
            ))
            .put("outbounds", JSONArray().put(outbound(profile, secrets)).put(
                JSONObject().put("type", "dns").put("tag", "dns-out")
            ))
            .put("route", JSONObject()
                .put("auto_detect_interface", true)
                .put("final", "proxy")
                .put("rules", JSONArray().put(
                    JSONObject().put("protocol", "dns").put("action", "hijack-dns")
                ))
            )
            .toString()
        require(JSONObject(config).getJSONArray("outbounds").length() == 2) { "Invalid generated config" }
        config
    }

    private fun outbound(profile: CatalogProfile, secrets: ProfileSecrets): JSONObject {
        return when (profile.protocol) {
            Protocol.VLESS -> {
                val uuid = requireNotNull(secrets.uuid) { "VLESS UUID is missing" }
                val publicKey = requireNotNull(secrets.publicKey) { "Reality public key is missing" }
                val shortId = requireNotNull(secrets.shortId) { "Reality short ID is missing" }
                JSONObject()
                    .put("type", "vless")
                    .put("tag", "proxy")
                    .put("server", profile.serverAddress)
                    .put("server_port", profile.serverPort)
                    .put("uuid", uuid)
                    .put("network", profile.network)
                    .put("tls", JSONObject()
                        .put("enabled", true)
                        .put("server_name", profile.sni ?: profile.serverAddress)
                        .put("utls", JSONObject().put("enabled", true).put("fingerprint", profile.fingerprint ?: "chrome"))
                        .put("reality", JSONObject().put("enabled", true).put("public_key", publicKey).put("short_id", shortId))
                    )
                    .apply {
                        profile.host?.let { put("transport", JSONObject().put("type", "ws").put("path", profile.path ?: "/").put("headers", JSONObject().put("Host", it))) }
                    }
            }
            Protocol.SHADOWSOCKS_2022 -> {
                val password = requireNotNull(secrets.password) { "Shadowsocks password is missing" }
                JSONObject().put("type", "shadowsocks").put("tag", "proxy").put("server", profile.serverAddress).put("server_port", profile.serverPort).put("method", "2022-blake3-aes-256-gcm").put("password", password)
            }
            Protocol.TROJAN -> {
                val password = requireNotNull(secrets.password) { "Trojan password is missing" }
                JSONObject().put("type", "trojan").put("tag", "proxy").put("server", profile.serverAddress).put("server_port", profile.serverPort).put("password", password).put("tls", JSONObject().put("enabled", true).put("server_name", profile.sni ?: profile.serverAddress))
            }
        }
    }
}
