package com.shadevpn.android

import android.content.Intent
import android.net.VpnService

class ShadeVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { return START_NOT_STICKY }
    override fun onRevoke() { stopSelf(); super.onRevoke() }
}
