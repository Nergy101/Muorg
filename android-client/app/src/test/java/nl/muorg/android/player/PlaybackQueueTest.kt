package nl.muorg.android.player

import nl.muorg.android.data.api.CatalogTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The play order.
 *
 * Two shipped bugs live in here. Adding a track to the queue while an album was
 * playing deleted the rest of the album — the old controller cleared every
 * upcoming system item so the addition would be next. And turning on shuffle
 * flipped Media3's shuffle mode, which the queue screen never read, so the list
 * showed one order while playback followed another.
 *
 * Both come from the same thing: nothing owned the order. It does now, and the
 * list this holds *is* what plays.
 */
class PlaybackQueueTest {

    private fun track(id: Int) = CatalogTrack(
        id = id,
        path = "/m/$id.mp3",
        rootId = 1,
        title = "t$id",
        artist = "A",
        album = "Album",
        albumArtist = "A",
        featuring = null,
        year = 2020,
        genre = "Rock",
        trackNumber = id,
        discNumber = 1,
        durationSecs = 180.0,
        format = "mp3",
        mtimeSecs = 0,
        hasCover = false,
        rating = null,
        playCount = 0,
        lastPlayedAt = null,
    )

    private fun tracks(vararg ids: Int) = ids.map(::track)

    private val PlaybackQueue.ids: List<Int> get() = entries.map { it.track.id }
    private val PlaybackQueue.origins: List<QueueOrigin> get() = entries.map { it.origin }

    private fun album() = PlaybackQueue().playContext(tracks(1, 2, 3, 4, 5), track(1))

    // ── Starting a context ────────────────────────────────────────────────

    @Test
    fun `playing a context queues all of it in order`() {
        val q = album()
        assertEquals(listOf(1, 2, 3, 4, 5), q.ids)
        assertEquals(0, q.position)
        assertTrue(q.origins.all { it == QueueOrigin.SYSTEM })
    }

    @Test
    fun `starting mid-context leaves the earlier tracks behind as history`() {
        val q = PlaybackQueue().playContext(tracks(1, 2, 3, 4, 5), track(3))
        assertEquals(3, q.current?.id)
        assertEquals(listOf(4, 5), q.upNext.map { it.track.id })
    }

    @Test
    fun `a new context replaces the queued tracks of the old one`() {
        val q = album().addToUserQueue(tracks(9))
        val next = q.playContext(tracks(6, 7), track(6))
        assertEquals(listOf(6, 7), next.ids)
        assertTrue(next.userUpNext.isEmpty())
    }

    @Test
    fun `playing an empty context clears the queue rather than throwing`() {
        assertTrue(album().playContext(emptyList(), null).isEmpty)
    }

    @Test
    fun `with shuffle already on, the chosen track still plays first`() {
        val q = PlaybackQueue(shuffleEnabled = true)
            .playContext(tracks(1, 2, 3, 4, 5), track(3), Random(7))
        assertEquals(3, q.current?.id)
        assertEquals(setOf(1, 2, 3, 4, 5), q.ids.toSet())
    }

    // ── Shuffle ───────────────────────────────────────────────────────────

    @Test
    fun `shuffling reorders what is still to play`() {
        val long = PlaybackQueue().playContext((1..40).map(::track), track(1))
        val shuffled = long.withShuffle(true, Random(1))
        assertEquals(long.ids.toSet(), shuffled.ids.toSet())
        assertTrue("the order should actually change", shuffled.ids != long.ids)
    }

    @Test
    fun `shuffling does not move the track that is playing`() {
        val q = PlaybackQueue().playContext(tracks(1, 2, 3, 4, 5), track(2))
        val shuffled = q.withShuffle(true, Random(3))
        assertEquals(2, shuffled.current?.id)
    }

