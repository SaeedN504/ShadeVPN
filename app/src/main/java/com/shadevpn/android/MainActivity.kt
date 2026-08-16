package com.shadevpn.android

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ShadeVpnApp { requestVpnPermission() } } }
    private fun requestVpnPermission() { VpnService.prepare(this)?.let { startActivityForResult(it, 100) } }
}

@Composable
private fun ShadeVpnApp(onPermission: () -> Unit) {
    var status by remember { mutableStateOf("Not connected") }
    MaterialTheme { Scaffold { padding -> Column(Modifier.padding(padding).padding(24.dp)) {
        Text("ShadeVPN", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp)); Text("Iran-first Android client foundation")
        Spacer(Modifier.height(24.dp)); Text("Status: $status")
        Spacer(Modifier.height(16.dp)); Button(onClick = { onPermission(); status = "VPN permission requested" }) { Text("Set up VPN permission") }
        Spacer(Modifier.height(12.dp)); Text("No connection is reported until a real data-plane probe succeeds.")
    } } }
}
