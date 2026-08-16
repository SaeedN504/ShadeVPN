package com.shadevpn.android.service

import android.content.Context
import android.content.Intent
import android.net.VpnService

object ShadeVpnServiceController {
    const val ACTION_START = "com.shadevpn.android.action.START"
    const val ACTION_STOP = "com.shadevpn.android.action.STOP"
    const val EXTRA_PROFILE = "profile"

    fun start(context: Context, profile: String) {
        val intent = Intent(context, com.shadevpn.android.ShadeVpnService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_PROFILE, profile)
        }
        context.startService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, com.shadevpn.android.ShadeVpnService::class.java).apply {
            action = ACTION_STOP
        }
        context.startService(intent)
    }

    fun permissionIntent(context: Context): Intent? = VpnService.prepare(context)
}
