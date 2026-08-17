package com.shadevpn.android.engine

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.StateFlow

/**
 * App-facing contract for the embedded VPN engine.
 *
 * The UI and orchestration layer depend on this contract only. The concrete
 * libbox adapter is the only code allowed to know the AAR API.
 */
interface VpnEngine {
    val state: StateFlow<EngineState>

    suspend fun start(
        configJson: String,
        tun: ParcelFileDescriptor,
    ): Result<Unit>

    suspend fun verifyDataPlane(): Result<DataPlaneProof>

    suspend fun stop()
}

enum class EngineState {
    STOPPED,
    STARTING,
    RUNNING,
    FAILED,
}

data class DataPlaneProof(
    val verified: Boolean,
    val roundTripMillis: Long?,
    val detail: String,
)
