package com.torrentclaw.movies.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.torrentclaw.movies.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.torrentclaw.movies.domain.model.QualityFilter
import com.torrentclaw.movies.domain.model.SortOption
import com.torrentclaw.movies.ui.components.ListRowsSkeleton
import com.torrentclaw.movies.ui.components.MovieRow
import com.torrentclaw.movies.ui.components.NoResultsState
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.theme.extendedColors

/**
 * Search & filter screen: auto-focused search field, debounced query execution
 * (400 ms in the ViewModel), quality/sort chips, and list results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.search),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (!uiState.filters.isDefault) {
                        IconButton(onClick = { viewModel.onAction(SearchAction.ResetFilters) }) {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = stringResource(R.string.search_reset_filters),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(
                query = uiState.query,
                onQueryChange = { viewModel.onAction(SearchAction.QueryChanged(it)) },
                onClear = { viewModel.onAction(SearchAction.ClearQuery) },
                onSearch = { keyboard?.hide() },
                focusRequester = focusRequester,
            )

            FilterRow(
                filters = uiState.filters,
                onQualitySelected = { viewModel.onAction(SearchAction.QualitySelected(it)) },
                onSortSelected = { viewModel.onAction(SearchAction.SortSelected(it)) },
            )

            // A thin progress bar is less jarring than swapping the list for a spinner.
            AnimatedVisibility(visible = uiState.isLoading, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }

            when {
                uiState.showIdle -> SearchIdleState(
                    onSampleClick = { sample ->
                        viewModel.onAction(SearchAction.QueryChanged(sample))
                        keyboard?.hide()
                    },
                )

                uiState.isLoading && uiState.movies.isEmpty() -> {
                    Spacer(Modifier.height(8.dp))
                    ListRowsSkeleton(rows = 5)
                }

                uiState.errorMessage != null -> NoResultsState(
                    title = stringResource(
                        if (uiState.isRateLimited) R.string.search_rate_limited_title else R.string.search_error_title,
                    ),
                    message = uiState.errorMessage.orEmpty(),
                    onClearFilters = if (!uiState.filters.isDefault) {
                        { viewModel.onAction(SearchAction.ResetFilters) }
                    } else {
                        { viewModel.onAction(SearchAction.Retry) }
                    },
                )

                uiState.isEmpty -> NoResultsState(
                    title = stringResource(R.string.search_empty_title),
                    message = stringResource(R.string.search_empty_body),
                    onClearFilters = if (!uiState.filters.isDefault) {
                        { viewModel.onAction(SearchAction.ResetFilters) }
                    } else {
                        null
                    },
                )

                else -> SearchResults(
                    uiState = uiState,
                    onMovieClick = onMovieClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_field_hint)) },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun FilterRow(
    filters: com.torrentclaw.movies.domain.model.SearchFilters,
    onQualitySelected: (QualityFilter) -> Unit,
    onSortSelected: (SortOption) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(QualityFilter.entries.toList(), key = { it.name }) { quality ->
                FilterChip(
                    selected = filters.quality == quality,
                    onClick = { onQualitySelected(quality) },
                    label = { Text(quality.label) },
                    shape = CardShapes.pill,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(SortOption.entries.toList(), key = { it.name }) { sort ->
                FilterChip(
                    selected = filters.sort == sort,
                    onClick = { onSortSelected(sort) },
                    label = { Text(sort.label) },
                    shape = CardShapes.pill,
                    leadingIcon = if (filters.sort == sort) {
                        {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    onMovieClick: (Int, String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (uiState.total > 0) {
                        stringResource(R.string.search_results_count, uiState.total)
                    } else {
                        stringResource(R.string.search_results_header)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.filters.quality.takeIf { it != QualityFilter.ALL }?.let {
                    Text(
                        text = stringResource(R.string.search_filtered_to, it.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        items(uiState.movies, key = { movie -> movie.id }) { movie ->
            MovieRow(
                movie = movie,
                isBookmarked = movie.id in uiState.bookmarkedIds,
                onClick = { onMovieClick(movie.id, movie.title) },
            )
        }
    }
}

@Composable
private fun SearchIdleState(onSampleClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.search_idle_title_long),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_idle_body_long),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))

        // A few example searches double as onboarding and as a fast path in.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf("Dune", "Oppenheimer", "Blade Runner 2049").forEach { sample ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, CardShapes.release)
                        .clickable { onSampleClick(sample) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
