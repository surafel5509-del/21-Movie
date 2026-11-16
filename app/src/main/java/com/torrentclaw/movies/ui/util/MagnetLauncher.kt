package com.torrentclaw.movies.ui.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.getSystemService

/**
 * Result of an "Open magnet" attempt, so the caller can react in Compose
 * (snackbar + clipboard fallback) instead of showing an Android Toast.
 */
sealed interface MagnetOpenResult {
    data object Launched : MagnetOpenResult

    /** No torrent client handled the URI. The magnet was copied as a fallback. */
    data class NoClientAvailable(val copied: Boolean) : MagnetOpenResult

    /** The magnet string itself was malformed. */
    data object InvalidLink : MagnetOpenResult

    /** The system refused the intent for another reason. */
    data class Failed(val reason: String) : MagnetOpenResult
}

/**
 * Thin, testable boundary around the platform: building intents, launching
 * them and touching the clipboard.
 *
 * Keeping this out of the composables means the screens stay declarative and
 * the ACTION_VIEW contract lives in exactly one place.
 */
object MagnetLauncher {

    fun openMagnet(context: Context, magnetUri: String, autoCopyOnFailure: Boolean = true): MagnetOpenResult {
        if (!magnetUri.startsWith("magnet:?", ignoreCase = true)) {
            return MagnetOpenResult.InvalidLink
        }

        val uri = Uri.parse(magnetUri)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return try {
            // resolveActivity is null when the package manager sees no handler —
            // the <queries> entry in the manifest is what makes this reliable on
            // Android 11+. We still guard with a try/catch for the race where a
            // client is uninstalled between resolve and start.
            if (intent.resolveActivity(context.packageManager) == null) {
                val copied = if (autoCopyOnFailure) copyToClipboard(context, magnetUri) != null else false
                MagnetOpenResult.NoClientAvailable(copied)
            } else {
                context.startActivity(intent)
                MagnetOpenResult.Launched
            }
        } catch (e: ActivityNotFoundException) {
            val copied = if (autoCopyOnFailure) copyToClipboard(context, magnetUri) != null else false
            MagnetOpenResult.NoClientAvailable(copied)
        } catch (e: SecurityException) {
            MagnetOpenResult.Failed(e.message ?: "The system blocked opening this link.")
        } catch (e: Exception) {
            MagnetOpenResult.Failed(e.message ?: "Couldn't open the magnet link.")
        }
    }

    /** Same handler check without launching anything — used to pick UI wording. */
    fun hasTorrentClient(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("magnet:?xt=urn:btih:0")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    /** Returns the copied text, or null when the clipboard is unavailable. */
    fun copyToClipboard(context: Context, label: String, text: String = label): String? {
        val manager = context.getSystemService<ClipboardManager>() ?: return null
        return try {
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            // Android 13+ shows its own clipboard confirmation; we only report below that.
            text
        } catch (e: IllegalStateException) {
            Toast.makeText(context, "Clipboard is busy, try again", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
