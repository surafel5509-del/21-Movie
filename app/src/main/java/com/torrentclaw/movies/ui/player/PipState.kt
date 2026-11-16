package com.torrentclaw.movies.ui.player

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the activity is currently in picture-in-picture.
 *
 * Provided by `MainActivity` (the only place the Activity callback is available)
 * and read by the player to simplify its overlay: in a small floating window the
 * controls are hidden so the video gets every pixel.
 */
val LocalPipState = compositionLocalOf { false }
