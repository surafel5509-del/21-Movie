package com.torrentclaw.movies.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.theme.extendedColors

/**
 * Empty / error / loading surfaces shared by every screen so states never
 * look inconsistent (a bespoke error layout per screen is a common regression
 * source).
 */

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shape = CardShapes.pill) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Retry",
    isOffline: Boolean = false,
) {
    EmptyState(
        icon = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Explore,
        title = if (isOffline) "You're offline" else "Something went wrong",
        message = message,
        modifier = modifier,
        actionLabel = retryLabel,
        onAction = onRetry,
    )
}

@Composable
fun NoResultsState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onClearFilters: (() -> Unit)? = null,
) {
    EmptyState(
        icon = Icons.Rounded.SearchOff,
        title = title,
        message = message,
        modifier = modifier,
        actionLabel = if (onClearFilters != null) "Reset filters" else null,
        onAction = onClearFilters,
    )
}

/** Pulsing skeleton block — used by the grid and detail loading states. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
    )
}

/** 2-column skeleton that mirrors the poster grid, so loading looks structural. */
@Composable
fun PosterGridSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            shape = CardShapes.poster,
                        )
                        Spacer(Modifier.height(8.dp))
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Skeleton row used by the search list and watchlist while Room loads. */
@Composable
fun ListRowsSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CardShapes.release)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(64.dp)
                        .height(96.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(10.dp))
                    Spacer(Modifier.height(12.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(10.dp))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(10.dp))
                }
            }
        }
    }
}

/** Inline banner shown when a refresh failed but cached content is displayed. */
@Composable
fun InlineNotice(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val extended = MaterialTheme.extendedColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(extended.swarmFair.copy(alpha = 0.12f), CardShapes.release)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
