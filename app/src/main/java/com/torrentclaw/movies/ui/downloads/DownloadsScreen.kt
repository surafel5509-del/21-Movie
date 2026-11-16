package com.torrentclaw.movies.ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.R
import com.torrentclaw.movies.data.local.DownloadEntity
import com.torrentclaw.movies.data.local.DownloadKind
import com.torrentclaw.movies.data.local.DownloadStatus
import com.torrentclaw.movies.ui.components.EmptyState
import com.torrentclaw.movies.ui.components.Formatters
import com.torrentclaw.movies.ui.components.PosterImage
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.theme.extendedColors
import androidx.compose.ui.res.stringResource

/**
 * Downloads: live queue with byte-level progress, pause/resume/retry, and a
 * handoff button that opens a finished `.torrent` in the user's client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.downloads_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    },
                    actions = {
                        if (uiState.completed.isNotEmpty() || uiState.failed.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearFinished() }) {
                                Text(stringResource(R.string.downloads_clear_finished))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                if (!uiState.isEmpty) {
                    Text(
                        text = stringResource(
                            R.string.downloads_summary,
                            Formatters.fileSize(uiState.totalCompletedBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isEmpty) {
            EmptyState(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.downloads_empty_title),
                message = stringResource(R.string.downloads_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (uiState.active.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.downloads_active)) }
                    items(uiState.active, key = { it.id }) { entity ->
                        ActiveDownloadCard(
                            entity = entity,
                            onPause = { viewModel.pause(entity.id) },
                            onResume = { viewModel.resume(entity.id) },
                            onRetry = { viewModel.retry(entity.id) },
                            onCancel = { viewModel.remove(entity.id) },
                        )
                    }
                }

                if (uiState.completed.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.downloads_completed)) }
                    items(uiState.completed, key = { it.id }) { entity ->
                        FinishedDownloadCard(
                            entity = entity,
                            onOpen = { viewModel.openTorrent(entity) },
                            onRemove = { viewModel.remove(entity.id) },
                        )
                    }
                }

                if (uiState.failed.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.downloads_failed)) }
                    items(uiState.failed, key = { it.id }) { entity ->
                        FailedDownloadCard(
                            entity = entity,
                            onRetry = { viewModel.retry(entity.id) },
                            onRemove = { viewModel.remove(entity.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    entity: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val status = DownloadStatus.fromStorage(entity.status)
    val running = status == DownloadStatus.ACTIVE || status == DownloadStatus.QUEUED

    Surface(
        shape = CardShapes.release,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterImage(
                url = entity.posterUrl,
                contentDescription = null,
                fallbackTitle = entity.movieTitle,
                modifier = Modifier
                    .size(width = 52.dp, height = 78.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.movieTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    entity.quality?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = if (entity.kind == DownloadKind.TORRENT_FILE.name) {
                            stringResource(R.string.download_kind_torrent)
                        } else {
                            stringResource(R.string.download_kind_media)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (entity.totalBytes > 0) entity.progress else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = buildProgressLabel(entity),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (running && entity.bytesPerSecond > 0) {
                        Text(
                            text = "${Formatters.fileSize(entity.bytesPerSecond)}/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    running -> IconButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.download_pause))
                    }
                    status == DownloadStatus.PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.download_resume))
                    }
                    else -> IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.download_retry))
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.download_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishedDownloadCard(
    entity: DownloadEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val extended = MaterialTheme.extendedColors
    Surface(
        shape = CardShapes.release,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, extended.swarmStrong.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = extended.swarmStrong,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.movieTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        Formatters.fileSize(entity.totalBytes),
                        entity.localPath,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entity.kind == DownloadKind.TORRENT_FILE.name) {
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.download_open),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.download_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FailedDownloadCard(
    entity: DownloadEntity,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val extended = MaterialTheme.extendedColors
    Surface(
        shape = CardShapes.release,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, extended.swarmWeak.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = extended.swarmWeak,
                modifier = Modifier.size(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.movieTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entity.errorMessage ?: stringResource(R.string.download_failed_generic),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.download_retry))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_cancel))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

private fun buildProgressLabel(entity: DownloadEntity): String {
    val downloaded = Formatters.fileSize(entity.downloadedBytes)
    return if (entity.totalBytes > 0) {
        val total = Formatters.fileSize(entity.totalBytes)
        val percent = (entity.progress * 100).toInt()
        "$downloaded / $total  ($percent%)"
    } else {
        downloaded
    }
}
