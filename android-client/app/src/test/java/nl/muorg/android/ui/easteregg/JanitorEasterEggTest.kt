package nl.muorg.android.ui.easteregg

import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.muorg.android.ui.OrcMood
import nl.muorg.android.ui.UiMessage
import nl.muorg.android.ui.UiMessenger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JanitorEasterEggTest {

    @Test
    fun `four taps keep the janitor asleep and silent`() = runTest {
        val messenger = UiMessenger()
        val egg = JanitorEasterEgg(messenger)
        val received = mutableListOf<UiMessage>()
        val collector = launch { messenger.messages.collect { received += it } }
        yield()

        repeat(4) { egg.onLogoTapped() }
        advanceUntilIdle()

        assertFalse(egg.awake.value)
        assertTrue(received.isEmpty())
        collector.cancel()
    }

    @Test
    fun `fifth tap wakes the janitor with one happy message`() = runTest {
        val messenger = UiMessenger()
        val egg = JanitorEasterEgg(messenger)
        val received = mutableListOf<UiMessage>()
        val collector = launch { messenger.messages.collect { received += it } }
        yield()

        repeat(5) { egg.onLogoTapped() }
        advanceUntilIdle()

        assertTrue(egg.awake.value)
        assertEquals(1, received.size)
        assertEquals("Awoken the Janitor", received[0].text)
        assertEquals(OrcMood.Happy, received[0].mood)
        collector.cancel()
    }

    @Test
    fun `extra taps after waking do not emit a second message`() = runTest {
        val messenger = UiMessenger()
        val egg = JanitorEasterEgg(messenger)
        val received = mutableListOf<UiMessage>()
        val collector = launch { messenger.messages.collect { received += it } }
        yield()

        repeat(10) { egg.onLogoTapped() }
        advanceUntilIdle()

        assertTrue(egg.awake.value)
        assertEquals(1, received.size)
        collector.cancel()
    }
}
