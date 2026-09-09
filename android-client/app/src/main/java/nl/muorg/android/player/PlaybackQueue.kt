package nl.muorg.android.player

import nl.muorg.android.data.api.CatalogTrack
import kotlin.random.Random

/** Where a queued track came from — see [PlaybackQueue]. */
enum class QueueOrigin {
    /** Explicitly queued by the user ("add to queue", "play next"). */
    USER,

    /** Loaded as a context: an album, a playlist, a mix, shuffle-all. */
    SYSTEM,
}

data class QueueEntry(val track: CatalogTrack, val origin: QueueOrigin)

/**
 * What plays next, and in what order.
 *
 * Modelled on the web client's player store, which splits the queue in two: a
 * *user queue* of tracks the listener explicitly asked for, and a *system
 * queue* holding whatever context they started — an album, a playlist, a mix.
 * The user queue has priority, and when it runs out playback continues the
 * system queue where it left off.
 *
 * Android had neither half. Everything lived in one Media3 timeline, and the
 * first "add to queue" deleted every upcoming system track so the addition
 * would be next — queueing one song while playing an album threw the rest of
 * the album away. Shuffle had the mirror problem: it flipped Media3's own
 * shuffle mode, whose ordering the queue screen does not read, so the list kept
 * showing the unshuffled order while playback jumped around it.
 *
 * So order lives here instead, as one flat list that already *is* the play
 * order, each entry tagged with where it came from. Media3's timeline is a
 * straight projection of it and its shuffle mode stays off. What the queue
 * screen shows is then what will actually play, by construction.
 *
 * Advancing is not modelled here — Media3 walks the timeline itself and the
 * controller feeds the new index back as [position]. This owns the *order*,
 * which is the part that was wrong.
 *
 * Immutable: every operation returns a new queue, which makes the whole model
 * testable without a player, a service or a device.
 */
