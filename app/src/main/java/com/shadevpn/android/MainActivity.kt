package com.shadevpn.android

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shadevpn.android.model.ConnectionPhase
import com.shadevpn.android.model.ConnectionSnapshot
import com.shadevpn.android.model.FailureReason
import com.shadevpn.android.service.ConnectionOrchestrator
import com.shadevpn.android.service.ShadeVpnServiceController

class MainActivity : ComponentActivity() {
    private val orchestrator = ConnectionOrchestrator()

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        orchestrator.onPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by orchestrator.state.collectAsState()
            ShadeVpnApp(
                state = state,
                onPrepare = { requestVpnPermission() },
                onLoadProfile = { raw -> orchestrator.loadProfile(raw) },
                onBuildLane = {
                    orchestrator.buildRealityLane()
                        .onSuccess { orchestrator.markControlPlaneReady() }
                        .onFailure { orchestrator.fail(FailureReason.JNI_ERROR, "Native lane preparation failed") }
                },
                onSimulateProbe = { orchestrator.markDataPlaneReady() },
                onStop = { ShadeVpnServiceController.stop(this) }
            )
        }
    }

    private fun requestVpnPermission() {
        orchestrator.markPermissionRequested()
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            orchestrator.onPermissionResult(true)
        } else {
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }
}

@Composable
private fun ShadeVpnApp(
    state: ConnectionSnapshot,
    onPrepare: () -> Unit,
    onLoadProfile: (String) -> Result<*>,
    onBuildLane: () -> Unit,
    onSimulateProbe: () -> Unit,
    onStop: () -> Unit
) {
    var rawProfile by remember {
        mutableStateOf("vless://00000000-0000-0000-0000-000000000000@example.com:443?security=reality&type=tcp&sni=cdn.example.com&pbk=publicKey&sid=short#ShadeVPN%20Reality")
    }
    MaterialTheme {
        Scaffold {
            padding -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                Text("ShadeVPN", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Milestone 2: orchestration, profile parsing, Reality lane skeleton")
                Spacer(Modifier.height(20.dp))
                StatusCard(state)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = rawProfile,
                    onValueChange = { rawProfile = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reality profile") },
                    minLines = 4,
                    supportingText = { Text("Secrets stay out of logs. Connected stays fake until probe passes.") }
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) { Text("1. Request VPN permission") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onLoadProfile(rawProfile) }, modifier = Modifier.fillMaxWidth()) { Text("2. Parse VLESS + Reality profile") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBuildLane,
                    enabled = state.selectedProfile != null && state.permissionGranted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("3. Build native Reality lane") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSimulateProbe,
                    enabled = state.phase == ConnectionPhase.PROBING_DATA,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("4. Mark data-plane probe success") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop service") }
            }
        }
    }
}

@Composable
private fun StatusCard(state: ConnectionSnapshot) {
    Column {
        Text("Status: ${state.statusLine}")
        Spacer(Modifier.height(6.dp))
        Text("Phase: ${state.phase}")
        Text("Lane: ${state.activeLane}")
        Text("Failure: ${state.failureReason}")
        Text("Native: ${state.nativeVersion}")
        Text("Permission: ${state.permissionGranted}")
        Text("TUN: ${state.tunEstablished}")
        Text("Control probe: ${state.controlPlaneReady}")
        Text("Data probe: ${state.dataPlaneReady}")
        Spacer(Modifier.height(10.dp))
        Text(
            text = when (state.phase) {
                ConnectionPhase.CONNECTED -> "Good: UI only says connected after data-plane proof."
                ConnectionPhase.FAILED -> "Good failure: ${failureText(state.failureReason)}"
                else -> "Still scaffold, but honest scaffold."
            },
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun failureText(reason: FailureReason): String = when (reason) {
    FailureReason.PERMISSION_DENIED -> "user blocked VPN permission"
    FailureReason.INVALID_PROFILE -> "profile parse or validation failed"
    FailureReason.JNI_ERROR -> "native lane skeleton rejected payload"
    FailureReason.CONTROL_PLANE_FAILED -> "handshake failed"
    FailureReason.DATA_PLANE_FAILED -> "probe failed"
    FailureReason.VPN_REVOKED -> "Android revoked the tunnel"
    FailureReason.TUN_SETUP_FAILED -> "builder never produced a TUN fd"
    FailureReason.NONE, FailureReason.UNKNOWN -> "none"
}
