package com.shadevpn.android.service

import com.shadevpn.android.NativeBridge
import com.shadevpn.android.model.ConnectionPhase
import com.shadevpn.android.model.ConnectionSnapshot
import com.shadevpn.android.model.FailureReason
import com.shadevpn.android.model.VlessProfile
import com.shadevpn.android.parser.VlessProfileParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionOrchestrator {
    private val _state = MutableStateFlow(
        ConnectionSnapshot(nativeVersion = runCatching { NativeBridge.nativeVersion() }.getOrDefault("unavailable"))
    )
    val state: StateFlow<ConnectionSnapshot> = _state.asStateFlow()

    fun markPermissionRequested() = mutate { copy(phase = ConnectionPhase.REQUESTING_PERMISSION, statusLine = "VPN permission requested") }

    fun onPermissionResult(granted: Boolean) {
        mutate {
            copy(
                permissionGranted = granted,
                phase = if (granted) ConnectionPhase.PREPARING else ConnectionPhase.FAILED,
                statusLine = if (granted) "Permission granted, preparing tunnel" else "VPN permission denied",
                failureReason = if (granted) FailureReason.NONE else FailureReason.PERMISSION_DENIED
            )
        }
    }

    fun loadProfile(rawProfile: String): Result<VlessProfile> {
        val result = VlessProfileParser.parse(rawProfile)
        result.onSuccess { profile ->
            mutate {
                copy(
                    selectedProfile = profile,
                    activeLane = "vless-reality",
                    statusLine = "Profile loaded: ${profile.name}",
                    failureReason = FailureReason.NONE
                )
            }
        }.onFailure {
            mutate {
                copy(
                    phase = ConnectionPhase.FAILED,
                    statusLine = "Invalid Reality profile",
                    failureReason = FailureReason.INVALID_PROFILE
                )
            }
        }
        return result
    }

    fun establishTun(fd: Int) {
        mutate {
            copy(
                tunEstablished = fd >= 0,
                phase = if (fd >= 0) ConnectionPhase.CONNECTING_CONTROL else ConnectionPhase.FAILED,
                statusLine = if (fd >= 0) "TUN established, starting control plane" else "Failed to establish TUN",
                failureReason = if (fd >= 0) FailureReason.NONE else FailureReason.TUN_SETUP_FAILED
            )
        }
    }

    fun buildRealityLane(): Result<String> {
        val profile = state.value.selectedProfile ?: return Result.failure(IllegalStateException("No profile selected"))
        return runCatching {
            val payload = VlessProfileParser.toSanitizedJson(profile)
            NativeBridge.nativeBuildLane(payload)
        }.onSuccess {
            mutate { copy(phase = ConnectionPhase.CONNECTING_CONTROL, controlPlaneReady = false, dataPlaneReady = false, statusLine = "Reality lane prepared") }
        }.onFailure {
            mutate { copy(phase = ConnectionPhase.FAILED, statusLine = "Native lane build failed", failureReason = FailureReason.JNI_ERROR) }
        }
    }

    fun markControlPlaneReady() = mutate {
        copy(phase = ConnectionPhase.PROBING_DATA, controlPlaneReady = true, statusLine = "Control plane ready, probing data path")
    }

    fun markDataPlaneReady() = mutate {
        copy(phase = ConnectionPhase.CONNECTED, dataPlaneReady = true, statusLine = "Connected after verified data-plane probe", failureReason = FailureReason.NONE)
    }

    fun fail(reason: FailureReason, message: String) = mutate {
        copy(phase = ConnectionPhase.FAILED, statusLine = message, failureReason = reason, controlPlaneReady = false, dataPlaneReady = false)
    }

    fun revoke() = mutate {
        copy(phase = ConnectionPhase.DISCONNECTED, statusLine = "VPN permission revoked", failureReason = FailureReason.VPN_REVOKED, tunEstablished = false, controlPlaneReady = false, dataPlaneReady = false)
    }

    private fun mutate(block: ConnectionSnapshot.() -> ConnectionSnapshot) {
        _state.value = _state.value.block()
    }
}