    @Test
    fun `shuffling leaves history alone so previous still works`() {
        val q = PlaybackQueue().playContext((1..20).map(::track), track(10))
        val shuffled = q.withShuffle(true, Random(5))
        assertEquals((1..10).toList(), shuffled.ids.take(10))
    }

    @Test
    fun `turning shuffle off puts the rest back in the context's order`() {
        val q = PlaybackQueue().playContext(tracks(1, 2, 3, 4, 5), track(1))
        val restored = q.withShuffle(true, Random(2)).withShuffle(false)
        assertEquals(listOf(1, 2, 3, 4, 5), restored.ids)
    }

    @Test
    fun `queued tracks keep their place when shuffle is toggled`() {
        // The listener put them there deliberately; shuffle is about the
        // context, not about their choices.
        val q = album().addToUserQueue(tracks(9))
        val shuffled = q.withShuffle(true, Random(4))
        assertEquals(9, shuffled.upNext.first().track.id)
        assertEquals(QueueOrigin.USER, shuffled.upNext.first().origin)
    }

    @Test
    fun `toggling to the state it is already in changes nothing`() {
        val q = album()
        assertEquals(q, q.withShuffle(false))
    }

    // ── The user queue ────────────────────────────────────────────────────

    @Test
    fun `queueing a track keeps the rest of the album`() {
        // The regression: the old controller deleted every upcoming system
        // track on the first user add, so queueing one song while an album
        // played threw the remaining album tracks away.
        val q = album().addToUserQueue(tracks(9))
        assertEquals(listOf(2, 3, 4, 5), q.systemUpNext.map { it.track.id })
    }

    @Test
    fun `a queued track plays before the rest of the context`() {
        val q = album().addToUserQueue(tracks(9))
        assertEquals(listOf(9, 2, 3, 4, 5), q.upNext.map { it.track.id })
    }

    @Test
    fun `queueing twice keeps the order they were added in`() {
        val q = album().addToUserQueue(tracks(9)).addToUserQueue(tracks(8))
        assertEquals(listOf(9, 8, 2, 3, 4, 5), q.upNext.map { it.track.id })
    }

    @Test
    fun `queueing a track that is already queued does not duplicate it`() {
        val q = album().addToUserQueue(tracks(9)).addToUserQueue(tracks(9))
        assertEquals(1, q.userUpNext.size)
    }

    @Test
    fun `queueing with nothing playing starts playback`() {
        val q = PlaybackQueue().addToUserQueue(tracks(9, 10))
        assertEquals(9, q.current?.id)
        assertEquals(0, q.position)
    }

    @Test
    fun `play next jumps ahead of anything already queued`() {
        val q = album().addToUserQueue(tracks(9)).playNext(track(8))
        assertEquals(listOf(8, 9, 2, 3, 4, 5), q.upNext.map { it.track.id })
    }

    @Test
    fun `play next on an already queued track moves it instead of cloning it`() {
        val q = album().addToUserQueue(tracks(9, 8)).playNext(track(8))
        assertEquals(listOf(8, 9), q.userUpNext.map { it.track.id })
    }

    // ── Advancing ─────────────────────────────────────────────────────────

    @Test
    fun `playback runs the queue first and then resumes the context`() {
        // The whole point of the split: queued tracks interrupt, they do not
        // replace. Media3 walks this list one step at a time, so the order it
        // is in *is* the order they play in — queued tracks, then the album
        // picking up where it was.
        val q = album().addToUserQueue(tracks(9, 8))
        assertEquals(listOf(9, 8, 2, 3, 4, 5), q.upNext.map { it.track.id })
        assertEquals(
            listOf(QueueOrigin.USER, QueueOrigin.USER) + List(4) { QueueOrigin.SYSTEM },
            q.upNext.map { it.origin },
        )
    }

    // ── Editing the queue ─────────────────────────────────────────────────

    @Test
    fun `removing a queued track drops it`() {
        val q = album().remove(3)
        assertEquals(listOf(1, 2, 4, 5), q.ids)
        assertEquals(1, q.current?.id)
    }

