package pizza.kulhad.medslave

import java.security.SecureRandom

/* Deck order, the same as the site's order.js (so a seed means the same order on both): the deck's notes sorted by id, then a
   Fisher–Yates shuffle driven by sfc32 seeded (cyrb128) from a 12-character base-64 seed. */
object Order {
    private const val B64 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"
    private const val SEEDL = 12
    const val RQMIN = 20
    const val RQMAX = 40

    fun newSeed(): String { val b = ByteArray(SEEDL); SecureRandom().nextBytes(b); return b.joinToString("") { B64[it.toInt() and 63].toString() } }
    fun valid(s: String?) = s != null && s.length == SEEDL && s.all { it in B64 }

    private fun rng(seed: String): () -> Double {
        var h1 = 1779033703; var h2 = 3144134277L.toInt(); var h3 = 1013904242; var h4 = 2773480762L.toInt()
        for (ch in seed) {
            val k = ch.code
            h1 = h2 xor ((h1 xor k) * 597399067); h2 = h3 xor ((h2 xor k) * 2869860233L.toInt())
            h3 = h4 xor ((h3 xor k) * 951274213); h4 = h1 xor ((h4 xor k) * 2716044179L.toInt())
        }
        h1 = (h3 xor (h1 ushr 18)) * 597399067; h2 = (h4 xor (h2 ushr 22)) * 2869860233L.toInt()
        h3 = (h1 xor (h3 ushr 17)) * 951274213; h4 = (h2 xor (h4 ushr 19)) * 2716044179L.toInt()
        var a = h1 xor h2 xor h3 xor h4; var b = h2 xor h1; var c = h3 xor h1; var d = h4 xor h1
        val f = {
            var t = a + b; a = b xor (b ushr 9); b = c + (c shl 3); c = (c shl 21) or (c ushr 11); d += 1; t += d; c += t
            (t.toLong() and 0xffffffffL).toDouble() / 4294967296.0
        }
        repeat(12) { f() }
        return f
    }

    fun orderFor(seed: String?, n: Int): IntArray? {
        if (!valid(seed)) return null
        val r = rng(seed!!); val p = IntArray(n) { it }
        for (i in n - 1 downTo 1) { val j = (r() * (i + 1)).toInt(); val t = p[i]; p[i] = p[j]; p[j] = t }
        return p
    }

    class Plan(val seed: String?, val q: List<Note>, val first: Int?, val start: Int)

    private fun byId(notes: List<Note>) = notes.sortedBy { it.id }

    /** a fresh pass doesn't start on the card the last one ended on */
    fun planPass(notes: List<Note>, savedSeed: String?, savedLast: Pair<Long, Int>?, prevLast: Note?): Plan {
        val s = byId(notes); val n = s.size
        var seed = savedSeed; var idx = orderFor(seed, n)
        if (idx == null) { var k = 0; do { seed = newSeed(); idx = orderFor(seed, n)!! } while (n > 1 && prevLast != null && s[idx!![0]] === prevLast && ++k < 20) }
        return resumeAt(seed, idx!!.map { s[it] }, savedLast)
    }
    fun planOrdered(notes: List<Note>, savedLast: Pair<Long, Int>?) = resumeAt(null, byId(notes), savedLast)

    /** the pass keeps the deck's full order; resuming starts at the saved card */
    private fun resumeAt(seed: String?, q: List<Note>, savedLast: Pair<Long, Int>?): Plan {
        var first: Int? = null; var start = 0
        if (savedLast != null) { val j = q.indexOfFirst { it.id == savedLast.first && savedLast.second in it.ords }; if (j >= 0) { start = j; first = savedLast.second } }
        return Plan(seed, q, first, start)
    }
}
