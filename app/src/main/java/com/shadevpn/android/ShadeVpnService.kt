package com.shadevpn.android

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.shadevpn.android.model.FailureReason
import com.shadevpn.android.service.ConnectionOrchestrator
import com.shadevpn.android.service.ShadeVpnServiceController

class ShadeVpnService : VpnService() {
    private val orchestrator = ConnectionOrchestrator()
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ShadeVpnServiceController.ACTION_STOP -> stopTunnel()
            ShadeVpnServiceController.ACTION_START -> startTunnel(intent.getStringExtra(ShadeVpnServiceController.EXTRA_PROFILE))
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(rawProfile: String?) {
        if (rawProfile.isNullOrBlank()) {
            orchestrator.fail(FailureReason.INVALID_PROFILE, "No profile supplied to service")
            stopSelf()
            return
        }
        orchestrator.onPermissionResult(true)
        orchestrator.loadProfile(rawProfile)
            .onFailure {
                stopSelf()
                return
            }
        tunInterface = Builder()
            .setSession("ShadeVPN")
            .addAddress("10.10.0.2", 32)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .establish()

        val fd = tunInterface?.fd ?: -1
        orchestrator.establishTun(fd)
        orchestrator.buildRealityLane()
            .onSuccess { orchestrator.markControlPlaneReady() }
            .onFailure { orchestrator.fail(FailureReason.JNI_ERROR, "Failed to build Reality lane") }
    }

    private fun stopTunnel() {
        tunInterface?.close()
        tunInterface = null
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
        orchestrator.revoke()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }
}
