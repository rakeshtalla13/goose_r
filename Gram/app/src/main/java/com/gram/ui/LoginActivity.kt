package com.gram.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.gram.api.ClaudeApiService
import com.gram.databinding.ActivityLoginBinding
import com.gram.utils.SecurePrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var securePrefs: SecurePrefsManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        securePrefs = SecurePrefsManager(this)

        // Already signed in — skip login
        if (securePrefs.isLoggedIn()) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
    }

    private fun setupUI() {
        binding.btnGetApiKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/")))
        }

        binding.btnSignIn.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (key.isBlank()) {
                binding.tilApiKey.error = "Please enter your API key"
                return@setOnClickListener
            }
            if (!key.startsWith("sk-ant-")) {
                binding.tilApiKey.error = "API key should start with  sk-ant-…"
                return@setOnClickListener
            }
            binding.tilApiKey.error = null
            validateAndLogin(key)
        }
    }

    private fun validateAndLogin(key: String) {
        setLoading(true)
        scope.launch {
            ClaudeApiService(key).validateKey()
                .onSuccess {
                    securePrefs.apiKey = key
                    goToMain()
                }
                .onFailure { e ->
                    setLoading(false)
                    binding.tilApiKey.error = when {
                        e.message?.contains("Invalid API key") == true ->
                            "Invalid API key — please check and try again"
                        else -> "Could not connect. Check your internet connection."
                    }
                }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSignIn.isEnabled = !loading
        binding.btnGetApiKey.isEnabled = !loading
        binding.etApiKey.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignIn.text = if (loading) "" else "Sign In"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