    @Test
    fun `removing something already played keeps the current track current`() {
        val q = PlaybackQueue().playContext(tracks(1, 2, 3), track(3)).remove(1)
        assertEquals(3, q.current?.id)
    }

    @Test
    fun `the playing track is not removable from the queue`() {
        // Removing what is playing is a skip, and the caller has to say so.
        val q = album()
        assertEquals(q, q.remove(1))
    }

    @Test
    fun `moving a track keeps the current one current`() {
        val q = PlaybackQueue().playContext(tracks(1, 2, 3, 4), track(2)).move(3, 1)
        assertEquals(2, q.current?.id)
        assertEquals(listOf(1, 4, 2, 3), q.ids)
    }

    @Test
    fun `moving the current track follows it`() {
        val q = PlaybackQueue().playContext(tracks(1, 2, 3, 4), track(1)).move(0, 2)
        assertEquals(1, q.current?.id)
        assertEquals(2, q.position)
    }

    @Test
    fun `skipping to a queued track makes it current`() {
        val q = album().skipTo(4)
        assertEquals(4, q.current?.id)
        assertEquals(listOf(5), q.upNext.map { it.track.id })
    }

    @Test
    fun `skipping to a track that is not queued does nothing`() {
        val q = album()
        assertEquals(q, q.skipTo(99))
    }

    @Test
    fun `clearing the queue leaves the context playing`() {
        val q = album().addToUserQueue(tracks(9, 8)).clearUserQueue()
        assertTrue(q.userUpNext.isEmpty())
        assertEquals(listOf(2, 3, 4, 5), q.systemUpNext.map { it.track.id })
    }

    @Test
    fun `clearing everything leaves only what is playing`() {
        val q = album().addToUserQueue(tracks(9)).clearUpNext()
        assertTrue(q.upNext.isEmpty())
        assertEquals(1, q.current?.id)
    }

    @Test
    fun `the two halves of up next are reported separately`() {
        val q = album().addToUserQueue(tracks(9))
        assertEquals(listOf(9), q.userUpNext.map { it.track.id })
        assertEquals(listOf(2, 3, 4, 5), q.systemUpNext.map { it.track.id })
    }

    @Test
    fun `a queued track dragged past the album is shown where it will play`() {
        // The sections are slices of the play order, not a filter over it. A
        // track dragged below the album really does play after it, and saying
        // otherwise would put the screen back to disagreeing with playback.
        val q = album().addToUserQueue(tracks(9)).move(1, 4)
        assertTrue(q.userUpNext.isEmpty())
        assertEquals(listOf(2, 3, 4, 9, 5), q.systemUpNext.map { it.track.id })
    }

    @Test
    fun `re-queueing a track dragged down the list does not duplicate it`() {
        val q = album().addToUserQueue(tracks(9)).move(1, 4).addToUserQueue(tracks(9))
        assertEquals(1, q.ids.count { it == 9 })
    }

    // ── Topping up a context ──────────────────────────────────────────────

    @Test
    fun `extending the context lands behind what the listener queued`() {
        // Shuffle-all tops itself up as it nears the end. Those tracks are not
        // the listener's own picks, so they must not jump the queue.
        val q = album().addToUserQueue(tracks(9)).appendToContext(tracks(6, 7))
        assertEquals(listOf(9, 2, 3, 4, 5, 6, 7), q.upNext.map { it.track.id })
        assertEquals(listOf(9), q.userUpNext.map { it.track.id })
    }

    @Test
    fun `extending the context skips tracks already queued`() {
        val q = album().appendToContext(tracks(5, 6))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), q.ids)
    }

    @Test
    fun `turning shuffle off restores tracks added after the context started`() {
        val q = album().appendToContext(tracks(6, 7))
        val restored = q.withShuffle(true, Random(9)).withShuffle(false)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), restored.ids)
    }
}
