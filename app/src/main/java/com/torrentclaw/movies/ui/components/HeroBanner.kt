package com.torrentclaw.movies.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.torrentclaw.movies.R
import com.torrentclaw.movies.domain.model.Movie
import com.torrentclaw.movies.ui.theme.CardShapes

/**
 * Featured hero banner for the #1 trending movie: full-bleed backdrop,
 * cinematic scrim, title, metadata and a single primary action.
 */
@Composable
fun HeroBanner(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String = "#1 Trending now",
    actionLabel: String = "View releases",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(0.92f)
            .clip(CardShapes.hero)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        PosterImage(
            url = movie.backdropUrl ?: movie.posterUrl,
            contentDescription = null,
            fallbackTitle = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )

        // Two-stage scrim: subtle at the top for the badge, strong at the bottom
        // for the text block.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f),
                        0.45f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        1.0f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            movie.bestQuality?.let { quality ->
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = quality,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RatingBadge(rating = movie.ratingLabel, compact = true)
                Text(
                    text = movie.yearLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                if (movie.maxSeeders > 0) {
                    Text(
                        text = stringResource(R.string.count_seeders, Formatters.compactCount(movie.maxSeeders)),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            Formatters.cleanText(movie.overview)?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.height(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Minimal hero skeleton that reserves the same footprint while loading. */
@Composable
fun HeroBannerSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(0.92f)
            .clip(CardShapes.hero),
    ) {
        SkeletonBlock(Modifier.fillMaxSize(), CardShapes.hero)
    }
}
