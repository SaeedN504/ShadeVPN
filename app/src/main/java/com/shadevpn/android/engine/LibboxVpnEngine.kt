package com.shadevpn.android.engine

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin adapter seam for the libbox AAR.
 *
 * This intentionally fails closed until the pinned libbox artifact and its
 * generated API are added. No fake Connected state is permitted here.
 */
class LibboxVpnEngine(
    private val runtime: LibboxRuntime,
) : VpnEngine {
    private val mutableState = MutableStateFlow(EngineState.STOPPED)
    override val state: StateFlow<EngineState> = mutableState.asStateFlow()

    override suspend fun start(
        configJson: String,
        tun: ParcelFileDescriptor,
    ): Result<Unit> = runCatching {
        mutableState.value = EngineState.STARTING
        runtime.start(configJson, tun)
        mutableState.value = EngineState.RUNNING
    }.onFailure {
        mutableState.value = EngineState.FAILED
    }

    override suspend fun verifyDataPlane(): Result<DataPlaneProof> = runCatching {
        runtime.verifyDataPlane().also { proof ->
            check(proof.verified) { "libbox data-plane proof failed: ${proof.detail}" }
        }
    }

    override suspend fun stop() {
        runCatching { runtime.stop() }
        mutableState.value = EngineState.STOPPED
    }
}

/** Implemented by the pinned libbox AAR adapter in the next engine task. */
interface LibboxRuntime {
    fun start(configJson: String, tun: ParcelFileDescriptor)
    fun verifyDataPlane(): DataPlaneProof
    fun stop()
}
