package com.torrentclaw.movies.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.torrentclaw.movies.ui.detail.DetailScreen
import com.torrentclaw.movies.ui.home.HomeScreen
import com.torrentclaw.movies.ui.search.SearchScreen
import com.torrentclaw.movies.ui.settings.SettingsScreen
import com.torrentclaw.movies.ui.watchlist.WatchlistScreen
import com.torrentclaw.movies.ui.MainViewModel

/**
 * Root navigation: a bottom-bar shell for the three primary tabs, with the
 * detail screen pushed on top (its own collapsing toolbar, no bottom bar).
 */
@Composable
fun TorrentClawNavGraph(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()

    val showBottomBar = TopLevelDestination.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The inner screens consume the system bars themselves (their top bars
        // go edge-to-edge under the status bar); this shell only owns the bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(animationSpec = tween(240)) { it / 2 } + fadeIn(tween(180)),
                exit = slideOutVertically(animationSpec = tween(200)) { it / 2 } + fadeOut(tween(150)),
            ) {
                TorrentClawBottomBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    onNavigate = { tab ->
                        navController.navigate(tab.destination) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable<Destination.Home> {
                HomeScreen(
                    onMovieClick = { id, title ->
                        navController.navigate(Destination.MovieDetail(id, title))
                    },
                    onSearchClick = { navController.navigateToTab(Destination.Search) },
                )
            }

            composable<Destination.Search> {
                SearchScreen(
                    onMovieClick = { id, title ->
                        navController.navigate(Destination.MovieDetail(id, title))
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigateToTab(Destination.Home)
                        }
                    },
                )
            }

            composable<Destination.Watchlist> {
                WatchlistScreen(
                    onMovieClick = { id, title ->
                        navController.navigate(Destination.MovieDetail(id, title))
                    },
                    onBrowseClick = { navController.navigateToTab(Destination.Home) },
                    hapticsEnabled = mainState.hapticsEnabled,
                )
            }

            composable<Destination.Settings> {
                SettingsScreen()
            }

            composable<Destination.MovieDetail>(
                enterTransition = {
                    slideInVertically(animationSpec = tween(280)) { it / 12 } + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(150)) },
                popExitTransition = {
                    slideOutVertically(animationSpec = tween(240)) { it / 12 } + fadeOut(tween(180))
                },
            ) {
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    hapticsEnabled = mainState.hapticsEnabled,
                )
            }
        }
    }
}

@Composable
private fun TorrentClawBottomBar(
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any {
                it.hasRoute(tab.destination::class)
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon(selected),
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun TopLevelDestination.icon(selected: Boolean): ImageVector = when (this) {
    TopLevelDestination.HOME -> if (selected) Icons.Rounded.Explore else Icons.Outlined.Explore
    TopLevelDestination.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
    TopLevelDestination.WATCHLIST ->
        if (selected) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder
}

private fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
