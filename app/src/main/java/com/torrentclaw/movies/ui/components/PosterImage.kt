package com.torrentclaw.movies.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.torrentclaw.movies.ui.theme.extendedColors

/**
 * Poster/backdrop loader with explicit loading, error and "no artwork" states.
 *
 * Uses [SubcomposeAsyncImage] rather than a bare `AsyncImage` so the skeleton,
 * the error state and the typography fallback are first-class rather than a
 * flash of empty colour.
 */
@Composable
fun PosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    showShimmerWhileLoading: Boolean = true,
) {
    val context = LocalContext.current

    if (url.isNullOrBlank()) {
        ArtworkPlaceholder(
            modifier = modifier,
            title = fallbackTitle,
            contentDescription = contentDescription,
        )
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            if (showShimmerWhileLoading) {
                ShimmerPlaceholder(Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
            }
        },
        error = {
            ArtworkPlaceholder(
                modifier = Modifier.fillMaxSize(),
                title = fallbackTitle,
                contentDescription = contentDescription,
                isError = true,
            )
        },
        success = { state ->
            Image(
                painter = state.painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/** Gradient + film icon (or the title) shown when a movie has no artwork. */
@Composable
fun ArtworkPlaceholder(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentDescription: String? = null,
    isError: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(colors.surfaceContainerHigh, colors.surfaceContainerLowest),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            Icon(
                imageVector = if (isError) Icons.Outlined.BrokenImage else Icons.Outlined.Movie,
                contentDescription = contentDescription,
                tint = if (isError) {
                    extended.swarmWeak.copy(alpha = 0.7f)
                } else {
                    colors.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.fillMaxSize(0.32f),
            )
        }
    }
}

/** Skeleton block used while artwork or content loads. */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    colors.surfaceContainerLow,
                    colors.surfaceContainerHigh,
                    colors.surfaceContainerLow,
                ),
            ),
        ),
    )
}

/** Fades artwork into the app background — used by the hero banner and detail header. */
fun bottomScrim(color: Color, heightFraction: Float = 0.62f): Brush =
    Brush.verticalGradient(
        0.0f to Color.Transparent,
        (1f - heightFraction).coerceIn(0f, 1f) to Color.Transparent,
        1f to color,
    )
