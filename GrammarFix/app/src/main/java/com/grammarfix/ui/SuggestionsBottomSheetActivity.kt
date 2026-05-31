package com.grammarfix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.grammarfix.R
import com.grammarfix.databinding.ActivitySuggestionsBinding
import com.grammarfix.service.FloatingOverlayService

class SuggestionsBottomSheetActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuggestionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent activity that hosts a bottom sheet
        setTheme(R.style.Theme_GrammarFix_BottomSheet)
        binding = ActivitySuggestionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val originalText = intent.getStringExtra(FloatingOverlayService.EXTRA_ORIGINAL_TEXT) ?: ""
        val platform = intent.getStringExtra(FloatingOverlayService.EXTRA_PLATFORM) ?: "Social"
        val corrected = intent.getStringExtra(FloatingOverlayService.EXTRA_GRAMMAR_RESULT) ?: ""
        val rewritten = intent.getStringExtra("extra_rewritten") ?: ""
        val summary = intent.getStringExtra("extra_summary") ?: ""

        setupUI(originalText, platform, corrected, rewritten, summary)
    }

    private fun setupUI(
        original: String,
        platform: String,
        corrected: String,
        rewritten: String,
        summary: String
    ) {
        binding.tvPlatform.text = platform
        binding.tvSummary.text = summary
        binding.tvOriginal.text = original

        // Corrected tab
        binding.tvCorrected.text = corrected
        binding.btnCopyCorrected.setOnClickListener {
            copyToClipboard(corrected)
        }

        // Rewritten tab
        binding.tvRewritten.text = rewritten
        binding.btnCopyRewritten.setOnClickListener {
            copyToClipboard(rewritten)
        }

        binding.btnClose.setOnClickListener { finish() }

        // Tab switching
        binding.tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnTabCorrected -> {
                    binding.cardCorrected.visibility = View.VISIBLE
                    binding.cardRewritten.visibility = View.GONE
                }
                R.id.btnTabRewritten -> {
                    binding.cardCorrected.visibility = View.GONE
                    binding.cardRewritten.visibility = View.VISIBLE
                }
            }
        }

        binding.btnTabCorrected.isChecked = true
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GrammarFix", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
