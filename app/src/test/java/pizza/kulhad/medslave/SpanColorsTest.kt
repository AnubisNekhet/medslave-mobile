package pizza.kulhad.medslave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanColorsTest {
    private fun accentOf(html: String) = Regex("var\\(--ctp-([a-z]+)\\)").find(html)?.groupValues?.get(1)

    @Test fun greysLoseTheirColour() {
        val out = SpanColors(1).fix("<span style=\"color: black\">x</span>")
        assertFalse(out, out.contains("black"))
        assertEquals(null, accentOf(out))
    }

    @Test fun namedColoursGoToAnAccent() {
        val out = SpanColors(1).fix("<span style=\"color: crimson\">x</span>")
        assertTrue(out, out.contains("color: rgb(var(--ctp-"))
    }

    @Test fun theAppsOwnColoursStay() {
        val v = "<span style=\"color: rgb(var(--ctp-red))\">x</span>"
        assertEquals(v, SpanColors(1).fix(v))
        val exact = "<span style=\"color: #d20f39\">x</span>" // the light red accent
        assertEquals(exact, SpanColors(1).fix(exact))
    }

    @Test fun backgroundsAndOtherTagsAreLeftAlone() {
        val bg = "<span style=\"background-color: #123abc\">x</span>"
        assertEquals(bg, SpanColors(1).fix(bg))
        val div = "<div style=\"color: #123abc\">x</div>"
        assertEquals(div, SpanColors(1).fix(div))
    }

    @Test fun oneValueGetsOneAccentForTheWholeImport() {
        val sc = SpanColors(7)
        val a = accentOf(sc.fix("<span style=\"color: #123abc\">x</span>"))
        val b = accentOf(sc.fix("<span style='color:#123abc'>y</span>"))
        assertTrue(a != null)
        assertEquals(a, b)
    }
}
