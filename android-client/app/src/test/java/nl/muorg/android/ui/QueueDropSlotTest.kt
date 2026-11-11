package nl.muorg.android.ui

import nl.muorg.android.player.QueueOrigin.SYSTEM
import nl.muorg.android.player.QueueOrigin.USER
import nl.muorg.android.ui.screen.queue.DropSlot
import nl.muorg.android.ui.screen.queue.resolveDropSlot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a held queue row would land, read off the layout: the index follows the
 * rows it has passed, the section follows the index, and only the divider
 * itself needs the finger to decide.
 */
class QueueDropSlotTest {

    // Others: your queue of two, up next of three. Centres every 50px from 25.
    private val others = listOf(USER, USER, SYSTEM, SYSTEM, SYSTEM)
    private val centers = listOf(25f, 75f, 125f, 175f, 225f)

    private fun resolve(
        dragCenter: Float,
        current: DropSlot = DropSlot(2, SYSTEM),
        header: Float? = 100f,
        origins: List<nl.muorg.android.player.QueueOrigin> = others,
        rowCenters: List<Float> = centers,
    ) = resolveDropSlot(origins, 0, rowCenters, dragCenter, header, current)

    @Test
    fun `the index is the number of rows the centre has passed`() {
        assertEquals(0, resolve(10f).index)
        assertEquals(3, resolve(130f).index)
        assertEquals(5, resolve(500f).index)
    }

    @Test
    fun `above the last queued track is your queue, below the first up-next one is up next`() {
        assertEquals(USER, resolve(10f).section)   // index 0
        assertEquals(SYSTEM, resolve(130f).section) // index 3
    }

    @Test
    fun `on the divider the finger decides`() {
        // Index 2 sits between your queue and up next; the header centre is 100.
        assertEquals(DropSlot(2, USER), resolve(90f))
        assertEquals(DropSlot(2, SYSTEM), resolve(110f))
    }

    @Test
    fun `lifting a track above the up-next header starts your queue`() {
        // No your-queue yet: just the "Up next" header on top, centre at 20.
        val onlyUpNext = listOf(SYSTEM, SYSTEM)
        val c = listOf(75f, 125f)
        // Held below the header's centre it is a plain reorder of up next...
        assertEquals(DropSlot(0, SYSTEM), resolve(40f, origins = onlyUpNext, rowCenters = c, header = 20f))
        // ...lifted above it, it becomes the first entry of your queue.
        assertEquals(DropSlot(0, USER), resolve(10f, origins = onlyUpNext, rowCenters = c, header = 20f))
    }

    @Test
    fun `past the end of the list the track keeps its own section`() {
        // Everything else is queued by the user; there is no divider below.
        val onlyQueued = listOf(USER, USER)
        val c = listOf(25f, 75f)
        assertEquals(SYSTEM, resolve(200f, current = DropSlot(2, SYSTEM), origins = onlyQueued, rowCenters = c, header = null).section)
        assertEquals(USER, resolve(200f, current = DropSlot(2, USER), origins = onlyQueued, rowCenters = c, header = null).section)
    }

    @Test
    fun `an off-screen run of rows above is counted`() {
        // Only the last two rows are on screen; three are scrolled out above.
        val slot = resolveDropSlot(others, 3, listOf(175f, 225f), 200f, null, DropSlot(0, SYSTEM))
        assertEquals(4, slot.index)
    }
}
