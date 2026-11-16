package com.torrentclaw.movies.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.torrentclaw.movies.ui.MainViewModel
import com.torrentclaw.movies.ui.detail.DetailScreen
import com.torrentclaw.movies.ui.downloads.DownloadsScreen
import com.torrentclaw.movies.ui.home.HomeScreen
import com.torrentclaw.movies.ui.player.PlayerRoute
import com.torrentclaw.movies.ui.search.SearchScreen
import com.torrentclaw.movies.ui.settings.SettingsScreen
import com.torrentclaw.movies.ui.watchlist.WatchlistScreen

/**
 * Root navigation: a floating pill navigation bar for the four primary tabs, with
 * detail and player pushed on top as full-screen surfaces.
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
                enter = slideInVertically(animationSpec = tween(280)) { it / 2 } + fadeIn(tween(200)),
                exit = slideOutVertically(animationSpec = tween(220)) { it / 2 } + fadeOut(tween(150)),
            ) {
                TorrentClawBottomBar(
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
                    onPlayClick = { downloadId ->
                        navController.navigate(Destination.Player(downloadId))
                    },
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

            composable<Destination.Downloads> {
                DownloadsScreen(
                    onPlay = { downloadId ->
                        navController.navigate(Destination.Player(downloadId))
                    },
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

            // The player is a full-screen, chrome-less surface: no bottom bar, no
            // insets, and it can go into picture-in-picture.
            composable<Destination.Player>(
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                PlayerRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Floating pill navigation bar.
 *
 * A stock `NavigationBar` is a full-bleed strip that visually separates the app
 * from the content. This is a detaching capsule instead: it floats above the
 * content with a soft shadow, only the selected tab shows its label, and the
 * selected icon sits in a tinted circle that animates in. That is both more
 * modern and more space-efficient on a phone.
 */
@Composable
private fun TorrentClawBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val selectedIndex = TopLevelDestination.entries.indexOfFirst { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    PillNavItem(
                        tab = tab,
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigate(tab)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillNavItem(
    tab: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "navScale",
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Selected-state halo behind the icon. Driven by animated alpha/scale
            // rather than AnimatedVisibility: nested inside Column → Box the
            // ColumnScope overload is the only resolvable candidate, which does not
            // compile here.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        val halo = if (selected) 1f else 0f
                        alpha = halo
                        scaleX = halo
                        scaleY = halo
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
            Icon(
                imageVector = tab.icon(selected),
                contentDescription = tab.label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(23.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

private fun TopLevelDestination.icon(selected: Boolean): ImageVector = when (this) {
    TopLevelDestination.HOME -> if (selected) Icons.Rounded.Explore else Icons.Outlined.Explore
    TopLevelDestination.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
    TopLevelDestination.WATCHLIST ->
        if (selected) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder
    TopLevelDestination.DOWNLOADS ->
        if (selected) Icons.Rounded.Download else Icons.Outlined.Download
}

private fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
