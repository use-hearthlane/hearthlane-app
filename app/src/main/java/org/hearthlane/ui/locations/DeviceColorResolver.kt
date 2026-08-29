package org.hearthlane.ui.locations

import androidx.compose.ui.graphics.Color

/**
 * Presentation-only device color identity for the Locations map and selector.
 *
 * The color is derived deterministically from the stable [deviceId], never from
 * the nickname (which may change). The same device always resolves to the same
 * color within a session and across sessions, so markers never appear to swap
 * colors on reopen. The palette is small and visually distinct; the color is a
 * reinforcement of identity, never the only differentiator.
 */
object DeviceColorResolver {

    /** Small, distinct, readable palette (also on light map tiles). */
    val palette: List<Color> = listOf(
        Color(0xFFD32F2F), // red
        Color(0xFF1976D2), // blue
        Color(0xFF388E3C), // green
        Color(0xFFF57C00), // orange
        Color(0xFF7B1FA2), // purple
        Color(0xFF00796B), // teal
        Color(0xFFC2185B), // pink
        Color(0xFF5D4037), // brown
    )

    /** Deterministic palette index for a device id. Stable for the same id. */
    fun colorFor(deviceId: String): Color = palette[stableIndex(deviceId)]

    /** Exposed for tests and marker icon generation. */
    fun stableIndex(deviceId: String): Int =
        (deviceId.hashCode() and 0x7fffffff) % palette.size
}