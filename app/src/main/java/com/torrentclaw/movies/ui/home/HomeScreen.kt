package com.torrentclaw.movies.ui.home

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
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.ui.components.ErrorState
import com.torrentclaw.movies.ui.components.HeroBanner
import com.torrentclaw.movies.ui.components.HeroBannerSkeleton
import com.torrentclaw.movies.ui.components.MovieCard
import com.torrentclaw.movies.ui.components.PosterGridSkeleton
import androidx.compose.ui.res.stringResource
import com.torrentclaw.movies.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

/**
 * Home / Discovery: a featured hero banner for the #1 trending movie, then a
 * 2-column poster grid. Refreshing is exposed in the app bar (a spinner replaces
 * the button while a refresh is in flight) instead of pull-to-refresh, so the
 * interaction is discoverable and testable across Compose versions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (Int, String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(event) {
        val current = event
        if (current is HomeEvent.ShowMessage) {
            snackbarHostState.showSnackbar(current.message)
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                },
                actions = {
                    if (uiState.isRefreshing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 2.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.home_refresh),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.home_search_action),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isInitialLoading -> HomeLoadingContent()
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> HomeContent(
                    uiState = uiState,
                    gridState = gridState,
                    onMovieClick = onMovieClick,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onMovieClick: (Int, String) -> Unit,
    onRefresh: () -> Unit,
) {
    val movies = if (uiState.popular.isNotEmpty()) uiState.popular else uiState.recent

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        uiState.featured?.let { featured ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroBanner(
                    movie = featured,
                    onClick = { onMovieClick(featured.id, featured.title) },
                    eyebrow = "#1 Trending now",
                )
            }
        }

        if (uiState.isOfflineNotice) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.torrentclaw.movies.ui.components.InlineNotice(
                    message = stringResource(R.string.home_offline_notice),
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // Continue watching: only rendered once something is part-watched, so a
        // fresh install has no empty shelf.
        if (uiState.continueWatching.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueWatchingRow(
                    entries = uiState.continueWatching,
                    onPlay = { entry -> viewModel.playResume(entry, onPlayClick) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                title = stringResource(
                    if (uiState.popular.isEmpty()) R.string.home_section_fresh else R.string.home_section_popular,
                ),
                subtitle = stringResource(R.string.home_section_subtitle, movies.size),
            )
        }

        // Edge gutters live on the cards so the hero banner can full-bleed.
        itemsIndexed(
            items = movies,
            key = { _, movie -> movie.id },
        ) { index, movie ->
            MovieCard(
                movie = movie,
                isBookmarked = movie.id in uiState.bookmarkedIds,
                highlightedQuality = uiState.preferredQuality
                    .takeIf { it != QualityFilter.ALL }
                    ?.apiValue,
                onClick = { onMovieClick(movie.id, movie.title) },
                modifier = Modifier.padding(
                    start = if (index % 2 == 0) 16.dp else 0.dp,
                    end = if (index % 2 == 0) 0.dp else 16.dp,
                ),
            )
        }

        if (movies.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.torrentclaw.movies.ui.components.EmptyState(
                    icon = Icons.Rounded.Explore,
                    title = stringResource(R.string.home_empty),
                    message = stringResource(R.string.home_empty_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeLoadingContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        HeroBannerSkeleton()
        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            com.torrentclaw.movies.ui.components.SkeletonBlock(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(20.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        PosterGridSkeleton(rows = 2)
    }
}

/**
 * Resume shelf: horizontal cards with poster, title and a watched-progress ring.
 * Tapping one reopens the in-app player at the saved second.
 */
@Composable
private fun ContinueWatchingRow(
    entries: List<com.torrentclaw.movies.data.local.PlaybackPositionEntity>,
    onPlay: (com.torrentclaw.movies.data.local.PlaybackPositionEntity) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Continue watching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.filePath }) { entry ->
                val fraction = entry.watchedFraction ?: 0f
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clip(com.torrentclaw.movies.ui.theme.CardShapes.poster)
                        .clickable { onPlay(entry) },
                ) {
                    Box {
                        AsyncImage(
                            model = entry.posterUrl,
                            contentDescription = entry.movieTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(186.dp)
                                .clip(com.torrentclaw.movies.ui.theme.CardShapes.poster),
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .align(Alignment.Center)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Resume",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        // Watched bar pinned to the poster's bottom edge.
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = entry.movieTitle,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Brand mark from the vector asset (gold reel + claw on a dark tile). */
@Composable
private fun BrandMark() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.torrentclaw.movies.R.drawable.ic_app_mark),
        contentDescription = null,
        modifier = Modifier.size(28.dp),
    )
}
