package com.torrentclaw.movies.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Poster cards use a softer 16 dp radius than Material's default so artwork
 * reads as a "print", while chips stay pill-shaped.
 */
val TorrentClawShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object CardShapes {
    val poster = RoundedCornerShape(16.dp)
    val hero = RoundedCornerShape(24.dp)
    val release = RoundedCornerShape(18.dp)
    val badge = RoundedCornerShape(8.dp)
    val pill = RoundedCornerShape(percent = 50)
}
