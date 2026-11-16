package com.torrentclaw.movies.ui.detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.torrentclaw.movies.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.domain.model.TorrentRelease
import com.torrentclaw.movies.ui.components.Formatters
import com.torrentclaw.movies.ui.components.GenreTag
import com.torrentclaw.movies.ui.components.MetaPill
import com.torrentclaw.movies.ui.components.PosterImage
import com.torrentclaw.movies.ui.components.RatingBadge
import com.torrentclaw.movies.ui.components.SkeletonBlock
import com.torrentclaw.movies.ui.components.ErrorState
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.util.MagnetLauncher
import com.torrentclaw.movies.ui.util.rememberHaptics

/**
 * Movie detail: collapsing backdrop header with poster overlay, synopsis meta,
 * bookmark toggle, and the "Available releases" list sorted by swarm health.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
    hapticsEnabled: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effect by viewModel.effect.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics(hapticsEnabled)
    val snackbarHostState = remember { SnackbarHostState() }
    var lastMagnetUri by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
    )

    // Magnet opening lives here (single Activity context, snackbar fallback).
    LaunchedEffect(effect) {
        when (val current = effect) {
            is DetailEffect.OpenMagnet -> {
                lastMagnetUri = current.magnetUri
                haptics.impact()
                val result = MagnetLauncher.openMagnet(context, current.magnetUri)
                when (result) {
                    is MagnetLauncher.MagnetOpenResult.Launched -> viewModel.clearEffect()
                    is MagnetLauncher.MagnetOpenResult.NoClientAvailable -> viewModel.onNoClient()
                    is MagnetLauncher.MagnetOpenResult.InvalidLink -> {
                        snackbarHostState.showSnackbar(context.getString(R.string.detail_open_magnet_malformed))
                        viewModel.clearEffect()
                    }
                    is MagnetLauncher.MagnetOpenResult.Failed -> {
                        snackbarHostState.showSnackbar(result.reason)
                        viewModel.clearEffect()
                    }
                }
            }
            DetailEffect.MagnetCopied -> {
                snackbarHostState.showSnackbar(context.getString(R.string.detail_magnet_copied))
                viewModel.clearEffect()
            }
            DetailEffect.TorrentQueued -> {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.download_queued),
                    actionLabel = null,
                    withDismissAction = true,
                )
                viewModel.clearEffect()
            }
            DetailEffect.NoTorrentClient -> {
                val snackbar = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.detail_no_client),
                    actionLabel = context.getString(R.string.retry_label),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                viewModel.clearEffect()
                if (snackbar == SnackbarResult.ActionPerformed) {
                    // The user may have installed a client while the app was open.
                    val retry = MagnetLauncher.openMagnet(context, lastMagnetUri)
                    if (retry is MagnetLauncher.MagnetOpenResult.NoClientAvailable) {
                        snackbarHostState.showSnackbar(context.getString(R.string.detail_install_client_hint))
                    }
                }
            }
            is DetailEffect.ShowSnackbar -> {
                snackbarHostState.showSnackbar(current.message)
                viewModel.clearEffect()
            }
            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = uiState.movie?.title ?: uiState.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBookmark(); haptics.confirm() }) {
                        Icon(
                            imageVector = if (uiState.isBookmarked) {
                                Icons.Rounded.Bookmark
                            } else {
                                Icons.Rounded.BookmarkBorder
                            },
                            contentDescription = if (uiState.isBookmarked) {
                                stringResource(R.string.detail_remove_bookmark)
                            } else {
                                stringResource(R.string.detail_bookmark)
                            },
                            tint = if (uiState.isBookmarked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> DetailLoading()
                uiState.errorMessage != null && uiState.movie == null -> ErrorState(
                    message = uiState.errorMessage.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> DetailContent(
                    uiState = uiState,
                    onOpenMagnet = viewModel::onOpenMagnet,
                    onCopyMagnet = { release ->
                        MagnetLauncher.copyToClipboard(
                            context = context,
                            label = "movie.magnet",
                            text = release.magnetUri,
                        )
                        haptics.confirm()
                        viewModel.onCopied(release)
                    },
                    onSortSelected = viewModel::setReleaseSort,
                    onDownloadTorrent = viewModel::onDownloadTorrent,
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    uiState: DetailUiState,
    onOpenMagnet: (TorrentRelease) -> Unit,
    onCopyMagnet: (TorrentRelease) -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onDownloadTorrent: (TorrentRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    val movie = uiState.movie ?: return

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            BackdropHeader(
                title = movie.title,
                backdropUrl = movie.backdropUrl ?: movie.posterUrl,
                posterUrl = movie.posterUrl,
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                movie.originalTitle?.takeIf { it != movie.title }?.let { original ->
                    Text(
                        text = original,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RatingBadge(rating = movie.ratingLabel)
                    movie.ratingTmdb?.let { tmdb ->
                        MetaPill("TMDB ${Formatters.rating(tmdb)}")
                    }
                    MetaPill(movie.yearLabel)
                    if (uiState.totalSeeders > 0) {
                        MetaPill("${Formatters.compactCount(uiState.totalSeeders)} seeders")
                    }
                }

                if (movie.genres.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        movie.genres.take(4).forEach { genre -> GenreTag(genre) }
                    }
                }

                Formatters.cleanText(movie.overview)?.let { overview ->
                    Text(
                        text = stringResource(R.string.detail_overview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    )
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.detail?.director?.let { director ->
                    Text(
                        text = stringResource(R.string.detail_directed_by, director),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MovieFilter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_releases),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.releases.isNotEmpty()) {
                    Text(
                        text = "${uiState.releases.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (uiState.releases.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortOption.entries.forEach { sort ->
                        FilterChip(
                            selected = uiState.releaseSort == sort,
                            onClick = { onSortSelected(sort) },
                            label = { Text(sort.label) },
                            shape = CardShapes.pill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            items(uiState.releases, key = { release -> release.infoHash }) { release ->
                ReleaseCard(
                    release = release,
                    isBest = release.infoHash == uiState.bestRelease?.infoHash,
                    highlightedQuality = null,
                    onOpenMagnet = { onOpenMagnet(release) },
                    onCopyMagnet = { onCopyMagnet(release) },
                    onDownloadTorrent = { onDownloadTorrent(release) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        } else {
            item {
                NoReleasesCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        item {
            Text(
                text = stringResource(R.string.detail_magnet_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            )
        }
    }
}

/** Backdrop + floating poster overlay, fading into the app background. */
@Composable
private fun BackdropHeader(
    title: String,
    backdropUrl: String?,
    posterUrl: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        PosterImage(
            url = backdropUrl,
            contentDescription = null,
            fallbackTitle = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.30f),
                        0.55f to Color.Transparent,
                        1.0f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        // Poster sits half-over the backdrop edge, the way streaming apps do it.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                fallbackTitle = title,
                modifier = Modifier
                    .width(96.dp)
                    .height(144.dp)
                    .clip(CardShapes.poster),
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    Column(modifier = Modifier.fillMaxSize()) {
        SkeletonBlock(Modifier.fillMaxWidth().height(260.dp), CardShapes.hero)
        Column(modifier = Modifier.padding(16.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(26.dp))
            Spacer(Modifier.height(10.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(14.dp))
            Spacer(Modifier.height(20.dp))
            repeat(3) {
                SkeletonBlock(Modifier.fillMaxWidth().height(12.dp))
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
            repeat(2) {
                SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), CardShapes.release)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
