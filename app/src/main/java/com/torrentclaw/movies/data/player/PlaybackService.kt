package com.torrentclaw.movies.data.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media3 playback service.
 *
 * This is the reason "watch the movie, leave the app, come back and continue"
 * works without a hack: playback belongs to a foreground service, not to the
 * player screen's ViewModel. Navigating away from the player composable, opening
 * another app or entering picture-in-picture all keep the same [ExoPlayer]
 * instance alive, still playing, still remembering its position.
 *
 * If Android does kill the process anyway, the resume position in Room brings the
 * viewer back to the same second — see [PlaybackRepository].
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // "Movie": keeps audio focus behaviour correct, and lets other
                    // apps duck instead of stopping the film.
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // keep decoding with the screen off
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * The user swiped the app away. Media3's documented behaviour is to stop
     * playback in that case unless the session is explicitly kept; a video that
     * keeps playing after the task is closed is surprising, so playback stops and
     * the position is already saved.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && player.playbackState != Player.STATE_IDLE) {
            player.pause()
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Tapping the media notification opens the app's player screen. */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, com.torrentclaw.movies.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
