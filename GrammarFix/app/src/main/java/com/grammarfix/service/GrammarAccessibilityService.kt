package com.grammarfix.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GrammarAccessibilityService : AccessibilityService() {

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_PLATFORM = "extra_platform"

        private val SUPPORTED_PACKAGES = mapOf(
            "com.linkedin.android" to "LinkedIn",
            "com.twitter.android" to "X (Twitter)",
            "com.x.android" to "X (Twitter)",
            "com.instagram.android" to "Instagram"
        )

        private const val MIN_TEXT_LENGTH = 20
    }

    private var lastSentText = ""
    private var currentPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        startFloatingOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return
        if (!SUPPORTED_PACKAGES.containsKey(packageName)) return

        currentPackage = packageName

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString("").trim()
                if (text.length >= MIN_TEXT_LENGTH && text != lastSentText) {
                    notifyOverlay(text, packageName)
                }
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val node = event.source ?: return
                if (node.isEditable) {
                    val text = node.text?.toString()?.trim() ?: ""
                    if (text.length >= MIN_TEXT_LENGTH) {
                        notifyOverlay(text, packageName)
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                broadcastPackageChanged(packageName)
            }
            else -> Unit
        }
    }

    override fun onInterrupt() {
        stopFloatingOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFloatingOverlay()
    }

    private fun notifyOverlay(text: String, packageName: String) {
        lastSentText = text
        val platform = SUPPORTED_PACKAGES[packageName] ?: return
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_TEXT_DETECTED
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_PLATFORM, platform)
        }
        startService(intent)
    }

    private fun broadcastPackageChanged(packageName: String) {
        val platform = SUPPORTED_PACKAGES[packageName] ?: return
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_PACKAGE_CHANGED
            putExtra(EXTRA_PLATFORM, platform)
        }
        startService(intent)
    }

    private fun startFloatingOverlay() {
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopFloatingOverlay() {
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_STOP
        }
        startService(intent)
    }
}
