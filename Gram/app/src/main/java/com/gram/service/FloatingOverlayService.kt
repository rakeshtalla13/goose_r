package com.gram.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.gram.R
import com.gram.api.ClaudeApiService
import com.gram.api.GrammarResult
import com.gram.ui.MainActivity
import com.gram.ui.SuggestionsBottomSheetActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val ACTION_TEXT_DETECTED = "action_text_detected"
        const val ACTION_PACKAGE_CHANGED = "action_package_changed"

        const val EXTRA_GRAMMAR_RESULT = "extra_grammar_result"
        const val EXTRA_ORIGINAL_TEXT = "extra_original_text"
        const val EXTRA_PLATFORM = "extra_platform"

        private const val NOTIFICATION_CHANNEL_ID = "gram_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val DEBOUNCE_MS = 1500L
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val claudeApi = ClaudeApiService()
    private var analysisJob: Job? = null

    private var currentText = ""
    private var currentPlatform = "LinkedIn"
    private var isAnalyzing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                showFloatingBubble()
            }
            ACTION_STOP -> {
                removeFloatingBubble()
                stopSelf()
            }
            ACTION_TEXT_DETECTED -> {
                currentText = intent.getStringExtra(GrammarAccessibilityService.EXTRA_TEXT) ?: return START_STICKY
                currentPlatform = intent.getStringExtra(GrammarAccessibilityService.EXTRA_PLATFORM) ?: currentPlatform
                scheduleAnalysis()
            }
            ACTION_PACKAGE_CHANGED -> {
                currentPlatform = intent.getStringExtra(EXTRA_PLATFORM) ?: currentPlatform
                updateBubblePlatform()
            }
        }
        return START_STICKY
    }

    private fun showFloatingBubble() {
        if (floatingView != null) return

        floatingView = LayoutInflater.from(this).inflate(R.layout.view_floating_bubble, null)
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 300
        }

        floatingView?.setOnTouchListener(createDragListener())
        floatingView?.setOnClickListener { openSuggestionsIfReady() }

        windowManager.addView(floatingView, bubbleParams)
        updateBubbleState(BubbleState.IDLE)
    }

    private fun removeFloatingBubble() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
    }

    private fun scheduleAnalysis() {
        analysisJob?.cancel()
        updateBubbleState(BubbleState.WAITING)

        analysisJob = coroutineScope.launch {
            delay(DEBOUNCE_MS)
            if (currentText.isBlank()) return@launch
            analyzeText(currentText, currentPlatform)
        }
    }

    private suspend fun analyzeText(text: String, platform: String) {
        updateBubbleState(BubbleState.ANALYZING)
        isAnalyzing = true

        claudeApi.analyzeText(text, platform)
            .onSuccess { result ->
                isAnalyzing = false
                cachedResult = result
                cachedOriginalText = text
                updateBubbleState(if (result.hasErrors) BubbleState.HAS_SUGGESTIONS else BubbleState.ALL_GOOD)
            }
            .onFailure {
                isAnalyzing = false
                updateBubbleState(BubbleState.IDLE)
            }
    }

    private var cachedResult: GrammarResult? = null
    private var cachedOriginalText = ""

    private fun openSuggestionsIfReady() {
        val result = cachedResult ?: return
        val intent = Intent(this, SuggestionsBottomSheetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_ORIGINAL_TEXT, cachedOriginalText)
            putExtra(EXTRA_PLATFORM, currentPlatform)
            putExtra(EXTRA_GRAMMAR_RESULT, result.correctedText)
            putExtra("extra_rewritten", result.rewrittenText)
            putExtra("extra_summary", result.summary)
        }
        startActivity(intent)
    }

    private fun updateBubbleState(state: BubbleState) {
        val view = floatingView ?: return
        val icon = view.findViewById<ImageView>(R.id.bubble_icon)
        val badge = view.findViewById<View>(R.id.bubble_badge)

        coroutineScope.launch(Dispatchers.Main) {
            when (state) {
                BubbleState.IDLE -> {
                    icon.setImageResource(R.drawable.ic_grammar_idle)
                    badge.visibility = View.GONE
                }
                BubbleState.WAITING -> {
                    icon.setImageResource(R.drawable.ic_grammar_idle)
                    badge.visibility = View.GONE
                }
                BubbleState.ANALYZING -> {
                    icon.setImageResource(R.drawable.ic_grammar_loading)
                    badge.visibility = View.GONE
                }
                BubbleState.HAS_SUGGESTIONS -> {
                    icon.setImageResource(R.drawable.ic_grammar_alert)
                    badge.visibility = View.VISIBLE
                }
                BubbleState.ALL_GOOD -> {
                    icon.setImageResource(R.drawable.ic_grammar_ok)
                    badge.visibility = View.GONE
                }
            }
        }
    }

    private fun updateBubblePlatform() {
        cachedResult = null
        updateBubbleState(BubbleState.IDLE)
    }

    private fun createDragListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        return View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams?.x ?: 0
                    initialY = bubbleParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        moved = true
                        bubbleParams?.x = initialX - dx
                        bubbleParams?.y = initialY + dy
                        floatingView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Gram Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the grammar correction bubble active"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Gram is active")
            .setContentText("Tap to open settings")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF6366F1.toInt())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingBubble()
        coroutineScope.cancel()
    }

    enum class BubbleState { IDLE, WAITING, ANALYZING, HAS_SUGGESTIONS, ALL_GOOD }
}
