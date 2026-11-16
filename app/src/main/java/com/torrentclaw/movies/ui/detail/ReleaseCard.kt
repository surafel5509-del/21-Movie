package com.torrentclaw.movies.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.torrentclaw.movies.domain.model.SwarmHealth
import com.torrentclaw.movies.domain.model.TorrentRelease
import com.torrentclaw.movies.ui.components.Formatters
import com.torrentclaw.movies.ui.components.QualityBadge
import com.torrentclaw.movies.ui.theme.CardShapes
import com.torrentclaw.movies.ui.theme.extendedColors
import androidx.compose.ui.res.stringResource
import com.torrentclaw.movies.R

/**
 * One torrent release: quality badge, size, swarm health, and the two actions
 * the app exists for — open the magnet, or copy it.
 */
@Composable
fun ReleaseCard(
    release: TorrentRelease,
    onOpenMagnet: () -> Unit,
    onCopyMagnet: () -> Unit,
    modifier: Modifier = Modifier,
    isBest: Boolean = false,
    highlightedQuality: String? = null,
) {
    val extended = MaterialTheme.extendedColors
    val healthColor = when (release.health) {
        SwarmHealth.STRONG -> extended.swarmStrong
        SwarmHealth.FAIR -> extended.swarmFair
        SwarmHealth.WEAK -> extended.swarmWeak
        SwarmHealth.DEAD -> extended.swarmDead
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShapes.release,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (isBest) 1.5.dp else 1.dp,
            color = if (isBest) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityBadge(
                    label = release.qualityLabel,
                    highlighted = highlightedQuality != null &&
                        release.qualityLabel.contains(highlightedQuality, ignoreCase = true),
                )
                Spacer(Modifier.width(8.dp))
                if (isBest) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.detail_best_swarm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.fileSize(release.sizeBytes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Swarm health: numbers plus a bar, because "847 seeders" alone is
            // hard to compare at a glance across twenty rows.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.count_seeders, Formatters.compactCount(release.seeders)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = healthColor,
                )
                Text(
                    text = stringResource(R.string.count_leechers, Formatters.compactCount(release.leechers)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                release.qualityScore?.let { score ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.detail_score, score),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { Formatters.healthRatio(release.seeders, release.leechers) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = healthColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            // Release metadata: tracker, group, codec, languages, flags.
            val meta = buildList {
                release.tracker?.let { add(it.uppercase()) }
                release.releaseGroup?.let { add(it) }
                release.codec?.let { add(it.uppercase()) }
                release.audioCodec?.let { add(it.uppercase()) }
                if (release.languages.isNotEmpty()) {
                    add(release.languages.take(3).joinToString("/") { Formatters.languageTag(it) })
                }
                if (release.isProper) add("PROPER")
                if (release.isRepack) add("REPACK")
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenMagnet,
                    modifier = Modifier.weight(1.35f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.detail_open_magnet),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    onClick = onCopyMagnet,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.detail_copy_magnet),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** Empty state for movies with metadata but no indexed torrents yet. */
@Composable
fun NoReleasesCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShapes.release,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.detail_no_torrents_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.detail_no_torrents_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
