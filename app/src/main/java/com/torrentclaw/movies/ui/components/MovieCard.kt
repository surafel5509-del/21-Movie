package com.torrentclaw.movies.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.domain.model.SwarmHealth
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.theme.extendedColors

/** Seeders → health bucket, shared by the cards and the detail screen. */
fun swarmHealthFor(seeders: Int): SwarmHealth = when {
    seeders >= 50 -> SwarmHealth.STRONG
    seeders >= 10 -> SwarmHealth.FAIR
    seeders > 0 -> SwarmHealth.WEAK
    else -> SwarmHealth.DEAD
}

/**
 * Poster card used by the home grid and the search results list.
 *
 * 2:3 poster with a bottom scrim, rating + quality badges and a seeder pill.
 * Press feedback is a subtle scale animation instead of a heavy ripple.
 */
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBookmarked: Boolean = false,
    highlightedQuality: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "posterPressScale",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(CardShapes.poster)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics {
                contentDescription = buildString {
                    append(movie.title)
                    movie.year?.let { append(", ").append(it) }
                    movie.ratingLabel?.let { append(", rated ").append(it) }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardShapes.poster)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            PosterImage(
                url = movie.posterUrl,
                contentDescription = null,
                fallbackTitle = movie.title,
                modifier = Modifier.matchParentSize(),
            )

            // Bottom scrim keeps the seeder pill legible over bright posters.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(top = 56.dp)
                    .background(bottomScrim(Color.Black.copy(alpha = 0.62f), 1f)),
            )

            val quality = movie.bestQuality
            if (quality != null) {
                QualityBadge(
                    label = quality,
                    highlighted = highlightedQuality != null &&
                        quality.contains(highlightedQuality, ignoreCase = true),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }

            RatingBadge(
                rating = movie.ratingLabel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )

            if (movie.maxSeeders > 0) {
                SwarmBadge(
                    seeders = movie.maxSeeders,
                    leechers = 0,
                    health = swarmHealthFor(movie.maxSeeders),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            if (isBookmarked) {
                BookmarkIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }
        }

        Text(
            text = movie.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Row(
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = movie.yearLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            movie.genres.firstOrNull()?.let { genre ->
                DotSeparator()
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Gold bookmark flag shown on saved posters. */
@Composable
fun BookmarkIndicator(modifier: Modifier = Modifier) {
    val extended = MaterialTheme.extendedColors
    Surface(
        modifier = modifier.size(24.dp),
        shape = RoundedCornerShape(8.dp),
        color = extended.ratingBadge.copy(alpha = 0.95f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = extended.onRatingBadge,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Compact row used on the watchlist screen (thumbnail + metadata + action slot). */
@Composable
fun MovieRow(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBookmarked: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShapes.release,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PosterImage(
                url = movie.posterUrl,
                contentDescription = null,
                fallbackTitle = movie.title,
                modifier = Modifier
                    .size(width = 64.dp, height = 96.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = movie.yearLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RatingBadge(rating = movie.ratingLabel, compact = true)
                }
                Formatters.cleanText(movie.overview)?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                movie.bestQuality?.let { quality ->
                    QualityBadge(
                        label = quality,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isBookmarked) {
                    BookmarkIndicator()
                }
                trailing?.invoke()
            }
        }
    }
}
