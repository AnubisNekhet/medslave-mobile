package pizza.kulhad.medslave

import org.junit.Assert.assertEquals
import org.junit.Test
import pizza.kulhad.medslave.ui.mdBlocks

class NotesTest {
    private val have = listOf("2.03", "2.04", "2.05", "2.10")

    @Test fun notesSinceTheLastVersionNewestFirst() {
        assertEquals(listOf("2.05", "2.04"), notesFor(have, "2.03", "2.05"))
        assertEquals(listOf("2.10", "2.05", "2.04"), notesFor(have, "2.03", "2.10"))
    }

    @Test fun noNotesWithoutAFileForTheNewVersions() {
        assertEquals(emptyList<String>(), notesFor(have, "2.10", "2.11"))
    }

    @Test fun updatedFromBeforeReleaseNotesShowsOnlyTheCurrentOnes() {
        assertEquals(listOf("2.04"), notesFor(have, null, "2.04"))
        assertEquals(emptyList<String>(), notesFor(have, null, "2.06"))
    }

    @Test fun markdownBlocks() {
        val b = mdBlocks("# What's new\n\nFaster **imports**.\nAnd more.\n\n- one\n  carried on\n* two\n1. first\n")
        assertEquals(listOf(1 to "What's new", 0 to "Faster **imports**. And more."), b.take(2).map { it.h to it.text })
        assertEquals(listOf("•" to "one carried on", "•" to "two", "1." to "first"), b.drop(2).map { it.li to it.text })
    }
}
