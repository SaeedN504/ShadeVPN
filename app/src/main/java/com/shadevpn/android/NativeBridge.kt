package com.shadevpn.android

object NativeBridge {
    init {
        runCatching { System.loadLibrary("shadevpn_native") }
            .onFailure { throw UnsatisfiedLinkError("Failed to load shadevpn_native: ${it.message}") }
    }

    external fun nativeVersion(): String
    external fun nativeBuildLane(profileJson: String): String
    external fun nativeValidateProfile(profileJson: String): String

    /**
     * Bounded TCP reachability only. A positive result is not Connected and
     * must be followed by the real TLS/VLESS handshake and data-plane probe.
     */
    external fun nativeProbeControlPlane(profileJson: String): String
}
