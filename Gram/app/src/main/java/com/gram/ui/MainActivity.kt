package com.gram.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gram.R
import com.gram.databinding.ActivityMainBinding
import com.gram.service.FloatingOverlayService
import com.gram.utils.PrefsManager
import com.gram.utils.SecurePrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var securePrefs: SecurePrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)
        securePrefs = SecurePrefsManager(this)

        showSignedInAccount()
        setupLogout()
        setupPermissionCards()
        setupAppToggles()
        setupOverlayToggle()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun showSignedInAccount() {
        val key = securePrefs.apiKey
        if (key.isNotBlank()) {
            // Show a masked key hint, e.g. "sk-ant-…xK3F"
            val hint = key.take(10) + "…" + key.takeLast(4)
            binding.tvAccountHint.text = hint
            binding.tvAccountHint.visibility = View.VISIBLE
        }
    }

    private fun setupLogout() {
        binding.btnSignOut.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("You'll need to re-enter your API key to use Gram.")
                .setPositiveButton("Sign out") { _, _ ->
                    stopOverlayService()
                    securePrefs.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupPermissionCards() {
        binding.cardAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.cardOverlayPermission.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun setupAppToggles() {
        binding.switchLinkedIn.isChecked = prefs.linkedInEnabled
        binding.switchLinkedIn.setOnCheckedChangeListener { _, checked ->
            prefs.linkedInEnabled = checked
        }
        binding.switchTwitter.isChecked = prefs.twitterEnabled
        binding.switchTwitter.setOnCheckedChangeListener { _, checked ->
            prefs.twitterEnabled = checked
        }
        binding.switchInstagram.isChecked = prefs.instagramEnabled
        binding.switchInstagram.setOnCheckedChangeListener { _, checked ->
            prefs.instagramEnabled = checked
        }
    }

    private fun setupOverlayToggle() {
        binding.switchOverlay.isChecked = prefs.isOverlayEnabled
        binding.switchOverlay.setOnCheckedChangeListener { _, checked ->
            prefs.isOverlayEnabled = checked
            if (checked) startOverlayService() else stopOverlayService()
        }
    }

    private fun refreshPermissionStatus() {
        val accessibilityEnabled = isAccessibilityEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)

        binding.tvAccessibilityStatus.text = if (accessibilityEnabled) "Enabled" else "Tap to enable"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (accessibilityEnabled) R.color.status_green else R.color.status_orange)
        )
        binding.tvOverlayStatus.text = if (overlayEnabled) "Enabled" else "Tap to enable"
        binding.tvOverlayStatus.setTextColor(
            getColor(if (overlayEnabled) R.color.status_green else R.color.status_orange)
        )
        binding.statusBanner.visibility = if (accessibilityEnabled && overlayEnabled) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/com.gram.service.GrammarAccessibilityService"
        return try {
            val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            if (enabled == 1) {
                val services = Settings.Secure.getString(
                    contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                services.split(':').any { it.equals(service, ignoreCase = true) }
            } else false
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }

    private fun startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_START
        })
    }

    private fun stopOverlayService() {
        startService(Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_STOP
        })
    }
}
