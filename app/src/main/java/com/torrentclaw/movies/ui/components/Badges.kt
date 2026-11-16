package com.torrentclaw.movies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.torrentclaw.movies.domain.model.SwarmHealth
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.theme.extendedColors

/** Gold rating pill: "★ 8.8". Hidden when the rating is unknown. */
@Composable
fun RatingBadge(
    rating: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (rating == null) return
    val extended = MaterialTheme.extendedColors
    Row(
        modifier = modifier
            .background(extended.ratingBadge, CardShapes.badge)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = extended.onRatingBadge,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = rating,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.ExtraBold,
            color = extended.onRatingBadge,
        )
    }
}

/** Quality pill, e.g. "1080p BluRay" or "4K HDR". */
@Composable
fun QualityBadge(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val extended = MaterialTheme.extendedColors
    val background = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        extended.qualityBadge
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        extended.onQualityBadge
    }

    Box(
        modifier = modifier
            .background(background, CardShapes.badge)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

/** Seeder count with a colour-coded dot; green means a healthy swarm. */
@Composable
fun SwarmBadge(
    seeders: Int,
    leechers: Int,
    health: SwarmHealth,
    modifier: Modifier = Modifier,
) {
    val extended = MaterialTheme.extendedColors
    val color = when (health) {
        SwarmHealth.STRONG -> extended.swarmStrong
        SwarmHealth.FAIR -> extended.swarmFair
        SwarmHealth.WEAK -> extended.swarmWeak
        SwarmHealth.DEAD -> extended.swarmDead
    }

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), CardShapes.badge)
            .border(1.dp, color.copy(alpha = 0.32f), CardShapes.badge)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = Formatters.compactCount(seeders),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = extended.swarmWeak.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = Formatters.compactCount(leechers),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Neutral outline pill for metadata such as year, size or tracker name. */
@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CardShapes.pill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/** "-" separator used between metadata in dense rows. */
@Composable
fun DotSeparator(modifier: Modifier = Modifier) {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

/** Rounded tag for a genre chip on the detail screen. */
@Composable
fun GenreTag(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
