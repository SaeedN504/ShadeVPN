package com.shadevpn.android.model

/** Safe-to-display profile metadata. Secret values live in ProfileSecretStore. */
data class CatalogProfile(
    val id: String,
    val name: String,
    val serverAddress: String,
    val serverPort: Int,
    val protocol: Protocol,
    val network: String,
    val sni: String?,
    val host: String?,
    val path: String?,
    val fingerprint: String?,
    val createdAtEpochMillis: Long,
)

enum class Protocol { VLESS, SHADOWSOCKS_2022, TROJAN }

data class ProfileSecrets(
    val uuid: String? = null,
    val password: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
)

data class CatalogHealth(
    val attempts: Int = 0,
    val successes: Int = 0,
    val p50RoundTripMillis: Long? = null,
    val p95RoundTripMillis: Long? = null,
    val carrier: String? = null,
    val asn: String? = null,
    val lastKnownGoodEpochMillis: Long? = null,
)

data class CatalogEntry(
    val profile: CatalogProfile,
    val health: CatalogHealth = CatalogHealth(),
)
