package com.grammarfix.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.grammarfix.utils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PrefsManager(context).isOverlayEnabled) return

        val overlayIntent = Intent(context, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_START
        }
        context.startForegroundService(overlayIntent)
    }
}
