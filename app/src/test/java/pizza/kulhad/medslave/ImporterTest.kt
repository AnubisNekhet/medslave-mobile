package pizza.kulhad.medslave

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImporterTest {
    /* ---- note types: guessed kind and slot plan */

    @Test fun basicFieldsByName() {
        val p = plan(listOf("Front", "Back", "Extra", "Source"), "b")
        assertArrayEquals(intArrayOf(0, 1, 2, -1, 3, -1, -1), p)
    }

    @Test fun basicFallsBackToTheFirstUnusedFields() {
        assertArrayEquals(intArrayOf(0, 1, -1, -1, -1, -1, -1), plan(listOf("Foo", "Bar"), "b"))
    }

    @Test fun reverseIsMatchedByNameButNeverFilled() {
        assertEquals(2, plan(listOf("Front", "Back", "Add Reverse"), "b")[6])
        assertEquals(-1, plan(listOf("Front", "Back", "Other"), "b")[6])
    }

    @Test fun clozeText() {
        assertEquals(0, plan(listOf("Text", "Back Extra"), "c")[0])
        assertEquals(1, plan(listOf("Text", "Back Extra"), "c")[1])
    }

    @Test fun kindsAreRecognised() {
        assertTrue(isMCQ(listOf("Question", "Correct", "Option 1", "Option 2", "Option 3")))
        assertFalse(isMCQ(listOf("Front", "Back")))
        assertTrue(isMatch(listOf("Question", "Left", "Right", "Pairs")))
        assertFalse(isMatch(listOf("Question", "Left", "Answer")))
        assertTrue(isIOE(listOf("Image", "Question Mask", "Answer Mask")))
        assertFalse(isIOE(listOf("Image", "Header")))
    }

    /* ---- fields */

    @Test fun cleanRemovesScriptsHandlersAndJavascriptLinks() {
        val out = clean("<b>ok</b><script>alert(1)</script><img src=x onerror=\"alert(2)\"><a href=\"javascript:alert(3)\">l</a><iframe src=y></iframe>")
        assertFalse(out.contains("script", ignoreCase = true))
        assertFalse(out.contains("onerror"))
        assertFalse(out.contains("javascript:"))
        assertFalse(out.contains("iframe"))
        assertTrue(out.contains("<b>ok</b>"))
    }

    @Test fun htmlPointsImagesAtTheMediaFolderAndDropsSounds() {
        val used = HashSet<String>()
        val out = html("<img src=\"a%20b.png\"> [sound:x.mp3] <img src=\"https://e.com/c.png\">", used, SpanColors(1))
        assertTrue(out, out.contains("<img data-m=\"a b.png\""))
        assertTrue(out.contains("src=\"https://e.com/c.png\""))
        assertFalse(out.contains("[sound:"))
        assertEquals(setOf("a b.png"), used)
    }

    @Test fun stripMakesAPlainPreview() {
        assertEquals("the heart pumps", strip("the {{c1::heart::organ}} <b>pumps</b>"))
        assertEquals("(image) & <", strip("<img src=a.png>&amp; &lt;").trim())
        assertEquals("(occlusion shapes)", strip("{{c1::image-occlusion:rect:left=.1}}"))
    }

    /* ---- the media map's protobuf */

    @Test fun protobufVarintsAndStrings() {
        val bytes = byteArrayOf(0x08, 0x96.toByte(), 0x01, 0x12, 0x02, 'h'.code.toByte(), 'i'.code.toByte())
        val f = pb(bytes)
        assertEquals(1 to 150L, f[0])
        assertEquals(2, f[1].first)
        assertEquals("hi", String(f[1].second as ByteArray))
    }

    /* ---- MCQs */

    @Test fun mcqCorrectOptionByNumberLetterOrText() {
        val ops = listOf("Heart", "Lung", "Liver", "Kidney")
        assertEquals(1, mcqOptions("2", ops).second)
        assertEquals(2, mcqOptions("<b>c</b>", ops).second)
        assertEquals(3, mcqOptions("kidney", ops).second)
        assertEquals(-1, mcqOptions("Spleen", ops).second)
    }

    @Test fun mcqBlankOptionsDoNotShiftTheAnswer() {
        val (op, right) = mcqOptions("3", listOf("Heart", "", "Liver", "Kidney"))
        assertEquals(listOf("Heart", "Liver", "Kidney"), op)
        assertEquals(1, right) // Liver, the third field
        assertEquals(-1, mcqOptions("2", listOf("Heart", "", "Liver")).second) // the blank one
    }

    /* ---- Match */

    @Test fun matchTableKeysEachCell() {
        val t = matchTable("A. Heart<br>B. Lung", "1. Pump<br>2. Breathe", "A-2, B-1")
        assertTrue(t, t.startsWith("<table class=\"mt-src\">"))
        assertTrue(t, t.contains("<td data-k=\"A\">Heart</td>"))
        assertTrue(t, t.contains("<td data-k=\"B\">Lung</td>"))
        assertTrue(t, t.contains("<td data-k=\"1\">Pump</td>"))
        assertTrue(t, t.contains("<td data-k=\"2\">Breathe</td>"))
    }

    @Test fun matchItemsWithoutKeysTakeThemInOrder() {
        val t = matchTable("Heart<br>Lung", "Pump<br>Breathe", "A-1, B-2")
        assertTrue(t, t.contains("<td data-k=\"A\">Heart</td>"))
        assertTrue(t, t.contains("<td data-k=\"2\">Breathe</td>"))
    }

    @Test fun matchWithoutPairsGivesNothing() {
        assertEquals("", matchTable("Heart", "Pump", "no pairs here"))
    }

    @Test fun keyOrderPutsNumbersThenRomanThenLetters() {
        val ks = listOf("b", "ii", "10", "2", "a", "iv").sortedBy(::keyOrder)
        assertEquals(listOf("2", "10", "ii", "iv", "a", "b"), ks)
    }
}
