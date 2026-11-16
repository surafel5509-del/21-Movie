package com.torrentclaw.movies.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback wrapper that respects the user's preference and the platform
 * defaults: `CONFIRM` on API 30+, `VIRTUAL_KEY` before that.
 */
@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

class Haptics(
    private val view: android.view.View,
    private val enabled: Boolean,
) {

    /** Copy / bookmark confirmation. */
    fun confirm() {
        if (!enabled) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    /** Slightly stronger pulse for opening a magnet / removing an item. */
    fun impact() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
