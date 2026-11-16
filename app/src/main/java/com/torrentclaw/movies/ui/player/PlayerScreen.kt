package com.torrentclaw.movies.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.torrentclaw.movies.ui.components.Formatters
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The in-app player.
 *
 * Requirements this satisfies, and how:
 *  - **Plays the downloaded file inside the app** — ExoPlayer over a `file://`
 *    style MediaItem; no hand-off to another app.
 *  - **Picture-in-picture** — [enterPip] shrinks the *same* player to a floating
 *    window, so leaving the app does not stop the movie. The activity keeps its
 *    player; nothing is re-created.
 *  - **Survives leaving and returning** — position is pushed into
 *    [PlaybackRepository] as it changes, and the screen restores it on entry.
 *  - **Resizable/orientation** — free rotation, lock toggle, and a fit/fill toggle
 *    for the video surface.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: PlayerUiSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = session.player

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var inPip by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    val latestPosition by rememberUpdatedState(positionMs)
    val latestDuration by rememberUpdatedState(durationMs)

    // ── player wiring ───────────────────────────────────────────────────────
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    session.onFinished()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Position polling: cheap, and it keeps the slider smooth without a seek
    // listener that would fight the user's drag.
    LaunchedEffect(player) {
        while (true) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (durationMs <= 0) durationMs = player.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    // ── resume: start from the saved second, once metadata is known ─────────
    LaunchedEffect(player, durationMs) {
        if (session.resumeApplied) return@LaunchedEffect
        if (session.startPositionMs <= 0) {
            session.resumeApplied = true
            return@LaunchedEffect
        }
        // Seeking before the player knows the duration lands wrong on some
        // containers, so wait for READY (or 2 s, whichever comes first).
        if (player.playbackState == Player.STATE_READY || durationMs > 0) {
            player.seekTo(session.startPositionMs)
            session.resumeApplied = true
        } else {
            delay(2_000)
            player.seekTo(session.startPositionMs)
            session.resumeApplied = true
        }
    }

    // ── persist the position on every stop and while playing ────────────────
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying && !isScrubbing) {
                session.persist(latestPosition, latestDuration)
            }
            delay(5_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { session.persist(latestPosition, latestDuration) }
    }

    // Pause when the app is genuinely backgrounded without PiP: audio continuing
    // from a video the user cannot see is a bug, not a feature. PiP is the
    // sanctioned way to keep watching, and the media service keeps audio alive
    // for the lock-screen controls.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!inPip && !isInPip(activity)) {
                    player.pause()
                    session.persist(latestPosition, latestDuration)
                }
                Lifecycle.Event.ON_START -> inPip = isInPip(activity)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the screen awake while playing, and auto-hide the controls.
    LaunchedEffect(isPlaying, controlsVisible) {
        activity?.window?.let { window ->
            if (isPlaying) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        if (isPlaying && controlsVisible) {
            delay(4_000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        // ── the video surface ───────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // our own controls, for a consistent look
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = player
                    resizeMode = session.initialResizeMode
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                view.resizeMode = resizeMode
            },
        )

        if (isBuffering) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        // ── controls overlay ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            ) {
                // top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (session.quality != null) {
                            Text(
                                text = session.quality,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = {
                            inPip = enterPip(activity, player, session)
                            if (!inPip) {
                                // No PiP support: fall back to the system player.
                                session.onOpenExternally()
                            }
                        }) {
                            Icon(
                                Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "Picture in picture",
                                tint = Color.White,
                            )
                        }
                    }
                }

                // bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // scrubbable timeline
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTime(positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Slider(
                            value = if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            onValueChange = {
                                isScrubbing = true
                                positionMs = (it * durationMs).toLong()
                                controlsVisible = true
                            },
                            onValueChangeFinished = {
                                player.seekTo(positionMs)
                                isScrubbing = false
                                session.persist(positionMs, durationMs)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        Text(
                            text = formatTime(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(Icons.Rounded.Replay10, "Back 10 seconds") {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0L))
                        }
                        Spacer(Modifier.width(20.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.size(62.dp),
                        ) {
                            IconButton(onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                    session.persist(latestPosition, latestDuration)
                                } else {
                                    player.play()
                                }
                                controlsVisible = true
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        ControlButton(Icons.Rounded.Forward10, "Forward 10 seconds") {
                            val target = (player.currentPosition + 10_000)
                            player.seekTo(
                                if (durationMs > 0) target.coerceAtMost(durationMs) else target,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // secondary controls: fill/fit, rotate, fullscreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fit" else "Fill",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .clickable {
                                        resizeMode =
                                            if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            } else {
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ControlButton(Icons.Rounded.ScreenRotation, "Rotate") {
                                activity?.let { act ->
                                    act.requestedOrientation =
                                        if (act.resources.configuration.orientation ==
                                            Configuration.ORIENTATION_LANDSCAPE
                                        ) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                }
                            }
                        }
                        ControlButton(
                            if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        ) {
                            isFullscreen = !isFullscreen
                            activity?.let { act ->
                                if (isFullscreen) {
                                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    // Hide bars for true immersion.
                                    @Suppress("DEPRECATION")
                                    act.window.decorView.systemUiVisibility =
                                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                } else {
                                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    @Suppress("DEPRECATION")
                                    act.window.decorView.systemUiVisibility =
                                        android.view.View.SYSTEM_UI_FLAG_VISIBLE
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ControlButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture") {
                                inPip = enterPip(activity, player, session)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/**
 * Enters picture-in-picture for the *current* player. Returns true when the
 * request was accepted. The source rect is guessed from the video aspect ratio so
 * the floating window is not letterboxed.
 */
fun enterPip(activity: Activity?, player: Player, session: PlayerUiSession): Boolean {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
        val videoSize = player.videoSize
        val ratio = if (videoSize.width > 0 && videoSize.height > 0) {
            Rational(videoSize.width, videoSize.height)
        } else {
            Rational(16, 9)
        }
        val actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Play/pause/fast-forward/rewind inside the PiP window.
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(activity, android.R.drawable.ic_media_play),
                "Play",
                android.app.PendingIntent.getBroadcast(
                    activity,
                    0,
                    android.content.Intent(PlayerPipReceiver.ACTION_TOGGLE),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            ).let { listOf(it) }
        } else {
            emptyList()
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setActions(actions) }
            .build()
        activity.enterPictureInPictureMode(params)
    }.getOrDefault(false)
}

fun isInPip(activity: Activity?): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        activity?.isInPictureInPictureMode == true

/** Context → Activity, for PiP and orientation requests from a composable. */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 3_725_000 → "1:02:05", 65_000 → "1:05". */
fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * State the player screen needs that must outlive its own composition: the
 * ExoPlayer instance, where to resume, and how to report progress.
 *
 * Deliberately a plain class (not a ViewModel) so the owning ViewModel holds the
 * player across configuration changes and PiP transitions.
 */
class PlayerUiSession(
    val player: ExoPlayer,
    val title: String,
    val quality: String?,
    val filePath: String,
    val startPositionMs: Long,
    val initialResizeMode: Int,
) {
    /** Reset when playback finishes so a re-open starts from the beginning. */
    var resumeApplied: Boolean = false

    var onFinished: () -> Unit = {}
    var onOpenExternally: () -> Unit = {}

    /** Set by the screen's owner; persists the position to Room. */
    var persist: (Long, Long) -> Unit = { _, _ -> }

    fun release() {
        runCatching { player.release() }
    }

    companion object {
        fun buildMediaItem(filePath: String): MediaItem =
            MediaItem.Builder()
                .setUri(java.io.File(filePath).toURI().toString())
                .build()

        /** ExoPlayer needs these; kept here so the ViewModel and screen agree. */
        const val BUFFER_MS = 30_000
    }
}

/**
 * Route wrapper: resolves the download, builds the player, and hands the screen a
 * session. Kept separate from [PlayerScreen] so the composable stays testable and
 * has no ViewModel dependency.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = viewModel.session

    when {
        state.error != null -> {
            PlayerErrorScreen(
                title = state.title,
                message = state.error.orEmpty(),
                onBack = onBack,
            )
        }
        session == null -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
        }
        else -> {
            PlayerScreen(session = session, onBack = onBack)
            // A one-shot "resuming from…" note, so a jump into the middle of a
            // movie is explained rather than surprising.
            if (state.resumedAtMs > 0) {
                var visible by remember(state.resumedAtMs) { mutableStateOf(true) }
                LaunchedEffect(state.resumedAtMs) {
                    kotlinx.coroutines.delay(3_000)
                    visible = false
                }
                if (visible) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.padding(bottom = 120.dp),
                        ) {
                            Text(
                                text = "Resuming from ${formatTime(state.resumedAtMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorScreen(title: String, message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = onBack) { Text("Go back") }
        }
    }
}
