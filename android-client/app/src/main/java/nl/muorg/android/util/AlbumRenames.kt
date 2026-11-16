package nl.muorg.android.util

/**
 * Session-only redirects for renamed albums: old name → new name. The album's
 * identity is its display name, so saving new album metadata renames it — any
 * back-stack entry still holding the old name (the album page underneath the
 * editor when the user navigates back) is forwarded to the new name instead of
 * showing an empty album.
 */
object AlbumRenames {
    private val redirects = mutableMapOf<String, String>()

    @Synchronized
    fun record(fromName: String, toName: String) {
        if (fromName != toName) redirects[fromName] = toName
    }

    /** Follows recorded renames transitively (A→B→C resolves A to C). */
    @Synchronized
    fun resolve(name: String): String {
        val seen = mutableSetOf<String>()
        var current = name
        while (seen.add(current)) {
            current = redirects[current] ?: return current
        }
        return current
    }
}
