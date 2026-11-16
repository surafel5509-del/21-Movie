package com.torrentclaw.movies.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * PiP action receiver (Android 13+).
 *
 * Registered in the manifest because `PendingIntent.getBroadcast` needs a
 * concrete component. The toggle itself is handled by the media session, so this
 * receiver intentionally does nothing — a stale action is harmless.
 */
class PlayerPipReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE) {
            // Handled by the active media session; nothing to do here.
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.torrentclaw.movies.action.PIP_TOGGLE"
    }
}
