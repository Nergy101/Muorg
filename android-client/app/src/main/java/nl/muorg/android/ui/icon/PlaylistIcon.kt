package nl.muorg.android.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Playlist icon format: `"<lucide-name>:#RRGGBB"` (e.g. `"guitar:#F87171"`),
 * stored in the playlists.icon column. Legacy emoji values resolve to the
 * default below instead of rendering as emoji.
 */
object PlaylistIconDefaults {
    const val NAME = "eighth-notes"
    const val COLOR = "#FFFFFF"
    const val VALUE = "eighth-notes:#FFFFFF"

    /** Icon for the server-side Favorites playlist: a gold star. */
    const val FAVORITES_VALUE = "star:#FFD700"

    /** Music-themed lucide icons (all in the canonical set) for random mix icons. */
    val MUSIC_ICON_NAMES: List<String> = listOf(
        "eighth-note", "eighth-notes", "half-note", "sixteenth-notes",
        "guitar", "guitar-electric", "drum", "drumstick",
        "disc", "disc-2", "disc-3", "disc-album",
        "headphones", "speaker", "mic", "mic-audio-lines",
        "audio-lines", "audio-waveform", "keyboard-music", "list-music",
        "file-music", "radio",
    )

    /** Swatches offered in the playlist icon picker, in display order. */
    val COLORS: List<String> = listOf(
        "#FFFFFF", "#F87171", "#FB923C", "#FBBF24", "#A3E635", "#34D399",
        "#2DD4BF", "#22D3EE", "#60A5FA", "#818CF8", "#A78BFA", "#E879F9",
        "#F472B6", "#FB7185", "#D6D3D1", "#78716C",
    )
}

data class ResolvedPlaylistIcon(val name: String, val colorHex: String) {
    val color: Color get() = Color(android.graphics.Color.parseColor(colorHex))
}

private val NAME_RE = Regex("[a-z0-9]+(-[a-z0-9]+)*")
private val COLOR_RE = Regex("#[0-9a-fA-F]{6}")

fun formatPlaylistIcon(name: String, colorHex: String): String = "$name:$colorHex"

/** A random music icon in a random palette color, as a stored icon string. */
fun randomMusicIcon(): String {
    val name = PlaylistIconDefaults.MUSIC_ICON_NAMES.random()
    val color = PlaylistIconDefaults.COLORS.random()
    return formatPlaylistIcon(name, color)
}

/**
 * Parse a stored playlist icon. Anything that is not a well-formed
 * `name:#RRGGBB` pair — including legacy emoji — resolves to the default
 * eighth-notes-in-white icon.
 */
fun resolvePlaylistIcon(icon: String?): ResolvedPlaylistIcon {
    if (!icon.isNullOrBlank()) {
        val sep = icon.indexOf(':')
        if (sep > 0) {
            val name = icon.substring(0, sep)
            val colorHex = icon.substring(sep + 1)
            if (NAME_RE.matches(name) && COLOR_RE.matches(colorHex)) {
                return ResolvedPlaylistIcon(name, colorHex)
            }
        }
    }
    return ResolvedPlaylistIcon(PlaylistIconDefaults.NAME, PlaylistIconDefaults.COLOR)
}

/** A playlist's lucide icon in its stored color (default: eighth notes, white). */
@Composable
fun PlaylistIcon(
    icon: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resolved = resolvePlaylistIcon(icon)
    LucideIcon(
        name = resolved.name,
        modifier = modifier,
        tint = resolved.color,
        contentDescription = contentDescription,
    )
}
