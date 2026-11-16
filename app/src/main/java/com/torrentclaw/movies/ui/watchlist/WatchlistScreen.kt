package com.torrentclaw.movies.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.torrentclaw.movies.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.ui.components.EmptyState
import com.torrentclaw.movies.ui.components.ListRowsSkeleton
import com.torrentclaw.movies.ui.components.MovieRow
import com.torrentclaw.movies.ui.util.rememberHaptics

/**
 * Watchlist: Room-backed, fully offline. Rows can be removed with swipe-free
 * explicit buttons and an undo snackbar, so nothing is lost by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onMovieClick: (Int, String) -> Unit,
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberHaptics(hapticsEnabled)

    LaunchedEffect(removed) {
        val movie = removed ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.watchlist_removed_message, movie.title),
            actionLabel = context.getString(R.string.undo),
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoRemove()
        } else {
            viewModel.clearRemoved()
        }
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
                            text = stringResource(R.string.watchlist_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                (uiState as? WatchlistUiState.Success)?.movies?.takeIf { it.isNotEmpty() }?.let { movies ->
                    Text(
                        text = stringResource(R.string.watchlist_count, movies.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            WatchlistUiState.Loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(top = 8.dp),
            ) {
                ListRowsSkeleton(rows = 4)
            }

            is WatchlistUiState.Success -> {
                if (state.movies.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.BookmarkBorder,
                        title = stringResource(R.string.watchlist_empty_title),
                        message = stringResource(R.string.watchlist_empty_body),
                        actionLabel = stringResource(R.string.watchlist_browse),
                        onAction = onBrowseClick,
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
                        items(state.movies, key = { movie -> movie.id }) { movie ->
                            WatchlistRow(
                                movie = movie,
                                onClick = { onMovieClick(movie.id, movie.title) },
                                onRemove = {
                                    haptics.impact()
                                    viewModel.remove(movie)
                                },
                            )
                        }
                    }
                }
            }

            is WatchlistUiState.Error -> EmptyState(
                icon = Icons.Rounded.BookmarkBorder,
                title = stringResource(R.string.watchlist_error_title),
                message = state.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun WatchlistRow(
    movie: Movie,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    MovieRow(
        movie = movie,
        onClick = onClick,
        isBookmarked = false,
        modifier = Modifier.fillMaxWidth(),
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = movie.title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
