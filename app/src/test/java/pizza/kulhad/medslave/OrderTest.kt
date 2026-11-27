package pizza.kulhad.medslave

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTest {
    private fun notes(n: Int) = List(n) { Note(1000L + it, 'b', 0, intArrayOf(0), longArrayOf(1L)) }

    /* computed with an independent port of the JS cyrb128 + sfc32 (Math.imul, >>> 0), 12 draws discarded: a seed must give the same
       order as the web version did, and the same order after any refactor (saved progress depends on it) */
    @Test fun seedsGiveTheSameOrderAsTheJsVersion() {
        assertArrayEquals(intArrayOf(4, 8, 5, 2, 3, 7, 9, 0, 1, 6), Order.orderFor("AAAAAAAAAAAA", 10))
        assertArrayEquals(intArrayOf(8, 0, 2, 5, 6, 4, 3, 7, 9, 1), Order.orderFor("0123456789ab", 10))
        assertArrayEquals(
            intArrayOf(11, 13, 3, 14, 8, 17, 2, 6, 0, 9, 10, 7, 16, 18, 12, 15, 1, 19, 5, 4),
            Order.orderFor("medslave-_Zz", 20)
        )
    }

    @Test fun seedValidation() {
        assertTrue(Order.valid("AAAAAAAAAAAA"))
        assertFalse(Order.valid(null))
        assertFalse(Order.valid("short"))
        assertFalse(Order.valid("AAAAAAAAAAA!"))
        assertNull(Order.orderFor("bad", 5))
    }

    @Test fun newSeedsAreValidAndShufflesArePermutations() {
        repeat(50) {
            val s = Order.newSeed()
            assertTrue(Order.valid(s))
            val o = Order.orderFor(s, 37)!!
            assertEquals((0 until 37).toList(), o.sorted())
        }
    }

    @Test fun aSavedSeedResumesAtTheSavedCard() {
        val ns = notes(10)
        val first = Order.planPass(ns, "AAAAAAAAAAAA", null, null)
        assertEquals(0, first.start)
        val target = first.q[6]
        val again = Order.planPass(ns.shuffled(), "AAAAAAAAAAAA", target.id to 0, null)
        assertEquals(first.q.map { it.id }, again.q.map { it.id })
        assertEquals(6, again.start)
        assertEquals(0, again.first)
    }

    @Test fun aSavedCardWithAnOrdinalItNoLongerHasStartsFromTheTop() {
        val ns = notes(5)
        val p = Order.planPass(ns, "AAAAAAAAAAAA", ns[2].id to 3, null)
        assertEquals(0, p.start)
        assertNull(p.first)
    }

    @Test fun aFreshPassDoesNotStartOnTheCardTheLastOneEndedOn() {
        val ns = notes(2)
        repeat(30) {
            val p = Order.planPass(ns, null, null, ns[0])
            assertNotSame(ns[0], p.q[0])
        }
    }

    @Test fun orderedModeSortsByNoteId() {
        val ns = notes(6).shuffled()
        val p = Order.planOrdered(ns, null)
        assertEquals(ns.map { it.id }.sorted(), p.q.map { it.id })
        assertNull(p.seed)
    }
}
