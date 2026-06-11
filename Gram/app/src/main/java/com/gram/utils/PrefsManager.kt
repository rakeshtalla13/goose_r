package com.gram.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gram_prefs", Context.MODE_PRIVATE)

    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean("overlay_enabled", true)
        set(value) = prefs.edit().putBoolean("overlay_enabled", value).apply()

    var linkedInEnabled: Boolean
        get() = prefs.getBoolean("linkedin_enabled", true)
        set(value) = prefs.edit().putBoolean("linkedin_enabled", value).apply()

    var twitterEnabled: Boolean
        get() = prefs.getBoolean("twitter_enabled", true)
        set(value) = prefs.edit().putBoolean("twitter_enabled", value).apply()

    var instagramEnabled: Boolean
        get() = prefs.getBoolean("instagram_enabled", true)
        set(value) = prefs.edit().putBoolean("instagram_enabled", value).apply()
}