data class PlaybackQueue(
    /** The play order itself, history included. */
    val entries: List<QueueEntry> = emptyList(),
    /** Index into [entries] of the track playing now, or -1 when idle. */
    val position: Int = -1,
    val shuffleEnabled: Boolean = false,
    /**
     * The context as it was handed over, in its own order. Kept so turning
     * shuffle off can put the remaining system tracks back the way they were
     * rather than leaving them in whatever order the shuffle produced.
     */
    val context: List<CatalogTrack> = emptyList(),
) {
    val current: CatalogTrack? get() = entries.getOrNull(position)?.track

    /** Everything still to play, in order. */
    val upNext: List<QueueEntry> get() = entries.drop(position + 1)

    /**
     * The user queue's remainder — "Your queue" on the queue screen.
     *
     * The leading run rather than every user entry, because this list is a
     * *slice of the play order*, not a filter over it: these are the tracks
     * that play before the context resumes. Every operation here keeps queued
     * tracks in one run right after the current track; only a manual drag can
     * push one past the context, and then it genuinely does play later.
     */
    val userUpNext: List<QueueEntry> get() = upNext.takeWhile { it.origin == QueueOrigin.USER }

    /** The rest of what is queued — "Up next" on the queue screen. */
    val systemUpNext: List<QueueEntry> get() = upNext.drop(userUpNext.size)

    val tracks: List<CatalogTrack> get() = entries.map { it.track }

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Start a context: an album, a playlist, a mix.
     *
     * Replaces everything, user queue included — those tracks were queued
     * against the context being left behind.
     */
    fun playContext(
        tracks: List<CatalogTrack>,
        start: CatalogTrack?,
        random: Random = Random.Default,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return PlaybackQueue(shuffleEnabled = shuffleEnabled)
        val startIndex = tracks.indexOfFirst { it.id == start?.id }.coerceAtLeast(0)
        val ordered = if (shuffleEnabled) {
            // The chosen track still plays first; shuffle applies to the rest.
            listOf(tracks[startIndex]) + tracks.filterIndexed { i, _ -> i != startIndex }.shuffled(random)
        } else {
            tracks
        }
        val pos = if (shuffleEnabled) 0 else startIndex
        return PlaybackQueue(
            entries = ordered.map { QueueEntry(it, QueueOrigin.SYSTEM) },
            position = pos,
            shuffleEnabled = shuffleEnabled,
            context = tracks,
        )
    }

    /**
     * Turn shuffle on or off, re-ordering what has not played yet.
     *
     * Only the system tracks ahead of the current one move: history stays put
     * so "previous" still works, the current track keeps playing, and user-
     * queued tracks hold their place — the listener put them there on purpose.
     */
    fun withShuffle(enabled: Boolean, random: Random = Random.Default): PlaybackQueue {
        if (enabled == shuffleEnabled) return this
        val head = entries.take(position + 1)
        val tail = entries.drop(position + 1)
        val systemTail = tail.filter { it.origin == QueueOrigin.SYSTEM }
        val reordered = if (enabled) {
            systemTail.shuffled(random)
        } else {
            // Back to the context's own order, keeping only what is still queued.
            val remaining = systemTail.map { it.track.id }.toSet()
            context.filter { it.id in remaining }.map { QueueEntry(it, QueueOrigin.SYSTEM) }
                .ifEmpty { systemTail }
        }
        // Rebuild the tail in place: user entries keep their slots, system
        // entries fill the rest in their new order.
        val iterator = reordered.iterator()
        val newTail = tail.map { entry ->
            if (entry.origin == QueueOrigin.USER) entry
            else if (iterator.hasNext()) iterator.next() else entry
        }
        return copy(entries = head + newTail, shuffleEnabled = enabled)
    }

    /**
     * Append to the user queue: after anything already queued, before the rest
     * of the context.
     *
     * Tracks already queued by the user are left where they are rather than
     * duplicated.
     */
    fun addToUserQueue(toAdd: List<CatalogTrack>): PlaybackQueue {
        // Deduped against everything still queued by hand, not just the run
        // above, so a track dragged down the list is not silently re-added.
        val queuedByHand = upNext.filter { it.origin == QueueOrigin.USER }
        val fresh = toAdd.filterNot { t -> queuedByHand.any { it.track.id == t.id } }
        if (fresh.isEmpty()) return this
        val at = userQueueEnd()
        val added = fresh.map { QueueEntry(it, QueueOrigin.USER) }
        val next = entries.subList(0, at) + added + entries.subList(at, entries.size)
        // Nothing playing: the first added track starts.
        return copy(entries = next, position = if (position < 0) 0 else position)
    }

    /**
     * Extend the context at the end — what shuffle-all uses to top itself up.
     *
     * These are not the listener's own picks, so they go behind everything,
     * queued tracks included.
     */
    fun appendToContext(toAdd: List<CatalogTrack>): PlaybackQueue {
        val fresh = toAdd.filterNot { t -> entries.any { it.track.id == t.id } }
        if (fresh.isEmpty()) return this
        return copy(
            entries = entries + fresh.map { QueueEntry(it, QueueOrigin.SYSTEM) },
            context = context + fresh,
            position = if (position < 0) 0 else position,
        )
    }

    /** Queue a track to play immediately after the current one. */
    fun playNext(track: CatalogTrack): PlaybackQueue {
        val without = removeUpcoming(track.id)
        val at = (without.position + 1).coerceAtLeast(0)
        val next = without.entries.subList(0, at) +
            QueueEntry(track, QueueOrigin.USER) +
            without.entries.subList(at, without.entries.size)
        return without.copy(entries = next, position = if (without.position < 0) 0 else without.position)
    }

    /** Jump to a queued track. */
    fun skipTo(trackId: Int): PlaybackQueue {
        val i = entries.indexOfFirst { it.track.id == trackId }
        return if (i < 0) this else copy(position = i)
    }

    /**
     * Drop a queued track.
     *
     * The current track stays: removing what is playing is a skip, not a queue
     * edit, and the two want different handling.
     */
    fun remove(trackId: Int): PlaybackQueue {
        val i = entries.indexOfFirst { it.track.id == trackId }
        if (i < 0 || i == position) return this
        return copy(
            entries = entries.filterIndexed { index, _ -> index != i },
            position = if (i < position) position - 1 else position,
        )
    }

    /** Move a queued track, by index into [entries]. */
    fun move(from: Int, to: Int): PlaybackQueue {
        if (from !in entries.indices || to !in entries.indices || from == to) return this
        val next = entries.toMutableList()
        next.add(to, next.removeAt(from))
        val newPosition = when {
            position == from -> to
            from < position && to >= position -> position - 1
            from > position && to <= position -> position + 1
            else -> position
        }
        return copy(entries = next, position = newPosition)
    }

    /** Drop everything the user queued that has not played yet. */
    fun clearUserQueue(): PlaybackQueue = copy(
        entries = entries.filterIndexed { i, e ->
            i <= position || e.origin == QueueOrigin.SYSTEM
        },
    )

    /** Drop everything still queued, from either half. */
    fun clearUpNext(): PlaybackQueue = copy(entries = entries.take(position + 1))

    /** Index just past the run of user entries following the current track. */
    private fun userQueueEnd(): Int {
        var at = position + 1
        while (at < entries.size && entries[at].origin == QueueOrigin.USER) at++
        return at
    }

    /** Drop an upcoming copy of a track so requeueing it moves it, not clones it. */
    private fun removeUpcoming(trackId: Int): PlaybackQueue {
        val i = entries.indexOfFirst { it.track.id == trackId }
        return if (i <= position) this else remove(trackId)
    }
}
