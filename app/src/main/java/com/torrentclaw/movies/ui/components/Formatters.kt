package com.torrentclaw.movies.ui.components

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Human-readable presentation helpers shared by every screen. Kept free of
 * Android dependencies so they are unit-testable on the JVM.
 */
object Formatters {

    private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /** 2147483648L → "2.0 GB". Uses binary units, like torrent clients do. */
    fun fileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "—"
        val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, SIZE_UNITS.lastIndex)
        val value = bytes / 1024.0.pow(group.toDouble())
        val decimals = when {
            group == 0 -> 0
            value >= 100 -> 0
            value >= 10 -> 1
            else -> 1
        }
        return String.format(Locale.US, "%.${decimals}f %s", value, SIZE_UNITS[group])
    }

    /** 847 → "847", 12_400 → "12.4k". Seeder badges are space constrained. */
    fun compactCount(count: Int): String = when {
        count < 0 -> "0"
        count < 1_000 -> count.toString()
        count < 1_000_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0).replace(".0k", "k")
        else -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
    }

    /** "2024-03-15T12:00:00Z" → "2024". */
    fun yearFromIso(iso: String?): String? =
        iso?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()?.toString()

    /** Title/overview cleanup: collapse whitespace, trim, drop HTML-ish leftovers. */
    fun cleanText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    /** 8.83 → "8.8"; null → null. */
    fun rating(value: Double?): String? =
        value?.let { String.format(Locale.US, "%.1f", it.coerceIn(0.0, 10.0)) }

    /** "en" → "EN", filtering blanks. */
    fun languageTag(code: String): String =
        code.trim().uppercase(Locale.US).take(3)

    /** Approximate ratio: seeders / (seeders + leechers). */
    fun healthRatio(seeders: Int, leechers: Int): Float {
        val total = seeders + leechers
        if (total <= 0) return 0f
        return seeders.toFloat() / total
    }

    /** Percentage as an Int, e.g. 0.92 → 92. */
    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).toInt()

    /** Avoids the "NaN%" / "-0" class of bugs in count strings. */
    fun safeNonNegative(value: Int): Int = abs(value).coerceAtLeast(0)
}
